package com.strife.production;

import com.strife.core.AuctionBids;
import com.strife.core.InventoryOps;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 密封投标（ADR-041 第三批）：与荷兰拍并存的附加轨——急者当场按荷兰价拿，赌者密封出价等槽末结算。
 *
 * <p>语义（ADR-041）：出价即时托管；结算 = 每槽最高价胜、同价先出价胜、同玩家只最高一笔参与（余款退）； 一价密封（胜者按出价成交，托管已扣）；败者全额退款进待领取池；池随任何
 * auction 交互或 {@code claim} 对在线玩家发放。 结算纯函数 {@link #settleSlots} 不碰 I/O（用例直测），附件读写集中在 {@link
 * #state}/{@link #save}。
 */
public final class AuctionBidding {

    private static final String LINGSHI = "item_lingshi";

    private AuctionBidding() {}

    // ═══ 状态存取（overworld Level 附件，ADR-041 §5）═══

    public static AuctionBids state(MinecraftServer server) {
        return server.overworld().getData(com.strife.core.StrifeAttachmentTypes.AUCTION_BIDS);
    }

    /** 包内可见供用例直测：附件读的形状锚。 */
    static AuctionBids stateOf(ServerLevel overworld) {
        return overworld.getData(com.strife.core.StrifeAttachmentTypes.AUCTION_BIDS);
    }

    private static void save(MinecraftServer server, AuctionBids updated) {
        saveTo(server.overworld(), updated);
    }

    static void saveTo(ServerLevel overworld, AuctionBids updated) {
        overworld.setData(com.strife.core.StrifeAttachmentTypes.AUCTION_BIDS, updated);
    }

    /** 当前槽位号（与 AuctionHouse 的轮换同源：gameTime / slotTicks）。 */
    public static long currentSlot(MinecraftServer server) {
        return server.overworld().getGameTime() / AuctionHouse.slotTicks();
    }

    // ═══ 出价 ═══

    /** 出价校验（不碰 I/O；返回错误文案或 null=通过）。 */
    static String validateBid(AuctionHouse.Lot lot, boolean isCurrentLot, int amount) {
        if (lot == null) {
            return "（拍品不存在）";
        }
        if (!isCurrentLot) {
            return "（" + lot.id() + " 不在本槽——密封投标只对当前拍品，/strife auction list 看当前）";
        }
        if (amount < lot.min()) {
            return "（出价低于底价：底 " + lot.min() + " 灵石）";
        }
        return null;
    }

    /** 出价入口：懒结算 → 校验 → 托管 → 落流水。 */
    public static void bid(ServerPlayer player, String lotId, int amount) {
        MinecraftServer server = player.server;
        settleAndPersist(server);
        AuctionHouse.Lot lot = AuctionHouse.lots().get(lotId);
        long slot = currentSlot(server);
        boolean current =
                lot != null
                        && lot.id()
                                .equals(AuctionHouse.currentLot(player.level().getGameTime()).id());
        String error = validateBid(lot, current, amount);
        if (error != null) {
            player.displayClientMessage(Component.literal(error), true);
            return;
        }
        InventoryOps.PlayerContainers containers =
                new InventoryOps.PlayerContainers(
                        player.getInventory(), player.getEnderChestInventory());
        if (InventoryOps.countOwned(containers, LINGSHI) < amount) {
            player.displayClientMessage(Component.literal("（灵石不足：出价 " + amount + " 需全额托管）"), true);
            return;
        }
        long removed = InventoryOps.remove(containers, LINGSHI, amount);
        if (removed < amount) {
            player.displayClientMessage(Component.literal("（托管异常已回滚）"), true);
            return;
        }
        AuctionBids state = state(server);
        List<AuctionBids.BidEntry> bids = new ArrayList<>(state.bids());
        bids.add(
                new AuctionBids.BidEntry(
                        slot,
                        player.getStringUUID(),
                        player.getName().getString(),
                        lot.id(),
                        amount,
                        player.level().getGameTime(),
                        AuctionBids.BidEntry.ACTIVE));
        save(server, new AuctionBids(bids, state.payouts()));
        player.displayClientMessage(
                Component.literal("✦ 密封出价已递入暗箱：" + amount + " 灵石（槽末开箱，价高者得）"), false);
    }

    // ═══ 结算（纯函数，用例直测）═══

    /** 结算产物：新出价流水 + 追加的待领取行。 */
    public record SettleResult(
            List<AuctionBids.BidEntry> bids, List<AuctionBids.PayoutRow> payouts) {}

    /**
     * 懒结算纯函数：对 slotIndex &lt; currentSlot 的 ACTIVE 出价按槽分组判定；未到槽的出价与全部终态行原样保留。
     *
     * <p>规则：同玩家同槽只最高一笔参与（余款退）；最高价胜（同价 bidGameTime 早者胜）； 胜者 WON（拍品入池，托管已扣不再退灵石），其余 LOST（全额退款入池）。
     */
    static SettleResult settleSlots(List<AuctionBids.BidEntry> bids, long currentSlot) {
        List<AuctionBids.BidEntry> updated = new ArrayList<>(bids.size());
        List<AuctionBids.PayoutRow> payouts = new ArrayList<>();
        Map<Long, List<AuctionBids.BidEntry>> elapsed = new LinkedHashMap<>();
        for (AuctionBids.BidEntry bid : bids) {
            if (AuctionBids.BidEntry.ACTIVE.equals(bid.status()) && bid.slotIndex() < currentSlot) {
                elapsed.computeIfAbsent(bid.slotIndex(), k -> new ArrayList<>()).add(bid);
            } else {
                updated.add(bid);
            }
        }
        for (List<AuctionBids.BidEntry> slotBids : elapsed.values()) {
            // 同玩家只最高一笔参与：playerUUID → 该玩家最高出价（同额取先出的那笔）
            Map<String, AuctionBids.BidEntry> highestPerPlayer = new LinkedHashMap<>();
            List<AuctionBids.BidEntry> refunds = new ArrayList<>();
            for (AuctionBids.BidEntry bid : slotBids) {
                AuctionBids.BidEntry kept = highestPerPlayer.get(bid.bidderId());
                if (kept == null) {
                    highestPerPlayer.put(bid.bidderId(), bid);
                } else if (bid.amount() > kept.amount()
                        || (bid.amount() == kept.amount()
                                && bid.bidGameTime() < kept.bidGameTime())) {
                    highestPerPlayer.put(bid.bidderId(), bid);
                    refunds.add(kept);
                } else {
                    refunds.add(bid);
                }
            }
            // 槽内决胜负：价高者得，同价先出价者得（max 语义：同价时 bidGameTime 反序 = 早者为大）
            Comparator<AuctionBids.BidEntry> wins =
                    Comparator.comparingInt(AuctionBids.BidEntry::amount)
                            .thenComparing(
                                    Comparator.comparingLong(AuctionBids.BidEntry::bidGameTime)
                                            .reversed());
            AuctionBids.BidEntry winner =
                    highestPerPlayer.values().stream().max(wins).orElseThrow();
            for (AuctionBids.BidEntry bid : highestPerPlayer.values()) {
                if (bid == winner) {
                    updated.add(withStatus(bid, AuctionBids.BidEntry.WON));
                    payouts.add(new AuctionBids.PayoutRow(bid.bidderId(), 0, bid.lotId(), 1));
                } else {
                    updated.add(withStatus(bid, AuctionBids.BidEntry.LOST));
                    payouts.add(new AuctionBids.PayoutRow(bid.bidderId(), bid.amount(), "", 0));
                }
            }
            for (AuctionBids.BidEntry bid : refunds) {
                updated.add(withStatus(bid, AuctionBids.BidEntry.LOST));
                payouts.add(new AuctionBids.PayoutRow(bid.bidderId(), bid.amount(), "", 0));
            }
        }
        return new SettleResult(List.copyOf(updated), List.copyOf(payouts));
    }

    private static AuctionBids.BidEntry withStatus(AuctionBids.BidEntry bid, String status) {
        return new AuctionBids.BidEntry(
                bid.slotIndex(),
                bid.bidderId(),
                bid.bidderName(),
                bid.lotId(),
                bid.amount(),
                bid.bidGameTime(),
                status);
    }

    /** 懒结算入口：任何 auction 交互先调。状态无变化时不重写附件。 */
    public static void settleAndPersist(MinecraftServer server) {
        AuctionBids state = state(server);
        SettleResult result = settleSlots(state.bids(), currentSlot(server));
        if (result.payouts().isEmpty()) {
            return;
        }
        List<AuctionBids.PayoutRow> payouts = new ArrayList<>(state.payouts());
        payouts.addAll(result.payouts());
        save(server, new AuctionBids(result.bids(), payouts));
        notifySettled(server, result.payouts());
    }

    /**
     * 结算通知（修仙世界感配套）：暗箱开在交互瞬间，但中拍/退款的人不该靠"碰巧路过"才知道—— 在线者即时播报；离线者靠下次任何 auction 交互或 claim
     * 兜底（延迟领取池语义不变）。
     */
    private static void notifySettled(
            MinecraftServer server, List<AuctionBids.PayoutRow> freshPayouts) {
        for (AuctionBids.PayoutRow row : freshPayouts) {
            var online =
                    server.getPlayerList().getPlayer(java.util.UUID.fromString(row.playerId()));
            if (online == null) {
                continue;
            }
            String message =
                    !row.itemId().isEmpty()
                            ? "✦ 暗箱开——" + row.itemId() + " 归你！/strife auction claim 领取"
                            : "✦ 暗箱开——你的投标未中，退款 " + row.lingshi() + " 灵石入池";
            online.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(message)
                            .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE),
                    false);
        }
    }

    // ═══ 发放（待领取池 → 在线玩家）═══

    /** 领取：把该玩家的待领取行兑现（灵石/拍品入包，空间不足则留在池内）。返回发放摘要文案（null=无可领）。 */
    public static String dispense(ServerPlayer player) {
        settleAndPersist(player.server);
        AuctionBids state = state(player.server);
        String self = player.getStringUUID();
        List<AuctionBids.PayoutRow> remaining = new ArrayList<>();
        int lingshiPaid = 0;
        int itemsGiven = 0;
        for (AuctionBids.PayoutRow row : state.payouts()) {
            if (!row.playerId().equals(self)) {
                remaining.add(row);
                continue;
            }
            boolean paid = false;
            if (row.lingshi() > 0) {
                Item lingshi = InventoryOps.resolve(LINGSHI).orElse(null);
                paid =
                        lingshi != null
                                && player.getInventory().add(new ItemStack(lingshi, row.lingshi()));
                if (paid) {
                    lingshiPaid += row.lingshi();
                }
            } else if (!row.itemId().isEmpty()) {
                Item item = InventoryOps.resolve(row.itemId()).orElse(null);
                paid =
                        item != null
                                && player.getInventory().add(new ItemStack(item, row.itemCount()));
                if (paid) {
                    itemsGiven += row.itemCount();
                }
            }
            if (!paid) {
                remaining.add(row);
            }
        }
        if (lingshiPaid == 0 && itemsGiven == 0) {
            return null;
        }
        save(player.server, new AuctionBids(state.bids(), remaining));
        StringBuilder message = new StringBuilder("✦ 暗箱开启：");
        if (itemsGiven > 0) {
            message.append("拍品到手 ×").append(itemsGiven);
        }
        if (lingshiPaid > 0) {
            if (itemsGiven > 0) {
                message.append("，");
            }
            message.append("退款 ").append(lingshiPaid).append(" 灵石");
        }
        return message.toString();
    }
}

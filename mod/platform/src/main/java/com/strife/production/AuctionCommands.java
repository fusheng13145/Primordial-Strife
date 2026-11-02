package com.strife.production;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.strife.core.AuctionBids;
import com.strife.core.InventoryOps;
import com.strife.core.StrifeAttachmentTypes;
import com.strife.core.StrifeData;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 拍卖行命令（EP1，ADR-037 ① + ADR-041 第三批）：荷兰拍 {@code list|buy}（急售当场成交）+ 密封投标 {@code
 * bid|bids|claim}（槽末开箱，价高者得）。 任何 auction 交互先懒结算已过槽的出价（{@link AuctionBidding#settleAndPersist}）。
 */
public final class AuctionCommands {

    private AuctionCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> subtree() {
        return Commands.literal("auction")
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(
                        Commands.literal("buy")
                                .then(
                                        Commands.argument("拍品", StringArgumentType.string())
                                                .executes(
                                                        ctx ->
                                                                buy(
                                                                        ctx.getSource(),
                                                                        StringArgumentType
                                                                                .getString(
                                                                                        ctx,
                                                                                        "拍品")))))
                .then(
                        Commands.literal("bid")
                                .then(
                                        Commands.argument("拍品", StringArgumentType.string())
                                                .then(
                                                        Commands.argument(
                                                                        "出价",
                                                                        IntegerArgumentType.integer(
                                                                                1))
                                                                .executes(
                                                                        ctx ->
                                                                                bid(
                                                                                        ctx
                                                                                                .getSource(),
                                                                                        StringArgumentType
                                                                                                .getString(
                                                                                                        ctx,
                                                                                                        "拍品"),
                                                                                        IntegerArgumentType
                                                                                                .getInteger(
                                                                                                        ctx,
                                                                                                        "出价"))))))
                .then(Commands.literal("bids").executes(ctx -> myBids(ctx.getSource())))
                .then(Commands.literal("claim").executes(ctx -> claim(ctx.getSource())));
    }

    private static int list(CommandSourceStack source) {
        if (source.getPlayer() != null) {
            // 懒结算（ADR-041）：槽末的密封投标在任何交互时开箱
            AuctionBidding.settleAndPersist(source.getServer());
        }
        long gameTime = source.getLevel().getGameTime();
        StringBuilder sb = new StringBuilder("拍场（荷兰拍急售 + 密封投标，每槽 2 时辰轮换）：");
        for (AuctionHouse.Lot lot : AuctionHouse.lots().values()) {
            boolean current = lot.equals(AuctionHouse.currentLot(gameTime));
            int price =
                    AuctionHouse.currentPrice(
                            lot, AuctionHouse.elapsedInSlot(gameTime), AuctionHouse.slotTicks());
            sb.append('\n')
                    .append(current ? "▶ " : "  ")
                    .append(lot.itemId())
                    .append(" 现价 ")
                    .append(price)
                    .append("（起 ")
                    .append(lot.start())
                    .append(" 底 ")
                    .append(lot.min())
                    .append("，每时辰 −")
                    .append(lot.drop())
                    .append('）');
        }
        long elapsed = AuctionHouse.elapsedInSlot(gameTime);
        sb.append('\n')
                .append("本槽剩余 ")
                .append(AuctionHouse.remainingShichen(elapsed))
                .append(" 时辰");
        source.sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int buy(CommandSourceStack source, String lotId) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        AuctionBidding.settleAndPersist(player.server);
        long gameTime = player.level().getGameTime();
        AuctionHouse.Lot lot = AuctionHouse.lots().get(lotId);
        if (lot == null) {
            source.sendFailure(Component.literal("（拍品不存在：" + lotId + "）"));
            return 0;
        }
        if (!lot.equals(AuctionHouse.currentLot(gameTime))) {
            source.sendFailure(
                    Component.literal("（" + lotId + " 不在本槽——等轮换，/strife auction list 看当前）"));
            return 0;
        }
        int price =
                AuctionHouse.currentPrice(
                        lot, AuctionHouse.elapsedInSlot(gameTime), AuctionHouse.slotTicks());
        InventoryOps.PlayerContainers containers =
                new InventoryOps.PlayerContainers(
                        player.getInventory(), player.getEnderChestInventory());
        if (InventoryOps.countOwned(containers, "item_lingshi") < price) {
            player.displayClientMessage(Component.literal("（灵石不足：需 " + price + "——等降价也是策略）"), true);
            return 0;
        }
        long removed = InventoryOps.remove(containers, "item_lingshi", price);
        if (removed < price) {
            player.displayClientMessage(Component.literal("（结算异常已回滚）"), true);
            return 0;
        }
        Item item = InventoryOps.resolve(lot.itemId()).orElse(null);
        if (item == null) {
            player.getInventory().add(new ItemStack(StrifeItems.LINGSHI.get(), (int) removed));
            player.displayClientMessage(Component.literal("（拍品暂未到岸，已退灵石）"), true);
            return 0;
        }
        player.getInventory().add(new ItemStack(item, 1));
        StrifeData data = player.getData(StrifeAttachmentTypes.PLAYER_DATA);
        Map<String, Integer> reputation = new HashMap<>(data.reputation());
        reputation.merge("fac_yuelai", 2, Integer::sum);
        player.setData(
                StrifeAttachmentTypes.PLAYER_DATA, data.withReputation(Map.copyOf(reputation)));
        player.displayClientMessage(
                Component.literal("✦ 落槌！" + lot.itemId() + " 以 " + price + " 灵石归你"), true);
        return 1;
    }

    // ═══ 密封投标（ADR-041 第三批）═══

    private static int bid(CommandSourceStack source, String lotId, int amount)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        AuctionBidding.bid(player, lotId, amount);
        return 1;
    }

    /** 我的出价 + 待领取池：懒结算后按个人视图展示。 */
    private static int myBids(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        AuctionBidding.settleAndPersist(player.server);
        AuctionBids state = AuctionBidding.state(player.server);
        String self = player.getStringUUID();
        StringBuilder sb = new StringBuilder("你的密封投标：");
        boolean anyBid = false;
        for (AuctionBids.BidEntry entry : state.bids()) {
            if (!entry.bidderId().equals(self)) {
                continue;
            }
            anyBid = true;
            sb.append("\n· ")
                    .append(entry.lotId())
                    .append(' ')
                    .append(entry.amount())
                    .append(" 灵石");
            if (AuctionBids.BidEntry.ACTIVE.equals(entry.status())) {
                sb.append("（竞标中）");
            } else if (AuctionBids.BidEntry.WON.equals(entry.status())) {
                sb.append("（已中拍——/strife auction claim 领取）");
            } else {
                sb.append("（未中，退款入池）");
            }
        }
        if (!anyBid) {
            sb.append("还没有出过价（/strife auction bid <拍品> <价格>）");
        }
        int pending = 0;
        for (AuctionBids.PayoutRow row : state.payouts()) {
            if (row.playerId().equals(self)) {
                pending++;
            }
        }
        if (pending > 0) {
            sb.append("\n待领取 ").append(pending).append(" 笔——/strife auction claim");
        }
        source.sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int claim(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        String message = AuctionBidding.dispense(player);
        if (message == null) {
            player.displayClientMessage(Component.literal("（暗箱里没有你的东西）"), true);
            return 0;
        }
        player.displayClientMessage(Component.literal(message), false);
        return 1;
    }
}

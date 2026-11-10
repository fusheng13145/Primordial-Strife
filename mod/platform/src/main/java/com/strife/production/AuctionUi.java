package com.strife.production;

import com.strife.core.AuctionBids;
import com.strife.core.net.StrifeNetwork;
import java.util.ArrayList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 拍卖看板的数据构建（StrifeUiBridge 第三屏）：荷兰拍当前价由游戏时间确定性推导（零持久化）， 看板数据随开随算——与炉/坊市屏同一纪律（服务端算好，客户端只渲染）。
 *
 * <p>tag schema（production 自钉，{@code AuctionUiTest} 锚定）：
 *
 * <pre>
 * root: {
 *   lots: [ { lot, item, price, min, remain_shichen, current } ]   # 表序；current=本槽拍品
 *   my_bids: [ { lot, amount, status } ]                            # 我的密封投标（全量，含终态）
 *   payouts: int                                                    # 待领取行数
 * }
 * </pre>
 *
 * <p>密封投标的开箱价不由看板展示（暗箱语义——其他人的出价不外显，自己的也只看到状态）。
 */
public final class AuctionUi {

    private AuctionUi() {}

    /** 右键拍卖看板：组数据并下发。 */
    public static void openFor(ServerPlayer player) {
        var containers =
                new com.strife.core.InventoryOps.PlayerContainers(
                        player.getInventory(), player.getEnderChestInventory());
        StrifeNetwork.sendTo(
                player,
                new com.strife.core.net.StrifeUiPayload(
                        com.strife.core.StrifeUiBridge.Keys.AUCTION_SCREEN,
                        build(player.server, player.getUUID(), player.level().getGameTime())));
    }

    /** 看板数据：目录段纯时间推导 + 玩家段读投标状态（openFor 组装，测试切片直测）。 */
    static CompoundTag build(MinecraftServer server, java.util.UUID playerId, long gameTime) {
        CompoundTag root = new CompoundTag();
        root.put(
                com.strife.core.StrifeUiBridge.Keys.LOTS,
                catalog(gameTime, AuctionHouse.currentLot(gameTime).id()));
        appendPlayerSection(root, AuctionBidding.state(server), playerId);
        return root;
    }

    /** 玩家段切片（纯函数，供用例直测）：镜像自己的投标 + 待领取行数。 */
    static CompoundTag playerSection(AuctionBids state, java.util.UUID self) {
        CompoundTag section = new CompoundTag();
        appendPlayerSection(section, state, self);
        return section;
    }

    /** 目录段（纯函数，供用例）：全表序拍品，本槽标 current。 */
    static ListTag catalog(long gameTime, String currentLotId) {
        long slotTicks = AuctionHouse.slotTicks();
        ListTag rows = new ListTag();
        for (AuctionHouse.Lot lot : AuctionHouse.lots().values()) {
            CompoundTag row = new CompoundTag();
            row.putString(com.strife.core.StrifeUiBridge.Keys.LOT, lot.id());
            row.putString(com.strife.core.StrifeUiBridge.Keys.ITEM, lot.itemId());
            row.putInt(
                    com.strife.core.StrifeUiBridge.Keys.PRICE,
                    AuctionHouse.currentPrice(
                            lot, AuctionHouse.elapsedInSlot(gameTime), slotTicks));
            row.putInt(com.strife.core.StrifeUiBridge.Keys.MIN, lot.min());
            row.putLong(
                    com.strife.core.StrifeUiBridge.Keys.REMAIN,
                    AuctionHouse.remainingShichen(AuctionHouse.elapsedInSlot(gameTime)));
            row.putBoolean(
                    com.strife.core.StrifeUiBridge.Keys.CURRENT, lot.id().equals(currentLotId));
            rows.add(row);
        }
        return rows;
    }

    /** 玩家段：我的密封投标（流水全量）+ 待领取行数。 */
    private static void appendPlayerSection(
            CompoundTag root, AuctionBids state, java.util.UUID playerId) {
        String self = playerId.toString();
        ListTag bids = new ListTag();
        for (AuctionBids.BidEntry entry : state.bids()) {
            if (!entry.bidderId().equals(self)) {
                continue;
            }
            CompoundTag row = new CompoundTag();
            row.putString(com.strife.core.StrifeUiBridge.Keys.LOT, entry.lotId());
            row.putInt(com.strife.core.StrifeUiBridge.Keys.AMOUNT, entry.amount());
            row.putString(com.strife.core.StrifeUiBridge.Keys.STATUS, entry.status());
            bids.add(row);
        }
        root.put(com.strife.core.StrifeUiBridge.Keys.MY_BIDS, bids);
        int pending = 0;
        for (AuctionBids.PayoutRow row : state.payouts()) {
            if (row.playerId().equals(self)) {
                pending++;
            }
        }
        root.putInt(com.strife.core.StrifeUiBridge.Keys.PAYOUTS, pending);
    }

    /** 行取数辅助（clientfx 只碰 tag）。 */
    public static java.util.List<Tag> tags(CompoundTag root, String key) {
        ListTag list = root.getList(key, Tag.TAG_COMPOUND);
        java.util.List<Tag> out = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            out.add(list.get(i));
        }
        return out;
    }
}

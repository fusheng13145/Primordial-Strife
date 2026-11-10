package com.strife.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.strife.core.AuctionBids;
import com.strife.core.StrifeUiBridge;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

/** 拍卖看板数据形状 ×【真实拍卖产物】：目录段纯时间推导（同一时刻永远同一价——荷兰拍零持久化承诺的屏上回声）、 玩家段只镜像自己的投标。 */
class AuctionUiTest {

    /** 目录段：行数 = 拍品数、恰一个 current、价格在 [min, start] 区间、剩余时辰非负。 */
    @Test
    void catalogRowsAreDeterministicAndExactlyOneCurrent() {
        var lots = AuctionHouse.lots();
        long gameTime = 97_000L;
        ListTag rows = AuctionUi.catalog(gameTime, AuctionHouse.currentLot(gameTime).id());

        assertEquals(lots.size(), rows.size());
        int currentCount = 0;
        for (var element : rows) {
            CompoundTag row = (CompoundTag) element;
            AuctionHouse.Lot lot = lots.get(row.getString(StrifeUiBridge.Keys.LOT));
            int price = row.getInt(StrifeUiBridge.Keys.PRICE);
            assertTrue(price <= lot.start() && price >= lot.min(), "荷兰价在 [底,起] 区间");
            if (row.getBoolean(StrifeUiBridge.Keys.CURRENT)) {
                currentCount++;
            }
            assertTrue(row.getLong(StrifeUiBridge.Keys.REMAIN) >= 0);
        }
        assertEquals(1, currentCount, "恰一个本槽拍品（轮换唯一性）");
    }

    /** 同一 gameTime 两次取目录 = 完全一致（荷兰拍确定性在看板上的回声）。 */
    @Test
    void catalogIsDeterministicForTheSameGameTime() {
        long gameTime = 123_456L;
        String current = AuctionHouse.currentLot(gameTime).id();
        assertEquals(AuctionUi.catalog(gameTime, current), AuctionUi.catalog(gameTime, current));
    }

    /** 玩家段：只镜像自己的投标（他人出价不外显——暗箱语义），待领取计数一并进段。 */
    @Test
    void playerSectionMirrorsOnlyOwnBids() {
        UUID self = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        var state =
                new AuctionBids(
                        List.of(
                                new AuctionBids.BidEntry(
                                        0,
                                        self.toString(),
                                        "甲",
                                        "auction_xuanyu",
                                        15,
                                        100L,
                                        AuctionBids.BidEntry.ACTIVE),
                                new AuctionBids.BidEntry(
                                        0,
                                        other.toString(),
                                        "乙",
                                        "auction_xuanyu",
                                        18,
                                        200L,
                                        AuctionBids.BidEntry.ACTIVE)),
                        List.of(new AuctionBids.PayoutRow(self.toString(), 12, "", 0)));

        CompoundTag section = AuctionUi.playerSection(state, self);

        var bids = section.getList(StrifeUiBridge.Keys.MY_BIDS, net.minecraft.nbt.Tag.TAG_COMPOUND);
        assertEquals(1, bids.size(), "他人出价不进我的段（暗箱）");
        CompoundTag mine = (CompoundTag) bids.get(0);
        assertEquals(15, mine.getInt(StrifeUiBridge.Keys.AMOUNT));
        assertEquals("active", mine.getString(StrifeUiBridge.Keys.STATUS));
        assertEquals(1, section.getInt(StrifeUiBridge.Keys.PAYOUTS), "待领取计数");
    }

    /** PlayerKeys 常量与 clientfx 解析同源。 */
    @Test
    void playerKeysAreStableContractValues() {
        assertEquals("payouts", StrifeUiBridge.Keys.PAYOUTS);
        assertEquals("auction", StrifeUiBridge.Keys.AUCTION_SCREEN);
    }
}

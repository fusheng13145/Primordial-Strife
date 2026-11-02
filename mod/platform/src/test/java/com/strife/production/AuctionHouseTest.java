package com.strife.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 拍卖行定价核（EP1，ADR-037 ①）×【真实产物】：荷兰拍确定性、触底钳制、轮换。
 *
 * <p>核心契约：**当前价由 (gameTime, 产物) 确定性推导**——同样的时间永远同样的价， 零持久化状态。slot_shichen=2 → 每槽 24000 刻，每时辰（12000
 * 刻）降一次。
 */
class AuctionHouseTest {

    private static final int SLOT_TICKS = 24000;

    @Test
    void priceDropsPerShichenAndClampsAtFloor() {
        var lot = AuctionHouse.lots().get("auction_xuanyu");
        assertEquals(20, lot.start());
        assertEquals(10, lot.min());
        assertEquals(2, lot.drop());

        assertEquals(20, AuctionHouse.currentPrice(lot, 0, SLOT_TICKS), "槽首 = 起拍价");
        assertEquals(18, AuctionHouse.currentPrice(lot, 12000, SLOT_TICKS), "1 时辰后 −2");
        assertEquals(16, AuctionHouse.currentPrice(lot, 24001, SLOT_TICKS), "跨槽沿 24000 取整");
        assertEquals(12, AuctionHouse.currentPrice(lot, 48000, SLOT_TICKS), "4 时辰 = 20−8");
        assertEquals(10, AuctionHouse.currentPrice(lot, 60000, SLOT_TICKS), "5 时辰触底 = 底价");
        assertEquals(10, AuctionHouse.currentPrice(lot, 999999, SLOT_TICKS), "永不低于底价");
    }

    @Test
    void lotsRotateByGameTime() {
        long t0 = 0;
        var first = AuctionHouse.currentLot(t0);
        var second = AuctionHouse.currentLot(t0 + AuctionHouse.slotTicks());
        var wrapped =
                AuctionHouse.currentLot(t0 + AuctionHouse.slotTicks() * AuctionHouse.lots().size());
        assertTrue(!first.equals(second), "槽位推进必须换拍品");
        assertEquals(first, wrapped, "拍品按表序循环（零状态轮换）");
    }

    @Test
    void allShippedLotsAreWellFormed() {
        assertTrue(AuctionHouse.lots().size() >= 3, "至少 3 件拍品（初版）");
        for (var lot : AuctionHouse.lots().values()) {
            assertTrue(lot.min() < lot.start());
            assertTrue(lot.drop() >= 1);
            // 拍品可为黑市/拍卖专属品（明市无价是合法的——EP2 稀有资源获取路径）
        }
        assertEquals(2, AuctionHouse.slotTicks() / 12000, "slot_shichen=2（NUMBERS @@auction）");
    }
}

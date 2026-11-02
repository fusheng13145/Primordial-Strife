package com.strife.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 黑市价目 ×【真实产物】（EP1，ADR-036 ②）。
 *
 * <p>核心契约：赃货折扣（同物黑市价 < 明市买入价）+ 黑市只卖不收。
 */
class BlackMarketTest {

    @Test
    void shadowPricesAreDiscountsBelowOpenBuyPrice() {
        // 同物对比：黑市价必须 < 明市买入价（赃货折扣语义）。
        for (var e : BlackMarketCommands.snapshot().entrySet()) {
            var openQuote = MarketPrices.quote(e.getKey());
            // 黑市专属品（无明市价）允许存在——异火/稀有材料走黑市限定获取路径
            if (openQuote.isPresent()) {
                assertTrue(
                        e.getValue() < openQuote.get().buyLingshi(),
                        e.getKey()
                                + " 黑市价 "
                                + e.getValue()
                                + " 必须 < 明市买 "
                                + openQuote.get().buyLingshi());
            }
        }
        assertTrue(BlackMarketCommands.snapshot().size() >= 3, "黑市至少 3 件货（E 批初版）");
    }

    @Test
    void currencyIsNeverTraded() {
        assertFalse(BlackMarketCommands.snapshot().containsKey("item_lingshi"), "货币不入黑市");
        assertEquals(java.util.Optional.empty(), BlackMarketCommands.price("item_lingshi"));
    }

    @Test
    void snapshotIsStableAcrossLoads() {
        assertEquals(BlackMarketCommands.snapshot(), BlackMarketCommands.snapshot());
    }
}

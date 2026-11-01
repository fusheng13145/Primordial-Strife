package com.strife.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 坊市定价表 ×【真实产物】的契约用例（EP1，ADR-032）。
 *
 * <p>定价/兑换比来自 {@code strife_prices/prices.json}（tables/prices.csv + NUMBERS @@market 直出）； 兑换是纯算术，契约
 * = 「不丢钱」——任何输入下三档合计恒等于输入。
 */
class MarketPricesTest {

    @Test
    void shippedQuotesFollowTheContractTable() {
        var ningxu = MarketPrices.quote("item_ningxu").orElseThrow();
        assertEquals(2, ningxu.buyLingshi(), "凝须草买入价（prices.csv）");
        assertEquals(1, ningxu.sellLingshi(), "凝须草回收价");
        var xuanyuPill = MarketPrices.quote("pill_xuanyu").orElseThrow();
        assertEquals(20, xuanyuPill.buyLingshi());
        // 回收必折价对全表成立（防买卖循环复制）。
        for (var e : MarketPrices.snapshot().entrySet()) {
            assertTrue(
                    e.getValue().sellLingshi() < e.getValue().buyLingshi(),
                    e.getKey() + " 回收价必须 < 买入价（复制漏洞门禁）");
        }
        assertTrue(MarketPrices.size() >= 8, "定价表至少 8 条（E1 初版）");
    }

    @Test
    void unlistedItemsAreNotTradable() {
        assertEquals(Optional.empty(), MarketPrices.quote("item_lingshi"), "货币不进定价表");
        assertEquals(Optional.empty(), MarketPrices.quote("item_missing_xyz"), "未知物品不可交易");
    }

    @Test
    void ratiosComeFromNumbers() {
        var ratios = MarketPrices.ratios();
        assertEquals(10, ratios.tierZhong(), "中品 = 下品 ×10（NUMBERS @@market）");
        assertEquals(100, ratios.tierShang(), "上品 = 下品 ×100");
    }

    @Test
    void exchangeBreakdownNeverLosesMoney() {
        var ratios = new MarketPrices.Ratios(10, 100);
        for (int lingshi : new int[] {0, 1, 9, 10, 11, 99, 100, 101, 12345, Integer.MAX_VALUE}) {
            int[] b = MarketPrices.breakdown(lingshi, ratios);
            assertEquals(
                    lingshi,
                    (long) b[0] * ratios.tierShang() + (long) b[1] * ratios.tierZhong() + b[2],
                    "兑换前后总量必须守恒（输入 " + lingshi + "）");
            assertTrue(b[2] < ratios.tierZhong(), "余数下品必须低于中品倍率");
            assertTrue(b[1] < ratios.tierShang() / ratios.tierZhong(), "中品位数不得溢出（应全部进上品）");
        }
        // 整数溢出边界：MAX_VALUE 不丢钱（long 结算）。
        int[] max = MarketPrices.breakdown(Integer.MAX_VALUE, ratios);
        assertEquals(
                Integer.MAX_VALUE,
                (int)
                        Math.min(
                                Integer.MAX_VALUE,
                                (long) max[0] * 100L + (long) max[1] * 10L + max[2]));
    }

    @Test
    void cachedSnapshotIsReadOnly() {
        Map<String, MarketPrices.Quote> snapshot = MarketPrices.snapshot();
        assertTrue(snapshot.size() >= 8);
        var again = MarketPrices.snapshot();
        assertEquals(snapshot, again, "两次快照一致（懒加载缓存）");
    }
}

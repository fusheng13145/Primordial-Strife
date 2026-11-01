package com.strife.realm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 闭关结算纯数学（EP1，ADR-034）。
 *
 * <p>核心契约：**闭关 = 打坐结算的时间重放**——收益必须与 {@code MeditationMath.earnedSince} 逐刻口径一致（同速率同收益），寿元代价 tick
 * 精确，拒绝理由按优先级（打坐中 &gt; 不安全 &gt; 时辰越界）。
 */
class SeclusionMathTest {

    private static final long TICKS_PER_YEAR = 24000L;

    @Test
    void earnedQiMatchesPerTickMeditationReplay() {
        // 0.5 修为/刻 × 12000 刻（1 时辰）= 6000；与 MeditationMath 逐刻口径同源。
        assertEquals(6000, SeclusionMath.earnedQi(0.5, 12000));
        assertEquals(
                MeditationMath.earnedSince(0, 12000, 0.5),
                SeclusionMath.earnedQi(0.5, 12000),
                "闭关收益必须等于打坐重放——口径分叉就是隐性双通道");
        // 12 时辰 = 144000 刻 × 1.25 修为/刻 = 180000。
        assertEquals(180000, SeclusionMath.earnedQi(1.25, 144000));
    }

    @Test
    void lifespanCostIsTickExactAndDisplayConverts() {
        assertEquals(12000L, SeclusionMath.lifespanTicks(12000), "1 时辰 = 12000 刻代价");
        assertEquals("6.0", SeclusionMath.lifespanYears(144000, TICKS_PER_YEAR), "12 时辰 = 6 修行年");
        assertEquals("0.5", SeclusionMath.lifespanYears(12000, TICKS_PER_YEAR), "半位小数展示");
    }

    @Test
    void rejectionReasonsInPriorityOrder() {
        // 1. 打坐中优先于一切（两套结算互斥）。
        assertEquals("（先出定再闭关——两套结算不能同时走）", SeclusionMath.rejectionReason(true, 3, 1, 12));
        // 2. 不安全次之（报数量）。
        assertTrue(SeclusionMath.rejectionReason(false, 2, 1, 12).contains("2 只敌对生物"));
        // 3. 时辰越界最后（上下界都点名）。
        assertTrue(SeclusionMath.rejectionReason(false, 0, 0, 12).contains("1..12"));
        assertTrue(SeclusionMath.rejectionReason(false, 0, 13, 12).contains("1..12"));
        // 全部通过 = null（可以入定）。
        assertNull(SeclusionMath.rejectionReason(false, 0, 6, 12));
    }
}

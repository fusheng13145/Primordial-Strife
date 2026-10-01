package com.strife.realm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 大限结算口径（docs/05 §4、ADR-008 非破坏性；NUMBERS {@code @@lifespan}）。 */
class DashengMathTest {

    /** NUMBERS §1/@lifespan：回退 1 档、残余寿元比例 0.05、1 修行年 = 24000 刻。 */
    private static final int DROP_STAGES = 1;

    private static final double RESET_RATIO = 0.05;
    private static final long TICKS_PER_YEAR = 24000L;

    @Test
    void demotesByTheConfiguredNumberOfStages() {
        DashengMath.Outcome outcome =
                DashengMath.settle(4, DROP_STAGES, 400, RESET_RATIO, TICKS_PER_YEAR);

        assertEquals(3, outcome.newOrdinal(), "金丹(3) ← 元婴(4) 回退一档");
        assertTrue(outcome.demoted());
        assertEquals(0, outcome.qi(), "修为归零重来，不保留旧境界进度");
    }

    @Test
    void neverDemotesBelowMortalAndSaysSo() {
        DashengMath.Outcome outcome =
                DashengMath.settle(0, DROP_STAGES, 80, RESET_RATIO, TICKS_PER_YEAR);

        assertEquals(0, outcome.newOrdinal(), "已在凡人：不删角色、不出现负境界");
        assertFalse(outcome.demoted(), "没有退档就不能对外宣称退档（面板文案据此分支）");
        assertEquals(80L * 24000L, outcome.lifespanTicks(), "退无可退时寿元仍按凡人年限重置");
    }

    @Test
    void residualLifespanIsAShareOfTheNewRealmAndNeverZero() {
        DashengMath.Outcome outcome = DashengMath.settle(4, 1, 800, RESET_RATIO, TICKS_PER_YEAR);

        assertEquals(
                (long) Math.floor(800 * RESET_RATIO) * TICKS_PER_YEAR, outcome.lifespanTicks());
    }

    @Test
    void atLeastOneYearIsAlwaysGranted() {
        // 比例或年限被调到极小值时，0 年残余会让下一个 tick 立刻再次触发大限，形成死亡螺旋。
        DashengMath.Outcome outcome = DashengMath.settle(4, 1, 10, 0.01, TICKS_PER_YEAR);

        assertEquals(1L * TICKS_PER_YEAR, outcome.lifespanTicks());
    }

    @Test
    void nonsenseInputsDoNotProduceNegativeState() {
        DashengMath.Outcome outcome = DashengMath.settle(-5, -3, 80, -1.0, 0L);

        assertEquals(0, outcome.newOrdinal());
        assertEquals(0, outcome.qi());
        assertTrue(outcome.lifespanTicks() > 0L, "换算率缺失也要给正寿元，不能是 0");
    }
}

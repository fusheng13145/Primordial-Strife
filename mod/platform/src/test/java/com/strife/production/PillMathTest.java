package com.strife.production;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * 丹药数值口径（docs/05 §7 NUMBERS {@code @@pills}）。
 *
 * <p>用例针对的是两条"没有它就会崩"的规则：连嗑必须有递减与封底（否则囤一背包就是无限加速），以及延寿必须按境界封顶 （否则堆延寿丹就是无限续命）。
 */
class PillMathTest {

    /** NUMBERS §7 聚气丹：+25%、300 秒、每次递减 5%、封底 10%。 */
    private static final double BONUS = 0.25;

    private static final double STEP = 0.05;
    private static final double FLOOR = 0.10;

    @Test
    void firstDoseGivesTheFullBonus() {
        assertEquals(0.25, PillMath.effectiveBonus(BONUS, STEP, FLOOR, 0), 1e-9);
    }

    @Test
    void repeatsDecayStepByStep() {
        assertEquals(0.20, PillMath.effectiveBonus(BONUS, STEP, FLOOR, 1), 1e-9);
        assertEquals(0.15, PillMath.effectiveBonus(BONUS, STEP, FLOOR, 2), 1e-9);
        assertEquals(0.10, PillMath.effectiveBonus(BONUS, STEP, FLOOR, 3), 1e-9);
    }

    @Test
    void decayStopsAtTheFloor() {
        assertEquals(0.10, PillMath.effectiveBonus(BONUS, STEP, FLOOR, 4), 1e-9);
        assertEquals(
                0.10, PillMath.effectiveBonus(BONUS, STEP, FLOOR, 100), 1e-9, "封底之后不再下降，也不该反弹");
    }

    @Test
    void maxStacksIsWhereDecayReachesTheFloor() {
        assertEquals(3, PillMath.maxStacks(BONUS, STEP, FLOOR), "0.25→0.10 需要 3 次递减");
        assertEquals(2, PillMath.maxStacks(0.20, 0.05, 0.10), 1e-9);
        assertEquals(0, PillMath.maxStacks(0.25, 0.0, 0.10), "没有递减规则就不该有重复额度");
    }

    @Test
    void lifespanGainIsCappedPerRealm() {
        assertEquals(30, PillMath.remainingLifespanGain(30, 0));
        assertEquals(20, PillMath.remainingLifespanGain(30, 10));
        assertEquals(0, PillMath.remainingLifespanGain(30, 30));
        assertEquals(0, PillMath.remainingLifespanGain(30, 99), "超发过也不得出现负的余额");
    }

    @Test
    void grantedYearsIsTheSmallerOfTheDoseAndTheRemainingRoom() {
        assertEquals(10, PillMath.grantedLifespanYears(10, 30));
        assertEquals(5, PillMath.grantedLifespanYears(10, 5), "余额只剩 5 年就只给 5 年");
        assertEquals(0, PillMath.grantedLifespanYears(10, 0));
        assertEquals(0, PillMath.grantedLifespanYears(-3, 30), "负数丹方不该倒扣寿元");
    }
}

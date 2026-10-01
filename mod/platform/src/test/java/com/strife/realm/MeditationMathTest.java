package com.strife.realm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 打坐结算口径（docs/03 §3 时间片结算；NUMBERS §5 {@code interrupt_progress_keep} / {@code
 * tick_interval_ticks}）。
 *
 * <p>用例的重点不是"能算"，而是几个只有对拍才会发现的边界：零头不许每片蒸发、时钟回退不许倒扣、撞上限必须显式标记。
 */
class MeditationMathTest {

    /** NUMBERS §1 练气 sit_rate 0.80/秒 ÷ 20 刻 = 0.04 修为/刻。 */
    private static final double QI_PER_TICK = 0.04;

    @Test
    void noEarningsBeforeTheSessionStarts() {
        assertEquals(0, MeditationMath.earnedSince(100L, 100L, QI_PER_TICK));
        assertEquals(0, MeditationMath.earnedSince(-1L, 500L, QI_PER_TICK));
        assertEquals(0, MeditationMath.earnedSince(200L, 100L, QI_PER_TICK), "时钟回退不得倒扣");
    }

    @Test
    void earningsAreLinearInElapsedTicks() {
        assertEquals(4, MeditationMath.earnedSince(0L, 100L, QI_PER_TICK));
        assertEquals(40, MeditationMath.earnedSince(0L, 1000L, QI_PER_TICK));
        assertEquals(1, MeditationMath.earnedSince(0L, 25L, QI_PER_TICK), "25 刻 = 1.0 修为");
        assertEquals(0, MeditationMath.earnedSince(0L, 24L, QI_PER_TICK), "24 刻 = 0.96，向下取整为 0");
    }

    /** 回归：按"片数 × 每片收益"记账时，每片都会向下取整（40 刻 × 0.04 = 1.6 → 记 1），100 片只剩 100 修为。 按累计量记账应得 160。 */
    @Test
    void fractionalRemaindersDoNotEvaporatePerSlice() {
        long hundredSlices = 100L * 40L;

        assertEquals(160, MeditationMath.earnedSince(0L, hundredSlices, QI_PER_TICK));
    }

    @Test
    void interruptKeepsTheConfiguredShareOfTheWholeSession() {
        // 1000 刻应得 40；保留 90% = 36。
        assertEquals(36, MeditationMath.earnedAfterInterrupt(0L, 1000L, QI_PER_TICK, 0.90));
        assertEquals(40, MeditationMath.earnedAfterInterrupt(0L, 1000L, QI_PER_TICK, 1.0));
        assertEquals(0, MeditationMath.earnedAfterInterrupt(0L, 1000L, QI_PER_TICK, 0.0));
    }

    @Test
    void settlementOnlyPaysTheUnpaidDifference() {
        assertEquals(40, MeditationMath.settlementDelta(40, 0));
        assertEquals(0, MeditationMath.settlementDelta(40, 40), "已发满不得重复发");
        assertEquals(-4, MeditationMath.settlementDelta(36, 40), "打断惩罚允许收回已发的部分");
    }

    @Test
    void settlementIsClampedAtTheRealmCeiling() {
        MeditationMath.Applied applied = MeditationMath.apply(220, 40, 230);

        assertEquals(230, applied.qi());
        assertEquals(10, applied.applied());
        assertTrue(applied.capped(), "撞上限必须显式标记——调用方据此结束会话，否则记账与实发会永久错位");
    }

    @Test
    void settlementBelowTheCeilingIsNotMarkedAsCapped() {
        MeditationMath.Applied applied = MeditationMath.apply(100, 40, 230);

        assertEquals(140, applied.qi());
        assertEquals(40, applied.applied());
        assertFalse(applied.capped());
    }

    @Test
    void clawbackNeverDrivesQiBelowZero() {
        MeditationMath.Applied applied = MeditationMath.apply(3, -10, 230);

        assertEquals(0, applied.qi());
        assertEquals(-3, applied.applied());
    }
}

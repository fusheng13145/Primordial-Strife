package com.strife.realm;

/**
 * 大限结算的纯数学（docs/05 §4、ADR-008"非破坏性代价"）。
 *
 * <p>三条硬口径都落在这里，且都能用用例直接钉住：
 *
 * <ol>
 *   <li><b>绝不删角色</b>：境界只回退、不低于凡人，修为归零但不为负；
 *   <li><b>回退档数来自表</b>（NUMBERS {@code @@lifespan.dasheng_realm_drop_stages}），已在凡人时不再退档，寿元照旧重置；
 *   <li><b>残余寿元</b> = 新境界年限 × {@code dasheng_reset_years_ratio}，至少 1 年——0 年会立刻再次触发大限，形成死亡螺旋。
 * </ol>
 */
public final class DashengMath {

    /**
     * @param newOrdinal 回退后的境界序号（不低于 0）
     * @param demoted 是否真的退了档（已在凡人时为 false，但寿元仍重置）
     * @param qi 回退后的修为（新境界的起点：0）
     * @param lifespanTicks 残余寿元
     */
    public record Outcome(int newOrdinal, boolean demoted, int qi, long lifespanTicks) {}

    private DashengMath() {}

    /**
     * @param currentOrdinal 当前境界序号
     * @param stagesToDrop NUMBERS {@code dasheng_realm_drop_stages}
     * @param lifespanYearsOfNewRealm 回退后境界的年限（NUMBERS §1 {@code lifespan_years}）
     * @param resetYearsRatio NUMBERS {@code dasheng_reset_years_ratio}
     * @param ticksPerYear 1 修行年的刻数（core 受管产物 {@code derived.ticks_per_year}）
     */
    public static Outcome settle(
            int currentOrdinal,
            int stagesToDrop,
            int lifespanYearsOfNewRealm,
            double resetYearsRatio,
            long ticksPerYear) {
        int drop = Math.max(0, stagesToDrop);
        int newOrdinal = Math.max(0, currentOrdinal - drop);
        boolean demoted = newOrdinal < currentOrdinal;
        long years =
                Math.max(
                        1L,
                        (long) Math.floor(lifespanYearsOfNewRealm * clampRatio(resetYearsRatio)));
        return new Outcome(newOrdinal, demoted, 0, years * Math.max(1L, ticksPerYear));
    }

    private static double clampRatio(double ratio) {
        return Math.max(0.0, Math.min(1.0, ratio));
    }
}

package com.strife.production;

/**
 * 丹药增益的纯数学（docs/05 §7 数值块：{@code qi_rate_bonus} / {@code repeat_step} / {@code repeat_floor}）。
 *
 * <p>两件事在这里定死，用例直接钉住：
 *
 * <ol>
 *   <li><b>加重递减、有底</b>：同一种丹药在当前窗口内连续服用，加成按 {@code repeat_step} 递减、到 {@code repeat_floor} 封底——
 *       没有这条，"囤一背包聚气丹连嗑"就是无限加速；
 *   <li><b>封顶次数</b>：递减到封底之后再嗑也没意义，所以 {@link #maxStacks} 直接算出"还能嗑几次"，避免无意义地刷新持续时间 （否则玩家靠无限连嗑把 300
 *       秒刷成永久）。
 * </ol>
 */
public final class PillMath {

    private PillMath() {}

    /** 第 {@code stacks} 次服用时的实际加成（stacks = 0 为首次）。 */
    public static double effectiveBonus(
            double base, double repeatStep, double repeatFloor, int stacks) {
        int extra = Math.max(0, stacks);
        double decayed = base - Math.max(0.0, repeatStep) * extra;
        return Math.max(Math.max(0.0, repeatFloor), decayed);
    }

    /**
     * 递减到封底之前允许的重复次数（含首次）：{@code base - step × k <= floor} 的最小 k。
     *
     * <p>{@code step <= 0} 时返回 0——没有递减规则就不该有重复额度，否则"连嗑刷时长"没有上限。
     */
    public static int maxStacks(double base, double repeatStep, double repeatFloor) {
        if (!(repeatStep > 0.0)) {
            return 0;
        }
        double span = Math.max(0.0, base - Math.max(0.0, repeatFloor));
        return (int) Math.ceil(span / repeatStep);
    }

    /**
     * 本境界还能用丹药延长多少年（NUMBERS {@code max_gain_per_realm} 的记账口径）。
     *
     * <p>返回 0 = 本境界已用满；调用方应拒绝服药并给出可读原因，而不是"吃了没反应"。
     */
    public static int remainingLifespanGain(int maxGainPerRealm, int alreadyGained) {
        return Math.max(0, Math.max(0, maxGainPerRealm) - Math.max(0, alreadyGained));
    }

    /** 本次实际能延长多少年（受年限总量与单次收益的较小者约束）。 */
    public static int grantedLifespanYears(int yearsGain, int remaining) {
        return Math.max(0, Math.min(Math.max(0, yearsGain), Math.max(0, remaining)));
    }
}

package com.strife.realm;

/**
 * 打坐结算的纯数学（docs/03 §3"时间片结算：记录起止时间戳 + 环境快照，结算时一次性算"）。
 *
 * <p>把这一层单独拆出来的理由和 {@link BreakthroughMath} 一样：它是"时间 × 速率"的全部口径，出错的形态是"玩家打坐十分钟
 * 修为没涨/涨了三倍"这类只有靠对拍才能发现的偏差。抽成纯函数后，边界（刚好一片、跨多片、时钟回退、跨过上限截断）都能 用用例直接钉住。
 *
 * <p>记账口径：会话只记"自开始以来应得多少"与"已经发过多少"，差值才是本次要补的。按"已结算片数"记账会在每片做一次向下 取整（40 刻 × 0.04 修为/刻 = 1.6 → 每次记
 * 1），长期运行会凭空蒸发三成修为。
 */
public final class MeditationMath {

    private MeditationMath() {}

    /** 会话自 {@code startTick} 到 {@code nowTick} 应得的修为总量（向下取整，只在这里取一次整）。 */
    public static int earnedSince(long startTick, long nowTick, double qiPerTick) {
        if (startTick < 0L || nowTick <= startTick || qiPerTick <= 0.0) {
            return 0;
        }
        long elapsedTicks = nowTick - startTick;
        double earned = elapsedTicks * qiPerTick;
        if (earned >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.floor(earned);
    }

    /**
     * 打断时该会话最终认定的修为总量 = 应得量 × {@code keepRatio}（NUMBERS §5 {@code
     * interrupt_progress_keep}，惩罚的是本次会话的全部所得，不只是零头）。
     */
    public static int earnedAfterInterrupt(
            long startTick, long nowTick, double qiPerTick, double keepRatio) {
        int earned = earnedSince(startTick, nowTick, qiPerTick);
        double kept = earned * clampRatio(keepRatio);
        return (int) Math.floor(kept);
    }

    /** 本次结算要补发的修为 = 应得 − 已发；可为负（打断惩罚要收回已发放的部分），由调用方决定如何落账。 */
    public static int settlementDelta(int earned, int creditedQi) {
        return earned - creditedQi;
    }

    /**
     * 把补发量落到 qi 上并夹在 {@code [0, qiMax]} 之间，返回落账后的 qi 与"实际生效的量"。
     *
     * @param capped 是否因为撞到修为上限而截断——撞上限意味着这次会话不能再产出了（玩家该去突破）
     */
    public record Applied(int qi, int applied, boolean capped) {}

    public static Applied apply(int qi, int delta, int qiMax) {
        int raw = qi + delta;
        int clamped = Math.max(0, Math.min(Math.max(0, qiMax), raw));
        return new Applied(clamped, clamped - qi, delta > 0 && clamped < raw);
    }

    private static double clampRatio(double keepRatio) {
        return Math.max(0.0, Math.min(1.0, keepRatio));
    }
}

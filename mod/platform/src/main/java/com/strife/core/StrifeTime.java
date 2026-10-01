package com.strife.core;

/**
 * 刻与秒的换算（平台常量，不是设计数值——所以不进 NUMBERS）。
 *
 * <p>存在的理由：NUMBERS 里的速率类数值以"每秒"为单位（{@code sit_rate}、{@code years_per_...} 换算等），而结算发生在
 * 游戏刻上。把这个换算写成散落的 {@code / 20.0} 会让"某个地方漏乘了 20"变成一类只有对拍才能发现的偏差。
 */
public final class StrifeTime {

    /** Minecraft 固定 20 刻/秒。 */
    public static final int TICKS_PER_SECOND = 20;

    private StrifeTime() {}

    /** 秒 → 刻（四舍五入；用于把 NUMBERS 的秒级时长变成刻级冷却）。 */
    public static long secondsToTicks(double seconds) {
        return Math.round(Math.max(0.0, seconds) * TICKS_PER_SECOND);
    }

    /** 每秒速率 → 每刻速率。 */
    public static double perSecondToPerTick(double perSecond) {
        return perSecond / TICKS_PER_SECOND;
    }
}

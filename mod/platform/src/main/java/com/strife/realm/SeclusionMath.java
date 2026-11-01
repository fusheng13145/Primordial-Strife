package com.strife.realm;

/**
 * 闭关结算的纯数学（EP1，ADR-034）：时长重放 = 打坐四因子速率 × 闭关刻数，寿元同步流逝。
 *
 * <p>为什么复用 {@link MeditationMath}：闭关不是新成长通道（ADR-026 ② 修为渠道锁三），只是 打坐结算的<b>时间重放</b>——速率公式、封顶、
 * creditedQi 语义必须与逐刻打坐完全一致，否则 "闭关收益 &gt; 打坐"或反之都是口径分叉。
 */
public final class SeclusionMath {

    private SeclusionMath() {}

    /** 闭关修为收益（未封顶毛值）：与逐刻打坐共用 {@link MeditationMath#earnedSince}。 */
    public static int earnedQi(double qiPerTick, long ticks) {
        return MeditationMath.earnedSince(0L, ticks, qiPerTick);
    }

    /** 寿元代价（tick 精确）：闭关多久，寿元走多久——"山中方七日"的代价面。 */
    public static long lifespanTicks(long ticks) {
        return ticks;
    }

    /** 代价的展示口径（年，一位小数）：1 修行年 = 24000 刻（StrifeData 契约）。 */
    public static String lifespanYears(long ticks, long ticksPerYear) {
        return String.format("%.1f", ticks / (double) ticksPerYear);
    }

    /** 拒入原因（null = 可以入定）。纯函数供用例与命令共用。 */
    public static String rejectionReason(
            boolean meditating, int hostileNearby, int shichen, int maxShichen) {
        if (meditating) {
            return "（先出定再闭关——两套结算不能同时走）";
        }
        if (hostileNearby > 0) {
            return "（不安全：" + hostileNearby + " 只敌对生物在侧，闭关等于送死）";
        }
        if (shichen < 1 || shichen > maxShichen) {
            return "（时辰须在 1.." + maxShichen + " 之间）";
        }
        return null;
    }
}

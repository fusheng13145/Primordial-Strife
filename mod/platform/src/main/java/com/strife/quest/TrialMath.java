package com.strife.quest;

import net.minecraft.core.BlockPos;

/**
 * 心性试炼的纯数学（试炼台批）：坚守时长、离台判定、心魔来袭节奏——零世界访问，用例直钉。
 *
 * <p>quest_ep2_trial_will_01 的目标计数 = 120（SURVIVE 秒）。台上每秒 report 1，120 次后任务由既有完成链收束。
 */
public final class TrialMath {

    /** 坚守目标（SURVIVE 目标计数，pills 表口径一致）。 */
    public static final int TARGET_SECONDS = 120;

    /** 离台判定半径（方块）。 */
    public static final double RADIUS = 8.0;

    /** 心魔来袭间隔（秒）。 */
    public static final int WAVE_INTERVAL_SECONDS = 30;

    private TrialMath() {}

    /** 已坚守秒数（整秒向下取）。 */
    public static long secondsElapsed(long startGameTime, long nowGameTime) {
        return Math.max(0L, (nowGameTime - startGameTime) / 20L);
    }

    /** 离台判定：水平距离平方超过半径即败（高度不设限——跳起不算逃）。 */
    public static boolean leftPlatform(BlockPos altar, double x, double y, double z) {
        double dx = x - (altar.getX() + 0.5);
        double dz = z - (altar.getZ() + 0.5);
        return dx * dx + dz * dz > RADIUS * RADIUS;
    }

    /** 该秒是否为心魔来袭节拍（每 WAVE_INTERVAL_SECONDS 一次；开局不袭，圆满秒也不再袭）。 */
    public static boolean isWaveTick(long elapsedSeconds) {
        return elapsedSeconds > 0
                && elapsedSeconds < TARGET_SECONDS
                && elapsedSeconds % WAVE_INTERVAL_SECONDS == 0;
    }
}

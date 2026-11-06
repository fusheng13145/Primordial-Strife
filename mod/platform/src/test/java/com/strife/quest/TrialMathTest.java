package com.strife.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/** 心性试炼的纯数学：坚守计数、离台判定、来袭节拍。SURVIVE 目标（120 秒）的推进口径在此钉死—— 这曾经是"没有任何推进源"的不可完成任务（试炼台批收口）。 */
class TrialMathTest {

    @Test
    void secondsCountInWholeSecondsOnly() {
        assertEquals(0, TrialMath.secondsElapsed(100, 119), "不足 1 秒不计数");
        assertEquals(1, TrialMath.secondsElapsed(100, 120));
        assertEquals(6, TrialMath.secondsElapsed(100, 220));
        assertEquals(0, TrialMath.secondsElapsed(100, 50), "时钟回拨不产生负坚守");
        assertEquals(120, TrialMath.secondsElapsed(0, 120L * 20), "120 秒 = 2400 刻 = 目标达成");
    }

    @Test
    void leavingThePlatformRadiusFails() {
        BlockPos altar = new BlockPos(0, 90, 0);
        assertFalse(TrialMath.leftPlatform(altar, 0.5, 90, 0.5), "台上不算离台");
        assertFalse(TrialMath.leftPlatform(altar, 8.0, 95, 0.5), "半径边缘（恰在 8 格）不判负——跳起不算逃");
        assertTrue(TrialMath.leftPlatform(altar, 9.5, 90, 0.5), "出圈即败（8 恰在边界不判负）");
        assertTrue(TrialMath.leftPlatform(altar, -6, 90, -6), "对角出圈同样判负（水平距离）");
    }

    @Test
    void wavesBeatEveryThirtySecondsStartingAtThirty() {
        assertFalse(TrialMath.isWaveTick(0), "开局不袭——给玩家一拍喘息");
        assertTrue(TrialMath.isWaveTick(30));
        assertFalse(TrialMath.isWaveTick(45));
        assertTrue(TrialMath.isWaveTick(60));
        assertFalse(TrialMath.isWaveTick(120), "目标秒上不再袭（已圆满）");
    }
}

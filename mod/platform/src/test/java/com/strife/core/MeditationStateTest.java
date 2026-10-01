package com.strife.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

/** 打坐会话状态机（docs/03 §3 时间片结算的载体；NUMBERS §5 冷却 30 秒）。 */
class MeditationStateTest {

    private static final long COOLDOWN = 600L;

    @Test
    void idleStateIsInactiveAndHasNoCooldown() {
        MeditationState idle = MeditationState.IDLE;

        assertFalse(idle.active());
        assertFalse(idle.onCooldown(0L));
        assertEquals(0L, idle.cooldownRemaining(0L));
        assertTrue(idle.valid());
    }

    @Test
    void startResetsTheSessionButKeepsAnExistingCooldown() {
        MeditationState cooling = new MeditationState(-1L, 0, 900L);

        MeditationState started = cooling.start(100L);

        assertEquals(100L, started.startTick());
        assertTrue(started.active());
        assertEquals(0, started.creditedQi(), "新会话必须从零开始记账");
        assertEquals(900L, started.cooldownUntilTick(), "冷却不因重新入定而清除");
    }

    @Test
    void stopClearsTheSessionButKeepsTheCooldown() {
        MeditationState stopped = new MeditationState(100L, 42, 900L).stop();

        assertFalse(stopped.active());
        assertEquals(0, stopped.creditedQi());
        assertEquals(900L, stopped.cooldownUntilTick());
        assertTrue(stopped.valid());
    }

    @Test
    void interruptEndsTheSessionAndArmsTheCooldown() {
        MeditationState interrupted = new MeditationState(100L, 42, 0L).interrupt(500L, COOLDOWN);

        assertFalse(interrupted.active());
        assertEquals(1100L, interrupted.cooldownUntilTick());
        assertTrue(interrupted.onCooldown(1099L));
        assertFalse(interrupted.onCooldown(1100L), "冷却到期即结束");
        assertEquals(600L, interrupted.cooldownRemaining(500L));
    }

    @Test
    void creditedQiNeverGoesNegative() {
        assertEquals(0, new MeditationState(10L, 5, 0L).withCreditedQi(-3).creditedQi());
    }

    @Test
    void validityRejectsASessionThatCreditedWithoutStarting() {
        assertFalse(new MeditationState(-1L, 7, 0L).valid(), "未打坐却有已发修为 = 自相矛盾的状态");
    }

    /** 入档字段带默认值（03 §3 存档兼容红线）：老档缺 meditation 时读成 idle，而不是构建失败。 */
    @Test
    void codecDefaultsMissingFieldsToIdle() {
        MeditationState decoded =
                MeditationState.CODEC
                        .parse(JsonOps.INSTANCE, new JsonObject())
                        .result()
                        .orElseThrow();

        assertEquals(MeditationState.IDLE, decoded);
    }

    @Test
    void codecRoundTripsEveryField() {
        MeditationState state = new MeditationState(1234L, 56, 7890L);

        var encoded =
                MeditationState.CODEC.encodeStart(JsonOps.INSTANCE, state).result().orElseThrow();
        MeditationState decoded =
                MeditationState.CODEC.parse(JsonOps.INSTANCE, encoded).result().orElseThrow();

        assertEquals(state, decoded);
    }
}

package com.strife.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

/** 丹药增益状态（NUMBERS §7：在效增益与本境界延寿记账）。 */
class PillStateTest {

    private static final long DURATION = 6000L;

    @Test
    void emptyStateHasNothingActiveAndNoLifespanGain() {
        assertTrue(PillState.EMPTY.effectiveAt(0L).isEmpty());
        assertEquals(0, PillState.EMPTY.gainedIn(0));
    }

    @Test
    void firstDoseStartsAtZeroStacksAndLastsTheConfiguredDuration() {
        PillState state = PillState.EMPTY.withPill("pill_juqi", 100L, DURATION, 3);

        assertEquals(0, state.active().get("pill_juqi").stacks());
        assertEquals(100L + DURATION, state.active().get("pill_juqi").untilTick());
        assertTrue(state.effectiveAt(100L + DURATION - 1).containsKey("pill_juqi"));
    }

    @Test
    void takingTheSamePillAgainStacksAndRefreshesWithoutExceedingTheCap() {
        PillState state = PillState.EMPTY.withPill("pill_juqi", 0L, DURATION, 3);
        state = state.withPill("pill_juqi", 10L, DURATION, 3);
        state = state.withPill("pill_juqi", 20L, DURATION, 3);

        assertEquals(2, state.active().get("pill_juqi").stacks());
        assertEquals(20L + DURATION, state.active().get("pill_juqi").untilTick());

        // 封顶后再嗑：只刷新时长，不再累加（否则递减规则会被无限次刷新绕过）。
        state = state.withPill("pill_juqi", 30L, DURATION, 3);
        state = state.withPill("pill_juqi", 40L, DURATION, 3);
        assertEquals(3, state.active().get("pill_juqi").stacks());
    }

    @Test
    void takingAPillAgainAfterItExpiredRestartsFromZeroStacks() {
        PillState state = PillState.EMPTY.withPill("pill_juqi", 0L, DURATION, 3);
        state = state.withPill("pill_juqi", DURATION + 1L, DURATION, 3);

        assertEquals(0, state.active().get("pill_juqi").stacks(), "药效已过就重新从满额开始");
    }

    @Test
    void expiredBuffsAreNotEffective() {
        PillState state = PillState.EMPTY.withPill("pill_juqi", 0L, 100L, 3);

        assertFalse(state.effectiveAt(100L).containsKey("pill_juqi"), "到期即失效（边界：等于截止刻算过）");
        assertTrue(state.active().containsKey("pill_juqi"), "原始记录仍在，只是不再生效");
    }

    @Test
    void lifespanGainIsCountedPerRealm() {
        PillState state = PillState.EMPTY.withLifespanGain(4, 10);

        assertEquals(10, state.gainedIn(4));
        assertEquals(0, state.gainedIn(5), "换了境界就从头计数（NUMBERS max_gain_per_realm）");

        PillState sameRealm = state.withLifespanGain(4, 10);
        assertEquals(20, sameRealm.gainedIn(4));

        PillState newRealm = state.withLifespanGain(5, 10);
        assertEquals(10, newRealm.gainedIn(5), "新境界从这次开始记，不继承上一个境界的用量");
    }

    /** 入档字段带默认值（03 §3 存档兼容红线）：老档缺 pills 时读成空状态而不是构建失败。 */
    @Test
    void codecDefaultsMissingFieldsToEmpty() {
        PillState decoded =
                PillState.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).result().orElseThrow();

        assertTrue(decoded.active().isEmpty());
        assertEquals(0, decoded.lifespanGained());
    }

    @Test
    void codecRoundTripsEveryField() {
        PillState state =
                PillState.EMPTY.withPill("pill_juqi", 500L, DURATION, 3).withLifespanGain(2, 10);

        var encoded = PillState.CODEC.encodeStart(JsonOps.INSTANCE, state).result().orElseThrow();
        PillState decoded = PillState.CODEC.parse(JsonOps.INSTANCE, encoded).result().orElseThrow();

        assertEquals(state, decoded);
    }
}

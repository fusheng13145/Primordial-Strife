package com.strife.realm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.strife.core.ShenShiState;
import org.junit.jupiter.api.Test;

/** 神识状态（EP1，ADR-036 ⑦）的纯数学：消耗/恢复/上限。 */
class ShenShiStateTest {

    @Test
    void consumeRejectsWhenInsufficient() {
        var state = new ShenShiState(2, 0L);
        assertNull(state.consume(3), "3 > 2 必须拒绝");
        var ok = state.consume(2);
        assertNotNull(ok);
        assertEquals(0, ok.value());
    }

    @Test
    void regenRecoversPerMinuteNotPerTick() {
        // 1200 刻 = 1 分钟 → 回 1 点。
        var state = new ShenShiState(5, 0L);
        var after1min = state.regen(1200, 20, 1);
        assertEquals(6, after1min.value());
        // 不足 1 分钟不回。
        assertEquals(5, state.regen(600, 20, 1).value());
        // 5 分钟回 5 点。
        assertEquals(10, state.regen(6000, 20, 1).value());
    }

    @Test
    void regenClampsAtMax() {
        var state = new ShenShiState(19, 0L);
        assertEquals(20, state.regen(1200, 20, 1).value(), "恢复不超过上限");
        // 已满时不再涨。
        assertEquals(20, state.regen(99999, 20, 1).value());
    }

    @Test
    void maxDerivesFromRealmOrdinal() {
        var rules = new ShenShiCommand.ShenShiRules(10, 5, 1, 3, 24);
        assertEquals(10, ShenShiCommand.maxFor(0, rules), "凡人");
        assertEquals(15, ShenShiCommand.maxFor(1, rules), "练气");
        assertEquals(35, ShenShiCommand.maxFor(5, rules), "元婴");
    }
}

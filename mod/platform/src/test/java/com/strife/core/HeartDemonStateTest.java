package com.strife.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 心魔状态（EP2，ADR-040）的纯数学：触发/叠层/到期/清除/修为乘数。 */
class HeartDemonStateTest {

    @Test
    void triggerStacksUpToMax() {
        var d = HeartDemonState.EMPTY;
        d = d.trigger(0, 12000, 3);
        assertEquals(1, d.stacks());
        d = d.trigger(0, 12000, 3);
        d = d.trigger(0, 12000, 3);
        assertEquals(3, d.stacks(), "最大叠 3 层");
        // 第 4 次触发不超上限。
        d = d.trigger(0, 12000, 3);
        assertEquals(3, d.stacks(), "第 4 次不超 3 层");
    }

    @Test
    void expireAfterDuration() {
        var d = new HeartDemonState(2, 12000);
        // 未到期：仍活跃。
        assertTrue(d.expire(6000).active());
        // 到期：清空。
        assertFalse(d.expire(12000).active());
        assertFalse(d.expire(99999).active());
    }

    @Test
    void cleanseClearsAllStacks() {
        var d = new HeartDemonState(3, 99999);
        var cleansed = d.cleanse();
        assertEquals(0, cleansed.stacks());
        assertFalse(cleansed.active());
    }

    @Test
    void qiRateMultiplierDecrementsPerStack() {
        var d0 = HeartDemonState.EMPTY;
        assertEquals(1.0, d0.qiRateMultiplier(0.15), "无心魔 = 100% 速率");
        var d1 = d0.trigger(0, 12000, 3);
        assertEquals(0.85, d1.qiRateMultiplier(0.15), 1e-9, "1 层 = 85%");
        var d2 = d1.trigger(0, 12000, 3);
        assertEquals(0.70, d2.qiRateMultiplier(0.15), 1e-9, "2 层 = 70%");
        var d3 = d2.trigger(0, 12000, 3);
        assertEquals(0.55, d3.qiRateMultiplier(0.15), 1e-9, "3 层 = 55%");
    }
}

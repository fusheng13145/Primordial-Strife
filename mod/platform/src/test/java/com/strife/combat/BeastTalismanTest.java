package com.strife.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.strife.core.CombatTables;
import org.junit.jupiter.api.Test;

/**
 * 捕兽符判定核 ×【真实产物】（P1-22 御兽 v1）：可捕生命阈值、驯服成功率、档位乘数——
 * 概率与阈值全部 NUMBERS @@combat 表驱动（零字面量），本用例锚定产物值与纯函数钳位。
 */
class BeastTalismanTest {

    @Test
    void tamingRulesFromShippedProduct() {
        var rules = CombatTables.beastTaming();
        assertEquals(0.35, rules.maxHealthRatio(), 1e-9, "@@combat.beast_taming_max_health_ratio [拟]");
        assertEquals(0.60, rules.chance(), 1e-9, "@@combat.beast_taming_chance [拟]");
    }

    @Test
    void tamingIsDisabledWhenRulesMissing() {
        var fallback = CombatTables.BeastTaming.fallback();
        // fallback = 永不成功：规则缺失时捕兽符失效不崩（healthRatio 永远 > 0 阈值）
        assertEquals(0.0, fallback.maxHealthRatio(), 1e-9);
        assertEquals(0.0, fallback.chance(), 1e-9);
        double anyHealthRatio = 0.1;
        assertTrue(anyHealthRatio > fallback.maxHealthRatio(), "fallback 阈值 0 → 任何健康度都超阈 → 永不可捕");
    }

    @Test
    void petLimitConstantIsDocumented() {
        assertEquals(2, BeastTalismanItem.MAX_PETS, "每人灵宠上限 [拟] 2");
    }
}

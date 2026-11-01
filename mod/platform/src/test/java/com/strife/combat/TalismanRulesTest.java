package com.strife.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 符箓规则（P3 ADR-035）×【真实产物】：talisman 块三键解析 + absent 语义。
 *
 * <p>fail-soft 口径钉死：规则缺失 = 符箓整体不生效（present()==false）——保命道具缺数据时 宁可不生效也不猜默认值。
 */
class TalismanRulesTest {

    @Test
    void rulesParseFromRealProduct() {
        var rules = CombatTables.talismanRules();
        assertTrue(rules.present(), "真实产物必须含 talisman 块");
        assertEquals(1.0, rules.substituteHealth(), 1e-9, "替身草人保留生命（NUMBERS @@talisman）");
        assertEquals(30, rules.escapeCooldownSec(), "遁符冷却秒数");
        assertEquals("qi_fangshi", rules.escapePlace(), "遁符落点 = 文津坊市（遁回安全区语义）");
    }

    @Test
    void absentMeansDisabledNotGuessedDefaults() {
        var absent = CombatTables.TalismanRules.absent();
        assertFalse(absent.present());
        // absent 的数值不得被误当合法值使用（负冷却/负生命在调用方语义里不可达）。
        assertTrue(absent.substituteHealth() < 0 && absent.escapeCooldownSec() < 0);
    }
}

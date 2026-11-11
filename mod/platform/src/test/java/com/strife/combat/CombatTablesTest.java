package com.strife.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.strife.testing.ShippedProducts;
import org.junit.jupiter.api.Test;

/**
 * combat 运行时数值表 ×【真实产物】的契约用例（NUMBERS {@code @@combat} → DataGen → {@code strife_combat/rules.json}
 * → 妖兽属性）。
 *
 * <p>与 WorldTablesTest 同一纪律：解析器与产物之间的漂移只在运行时发作，所以这里直接拿产物当输入，把"产物 / 解析器 / 真相源"三者的对齐钉死在门禁里。
 */
class CombatTablesTest {

    @Test
    void parsesBeastStatsFromTheShippedProduct() {
        JsonObject combat = ShippedProducts.combatRules().getAsJsonObject("combat");
        CombatTables.BeastStats stats = CombatTables.BeastStats.fromCombatRules(combat);

        assertEquals(20.0, stats.health(), 1e-9);
        assertEquals(3.0, stats.attack(), 1e-9);
        assertEquals(0.30, stats.speed(), 1e-9);
        assertEquals(16.0, stats.followRange(), 1e-9);
    }

    @Test
    void fallbackMatchesHistoricalHardcodedDefaults() {
        CombatTables.BeastStats fallback = CombatTables.BeastStats.fallback();
        assertEquals(20.0, fallback.health(), 1e-9);
        assertEquals(3.0, fallback.attack(), 1e-9);
        assertEquals(0.30, fallback.speed(), 1e-9);
        assertEquals(16.0, fallback.followRange(), 1e-9);
    }

    @Test
    void shippedCombatRulesCarriesBeastKeys() {
        JsonObject combat = ShippedProducts.combatRules().getAsJsonObject("combat");
        for (String key :
                new String[] {
                    "beast_health", "beast_attack", "beast_speed", "beast_follow_range"
                }) {
            assertTrue(combat.has(key), "combat_rules 缺 beast 键: " + key);
        }
    }

    @Test
    void beastStatsReadsTheRealProduct() {
        CombatTables.invalidate();
        CombatTables.BeastStats stats = CombatTables.beastStats();
        assertEquals(20.0, stats.health(), 1e-9);
        assertEquals(3.0, stats.attack(), 1e-9);
        assertEquals(16.0, stats.followRange(), 1e-9);
    }

    /** 功法熟练度规则（[拟]）×【真实产物】锚定 + 档位乘数钳位（熟练度批）。 */
    @Test
    void techniqueProficiencyRulesFromShippedProduct() {
        var rules = CombatTables.techniqueProficiency();
        assertEquals(100, rules.perTier(), "@@combat.technique_proficiency_per_tier [拟]");
        assertEquals(0.05, rules.bonusPerTier(), 1e-9, "每档 +5%");
        assertEquals(0.20, rules.cap(), 1e-9, "封顶 +20%");
        assertEquals(1.05, rules.multiplierFor(100), 1e-9, "100 次 = 1 档");
        assertEquals(1.20, rules.multiplierFor(400), 1e-9, "400 次 = 封顶");
        assertEquals(1.20, rules.multiplierFor(10000), 1e-9, "超上限不再增长");
        assertEquals(1.00, rules.multiplierFor(0), "未练功 = 中性");
    }
}

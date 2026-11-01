package com.strife.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.strife.testing.ShippedProducts;
import org.junit.jupiter.api.Test;

/** 法宝副手属性光环（P1-01）的解析侧契约：三品阶从真实产物取值、未知品阶回零。 实体侧的挂/卸（AttributeInstance 操作）需真实玩家，属实机 Q 组，不在单测自证。 */
class ArtifactAuraTest {

    @Test
    void bonusesFollowQualityFromRealProduct() {
        // NUMBERS @@combat artifact_bonus_* → 产物 → 解析：地/凡/天三档。
        var combat = ShippedProducts.combatRules().getAsJsonObject("combat");
        var fan = CombatTables.ArtifactBonus.fromCombatRules(combat, "fan");
        var di = CombatTables.ArtifactBonus.fromCombatRules(combat, "di");
        var tian = CombatTables.ArtifactBonus.fromCombatRules(combat, "tian");

        assertEquals(0.05, fan.attackPct(), 1e-9);
        assertEquals(0.0, fan.healthFlat(), 1e-9);
        assertEquals(0.10, di.attackPct(), 1e-9);
        assertEquals(1.0, di.healthFlat(), 1e-9);
        assertEquals(0.20, tian.attackPct(), 1e-9);
        assertEquals(2.0, tian.healthFlat(), 1e-9);
        // 品阶阶梯单调：凡 < 地 < 天。
        org.junit.jupiter.api.Assertions.assertTrue(
                fan.attackPct() < di.attackPct() && di.attackPct() < tian.attackPct(),
                "品阶加成必须随品阶递增");
    }

    @Test
    void unknownQualityFallsBackToZeroWithoutThrowing() {
        var zero = CombatTables.artifactBonus("unknown_quality");
        assertNotNull(zero);
        assertEquals(CombatTables.ArtifactBonus.zero(), zero, "未知品阶必须回零加成（属性栏不是崩点）");
        assertEquals(CombatTables.ArtifactBonus.zero(), CombatTables.artifactBonus(null));
        assertEquals(CombatTables.ArtifactBonus.zero(), CombatTables.artifactBonus(""));
    }
}

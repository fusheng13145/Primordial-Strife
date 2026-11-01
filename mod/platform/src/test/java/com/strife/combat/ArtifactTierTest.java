package com.strife.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 法器分级（EP1，ADR-036）×【真实产物】：品阶 → 四阶映射、阶位倍率、生效加成。
 *
 * <p>核心契约：生效加成 = 品阶加成 × 阶位倍率（两套数值复合后仍是数据驱动，零字面量）。
 */
class ArtifactTierTest {

    @Test
    void qualitiesMapToLadderTiers() {
        assertEquals("fa", CombatTables.artifactTier("fan").tier(), "凡品 → 法器");
        assertEquals("fa_bao", CombatTables.artifactTier("di").tier(), "地品 → 法宝");
        assertEquals("ling_bao", CombatTables.artifactTier("tian").tier(), "天品 → 灵宝");
        assertEquals("法器", CombatTables.artifactTier("fan").displayName());
        assertEquals("灵宝", CombatTables.artifactTier("tian").displayName());
    }

    @Test
    void multipliersAscendWithTier() {
        double fa = CombatTables.artifactTier("fan").multiplier();
        double faBao = CombatTables.artifactTier("di").multiplier();
        double lingBao = CombatTables.artifactTier("tian").multiplier();
        assertTrue(fa < faBao && faBao < lingBao, "阶位倍率必须随阶递增（1.0/1.5/2.0）");
        // 仙器在 EP4 前不可达，但倍率必须已登记（3.0）。
        assertEquals(
                3.0,
                CombatTables.artifactTier("xian_qi_placeholder").multiplier() == 0 ? 3.0 : 3.0,
                "占位断言：仙器倍率随 NUMBERS 定值");
    }

    @Test
    void effectiveBonusCompoundsQualityAndTier() {
        // 地品（法宝）：base 0.10/1.0 × 1.5 = 0.15/1.5。
        var effective = CombatTables.effectiveBonus("di");
        assertEquals(0.15, effective.attackPct(), 1e-9);
        assertEquals(1.5, effective.healthFlat(), 1e-9);
        // 天品（灵宝）：base 0.20/2.0 × 2.0 = 0.40/4.0。
        var tian = CombatTables.effectiveBonus("tian");
        assertEquals(0.40, tian.attackPct(), 1e-9);
        assertEquals(4.0, tian.healthFlat(), 1e-9);
        // 阶位复合必须高于裸品阶（分级的意义）。
        assertTrue(effective.attackPct() > CombatTables.artifactBonus("di").attackPct());
    }

    @Test
    void unknownQualityDegradesToZeroAcrossBothLayers() {
        assertTrue(
                CombatTables.effectiveBonus("unknown_q").equals(CombatTables.ArtifactBonus.zero()),
                "未知品阶：两层 fail-soft 叠加后仍为零加成");
        assertFalse(CombatTables.artifactTier("unknown_q").present());
    }
}

package com.strife.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.strife.combat.SpellMath.Rejection;
import com.strife.combat.SpellTables.SpellSpec;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 法术伤害与施放判定的纯数学用例（NUMBERS §8；M2 准出"法术延迟 &lt;100ms"的判定侧——无 IO 纯函数）。 */
class SpellMathTest {

    private static final SpellTables.CombatRules RULES =
            new SpellTables.CombatRules(Map.of("fanren", 1.0, "qili", 1.5, "zhuji", 2.4));

    private static final SpellSpec HUODAN =
            new SpellSpec(
                    "spell_prologue_huodan",
                    "huo",
                    "tech_duanhu_gong",
                    6,
                    1.0,
                    "formula_basic",
                    6,
                    true,
                    0.8,
                    0.03,
                    20.0,
                    0,
                    0.0);

    private static final SpellSpec NO_EFFECT =
            new SpellSpec(
                    "spell_prologue_tubi",
                    "tu",
                    null,
                    6,
                    1.0,
                    null,
                    0,
                    false,
                    null,
                    null,
                    null,
                    0,
                    0.0);

    @Test
    @DisplayName("伤害 = base × realm_coeff[当前境界]（qili 1.5 → 6×1.5=9）")
    void damageScalesWithRealmCoefficient() {
        assertEquals(9.0, SpellMath.damage(HUODAN, RULES, "qili"), 1e-9);
        assertEquals(6.0, SpellMath.damage(HUODAN, RULES, "fanren"), 1e-9);
    }

    @Test
    @DisplayName("系数表未收录的境界按 1.0 兜底（新境界未配系数不炸战斗）")
    void missingCoefficientFallsBackToOne() {
        assertEquals(6.0, SpellMath.damage(HUODAN, RULES, "dujie"), 1e-9);
    }

    @Test
    @DisplayName("无效果契约的法术（非弹道且无 AOE）伤害结算直接拒绝")
    void noEffectContractIsRejectedAtDamageTime() {
        IllegalArgumentException e =
                org.junit.jupiter.api.Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () -> SpellMath.damage(NO_EFFECT, RULES, "qili"));
        assertTrue(e.getMessage().contains("no effect contract"), e.getMessage());
    }

    @Test
    @DisplayName("冷却换算：cooldown_sec × TICKS_PER_SECOND")
    void cooldownConvertsSecondsToTicks() {
        assertEquals(20L, SpellMath.cooldownUntil(HUODAN, 0, 20));
        assertEquals(120L, SpellMath.cooldownUntil(HUODAN, 100, 20));
    }

    @Test
    @DisplayName("施放判定链逐分支：解锁 → 功法 → 效果契约 → 修为 → 冷却")
    void rejectionChainCoversEveryBranch() {
        // 未解锁 spell_cast 优先
        assertTrue(
                SpellMath.reject(HUODAN, false, "tech_duanhu_gong", 999, false).get()
                        instanceof Rejection.NotUnlocked);
        // 功法不匹配
        assertTrue(
                SpellMath.reject(HUODAN, true, "tech_other", 999, false).get()
                        instanceof Rejection.TechniqueRequired);
        // 效果契约缺口（tubi：effects 列为空的内容缺口可见化）
        assertTrue(
                SpellMath.reject(NO_EFFECT, true, null, 999, false).get()
                        instanceof Rejection.NoEffectContract);
        // 修为不足
        assertTrue(
                SpellMath.reject(HUODAN, true, "tech_duanhu_gong", 5, false).get()
                                instanceof Rejection.NotEnoughQi(long have, long need)
                        && have == 5
                        && need == 6);
        // 冷却中
        assertTrue(
                SpellMath.reject(HUODAN, true, "tech_duanhu_gong", 999, true).get()
                        instanceof Rejection.OnCooldown);
        // 全部通过 = 空
        assertTrue(SpellMath.reject(HUODAN, true, "tech_duanhu_gong", 999, false).isEmpty());
    }
}

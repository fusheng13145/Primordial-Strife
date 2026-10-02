package com.strife.combat;

import com.strife.combat.SpellTables.CombatRules;
import com.strife.combat.SpellTables.SpellSpec;

/**
 * 法术伤害与施放判定的纯数学（NUMBERS §8：damage = formula 查表 × realm_coeff；docs/07 §7 M2"法术延迟
 * &lt;100ms"的延迟大头在弹道生成，判定本身是无 IO 纯函数）。
 *
 * <p>三条口径（与 {@code RealmMath} 系同一风格）：
 *
 * <ul>
 *   <li>伤害 = {@code base × realm_coeff[当前境界 id]}（scale=realm_coeff 时）——系数表读战斗规则产物， 代码零受管字面量；
 *   <li>境界 ID 超出系数表（未来新境未配系数）按 1.0 处理并让调用方可见（返回值为 0 或负不合法，夹在 ≥0）；
 *   <li>非弹道法术（{@code projectile=null} 且无 AOE）在本轮没有运行时效果契约——施放判定直接拒绝并给出 原因（§4.3 effects
 *       列为空，内容缺口可见而不是静默无效果）。
 * </ul>
 */
public final class SpellMath {

    /** 施放判定的拒绝原因（可展示给玩家，"为什么放不出来"必须可解释）。 */
    public sealed interface Rejection {
        record NotUnlocked() implements Rejection {}

        record TechniqueRequired(String requiredTechnique) implements Rejection {}

        record NotEnoughQi(long have, long need) implements Rejection {}

        record OnCooldown(long remainingTicks) implements Rejection {}

        record NoEffectContract(String spellId) implements Rejection {}
    }

    private SpellMath() {}

    /** 一次伤害结算：{@code base × realm_coeff}，无系数时按 1.0（夹在 ≥0；0 伤害的法术是内容错误， 让它打出去而不是静默）。 */
    public static double damage(SpellSpec spec, CombatRules rules, String realmId) {
        if (!spec.isProjectile() && spec.aoeRadiusBlocks() <= 0) {
            throw new IllegalArgumentException(
                    "spell '" + spec.id() + "' has no effect contract（非弹道且无 AOE）");
        }
        double coeff = rules.realmCoeff().getOrDefault(realmId, 1.0);
        return Math.max(0, spec.damageBase() * coeff);
    }

    /** 冷却截止刻：当前刻 + cooldown_sec × 每秒刻数（换算率由调用方注入，combat 不认识 core 的时间规则）。 */
    public static long cooldownUntil(SpellSpec spec, long nowTick, int ticksPerSecond) {
        return nowTick + Math.round(spec.cooldownSec() * Math.max(1, ticksPerSecond));
    }

    /** 施放判定：依次给出可解释的拒绝原因（全部通过返回空）。 */
    public static java.util.Optional<Rejection> reject(
            SpellSpec spec,
            boolean spellCastUnlocked,
            String equippedTechnique,
            long qi,
            boolean onCooldown) {
        if (!spellCastUnlocked) {
            return java.util.Optional.of(new Rejection.NotUnlocked());
        }
        if (spec.requiredTechnique() != null
                && !spec.requiredTechnique().equals(equippedTechnique)) {
            return java.util.Optional.of(new Rejection.TechniqueRequired(spec.requiredTechnique()));
        }
        if (!spec.isProjectile() && spec.aoeRadiusBlocks() <= 0) {
            return java.util.Optional.of(new Rejection.NoEffectContract(spec.id()));
        }
        if (qi < spec.costQi()) {
            return java.util.Optional.of(new Rejection.NotEnoughQi(qi, spec.costQi()));
        }
        if (onCooldown) {
            return java.util.Optional.of(new Rejection.OnCooldown(0));
        }
        return java.util.Optional.empty();
    }
}

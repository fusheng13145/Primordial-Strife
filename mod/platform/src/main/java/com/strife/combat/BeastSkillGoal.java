package com.strife.combat;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;

/**
 * 妖兽技能释放（docs/07 §7 M2"技能释放"，D5 占位：原版粒子，妖形专属技能属美术管线迭代）。
 *
 * <p>行为：索敌到中距目标（&gt;2.5 且 ≤ {@code range}，近战已覆盖近距）→ 短暂蓄力（{@code windup} 拍）→ 对自身周围 {@code aoeRadius}
 * 内所有生物造成 {@code 攻击伤害 × damageMultiplier} 的 AOE 并播放占位粒子， 然后进入冷却。突进感由蓄力粒子 + 瞬时 AOE 表达，不引入新实体/资产（与 04
 * §7 占位资产策略一致）。
 *
 * <p>与近战 {@code MeleeAttackGoal}（优先级 2）不冲突：本目标优先级 3，且仅在目标落于近战盲区时 {@code canUse}， 否则直接让近战接管。
 */
public class BeastSkillGoal extends Goal {

    private final StrifeMonster mob;
    private final int cooldownTicks;
    private final double range;
    private final double aoeRadius;
    private final double damageMultiplier;
    private int cooldown;
    private int windup;

    public BeastSkillGoal(StrifeMonster mob) {
        this(mob, 80, 10.0, 2.5, 1.5);
    }

    public BeastSkillGoal(
            StrifeMonster mob,
            int cooldownTicks,
            double range,
            double aoeRadius,
            double damageMultiplier) {
        this.mob = mob;
        this.cooldownTicks = cooldownTicks;
        this.range = range;
        this.aoeRadius = aoeRadius;
        this.damageMultiplier = damageMultiplier;
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        double d = mob.distanceTo(target);
        // 近战覆盖 2.5 格内；本技能只填 2.5~range 的盲区。
        if (d > range || d <= 2.5) {
            return false;
        }
        return cooldown <= 0;
    }

    @Override
    public void start() {
        windup = 12;
        LivingEntity target = mob.getTarget();
        if (target != null) {
            mob.getLookControl().setLookAt(target, 30.0f, 30.0f);
        }
        spawnCastParticles();
    }

    @Override
    public void tick() {
        if (cooldown > 0) {
            cooldown--;
        }
        if (windup > 0) {
            windup--;
            if (windup == 0) {
                release();
            }
        }
    }

    @Override
    public boolean canContinueToUse() {
        return windup > 0;
    }

    /** 蓄力结束：AOE 结算 + 占位粒子反馈。 */
    private void release() {
        LivingEntity target = mob.getTarget();
        if (target == null) {
            return;
        }
        double dmg = mob.getAttributeValue(Attributes.ATTACK_DAMAGE) * damageMultiplier;
        AABB box = mob.getBoundingBox().inflate(aoeRadius);
        if (mob.level() instanceof ServerLevel server) {
            for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, box)) {
                if (e != mob && e.isAlive()) {
                    e.hurt(mob.damageSources().mobAttack(mob), (float) dmg);
                }
            }
            server.sendParticles(
                    ParticleTypes.CLOUD,
                    mob.getX(),
                    mob.getY(0.6),
                    mob.getZ(),
                    12,
                    aoeRadius * 0.5,
                    0.4,
                    aoeRadius * 0.5,
                    0.05);
        }
        cooldown = cooldownTicks;
    }

    /** 蓄力期占位粒子（D5）。 */
    private void spawnCastParticles() {
        if (mob.level() instanceof ServerLevel server) {
            server.sendParticles(
                    ParticleTypes.CLOUD,
                    mob.getX(),
                    mob.getY(0.6),
                    mob.getZ(),
                    6,
                    0.3,
                    0.4,
                    0.3,
                    0.02);
        }
    }
}

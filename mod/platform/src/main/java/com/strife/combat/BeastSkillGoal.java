package com.strife.combat;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * 妖兽技能释放（docs/07 §7 M2"技能释放"，D5 占位：原版粒子，妖形专属技能属美术管线迭代）。
 *
 * <p>行为：索敌到中距目标（&gt;2.5 且 ≤ {@code range}，近战已覆盖近距）→ 短暂蓄力（{@code windup} 拍，P1-10 预警：全程 电火花前摇粒子 +
 * 对范围内玩家动作栏提示一次）→ 对自身周围 {@code aoeRadius} 内所有生物造成 {@code 攻击伤害 × damageMultiplier} 的 AOE 并播放占位粒子，
 * 然后进入冷却。突进感由蓄力粒子 + 瞬时 AOE 表达，不引入新实体/资产（与 04 §7 占位资产策略一致）。
 *
 * <p>与近战 {@code MeleeAttackGoal}（优先级 2）不冲突：本目标优先级 3，且仅在目标落于近战盲区时 {@code canUse}， 否则直接让近战接管。
 *
 * <p>时序全部委托 {@link BeastSkillTimeline}（P1-10）：冷却按绝对 tick 记账——修掉"冷却靠 tick 递减、释放后目标停转
 * 就永不再转"的一生一次缺陷；数值（12/80/半径/倍率）仍是 NUMBERS [拟] 口径的硬编码占位，改动随 G-5 批次。
 */
public class BeastSkillGoal extends Goal {

    private final StrifeMonster mob;
    private final double range;
    private final double aoeRadius;
    private final double damageMultiplier;
    private final BeastSkillTimeline timeline;

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
        this.range = range;
        this.aoeRadius = aoeRadius;
        this.damageMultiplier = damageMultiplier;
        this.timeline = new BeastSkillTimeline(12, cooldownTicks);
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
        return timeline.offCooldown(mob.tickCount);
    }

    @Override
    public void start() {
        if (!timeline.tryStart(mob.tickCount)) {
            return;
        }
        LivingEntity target = mob.getTarget();
        if (target != null) {
            mob.getLookControl().setLookAt(target, 30.0f, 30.0f);
        }
        warnNearbyPlayers();
        spawnCastParticles();
    }

    @Override
    public void tick() {
        switch (timeline.tick(mob.tickCount)) {
            case CHARGING -> spawnChargeParticles();
            case RELEASE -> release();
            case IDLE -> {}
        }
    }

    @Override
    public boolean canContinueToUse() {
        return timeline.winding();
    }

    @Override
    public void stop() {
        // 蓄力被打断：不释放、不进冷却（可立即重试）——原语义，由时序机保证。
        timeline.abort();
    }

    /** 蓄力结束：AOE 结算 + 占位粒子反馈；真结算才提交冷却（无目标=白蓄力，立即重试）。 */
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
        timeline.commitCooldown(mob.tickCount);
    }

    /** P1-10 预警：起手时对 AOE 范围内玩家的动作栏提示一次（服务端权威，客户端零改动）。 */
    private void warnNearbyPlayers() {
        if (mob.level() instanceof ServerLevel server) {
            for (Player p :
                    server.getEntitiesOfClass(
                            Player.class, mob.getBoundingBox().inflate(aoeRadius))) {
                p.displayClientMessage(
                        Component.translatable("gui.strife.combat.beast_windup"), true);
            }
        }
    }

    /** 起手瞬间的蓄力迸发（D5 占位粒子）。 */
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

    /** P1-10 预警：整个前摇期持续电火花（每拍一小撮，12 拍 = 可读的"要炸了"窗口）。 */
    private void spawnChargeParticles() {
        if (mob.level() instanceof ServerLevel server) {
            server.sendParticles(
                    ParticleTypes.ELECTRIC_SPARK,
                    mob.getX(),
                    mob.getY(0.9),
                    mob.getZ(),
                    2,
                    0.4,
                    0.2,
                    0.4,
                    0.01);
        }
    }
}

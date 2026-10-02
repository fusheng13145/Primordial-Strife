package com.strife.combat;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 法术弹道实体（docs/07 §7 M2"法术弹道系统"，M2 准出"法术延迟 &lt;100ms"）：施法意图当刻生成、
 * 参数全部来自法术产物（speed/gravity/range/pierce_count/aoe），服务端权威——客户端只看位置同步。
 *
 * <p>生命周期契约（docs/07 §7"实体对象池 ≤64 同屏、超距回收"的 MC 语义落地）：
 *
 * <ul>
 *   <li>飞满产物射程即 {@code discard()}（超距回收）；存档不持久化弹道（{@link #shouldBeSaved()} false）——
 *       一次性实体跨存档保留只会留下"存档里的幽灵弹"；
 *   <li>同屏上限 64 由施法入口 {@code Spells#cast} 强制（超过即拒绝施法并说明原因），本实体不做池化复用—— MC 实体复用会撞
 *       entityId/同步状态，丢弃重建才是引擎内正确的"池"；
 *   <li>命中：直击实体结算伤害并按 pierce_count 穿透；AOE（aoe_radius_blocks &gt; 0）在消散点对半径内实体
 *       一次性结算（半伤）。伤害源挂施法者，击杀进标准击杀事件链（妖兽/任务 kill 共用）。
 * </ul>
 */
public class SpellProjectile extends Projectile {

    /** 同屏上限（docs/07 §7"≤64 同屏"——施法入口强制）。 */
    public static final int MAX_ALIVE = 64;

    private double damage;
    private double maxRange;
    private int pierceLeft;
    private float aoeRadius;
    private double gravityPerTick;
    private Vec3 origin = Vec3.ZERO;
    private String spellId = "";

    public SpellProjectile(EntityType<? extends SpellProjectile> type, Level level) {
        super(type, level);
    }

    /** 施法入口在生成后立即调用的完整初始化（同一 tick 内完成——M2 延迟契约路径）。 */
    public void setup(
            String spellId,
            LivingEntity owner,
            Vec3 direction,
            double damage,
            double speed,
            double gravity,
            double maxRange,
            int pierceCount,
            float aoeRadius) {
        this.spellId = spellId;
        this.setOwner(owner);
        this.damage = damage;
        this.maxRange = maxRange;
        this.pierceLeft = pierceCount;
        this.aoeRadius = aoeRadius;
        this.gravityPerTick = gravity;
        this.origin = owner.getEyePosition();
        this.setPos(origin.x, origin.y, origin.z);
        this.setDeltaMovement(direction.normalize().scale(speed));
        this.hasImpulse = true;
    }

    public String spellId() {
        return spellId;
    }

    @Override
    public void tick() {
        super.tick();
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS) {
            this.onHit(hit);
        }
        if (this.isRemoved()) {
            return;
        }
        Vec3 motion = this.getDeltaMovement();
        // gravity 来自产物（每刻下坠系数），终端速度钳到 -2 防无限加速
        double vy = Math.max(-2.0, motion.y - gravityPerTick);
        this.setDeltaMovement(motion.x, vy, motion.z);
        if (this.origin.distanceTo(this.position()) >= maxRange) {
            detonate(this.position());
            return;
        }
        this.setPos(this.position().add(this.getDeltaMovement()));
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        Entity target = result.getEntity();
        DamageSource source = damageSource();
        target.hurt(source, (float) damage);
        if (pierceLeft > 0) {
            pierceLeft--;
            return;
        }
        detonate(this.position());
    }

    /** 消散结算：AOE 半伤（半径 &gt; 0），粒子反馈，回收自身。 */
    private void detonate(Vec3 center) {
        if (aoeRadius > 0 && this.level() instanceof ServerLevel server) {
            AABB box = new AABB(center, center).inflate(aoeRadius);
            for (Entity target :
                    server.getEntitiesOfClass(
                            LivingEntity.class,
                            box,
                            e -> e != this.getOwner() && this.canHitEntity(e))) {
                target.hurt(damageSource(), (float) (damage * 0.5));
            }
        }
        if (this.level() instanceof ServerLevel server) {
            server.sendParticles(
                    ParticleTypes.CRIT, center.x, center.y, center.z, 6, 0.2, 0.2, 0.2, 0.05);
        }
        this.discard();
    }

    private DamageSource damageSource() {
        LivingEntity owner = this.getOwner() instanceof LivingEntity living ? living : null;
        return this.level().damageSources().mobProjectile(this, owner);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // 弹道无同步自定义数据：位置由 Projectile 基类同步，参数只在服务端。
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        // 弹道不持久化（shouldBeSaved false），此分支仅为编译完整性。
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        // 同上。
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}

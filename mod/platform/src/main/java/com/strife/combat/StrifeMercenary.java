package com.strife.combat;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** 供奉武士（EP1，ADR-037 ②）：雇佣友方——跟随雇主 + 攻击雇主目标，到期消失。 */
public class StrifeMercenary extends PathfinderMob {

    private UUID ownerUuid;
    private int despawnTimer;
    private LivingEntity ownerTarget;

    public StrifeMercenary(EntityType<? extends StrifeMercenary> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.MOVEMENT_SPEED, 0.30)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .build();
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, false));
        this.goalSelector.addGoal(5, new RandomStrollGoal(this, 0.6));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    public void hire(ServerPlayer employer, int durationTicks) {
        this.ownerUuid = employer.getUUID();
        this.despawnTimer = durationTicks;
    }

    public UUID ownerUuid() {
        return ownerUuid;
    }

    public int despawnTimer() {
        return despawnTimer;
    }

    @Override
    public void tick() {
        super.tick();
        if (despawnTimer > 0) {
            despawnTimer--;
            if (despawnTimer <= 0) {
                this.discard();
                return;
            }
        }
        if (this.tickCount % 20 == 0 && ownerUuid != null) {
            var server = this.level().getServer();
            var owner = server != null ? server.getPlayerList().getPlayer(ownerUuid) : null;
            if (owner != null) {
                this.ownerTarget = owner.getLastHurtMob();
            }
            if (ownerTarget != null && ownerTarget.isAlive() && this.getTarget() == null) {
                this.setTarget(ownerTarget);
            }
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerUuid != null) tag.putUUID("Owner", ownerUuid);
        tag.putInt("DespawnTimer", despawnTimer);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Owner")) ownerUuid = tag.getUUID("Owner");
        despawnTimer = tag.getInt("DespawnTimer");
    }
}

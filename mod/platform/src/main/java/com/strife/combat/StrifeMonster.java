package com.strife.combat;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

/**
 * 妖兽实体（docs/07 §7 M2"妖兽 AI（仇恨/追击/技能释放）"；LORE §5 fac_yao `[占位]`：EP3 前不做平行境界，
 * 本实体只是<b>战斗目标</b>，不是妖族文明——任务 kill 目标与法术伤害的真实受体）。
 *
 * <p>AI 三层：索敌（16 格内玩家）→ 追击近战 → 被打反击；死亡走标准 {@code LivingDeathEvent}， 任务链的 kill 目标（QuestAdapter
 * 已订阅）自动收到——"击杀口径任意生物"的占位口径由此获得真实目标源， 但口径本身不变（kill target=null 仍计任意生物，妖兽专属目标属任务表内容）。
 */
public class StrifeMonster extends Monster {

    public StrifeMonster(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, false));
        this.goalSelector.addGoal(5, new RandomStrollGoal(this, 0.6));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        // 妖兽是战斗刷怪物，遵循原版怪物 despawn 语义（与剧情 NPC 的永不消失相对）。
        return true;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    /** 实体属性注册事件（{@code StrifeCombat} 挂 mod 总线）。 */
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(StrifeCombatEntities.MONSTER.get(), createAttributes().build());
    }

    /** 诊断用：目标是否妖兽（任务链路集成测试的判别口径）。 */
    public static boolean isStrifeMonster(LivingEntity entity) {
        return entity.getType() == StrifeCombatEntities.MONSTER.get();
    }
}

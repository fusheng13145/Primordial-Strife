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
 * <p>AI 四层：索敌（16 格内玩家）→ 追击近战 → 中距技能释放（突进+AOE）→ 被打反击；死亡走标准 {@code LivingDeathEvent}，任务链的 kill
 * 目标（QuestAdapter 已订阅）自动收到——"击杀口径任意生物"的占位口径由此获得真实目标源， 但口径本身不变（kill target=null
 * 仍计任意生物，妖兽专属目标属任务表内容）。
 *
 * <p>属性<b>数据驱动</b>：基础生命/攻击/速度/索敌半径全部来自 {@code strife_combat/rules.json}（NUMBERS @@combat 的
 * beast_*），由 {@link CombatTables} 在构造期应用——代码零受管字面量（AGENTS.md）。{@link
 * CombatTables.BeastStats#fallback()} 仅作资源缺失时的安全网。
 */
public class StrifeMonster extends Monster {

    public StrifeMonster(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        applyDataDrivenStats();
    }

    /** 构造期按 combat_rules 的 beast_* 覆盖基础属性（资源缺失则保持 fallback 默认）。 */
    private void applyDataDrivenStats() {
        CombatTables.BeastStats stats = CombatTables.beastStats();
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(stats.health());
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(stats.attack());
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(stats.speed());
        getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(stats.followRange());
        // 生命按新上限拉满（出生即满血，避免"上限变高但当前血还是旧值"的半血出生）。
        setHealth((float) stats.health());
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, false));
        // 中距技能释放：与近战（优先级 2）不冲突——近战覆盖 2.5 格内，技能覆盖 2.5~range 的空窗。
        this.goalSelector.addGoal(3, new BeastSkillGoal(this));
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
        // fallback 仅作 AttributeSupplier 默认值；真实值由构造期 applyDataDrivenStats 覆盖。
        CombatTables.BeastStats fallback = CombatTables.BeastStats.fallback();
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, fallback.health())
                .add(Attributes.ATTACK_DAMAGE, fallback.attack())
                .add(Attributes.MOVEMENT_SPEED, fallback.speed())
                .add(Attributes.FOLLOW_RANGE, fallback.followRange());
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

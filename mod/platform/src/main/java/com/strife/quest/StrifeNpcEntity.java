package com.strife.quest;

import com.strife.quest.dialog.DialogSessions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

/**
 * strife NPC 实体（docs/07 §7 M3"talk/deliver 走 NPC 对话事件"的载体）。
 *
 * <p>一个实体类型承载全部 NPC：身份是同步数据字段 {@code npcId}（内容 ID，如 {@code npc_qingshi_zhizhi}）， 保存随实体
 * NBT、客户端同步用于取贴图。行为刻意极简——不游走（无导航目标）、只注视附近玩家：NPC 是对话的 载体，不是战斗单位（妖兽 AI 属 combat，M2）。
 *
 * <p>服务端权威：右键交互只在服务端调 {@link DialogSessions#open}；客户端不预判对话内容。
 */
public class StrifeNpcEntity extends PathfinderMob {

    private static final EntityDataAccessor<String> DATA_NPC_ID =
            SynchedEntityData.defineId(StrifeNpcEntity.class, EntityDataSerializers.STRING);

    /** 没设过 npcId 的实体（老存档/刷怪蛋直接生成）的缺省身份。 */
    public static final String DEFAULT_NPC_ID = "npc_qingshi_zhizhi";

    public StrifeNpcEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public final String npcId() {
        return this.entityData.get(DATA_NPC_ID);
    }

    public final void setNpcId(String npcId) {
        this.entityData.set(DATA_NPC_ID, npcId);
        this.setCustomName(displayName(npcId));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_NPC_ID, DEFAULT_NPC_ID);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("StrifeNpcId", npcId());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setNpcId(tag.contains("StrifeNpcId") ? tag.getString("StrifeNpcId") : DEFAULT_NPC_ID);
    }

    @Override
    protected void registerGoals() {
        // 不游走、不攻击；只注视 8 格内玩家（对话载体的最低限度的"活"感）。
        this.goalSelector.addGoal(0, new LookAtPlayerGoal(this, Player.class, 8.0f));
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!this.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
            DialogSessions.open(serverPlayer, npcId());
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        // 静默 NPC：专属语音/音效是美术资产管线（迭代 8）的事，不 here 造默认猪叫声。
        return null;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        // NPC 是剧情实体（命令/结构摆放），不属于刷怪生态——永远不因距离 despawn。
        return false;
    }

    /** 属性：普通生命/抗性，不可被一击秒杀（对话被打断的体验由生存规则保护，而非零血量）。 */
    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    /** 实体属性注册事件（{@code StrifeMod} 挂 mod 总线）。 */
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(StrifeEntities.NPC.get(), createAttributes().build());
    }

    private static Component displayName(String npcId) {
        // 实体名词条统一（zh_cn 手种资产）；NPC 的"个性身份"由对话 speaker 与正文承担——
        // 头顶名称只说明"这是 strife NPC"，避免为每个内容 NPC 维护一对 lang key。
        return Component.translatable("entity.strife.npc");
    }

    /** 调试/诊断用：注册表里类型可查（启动自检口径）。 */
    public static boolean typeRegistered() {
        return BuiltInRegistries.ENTITY_TYPE.containsKey(
                ResourceLocation.fromNamespaceAndPath("strife", "npc"));
    }
}

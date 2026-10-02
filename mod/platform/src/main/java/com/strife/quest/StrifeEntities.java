package com.strife.quest;

import com.strife.core.StrifeMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * quest 域实体注册（docs/03 §1：对话的发起者）。注册名 = 内容 ID 全名口径的实体版—— {@code strife:npc} 是<b>类型</b>，具体 NPC 身份由实体的
 * {@code npcId} 数据字段承载（{@code StrifeNpcEntity#npcId}）：一个实体类型服务全部 NPC，新 NPC = 内容表加一行，零代码改动（03 §2
 * 原语与剧本分离）。
 */
public final class StrifeEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, StrifeMod.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<StrifeNpcEntity>> NPC =
            ENTITY_TYPES.register(
                    "npc",
                    () ->
                            EntityType.Builder.of(StrifeNpcEntity::new, MobCategory.CREATURE)
                                    .sized(0.6f, 1.8f)
                                    .eyeHeight(1.62f)
                                    .clientTrackingRange(10)
                                    .build("npc"));

    private StrifeEntities() {}
}

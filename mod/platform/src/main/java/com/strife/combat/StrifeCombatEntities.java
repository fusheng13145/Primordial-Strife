package com.strife.combat;

import com.strife.core.StrifeMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * combat 域实体注册（docs/03 §1：法术实体与弹道、妖兽 AI）。注册名即类型 id（{@code strife:spell_projectile} / {@code
 * strife:monster}）；弹道参数与妖兽行为全部在实体/服务类内数据驱动，注册表只留类型。
 */
public final class StrifeCombatEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, StrifeMod.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<SpellProjectile>>
            SPELL_PROJECTILE =
                    ENTITY_TYPES.register(
                            "spell_projectile",
                            () ->
                                    EntityType.Builder.<SpellProjectile>of(
                                                    SpellProjectile::new, MobCategory.MISC)
                                            .sized(0.25f, 0.25f)
                                            .clientTrackingRange(8)
                                            .updateInterval(2)
                                            .build("spell_projectile"));

    public static final DeferredHolder<EntityType<?>, EntityType<StrifeMonster>> MONSTER =
            ENTITY_TYPES.register(
                    "monster",
                    () ->
                            EntityType.Builder.<StrifeMonster>of(
                                            StrifeMonster::new, MobCategory.MONSTER)
                                    .sized(0.9f, 1.4f)
                                    .clientTrackingRange(10)
                                    .build("monster"));

    private StrifeCombatEntities() {}
}

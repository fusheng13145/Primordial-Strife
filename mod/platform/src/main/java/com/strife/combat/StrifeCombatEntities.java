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

    // ── EP1 妖兽种类扩展（ADR-039）：共用 StrifeMonster 类，breed 由 EntityType ID 推导 ──

    public static final DeferredHolder<EntityType<?>, EntityType<StrifeMonster>> WOLF_BEAST =
            registerBeast("wolf_beast", 0.7F, 1.5F);

    public static final DeferredHolder<EntityType<?>, EntityType<StrifeMonster>> ROCK_BEAST =
            registerBeast("rock_beast", 1.2F, 1.8F);

    public static final DeferredHolder<EntityType<?>, EntityType<StrifeMonster>> FIRE_FOX =
            registerBeast("fire_fox", 0.6F, 1.0F);

    public static final DeferredHolder<EntityType<?>, EntityType<StrifeMonster>> ICE_SERPENT =
            registerBeast("ice_serpent", 0.8F, 2.2F);

    public static final DeferredHolder<EntityType<?>, EntityType<StrifeMonster>> SHADOW_LEOPARD =
            registerBeast("shadow_leopard", 0.7F, 1.4F);

    public static final DeferredHolder<EntityType<?>, EntityType<StrifeMonster>> TREANT =
            registerBeast("treant", 1.4F, 3.0F);

    private static DeferredHolder<EntityType<?>, EntityType<StrifeMonster>> registerBeast(
            String id, float width, float height) {
        return ENTITY_TYPES.register(
                id,
                () ->
                        EntityType.Builder.<StrifeMonster>of(
                                        StrifeMonster::new, MobCategory.MONSTER)
                                .sized(width, height)
                                .build(id));
    }

    /** 供奉武士（EP1，ADR-037 ②）：雇佣友方——跟随+攻击雇主目标。 */
    public static final DeferredHolder<EntityType<?>, EntityType<StrifeMercenary>> MERCENARY =
            ENTITY_TYPES.register(
                    "mercenary",
                    () ->
                            EntityType.Builder.<StrifeMercenary>of(
                                            StrifeMercenary::new, MobCategory.CREATURE)
                                    .sized(0.6F, 1.8F)
                                    .build("mercenary"));

    private StrifeCombatEntities() {}
}

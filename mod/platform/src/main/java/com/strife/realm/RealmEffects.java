package com.strife.realm;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.strife.core.StrifeMod;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 负面状态（docs/05 §4：大限虚弱、渡劫重伤）。两个 id 都是 NUMBERS 里点名的键（{@code @@lifespan.dasheng_debuff_key} 与
 * {@code @@breakthrough_cost.tribulation_debuff_key}），代码里只登记实现，键名与数值一律来自真相源。
 *
 * <p>数值来源说明：属性修饰符必须<b>在效果构造时</b>确定，而那一刻数据包还没加载（注册期早于 datapack），ResourceManager 拿不到产物。因此这里读<b>自己 jar
 * 里的同一份产物</b>（classpath 资源）——产物本身就是 DataGen 的确定性输出，读它与读数据包 是同一份真相，区别只是可用时机。读不到就 fail-fast：MOD
 * 起不来，而不是默默给一个没有数值的 Debuff。
 *
 * <p>"全属性 -30%"的落地范围见 {@link #AFFECTED}；含最大生命是安全的——原版在 MAX_HEALTH 变化时会夹血 （{@code
 * LivingEntity#onAttributeUpdated}，1.21.1 源码 net/minecraft/world/entity/LivingEntity.java:1078）。
 */
public final class RealmEffects {

    /** NUMBERS {@code @@lifespan.dasheng_debuff_key}。 */
    public static final String WEAK_ID = "debuff_weak";

    /** NUMBERS {@code @@breakthrough_cost.tribulation_debuff_key}。 */
    public static final String HEAVY_WOUND_ID = "debuff_heavy_wound";

    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, StrifeMod.MOD_ID);

    /** 大限虚弱：寿元耗尽后的可恢复状态（05 §4，绝不删角色）。 */
    public static final DeferredHolder<MobEffect, MobEffect> WEAK =
            EFFECTS.register(WEAK_ID, () -> new StatDebuff(WEAK_ID, 0x6E6A8A));

    /** 渡劫/突破失败的重伤：全属性下调 + 限时（05 §4）。 */
    public static final DeferredHolder<MobEffect, MobEffect> HEAVY_WOUND =
            EFFECTS.register(HEAVY_WOUND_ID, () -> new StatDebuff(HEAVY_WOUND_ID, 0x8A3A3A));

    /** "全属性"的落地清单：战斗与机动相关的六项 + 最大生命。刻意不含视野/交互距离这类外设属性——调低它们只会让玩家 觉得"游戏坏了"，而不是"我受伤了"。 */
    private static final List<Holder<Attribute>> AFFECTED =
            List.of(
                    Attributes.ATTACK_DAMAGE,
                    Attributes.ATTACK_SPEED,
                    Attributes.MOVEMENT_SPEED,
                    Attributes.ARMOR,
                    Attributes.ARMOR_TOUGHNESS,
                    Attributes.BLOCK_BREAK_SPEED,
                    Attributes.MAX_HEALTH);

    private RealmEffects() {}

    /** 把负面状态加到玩家身上（时长来自 NUMBERS，调用方负责换算成刻）。 */
    public static void apply(
            ServerPlayer player, DeferredHolder<MobEffect, MobEffect> effect, long durationTicks) {
        int duration = (int) Math.max(1L, Math.min(Integer.MAX_VALUE, durationTicks));
        player.addEffect(new MobEffectInstance(effect, duration, 0, false, true, true));
    }

    /** 效果名读的是 {@code effect.strife.<id>}（JSON_SCHEMA §4.10 的 lang 前缀口径）。 */
    public static String langKey(String effectId) {
        return "effect.strife." + effectId;
    }

    /** 从自己 jar 里的 DataGen 产物读 {@code debuff_all_stat_delta}。注册期没有 ResourceManager，见类注。 */
    private static double statDeltaFromProduct() {
        String path = "/data/strife/strife_realms/rules.json";
        try (InputStream stream = RealmEffects.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException(
                        path + " 不在 classpath 上——content-base 产物没有打进 MOD（docs/04 §1）");
            }
            JsonObject rules =
                    JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                            .getAsJsonObject();
            return rules.getAsJsonObject("breakthrough_cost")
                    .get("debuff_all_stat_delta")
                    .getAsDouble();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + path, e);
        }
    }

    /** 一个按 NUMBERS 比例下调属性的负面状态。 */
    private static final class StatDebuff extends MobEffect {

        StatDebuff(String effectId, int color) {
            super(MobEffectCategory.HARMFUL, color);
            double delta = statDeltaFromProduct();
            for (Holder<Attribute> attribute : AFFECTED) {
                // 修饰符 id 必须按"效果 + 属性"唯一：两个效果共用同一个 id 时，后加的会顶掉先加的
                // （属性修饰符以 id 为键），玩家同时中虚弱与重伤时就只剩一个生效。
                addAttributeModifier(
                        attribute,
                        ResourceLocation.fromNamespaceAndPath(
                                StrifeMod.MOD_ID, effectId + "/" + attributePath(attribute)),
                        delta,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            }
        }

        private static String attributePath(Holder<Attribute> attribute) {
            return attribute
                    .unwrapKey()
                    .map(key -> key.location().getPath())
                    .orElse("unknown_attribute");
        }
    }
}

package com.strife.combat;

import com.strife.combat.Artifacts.Contract;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 法宝副手属性光环（P1-01，ADR-031 ③）：副手装备法宝时按品阶应用 NUMBERS {@code artifact_bonus_*} 的加成，卸下 / 换品阶即撤。
 *
 * <p><b>幂等对账</b>：每 20 拍按「期望签名 vs 已应用签名」diff 一次，只增删变化的 modifier——装备栏在 两拍之间怎么折腾都不会叠加。modifier 用
 * {@code addTransientModifier}（不入档，重登重算），登录/登出 即清账，跨存档零残留。品阶未知 / 表缺失回零加成（fail-soft，属性栏不是崩点）。
 *
 * <p>数值语义：{@code attack_pct} → 攻击伤害 {@code ADD_MULTIPLIED_BASE}（乘基础攻击）， {@code health_flat} → 生命上限
 * {@code ADD_VALUE}（平加）。
 */
public final class ArtifactAura {

    private static final Logger LOGGER = LoggerFactory.getLogger("strife/combat");
    private static final ResourceLocation ATTACK_ID =
            ResourceLocation.fromNamespaceAndPath("strife", "artifact_bonus_attack");
    private static final ResourceLocation HEALTH_ID =
            ResourceLocation.fromNamespaceAndPath("strife", "artifact_bonus_health");
    private static final int REFRESH_INTERVAL_TICKS = 20;

    private static final Map<UUID, Applied> APPLIED = new HashMap<>();

    private record Applied(AttributeModifier attack, AttributeModifier health, String signature) {}

    private ArtifactAura() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(ArtifactAura::onTick);
        NeoForge.EVENT_BUS.addListener(ArtifactAura::onLoggedOut);
        NeoForge.EVENT_BUS.addListener(ArtifactAura::onServerStopping);
        LOGGER.info("strife combat artifact aura registered");
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.tickCount % REFRESH_INTERVAL_TICKS != 0) {
            return;
        }
        refresh(player);
    }

    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        APPLIED.remove(event.getEntity().getUUID());
    }

    private static void onServerStopping(
            net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        APPLIED.clear();
    }

    /** 期望加成：副手法宝品阶 → NUMBERS 值；非法宝/无品阶回零。包内可见（用例直接喂）。 */
    static CombatTables.ArtifactBonus desired(ServerPlayer player) {
        ItemStack offhand = player.getOffhandItem();
        CustomData custom = offhand.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        Contract contract = Artifacts.readContract(custom == null ? null : custom.copyTag());
        if (contract == null || contract.quality() == null || contract.quality().isBlank()) {
            return CombatTables.ArtifactBonus.zero();
        }
        // ADR-036 法器分级：生效加成 = 品阶加成 × 阶位倍率。
        return CombatTables.effectiveBonus(contract.quality());
    }

    private static void refresh(ServerPlayer player) {
        CombatTables.ArtifactBonus bonus = desired(player);
        String signature = bonus.attackPct() + "/" + bonus.healthFlat();
        Applied applied = APPLIED.get(player.getUUID());
        if (applied != null && applied.signature().equals(signature)) {
            return;
        }
        if (applied != null) {
            removeModifier(player, Attributes.ATTACK_DAMAGE, applied.attack());
            removeModifier(player, Attributes.MAX_HEALTH, applied.health());
        }
        boolean zero = bonus.attackPct() == 0.0 && bonus.healthFlat() == 0.0;
        if (zero) {
            APPLIED.remove(player.getUUID());
            return;
        }
        AttributeModifier attack =
                new AttributeModifier(
                        ATTACK_ID,
                        bonus.attackPct(),
                        AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        AttributeModifier health =
                new AttributeModifier(
                        HEALTH_ID, bonus.healthFlat(), AttributeModifier.Operation.ADD_VALUE);
        addModifier(player, Attributes.ATTACK_DAMAGE, attack);
        addModifier(player, Attributes.MAX_HEALTH, health);
        APPLIED.put(player.getUUID(), new Applied(attack, health, signature));
    }

    private static void addModifier(
            ServerPlayer player,
            net.minecraft.core.Holder<Attribute> attribute,
            AttributeModifier modifier) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(modifier.id());
            instance.addTransientModifier(modifier);
        }
    }

    private static void removeModifier(
            ServerPlayer player,
            net.minecraft.core.Holder<Attribute> attribute,
            AttributeModifier modifier) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(modifier.id());
        }
    }
}

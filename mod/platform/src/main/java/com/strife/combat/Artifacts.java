package com.strife.combat;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.strife.core.SpellState;
import com.strife.core.StrifeAttachmentTypes;
import com.strife.core.StrifeData;
import com.strife.core.StrifeTime;
import com.strife.core.net.IntentRateLimiter;
import com.strife.core.net.StrifeIntents;
import com.strife.realm.UnlockBits;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 法宝主动使用（docs/07 §7 M2；器胚由 production 写成 {@code CustomData} 契约）。
 *
 * <p>槽位取副手：不新增常驻玩家状态、不引入 Accessories 软依赖，交换副手（默认 F）即换法宝。判定链全部服务端：契约 → 槽位 → 主动技能 → {@code
 * artifact_slot} 解锁 → 法术存在 → 修为 → 法宝/法术双冷却。主动技能的法术产物由 combat 自己查表， production 只在炼器时写入"是哪个法术"。
 */
public final class Artifacts {

    static final String ARTIFACT_ID_TAG = "artifact_id";
    static final String QUALITY_TAG = "quality";
    static final String SLOT_TYPE_TAG = "slot_type";
    static final String ACTIVE_SPELL_TAG = "active_spell";
    static final String DURABILITY_TAG = "durability";
    static final String MAX_DURABILITY_TAG = "max_durability";

    private static final String SLOT_TYPE_TREASURE = "treasure";
    private static final String SLOT_UNLOCK = "artifact_slot";

    private static final Logger LOGGER = LoggerFactory.getLogger("strife/combat");

    /** 器胚 {@code CustomData} 的只读视图；缺 {@code artifact_id} 时整体为 null。 */
    public record Contract(
            String artifactId,
            String quality,
            String slotType,
            String activeSpell,
            int durability,
            int maxDurability) {}

    enum Rejection {
        NO_ARTIFACT,
        WRONG_SLOT,
        BROKEN,
        NO_ACTIVE_SKILL,
        NOT_UNLOCKED,
        UNKNOWN_SPELL,
        NO_EFFECT,
        NOT_ENOUGH_QI,
        ARTIFACT_ON_COOLDOWN,
        SPELL_ON_COOLDOWN
    }

    private Artifacts() {}

    /** combat 入口构造期调用：登记 {@code artifact} 意图（限速由 core 规则表给）。 */
    public static synchronized void register() {
        StrifeIntents.register(IntentRateLimiter.ARTIFACT, Artifacts::onIntent);
        LOGGER.info("strife combat artifact intent registered");
    }

    /** 从器胚标签读契约；不是法宝返回 null（武器/丹药的 CustomData 不应被误认）。 */
    public static Contract readContract(CompoundTag tag) {
        if (tag == null || !tag.contains(ARTIFACT_ID_TAG)) {
            return null;
        }
        return new Contract(
                tag.getString(ARTIFACT_ID_TAG),
                tag.getString(QUALITY_TAG),
                tag.getString(SLOT_TYPE_TAG),
                tag.getString(ACTIVE_SPELL_TAG),
                readDurability(tag),
                readMaxDurability(tag));
    }

    static int readDurability(CompoundTag tag) {
        return tag == null ? 0 : tag.getInt(DURABILITY_TAG);
    }

    static int readMaxDurability(CompoundTag tag) {
        return tag == null ? 0 : tag.getInt(MAX_DURABILITY_TAG);
    }

    /** 成功激发一次后的耐久标签；不改原标签。旧器胚无耐久记录时原样返回。 */
    static CompoundTag consumeDurability(CompoundTag tag) {
        CompoundTag copy = tag.copy();
        if (readMaxDurability(copy) <= 0) {
            return copy;
        }
        copy.putInt(DURABILITY_TAG, Math.max(0, readDurability(copy) - 1));
        return copy;
    }

    /** 使用判定链；返回 null 表示可施放。 */
    static Rejection check(
            Contract contract,
            boolean slotUnlocked,
            SpellTables.SpellSpec spec,
            long qi,
            boolean artifactOnCooldown,
            boolean spellOnCooldown) {
        if (contract == null) {
            return Rejection.NO_ARTIFACT;
        }
        if (!SLOT_TYPE_TREASURE.equals(contract.slotType())) {
            return Rejection.WRONG_SLOT;
        }
        if (contract.maxDurability() > 0 && contract.durability() <= 0) {
            return Rejection.BROKEN;
        }
        if (contract.activeSpell().isEmpty()) {
            return Rejection.NO_ACTIVE_SKILL;
        }
        if (!slotUnlocked) {
            return Rejection.NOT_UNLOCKED;
        }
        if (spec == null) {
            return Rejection.UNKNOWN_SPELL;
        }
        if (!spec.isProjectile()) {
            return Rejection.NO_EFFECT;
        }
        if (qi < spec.costQi()) {
            return Rejection.NOT_ENOUGH_QI;
        }
        if (artifactOnCooldown) {
            return Rejection.ARTIFACT_ON_COOLDOWN;
        }
        if (spellOnCooldown) {
            return Rejection.SPELL_ON_COOLDOWN;
        }
        return null;
    }

    static String cooldownKey(String artifactId) {
        return "artifact:" + artifactId;
    }

    static long cooldownUntil(long nowTick, int ticksPerSecond, double cooldownSec) {
        return nowTick + Math.round(cooldownSec * Math.max(1, ticksPerSecond));
    }

    /** 使用副手法宝（服务端主线程；命令与 {@code artifact} 意图共用）。 */
    public static void cast(ServerPlayer player) {
        ItemStack offhand = player.getOffhandItem();
        CustomData custom = offhand.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = custom == null ? null : custom.copyTag();
        Contract contract = readContract(tag);
        StrifeData data = player.getData(StrifeAttachmentTypes.PLAYER_DATA);
        long now = player.level().getGameTime();
        SpellTables.SpellSpec spec =
                contract == null ? null : Spells.tables().spell(contract.activeSpell());
        String artifactKey = contract == null ? "" : cooldownKey(contract.artifactId());
        Rejection rejection =
                check(
                        contract,
                        UnlockBits.has(data.flags(), SLOT_UNLOCK),
                        spec,
                        data.qi(),
                        contract != null && data.spells().onCooldown(artifactKey, now),
                        spec != null && data.spells().onCooldown(spec.id(), now));
        if (rejection != null) {
            player.displayClientMessage(
                    Component.literal("（" + describe(rejection, data.qi(), spec) + "）"), true);
            return;
        }
        if (spec == null) {
            return; // check() 已覆盖 UNKNOWN_SPELL，这里只收窄类型
        }
        if (player.level() instanceof ServerLevel level
                && Spells.countAlive(level) >= SpellProjectile.MAX_ALIVE) {
            player.displayClientMessage(Component.literal("（灵气扰动过强，法宝暂无法激发）"), true);
            return;
        }

        data = data.withQi(data.qi() - spec.costQi());
        data =
                data.withSpells(
                        data.spells()
                                .withCooldown(
                                        artifactKey,
                                        cooldownUntil(
                                                now,
                                                StrifeTime.TICKS_PER_SECOND,
                                                Spells.tables().rules().artifactCooldownSec()))
                                .withCooldown(
                                        spec.id(),
                                        SpellMath.cooldownUntil(
                                                spec, now, StrifeTime.TICKS_PER_SECOND)));
        player.setData(StrifeAttachmentTypes.PLAYER_DATA, data);
        Spells.fire(player, spec);
        if (tag != null && contract.maxDurability() > 0) {
            offhand.set(DataComponents.CUSTOM_DATA, CustomData.of(consumeDurability(tag)));
        }
    }

    private static String describe(Rejection rejection, long qi, SpellTables.SpellSpec spec) {
        return switch (rejection) {
            case NO_ARTIFACT -> "副手未持法宝（按 F 交换副手）";
            case WRONG_SLOT -> "该法宝需要「法宝」槽，副手暂只承载法宝类";
            case BROKEN -> "法宝已损毁（耐久 0，需修复）";
            case NO_ACTIVE_SKILL -> "此法宝无主动之能";
            case NOT_UNLOCKED -> "尚未领悟法宝御使之法（金丹解锁）";
            case UNKNOWN_SPELL -> "法宝主动技能缺失";
            case NO_EFFECT -> "该法宝主动技能尚无弹道形态";
            case NOT_ENOUGH_QI -> "修为不足（现有 " + qi + "，需 " + spec.costQi() + "）";
            case ARTIFACT_ON_COOLDOWN -> "法宝尚在冷却";
            case SPELL_ON_COOLDOWN -> "该法术尚在冷却";
        };
    }

    private static void onIntent(StrifeIntents.Invocation invocation) {
        ServerPlayer player = invocation.player();
        if (player.level().isClientSide) {
            return;
        }
        CompoundTag args = invocation.args();
        String action =
                args == null || !args.contains("action") ? "cast" : args.getString("action");
        switch (action) {
            case "cast" -> cast(player);
            case "info" -> info(player);
            default -> LOGGER.debug("artifact intent: unknown action '{}'", action);
        }
    }

    private static void info(ServerPlayer player) {
        ItemStack offhand = player.getOffhandItem();
        CustomData custom = offhand.get(DataComponents.CUSTOM_DATA);
        Contract contract = readContract(custom == null ? null : custom.copyTag());
        if (contract == null) {
            player.displayClientMessage(Component.literal("（副手未持法宝；按 F 交换副手）"), false);
            return;
        }
        StrifeData data = player.getData(StrifeAttachmentTypes.PLAYER_DATA);
        long now = player.level().getGameTime();
        player.displayClientMessage(
                Component.literal("法宝：")
                        .append(Component.translatable("artifact.strife." + contract.artifactId()))
                        .append("（")
                        .append(Component.translatable("quality.strife." + contract.quality()))
                        .append("）"),
                false);
        CombatTables.ArtifactTier tier = CombatTables.artifactTier(contract.quality());
        if (tier.present()) {
            player.displayClientMessage(
                    Component.literal(
                            "  阶位：" + tier.displayName() + "（属性 ×" + tier.multiplier() + "）"),
                    false);
        }
        player.displayClientMessage(
                Component.literal(
                        "  槽位：副手（"
                                + slotLabel(contract.slotType())
                                + "）；法宝槽"
                                + (UnlockBits.has(data.flags(), SLOT_UNLOCK) ? "已开" : "未开（金丹解锁）")),
                false);
        player.displayClientMessage(
                Component.literal(
                        contract.maxDurability() > 0
                                ? "  耐久：" + contract.durability() + " / " + contract.maxDurability()
                                : "  耐久：未记录（旧器胚）"),
                false);
        if (contract.activeSpell().isEmpty()) {
            player.displayClientMessage(Component.literal("  主动：无"), false);
            return;
        }
        SpellTables.SpellSpec spec = Spells.tables().spell(contract.activeSpell());
        if (spec == null) {
            player.displayClientMessage(
                    Component.literal("  主动：" + contract.activeSpell() + "（法术缺失）"), false);
            return;
        }
        long remaining =
                Math.max(
                        remainingTicks(data.spells(), cooldownKey(contract.artifactId()), now),
                        remainingTicks(data.spells(), spec.id(), now));
        player.displayClientMessage(activeLine(spec, remaining), false);
    }

    static String slotLabel(String slotType) {
        return switch (slotType) {
            case "treasure" -> "法宝";
            case "weapon" -> "兵器";
            case "armor" -> "护具";
            default -> slotType;
        };
    }

    /** 技能名必须是 translatable 子组件：在服务端 {@code getString()} 会按服务端语言解析成英文。 */
    static Component activeLine(SpellTables.SpellSpec spec, long remainingTicks) {
        String state =
                remainingTicks > 0
                        ? String.format(
                                "剩余冷却 %.1f 秒",
                                remainingTicks / (double) StrifeTime.TICKS_PER_SECOND)
                        : "可激发";
        return Component.literal("  主动：")
                .append(Component.translatable("spell.strife." + spec.id()))
                .append(Component.literal("（修为 " + spec.costQi() + "，" + state + "）"));
    }

    private static long remainingTicks(SpellState spells, String key, long now) {
        Long until = spells.cooldowns().get(key);
        return until == null ? 0 : Math.max(0, until - now);
    }

    /** {@code /strife artifact info|cast}：副手槽的兜底入口（法宝 GUI/轮盘属后续迭代）。 */
    static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("artifact")
                .then(
                        Commands.literal("info")
                                .executes(
                                        context -> {
                                            ServerPlayer player = context.getSource().getPlayer();
                                            if (player == null) {
                                                context.getSource()
                                                        .sendFailure(
                                                                Component.literal(
                                                                        "artifact info 需要玩家执行"));
                                                return 0;
                                            }
                                            info(player);
                                            return 1;
                                        }))
                .then(
                        Commands.literal("cast")
                                .executes(
                                        context -> {
                                            ServerPlayer player = context.getSource().getPlayer();
                                            if (player == null) {
                                                context.getSource()
                                                        .sendFailure(
                                                                Component.literal(
                                                                        "artifact cast 需要玩家执行"));
                                                return 0;
                                            }
                                            cast(player);
                                            return 1;
                                        }));
    }
}

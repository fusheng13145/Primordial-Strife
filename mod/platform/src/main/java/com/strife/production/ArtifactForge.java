package com.strife.production;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.strife.core.InventoryOps;
import com.strife.core.StrifeAttachmentTypes;
import com.strife.core.StrifeData;
import com.strife.realm.RealmTables;
import java.util.Random;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * 炼器服务（统一配方机第二类：器图，docs/07 §7 M2）。
 *
 * <p>一次炼器：器图存在 → 境界校验 → 实扣材料 → 品质抽签 → 产出带契约与满耐久的器胚；修复见 {@link ArtifactRepair}。
 */
public final class ArtifactForge {

    static final String ARTIFACT_ID_TAG = "artifact_id";
    static final String QUALITY_TAG = "quality";
    static final String SLOT_TYPE_TAG = "slot_type";
    static final String ACTIVE_SPELL_TAG = "active_spell";
    static final String DURABILITY_TAG = "durability";
    static final String MAX_DURABILITY_TAG = "max_durability";

    private ArtifactForge() {}

    /** 一次炼器（服务端主线程；命令与未来炼器 UI 共用）。 */
    public static void craft(ServerPlayer player, String artifactId) {
        ArtifactRecipe recipe = ArtifactTables.recipes().get(artifactId);
        if (recipe == null) {
            player.displayClientMessage(Component.literal("（未知器图：" + artifactId + "）"), true);
            return;
        }

        StrifeData data = player.getData(StrifeAttachmentTypes.PLAYER_DATA);
        RealmTables tables = RealmTables.get(player.server);
        RealmTables.RealmEntry required = tables.realmById(recipe.requiredRealm());
        if (required == null) {
            player.displayClientMessage(
                    Component.literal("（器图 required_realm 未知：" + recipe.requiredRealm() + "）"),
                    true);
            return;
        }
        if (!realmAllows(data.realmOrdinal(), required)) {
            RealmTables.RealmEntry current = tables.realm(data.realmOrdinal());
            player.displayClientMessage(
                    Component.literal("（境界不足：需 ")
                            .append(realmName(required.id()))
                            .append("，当前 ")
                            .append(
                                    current == null
                                            ? Component.literal("?")
                                            : realmName(current.id()))
                            .append("）"),
                    true);
            return;
        }

        InventoryOps.PlayerContainers containers =
                new InventoryOps.PlayerContainers(
                        player.getInventory(), player.getEnderChestInventory());
        for (AlchemyRecipe.Material material : recipe.consumePlan()) {
            long owned = InventoryOps.countOwned(containers, material.itemId());
            if (owned < material.count()) {
                player.displayClientMessage(
                        Component.literal(
                                "（材料不足："
                                        + material.itemId()
                                        + " 现有 "
                                        + owned
                                        + "，需 "
                                        + material.count()
                                        + "）"),
                        true);
                return;
            }
        }
        for (AlchemyRecipe.Material material : recipe.consumePlan()) {
            long removed = InventoryOps.remove(containers, material.itemId(), material.count());
            if (removed != material.count()) {
                throw new IllegalStateException(
                        "artifact material removal mismatch: "
                                + material.itemId()
                                + " want "
                                + material.count()
                                + " got "
                                + removed);
            }
        }

        String quality = recipe.rollQuality(new Random()::nextInt);
        Item blank = InventoryOps.resolve(recipe.blank()).orElse(null);
        if (blank == null) {
            player.displayClientMessage(
                    Component.literal("（器胚 " + recipe.blank() + " 尚未注册）"), true);
            return;
        }
        ItemStack stack = new ItemStack(blank);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(artifactTags(recipe, quality)));
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        player.displayClientMessage(
                Component.literal("✦ 器成：")
                        .append(Component.translatable("artifact.strife." + recipe.id()))
                        .append("（")
                        .append(qualityName(quality))
                        .append("）")
                        .withStyle(ChatFormatting.GOLD),
                false);
    }

    /** 器胚数据契约：combat 只认这些键，不反向 import production 的运行时表；耐久由 combat 扣、production 修。 */
    static CompoundTag artifactTags(ArtifactRecipe recipe, String quality) {
        CompoundTag tag = new CompoundTag();
        tag.putString(ARTIFACT_ID_TAG, recipe.id());
        tag.putString(QUALITY_TAG, quality);
        tag.putString(SLOT_TYPE_TAG, recipe.slotType());
        if (recipe.activeSkill() != null) {
            tag.putString(ACTIVE_SPELL_TAG, recipe.activeSkill().spellId());
        }
        tag.putInt(DURABILITY_TAG, recipe.durabilityPoints());
        tag.putInt(MAX_DURABILITY_TAG, recipe.durabilityPoints());
        return tag;
    }

    /** 境界门禁：required 为空（器图引用了不存在的境界）时 fail-closed。 */
    static boolean realmAllows(int playerOrdinal, RealmTables.RealmEntry required) {
        return required != null && playerOrdinal >= required.ordinal();
    }

    static Component realmName(String realmId) {
        return Component.translatable("realm.strife." + realmId);
    }

    static Component qualityName(String tier) {
        return Component.translatable("quality.strife." + tier);
    }

    /** {@code /strife recipe artifact list|show}：挂在 {@link Alchemy#recipeCommand()} 的 recipe 根下。 */
    public static LiteralArgumentBuilder<CommandSourceStack> recipeBranch() {
        return Commands.literal("artifact")
                .then(
                        Commands.literal("list")
                                .executes(
                                        context -> {
                                            for (ArtifactRecipe recipe :
                                                    ArtifactTables.recipes().values()) {
                                                context.getSource()
                                                        .sendSuccess(
                                                                () ->
                                                                        Component.literal(
                                                                                recipe.id()
                                                                                        + "（需 "
                                                                                        + recipe
                                                                                                .requiredRealm()
                                                                                        + "，品质档 "
                                                                                        + recipe.qualityProbs()
                                                                                                .size()
                                                                                        + "，耐久 "
                                                                                        + recipe
                                                                                                .durabilityPoints()
                                                                                        + "）"),
                                                                false);
                                            }
                                            return ArtifactTables.recipes().size();
                                        }))
                .then(
                        Commands.literal("show")
                                .then(
                                        Commands.argument("artifactId", StringArgumentType.word())
                                                .executes(
                                                        context -> {
                                                            CommandSourceStack source =
                                                                    context.getSource();
                                                            String artifactId =
                                                                    StringArgumentType.getString(
                                                                            context, "artifactId");
                                                            ArtifactRecipe recipe =
                                                                    ArtifactTables.recipes()
                                                                            .get(artifactId);
                                                            if (recipe == null) {
                                                                source.sendFailure(
                                                                        Component.literal(
                                                                                "（未知器图："
                                                                                        + artifactId
                                                                                        + "）"));
                                                                return 0;
                                                            }
                                                            source.sendSuccess(
                                                                    () ->
                                                                            Component.literal(
                                                                                    "器图 "
                                                                                            + artifactId
                                                                                            + "（需 "
                                                                                            + recipe
                                                                                                    .requiredRealm()
                                                                                            + "，耐久 "
                                                                                            + recipe
                                                                                                    .durabilityPoints()
                                                                                            + "）"),
                                                                    false);
                                                            source.sendSuccess(
                                                                    () ->
                                                                            Component.literal(
                                                                                    "  器胚 "
                                                                                            + recipe
                                                                                                    .blank()
                                                                                            + " / 核心 "
                                                                                            + recipe
                                                                                                    .coreSlot()),
                                                                    false);
                                                            StringBuilder consume =
                                                                    new StringBuilder();
                                                            for (AlchemyRecipe.Material material :
                                                                    recipe.consumePlan()) {
                                                                if (!consume.isEmpty()) {
                                                                    consume.append("、");
                                                                }
                                                                consume.append(material.itemId())
                                                                        .append(" ×")
                                                                        .append(material.count());
                                                            }
                                                            source.sendSuccess(
                                                                    () ->
                                                                            Component.literal(
                                                                                    "  实扣 "
                                                                                            + consume),
                                                                    false);
                                                            StringBuilder quality =
                                                                    new StringBuilder();
                                                            for (ArtifactRecipe.Quality entry :
                                                                    recipe.qualityProbs()) {
                                                                if (!quality.isEmpty()) {
                                                                    quality.append(" / ");
                                                                }
                                                                quality.append(entry.tier())
                                                                        .append(" ")
                                                                        .append(
                                                                                String.format(
                                                                                        "%.2f",
                                                                                        entry
                                                                                                .prob()));
                                                            }
                                                            source.sendSuccess(
                                                                    () ->
                                                                            Component.literal(
                                                                                    "  品质 "
                                                                                            + quality),
                                                                    false);
                                                            return 1;
                                                        })));
    }

    /** 器胚 tooltip：物品名下方补「法宝：<名>（<品阶>）」；未鉴定短路（只露名字与「未鉴定」）。 */
    /** 是否器胚（CustomData 带 artifact_id）——测试与鉴定路径共用。 */
    public static boolean hasArtifactTag(ItemStack stack) {
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        return custom != null && custom.copyTag().contains(ARTIFACT_ID_TAG);
    }

    public static void onTooltip(ItemTooltipEvent event) {
        CustomData custom = event.getItemStack().get(DataComponents.CUSTOM_DATA);
        if (custom == null) {
            return;
        }
        CompoundTag tag = custom.copyTag();
        if (!tag.contains(ARTIFACT_ID_TAG)) {
            return;
        }
        Component artifact =
                Component.translatable("artifact.strife." + tag.getString(ARTIFACT_ID_TAG));
        if (com.strife.core.ArtifactTags.isUnidentified(event.getItemStack())) {
            event.getToolTip()
                    .add(
                            Component.translatable(
                                    "tooltip.strife.artifact", artifact, Component.literal("未鉴定")));
            return;
        }
        Component quality =
                tag.contains(QUALITY_TAG)
                        ? qualityName(tag.getString(QUALITY_TAG))
                        : Component.literal("?");
        event.getToolTip()
                .add(Component.translatable("tooltip.strife.artifact", artifact, quality));
        if (tag.contains(ACTIVE_SPELL_TAG)) {
            event.getToolTip()
                    .add(
                            Component.translatable(
                                    "tooltip.strife.artifact.active",
                                    Component.translatable(
                                            "spell.strife." + tag.getString(ACTIVE_SPELL_TAG))));
        }
        if (tag.contains(MAX_DURABILITY_TAG)) {
            event.getToolTip()
                    .add(
                            Component.translatable(
                                    "tooltip.strife.artifact.durability",
                                    tag.getInt(DURABILITY_TAG),
                                    tag.getInt(MAX_DURABILITY_TAG)));
        }
    }
}

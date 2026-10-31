package com.strife.production;

import com.strife.core.InventoryOps;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** 法宝修复：消耗器图核心材料恢复耐久（归属 production，器胚契约由 production 维护）。 */
public final class ArtifactRepair {

    private ArtifactRepair() {}

    static boolean needsRepair(int durability, int maxDurability) {
        return maxDurability > 0 && durability < maxDurability;
    }

    static CompoundTag restored(CompoundTag tag) {
        CompoundTag copy = tag.copy();
        copy.putInt(ArtifactForge.DURABILITY_TAG, copy.getInt(ArtifactForge.MAX_DURABILITY_TAG));
        return copy;
    }

    static String repairCore(CompoundTag tag) {
        if (tag == null || !tag.contains(ArtifactForge.ARTIFACT_ID_TAG)) {
            return null;
        }
        ArtifactRecipe recipe =
                ArtifactTables.recipes().get(tag.getString(ArtifactForge.ARTIFACT_ID_TAG));
        return recipe == null ? null : recipe.coreSlot();
    }

    /** 修复副手法宝：一次消耗 1 个 core_slot 材料，耐久回满。 */
    public static void repair(ServerPlayer player) {
        ItemStack offhand = player.getOffhandItem();
        CustomData custom = offhand.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = custom == null ? null : custom.copyTag();
        if (tag == null || !tag.contains(ArtifactForge.ARTIFACT_ID_TAG)) {
            player.displayClientMessage(Component.literal("（副手未持法宝；按 F 交换副手）"), true);
            return;
        }
        int durability = tag.getInt(ArtifactForge.DURABILITY_TAG);
        int maxDurability = tag.getInt(ArtifactForge.MAX_DURABILITY_TAG);
        if (maxDurability <= 0) {
            player.displayClientMessage(Component.literal("（旧器胚无耐久记录，无需修复）"), true);
            return;
        }
        if (!needsRepair(durability, maxDurability)) {
            player.displayClientMessage(Component.literal("（法宝完好，无需修复）"), true);
            return;
        }
        String core = repairCore(tag);
        if (core == null || core.isEmpty()) {
            player.displayClientMessage(Component.literal("（器图缺失，无法修复）"), true);
            return;
        }
        InventoryOps.PlayerContainers containers =
                new InventoryOps.PlayerContainers(
                        player.getInventory(), player.getEnderChestInventory());
        if (InventoryOps.countOwned(containers, core) < 1) {
            player.displayClientMessage(Component.literal("（修复材料不足：" + core + " ×1）"), true);
            return;
        }
        long removed = InventoryOps.remove(containers, core, 1);
        if (removed != 1) {
            throw new IllegalStateException("artifact repair material removal mismatch: " + core);
        }
        offhand.set(DataComponents.CUSTOM_DATA, CustomData.of(restored(tag)));
        player.displayClientMessage(
                Component.literal("✦ 法宝已修复（耐久 " + maxDurability + " / " + maxDurability + "）"),
                false);
    }
}

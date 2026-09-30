package com.strife.world;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * 草类方块的草药掉落（序章 #3 凝须草 / #8 断血草 的采集来源，MVP 快速通道——<b>待 A 评审</b>）。
 *
 * <p>概率表来自 {@link WorldTables}（NUMBERS @@world herb_grass_drop_prob），键即物品内容 ID——采集
 * 数值零字面量。事件式追加掉落而非覆盖原版 loot_table：保留草丛的小麦种子掉落，不动原版产物。概率与 数值口径待 C 审定后可只改 NUMBERS.md。
 */
public final class HerbDrops {

    private HerbDrops() {}

    static void onBreakBlock(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockState state = event.getState();
        if (!state.is(Blocks.SHORT_GRASS)
                && !state.is(Blocks.TALL_GRASS)
                && !state.is(Blocks.FERN)) {
            return;
        }
        WorldTables tables = WorldTables.getOrNull(level.getServer());
        if (tables == null) {
            return;
        }
        RandomSource random = level.random;
        BlockPos pos = event.getPos();
        for (Map.Entry<String, Double> entry : tables.herbGrassDropProb().entrySet()) {
            if (random.nextDouble() >= entry.getValue()) {
                continue;
            }
            Item item =
                    BuiltInRegistries.ITEM
                            .getOptional(
                                    ResourceLocation.fromNamespaceAndPath("strife", entry.getKey()))
                            .orElse(null);
            if (item != null) {
                level.addFreshEntity(
                        new ItemEntity(
                                level,
                                pos.getX() + 0.5,
                                pos.getY() + 0.5,
                                pos.getZ() + 0.5,
                                new ItemStack(item)));
            }
        }
    }
}

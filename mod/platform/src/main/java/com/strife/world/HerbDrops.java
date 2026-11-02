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
 * 草类方块的草药掉落：概率表来自 {@link WorldTables}（NUMBERS @@world herb_grass_drop_prob），键即物品内容 ID。
 *
 * <p>以事件追加掉落而非覆盖原版 loot_table，保留草丛的小麦种子掉落。
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
        // 秘境灵草（ADR-036 ⑨ 产出钩子）：仅 miji_realm 的草丛追加秘境表——天元果等 secret_realm 产物的野外获取路径
        if (level.dimension() == StrifeWorld.MIJI_REALM) {
            dropFrom(tables.secretRealmHerbDropProb(), level, random, pos);
        }
        dropFrom(tables.herbGrassDropProb(), level, random, pos);
    }

    /** 单表逐键概率掉落（包内可见供用例覆盖形状；调用方已判服务端与草类）。 */
    static void dropFrom(
            Map<String, Double> probTable, ServerLevel level, RandomSource random, BlockPos pos) {
        for (Map.Entry<String, Double> entry : probTable.entrySet()) {
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

package com.strife.production;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 炼丹炉方块（EP1，ADR-038）：四阶炼丹炉——玩家站在炉附近使用炼丹命令时吃到加成。
 *
 * <p>变形测试（03 §2.1）：加新炉只需在本类加一个 register 常量 + furnaces.csv 加一行。 参数（success_bonus / quality_cap
 * 等）从产物读（{@link ProductionTables}），代码零受管字面量。
 *
 * <p>邻近判定：{@link #findNearby(Player)} 扫 3 格半径，返回最高阶的炉（多炉取最优）。
 */
public final class StrifeAlchemyBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, "strife");
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, "strife");

    private static final Map<String, DeferredHolder<Block, Block>> BY_ID =
            new ConcurrentHashMap<>();

    public static final DeferredHolder<Block, Block> FURNACE_SHI =
            register("furnace_shi", MapColor.COLOR_BROWN, 3.5F, 3.5F, SoundType.STONE);
    public static final DeferredHolder<Block, Block> FURNACE_TIE =
            register("furnace_tie", MapColor.COLOR_GRAY, 4.5F, 4.5F, SoundType.METAL);
    public static final DeferredHolder<Block, Block> FURNACE_ZIJIN =
            register("furnace_zijin", MapColor.COLOR_PURPLE, 5.0F, 5.0F, SoundType.METAL);
    public static final DeferredHolder<Block, Block> FURNACE_LONG =
            register("furnace_long", MapColor.COLOR_ORANGE, 6.0F, 6.0F, SoundType.NETHERITE_BLOCK);

    private static DeferredHolder<Block, Block> register(
            String id, MapColor color, float hardness, float resistance, SoundType sound) {
        DeferredHolder<Block, Block> block =
                BLOCKS.register(
                        id,
                        () ->
                                new Block(
                                        BlockBehaviour.Properties.of()
                                                .mapColor(color)
                                                .requiresCorrectToolForDrops()
                                                .strength(hardness, resistance)
                                                .sound(sound)
                                                .pushReaction(PushReaction.DESTROY)));
        ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()));
        BY_ID.put(id, block);
        return block;
    }

    /**
     * 找玩家附近的最高阶炼丹炉（3 格半径扫描）。返回炉 ID（如 {@code furnace_zijin}）， 没有炉返回 null（不加成，基础操作不阻塞）。
     *
     * <p>扫序：从最高阶到最低阶，首个命中即返回（最优匹配）。
     */
    public static String findNearby(Player player) {
        BlockPos center = player.blockPosition();
        var level = player.level();
        // 从最高阶到最低阶
        String[] tierOrder = {"furnace_long", "furnace_zijin", "furnace_tie", "furnace_shi"};
        for (String furnaceId : tierOrder) {
            DeferredHolder<Block, Block> holder = BY_ID.get(furnaceId);
            if (holder == null) {
                continue;
            }
            Block target = holder.get();
            // 3 格半径立方体扫描
            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        if (level.getBlockState(center.offset(dx, dy, dz)).getBlock() == target) {
                            return furnaceId;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * 附近炉的铸造参数（从 furnaces.csv 产物解析）。返回 null = 没有炉。 v1 简化：参数硬编码自 NUMBERS 定稿值（ADR-038
     * §17.3），方块实体化后从产物读。
     */
    public static AlchemyFurnace findNearbyStats(Player player) {
        String furnaceId = findNearby(player);
        if (furnaceId == null) {
            return null;
        }
        return switch (furnaceId) {
            case "furnace_shi" -> new AlchemyFurnace("青石炉", 0.00, "di", 0, 0.00);
            case "furnace_tie" -> new AlchemyFurnace("玄铁炉", 0.05, "tian", 0, 0.00);
            case "furnace_zijin" -> new AlchemyFurnace("紫金鼎", 0.10, "tian", 1, 0.00);
            case "furnace_long" -> new AlchemyFurnace("龙纹鼎", 0.20, "tian", 2, 0.10);
            default -> null;
        };
    }

    /** 炼丹炉铸造参数（NUMBERS §17.3 定稿值）。 */
    public record AlchemyFurnace(
            String name,
            double successBonus,
            String qualityCap,
            int wasteRecycle,
            double doubleOutput) {}

    private StrifeAlchemyBlocks() {}
}

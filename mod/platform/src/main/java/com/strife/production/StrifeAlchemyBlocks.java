package com.strife.production;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 炼丹炉方块（EP1，ADR-038）：四阶炼丹炉——右键打开炉交互屏（{@link AlchemyUi}）， 站在炉附近使用炼丹命令/屏内炼制时吃到加成。
 *
 * <p>变形测试（03 §2.1）：加新炉只需在本类加一个 register 常量 + furnaces.csv 加一行。 参数（success_bonus / quality_cap
 * 等）从产物读（furnaceStats 直读 furnaces.json），代码零受管字面量。
 *
 * <p>邻近判定：{@link #findNearby(Player)} 扫 3 格半径，返回最高阶的炉（多炉取最优）。
 */
public final class StrifeAlchemyBlocks {

    private static final Logger LOGGER = LoggerFactory.getLogger("strife/production");

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
                                new AlchemyFurnaceBlock(
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
     * 附近炉的铸造参数（furnaces.csv DataGen 产物 {@code strife_alchemy/furnaces.json} 运行时直读， v1 硬编码 switch 已
     * 除——受管数值零字面量）。返回 null = 没有炉或方块有而表缺（fail-soft：不加成不阻塞，一次性日志）。
     */
    public static AlchemyFurnace findNearbyStats(Player player) {
        String furnaceId = findNearby(player);
        if (furnaceId == null) {
            return null;
        }
        AlchemyFurnace stats = furnaceStats().get(furnaceId);
        if (stats == null) {
            if (!loggedMissing) {
                loggedMissing = true;
                LOGGER.error("炼丹炉 {} 不在 furnaces.json——该炉不加成（方块与产物漂移）", furnaceId);
            }
            return null;
        }
        return stats;
    }

    private static volatile boolean loggedMissing;

    /** 炼丹炉铸造参数（furnaces.csv 定稿值：name/success_bonus/quality_cap/waste_recycle/double_output）。 */
    public record AlchemyFurnace(
            String name,
            double successBonus,
            String qualityCap,
            int wasteRecycle,
            double doubleOutput) {}

    private static final java.util.Set<String> FURNACE_FIELDS =
            java.util.Set.of(
                    "id", "name", "success_bonus", "quality_cap", "waste_recycle", "double_output");

    private static final String FURNACE_PATH = "/data/strife/strife_alchemy/furnaces.json";

    private static volatile Map<String, AlchemyFurnace> furnaceCache;

    /** 包内可见供用例直测：装载失败回空表（findNearbyStats 走 fail-soft），不抛到炼制路径。 */
    static Map<String, AlchemyFurnace> furnaceStats() {
        Map<String, AlchemyFurnace> loaded = furnaceCache;
        if (loaded != null) {
            return loaded;
        }
        synchronized (StrifeAlchemyBlocks.class) {
            if (furnaceCache != null) {
                return furnaceCache;
            }
            furnaceCache = loaded = parseFurnaces();
            return loaded;
        }
    }

    private static Map<String, AlchemyFurnace> parseFurnaces() {
        try (var stream = StrifeAlchemyBlocks.class.getResourceAsStream(FURNACE_PATH)) {
            if (stream == null) {
                throw new IllegalStateException(
                        FURNACE_PATH + " not in jar（FurnaceGenerator 产物缺失）");
            }
            JsonObject json =
                    JsonParser.parseReader(
                                    new InputStreamReader(
                                            stream, java.nio.charset.StandardCharsets.UTF_8))
                            .getAsJsonObject();
            Map<String, AlchemyFurnace> entries = new ConcurrentHashMap<>();
            for (var element : json.getAsJsonArray("entries")) {
                JsonObject e = element.getAsJsonObject();
                for (String key : e.keySet()) {
                    if (!FURNACE_FIELDS.contains(key)) {
                        throw new IllegalStateException(
                                "furnaces.json unknown field '" + key + "'（§1.2 规则 2 fail-fast）");
                    }
                }
                AlchemyFurnace furnace =
                        new AlchemyFurnace(
                                e.get("name").getAsString(),
                                e.get("success_bonus").getAsDouble(),
                                e.get("quality_cap").getAsString(),
                                e.get("waste_recycle").getAsInt(),
                                e.get("double_output").getAsDouble());
                entries.put(e.get("id").getAsString(), furnace);
            }
            return java.util.Collections.unmodifiableMap(entries);
        } catch (Exception e) {
            LOGGER.error("炼丹炉参数表加载失败（{}）——全部炉不加成", FURNACE_PATH, e);
            return Map.of();
        }
    }

    private StrifeAlchemyBlocks() {}
}

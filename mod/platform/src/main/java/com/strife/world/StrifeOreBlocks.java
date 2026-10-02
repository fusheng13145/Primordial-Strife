package com.strife.world;

import com.strife.core.StrifeMod;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
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
 * M4 矿石方块与物品注册（docs/03 §1 world 职责"矿石 placement"）。
 *
 * <p><b>为什么方块走代码注册、placement 走数据</b>：契约 §4.8 的 {@code drop_table} 与 {@code placement} 都是 数据（原版 loot
 * 2 表 + 原版 worldgen 目录），而"这个方块什么硬度、什么材质色、掉落自己"是 Java 侧的方块属性， datapack 的 blockstate 表达不了。分工划清后：
 *
 * <ul>
 *   <li><b>内容表</b>（{@code tables/ores.csv}）决定 <i>哪种矿、在哪些群系、什么高度、每区块几脉</i>， 由 {@link
 *       com.strife.tools.datagen.OreGenerator} 落成原版 configured/placed/biome_modifier 三份 JSON；
 *   <li><b>本类</b>决定 <i>方块本身</i>，注册名严格等于内容 ID（ADR-017"内容 ID 即运行时 ID"）。
 * </ul>
 *
 * <p>变形测试（03 §2.1）："矿种从 3 种变 30 种"需要改代码吗？只需要在本类加 30 个 {@code register} 常量
 * （每种一行），世界生成、掉落、模型全部由表与数据驱动自动跟上——生成逻辑零改动。
 *
 * <p>硬度/声音/材质色是<b>材质属性不是受管数值</b>（与 {@code SpellProjectile.MAX_ALIVE=64} 同类）：它们不进
 * NUMBERS、不参与平衡流程，评审时按"像不像矿石"判断。此处显式说明，避免被误当成 06 §5 的字面量违规。
 */
public final class StrifeOreBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, StrifeMod.MOD_ID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, StrifeMod.MOD_ID);

    /** 内容 ID → 方块，供 {@link OreTables} 交叉校验（表里有行、代码没注册 = 真实缺口，不静默跳过）。 */
    private static final Map<String, DeferredHolder<Block, Block>> BY_ID = new LinkedHashMap<>();

    /** 灵玉矿：金属性，常见档（NUMBERS {@code ore_drop_weights.common=70}）。 */
    public static final DeferredHolder<Block, Block> ORE_LINGYU =
            registerOre("block_ore_lingyu", MapColor.METAL, 3.0F, 3.0F, SoundType.COPPER);

    /** 赤炎矿：火属性，稀有档（{@code ore_drop_weights.rare=25}）。 */
    public static final DeferredHolder<Block, Block> ORE_CHIYAN =
            registerOre("block_ore_chiyan", MapColor.COLOR_RED, 4.0F, 4.5F, SoundType.BASALT);

    /** 寒玉矿：水属性，灵品档（{@code ore_drop_weights.spirit=5}，最稀有）。 */
    public static final DeferredHolder<Block, Block> ORE_HANYU =
            registerOre(
                    "block_ore_hanyu", MapColor.COLOR_LIGHT_BLUE, 5.0F, 6.0F, SoundType.AMETHYST);

    private static DeferredHolder<Block, Block> registerOre(
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
        // BlockItem 与 Block 同名同 ID：原版"破坏方块掉落自身"的默认规则按 ID 配对，两边不一致会掉出空气。
        ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()));
        BY_ID.put(id, block);
        return block;
    }

    /** 内容 ID → 方块 holder；未注册返回 null（表里有行但代码没注册常量时的真实状态）。 */
    public static DeferredHolder<Block, Block> holder(String oreId) {
        return BY_ID.get(oreId);
    }

    /** 已注册的矿石内容 ID，供测试与调试遍历。 */
    public static Iterable<String> registeredIds() {
        return Collections.unmodifiableSet(BY_ID.keySet());
    }

    private StrifeOreBlocks() {}
}

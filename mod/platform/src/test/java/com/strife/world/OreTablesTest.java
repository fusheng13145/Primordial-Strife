package com.strife.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.strife.testing.ShippedProducts;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * M4 矿石产物 × 运行时解析器的契约用例（{@code tables/ores.csv} → DataGen 五份产物 → {@code OreTables}）。
 *
 * <p><b>这一层为什么必须有</b>：矿石链的每一环缺失，原版都<b>不报错</b>——找不到 configured feature 就不生成矿， 找不到 loot table
 * 就掉空气。玩家侧的表现是"挖了半天什么都没出"，而开服冒烟、跑单测、跑 Validator 全部照不出来
 * （它们都不验证"世界里到底有没有矿、掉了什么"）。所以这里用真实产物把四个断链面各自钉成红用例：
 *
 * <ol>
 *   <li>产物字段面 —— 解析器读得懂 DataGen 写出的东西（{@code @generated} 之外的键一个不多一个不少）；
 *   <li>引用面 —— {@code drop_table} 与方块注册名一致（否则掉空气）；
 *   <li>原版格式面 —— placed feature 的高度换算与矿脉数取整正确（算错就是矿长在 y=112 的天上或完全不长）；
 *   <li>资产面 —— 每种矿都有方块常量、模型、lang 词条（缺任一项，矿在地里但玩家看不见/拿不到名字）。
 * </ol>
 */
class OreTablesTest {

    /** 三个已落地的矿石内容 ID（tables/ores.csv 当前行集）。 */
    private static final List<String> ORES =
            List.of("block_ore_lingyu", "block_ore_chiyan", "block_ore_hanyu");

    @Test
    void everyShippedOreParsesIntoASpec() {
        List<JsonObject> products = ShippedProducts.allOres();
        assertEquals(
                ORES.size(),
                products.size(),
                "strife_ores 产物数与 ores.csv 行数不符：products=" + products.size());

        for (JsonObject product : products) {
            String id = product.get("id").getAsString();
            OreTables.OreSpec spec = OreTables.parseSpec(product, "strife_ores/" + id + ".json");

            assertEquals(id, spec.id());
            assertFalse(spec.element().isBlank(), id + " 缺 element");
            assertNotNull(spec.placement());
            assertFalse(spec.placement().biomes().isEmpty(), id + " 没有任何群系 = 永不生成");
            assertTrue(spec.densityRatio() > 0.0 && spec.densityRatio() <= 1.0, id + " 密度倍率越界");
        }
    }

    /**
     * 五行属性必须是契约枚举里的值（{@code content/JSON_SCHEMA.md} §1.4）。
     *
     * <p>为什么在这里查而不在 Validator：Validator 按 CSV 查，解析器按 JSON 查，两侧任一漏掉都会让非法属性名进入
     * 运行时，而属性名下游接的是"灵根亲和"判定，错了不会崩、只会算错五行。
     */
    @Test
    void everyOreElementIsOneOfTheFiveContractValues() {
        List<String> legal = List.of("jin", "mu", "shui", "huo", "tu");
        for (JsonObject product : ShippedProducts.allOres()) {
            String id = product.get("id").getAsString();
            String element = product.get("element").getAsString();
            assertTrue(
                    legal.contains(element),
                    id
                            + ".json 的 element='"
                            + element
                            + "' 不在五行枚举 "
                            + legal
                            + "（JSON_SCHEMA §1.4）");
        }
    }

    /**
     * 掉落表路径必须等于 {@code blocks/<方块ID>}——1.21.1 的方块掉落表路径由注册名唯一推导 （{@code BlockBehaviour} 构造器：{@code
     * BuiltInRegistries.BLOCK.getKey(block).withPrefix("blocks/")}， 且 {@code Properties} 无公开
     * lootTable setter、{@code Block.getLootTable()} 为 final）。
     *
     * <p>这条错了的形态是<b>静默掉空气</b>，是本仓最贵的一类缺陷，所以单独钉一条用例。
     */
    @Test
    void dropTableAlwaysPointsAtTheBlocksPrefixedPathOfTheSameBlockId() {
        for (JsonObject product : ShippedProducts.allOres()) {
            String id = product.get("id").getAsString();
            String dropTable = product.get("drop_table").getAsString();

            assertEquals(
                    "blocks/" + id,
                    dropTable,
                    id + ".json 的 drop_table 与方块注册名不一致：原版会找不到表并静默不掉落（掉空气）");

            // 产物里那张表必须真的存在，而不是只在 JSON 里写着路径。
            JsonObject loot = ShippedProducts.lootTable(dropTable);
            assertEquals(
                    "minecraft:block",
                    loot.get("type").getAsString(),
                    dropTable + " 的 type 必须是 minecraft:block");
            assertNotNull(loot.getAsJsonArray("pools"), dropTable + " 缺 pools");
        }
    }

    /** 掉落表必须真的掉东西：两个 children（丝触掉方块 / 常规掉物品），且都指向已注册的物品。 */
    @Test
    void lootTableDropsTheOreItselfInBothBranches() {
        for (String oreId : ORES) {
            JsonObject loot = ShippedProducts.lootTable("blocks/" + oreId);
            JsonObject pool = loot.getAsJsonArray("pools").get(0).getAsJsonObject();
            JsonArray children =
                    pool.getAsJsonArray("entries")
                            .get(0)
                            .getAsJsonObject()
                            .getAsJsonArray("children");

            assertEquals(2, children.size(), oreId + " 的 alternatives 应有丝触与常规两个分支");
            for (var child : children) {
                String name = child.getAsJsonObject().get("name").getAsString();
                assertEquals("strife:" + oreId, name, oreId + " 的掉落物必须是矿石方块自身（本期无冶炼材料物品）");
            }
        }
    }

    /**
     * 矿脉数 = {@code veins_per_chunk × density_ratio} 向上取整且保底 1；高度区间 = 绝对高度减 64。
     *
     * <p>这两处换算错了都不会报错：矿脉数算成 0 → 矿完全不生成；高度算错 → 矿长在玩家挖不到的地方。灵玉矿 8×0.85=6.8 → 7、寒玉矿 2×0.15=0.3 → 保底
     * 1，都是"向上取整 + 保底"的语义（稀，不是没有）。
     */
    @Test
    void placedFeatureFoldsDensityAndShiftsHeightByTheWorldBaseline() {
        assertEquals(7, placedCount("block_ore_lingyu"), "8 × 0.85 = 6.8 → ceil = 7");
        assertEquals(2, placedCount("block_ore_chiyan"), "4 × 0.45 = 1.8 → ceil = 2");
        assertEquals(1, placedCount("block_ore_hanyu"), "2 × 0.15 = 0.3 → ceil 后保底 1（稀，不是没有）");

        JsonObject range = heightRange("block_ore_lingyu");
        assertEquals(112, range.get("height").getAsInt(), "y_max 48 − (−64) = 112");
        assertEquals(16, range.get("y").getAsInt(), "y_min −48 − (−64) = 16");

        JsonObject lingyu = ShippedProducts.ore("block_ore_lingyu");
        JsonObject placement = lingyu.getAsJsonObject("placement");
        assertEquals(-48, placement.get("y_min").getAsInt());
        assertEquals(48, placement.get("y_max").getAsInt());
        assertEquals(10, placement.get("vein_size").getAsInt());
    }

    /** biome_modifier 必须把矿挂到 UNDERGROUND_ORES 步的指定群系上，否则矿不生成或生成在错误阶段。 */
    @Test
    void biomeModifierHooksTheOreIntoUndergroundOres() {
        for (String oreId : ORES) {
            JsonObject modifier = ShippedProducts.ore(oreId);
            assertNotNull(modifier.get("placement"), oreId + " 契约镜像缺 placement");
            int biomeCount = modifier.getAsJsonObject("placement").getAsJsonArray("biomes").size();
            assertTrue(biomeCount > 0, oreId + " 未列群系");
        }
    }

    /** 每种矿都必须在代码里注册了同名方块与物品常量（表有行、代码没注册 = 矿挖了掉空气）。 */
    @Test
    void everyOreHasARegisteredBlockConstant() {
        for (String oreId : ORES) {
            assertNotNull(
                    StrifeOreBlocks.holder(oreId), "StrifeOreBlocks 未注册 " + oreId + "（表里有行、代码缺方块）");
        }
    }

    /** 未知字段一律拒绝：产物是 DataGen 写的，多出来的键只有"契约改了"或"有人手改产物"两种来源，两种都该红。 */
    @Test
    void unknownFieldIsRejectedWithTheFieldName() {
        JsonObject broken = ShippedProducts.ore("block_ore_lingyu").deepCopy();
        broken.addProperty("luck_bonus", 3);

        IllegalStateException error =
                assertThrows(
                        IllegalStateException.class,
                        () -> OreTables.parseSpec(broken, "strife_ores/block_ore_lingyu.json"));

        assertTrue(error.getMessage().contains("luck_bonus"), error.getMessage());
    }

    /** 密度倍率越界必须在加载期红：0 会让矿消失，>1 语义非法。 */
    @Test
    void densityRatioOutsideZeroToOneIsRejected() {
        JsonObject zero = ShippedProducts.ore("block_ore_lingyu").deepCopy();
        zero.addProperty("density_ratio", 0.0);
        assertTrue(
                assertThrows(
                                IllegalStateException.class,
                                () ->
                                        OreTables.parseSpec(
                                                zero, "strife_ores/block_ore_lingyu.json"))
                        .getMessage()
                        .contains("density_ratio"),
                "density_ratio=0 必须被拒（会让该矿彻底不生成）");

        JsonObject over = ShippedProducts.ore("block_ore_lingyu").deepCopy();
        over.addProperty("density_ratio", 1.5);
        assertThrows(
                IllegalStateException.class,
                () -> OreTables.parseSpec(over, "strife_ores/block_ore_lingyu.json"));
    }

    /** regen_period_key 非 null 必须拒绝：H5 未实现，写非 null 会让表作者以为矿会重生。 */
    @Test
    void nonNullRegenPeriodKeyIsRejectedBecauseH5IsNotImplemented() {
        JsonObject broken = ShippedProducts.ore("block_ore_lingyu").deepCopy();
        broken.addProperty("regen_period_key", "per_10y");

        IllegalStateException error =
                assertThrows(
                        IllegalStateException.class,
                        () -> OreTables.parseSpec(broken, "strife_ores/block_ore_lingyu.json"));

        assertTrue(error.getMessage().contains("per_10y"), error.getMessage());
    }

    /** 上下界反了必须报错：会生成不出任何矿，且原版不报错。 */
    @Test
    void invertedHeightRangeIsRejected() {
        JsonObject broken = ShippedProducts.ore("block_ore_lingyu").deepCopy();
        broken.getAsJsonObject("placement").addProperty("y_min", 60);
        broken.getAsJsonObject("placement").addProperty("y_max", 10);

        assertThrows(
                IllegalStateException.class,
                () -> OreTables.parseSpec(broken, "strife_ores/block_ore_lingyu.json"));
    }

    /**
     * 群系 ID 必须是原版能解析的形状；"ID 是否真实存在"由 {@code /strife world ore status} 在运行时核对。
     *
     * <p>顺带固化一条原版行为：无命名空间的 ID 会被 {@code ResourceLocation.tryBySeparator} <b>补上默认命名空间</b> （{@code
     * minecraft}）。所以"缺命名空间"不是错误形态——本仓表里写全 {@code minecraft:} 前缀是<b>可读性约定</b>，
     * 不是格式强制项，这一点必须写清，否则后人会误加一条"必须带命名空间"的假门禁。
     */
    @Test
    void everyBiomeIdIsParseableByTheVanillaResourceLocation() {
        for (JsonObject product : ShippedProducts.allOres()) {
            String id = product.get("id").getAsString();
            OreTables.OreSpec spec = OreTables.parseSpec(product, "strife_ores/" + id + ".json");

            for (String biomeId : spec.placement().biomes()) {
                assertNotNull(
                        ResourceLocation.tryParse(biomeId),
                        id + " 的群系 ID '" + biomeId + "' 不是原版可解析的资源位置");
            }
        }
    }

    /**
     * 群系 ID 形状非法的必须报错。
     *
     * <p>注意用 {@code minecraft:Bad ID}（含空格）而不是"缺命名空间"当反例：原版 {@code ResourceLocation.tryBySeparator}
     * 对无命名空间的字符串会<b>补默认命名空间</b>（{@code DEFAULT_NAMESPACE="minecraft"}），所以 {@code plains} 会被解析成
     * {@code minecraft:plains} 而不是失败。 拿它当反例会让用例断言一个不存在的行为——这正是"测试先跑一遍确认它真的会因为正确的原因而红"的价值。
     */
    @Test
    void malformedBiomeIdIsRejectedWithTheId() {
        JsonObject broken = ShippedProducts.ore("block_ore_lingyu").deepCopy();
        JsonArray biomes = new JsonArray();
        biomes.add("minecraft:plains");
        biomes.add("minecraft:Bad ID");
        broken.getAsJsonObject("placement").add("biomes", biomes);

        IllegalStateException error =
                assertThrows(
                        IllegalStateException.class,
                        () -> OreTables.parseSpec(broken, "strife_ores/block_ore_lingyu.json"));

        assertTrue(error.getMessage().contains("minecraft:Bad ID"), error.getMessage());
    }

    /** 空项群系必须报错：原版对"某个群系 ID 是空串"不校验，矿会静默不在任何群系生成。 */
    @Test
    void blankBiomeIdIsRejected() {
        JsonObject broken = ShippedProducts.ore("block_ore_lingyu").deepCopy();
        JsonArray biomes = new JsonArray();
        biomes.add("minecraft:plains");
        biomes.add("   ");
        broken.getAsJsonObject("placement").add("biomes", biomes);

        IllegalStateException error =
                assertThrows(
                        IllegalStateException.class,
                        () -> OreTables.parseSpec(broken, "strife_ores/block_ore_lingyu.json"));

        assertTrue(error.getMessage().contains("invalid id"), error.getMessage());
    }

    private static int placedCount(String oreId) {
        for (var element : ShippedProducts.placedFeature(oreId).getAsJsonArray("placement")) {
            JsonObject modifier = element.getAsJsonObject();
            if (modifier.has("count")) {
                return modifier.get("count").getAsInt();
            }
        }
        throw new AssertionError(oreId + " 的 placed_feature 缺 count 修饰符");
    }

    private static JsonObject heightRange(String oreId) {
        for (var element : ShippedProducts.placedFeature(oreId).getAsJsonArray("placement")) {
            JsonObject modifier = element.getAsJsonObject();
            if (modifier.has("height_range")) {
                return modifier.getAsJsonObject("height_range");
            }
        }
        throw new AssertionError(oreId + " 的 placed_feature 缺 height_range 修饰符");
    }
}

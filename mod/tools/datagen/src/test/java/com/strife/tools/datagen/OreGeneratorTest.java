package com.strife.tools.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code tables/ores.csv} → 五份矿石产物的生成器用例（契约 content/JSON_SCHEMA.md §4.8）。
 *
 * <p>本层的缺陷形态与 runtime 层不同：runtime 层是"产物与解析器对不上"，本层是<b>"表里的错值被原样翻译进产物"</b>。
 * 而原版对矿石链的每一环都<b>不报错</b>（找不到配置特征就不生成、找不到掉落表就掉空气），所以错值一旦翻译成功， 就会一路静默到玩家手上。全部错误信息因此都带 {@code
 * 表名:行号}：策划改表时能直接定位到行。
 */
class OreGeneratorTest {

    /** 契约 §4.8 列集；{@code _note} 是注释列，不得进产物。 */
    private static final List<String> HEADER =
            List.of(
                    "id",
                    "element",
                    "placement",
                    "density_ratio",
                    "drop_table",
                    "regen_period_key",
                    "_note");

    private static final String GOOD_PLACEMENT =
            "biomes=minecraft:plains|minecraft:forest;y_min=-48;y_max=48;veins_per_chunk=8;vein_size=10";

    private static TableSource readTable(Path dir, List<List<String>> rows) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(String.join(",", HEADER));
        for (List<String> cells : rows) {
            assertEquals(HEADER.size(), cells.size(), "test row must match the contract header");
            lines.add(String.join(",", cells));
        }
        Path csv = dir.resolve("ores.csv");
        Files.createDirectories(dir);
        Files.write(csv, lines);
        return TableSource.read(csv);
    }

    private static List<String> goodRow(String id) {
        return List.of(id, "jin", GOOD_PLACEMENT, "0.85", "blocks/" + id, "", "测试行");
    }

    @Test
    void oneRowProducesFiveProductsAtTheContractedPaths(@TempDir Path dir) throws IOException {
        TableSource source = readTable(dir, List.of(goodRow("block_ore_lingyu")));

        List<Product> products = new OreGenerator().generate(source);

        assertEquals(5, products.size(), "一行矿必须出五份产物：配置特征/放置特征/群系修正/掉落表/契约镜像");
        assertEquals(
                List.of(
                        "data/strife/worldgen/configured_feature/block_ore_lingyu.json",
                        "data/strife/worldgen/placed_feature/block_ore_lingyu.json",
                        "data/strife/worldgen/biome_modifier/block_ore_lingyu.json",
                        "data/strife/loot_table/blocks/block_ore_lingyu.json",
                        "data/strife/strife_ores/block_ore_lingyu.json"),
                products.stream().map(Product::relativePath).toList());
        for (Product product : products) {
            assertEquals(
                    "from tables/ores.csv @ sha256:" + source.sha256(), product.generatedHeader());
        }
    }

    /** 密度倍率向上取整且保底 1：0.15 的意图是"稀"不是"没有"。 */
    @Test
    void densityRatioRoundsUpAndNeverBecomesZero(@TempDir Path dir) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        rows.add(goodRow("block_ore_common"));
        rows.add(
                List.of(
                        "block_ore_rare",
                        "huo",
                        "biomes=minecraft:desert;y_min=0;y_max=32;veins_per_chunk=2;vein_size=5",
                        "0.15",
                        "blocks/block_ore_rare",
                        "",
                        ""));
        TableSource source = readTable(dir, rows);

        List<Product> products = new OreGenerator().generate(source);
        JsonObject common = placed(products, "block_ore_common");
        JsonObject rare = placed(products, "block_ore_rare");

        assertEquals(7, countOf(common), "8 × 0.85 = 6.8 → ceil = 7");
        assertEquals(1, countOf(rare), "2 × 0.15 = 0.3 → 保底 1（取整成 0 会让该矿彻底消失）");
    }

    /** 高度区间是"以 y=0 为基准的相对高度"，绝对高度要减掉原版世界基线 −64。 */
    @Test
    void heightRangeShiftsAbsoluteYByTheWorldBaseline(@TempDir Path dir) throws IOException {
        TableSource source = readTable(dir, List.of(goodRow("block_ore_lingyu")));

        JsonObject range =
                heightRangeOf(placed(new OreGenerator().generate(source), "block_ore_lingyu"));

        assertEquals(112, range.get("height").getAsInt(), "y_max 48 − (−64) = 112");
        assertEquals(16, range.get("y").getAsInt(), "y_min −48 − (−64) = 16");
    }

    /** 掉落表必须与方块注册名一致：1.21.1 的掉落表路径由注册名推导，写别的名字 = 静默掉空气。 */
    @Test
    void dropTableMustMatchTheBlockIdOrTheOreWouldDropNothing(@TempDir Path dir)
            throws IOException {
        TableSource source =
                readTable(
                        dir,
                        List.of(
                                List.of(
                                        "block_ore_lingyu",
                                        "jin",
                                        GOOD_PLACEMENT,
                                        "0.85",
                                        "loot_ore_lingyu",
                                        "",
                                        "")));

        IllegalStateException error =
                assertThrows(
                        IllegalStateException.class, () -> new OreGenerator().generate(source));

        assertTrue(error.getMessage().contains("loot_ore_lingyu"), error.getMessage());
        assertTrue(error.getMessage().contains("blocks/block_ore_lingyu"), error.getMessage());
        assertTrue(error.getMessage().contains("ores.csv:2"), "错误必须报到行：" + error.getMessage());
    }

    @Test
    void invertedHeightRangeIsRejectedWithTheRowNumber(@TempDir Path dir) throws IOException {
        TableSource source =
                readTable(
                        dir,
                        List.of(
                                List.of(
                                        "block_ore_bad",
                                        "jin",
                                        "biomes=minecraft:plains;y_min=60;y_max=10;veins_per_chunk=4;vein_size=5",
                                        "0.5",
                                        "blocks/block_ore_bad",
                                        "",
                                        "")));

        IllegalStateException error =
                assertThrows(
                        IllegalStateException.class, () -> new OreGenerator().generate(source));

        assertTrue(error.getMessage().contains("y_max"), error.getMessage());
        assertTrue(error.getMessage().contains("ores.csv:2"), error.getMessage());
    }

    @Test
    void zeroVeinsPerChunkIsRejectedBecauseTheOreWouldNeverGenerate(@TempDir Path dir)
            throws IOException {
        TableSource source =
                readTable(
                        dir,
                        List.of(
                                List.of(
                                        "block_ore_bad",
                                        "jin",
                                        "biomes=minecraft:plains;y_min=0;y_max=32;veins_per_chunk=0;vein_size=5",
                                        "0.5",
                                        "blocks/block_ore_bad",
                                        "",
                                        "")));

        IllegalStateException error =
                assertThrows(
                        IllegalStateException.class, () -> new OreGenerator().generate(source));

        assertTrue(error.getMessage().contains("veins_per_chunk"), error.getMessage());
    }

    @Test
    void emptyBiomeEntryIsRejectedWithTheRowNumber(@TempDir Path dir) throws IOException {
        TableSource source =
                readTable(
                        dir,
                        List.of(
                                List.of(
                                        "block_ore_bad",
                                        "jin",
                                        "biomes=minecraft:plains|;y_min=0;y_max=32;veins_per_chunk=4;vein_size=5",
                                        "0.5",
                                        "blocks/block_ore_bad",
                                        "",
                                        "")));

        IllegalStateException error =
                assertThrows(
                        IllegalStateException.class, () -> new OreGenerator().generate(source));

        assertTrue(error.getMessage().contains("empty entry"), error.getMessage());
    }

    @Test
    void nonNumericDensityIsRejected(@TempDir Path dir) throws IOException {
        TableSource source =
                readTable(
                        dir,
                        List.of(
                                List.of(
                                        "block_ore_bad",
                                        "jin",
                                        GOOD_PLACEMENT,
                                        "很多",
                                        "blocks/block_ore_bad",
                                        "",
                                        "")));

        assertThrows(IllegalStateException.class, () -> new OreGenerator().generate(source));
    }

    /** regen_period_key 列本期必须留空：H5 未实现，生成器不读它，但也不该把它当有效配置丢掉。 */
    @Test
    void regenPeriodKeyIsCarriedAsExplicitNullInTheMirror(@TempDir Path dir) throws IOException {
        TableSource source = readTable(dir, List.of(goodRow("block_ore_lingyu")));

        JsonObject mirror =
                JsonParser.parseString(
                                mirrorOf(new OreGenerator().generate(source), "block_ore_lingyu")
                                        .toJson())
                        .getAsJsonObject();

        assertTrue(mirror.has("regen_period_key"), "契约要求该键必填（可 null）");
        assertTrue(mirror.get("regen_period_key").isJsonNull(), "本期恒 null：写进去是为了区分'未启用'与'漏填'");
    }

    /** 注释列不得进产物（与其它生成器同一纪律）。 */
    @Test
    void noteColumnNeverReachesProducts(@TempDir Path dir) throws IOException {
        TableSource source = readTable(dir, List.of(goodRow("block_ore_lingyu")));

        for (Product product : new OreGenerator().generate(source)) {
            assertTrue(!product.toJson().contains("测试行"), product.relativePath() + " 把注释列写进了产物");
        }
    }

    /** 掉落表产物必须与原版 loot 2 同构：type + pools + random_sequence，且随机序列落在自身路径。 */
    @Test
    void lootTableMatchesTheVanillaBlockSchema(@TempDir Path dir) throws IOException {
        TableSource source = readTable(dir, List.of(goodRow("block_ore_lingyu")));

        JsonObject loot =
                JsonParser.parseString(
                                lootOf(new OreGenerator().generate(source), "block_ore_lingyu")
                                        .toJson())
                        .getAsJsonObject();

        assertEquals("minecraft:block", loot.get("type").getAsString());
        assertEquals("strife:blocks/block_ore_lingyu", loot.get("random_sequence").getAsString());

        JsonObject pool = loot.getAsJsonArray("pools").get(0).getAsJsonObject();
        assertEquals(1.0, pool.get("rolls").getAsDouble(), 1e-9);
        JsonArray children =
                pool.getAsJsonArray("entries").get(0).getAsJsonObject().getAsJsonArray("children");
        assertEquals(2, children.size(), "丝触与常规两个分支");
        assertEquals(
                "strife:block_ore_lingyu",
                children.get(0).getAsJsonObject().get("name").getAsString());
    }

    private static JsonObject placed(List<Product> products, String id) {
        return parse(find(products, "data/strife/worldgen/placed_feature/" + id + ".json"));
    }

    private static Product lootOf(List<Product> products, String id) {
        return find(products, "data/strife/loot_table/blocks/" + id + ".json");
    }

    private static Product mirrorOf(List<Product> products, String id) {
        return find(products, "data/strife/strife_ores/" + id + ".json");
    }

    private static Product find(List<Product> products, String path) {
        return products.stream()
                .filter(product -> product.relativePath().equals(path))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少产物 " + path));
    }

    private static JsonObject parse(Product product) {
        assertNotNull(product);
        return JsonParser.parseString(product.toJson()).getAsJsonObject();
    }

    private static int countOf(JsonObject placedFeature) {
        for (var element : placedFeature.getAsJsonArray("placement")) {
            JsonObject modifier = element.getAsJsonObject();
            if (modifier.has("count")) {
                return modifier.get("count").getAsInt();
            }
        }
        throw new AssertionError("placed_feature 缺 count 修饰符");
    }

    private static JsonObject heightRangeOf(JsonObject placedFeature) {
        for (var element : placedFeature.getAsJsonArray("placement")) {
            JsonObject modifier = element.getAsJsonObject();
            if (modifier.has("height_range")) {
                return modifier.getAsJsonObject("height_range");
            }
        }
        throw new AssertionError("placed_feature 缺 height_range 修饰符");
    }
}

package com.strife.tools.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code tables/prices.csv} → 定价产物的生成器用例（EP1，ADR-032）。
 *
 * <p>价格门禁在生成器（构建期红）：回收必折价（sell&lt;buy）、非负、item_id 唯一且限 {@code
 * item_|pill_}——错值一旦进产物，"买卖循环刷灵石"就静默成立，所以每条都钉红脸。 happy 路径读真实 NUMBERS（顺带钉住 @@market 块的解析契约）。
 */
class PriceGeneratorTest {

    private static final List<String> HEADER =
            List.of("id", "item_id", "buy_lingshi", "sell_lingshi", "_note");

    private static NumbersSource numbers() {
        // 真实 NUMBERS（ADR-020 精神：不写夹具）。测试工作目录随构建插件变化（moddev 覆盖），
        // 与其猜相对路径，不如自 current dir 向上找 content/NUMBERS.md——仓库布局唯一。
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null
                && !Files.exists(dir.resolve("content" + java.io.File.separator + "NUMBERS.md"))) {
            dir = dir.getParent();
        }
        if (dir == null) {
            throw new IllegalStateException(
                    "content/NUMBERS.md not found upward from " + Path.of("").toAbsolutePath());
        }
        return NumbersSource.at(dir.resolve("content"));
    }

    @Test
    void happyRowProducesEntriesAndRatios(@TempDir Path dir) throws IOException {
        var product =
                new PriceGenerator(numbers())
                        .generate(
                                readTable(
                                        dir,
                                        List.of(
                                                List.of(
                                                        "price_ningxu",
                                                        "item_ningxu",
                                                        "2",
                                                        "1",
                                                        "x"),
                                                List.of(
                                                        "price_pill_xuanyu",
                                                        "pill_xuanyu",
                                                        "20",
                                                        "10",
                                                        "x"))));
        assertEquals(1, product.size());
        var json = product.get(0).fields();
        assertEquals("prices", json.get("id"));
        @SuppressWarnings("unchecked")
        var entries = (List<java.util.Map<String, Object>>) json.get("entries");
        assertEquals(2, entries.size());
        assertEquals("item_ningxu", entries.get(0).get("item_id"));
        assertEquals(20, entries.get(1).get("buy_lingshi"));
        assertTrue(json.containsKey("ratios"), "三档兑换比必须随产物下发");
        @SuppressWarnings("unchecked")
        var ratios = (java.util.Map<String, Object>) json.get("ratios");
        assertEquals(
                10, ((Number) ratios.get("tier_zhong")).intValue(), "NUMBERS @@market tier_zhong");
        assertEquals(
                100, ((Number) ratios.get("tier_shang")).intValue(), "NUMBERS @@market tier_shang");
    }

    @Test
    void sellAtOrAboveBuyIsRejected(@TempDir Path dir) throws IOException {
        var e =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                new PriceGenerator(numbers())
                                        .generate(
                                                readTable(
                                                        dir,
                                                        List.of(
                                                                List.of(
                                                                        "price_bad",
                                                                        "item_x",
                                                                        "5",
                                                                        "5",
                                                                        "x")))));
        assertTrue(e.getMessage().contains("回收必折价"), e.getMessage());
    }

    @Test
    void negativePriceIsRejected(@TempDir Path dir) throws IOException {
        var e =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                new PriceGenerator(numbers())
                                        .generate(
                                                readTable(
                                                        dir,
                                                        List.of(
                                                                List.of(
                                                                        "price_bad",
                                                                        "item_x",
                                                                        "-1",
                                                                        "0",
                                                                        "x")))));
        assertTrue(e.getMessage().contains("非负"), e.getMessage());
    }

    @Test
    void duplicateItemIdIsRejected(@TempDir Path dir) throws IOException {
        var e =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                new PriceGenerator(numbers())
                                        .generate(
                                                readTable(
                                                        dir,
                                                        List.of(
                                                                List.of(
                                                                        "price_a", "item_x", "5",
                                                                        "1", "x"),
                                                                List.of(
                                                                        "price_b", "item_x", "6",
                                                                        "1", "x")))));
        assertTrue(e.getMessage().contains("重复"), e.getMessage());
    }

    @Test
    void currencyAndForeignPrefixesAreRejected(@TempDir Path dir) throws IOException {
        // 货币（item_lingshi）与其余前缀不入定价表——定价表的 item_id 只能是被交易物。
        var e =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                new PriceGenerator(numbers())
                                        .generate(
                                                readTable(
                                                        dir,
                                                        List.of(
                                                                List.of(
                                                                        "price_lingshi",
                                                                        "item_lingshi",
                                                                        "5",
                                                                        "1",
                                                                        "x")))));
        assertTrue(e.getMessage().contains("货币不入定价表"), e.getMessage());
    }

    private TableSource readTable(Path dir, List<List<String>> rows) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(String.join(",", HEADER));
        for (List<String> cells : rows) {
            assertEquals(HEADER.size(), cells.size(), "test row must match the contract header");
            lines.add(String.join(",", cells));
        }
        Path csv = dir.resolve("prices.csv");
        Files.createDirectories(dir);
        Files.write(csv, lines);
        return TableSource.read(csv);
    }
}

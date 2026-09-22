package com.strife.tools.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FactionGeneratorTest {

    /**
     * Content/JSON_SCHEMA.md §5.2 column set; {@code _note} is a comment column and must vanish.
     */
    private static final List<String> HEADER =
            List.of("id", "display_name_key", "alignment", "home_region", "relations", "_note");

    private static TableSource readTable(Path dir, List<List<String>> rows) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(String.join(",", HEADER));
        for (List<String> cells : rows) {
            assertEquals(HEADER.size(), cells.size(), "test row must match the contract header");
            lines.add(String.join(",", cells));
        }
        Path csv = dir.resolve("factions.csv");
        Files.write(csv, lines);
        return TableSource.read(csv);
    }

    @Test
    void writesOneProductPerRowAtTheContractedPath(@TempDir Path dir) throws IOException {
        TableSource source =
                readTable(
                        dir,
                        List.of(
                                List.of(
                                        "fac_qingshi",
                                        "faction.strife.fac_qingshi",
                                        "orthodox",
                                        "qi_zhongyuan",
                                        "yuelai=-80",
                                        "青石宗")));

        List<Product> products = new FactionGenerator().generate(source);

        assertEquals(1, products.size());
        Product product = products.get(0);
        assertEquals("data/strife/strife_factions/fac_qingshi.json", product.relativePath());
        assertEquals(
                "from tables/factions.csv @ sha256:" + source.sha256(), product.generatedHeader());

        JsonObject object = JsonParser.parseString(product.toJson()).getAsJsonObject();
        assertEquals("fac_qingshi", object.get("id").getAsString());
        assertEquals("orthodox", object.get("alignment").getAsString());
        assertEquals("qi_zhongyuan", object.get("home_region").getAsString());
        assertTrue(
                object.getAsJsonObject("relations").get("yuelai").getAsJsonPrimitive().isNumber(),
                "H7 attitude matrix entries are numbers, not strings:\n" + product.toJson());
        assertEquals(-80, object.getAsJsonObject("relations").get("yuelai").getAsInt());
        assertTrue(!product.toJson().contains("_note"), "comment columns must not reach products");
    }

    @Test
    void writesBlankOptionalCellsAsNullAndEmptyRelationsAsEmptyObject(@TempDir Path dir)
            throws IOException {
        TableSource source =
                readTable(
                        dir,
                        List.of(
                                List.of(
                                        "fac_wandering",
                                        "faction.strife.fac_wandering",
                                        "neutral",
                                        "",
                                        "()",
                                        "")));

        Product product = new FactionGenerator().generate(source).get(0);

        JsonObject object = JsonParser.parseString(product.toJson()).getAsJsonObject();
        assertTrue(object.get("home_region").isJsonNull(), product.toJson());
        assertTrue(object.get("relations").isJsonObject(), product.toJson());
        assertEquals(0, object.getAsJsonObject("relations").size(), product.toJson());
    }

    @Test
    void refusesToInventALangKey(@TempDir Path dir) throws IOException {
        TableSource source =
                readTable(dir, List.of(List.of("fac_qingshi", "", "orthodox", "", "", "")));

        IllegalStateException error =
                assertThrows(
                        IllegalStateException.class, () -> new FactionGenerator().generate(source));

        assertTrue(
                error.getMessage().contains("still [拟]")
                        && error.getMessage().contains("JSON_SCHEMA.md §4.10"),
                error.getMessage());
    }

    @Test
    void rejectsANonNumericAttitude(@TempDir Path dir) throws IOException {
        TableSource source =
                readTable(
                        dir,
                        List.of(
                                List.of(
                                        "fac_qingshi",
                                        "faction.strife.fac_qingshi",
                                        "orthodox",
                                        "",
                                        "yuelai=敌对",
                                        "")));

        IllegalStateException error =
                assertThrows(
                        IllegalStateException.class, () -> new FactionGenerator().generate(source));

        assertTrue(error.getMessage().contains("non-integer attitude"), error.getMessage());
    }

    @Test
    void theRunLeavesRealFilesOnDisk(@TempDir Path root) throws IOException {
        Path tables = root.resolve("tables");
        readTable(
                tables,
                List.of(
                        List.of(
                                "fac_qingshi",
                                "faction.strife.fac_qingshi",
                                "orthodox",
                                "",
                                "",
                                ""),
                        List.of(
                                "fac_yuelai",
                                "faction.strife.fac_yuelai",
                                "demonic",
                                "qi_beihuang",
                                "qingshi=-70",
                                "")));

        DataGenMain.Summary summary =
                DataGenMain.run(
                        new DataGenMain.Options(tables, root.resolve("resources")),
                        List.of(new FactionGenerator()));

        assertEquals(2, summary.products());
        assertEquals(List.of(), summary.problems());
        String json =
                Files.readString(
                        root.resolve("resources/data/strife/strife_factions/fac_qingshi.json"));
        assertTrue(json.contains("\"display_name_key\": \"faction.strife.fac_qingshi\""), json);
    }
}

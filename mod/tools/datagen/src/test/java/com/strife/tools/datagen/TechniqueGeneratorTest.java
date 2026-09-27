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

class TechniqueGeneratorTest {

    private static final List<String> HEADER =
            List.of(
                    "id",
                    "grade",
                    "element",
                    "required_realm",
                    "required_stage",
                    "required_spiritroot",
                    "affinity_rule",
                    "qi_rate_ratio",
                    "passives",
                    "grants_spells",
                    "faction",
                    "price",
                    "source",
                    "disabled_reason_key",
                    "_note");

    private static TableSource readTable(Path dir, List<List<String>> rows) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(String.join(",", HEADER));
        for (List<String> cells : rows) {
            assertEquals(HEADER.size(), cells.size(), "test row must match the contract header");
            lines.add(String.join(",", cells));
        }
        Files.createDirectories(dir);
        Path csv = dir.resolve("techniques.csv");
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
                                        "tech_qingxin_jue",
                                        "xuan",
                                        "mu",
                                        "zhuji",
                                        "1",
                                        "mu",
                                        "matched",
                                        "1.15",
                                        "",
                                        "",
                                        "fac_qingshi",
                                        "item_id=item_lingshi;count=50",
                                        "inherit",
                                        "",
                                        "示例行")));

        List<Product> products = new TechniqueGenerator().generate(source);

        assertEquals(1, products.size());
        Product product = products.get(0);
        assertEquals("data/strife/strife_techniques/tech_qingxin_jue.json", product.relativePath());
        JsonObject object = JsonParser.parseString(product.toJson()).getAsJsonObject();
        assertEquals("xuan", object.get("grade").getAsString());
        assertEquals(1, object.get("required_stage").getAsInt());
        assertEquals("mu", object.get("required_spiritroot").getAsJsonArray().get(0).getAsString());
        assertEquals(1.15, object.get("qi_rate_ratio").getAsDouble(), 1e-9);
        assertEquals(50, object.getAsJsonObject("price").get("count").getAsInt(), product.toJson());
        assertTrue(!product.toJson().contains("_note"), "comment columns must not reach products");
    }

    @Test
    void blankOptionalsBecomeNullAndDefaultedStageBecomesOne(@TempDir Path dir) throws IOException {
        TableSource source =
                readTable(
                        dir,
                        List.of(
                                List.of(
                                        "tech_qingxin_jue",
                                        "huang",
                                        "jin",
                                        "qili",
                                        "",
                                        "",
                                        "",
                                        "1.0",
                                        "",
                                        "",
                                        "",
                                        "",
                                        "",
                                        "",
                                        "")));

        JsonObject object =
                JsonParser.parseString(new TechniqueGenerator().generate(source).get(0).toJson())
                        .getAsJsonObject();

        assertEquals(1, object.get("required_stage").getAsInt(), "contract default 1 is visible");
        assertTrue(object.get("required_spiritroot").isJsonNull(), productJson(object));
        assertTrue(object.get("price").isJsonNull(), productJson(object));
        assertTrue(object.get("grants_spells").isJsonNull(), productJson(object));
    }

    @Test
    void anExplicitlyEmptySpiritrootListSurvives(@TempDir Path dir) throws IOException {
        TableSource source =
                readTable(
                        dir,
                        List.of(
                                List.of(
                                        "tech_qingxin_jue",
                                        "huang",
                                        "jin",
                                        "qili",
                                        "",
                                        "()",
                                        "",
                                        "1.0",
                                        "",
                                        "",
                                        "",
                                        "",
                                        "",
                                        "",
                                        "")));

        JsonObject object =
                JsonParser.parseString(new TechniqueGenerator().generate(source).get(0).toJson())
                        .getAsJsonObject();

        assertEquals(0, object.get("required_spiritroot").getAsJsonArray().size());
    }

    @Test
    void aRequiredColumnLeftBlankStopsTheRun(@TempDir Path dir) throws IOException {
        TableSource source =
                readTable(
                        dir,
                        List.of(
                                List.of(
                                        "tech_qingxin_jue",
                                        "xuan",
                                        "mu",
                                        "zhuji",
                                        "",
                                        "",
                                        "",
                                        "",
                                        "",
                                        "",
                                        "",
                                        "",
                                        "",
                                        "",
                                        "")));

        IllegalStateException error =
                assertThrows(
                        IllegalStateException.class,
                        () -> new TechniqueGenerator().generate(source));

        assertTrue(error.getMessage().contains("qi_rate_ratio"), error.getMessage());
        assertTrue(error.getMessage().contains("必填"), error.getMessage());
        assertTrue(error.getMessage().contains("techniques.csv:2"), error.getMessage());
    }

    private static String productJson(JsonObject object) {
        return object.toString();
    }
}

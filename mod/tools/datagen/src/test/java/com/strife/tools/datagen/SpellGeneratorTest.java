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

class SpellGeneratorTest {

    private static final List<String> HEADER =
            List.of(
                    "id",
                    "element",
                    "required_technique",
                    "cost_key",
                    "cooldown_key",
                    "damage_formula_key",
                    "projectile",
                    "aoe_radius_blocks",
                    "effects",
                    "price",
                    "_note");

    /**
     * The slice of NUMBERS §8 @@combat the *_key columns point into (content/JSON_SCHEMA.md §3).
     */
    private static final String COMBAT =
            """
            @@combat
            ```yaml
            spell_cost_qi:      { light: 6,  medium: 18, heavy: 45 }        # [拟]
            spell_cooldown_sec: { light: 1.0, medium: 4.0, heavy: 12.0 }    # [拟]
            formula_basic:      { base: 6,  scale: realm_coeff }            # [拟]
            ```
            """;

    private static TableSource readTable(Path dir, List<List<String>> rows) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(String.join(",", HEADER));
        for (List<String> cells : rows) {
            assertEquals(HEADER.size(), cells.size(), "test row must match the contract header");
            lines.add(String.join(",", cells));
        }
        Files.createDirectories(dir);
        Path csv = dir.resolve("spells.csv");
        Files.write(csv, lines);
        return TableSource.read(csv);
    }

    private static NumbersSource numbers(Path root) throws IOException {
        Path content = root.resolve("content");
        Files.createDirectories(content);
        Files.writeString(content.resolve("NUMBERS.md"), COMBAT);
        return NumbersSource.at(content);
    }

    @Test
    void expandsKeyColumnsInlineFromNumbers(@TempDir Path root) throws IOException {
        TableSource source =
                readTable(
                        root.resolve("tables"),
                        List.of(
                                List.of(
                                        "spell_prologue_demo",
                                        "mu",
                                        "tech_qingxin_jue",
                                        "medium",
                                        "light",
                                        "formula_basic",
                                        "speed=0.8;gravity=0.03;range=20;pierce_count=0",
                                        "0",
                                        "effect_id=slowness;duration_sec=5;amp=1",
                                        "",
                                        "")));

        JsonObject object =
                JsonParser.parseString(
                                new SpellGenerator(numbers(root)).generate(source).get(0).toJson())
                        .getAsJsonObject();

        assertEquals("medium", object.get("cost_key").getAsString());
        assertEquals(18, object.get("cost_qi").getAsInt(), "cost_qi inlined from NUMBERS §8");
        assertEquals("light", object.get("cooldown_key").getAsString());
        assertEquals(1.0, object.get("cooldown_sec").getAsDouble(), 1e-9);
        assertEquals(
                6,
                object.getAsJsonObject("damage_formula").get("base").getAsInt(),
                object.toString());
        assertEquals(
                "realm_coeff", object.getAsJsonObject("damage_formula").get("scale").getAsString());
        assertEquals(0.8, object.getAsJsonObject("projectile").get("speed").getAsDouble(), 1e-9);
        assertEquals(0, object.getAsJsonObject("projectile").get("pierce_count").getAsInt());
        assertEquals(0.0, object.get("aoe_radius_blocks").getAsDouble(), 1e-9, "0 = 单体 is a value");
        assertEquals(
                "slowness",
                object.getAsJsonArray("effects")
                        .get(0)
                        .getAsJsonObject()
                        .get("effect_id")
                        .getAsString());
        assertEquals(
                5.0,
                object.getAsJsonArray("effects")
                        .get(0)
                        .getAsJsonObject()
                        .get("duration_sec")
                        .getAsDouble(),
                1e-9);
    }

    @Test
    void blankCellsMeanNullNotZero(@TempDir Path root) throws IOException {
        TableSource source =
                readTable(
                        root.resolve("tables"),
                        List.of(
                                List.of(
                                        "spell_prologue_demo",
                                        "mu",
                                        "tech_qingxin_jue",
                                        "medium",
                                        "light",
                                        "formula_basic",
                                        "",
                                        "",
                                        "",
                                        "",
                                        "")));

        JsonObject object =
                JsonParser.parseString(
                                new SpellGenerator(numbers(root)).generate(source).get(0).toJson())
                        .getAsJsonObject();

        assertTrue(object.get("projectile").isJsonNull(), object.toString());
        assertTrue(object.get("aoe_radius_blocks").isJsonNull(), object.toString());
        assertTrue(object.get("effects").isJsonNull(), object.toString());
        assertTrue(object.get("price").isJsonNull(), object.toString());
    }

    @Test
    void anUnknownTierNamesTheNumbersTable(@TempDir Path root) throws IOException {
        TableSource source =
                readTable(
                        root.resolve("tables"),
                        List.of(
                                List.of(
                                        "spell_prologue_demo",
                                        "mu",
                                        "tech_qingxin_jue",
                                        "bogus",
                                        "light",
                                        "formula_basic",
                                        "",
                                        "",
                                        "",
                                        "",
                                        "")));

        IllegalStateException error =
                assertThrows(
                        IllegalStateException.class,
                        () -> new SpellGenerator(numbers(root)).generate(source));

        assertTrue(error.getMessage().contains("spell_cost_qi"), error.getMessage());
        assertTrue(error.getMessage().contains("spells.csv:2"), error.getMessage());
    }
}

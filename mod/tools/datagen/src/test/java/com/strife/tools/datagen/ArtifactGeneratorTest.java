package com.strife.tools.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

class ArtifactGeneratorTest {

    private static final List<String> HEADER =
            List.of(
                    "id",
                    "blank",
                    "restriction_count",
                    "core_slot",
                    "materials",
                    "quality_probs",
                    "slot_type",
                    "required_realm",
                    "active_skill",
                    "passives",
                    "durability_points",
                    "price",
                    "_note");

    private static final String COMBAT =
            """
            @@combat
            ```yaml
            artifact_cooldown_sec: 20   # [拟] 法宝主动技能
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
        Path csv = dir.resolve("artifacts.csv");
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
    void convertsQualityProbMapsAndExpandsActiveSkillCooldown(@TempDir Path root)
            throws IOException {
        TableSource source =
                readTable(
                        root.resolve("tables"),
                        List.of(
                                List.of(
                                        "art_demo",
                                        "item_blank_sword",
                                        "3",
                                        "item_core_x",
                                        "item_id=item_tie;count=5",
                                        "fan=0.70;di=0.25;tian=0.05",
                                        "weapon",
                                        "qili",
                                        "spell_id=spell_prologue_demo;cooldown_key=heavy",
                                        "",
                                        "120",
                                        "",
                                        "")));

        JsonObject object =
                JsonParser.parseString(
                                new ArtifactGenerator(numbers(root))
                                        .generate(source)
                                        .get(0)
                                        .toJson())
                        .getAsJsonObject();

        assertEquals(3, object.get("restriction_count").getAsInt());
        assertEquals(
                5,
                object.getAsJsonArray("materials")
                        .get(0)
                        .getAsJsonObject()
                        .get("count")
                        .getAsInt());
        assertEquals(
                "fan",
                object.getAsJsonArray("quality_probs")
                        .get(0)
                        .getAsJsonObject()
                        .get("quality_tier")
                        .getAsString(),
                object.toString());
        assertEquals(
                0.70,
                object.getAsJsonArray("quality_probs")
                        .get(0)
                        .getAsJsonObject()
                        .get("prob")
                        .getAsDouble(),
                1e-9);
        assertEquals(
                0.05,
                object.getAsJsonArray("quality_probs")
                        .get(2)
                        .getAsJsonObject()
                        .get("prob")
                        .getAsDouble(),
                1e-9);
        assertEquals(
                "spell_prologue_demo",
                object.getAsJsonObject("active_skill").get("spell_id").getAsString());
        assertEquals(
                20,
                object.getAsJsonObject("active_skill").get("cooldown_sec").getAsInt(),
                "cooldown_sec inlined from NUMBERS §8");
        assertEquals(120, object.get("durability_points").getAsInt());
        assertTrue(object.get("price").isJsonNull(), object.toString());
    }

    @Test
    void aBlankActiveSkillIsNull(@TempDir Path root) throws IOException {
        TableSource source =
                readTable(
                        root.resolve("tables"),
                        List.of(
                                List.of(
                                        "art_demo",
                                        "item_blank_sword",
                                        "3",
                                        "item_core_x",
                                        "item_id=item_tie;count=5",
                                        "fan=1.0",
                                        "treasure",
                                        "qili",
                                        "",
                                        "",
                                        "120",
                                        "",
                                        "")));

        JsonObject object =
                JsonParser.parseString(
                                new ArtifactGenerator(numbers(root))
                                        .generate(source)
                                        .get(0)
                                        .toJson())
                        .getAsJsonObject();

        assertTrue(object.get("active_skill").isJsonNull(), object.toString());
    }
}

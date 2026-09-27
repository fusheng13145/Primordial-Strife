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

class PillGeneratorTest {

    private static final List<String> HEADER =
            List.of(
                    "id",
                    "quality_tier",
                    "pattern",
                    "core_slot",
                    "materials",
                    "heat_range",
                    "outputs",
                    "effect_key",
                    "failure_output",
                    "price",
                    "_note");

    private static final String PILLS =
            """
            @@pills
            ```yaml
            pill_juqi: { qi_rate_bonus: 0.25, duration_sec: 300, repeat_step: 0.05, repeat_floor: 0.10 } # [拟]
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
        Path csv = dir.resolve("pills.csv");
        Files.write(csv, lines);
        return TableSource.read(csv);
    }

    private static NumbersSource numbers(Path root) throws IOException {
        Path content = root.resolve("content");
        Files.createDirectories(content);
        Files.writeString(content.resolve("NUMBERS.md"), PILLS);
        return NumbersSource.at(content);
    }

    @Test
    void writesObjectArraysAndExpandsTheEffect(@TempDir Path root) throws IOException {
        TableSource source =
                readTable(
                        root.resolve("tables"),
                        List.of(
                                List.of(
                                        "pill_juqi",
                                        "fan",
                                        "1x1=herb_ningxu",
                                        "herb_ningxu",
                                        "item_id=herb_ningxu;count=3",
                                        "0.30;0.70",
                                        "item_id=pill_juqi;count=1;prob=1.0;quality=fan",
                                        "pill_juqi",
                                        "",
                                        "",
                                        "")));

        JsonObject object =
                JsonParser.parseString(
                                new PillGenerator(numbers(root)).generate(source).get(0).toJson())
                        .getAsJsonObject();

        assertEquals(
                3,
                object.getAsJsonArray("materials")
                        .get(0)
                        .getAsJsonObject()
                        .get("count")
                        .getAsInt());
        assertEquals(0.30, object.getAsJsonArray("heat_range").get(0).getAsDouble(), 1e-9);
        assertEquals(0.70, object.getAsJsonArray("heat_range").get(1).getAsDouble(), 1e-9);
        assertEquals(
                "fan",
                object.getAsJsonArray("outputs")
                        .get(0)
                        .getAsJsonObject()
                        .get("quality")
                        .getAsString());
        assertEquals(
                1.0,
                object.getAsJsonArray("outputs").get(0).getAsJsonObject().get("prob").getAsDouble(),
                1e-9);
        assertEquals(
                0.25, object.getAsJsonObject("effect").get("qi_rate_bonus").getAsDouble(), 1e-9);
        assertTrue(object.get("failure_output").isJsonNull(), object.toString());
        assertTrue(object.get("price").isJsonNull(), object.toString());
    }

    @Test
    void aHeatRangeWithWrongArityFails(@TempDir Path root) throws IOException {
        TableSource source =
                readTable(
                        root.resolve("tables"),
                        List.of(
                                List.of(
                                        "pill_juqi",
                                        "fan",
                                        "1x1=herb_ningxu",
                                        "herb_ningxu",
                                        "item_id=herb_ningxu;count=3",
                                        "0.30",
                                        "item_id=pill_juqi;count=1;prob=1.0;quality=fan",
                                        "",
                                        "",
                                        "",
                                        "")));

        IllegalStateException error =
                assertThrows(
                        IllegalStateException.class,
                        () -> new PillGenerator(numbers(root)).generate(source));

        assertTrue(error.getMessage().contains("expects 'min;max'"), error.getMessage());
    }

    @Test
    void blankRequiredObjectColumnsStopTheRun(@TempDir Path root) throws IOException {
        TableSource source =
                readTable(
                        root.resolve("tables"),
                        List.of(
                                List.of(
                                        "pill_juqi",
                                        "fan",
                                        "1x1=herb_ningxu",
                                        "herb_ningxu",
                                        "",
                                        "0.30;0.70",
                                        "",
                                        "",
                                        "",
                                        "",
                                        "")));

        IllegalStateException error =
                assertThrows(
                        IllegalStateException.class,
                        () -> new PillGenerator(numbers(root)).generate(source));

        assertTrue(error.getMessage().contains("materials"), error.getMessage());
        assertTrue(error.getMessage().contains("必填"), error.getMessage());
    }
}

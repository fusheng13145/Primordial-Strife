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

class DialogTreeGeneratorTest {

    private static final List<String> HEADER =
            List.of("id", "npc", "root", "nodes", "effects", "max_depth_levels", "_note");

    private static TableSource readTable(Path dir, List<List<String>> rows) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(String.join(",", HEADER));
        for (List<String> cells : rows) {
            assertEquals(HEADER.size(), cells.size(), "test row must match the contract header");
            lines.add(String.join(",", cells));
        }
        Files.createDirectories(dir);
        Path csv = dir.resolve("dialog_trees_prologue.csv");
        Files.write(csv, lines);
        return TableSource.read(csv);
    }

    @Test
    void unwrapsNestedOptionsIntoObjectArrays(@TempDir Path dir) throws IOException {
        TableSource source =
                readTable(
                        dir,
                        List.of(
                                List.of(
                                        "dlg_prologue_intro",
                                        "npc_qingshi_zhizhi",
                                        "d1",
                                        "id=d1;speaker=npc_qingshi_zhizhi;text_key=dialog.strife.d1;next=d2|id=d2;speaker=npc_qingshi_zhizhi;text_key=dialog.strife.d2;options=((text_key=dialog.strife.d2_a;conditions=realm>=qili;next=d3)|(text_key=dialog.strife.d2_b;next=d3))",
                                        "",
                                        "16",
                                        "")));

        List<Product> products =
                new DialogTreeGenerator("dialog_trees_prologue.csv").generate(source);

        assertEquals(1, products.size());
        assertEquals("data/strife/dialog_trees/prologue.json", products.get(0).relativePath());
        JsonObject object = JsonParser.parseString(products.get(0).toJson()).getAsJsonObject();
        assertEquals("d1", object.get("id").getAsString(), "file id = entry tree's root node");
        var nodes = object.getAsJsonArray("trees").get(0).getAsJsonObject().getAsJsonArray("nodes");
        assertEquals(2, nodes.size());
        assertTrue(nodes.get(0).getAsJsonObject().get("options").isJsonNull());
        var options = nodes.get(1).getAsJsonObject().getAsJsonArray("options");
        assertEquals(2, options.size(), object.toString());
        assertEquals(
                "dialog.strife.d2_a",
                options.get(0).getAsJsonObject().get("text_key").getAsString());
        assertEquals(
                "realm>=qili", options.get(0).getAsJsonObject().get("conditions").getAsString());
        assertEquals(
                16,
                object.getAsJsonArray("trees")
                        .get(0)
                        .getAsJsonObject()
                        .get("max_depth_levels")
                        .getAsInt());
    }

    @Test
    void blankNodesStopTheRun(@TempDir Path dir) throws IOException {
        TableSource source =
                readTable(dir, List.of(List.of("dlg_x", "npc_x", "d1", "", "", "16", "")));

        IllegalStateException error =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                new DialogTreeGenerator("dialog_trees_prologue.csv")
                                        .generate(source));

        assertTrue(error.getMessage().contains("nodes"), error.getMessage());
    }

    @Test
    void anEmptyChapterTableProducesNothing(@TempDir Path dir) throws IOException {
        TableSource source = readTable(dir, List.of());

        assertEquals(List.of(), new DialogTreeGenerator("dialog_trees_ch1.csv").generate(source));
    }
}

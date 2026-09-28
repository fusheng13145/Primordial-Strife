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

class QuestGeneratorTest {

    private static final List<String> HEADER =
            List.of(
                    "id",
                    "chapter",
                    "entry",
                    "prerequisites",
                    "objectives",
                    "conditions",
                    "rewards",
                    "reputation_delta",
                    "causality",
                    "timer",
                    "fail_goto",
                    "flags_set",
                    "repeatable",
                    "hidden",
                    "price_reward",
                    "_note");

    private static TableSource readTable(Path dir, List<List<String>> rows) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(String.join(",", HEADER));
        for (List<String> cells : rows) {
            assertEquals(HEADER.size(), cells.size(), "test row must match the contract header");
            lines.add(String.join(",", cells));
        }
        Files.createDirectories(dir);
        Path csv = dir.resolve("quests_prologue.csv");
        Files.write(csv, lines);
        return TableSource.read(csv);
    }

    @Test
    void writesOneChapterFileWhoseIdIsTheEntryQuest(@TempDir Path dir) throws IOException {
        TableSource source =
                readTable(
                        dir,
                        List.of(
                                List.of(
                                        "quest_prologue_root_read_01",
                                        "prologue",
                                        "true",
                                        "",
                                        "id=1;type=talk;target=npc_qingshi_zhizhi;count=1;optional=false",
                                        "",
                                        "type=flag;id=fac_qingshi:prologue:met_elder",
                                        "",
                                        "",
                                        "",
                                        "",
                                        "",
                                        "no",
                                        "false",
                                        "",
                                        ""),
                                List.of(
                                        "quest_prologue_meditation_01",
                                        "prologue",
                                        "false",
                                        "quest_prologue_root_read_01",
                                        "id=1;type=sit;count=60;optional=false",
                                        "",
                                        "type=qi;count=40|type=unlock;unlock_key=meditation",
                                        "",
                                        "",
                                        "",
                                        "",
                                        "",
                                        "no",
                                        "false",
                                        "",
                                        "")));

        List<Product> products = new QuestGenerator("quests_prologue.csv").generate(source);

        assertEquals(1, products.size());
        assertEquals("data/strife/strife_quests/prologue.json", products.get(0).relativePath());
        JsonObject object = JsonParser.parseString(products.get(0).toJson()).getAsJsonObject();
        assertEquals("quest_prologue_root_read_01", object.get("id").getAsString());
        assertEquals(2, object.getAsJsonArray("quests").size());
        JsonObject entry = object.getAsJsonArray("quests").get(0).getAsJsonObject();
        assertTrue(entry.get("entry").getAsBoolean());
        assertEquals(
                "npc_qingshi_zhizhi",
                entry.getAsJsonArray("objectives")
                        .get(0)
                        .getAsJsonObject()
                        .get("target")
                        .getAsString());
        JsonObject second = object.getAsJsonArray("quests").get(1).getAsJsonObject();
        assertTrue(!second.get("entry").getAsBoolean());
        assertEquals(
                40,
                second.getAsJsonArray("rewards").get(0).getAsJsonObject().get("count").getAsInt());
        assertEquals(
                "meditation",
                second.getAsJsonArray("rewards")
                        .get(1)
                        .getAsJsonObject()
                        .get("unlock_key")
                        .getAsString());
        assertTrue(second.get("timer").isJsonNull(), productJson(object));
    }

    @Test
    void reputationDeltaMapShorthandBecomesObjectArray(@TempDir Path dir) throws IOException {
        TableSource source =
                readTable(
                        dir,
                        List.of(
                                List.of(
                                        "quest_prologue_root_read_01",
                                        "prologue",
                                        "true",
                                        "",
                                        "id=1;type=talk;count=1;optional=false",
                                        "",
                                        "()",
                                        "fac_qingshi=10;fac_yuelai=-5",
                                        "",
                                        "",
                                        "",
                                        "",
                                        "no",
                                        "false",
                                        "",
                                        "")));

        JsonObject object =
                JsonParser.parseString(
                                new QuestGenerator("quests_prologue.csv")
                                        .generate(source)
                                        .get(0)
                                        .toJson())
                        .getAsJsonObject();

        var deltas =
                object.getAsJsonArray("quests")
                        .get(0)
                        .getAsJsonObject()
                        .getAsJsonArray("reputation_delta");
        assertEquals(
                "fac_qingshi", deltas.get(0).getAsJsonObject().get("faction_id").getAsString());
        assertEquals(10, deltas.get(0).getAsJsonObject().get("delta").getAsInt());
        assertEquals(-5, deltas.get(1).getAsJsonObject().get("delta").getAsInt());
    }

    @Test
    void aSecondEntryRowStopsTheRun(@TempDir Path dir) throws IOException {
        TableSource source =
                readTable(
                        dir,
                        List.of(
                                List.of(
                                        "quest_a",
                                        "prologue",
                                        "true",
                                        "",
                                        "id=1;type=talk;count=1;optional=false",
                                        "",
                                        "()",
                                        "",
                                        "",
                                        "",
                                        "",
                                        "",
                                        "no",
                                        "false",
                                        "",
                                        ""),
                                List.of(
                                        "quest_b",
                                        "prologue",
                                        "true",
                                        "",
                                        "id=1;type=talk;count=1;optional=false",
                                        "",
                                        "()",
                                        "",
                                        "",
                                        "",
                                        "",
                                        "",
                                        "no",
                                        "false",
                                        "",
                                        "")));

        IllegalStateException error =
                assertThrows(
                        IllegalStateException.class,
                        () -> new QuestGenerator("quests_prologue.csv").generate(source));

        assertTrue(error.getMessage().contains("exactly one entry"), error.getMessage());
    }

    @Test
    void aRowFiledInTheWrongChapterStopsTheRun(@TempDir Path dir) throws IOException {
        TableSource source =
                readTable(
                        dir,
                        List.of(
                                List.of(
                                        "quest_a",
                                        "ch1",
                                        "true",
                                        "",
                                        "id=1;type=talk;count=1;optional=false",
                                        "",
                                        "()",
                                        "",
                                        "",
                                        "",
                                        "",
                                        "",
                                        "no",
                                        "false",
                                        "",
                                        "")));

        IllegalStateException error =
                assertThrows(
                        IllegalStateException.class,
                        () -> new QuestGenerator("quests_prologue.csv").generate(source));

        assertTrue(error.getMessage().contains("wrong chapter"), error.getMessage());
    }

    @Test
    void anEmptyChapterTableProducesNothing(@TempDir Path dir) throws IOException {
        TableSource source = readTable(dir, List.of());

        assertEquals(List.of(), new QuestGenerator("quests_ch1.csv").generate(source));
    }

    @Test
    void blankObjectivesStopTheRun(@TempDir Path dir) throws IOException {
        TableSource source =
                readTable(
                        dir,
                        List.of(
                                List.of(
                                        "quest_a",
                                        "prologue",
                                        "true",
                                        "",
                                        "",
                                        "",
                                        "()",
                                        "",
                                        "",
                                        "",
                                        "",
                                        "",
                                        "no",
                                        "false",
                                        "",
                                        "")));

        IllegalStateException error =
                assertThrows(
                        IllegalStateException.class,
                        () -> new QuestGenerator("quests_prologue.csv").generate(source));

        assertTrue(error.getMessage().contains("objectives"), error.getMessage());
        assertTrue(error.getMessage().contains("必填"), error.getMessage());
    }

    private static String productJson(JsonObject object) {
        return object.toString();
    }
}

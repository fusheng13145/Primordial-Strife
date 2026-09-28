package com.strife.tools.datagen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code tables/quests_<章>.csv} -&gt; {@code data/strife/strife_quests/<章>.json} — 一章一文件
 * (content/JSON_SCHEMA.md §4.6): the file's {@code id} is the chapter's entry quest, and all rows
 * travel inside a {@code quests} array in table order.
 *
 * <p>Structural rules enforced here (re-checked later by V-DAG once implemented): exactly one
 * {@code entry=true} row per chapter, and every row's {@code chapter} cell must match the file name
 * — a quest filed in the wrong chapter would otherwise become unreachable from its DAG.
 */
public final class QuestGenerator implements TableGenerator {

    private static final String CONTRACT = "JSON_SCHEMA §4.6";

    private final String tableFile;
    private final String chapter;

    public QuestGenerator(String tableFile) {
        this.tableFile = tableFile;
        this.chapter = tableFile.replaceFirst("^quests_", "").replaceFirst("\\.csv$", "");
    }

    @Override
    public String tableFile() {
        return tableFile;
    }

    @Override
    public List<Product> generate(TableSource source) {
        if (source.rows().isEmpty()) {
            return List.of(); // 章内容未写是合法状态；一旦有行，入口与 chapter 校验才生效
        }
        List<Map<String, Object>> quests = new ArrayList<>();
        String entryId = null;
        for (TableSource.Record row : source.rows()) {
            String rowChapter = Cells.required(source, row, "chapter", CONTRACT);
            if (!chapter.equals(rowChapter)) {
                throw new IllegalStateException(
                        source.fileName()
                                + ":"
                                + row.line()
                                + ": chapter '"
                                + rowChapter
                                + "' does not match the file's chapter '"
                                + chapter
                                + "' — a quest filed in the wrong chapter breaks its DAG ("
                                + CONTRACT
                                + ")");
            }
            Boolean entry = Cells.bool(source, row, "entry", CONTRACT);
            if (entry && entryId != null) {
                throw new IllegalStateException(
                        source.fileName()
                                + ":"
                                + row.line()
                                + ": second entry=true row '"
                                + row.id()
                                + "' — a chapter has exactly one entry ("
                                + CONTRACT
                                + ")");
            }
            if (entry) {
                entryId = row.id();
            }
            quests.add(quest(source, row, entry));
        }
        if (entryId == null) {
            throw new IllegalStateException(
                    source.fileName()
                            + ": no entry=true row — a chapter needs an entry ("
                            + CONTRACT
                            + ")");
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("id", entryId);
        fields.put("chapter", chapter);
        fields.put("quests", quests);
        return List.of(
                new Product(
                        "data/strife/strife_quests/" + chapter + ".json",
                        fields,
                        source.generatedHeader()));
    }

    private Map<String, Object> quest(TableSource source, TableSource.Record row, boolean entry) {
        Map<String, Object> quest = new LinkedHashMap<>();
        quest.put("id", row.id());
        quest.put("entry", entry);
        quest.put("prerequisites", source.list("prerequisites", row));
        quest.put("objectives", objectives(source, row));
        quest.put("conditions", source.requireScalar("conditions", row));
        quest.put("rewards", rewards(source, row));
        quest.put("reputation_delta", reputationDelta(source, row));
        quest.put("causality", causality(source, row));
        quest.put("timer", timer(source, row));
        quest.put("fail_goto", source.requireScalar("fail_goto", row));
        quest.put(
                "flags_set",
                source.get("flags_set", row) == null ? null : source.list("flags_set", row));
        quest.put("repeatable", Cells.required(source, row, "repeatable", CONTRACT));
        quest.put("hidden", Cells.bool(source, row, "hidden", CONTRACT));
        quest.put("price_reward", Cells.price(source, row, "price_reward"));
        return quest;
    }

    /** {@code id=1;type=sit;target=…;count=60;optional=false|…} — target may be blank (无定位). */
    private List<Map<String, Object>> objectives(TableSource source, TableSource.Record row) {
        List<Map<String, String>> raw = requiredObjects(source, row, "objectives");
        List<Map<String, Object>> objectives = new ArrayList<>();
        for (Map<String, String> object : raw) {
            Map<String, Object> objective = new LinkedHashMap<>();
            objective.put("id", part(source, row, object, "id", "objectives"));
            objective.put("type", part(source, row, object, "type", "objectives"));
            objective.put("target", object.get("target") == null ? null : object.get("target"));
            objective.put(
                    "count",
                    Cells.longOf(
                            source,
                            row,
                            "objectives",
                            part(source, row, object, "count", "objectives")));
            objective.put(
                    "optional",
                    Cells.boolOf(
                            source,
                            row,
                            "objectives",
                            part(source, row, object, "optional", "objectives")));
            objectives.add(objective);
        }
        return objectives;
    }

    /** {@code type=qi;count=40|type=unlock;unlock_key=meditation} — id/unlock_key per type. */
    private List<Map<String, Object>> rewards(TableSource source, TableSource.Record row) {
        List<Map<String, String>> raw = requiredObjects(source, row, "rewards");
        List<Map<String, Object>> rewards = new ArrayList<>();
        for (Map<String, String> object : raw) {
            Map<String, Object> reward = new LinkedHashMap<>();
            reward.put("type", part(source, row, object, "type", "rewards"));
            reward.put("id", object.get("id"));
            reward.put(
                    "count",
                    object.get("count") == null
                            ? null
                            : Cells.longOf(source, row, "rewards", object.get("count")));
            reward.put("unlock_key", object.get("unlock_key"));
            rewards.add(reward);
        }
        return rewards;
    }

    /** Contract type is {@code {faction_id, delta}[]}; the cell writes the map shorthand. */
    private List<Map<String, Object>> reputationDelta(TableSource source, TableSource.Record row) {
        if (source.get("reputation_delta", row) == null) {
            return null;
        }
        List<Map<String, Object>> deltas = new ArrayList<>();
        source.mapping("reputation_delta", row)
                .forEach(
                        (faction, delta) -> {
                            Map<String, Object> entry = new LinkedHashMap<>();
                            entry.put("faction_id", faction);
                            entry.put(
                                    "delta", Cells.longOf(source, row, "reputation_delta", delta));
                            deltas.add(entry);
                        });
        return deltas;
    }

    /** {@code kind=debt;subject_id=…;note_key=…|…} — H3 因果登记，本期只入库不结算. */
    private List<Map<String, Object>> causality(TableSource source, TableSource.Record row) {
        List<Map<String, String>> raw = source.objectList("causality", row);
        if (raw == null) {
            return null;
        }
        List<Map<String, Object>> entries = new ArrayList<>();
        for (Map<String, String> object : raw) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("kind", part(source, row, object, "kind", "causality"));
            entry.put("subject_id", part(source, row, object, "subject_id", "causality"));
            entry.put("note_key", object.get("note_key"));
            entries.add(entry);
        }
        return entries;
    }

    /** {@code sec=<n>;fail_goto=<id>} or a blank cell for null (序章不限时). */
    private Map<String, Object> timer(TableSource source, TableSource.Record row) {
        if (source.get("timer", row) == null) {
            return null;
        }
        Map<String, String> mapping = source.mapping("timer", row);
        Map<String, Object> timer = new LinkedHashMap<>();
        timer.put(
                "sec",
                Cells.longOf(
                        source, row, "timer", requiredCell(source, row, mapping, "sec", "timer")));
        timer.put("fail_goto", mapping.get("fail_goto"));
        return timer;
    }

    private List<Map<String, String>> requiredObjects(
            TableSource source, TableSource.Record row, String column) {
        List<Map<String, String>> objects = source.objectList(column, row);
        if (objects == null) {
            throw new IllegalStateException(
                    source.fileName()
                            + ":"
                            + row.line()
                            + ": column '"
                            + column
                            + "' is 必填 ("
                            + CONTRACT
                            + ") but the row leaves it blank — use () for an explicit empty list");
        }
        return objects;
    }

    private static String part(
            TableSource source,
            TableSource.Record row,
            Map<String, String> object,
            String key,
            String column) {
        return requiredCell(source, row, object, key, column);
    }

    private static String requiredCell(
            TableSource source,
            TableSource.Record row,
            Map<String, String> object,
            String key,
            String column) {
        String value = object.get(key);
        if (value == null) {
            throw new IllegalStateException(
                    source.fileName()
                            + ":"
                            + row.line()
                            + ": column '"
                            + column
                            + "' needs '"
                            + key
                            + "=…' in every object ("
                            + CONTRACT
                            + ")");
        }
        return value;
    }
}

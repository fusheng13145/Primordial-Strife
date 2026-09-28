package com.strife.tools.datagen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code tables/dialog_trees_<章>.csv} -&gt; {@code data/strife/dialog_trees/<章>.json} — 一章一文件
 * (content/JSON_SCHEMA.md §4.7): the file's {@code id} is the chapter's entry tree's root node, and
 * all rows travel inside a {@code trees} array in table order.
 *
 * <p>Node {@code options} arrive as a paren-wrapped object array kept verbatim by the reader; they
 * are unwrapped here into the contracted {@code {text_key, conditions, next, effects}[]} shape.
 * Effects ({@code type} + {@code args}) stay raw strings at both levels: their argument grammar is
 * per-type and belongs to the QuestEngine contract (M3), not to this conversion.
 */
public final class DialogTreeGenerator implements TableGenerator {

    private static final String CONTRACT = "JSON_SCHEMA §4.7";

    private final String tableFile;
    private final String chapter;

    public DialogTreeGenerator(String tableFile) {
        this.tableFile = tableFile;
        this.chapter = tableFile.replaceFirst("^dialog_trees_", "").replaceFirst("\\.csv$", "");
    }

    @Override
    public String tableFile() {
        return tableFile;
    }

    @Override
    public List<Product> generate(TableSource source) {
        if (source.rows().isEmpty()) {
            return List.of(); // 章内容未写是合法状态；一旦有行，下面才要求完整的树
        }
        List<Map<String, Object>> trees = new ArrayList<>();
        String entryNodeId = null;
        for (TableSource.Record row : source.rows()) {
            if (entryNodeId == null) {
                String root = Cells.required(source, row, "root", CONTRACT);
                entryNodeId = root;
            }
            trees.add(tree(source, row));
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("id", entryNodeId);
        fields.put("chapter", chapter);
        fields.put("trees", trees);
        return List.of(
                new Product(
                        "data/strife/dialog_trees/" + chapter + ".json",
                        fields,
                        source.generatedHeader()));
    }

    private Map<String, Object> tree(TableSource source, TableSource.Record row) {
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("id", row.id());
        tree.put("npc", Cells.required(source, row, "npc", CONTRACT));
        tree.put("root", Cells.required(source, row, "root", CONTRACT));
        tree.put("nodes", nodes(source, row));
        tree.put("effects", effects(source, row));
        tree.put(
                "max_depth_levels",
                Cells.longOf(
                        source,
                        row,
                        "max_depth_levels",
                        Cells.required(source, row, "max_depth_levels", CONTRACT)));
        return tree;
    }

    /**
     * {@code id=d1;speaker=…;text_key=…;next=d2;options=(text_key=…;next=end)|id=d2;…} — the one
     * place the contract nests an object array inside an object (§4.7).
     */
    private List<Map<String, Object>> nodes(TableSource source, TableSource.Record row) {
        List<Map<String, String>> raw = source.objectList("nodes", row);
        if (raw == null) {
            throw new IllegalStateException(
                    source.fileName()
                            + ":"
                            + row.line()
                            + ": column 'nodes' is 必填 ("
                            + CONTRACT
                            + ") but the row leaves it blank");
        }
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (Map<String, String> object : raw) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", cell(source, row, object, "id", "nodes"));
            node.put("speaker", object.get("speaker"));
            node.put("text_key", object.get("text_key"));
            node.put("conditions", object.get("conditions"));
            node.put("next", object.get("next"));
            node.put("options", source.parseObjectArray("nodes", row, object.get("options")));
            nodes.add(node);
        }
        return nodes;
    }

    /** Tree-level effects: {@code type=start_quest;args=quest_x|…} — kept raw (M3 contract). */
    private List<Map<String, Object>> effects(TableSource source, TableSource.Record row) {
        List<Map<String, String>> raw = source.objectList("effects", row);
        if (raw == null) {
            return null;
        }
        List<Map<String, Object>> effects = new ArrayList<>();
        for (Map<String, String> object : raw) {
            Map<String, Object> effect = new LinkedHashMap<>();
            effect.put("type", cell(source, row, object, "type", "effects"));
            effect.put("args", object.get("args"));
            effects.add(effect);
        }
        return effects;
    }

    private static String cell(
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

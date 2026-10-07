package com.strife.tools.datagen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code tables/wars.csv} -&gt; {@code data/strife/strife_wars/<id>.json} (content/JSON_SCHEMA
 * §5.3, the declarative conflict table; H7 state machine primitives, the script is not written).
 *
 * <p>{@code belligerents} is exactly two faction ids; {@code cause_predicate}/{@code peace_terms}
 * are DSL strings; {@code phases} is a list of {@code {id, entry_conditions, world_effects,
 * duration_ticks}} where the two {@code *effects} fields are DSL strings the runtime interprets;
 * {@code consequences} is a list of {@code {type, args}}. This converter only splits the table
 * grammar, it does not adjudicate the DSL — the Validator checks that belligerents reference real
 * factions (V-REF phase 2).
 */
public final class WarsGenerator implements TableGenerator {

    private static final String CONTRACT = "JSON_SCHEMA §5.3";

    @Override
    public String tableFile() {
        return "wars.csv";
    }

    @Override
    public List<Product> generate(TableSource source) {
        return source.rows().stream()
                .map(
                        row -> {
                            Map<String, Object> fields = new LinkedHashMap<>();
                            fields.put("id", row.id());
                            fields.put("belligerents", belligerents(source, row));
                            fields.put(
                                    "cause_predicate",
                                    Cells.required(source, row, "cause_predicate", CONTRACT));
                            fields.put("phases", phases(source, row));
                            fields.put(
                                    "peace_terms",
                                    Cells.required(source, row, "peace_terms", CONTRACT));
                            fields.put("consequences", consequences(source, row));
                            fields.put("form", Cells.required(source, row, "form", CONTRACT));
                            return new Product(
                                    "data/strife/strife_wars/" + row.id() + ".json",
                                    fields,
                                    source.generatedHeader());
                        })
                .toList();
    }

    private static List<String> belligerents(TableSource source, TableSource.Record row) {
        String value = source.get("belligerents", row);
        if (value == null) {
            throw new IllegalStateException(
                    source.fileName()
                            + ":"
                            + row.line()
                            + ": column 'belligerents' is 必填 ("
                            + CONTRACT
                            + ") but the row leaves it blank");
        }
        List<String> ids = source.list("belligerents", row);
        if (ids.size() != 2) {
            throw new IllegalStateException(
                    source.fileName()
                            + ":"
                            + row.line()
                            + ": belligerents expects exactly two faction ids ("
                            + CONTRACT
                            + "), got "
                            + ids.size()
                            + " ('"
                            + value
                            + "')");
        }
        return List.copyOf(ids);
    }

    private static List<Map<String, Object>> phases(TableSource source, TableSource.Record row) {
        List<Map<String, String>> objects = source.objectList("phases", row);
        if (objects == null) {
            throw new IllegalStateException(
                    source.fileName()
                            + ":"
                            + row.line()
                            + ": column 'phases' is 必填 ("
                            + CONTRACT
                            + ") but the row leaves it blank");
        }
        List<Map<String, Object>> phases = new ArrayList<>();
        for (Map<String, String> object : objects) {
            Map<String, Object> phase = new LinkedHashMap<>();
            phase.put("id", part(source, row, object, "id", "phases"));
            phase.put("entry_conditions", part(source, row, object, "entry_conditions", "phases"));
            phase.put("world_effects", part(source, row, object, "world_effects", "phases"));
            phase.put(
                    "duration_ticks",
                    Cells.longOf(
                            source,
                            row,
                            "phases",
                            part(source, row, object, "duration_ticks", "phases")));
            phases.add(phase);
        }
        return phases;
    }

    private static List<Map<String, Object>> consequences(
            TableSource source, TableSource.Record row) {
        List<Map<String, String>> objects = source.objectList("consequences", row);
        if (objects == null) {
            throw new IllegalStateException(
                    source.fileName()
                            + ":"
                            + row.line()
                            + ": column 'consequences' is 必填 ("
                            + CONTRACT
                            + ") but the row leaves it blank");
        }
        List<Map<String, Object>> results = new ArrayList<>();
        for (Map<String, String> object : objects) {
            Map<String, Object> consequence = new LinkedHashMap<>();
            consequence.put("type", part(source, row, object, "type", "consequences"));
            consequence.put("args", part(source, row, object, "args", "consequences"));
            results.add(consequence);
        }
        return results;
    }

    private static String part(
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

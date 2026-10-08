package com.strife.tools.datagen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code tables/periods.csv} -&gt; {@code data/strife/strife_periods/<id>.json}
 * (content/JSON_SCHEMA §5.1, the H5 global-clock period events).
 *
 * <p>{@code effect_on_world} is a list of {@code {type, args}} objects (JSON_SCHEMA §2); the args
 * string is a DSL the runtime interprets, so this converter only splits it, it does not adjudicate.
 * {@code condition} is an optional DSL predicate (blank cell -&gt; null).
 */
public final class PeriodsGenerator implements TableGenerator {

    private static final String CONTRACT = "JSON_SCHEMA §5.1";

    @Override
    public String tableFile() {
        return "periods.csv";
    }

    @Override
    public List<Product> generate(TableSource source) {
        return source.rows().stream()
                .map(
                        row -> {
                            Map<String, Object> fields = new LinkedHashMap<>();
                            fields.put("id", row.id());
                            fields.put("kind", Cells.required(source, row, "kind", CONTRACT));
                            fields.put(
                                    "period_ticks",
                                    Cells.longOf(
                                            source,
                                            row,
                                            "period_ticks",
                                            Cells.required(source, row, "period_ticks", CONTRACT)));
                            fields.put(
                                    "window_sec",
                                    Cells.longOf(
                                            source,
                                            row,
                                            "window_sec",
                                            Cells.required(source, row, "window_sec", CONTRACT)));
                            String condition = source.get("condition", row);
                            fields.put("condition", condition == null ? null : condition);
                            fields.put("effect_on_world", worldEffects(source, row));
                            return new Product(
                                    "data/strife/strife_periods/" + row.id() + ".json",
                                    fields,
                                    source.generatedHeader());
                        })
                .toList();
    }

    private static List<Map<String, Object>> worldEffects(
            TableSource source, TableSource.Record row) {
        List<Map<String, String>> objects = source.objectList("effect_on_world", row);
        if (objects == null) {
            throw new IllegalStateException(
                    source.fileName()
                            + ":"
                            + row.line()
                            + ": column 'effect_on_world' is 必填 ("
                            + CONTRACT
                            + ") but the row leaves it blank — fill it or drop the row");
        }
        List<Map<String, Object>> effects = new ArrayList<>();
        for (Map<String, String> object : objects) {
            Map<String, Object> effect = new LinkedHashMap<>();
            effect.put("type", part(source, row, object, "type", "effect_on_world"));
            effect.put("args", part(source, row, object, "args", "effect_on_world"));
            effects.add(effect);
        }
        return effects;
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

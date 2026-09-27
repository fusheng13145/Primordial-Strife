package com.strife.tools.datagen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code tables/pills.csv} -&gt; {@code data/strife/strife_pills/<id>.json} (content/JSON_SCHEMA.md
 * §4.4).
 *
 * <p>{@code effect_key} expands inline from NUMBERS §7 @@pills (§3), so the shipped product carries
 * the bonus values the runtime actually applies. Whether {@code outputs} probabilities sum to 1 is
 * V-PROB's call, not this conversion's.
 */
public final class PillGenerator implements TableGenerator {

    private static final String CONTRACT = "JSON_SCHEMA §4.4";

    private final NumbersSource numbers;

    public PillGenerator(NumbersSource numbers) {
        this.numbers = numbers;
    }

    @Override
    public String tableFile() {
        return "pills.csv";
    }

    @Override
    public List<Product> generate(TableSource source) {
        return source.rows().stream()
                .map(
                        row -> {
                            Map<String, Object> fields = new LinkedHashMap<>();
                            fields.put("id", row.id());
                            fields.put(
                                    "quality_tier",
                                    Cells.required(source, row, "quality_tier", CONTRACT));
                            fields.put("pattern", Cells.required(source, row, "pattern", CONTRACT));
                            fields.put(
                                    "core_slot",
                                    Cells.required(source, row, "core_slot", CONTRACT));
                            fields.put(
                                    "materials",
                                    typedMaterials(
                                            source,
                                            row,
                                            requiredObjects(source, row, "materials")));
                            fields.put("heat_range", heatRange(source, row));
                            fields.put(
                                    "outputs",
                                    typedOutputs(
                                            source, row, requiredObjects(source, row, "outputs")));
                            String effectKey = source.requireScalar("effect_key", row);
                            fields.put("effect_key", effectKey);
                            // NUMBERS §7 keys the block by pill id (pill_juqi, …), so the key is
                            // both the reference and the entry name; a missing entry fails here
                            // with the row named (NumbersSource names block+key).
                            fields.put(
                                    "effect",
                                    effectKey == null ? null : numbers.map("pills", effectKey));
                            if (source.get("failure_output", row) == null) {
                                fields.put("failure_output", null);
                            } else {
                                Map<String, String> mapping = source.mapping("failure_output", row);
                                Map<String, Object> failure = new LinkedHashMap<>();
                                failure.put(
                                        "item_id",
                                        part(source, row, mapping, "item_id", "failure_output"));
                                failure.put(
                                        "count",
                                        Cells.longOf(
                                                source,
                                                row,
                                                "failure_output",
                                                part(
                                                        source,
                                                        row,
                                                        mapping,
                                                        "count",
                                                        "failure_output")));
                                fields.put("failure_output", failure);
                            }
                            fields.put("price", Cells.price(source, row));
                            return new Product(
                                    "data/strife/strife_pills/" + row.id() + ".json",
                                    fields,
                                    source.generatedHeader());
                        })
                .toList();
    }

    /** {@code 0.30;0.70} — the contract's [min,max] binary range (FILLING_GUIDE §1.3). */
    private static List<Double> heatRange(TableSource source, TableSource.Record row) {
        List<String> parts = source.list("heat_range", row);
        if (parts.size() != 2) {
            throw new IllegalStateException(
                    source.fileName()
                            + ":"
                            + row.line()
                            + ": column 'heat_range' expects 'min;max' ("
                            + CONTRACT
                            + "), got "
                            + parts.size()
                            + " values");
        }
        return List.of(
                Cells.doubleOf(source, row, "heat_range", parts.get(0)),
                Cells.doubleOf(source, row, "heat_range", parts.get(1)));
    }

    private static List<Map<String, String>> requiredObjects(
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
                            + ") but the row leaves it blank");
        }
        return objects;
    }

    private static List<Map<String, Object>> typedMaterials(
            TableSource source, TableSource.Record row, List<Map<String, String>> objects) {
        List<Map<String, Object>> materials = new ArrayList<>();
        for (Map<String, String> object : objects) {
            Map<String, Object> material = new LinkedHashMap<>();
            material.put("item_id", part(source, row, object, "item_id", "materials"));
            material.put(
                    "count",
                    Cells.longOf(
                            source,
                            row,
                            "materials",
                            part(source, row, object, "count", "materials")));
            materials.add(material);
        }
        return materials;
    }

    private static List<Map<String, Object>> typedOutputs(
            TableSource source, TableSource.Record row, List<Map<String, String>> objects) {
        List<Map<String, Object>> outputs = new ArrayList<>();
        for (Map<String, String> object : objects) {
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("item_id", part(source, row, object, "item_id", "outputs"));
            output.put(
                    "count",
                    Cells.longOf(
                            source, row, "outputs", part(source, row, object, "count", "outputs")));
            output.put(
                    "prob",
                    Cells.doubleOf(
                            source, row, "outputs", part(source, row, object, "prob", "outputs")));
            output.put("quality", part(source, row, object, "quality", "outputs"));
            outputs.add(output);
        }
        return outputs;
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

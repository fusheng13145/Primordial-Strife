package com.strife.tools.datagen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code tables/spirit_field.csv} -&gt; {@code data/strife/strife_spirit_field/<id>.json}
 * (content/JSON_SCHEMA.md §4.8, the ambient-qi field half of worldgen).
 *
 * <p>One row, one product: the coarse-grained ambient-qi ratio for a region (03 §6 region cache).
 * The ratio must stay inside {@code [ambient_qi_min, ambient_qi_max]} from NUMBERS @@world and must
 * never be 0 — a 0 ratio would make a region "zero growth with no explanation", which the contract
 * forbids (05 §2). That bound lives in the truth source, so the Validator checks it (V-RANGE), not
 * this converter.
 */
public final class SpiritFieldGenerator implements TableGenerator {

    private static final String CONTRACT = "JSON_SCHEMA §4.8";

    @Override
    public String tableFile() {
        return "spirit_field.csv";
    }

    @Override
    public List<Product> generate(TableSource source) {
        return source.rows().stream()
                .map(
                        row -> {
                            Map<String, Object> fields = new LinkedHashMap<>();
                            fields.put("id", row.id());
                            fields.put(
                                    "region_id",
                                    Cells.required(source, row, "region_id", CONTRACT));
                            fields.put(
                                    "ambient_qi_ratio",
                                    Cells.doubleOf(
                                            source,
                                            row,
                                            "ambient_qi_ratio",
                                            Cells.required(
                                                    source, row, "ambient_qi_ratio", CONTRACT)));
                            return new Product(
                                    "data/strife/strife_spirit_field/" + row.id() + ".json",
                                    fields,
                                    source.generatedHeader());
                        })
                .toList();
    }
}

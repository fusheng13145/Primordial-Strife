package com.strife.tools.datagen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code tables/techniques.csv} -&gt; {@code data/strife/strife_techniques/<id>.json}
 * (content/JSON_SCHEMA.md §4.2). No NUMBERS expansion: {@code qi_rate_ratio} is a per-technique
 * multiplier the contract lets the table carry directly (05 §2 公式第四项), unlike the shared rate tiers
 * spells use.
 */
public final class TechniqueGenerator implements TableGenerator {

    private static final String CONTRACT = "JSON_SCHEMA §4.2";

    @Override
    public String tableFile() {
        return "techniques.csv";
    }

    @Override
    public List<Product> generate(TableSource source) {
        return source.rows().stream()
                .map(
                        row -> {
                            Map<String, Object> fields = new LinkedHashMap<>();
                            fields.put("id", row.id());
                            fields.put("grade", Cells.required(source, row, "grade", CONTRACT));
                            fields.put("element", Cells.required(source, row, "element", CONTRACT));
                            fields.put(
                                    "required_realm",
                                    Cells.required(source, row, "required_realm", CONTRACT));
                            // 可, default 1 (§4.2): the contract states the default, so DataGen
                            // writes it into the product instead of leaving it to the loader.
                            String stage = source.requireScalar("required_stage", row);
                            fields.put(
                                    "required_stage",
                                    stage == null
                                            ? Long.valueOf(1)
                                            : Cells.longOf(source, row, "required_stage", stage));
                            fields.put(
                                    "required_spiritroot",
                                    source.get("required_spiritroot", row) == null
                                            ? null
                                            : source.list("required_spiritroot", row));
                            fields.put("affinity_rule", source.requireScalar("affinity_rule", row));
                            fields.put(
                                    "qi_rate_ratio",
                                    Cells.doubleOf(
                                            source,
                                            row,
                                            "qi_rate_ratio",
                                            Cells.required(
                                                    source, row, "qi_rate_ratio", CONTRACT)));
                            fields.put(
                                    "passives",
                                    source.get("passives", row) == null
                                            ? null
                                            : source.list("passives", row));
                            fields.put(
                                    "grants_spells",
                                    source.get("grants_spells", row) == null
                                            ? null
                                            : source.list("grants_spells", row));
                            fields.put("faction", source.requireScalar("faction", row));
                            fields.put("price", Cells.price(source, row));
                            fields.put("source", source.requireScalar("source", row));
                            fields.put(
                                    "disabled_reason_key",
                                    source.requireScalar("disabled_reason_key", row));
                            return new Product(
                                    "data/strife/strife_techniques/" + row.id() + ".json",
                                    fields,
                                    source.generatedHeader());
                        })
                .toList();
    }
}

package com.strife.tools.datagen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code tables/artifacts.csv} -&gt; {@code data/strife/strife_artifacts/<id>.json}
 * (content/JSON_SCHEMA.md §4.5).
 *
 * <p>{@code quality_probs} is contracted as a list of {@code {quality_tier, prob}} objects; the
 * table writes it in the map shorthand the guide froze ({@code fan=0.70;di=0.25}), so this
 * generator converts shape, keeping cell order. {@code active_skill.cooldown_sec} expands from
 * NUMBERS §8 @@combat {@code artifact_cooldown_sec} (§3).
 */
public final class ArtifactGenerator implements TableGenerator {

    private static final String CONTRACT = "JSON_SCHEMA §4.5";

    private final NumbersSource numbers;

    public ArtifactGenerator(NumbersSource numbers) {
        this.numbers = numbers;
    }

    @Override
    public String tableFile() {
        return "artifacts.csv";
    }

    @Override
    public List<Product> generate(TableSource source) {
        return source.rows().stream()
                .map(
                        row -> {
                            Map<String, Object> fields = new LinkedHashMap<>();
                            fields.put("id", row.id());
                            fields.put("blank", Cells.required(source, row, "blank", CONTRACT));
                            fields.put(
                                    "restriction_count",
                                    Cells.longOf(
                                            source,
                                            row,
                                            "restriction_count",
                                            Cells.required(
                                                    source, row, "restriction_count", CONTRACT)));
                            fields.put(
                                    "core_slot",
                                    Cells.required(source, row, "core_slot", CONTRACT));
                            fields.put(
                                    "materials",
                                    typedMaterials(
                                            source,
                                            row,
                                            requiredObjects(source, row, "materials")));
                            fields.put("quality_probs", qualityProbs(source, row));
                            fields.put(
                                    "slot_type",
                                    Cells.required(source, row, "slot_type", CONTRACT));
                            fields.put(
                                    "required_realm",
                                    Cells.required(source, row, "required_realm", CONTRACT));
                            fields.put("active_skill", activeSkill(source, row));
                            fields.put(
                                    "passives",
                                    source.get("passives", row) == null
                                            ? null
                                            : source.list("passives", row));
                            fields.put(
                                    "durability_points",
                                    Cells.longOf(
                                            source,
                                            row,
                                            "durability_points",
                                            Cells.required(
                                                    source, row, "durability_points", CONTRACT)));
                            fields.put("price", Cells.price(source, row));
                            return new Product(
                                    "data/strife/strife_artifacts/" + row.id() + ".json",
                                    fields,
                                    source.generatedHeader());
                        })
                .toList();
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

    private static List<Map<String, Object>> qualityProbs(
            TableSource source, TableSource.Record row) {
        Cells.required(source, row, "quality_probs", CONTRACT);
        List<Map<String, Object>> probs = new ArrayList<>();
        for (Map.Entry<String, String> entry : source.mapping("quality_probs", row).entrySet()) {
            Map<String, Object> prob = new LinkedHashMap<>();
            prob.put("quality_tier", entry.getKey());
            prob.put("prob", Cells.doubleOf(source, row, "quality_probs", entry.getValue()));
            probs.add(prob);
        }
        return probs;
    }

    /** {@code spell_id=…;cooldown_key=…} or a blank cell for null (no active skill). */
    private Map<String, Object> activeSkill(TableSource source, TableSource.Record row) {
        if (source.get("active_skill", row) == null) {
            return null;
        }
        Map<String, String> mapping = source.mapping("active_skill", row);
        String spellId = mapping.get("spell_id");
        String cooldownKey = mapping.get("cooldown_key");
        if (spellId == null || cooldownKey == null) {
            throw new IllegalStateException(
                    source.fileName()
                            + ":"
                            + row.line()
                            + ": column 'active_skill' expects 'spell_id=<id>;cooldown_key=<tier>' ("
                            + CONTRACT
                            + "), got '"
                            + source.get("active_skill", row)
                            + "'");
        }
        Map<String, Object> skill = new LinkedHashMap<>();
        skill.put("spell_id", spellId);
        skill.put("cooldown_key", cooldownKey);
        // NUMBERS §8 carries a single artifact cooldown, not per-tier values (the guide's
        // "三档" note and the truth source disagree — the truth source wins here, and the
        // mismatch is filed for C/A review).
        skill.put("cooldown_sec", numbers.value("combat", "artifact_cooldown_sec"));
        return skill;
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

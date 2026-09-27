package com.strife.tools.datagen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code tables/spells.csv} -&gt; {@code data/strife/strife_spells/<id>.json}
 * (content/JSON_SCHEMA.md §4.3).
 *
 * <p>The three {@code *_key} columns are expanded inline from NUMBERS §8 @@combat per §3: the
 * product carries both the key (what the table owns) and the value (what runtime reads), so the
 * shipped content never needs the markdown and the truth source stays single.
 */
public final class SpellGenerator implements TableGenerator {

    private static final String CONTRACT = "JSON_SCHEMA §4.3";

    private final NumbersSource numbers;

    public SpellGenerator(NumbersSource numbers) {
        this.numbers = numbers;
    }

    @Override
    public String tableFile() {
        return "spells.csv";
    }

    @Override
    public List<Product> generate(TableSource source) {
        return source.rows().stream()
                .map(
                        row -> {
                            Map<String, Object> fields = new LinkedHashMap<>();
                            fields.put("id", row.id());
                            fields.put("element", Cells.required(source, row, "element", CONTRACT));
                            fields.put(
                                    "required_technique",
                                    Cells.required(source, row, "required_technique", CONTRACT));
                            fields.put(
                                    "cost_key", Cells.required(source, row, "cost_key", CONTRACT));
                            fields.put(
                                    "cost_qi",
                                    Cells.fromNumbers(
                                            source,
                                            row,
                                            numbers,
                                            "combat",
                                            "spell_cost_qi",
                                            fields.get("cost_key").toString()));
                            fields.put(
                                    "cooldown_key",
                                    Cells.required(source, row, "cooldown_key", CONTRACT));
                            fields.put(
                                    "cooldown_sec",
                                    Cells.fromNumbers(
                                            source,
                                            row,
                                            numbers,
                                            "combat",
                                            "spell_cooldown_sec",
                                            fields.get("cooldown_key").toString()));
                            fields.put(
                                    "damage_formula_key",
                                    Cells.required(source, row, "damage_formula_key", CONTRACT));
                            fields.put(
                                    "damage_formula",
                                    numbers.map(
                                            "combat", fields.get("damage_formula_key").toString()));
                            fields.put("projectile", projectile(source, row));
                            String aoe = source.requireScalar("aoe_radius_blocks", row);
                            fields.put(
                                    "aoe_radius_blocks",
                                    aoe == null
                                            ? null
                                            : Cells.doubleOf(
                                                    source, row, "aoe_radius_blocks", aoe));
                            fields.put("effects", effects(source, row));
                            fields.put("price", Cells.price(source, row));
                            return new Product(
                                    "data/strife/strife_spells/" + row.id() + ".json",
                                    fields,
                                    source.generatedHeader());
                        })
                .toList();
    }

    /** {@code speed=…;gravity=…;range=…;pierce_count=…} or a blank cell for null (瞬时/自身). */
    private static Map<String, Object> projectile(TableSource source, TableSource.Record row) {
        if (source.get("projectile", row) == null) {
            return null;
        }
        Map<String, String> mapping = source.mapping("projectile", row);
        Map<String, Object> projectile = new LinkedHashMap<>();
        projectile.put(
                "speed",
                Cells.doubleOf(
                        source,
                        row,
                        "projectile",
                        requirePart(source, row, mapping, "speed", "projectile")));
        projectile.put(
                "gravity",
                Cells.doubleOf(
                        source,
                        row,
                        "projectile",
                        requirePart(source, row, mapping, "gravity", "projectile")));
        projectile.put(
                "range",
                Cells.doubleOf(
                        source,
                        row,
                        "projectile",
                        requirePart(source, row, mapping, "range", "projectile")));
        projectile.put(
                "pierce_count",
                Cells.longOf(
                        source,
                        row,
                        "projectile",
                        requirePart(source, row, mapping, "pierce_count", "projectile")));
        return projectile;
    }

    /** {@code effect_id=…;duration_sec=…;amp=…|…} or null; contract type is a list of objects. */
    private static List<Map<String, Object>> effects(TableSource source, TableSource.Record row) {
        List<Map<String, String>> raw = source.objectList("effects", row);
        if (raw == null) {
            return null;
        }
        List<Map<String, Object>> effects = new ArrayList<>();
        for (Map<String, String> object : raw) {
            Map<String, Object> effect = new LinkedHashMap<>();
            effect.put("effect_id", requirePart(source, row, object, "effect_id", "effects"));
            effect.put(
                    "duration_sec",
                    Cells.doubleOf(
                            source,
                            row,
                            "effects",
                            requirePart(source, row, object, "duration_sec", "effects")));
            effect.put(
                    "amp",
                    Cells.doubleOf(
                            source,
                            row,
                            "effects",
                            requirePart(source, row, object, "amp", "effects")));
            effects.add(effect);
        }
        return effects;
    }

    private static String requirePart(
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

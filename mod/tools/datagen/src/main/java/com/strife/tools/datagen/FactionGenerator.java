package com.strife.tools.datagen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code tables/factions.csv} -&gt; {@code data/strife/strife_factions/<id>.json}
 * (content/JSON_SCHEMA.md §5.2, the H2/H7 home of faction data in 04 §2).
 *
 * <p>Two contract lines shape this class:
 *
 * <ul>
 *   <li>{@code rep_range} is fixed at {@code [-100,100]} and deliberately not in the table, so it
 *       is deliberately not in the product either — writing the bounds here would put a managed
 *       number literal in code, which docs/06 forbids.
 *   <li>Field-presence and enum-legality checks belong to the Validator (04 §6), not here. This
 *       class converts; it does not adjudicate. That keeps the two tools from diverging about what
 *       "valid" means once {@code alignment} gains its STORY-ratified values.
 * </ul>
 */
public final class FactionGenerator implements TableGenerator {

    @Override
    public String tableFile() {
        return "factions.csv";
    }

    @Override
    public List<Product> generate(TableSource source) {
        return source.rows().stream()
                .map(
                        row -> {
                            Map<String, Object> fields = new LinkedHashMap<>();
                            fields.put("id", row.id());
                            fields.put("display_name_key", displayNameKey(source, row));
                            fields.put("alignment", source.requireScalar("alignment", row));
                            fields.put("home_region", source.requireScalar("home_region", row));
                            fields.put("relations", attitudes(source, row));
                            return new Product(
                                    "data/strife/strife_factions/" + row.id() + ".json",
                                    fields,
                                    source.generatedHeader());
                        })
                .toList();
    }

    /**
     * The lang key is never invented here: {@code faction.strife.<id>} is still marked {@code [拟]}
     * in JSON_SCHEMA §4.10, so an empty cell is a gap the table owner must close, not a default.
     */
    private static String displayNameKey(TableSource source, TableSource.Record row) {
        String key = source.requireScalar("display_name_key", row);
        if (key == null) {
            throw new IllegalStateException(
                    source.fileName()
                            + ":"
                            + row.line()
                            + ": display_name_key is empty and no lang key is derived from the id,"
                            + " because the 'faction.strife.<id>' prefix is still [拟] in"
                            + " content/JSON_SCHEMA.md §4.10. Write the key, or get the derivation"
                            + " ratified and implement it here.");
        }
        return key;
    }

    /**
     * {@code fac_id=attitude;fac_id=attitude} (JSON_SCHEMA §2) -&gt; id to number, in cell order.
     */
    private static Map<String, Long> attitudes(TableSource source, TableSource.Record row) {
        Map<String, Long> relations = new LinkedHashMap<>();
        source.mapping("relations", row)
                .forEach(
                        (target, attitude) -> {
                            try {
                                relations.put(target, Long.parseLong(attitude));
                            } catch (NumberFormatException e) {
                                throw new IllegalStateException(
                                        source.fileName()
                                                + ":"
                                                + row.line()
                                                + ": relations entry '"
                                                + target
                                                + "="
                                                + attitude
                                                + "' has a non-integer attitude value"
                                                + " (H7 wants a number in -100..100)",
                                        e);
                            }
                        });
        return relations;
    }
}

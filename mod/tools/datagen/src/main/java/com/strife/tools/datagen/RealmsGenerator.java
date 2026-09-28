package com.strife.tools.datagen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NUMBERS §1 {@code @@realms} -&gt; {@code data/strife/strife_realms/<id>.json}
 * (content/JSON_SCHEMA.md §4.1, the ADR-005 M0 exit artifact made playable data).
 *
 * <p>Derivations the contract pins to DataGen, so the truth source never carries a second copy:
 *
 * <ul>
 *   <li>{@code ordinal} = the written order of the @@realms block (链序即真相, §4.1);
 *   <li>{@code tribulation} = whether {@code unlocks} contains {@code tribulation};
 *   <li>{@code display_name_key} = the §4.10 rule {@code realm.strife.<id>};
 *   <li>{@code disabled_by_placeholder} = the row's inline {@code [占位]} comment marker (后三段 境界命名待
 *       STORY 定稿), so the Validator's monotonicity checks can relax for them without NUMBERS
 *       gaining a second field.
 * </ul>
 */
public final class RealmsGenerator implements NumbersGenerator {

    @Override
    public List<Product> generate(NumbersSource numbers) {
        List<Product> products = new ArrayList<>();
        int ordinal = 0;
        for (Map.Entry<String, Object> entry : numbers.block("realms").entrySet()) {
            String id = entry.getKey();
            if (!(entry.getValue() instanceof Map)) {
                throw new IllegalStateException(
                        "content/NUMBERS.md: @@realms entry '"
                                + id
                                + "' must be a { qi_max: …, … } mapping (NUMBERS §1)");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> realm = (Map<String, Object>) entry.getValue();
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("id", id);
            fields.put("ordinal", (long) ordinal++);
            fields.put("qi_max", require(realm, id, "qi_max"));
            fields.put("stage_count", require(realm, id, "stage_count"));
            fields.put("lifespan_years", require(realm, id, "lifespan_years"));
            fields.put("sit_rate", require(realm, id, "sit_rate"));
            fields.put("unlocks", require(realm, id, "unlocks"));
            fields.put("breakthrough_success_key", require(realm, id, "breakthrough_success_key"));
            Object unlocks = fields.get("unlocks");
            fields.put(
                    "tribulation", unlocks instanceof List<?> list && list.contains("tribulation"));
            fields.put("display_name_key", "realm.strife." + id);
            String comment = numbers.comment("realms", id);
            fields.put("disabled_by_placeholder", comment != null && comment.contains("[占位]"));
            products.add(
                    new Product(
                            "data/strife/strife_realms/" + id + ".json",
                            fields,
                            numbers.generatedHeader()));
        }
        return products;
    }

    private static Object require(Map<String, Object> realm, String id, String key) {
        Object value = realm.get(key);
        if (value == null) {
            throw new IllegalStateException(
                    "content/NUMBERS.md: @@realms entry '"
                            + id
                            + "' has no "
                            + key
                            + " — all of qi_max/stage_count/lifespan_years/sit_rate/unlocks/"
                            + "breakthrough_success_key are 必填 (JSON_SCHEMA §4.1)");
        }
        return value;
    }
}

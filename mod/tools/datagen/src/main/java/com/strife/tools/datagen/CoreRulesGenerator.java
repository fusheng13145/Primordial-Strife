package com.strife.tools.datagen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NUMBERS @@limits / @@rate_limits -&gt; {@code data/strife/strife_core/rules.json} — the core
 * runtime's numeric table (JSON_SCHEMA §4.12).
 *
 * <p>Both blocks are managed numbers: @@limits carries the engineering red lines (attachment
 * budget, packet budget, resync interval) and @@rate_limits carries the C2S intent limits (03 §4).
 * They used to live only in prose, which forced the networking layer to either hardcode them (a
 * managed literal, AGENTS.md) or leave the limits unimplemented — this product is the third option.
 *
 * <p>{@code id} is {@code core_rules}: a config file, not content — V-TEXT's lang prefix table has
 * no entry for the domain, the same exemption {@code realm_rules} / {@code world_rules} use.
 */
public final class CoreRulesGenerator implements NumbersGenerator {

    private static final List<String> BLOCKS = List.of("limits", "rate_limits");

    @Override
    public List<Product> generate(NumbersSource numbers) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("id", "core_rules");
        for (String block : BLOCKS) {
            fields.put(block, numbers.block(block));
        }
        return List.of(
                new Product(
                        "data/strife/strife_core/rules.json", fields, numbers.generatedHeader()));
    }
}

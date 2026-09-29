package com.strife.tools.datagen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NUMBERS @@breakthrough / @@breakthrough_cost / @@meditation / @@spiritroot -&gt; {@code
 * data/strife/strife_realms/rules.json} — the M1 realm runtime's numeric table (MVP 快速通道).
 *
 * <p>The realm package reads this at datapack reload, so every managed number flows from the truth
 * source through DataGen exactly like realm stats do — no managed literals in code (AGENTS.md).
 * {@code id} is {@code realm_rules}: a config file, not content — V-TEXT exempts it from the
 * per-realm lang key rule.
 */
public final class RealmRulesGenerator implements NumbersGenerator {

    private static final List<String> BLOCKS =
            List.of("breakthrough", "breakthrough_cost", "meditation", "spiritroot");

    @Override
    public List<Product> generate(NumbersSource numbers) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("id", "realm_rules");
        for (String block : BLOCKS) {
            fields.put(block, numbers.block(block));
        }
        return List.of(
                new Product(
                        "data/strife/strife_realms/rules.json", fields, numbers.generatedHeader()));
    }
}

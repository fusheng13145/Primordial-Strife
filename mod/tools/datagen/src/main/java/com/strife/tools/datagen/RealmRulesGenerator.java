package com.strife.tools.datagen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NUMBERS @@breakthrough / @@breakthrough_cost / @@meditation / @@spiritroot / @@lifespan -&gt;
 * {@code data/strife/strife_realms/rules.json} — the M1 realm runtime's numeric table.
 *
 * <p>The realm package reads this at datapack reload, so every managed number flows from the truth
 * source through DataGen exactly like realm stats do — no managed literals in code (AGENTS.md).
 * {@code id} is {@code realm_rules}: a config file, not content — V-TEXT exempts it from the
 * per-realm lang key rule.
 *
 * <p>{@code @@lifespan} 必须在这一份里：寿元与大限（{@code dasheng_realm_drop_stages} / {@code
 * dasheng_reset_years_ratio} / {@code dasheng_debuff_key}）都住在那个块，而它们是 realm 运行时
 * 要读的。此前该块缺失，运行时解析会在第一个玩家登录时抛 NPE 并被容错分支吞掉——表现是"整个 realm 系统静默不工作"，而不是任何可诊断的错误。
 */
public final class RealmRulesGenerator implements NumbersGenerator {

    private static final List<String> BLOCKS =
            List.of("breakthrough", "breakthrough_cost", "meditation", "spiritroot", "lifespan");

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

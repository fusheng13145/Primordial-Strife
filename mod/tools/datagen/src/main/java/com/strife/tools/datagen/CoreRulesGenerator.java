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

    /** Minecraft 固定为 20 刻/秒——平台常量，不是可调数值，所以不进 NUMBERS。 */
    private static final long TICKS_PER_SECOND = 20L;

    @Override
    public List<Product> generate(NumbersSource numbers) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("id", "core_rules");
        for (String block : BLOCKS) {
            fields.put(block, numbers.block(block));
        }
        fields.put("derived", derived(numbers));
        return List.of(
                new Product(
                        "data/strife/strife_core/rules.json", fields, numbers.generatedHeader()));
    }

    /**
     * 由其他块<b>算出来</b>的值（JSON_SCHEMA §4.12）。放在生成期而不是写进 NUMBERS 的理由：{@code ticks_per_year} 不是 独立事实，而是
     * {@code @@lifespan.seconds_per_year} 的换算结果——写进 NUMBERS 就有了两个可以互相矛盾的真相，
     * 写进代码就是受管字面量（AGENTS.md）。生成期算一次是唯一不会漂移的位置。
     *
     * <p>顺带修掉一个真实精度坑：旧键 {@code years_per_realtime_sec: 0.000833} 是 1/1200 的三位近似，反推得到 24010 刻/年（一年差
     * 10 刻）。精确的"多少现实秒 = 1 修行年"没有这个问题。
     */
    private static Map<String, Object> derived(NumbersSource numbers) {
        long secondsPerYear = ((Number) numbers.value("lifespan", "seconds_per_year")).longValue();
        Map<String, Object> derived = new LinkedHashMap<>();
        derived.put("ticks_per_year", secondsPerYear * TICKS_PER_SECOND);
        return derived;
    }
}

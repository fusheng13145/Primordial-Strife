package com.strife.tools.datagen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NUMBERS @@world -&gt; {@code data/strife/strife_worldgen/rules.json} — world/掉落侧的运行时数值表（MVP
 * 快速通道，与 {@link RealmRulesGenerator} 同一通道：NUMBERS.md → DataGen → jar，代码零受管字面量）。
 *
 * <p>{@code id} 是 {@code world_rules}：配置文件而非内容——V-TEXT 的 lang 前缀表查不到该域即不设限（JSON_SCHEMA §4.10），与
 * realm_rules 同一豁免口径。
 */
public final class WorldRulesGenerator implements NumbersGenerator {

    private static final List<String> BLOCKS = List.of("world");

    @Override
    public List<Product> generate(NumbersSource numbers) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("id", "world_rules");
        for (String block : BLOCKS) {
            fields.put(block, numbers.block(block));
        }
        return List.of(
                new Product(
                        "data/strife/strife_worldgen/rules.json",
                        fields,
                        numbers.generatedHeader()));
    }
}

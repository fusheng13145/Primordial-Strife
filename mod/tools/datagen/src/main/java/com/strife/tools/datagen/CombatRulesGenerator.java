package com.strife.tools.datagen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NUMBERS @@combat -&gt; {@code data/strife/strife_combat/rules.json} — 战斗侧运行时数值表（realm_coeff /
 * spell_cost_qi / spell_cooldown_sec / artifact_cooldown_sec，与 {@link RealmRulesGenerator} 同一通道：
 * NUMBERS.md → DataGen → jar，combat 代码零受管字面量）。
 *
 * <p>{@code id} 是 {@code combat_rules}：配置文件而非内容——V-TEXT 的 lang 前缀表对该域不设限（JSON_SCHEMA §4.10 豁免口径，与
 * realm_rules/world_rules 同款）。`[拟]` 标记随 NUMBERS 原样透传注释在生成阶段剥除。
 */
public final class CombatRulesGenerator implements NumbersGenerator {

    private static final List<String> BLOCKS = List.of("combat");

    @Override
    public List<Product> generate(NumbersSource numbers) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("id", "combat_rules");
        for (String block : BLOCKS) {
            fields.put(block, numbers.block(block));
        }
        return List.of(
                new Product(
                        "data/strife/strife_combat/rules.json", fields, numbers.generatedHeader()));
    }
}

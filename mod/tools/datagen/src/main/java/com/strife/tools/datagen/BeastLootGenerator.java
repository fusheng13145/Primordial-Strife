package com.strife.tools.datagen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NUMBERS @@world 的 {@code beast_loot_rolls} → {@code data/strife/loot_table/entities/monster.json}
 * —— 妖兽掉落表。
 *
 * <p>消费此前<b>孤立</b>的 {@code beast_loot_rolls}（它已进 {@code strife_worldgen/rules.json}，但全项目零代码引用：
 * 妖兽被击杀掉空气，战斗闭环断在最后一环）。本生成器让它成为构建期事实——与矿石掉落表同构的纪律： 表里有值、产物里就生效，不靠运行时祈祷。
 *
 * <p>掉落物用 {@code strife:item_yaocai}（lang 已是「妖兽材料 / Beast Material」），它是炼器/炼丹域材料的占位挂点 （{@code
 * tables/artifacts.csv} 定稿后，妖兽材料即冶炼链输入）。{@code @generated} 头由 {@link Product} 注入， MC 解析 loot table
 * 时忽略该顶层键（与矿石掉落表一致，已实证）。
 */
public final class BeastLootGenerator implements NumbersGenerator {

    private static final String ITEM_YAO_CAI = "strife:item_yaocai";

    /** 妖兽实体注册名对应的掉落表路径（原版按注册名推导为 {@code loot_tables/entities/monster}）。 */
    static final String RELATIVE_PATH = "data/strife/loot_table/entities/monster.json";

    @Override
    public List<Product> generate(NumbersSource numbers) {
        Object raw = numbers.value("world", "beast_loot_rolls");
        long rolls = ((Number) raw).longValue();
        if (rolls < 1) {
            throw new IllegalStateException(
                    "NUMBERS @@world beast_loot_rolls=" + rolls + " must be >= 1（妖兽不掉落 = 战斗闭环断链）");
        }

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("type", "minecraft:item");
        entry.put("name", ITEM_YAO_CAI);

        Map<String, Object> pool = new LinkedHashMap<>();
        pool.put("rolls", rolls);
        pool.put("entries", List.of(entry));

        Map<String, Object> table = new LinkedHashMap<>();
        table.put("type", "minecraft:entity");
        table.put("pools", List.of(pool));

        return List.of(new Product(RELATIVE_PATH, table, numbers.generatedHeader()));
    }
}

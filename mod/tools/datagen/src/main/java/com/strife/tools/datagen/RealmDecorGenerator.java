package com.strife.tools.datagen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code tables/realm_decor.csv} → 上界维度的装饰生成链两产物（content/JSON_SCHEMA.md §4.16，ADR-021）。
 *
 * <p>产物（每行装饰块）：
 *
 * <ol>
 *   <li>{@code data/strife/worldgen/configured_feature/<id>.json} —— 矿式生成：{@code replace_target} 用
 *       <b>block_match 精确匹配</b>（end_stone 不在原版 {@code stone_ore_replaceables} tag 里，tag_match
 *       会静默一个都不替换——1.21.1 实证的行为差异，不是走矿石那条 tag 通道）；
 *   <li>{@code data/strife/worldgen/placed_feature/<id>.json} —— 每区块串数 × 高度范围（trapezoid）。
 * </ol>
 *
 * <p><b>为什么没有 biome_modifier</b>：结晶只长在自研群系 {@code strife:upper_realm}（{@code biomes.csv}
 * 的产物）里，而自研群系的 {@code features[6]} 直接列 placed feature 引用（{@code BiomeGenerator} 消费 {@code features}
 * 列）——biome_modifier 是「往<b>别人的</b>群系里塞东西」才需要的间接层，自研群系用不上。
 *
 * <p>与 {@link OreGenerator} 同分工：本类只做转换，数值校验（veins/size &gt; 0、y 范围合法）在表侧与 Validator。
 */
public final class RealmDecorGenerator implements TableGenerator {

    private static final String CONTRACT = "JSON_SCHEMA §4.16";

    @Override
    public String tableFile() {
        return "realm_decor.csv";
    }

    @Override
    public List<Product> generate(TableSource source) {
        List<Product> products = new ArrayList<>();
        for (TableSource.Record row : source.rows()) {
            String id = row.id();
            Map<String, String> values = row.values();
            String block = required(source, row, values, "block");
            String replaceTarget = required(source, row, values, "replace_target");
            if (!block.startsWith("strife:")) {
                throw new IllegalStateException(
                        source.fileName()
                                + ":"
                                + row.line()
                                + ": block 必须是 strife: 方块（本表只生成自研装饰）："
                                + block);
            }
            if (!replaceTarget.startsWith("minecraft:")) {
                throw new IllegalStateException(
                        source.fileName()
                                + ":"
                                + row.line()
                                + ": replace_target 必须是 minecraft: 方块（替换的是地形骨架）："
                                + replaceTarget);
            }
            int veins = intField(source, row, values, "veins_per_chunk");
            int veinSize = intField(source, row, values, "vein_size");
            int yMin = intField(source, row, values, "y_min");
            int yMax = intField(source, row, values, "y_max");
            if (veins <= 0 || veinSize <= 0) {
                throw new IllegalStateException(
                        source.fileName() + ":" + row.line() + ": veins/size 必须 > 0");
            }
            if (yMax < yMin) {
                throw new IllegalStateException(
                        source.fileName() + ":" + row.line() + ": y_max < y_min");
            }

            products.add(configured(source, id, block, replaceTarget, veinSize));
            products.add(placed(source, id, veins, yMin, yMax));
        }
        return products;
    }

    /** 矿式 configured feature：block_match 精确匹配替换目标（end_stone 等非 tag 覆盖的地形方块）。 */
    private Product configured(
            TableSource source, String id, String block, String replaceTarget, int veinSize) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("Name", block);
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("predicate_type", "minecraft:block_match");
        target.put("block", replaceTarget);
        Map<String, Object> targetEntry = new LinkedHashMap<>();
        targetEntry.put("state", state);
        targetEntry.put("target", target);
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("size", veinSize);
        config.put("discard_chance_on_air_exposure", 0.0);
        config.put("targets", List.of(targetEntry));
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("type", "minecraft:ore");
        fields.put("config", config);
        return new Product(
                "data/strife/worldgen/configured_feature/" + id + ".json",
                fields,
                source.generatedHeader());
    }

    /** placed：每区块串数（count）→ in_square → 高度（trapezoid 全高分布）→ biome 门。 */
    private Product placed(TableSource source, String id, int veins, int yMin, int yMax) {
        Map<String, Object> count = new LinkedHashMap<>();
        count.put("type", "minecraft:count");
        count.put("count", veins);
        Map<String, Object> inSquare = new LinkedHashMap<>();
        inSquare.put("type", "minecraft:in_square");
        Map<String, Object> min = new LinkedHashMap<>();
        min.put("absolute", yMin);
        Map<String, Object> max = new LinkedHashMap<>();
        max.put("absolute", yMax);
        Map<String, Object> height = new LinkedHashMap<>();
        height.put("type", "minecraft:trapezoid");
        height.put("min_inclusive", min);
        height.put("max_inclusive", max);
        Map<String, Object> heightRange = new LinkedHashMap<>();
        heightRange.put("type", "minecraft:height_range");
        heightRange.put("height", height);
        Map<String, Object> biomeGate = new LinkedHashMap<>();
        biomeGate.put("type", "minecraft:biome");
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("feature", "strife:" + id);
        fields.put("placement", List.of(count, inSquare, heightRange, biomeGate));
        return new Product(
                "data/strife/worldgen/placed_feature/" + id + ".json",
                fields,
                source.generatedHeader());
    }

    private static String required(
            TableSource source, TableSource.Record row, Map<String, String> values, String column) {
        String value = values.get(column);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    source.fileName() + ":" + row.line() + ": 缺必填列 " + column);
        }
        return value;
    }

    private static int intField(
            TableSource source, TableSource.Record row, Map<String, String> values, String column) {
        String raw = required(source, row, values, column);
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    source.fileName() + ":" + row.line() + ": 列 " + column + " 不是整数：" + raw);
        }
    }
}

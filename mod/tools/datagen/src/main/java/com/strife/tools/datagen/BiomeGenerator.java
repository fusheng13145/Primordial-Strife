package com.strife.tools.datagen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code tables/biomes.csv} → 自研群系产物 {@code data/strife/worldgen/biome/<id>.json}
 * （content/JSON_SCHEMA.md §4.15）。
 *
 * <p><b>为什么自研群系是「世界设计」的第一杠杆</b>：群系的 {@code effects}（天空/雾/水/草色）决定一 个维度一眼看上去是什么世界——上界此前借 {@code
 * minecraft:the_end} 群系，观感就是暗紫色的末地 复制品；换成自研「灵霄青冥」（天青天空 + 淡金雾 + 青玉草色）之后，同一个浮岛地形立刻读作 「仙界」。颜色是材质属性（04
 * §7 管线调），进表不进 NUMBERS。
 *
 * <p>格式以原版 jar 内 {@code data/minecraft/worldgen/biome/the_end.json} 为基准（1.21.1 实证）： {@code
 * features} 是 11 步数组（0..10 对应原版 GenerationStep.Ordering），装饰的 {@code features} 列（{@code
 * ores=a;b;...} 形状）落在 index 6（UNDERGROUND_ORES）。
 *
 * <p>上界口径：无降水、无 mood 洞窟声（灵界清净）、spawners 全空（无怪——怪物随 EP 排期）。
 */
public final class BiomeGenerator implements TableGenerator {

    private static final String CONTRACT = "JSON_SCHEMA §4.15";

    /** 原版 features 数组的步数（GenerationStep.Ordering 全量，1.21.1 实证 the_end.json）。 */
    private static final int FEATURE_STEPS = 11;

    /** 装饰列表落进的步：index 6 = UNDERGROUND_ORES（矿/结晶同一步）。 */
    private static final int ORE_STEP = 6;

    private static final List<String> SPAWNER_GROUPS =
            List.of(
                    "monster",
                    "creature",
                    "ambient",
                    "axolotls",
                    "underground_water_creature",
                    "water_creature",
                    "water_ambient",
                    "misc");

    @Override
    public String tableFile() {
        return "biomes.csv";
    }

    @Override
    public List<Product> generate(TableSource source) {
        List<Product> products = new ArrayList<>();
        for (TableSource.Record row : source.rows()) {
            String id = row.id();
            Map<String, String> values = row.values();

            int sky = hexField(source, row, values, "sky_color");
            int fog = hexField(source, row, values, "fog_color");
            int water = hexField(source, row, values, "water_color");
            int waterFog = hexField(source, row, values, "water_fog_color");
            int grass = hexField(source, row, values, "grass_color");
            double temperature = doubleField(source, row, values, "temperature");
            double downfall = doubleField(source, row, values, "downfall");
            List<String> ores = featureList(source, row, values, "features", "ores");

            Map<String, Object> effects = new LinkedHashMap<>();
            effects.put("fog_color", fog);
            effects.put("sky_color", sky);
            effects.put("water_color", water);
            effects.put("water_fog_color", waterFog);
            effects.put("grass_color", grass);

            List<List<String>> features = new ArrayList<>();
            for (int step = 0; step < FEATURE_STEPS; step++) {
                features.add(step == ORE_STEP ? ores : List.of());
            }

            Map<String, Object> spawners = new LinkedHashMap<>();
            SPAWNER_GROUPS.forEach(group -> spawners.put(group, List.of()));

            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("carvers", Map.of());
            fields.put("downfall", downfall);
            fields.put("effects", effects);
            fields.put("features", features);
            fields.put("has_precipitation", false);
            fields.put("spawn_costs", Map.of());
            fields.put("spawners", spawners);
            fields.put("temperature", temperature);

            products.add(
                    new Product(
                            "data/strife/worldgen/biome/" + id + ".json",
                            fields,
                            source.generatedHeader()));
        }
        return products;
    }

    /**
     * {@code features} 列形状：{@code ores=a;b;}（k=v; 映射，值分号分隔；空白 = 无装饰）。 列名与类别名分开传：列名决定取哪个单元格，类别名决定
     * step 归属（当前只有 {@code ores}）。
     */
    private static List<String> featureList(
            TableSource source,
            TableSource.Record row,
            Map<String, String> values,
            String column,
            String category) {
        String raw = values.get(column);
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        if (!raw.startsWith(category + "=")) {
            throw new IllegalStateException(
                    source.fileName()
                            + ":"
                            + row.line()
                            + ": "
                            + column
                            + " 单元格形状应为 "
                            + category
                            + "=a;b;："
                            + raw);
        }
        String list = raw.substring((category + "=").length());
        List<String> parsed = new ArrayList<>();
        for (String item : list.split(";")) {
            if (!item.isBlank()) {
                if (!item.startsWith("strife:")) {
                    throw new IllegalStateException(
                            source.fileName()
                                    + ":"
                                    + row.line()
                                    + ": 装饰引用必须是 strife: 产物（自研群系只列自研 feature）："
                                    + item);
                }
                parsed.add(item);
            }
        }
        return parsed;
    }

    /** 群系颜色是 int（原版 JSON 为十进制），表里用 0x 十六进制便于人读。 */
    private static int hexField(
            TableSource source, TableSource.Record row, Map<String, String> values, String column) {
        String raw = values.get(column);
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException(
                    source.fileName() + ":" + row.line() + ": 缺必填列 " + column);
        }
        try {
            return Integer.decode(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    source.fileName()
                            + ":"
                            + row.line()
                            + ": 列 "
                            + column
                            + " 不是颜色（0x 十六进制）："
                            + raw);
        }
    }

    private static double doubleField(
            TableSource source, TableSource.Record row, Map<String, String> values, String column) {
        String raw = values.get(column);
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException(
                    source.fileName() + ":" + row.line() + ": 缺必填列 " + column);
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    source.fileName() + ":" + row.line() + ": 列 " + column + " 不是数字：" + raw);
        }
    }
}

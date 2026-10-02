package com.strife.tools.datagen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code tables/dimensions.csv} → M4 上界维度的两个产物（content/JSON_SCHEMA.md §4.14，ADR-021）。
 *
 * <p><b>为什么是数据包维度而不是 Java 注册</b>：1.21.1 的 {@code DimensionType} 与维度本体都是原版注册表的 <b>数据驱动</b>条目——放进
 * {@code data/&lt;ns&gt;/dimension_type/} 与 {@code data/&lt;ns&gt;/dimension/} 即随 mod
 * 分发、进存档即生效，不需要任何 Java 注册代码。这正好落在 ADR-021 的归属线上：维度注册（数据真相源）归 core 治理体系（本表 + 本生成器 + Validator），world
 * 模块只经 NUMBERS {@code @@world} 提供灵气参数并引用维度 ID——core 的 <b>Java 禁区零改动</b>，与矿石链同一「表 → DataGen → 原版
 * JSON」模式。
 *
 * <p>产物清单（每行维度）：
 *
 * <ol>
 *   <li>{@code data/strife/dimension_type/<id>.json} —— 维度物理属性。格式以原版 jar 内 {@code
 *       data/minecraft/dimension_type/the_end.json} 为基准（1.21.1 实证：{@code monster_spawn_light_level}
 *       是 uniform IntProvider、{@code effects} 决定天空渲染）；
 *   <li>{@code data/strife/dimension/<id>.json} —— 维度本体：type 引用上者，noise 生成器 + fixed 群系源。 {@code
 *       noise_settings} 引用的 {@code minecraft:end_islands} 等标准 preset 硬编码在原版 {@code
 *       NoiseRouterData}（jar 里无 JSON 属正常，引用注册表 key 即可）。
 * </ol>
 *
 * <p><b>表列与受管数值的边界</b>（05 §1 精神）：灵气参数（成长数值）进 NUMBERS {@code @@world} （{@code upper_qi_min/max}，随
 * {@code WorldRulesGenerator} 直出 rules.json）；本表承载的是<b>材质属性</b>
 * （坐标比例、环境光、借哪个原版噪声/群系）——它们不参与平衡流程，评审按「像不像上界」判断。物理开关 （ultrawarm 等）按「上界 =
 * 宜居灵界」固定在本类并逐项注释，不做表列（避免一张表出现两个真相源）。
 *
 * <p>本类只做转换，不做裁定：id 命名规范、scale &gt; 0、ambient_light ∈ [0,1]、借用的原版资产必须带 {@code minecraft:}
 * 命名空间（显式声明「借原版资产」，防止手滑引用不存在的 strife 资产）。
 */
public final class DimensionGenerator implements TableGenerator {

    private static final String CONTRACT = "JSON_SCHEMA §4.14";

    @Override
    public String tableFile() {
        return "dimensions.csv";
    }

    @Override
    public List<Product> generate(TableSource source) {
        List<Product> products = new ArrayList<>();
        for (TableSource.Record row : source.rows()) {
            String id = row.id();
            requireSnakeCase(source, row, id);

            Map<String, String> values = row.values();
            String generatorType = required(source, row, values, "generator_type");
            if (!generatorType.equals("minecraft:noise")) {
                throw new IllegalStateException(
                        source.fileName()
                                + ":"
                                + row.line()
                                + ": generator_type 必须是 minecraft:noise（本生成器只覆盖 noise 生成器；"
                                + "其他类型显式红掉，防止生成出解析不了的结构）："
                                + generatorType);
            }
            String noiseSettings = required(source, row, values, "noise_settings");
            String biome = required(source, row, values, "biome");
            requireVanillaNamespace(source, row, "noise_settings", noiseSettings);
            requireKnownNamespace(source, row, "biome", biome);
            double coordinateScale = doubleField(source, row, values, "coordinate_scale");
            if (coordinateScale <= 0) {
                throw new IllegalStateException(
                        source.fileName()
                                + ":"
                                + row.line()
                                + ": coordinate_scale 必须 > 0："
                                + coordinateScale);
            }
            double ambientLight = doubleField(source, row, values, "ambient_light");
            if (ambientLight < 0.0 || ambientLight > 1.0) {
                throw new IllegalStateException(
                        source.fileName()
                                + ":"
                                + row.line()
                                + ": ambient_light 必须落在 [0,1]："
                                + ambientLight);
            }

            products.add(dimensionType(source, id, coordinateScale, ambientLight));
            products.add(dimension(source, id, generatorType, noiseSettings, biome));
        }
        return products;
    }

    /**
     * 维度物理属性——基准是原版 jar 里 {@code data/minecraft/dimension_type/the_end.json}（1.21.1 实证），逐项注释 与
     * the_end 的差异及理由。上界是「宜居灵界」：不死循环燃烧、不超立方、可放重生锚（跨界锚点设定）、无床（不设 重生点，与 the_end 同口径）、无天空光（暗天观感由
     * ambient_light 0.5 补足可视度）。
     */
    private Product dimensionType(
            TableSource source, String id, double coordinateScale, double ambientLight) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("ultrawarm", false);
        fields.put("natural", false);
        fields.put("piglin_safe", false);
        fields.put("respawn_anchor_works", true);
        fields.put("bed_works", false);
        fields.put("has_raids", false);
        fields.put("has_skylight", false);
        fields.put("has_ceiling", false);
        fields.put("coordinate_scale", coordinateScale);
        fields.put("ambient_light", ambientLight);
        fields.put("fixed_time", 6000);
        // 必须 LinkedHashMap：Map.of 的迭代顺序由 JVM 启动时的随机 SALT 决定（ImmutableCollections），
        // 会让同一份输入两次生成得到不同键序的产物，破坏 04 §5「同输入同产物」。见 DataGenDeterminismTest。
        Map<String, Object> spawnLight = new LinkedHashMap<>();
        spawnLight.put("type", "minecraft:uniform");
        spawnLight.put("min_inclusive", 0);
        spawnLight.put("max_inclusive", 7);
        fields.put("monster_spawn_light_level", spawnLight);
        fields.put("monster_spawn_block_light_limit", 0);
        fields.put("min_y", 0);
        fields.put("height", 256);
        fields.put("logical_height", 256);
        fields.put("infiniburn", "#minecraft:infiniburn_end");
        fields.put("effects", "minecraft:the_end");
        return new Product(
                "data/strife/dimension_type/" + id + ".json", fields, source.generatedHeader());
    }

    /** 维度本体：noise 生成器 + fixed 群系源（M4 最小切片，正式多群系随美术管线迭代）。 */
    private Product dimension(
            TableSource source,
            String id,
            String generatorType,
            String noiseSettings,
            String biome) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("type", "strife:" + id);
        Map<String, Object> biomeSource = new LinkedHashMap<>();
        biomeSource.put("type", "minecraft:fixed");
        biomeSource.put("biome", biome);
        Map<String, Object> generator = new LinkedHashMap<>();
        generator.put("type", generatorType);
        generator.put("settings", noiseSettings);
        generator.put("biome_source", biomeSource);
        fields.put("generator", generator);
        return new Product(
                "data/strife/dimension/" + id + ".json", fields, source.generatedHeader());
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

    private static double doubleField(
            TableSource source, TableSource.Record row, Map<String, String> values, String column) {
        String raw = required(source, row, values, column);
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    source.fileName() + ":" + row.line() + ": 列 " + column + " 不是数字：" + raw);
        }
    }

    /** 借用的原版资产必须带 {@code minecraft:} 命名空间——显式声明「这是借的」，防手滑引用不存在的 strife 资产。 */
    private static void requireVanillaNamespace(
            TableSource source, TableSource.Record row, String column, String value) {
        if (!value.startsWith("minecraft:")) {
            throw new IllegalStateException(
                    source.fileName()
                            + ":"
                            + row.line()
                            + ": 列 "
                            + column
                            + " 必须带 minecraft: 命名空间（本表只允许借原版资产）："
                            + value);
        }
    }

    /** 内容 ID 规范（ADR-017 同款）：小写字母/数字/下划线。 */
    private static void requireSnakeCase(TableSource source, TableSource.Record row, String id) {
        if (!id.matches("[a-z0-9_]+")) {
            throw new IllegalStateException(
                    source.fileName() + ":" + row.line() + ": id 必须是小写 snake_case：" + id);
        }
    }

    /**
     * 群系列允许 {@code minecraft:}（借原版）或 {@code strife:}（自研群系，{@code tables/biomes.csv}
     * 的产物）；其余命名空间一律红掉——引用不存在的第三方群系时原版只会静默生成失败。
     */
    private static void requireKnownNamespace(
            TableSource source, TableSource.Record row, String column, String value) {
        if (!value.startsWith("minecraft:") && !value.startsWith("strife:")) {
            throw new IllegalStateException(
                    source.fileName()
                            + ":"
                            + row.line()
                            + ": 列 "
                            + column
                            + " 必须是 minecraft:（借原版）或 strife:（biomes.csv 自研群系）："
                            + value);
        }
    }
}

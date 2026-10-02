package com.strife.tools.datagen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code tables/structures.csv} → 宗门结构的两个产物（content/JSON_SCHEMA.md §4.13，ADR-022）。
 *
 * <p><b>两条产物缺一即不生成</b>（且不报错，与矿石五份产物同构的静默断链）：{@code structure} 定义结构， {@code structure_set}
 * 决定放置间距。字段逐字对齐原版 jar 内 1.21.1 实证的<b>代码生成式</b>结构 （{@code ruined_portal} / {@code
 * nether_fossil}）——code 式<b>没有</b> {@code size} 与 {@code start_pool}， 多写这两个 jigsaw 专属字段属于"凭记忆改契约"。
 *
 * <p><b>{@code kind} 本期只认 {@code code}</b>（ADR-022）：jigsaw 式要 {@code .nbt} 模板，而 WorldEdit schematic
 * → StructureBlock 导出链在本机未验证，宁可在这里红掉"写了 jigsaw 却没资产"，也不要生成一份 引用不存在 start_pool
 * 的产物（原版的表现是在世界生成时静默跳过）。
 *
 * <p><b>{@code spacing > separation}</b> 是原版的硬语义：separation ≥ spacing 时结构视作无效而不生成。 这条校验必须落在
 * DataGen（构建期红），因为运行期玩家只会看到"我跑了一万格没找到宗门"。
 */
public final class StructureGenerator implements TableGenerator {

    private static final String CONTRACT = "JSON_SCHEMA §4.13";

    private static final List<String> STEPS =
            List.of("surface_structures", "underground_structures", "underground_decoration");

    private static final List<String> TERRAIN_ADAPTATIONS =
            List.of("none", "beard_thin", "beard_box", "bury", "encapsulate");

    @Override
    public String tableFile() {
        return "structures.csv";
    }

    @Override
    public List<Product> generate(TableSource source) {
        List<Product> products = new ArrayList<>();
        for (TableSource.Record row : source.rows()) {
            String id = row.id();
            Map<String, String> values = row.values();

            String kind = required(source, row, values, "kind");
            if (!kind.equals("code")) {
                throw new IllegalStateException(
                        source.fileName()
                                + ":"
                                + row.line()
                                + ": kind 本期只实现 code（ADR-022）；jigsaw 需 .nbt 模板且导出链未验证："
                                + kind);
            }
            String step = required(source, row, values, "step");
            if (!STEPS.contains(step)) {
                throw new IllegalStateException(
                        source.fileName() + ":" + row.line() + ": step 必须是原版生成阶段之一：" + step);
            }
            String terrain = values.get("terrain_adaptation");
            if (terrain != null && !terrain.isBlank() && !TERRAIN_ADAPTATIONS.contains(terrain)) {
                throw new IllegalStateException(
                        source.fileName()
                                + ":"
                                + row.line()
                                + ": terrain_adaptation 未知（留空 = none）："
                                + terrain);
            }
            List<String> biomes = biomes(source, row, values);
            int spacing = intField(source, row, values, "spacing");
            int separation = intField(source, row, values, "separation");
            int salt = intField(source, row, values, "salt");
            if (separation >= spacing) {
                throw new IllegalStateException(
                        source.fileName()
                                + ":"
                                + row.line()
                                + ": separation("
                                + separation
                                + ") 必须小于 spacing("
                                + spacing
                                + ")，否则原版视作无效而不生成");
            }

            products.add(structure(source, id, biomes, step, terrain));
            products.add(structureSet(source, id, spacing, separation, salt));
        }
        return products;
    }

    /** 结构定义（code 式：无 size / start_pool，字段对齐原版 ruined_portal 实证）。 */
    private Product structure(
            TableSource source, String id, List<String> biomes, String step, String terrain) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("type", "strife:" + id);
        fields.put("biomes", biomes);
        fields.put("spawn_overrides", Map.of());
        fields.put("step", step);
        if (terrain != null && !terrain.isBlank()) {
            fields.put("terrain_adaptation", terrain);
        }
        return new Product(
                "data/strife/worldgen/structure/" + id + ".json", fields, source.generatedHeader());
    }

    /** 放置规则：random_spread 三参数 + 单元素 structures[] 带 weight。 */
    private Product structureSet(
            TableSource source, String id, int spacing, int separation, int salt) {
        Map<String, Object> placement = new LinkedHashMap<>();
        placement.put("type", "minecraft:random_spread");
        placement.put("salt", salt);
        placement.put("separation", separation);
        placement.put("spacing", spacing);
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("structure", "strife:" + id);
        entry.put("weight", 1);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("placement", placement);
        fields.put("structures", List.of(entry));
        return new Product(
                "data/strife/worldgen/structure_set/" + id + ".json",
                fields,
                source.generatedHeader());
    }

    /** {@code biomes} 列：{@code minecraft:x|minecraft:y} 多值，禁空项（与 ores.csv 同规则）。 */
    private static List<String> biomes(
            TableSource source, TableSource.Record row, Map<String, String> values) {
        String raw = required(source, row, values, "biomes");
        List<String> parsed = new ArrayList<>();
        for (String biome : raw.split("\\|")) {
            String trimmed = biome.trim();
            if (trimmed.isBlank()) {
                throw new IllegalStateException(
                        source.fileName() + ":" + row.line() + ": biomes 有空项：" + raw);
            }
            if (!trimmed.startsWith("minecraft:")) {
                throw new IllegalStateException(
                        source.fileName()
                                + ":"
                                + row.line()
                                + ": biomes 只允许原版群系（结构挂主世界，借原版群系）："
                                + trimmed);
            }
            parsed.add(trimmed);
        }
        return parsed;
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

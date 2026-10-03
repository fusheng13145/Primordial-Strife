package com.strife.tools.datagen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 地点（世界域）真相源 → 运行时产物（docs/04 §5、JSON_SCHEMA §4.17）。
 *
 * <p>一行 {@code tables/places.csv} → 一份 {@code
 * data/strife/strife_places/<id>.json}。地点是<b>双消费</b>真相源：
 *
 * <ul>
 *   <li><b>G-4 任务导航</b>——任务目标 {@code type=reach;target=qi_wenjiang} 的 target 即地点 ID。导航命令（world 侧的
 *       {@code /strife world place nav}）按本产物给出的坐标与维度算方向与距离；
 *   <li><b>灵气地点差异化</b>——{@code qi_scale} 是地点的环境系数（05 §2 公式第二项），玩家落在地点半径内时覆盖全局噪声场， 闭合 docs/10 §3.3 的
 *       {@code spirit_field.csv} 欠账（此前灵气场只用 NUMBERS 的全局上下限，全图一个梯度）。
 * </ul>
 *
 * <p>坐标与维度是<b>材质/设定</b>属性不是受管数值：不进 NUMBERS、不参与平衡流程（与 04 §10.6 的「方块属性不进 NUMBERS」同口径）。 {@code
 * qi_scale} 虽是平衡相关的系数，但它与<b>单个地点强绑定</b>（每个地点一个值），与矿石 density 同理——放进 NUMBERS 会让 「加一个地点」必须同时动两张真相源，且
 * NUMBERS 的 {@code @@world} 是全局块，放逐地点参数会稀释语义。若后续需要按境界/难度调灵气， 再升为受管数值并另开 ADR。
 *
 * <p>校验在构建期红掉，因为产物缺失/字段错的代价是「导航指向虚空、灵气场永不触发」且不报错。
 */
public final class PlaceGenerator implements TableGenerator {

    private static final String PRODUCT_PREFIX = "data/strife/strife_places/";

    @Override
    public String tableFile() {
        return "places.csv";
    }

    @Override
    public List<Product> generate(TableSource source) {
        requireSnakeCaseIds(source);
        return source.rows().stream().map(row -> product(source, row)).toList();
    }

    private static Product product(TableSource source, TableSource.Record row) {
        String id = row.id();
        String dimension = requireKnownNamespace(source, row, "dimension");
        String name = requirePresent(source, row, "name");
        int x = parseInt(source, row, "x");
        int y = parseInt(source, row, "y");
        int z = parseInt(source, row, "z");
        int radius = parseInt(source, row, "radius");
        if (radius <= 0) {
            throw new IllegalStateException(
                    source.fileName()
                            + ":"
                            + row.line()
                            + ": "
                            + id
                            + " radius 必须 > 0，得到 "
                            + radius);
        }
        double qiScale = parseDouble(source, row, "qi_scale");
        if (!(qiScale > 0.0)) {
            throw new IllegalStateException(
                    source.fileName()
                            + ":"
                            + row.line()
                            + ": "
                            + id
                            + " qi_scale 必须 > 0（05 §2：灵气系数为 0 须有可展示理由），得到 "
                            + qiScale);
        }
        if (qiScale > 10.0) {
            throw new IllegalStateException(
                    source.fileName()
                            + ":"
                            + row.line()
                            + ": "
                            + id
                            + " qi_scale 越界（>10，疑似误填），得到 "
                            + qiScale);
        }
        String chapter = row.values().get("chapter");

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("id", id);
        fields.put("dimension", dimension);
        fields.put("name", name);
        fields.put("x", x);
        fields.put("y", y);
        fields.put("z", z);
        fields.put("radius", radius);
        fields.put("qi_scale", qiScale);
        if (chapter != null && !chapter.isBlank()) {
            if (!chapter.matches("^[a-z0-9_]+$")) {
                throw new IllegalStateException(
                        source.fileName()
                                + ":"
                                + row.line()
                                + ": "
                                + id
                                + " chapter 必须是小写蛇形，得到 '"
                                + chapter
                                + "'");
            }
            fields.put("chapter", chapter);
        }
        return new Product(PRODUCT_PREFIX + id + ".json", fields, source.generatedHeader());
    }

    private static void requireSnakeCaseIds(TableSource source) {
        for (TableSource.Record row : source.rows()) {
            if (!row.id().matches("^[a-z][a-z0-9_]*$")) {
                throw new IllegalStateException(
                        source.fileName()
                                + ":"
                                + row.line()
                                + ": place id 必须是小写蛇形（^[a-z][a-z0-9_]*$），得到 '"
                                + row.id()
                                + "'（玩家可见名走 name 列与 lang）");
            }
        }
    }

    private static String requireKnownNamespace(
            TableSource source, TableSource.Record row, String column) {
        String value = row.values().get(column);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    source.fileName() + ":" + row.line() + ": " + column + " 必填");
        }
        if (!value.startsWith("minecraft:") && !value.startsWith("strife:")) {
            throw new IllegalStateException(
                    source.fileName()
                            + ":"
                            + row.line()
                            + ": "
                            + column
                            + " 必须带已知命名空间（minecraft:/strife:），得到 '"
                            + value
                            + "'（地点维度须随服务端注册表校验，自定义命名空间无法校验）");
        }
        return value;
    }

    private static String requirePresent(
            TableSource source, TableSource.Record row, String column) {
        String value = row.values().get(column);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    source.fileName() + ":" + row.line() + ": " + column + " 必填");
        }
        return value;
    }

    private static int parseInt(TableSource source, TableSource.Record row, String column) {
        String value = row.values().get(column);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    source.fileName()
                            + ":"
                            + row.line()
                            + ": "
                            + row.id()
                            + " "
                            + column
                            + " 必须整数，得到 '"
                            + value
                            + "'");
        }
    }

    private static double parseDouble(TableSource source, TableSource.Record row, String column) {
        String value = row.values().get(column);
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    source.fileName()
                            + ":"
                            + row.line()
                            + ": "
                            + row.id()
                            + " "
                            + column
                            + " 必须数值，得到 '"
                            + value
                            + "'");
        }
    }
}

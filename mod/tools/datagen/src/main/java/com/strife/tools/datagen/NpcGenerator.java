package com.strife.tools.datagen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code tables/npcs.csv} → {@code data/strife/strife_npcs/<id>.json}：世界 NPC 的绝对摆点位。
 *
 * <p>坐标是绝对的 {@code x/z}（不是相对地点的偏移）：DataGen 的生成器只拿到自己那张表，跨表取地点中心 需要改生成器框架，而 MVP 的摆放只有个位数行。{@code
 * place} 列因此是<b>校验锚</b>——Validator 用它 确认点位落在该地点半径内、维度一致，运行时不读它（quest 侧不横向依赖 world）。
 *
 * <p>高度不写表：世界地形由种子决定，手填的 y 会在另一台存档里悬空或埋进土里；运行时按高度图取安全地表。
 */
public final class NpcGenerator implements TableGenerator {

    private static final String PRODUCT_PREFIX = "data/strife/strife_npcs/";

    @Override
    public String tableFile() {
        return "npcs.csv";
    }

    @Override
    public List<Product> generate(TableSource source) {
        return source.rows().stream().map(row -> product(source, row)).toList();
    }

    private static Product product(TableSource source, TableSource.Record row) {
        String id = row.id();
        if (!id.matches("^npc_[a-z0-9_]+$")) {
            throw new IllegalStateException(
                    source.fileName()
                            + ":"
                            + row.line()
                            + ": NPC id 必须是 npc_ 前缀的小写蛇形，得到 '"
                            + id
                            + "'");
        }
        String dimension = requireKnownNamespace(source, row, "dimension");
        String place = requirePresent(source, row, "place");
        if (!place.matches("^[a-z][a-z0-9_]*$")) {
            throw new IllegalStateException(
                    source.fileName()
                            + ":"
                            + row.line()
                            + ": place 必须是地点 ID（小写蛇形），得到 '"
                            + place
                            + "'");
        }
        int x = parseInt(source, row, "x");
        int z = parseInt(source, row, "z");

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("id", id);
        fields.put("dimension", dimension);
        fields.put("place", place);
        fields.put("x", x);
        fields.put("z", z);
        return new Product(PRODUCT_PREFIX + id + ".json", fields, source.generatedHeader());
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
                            + "'（自定义命名空间无法随服务端注册表校验）");
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
}

package com.strife.tools.datagen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code tables/auction.csv} → {@code strife_auction/auction.json}（EP1，ADR-037 ①：荷兰式降价拍）。
 *
 * <p>门禁（构建期红）：min < start、drop ≥1 且 (start−min) 必须能被 drop 整除到不越界（容差内）； item_id 唯一且限
 * item_/pill_。当前价由游戏时间确定性推导（零持久化状态，ADR-037 ①）。
 */
public final class AuctionGenerator implements TableGenerator {

    private final NumbersSource numbers;

    public AuctionGenerator(NumbersSource numbers) {
        this.numbers = numbers;
    }

    @Override
    public String tableFile() {
        return "auction.csv";
    }

    @Override
    public List<Product> generate(TableSource source) {
        List<Map<String, Object>> lots = new ArrayList<>();
        for (TableSource.Record row : source.rows()) {
            Map<String, String> values = row.values();
            String id = required(source, row, values, "id");
            if (!id.startsWith("auction_")) {
                throw new IllegalStateException(
                        source.fileName() + ":" + row.line() + ": id 必须用 auction_ 前缀：" + id);
            }
            String itemId = required(source, row, values, "item_id");
            if (!itemId.startsWith("item_") && !itemId.startsWith("pill_")) {
                throw new IllegalStateException(
                        source.fileName()
                                + ":"
                                + row.line()
                                + ": item_id 只允许 item_/pill_："
                                + itemId);
            }
            int start = intField(source, row, values, "start_lingshi");
            int min = intField(source, row, values, "min_lingshi");
            int drop = intField(source, row, values, "drop_lingshi");
            if (min >= start) {
                throw new IllegalStateException(
                        source.fileName()
                                + ":"
                                + row.line()
                                + ": min("
                                + min
                                + ") 必须 < start("
                                + start
                                + ")");
            }
            if (drop < 1) {
                throw new IllegalStateException(
                        source.fileName() + ":" + row.line() + ": drop 必须 ≥1");
            }
            Map<String, Object> lot = new LinkedHashMap<>();
            lot.put("id", id);
            lot.put("item_id", itemId);
            lot.put("start_lingshi", start);
            lot.put("min_lingshi", min);
            lot.put("drop_lingshi", drop);
            lots.add(lot);
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("id", "auction");
        fields.put("slot_shichen", numbers.block("auction").get("slot_shichen"));
        fields.put("lots", lots);
        return List.of(
                new Product(
                        "data/strife/strife_auction/auction.json",
                        fields,
                        source.generatedHeader()));
    }

    private static String required(
            TableSource source, TableSource.Record row, Map<String, String> values, String key) {
        String v = values.get(key);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException(
                    source.fileName() + ":" + row.line() + ": 缺列 '" + key + "'");
        }
        return v;
    }

    private static int intField(
            TableSource source, TableSource.Record row, Map<String, String> values, String key) {
        String v = required(source, row, values, key);
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    source.fileName() + ":" + row.line() + ": '" + key + "' 必须是整数：" + v);
        }
    }
}

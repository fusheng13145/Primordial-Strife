package com.strife.tools.datagen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code tables/blackmarket.csv} → {@code strife_blackmarket/market.json}（EP1，ADR-036 ②）。
 *
 * <p>门禁（构建期红）：价格 ≥1、item_id 唯一且限 {@code item_|pill_}、货币不入表——与 prices.csv
 * 同款纪律。"赃货折扣"（同物低于明市买入价）的跨表对账由 Validator 后续批补， 本期靠表注与经济语义约束。
 */
public final class BlackMarketGenerator implements TableGenerator {

    @Override
    public String tableFile() {
        return "blackmarket.csv";
    }

    @Override
    public List<Product> generate(TableSource source) {
        List<Map<String, Object>> entries = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (TableSource.Record row : source.rows()) {
            Map<String, String> values = row.values();
            String id = required(source, row, values, "id");
            if (!id.startsWith("bm_")) {
                throw new IllegalStateException(
                        source.fileName() + ":" + row.line() + ": id 必须用 bm_ 前缀：" + id);
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
            if (!seen.add(itemId)) {
                throw new IllegalStateException(
                        source.fileName() + ":" + row.line() + ": item_id 重复：" + itemId);
            }
            int price = intField(source, row, values, "price_lingshi");
            if (price < 1) {
                throw new IllegalStateException(
                        source.fileName() + ":" + row.line() + ": 黑市价必须 ≥1（零价=白送破坏经济）：");
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", id);
            entry.put("item_id", itemId);
            entry.put("price_lingshi", price);
            entries.add(entry);
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("id", "blackmarket");
        fields.put("entries", entries);
        return List.of(
                new Product(
                        "data/strife/strife_blackmarket/market.json",
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

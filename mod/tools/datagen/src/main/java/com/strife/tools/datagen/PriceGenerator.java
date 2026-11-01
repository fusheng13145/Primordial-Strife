package com.strife.tools.datagen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code tables/prices.csv} → {@code strife_prices/prices.json}（EP1 经济原语，ADR-032：H1 价格的
 * 原材料/物品侧定价表，灵石计价）。
 *
 * <p><b>价格门禁在生成器</b>（与 structures.csv 的 {@code separation >= spacing} 同构——构建期红， 不进 Validator
 * 主循环）：{@code sell < buy}（回收必折价，防"买卖循环刷灵石"的复制漏洞）、 双价非负、item_id 唯一且限 {@code item_|pill_}
 * 前缀。灵石本身（货币）不进定价表。
 *
 * <p>NUMBERS {@code @@market} 的三档兑换比随产物下发（构造器捕获 NumbersSource，与 {@code SpellGenerator(numbers)}
 * 同款）。
 */
public final class PriceGenerator implements TableGenerator {

    private final NumbersSource numbers;

    public PriceGenerator(NumbersSource numbers) {
        this.numbers = numbers;
    }

    @Override
    public String tableFile() {
        return "prices.csv";
    }

    @Override
    public List<Product> generate(TableSource source) {
        Map<String, Object> market = numbers.block("market");
        List<Map<String, Object>> entries = new ArrayList<>();
        Set<String> seenItems = new HashSet<>();
        for (TableSource.Record row : source.rows()) {
            Map<String, String> values = row.values();
            String id = required(source, row, values, "id");
            if (!id.startsWith("price_")) {
                throw new IllegalStateException(
                        source.fileName() + ":" + row.line() + ": id 必须用 price_ 前缀：" + id);
            }
            String itemId = required(source, row, values, "item_id");
            if (!itemId.startsWith("item_") && !itemId.startsWith("pill_")) {
                throw new IllegalStateException(
                        source.fileName()
                                + ":"
                                + row.line()
                                + ": item_id 只允许 item_/pill_ 内容 ID（货币不入定价表）："
                                + itemId);
            }
            if (itemId.startsWith("item_lingshi")) {
                throw new IllegalStateException(
                        source.fileName()
                                + ":"
                                + row.line()
                                + ": 货币不入定价表（灵石以自身计价，三档走兑换）："
                                + itemId);
            }
            if (!seenItems.add(itemId)) {
                throw new IllegalStateException(
                        source.fileName() + ":" + row.line() + ": item_id 重复：" + itemId);
            }
            int buy = intField(source, row, values, "buy_lingshi");
            int sell = intField(source, row, values, "sell_lingshi");
            if (buy < 0 || sell < 0) {
                throw new IllegalStateException(
                        source.fileName() + ":" + row.line() + ": 价格必须非负（负价=刷灵石）");
            }
            if (sell >= buy) {
                throw new IllegalStateException(
                        source.fileName()
                                + ":"
                                + row.line()
                                + ": sell_lingshi("
                                + sell
                                + ") 必须 < buy_lingshi("
                                + buy
                                + ")——回收必折价，否则买卖循环即复制漏洞");
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", id);
            entry.put("item_id", itemId);
            entry.put("buy_lingshi", buy);
            entry.put("sell_lingshi", sell);
            entries.add(entry);
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("id", "prices");
        fields.put("ratios", market);
        fields.put("entries", entries);
        return List.of(
                new Product(
                        "data/strife/strife_prices/prices.json", fields, source.generatedHeader()));
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

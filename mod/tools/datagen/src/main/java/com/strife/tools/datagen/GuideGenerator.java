package com.strife.tools.datagen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code tables/guide.csv} → {@code strife_guide/guide.json}（P1-21 自研入门指引物，D12/ADR-026 ④）。
 *
 * <p>文本直接进产物（与 {@code dialog_text} 同通道——固定少量指引文本不入 lang，避免"生成与手种混进 一个不可审计文件"）；LORE §3 语域 ≤40
 * 汉字在生成器把关（构建期红）。分五域（D12：境界/修炼/战斗/ 生产/任务），命令通道与未来客户端面板同读这一份产物——永不分叉。
 */
public final class GuideGenerator implements TableGenerator {

    /** D12 五域固定集合；表里出现其它 section 直接红（防条目漂进没人读的角落）。 */
    private static final List<String> SECTIONS =
            List.of("realm", "cultivation", "combat", "production", "quest");

    @Override
    public String tableFile() {
        return "guide.csv";
    }

    @Override
    public List<Product> generate(TableSource source) {
        Map<String, List<Map<String, Object>>> sections = new LinkedHashMap<>();
        for (String section : SECTIONS) {
            sections.put(section, new ArrayList<>());
        }
        for (TableSource.Record row : source.rows()) {
            Map<String, String> values = row.values();
            String id = required(source, row, values, "id");
            if (!id.startsWith("guide_")) {
                throw new IllegalStateException(
                        source.fileName() + ":" + row.line() + ": id 必须用 guide_ 前缀：" + id);
            }
            String section = required(source, row, values, "section");
            if (!sections.containsKey(section)) {
                throw new IllegalStateException(
                        source.fileName()
                                + ":"
                                + row.line()
                                + ": section 必须是五域之一 "
                                + SECTIONS
                                + "："
                                + section);
            }
            String text = required(source, row, values, "text");
            int hanzi = text.replaceAll("[^\\u4e00-\\u9fff]", "").length();
            if (hanzi > 40) {
                throw new IllegalStateException(
                        source.fileName()
                                + ":"
                                + row.line()
                                + ": 正文超 40 汉字（LORE §3 语域，实 "
                                + hanzi
                                + "）："
                                + text);
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", id);
            entry.put("title", required(source, row, values, "title"));
            entry.put("text", text);
            sections.get(section).add(entry);
        }
        for (Map.Entry<String, List<Map<String, Object>>> e : sections.entrySet()) {
            if (e.getValue().size() < 3) {
                throw new IllegalStateException(
                        source.fileName()
                                + ": 五域各至少 3 条（P1-21 验收），'"
                                + e.getKey()
                                + "' 只有 "
                                + e.getValue().size()
                                + " 条");
            }
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("id", "guide");
        fields.put("sections", sections);
        return List.of(
                new Product(
                        "data/strife/strife_guide/guide.json", fields, source.generatedHeader()));
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
}

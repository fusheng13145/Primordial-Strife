package com.strife.quest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 入门指引库（P1-21，D12/ADR-026 ④）：classpath 直读 DataGen 产物 {@code strife_guide/guide.json}（{@code
 * tables/guide.csv} 直出，五域 ×≥3 条）。
 *
 * <p>为什么在 quest：指引是任务引导语料的近亲（对话文本同通道先例），且命令入口挂在 {@code /strife} 树下与 {@code /strife quest}
 * 并列。客户端面板（未来增强）读同一份产物—— 命令与面板同源，永不分叉（D12 ④）。
 */
public final class GuideLibrary {

    /** 一条指引：标题 + 正文（成品中文，≤40 汉字，生成器把关）。 */
    public record Entry(String id, String title, String text) {}

    private static volatile Map<String, List<Entry>> sections;

    private GuideLibrary() {}

    /** 五域名 → 条目（固定顺序 = D12：realm/cultivation/combat/production/quest）。 */
    public static Map<String, List<Entry>> sections() {
        Map<String, List<Entry>> cached = sections;
        if (cached != null) {
            return cached;
        }
        synchronized (GuideLibrary.class) {
            if (sections == null) {
                sections = parse();
            }
            return sections;
        }
    }

    /** 测试与 /reload 后强制重读。 */
    static void invalidate() {
        sections = null;
    }

    private static Map<String, List<Entry>> parse() {
        try (InputStream stream =
                Objects.requireNonNull(
                        GuideLibrary.class.getResourceAsStream(
                                "/data/strife/strife_guide/guide.json"),
                        "strife_guide/guide.json not in jar — GuideGenerator 未跑？")) {
            JsonObject product =
                    JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                            .getAsJsonObject();
            if (product.get("@generated") == null) {
                throw new IllegalStateException(
                        "strife_guide/guide.json missing @generated header");
            }
            JsonObject jsonSections = product.getAsJsonObject("sections");
            Map<String, List<Entry>> out = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> e : jsonSections.entrySet()) {
                java.util.List<Entry> entries = new java.util.ArrayList<>();
                for (JsonElement el : e.getValue().getAsJsonArray()) {
                    JsonObject o = el.getAsJsonObject();
                    entries.add(
                            new Entry(
                                    o.get("id").getAsString(),
                                    o.get("title").getAsString(),
                                    o.get("text").getAsString()));
                }
                out.put(e.getKey(), List.copyOf(entries));
            }
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("cannot read strife_guide/guide.json", e);
        }
    }
}

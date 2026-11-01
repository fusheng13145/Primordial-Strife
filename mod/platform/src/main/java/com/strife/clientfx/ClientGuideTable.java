package com.strife.clientfx;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * 客户端指引表（P1-21，D12）：镜像 {@code SpellClientTable} 的读取通道——jar 内 {@code strife_guide/guide.json}
 * 直读、缺字段/空域 fail-fast（坏产物宁可红也不要 白屏）。与命令通道（服务端 {@code GuideLibrary}）读同一份产物，D12 ④ 不分叉。
 */
public final class ClientGuideTable {

    /** 一条指引的客户端形态：域 + 标题 + 正文。 */
    public record Entry(
            String section, String sectionTitle, String id, String title, String text) {}

    private static volatile List<Entry> entries;

    private ClientGuideTable() {}

    /** 五域显示名（顺序即 D12 口径）。 */
    /** 五域显示名（LinkedHashMap 保持 D12 顺序——面板翻页与命令通道同序）。 */
    public static final Map<String, String> SECTION_TITLES;

    static {
        java.util.Map<String, String> titles = new java.util.LinkedHashMap<>();
        titles.put("realm", "境界");
        titles.put("cultivation", "修炼");
        titles.put("combat", "战斗");
        titles.put("production", "生产");
        titles.put("quest", "任务");
        SECTION_TITLES = java.util.Collections.unmodifiableMap(titles);
    }

    public static List<Entry> load() {
        List<Entry> cached = entries;
        if (cached != null) {
            return cached;
        }
        synchronized (ClientGuideTable.class) {
            if (entries == null) {
                entries = load(net.minecraft.client.Minecraft.getInstance().getResourceManager());
            }
            return entries;
        }
    }

    /** 测试与资源重载后清缓存。 */
    static void invalidate() {
        entries = null;
    }

    static List<Entry> load(ResourceManager resources) {
        List<Entry> out = new ArrayList<>();
        var productOptional =
                resources.getResource(
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                "strife", "strife_guide/guide.json"));
        if (productOptional.isEmpty()) {
            throw new IllegalStateException(
                    "strife_guide/guide.json not in jar — GuideGenerator 未跑？");
        }
        JsonObject product;
        try (var reader =
                new InputStreamReader(productOptional.get().open(), StandardCharsets.UTF_8)) {
            product = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            throw new IllegalStateException("cannot read strife_guide/guide.json", e);
        }
        JsonObject sections = product.getAsJsonObject("sections");
        for (String section : SECTION_TITLES.keySet()) {
            JsonElement element = sections.get(section);
            if (element == null || !element.isJsonArray() || element.getAsJsonArray().isEmpty()) {
                throw new IllegalStateException("strife_guide/guide.json 缺域或空域：" + section);
            }
            for (JsonElement e : element.getAsJsonArray()) {
                JsonObject o = e.getAsJsonObject();
                out.add(
                        new Entry(
                                section,
                                SECTION_TITLES.get(section),
                                o.get("id").getAsString(),
                                o.get("title").getAsString(),
                                o.get("text").getAsString()));
            }
        }
        return List.copyOf(out);
    }

    /** 供面板用的副本（按域分组，保持 D12 顺序）。 */
    static Map<String, List<Entry>> bySection(List<Entry> all) {
        Map<String, List<Entry>> out = new LinkedHashMap<>();
        for (String section : SECTION_TITLES.keySet()) {
            List<Entry> group = new ArrayList<>();
            for (Entry e : all) {
                if (e.section().equals(section)) {
                    group.add(e);
                }
            }
            out.put(section, List.copyOf(group));
        }
        return out;
    }
}

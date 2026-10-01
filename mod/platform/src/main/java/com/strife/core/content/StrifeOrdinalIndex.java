package com.strife.core.content;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * "序号 → 内容 ID"的只读索引：把 DataGen 产物里的 {@code ordinal} 字段读成一张表。
 *
 * <p>存在的理由是一条纪律：链序（境界从低到高）的真相只允许存在于 NUMBERS.md，任何 Java 侧的副本都会在加境界时静默错位。 客户端也需要它（{@code
 * StrifeClientMirror} 里同步的是 ordinal，不是 ID），而 client_fx 按 03 §2 只能依赖 core—— 所以索引放在
 * core，且<b>按域泛化</b>：core 不认识"境界"是什么，只知道"某个域里的产物带 ordinal 字段"。
 *
 * <p>客户端与服务端读的是同一份 jar 内产物，因此两侧对同一个 ordinal 的解释必然一致（这正是不把 ID 塞进同步包的底气： 同步包只传序号，语义由内容层复原）。
 */
public final class StrifeOrdinalIndex {

    private static final Map<String, StrifeOrdinalIndex> CACHE = new HashMap<>();

    private final Map<Integer, String> idsByOrdinal;
    private final String domain;

    private StrifeOrdinalIndex(String domain, Map<Integer, String> idsByOrdinal) {
        this.domain = domain;
        this.idsByOrdinal = Map.copyOf(idsByOrdinal);
    }

    /** 读某个域（如 {@code strife_realms}）的全部产物，按 {@code ordinal} 建索引。 */
    public static StrifeOrdinalIndex of(ResourceManager resources, String domain) {
        synchronized (CACHE) {
            StrifeOrdinalIndex cached = CACHE.get(domain);
            if (cached != null) {
                return cached;
            }
        }
        Map<Integer, String> ids = new HashMap<>();
        Map<ResourceLocation, Resource> files =
                resources.listResources(domain, path -> path.getPath().endsWith(".json"));
        files.forEach(
                (location, resource) -> {
                    JsonObject object = read(resource);
                    // 配置单例（如 rules.json）没有 ordinal，跳过而不是报错。
                    if (!object.has("ordinal") || !object.has("id")) {
                        return;
                    }
                    ids.put(object.get("ordinal").getAsInt(), object.get("id").getAsString());
                });
        StrifeOrdinalIndex index = new StrifeOrdinalIndex(domain, ids);
        synchronized (CACHE) {
            CACHE.put(domain, index);
        }
        return index;
    }

    /** 序号对应的内容 ID；越界返回 null（调用方负责降级显示，不得编造名字）。 */
    public String idAt(int ordinal) {
        return idsByOrdinal.get(ordinal);
    }

    public int size() {
        return idsByOrdinal.size();
    }

    public String domain() {
        return domain;
    }

    /** {@code /reload} 与资源包切换后丢弃缓存。 */
    public static void invalidate() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }

    private static JsonObject read(Resource resource) {
        try (InputStream stream = resource.open();
                InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "cannot read content json " + resource.sourcePackId(), e);
        }
    }
}

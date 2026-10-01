package com.strife.combat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.strife.realm.FiveElements;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * combat 侧内容表：从服务端 datapack 读 DataGen 产物 {@code strife_techniques/*.json}（tables/techniques.csv →
 * DataGen，字段契约见 JSON_SCHEMA §4.2）。
 *
 * <p>功法倍率（{@code qi_rate_ratio}）与门禁字段都只在这里读，玩家档里存的是"学了哪些、装备哪部"。
 */
public final class TechniqueTables {

    private static final Logger LOGGER = LoggerFactory.getLogger("strife/combat");

    /**
     * 一部功法（产物字段的子集 + 判定所需的位掩码形态）。
     *
     * @param elementMask 主属性位掩码（{@link FiveElements}），0 = 无属性
     * @param requiredRealm 最低境界内容 ID（判定时经 {@code RealmTables.realmById} 换成序号）
     * @param requiredStage 小境界下限
     * @param requiredRootMask 所需灵根位掩码；0 = 无灵根要求
     * @param declaredAffinity 内容表声明的亲和（仅当玩家灵根未生成时兜底）
     * @param qiRateRatio 功法倍率（05 §2 公式第四项）
     * @param faction 归属势力内容 ID；空串 = 无门无派
     */
    public record Technique(
            String id,
            String grade,
            int elementMask,
            String requiredRealm,
            int requiredStage,
            int requiredRootMask,
            FiveElements.Affinity declaredAffinity,
            double qiRateRatio,
            String faction) {}

    private static volatile TechniqueTables instance;

    private static volatile String loggedFailure;

    private final Map<String, Technique> techniques;

    private TechniqueTables(ResourceManager resources) {
        Map<String, Technique> parsed = new LinkedHashMap<>();
        Map<ResourceLocation, Resource> files =
                resources.listResources(
                        "strife_techniques", path -> path.getPath().endsWith(".json"));
        if (files.isEmpty()) {
            throw new IllegalStateException(
                    "strife_techniques 域为空——TechniqueGenerator 未跑或产物未进 jar");
        }
        files.values()
                .forEach(
                        resource -> {
                            Technique technique = parse(read(resource));
                            parsed.put(technique.id(), technique);
                        });
        this.techniques = Map.copyOf(parsed);
    }

    public static TechniqueTables get(MinecraftServer server) {
        TechniqueTables tables = instance;
        if (tables == null) {
            synchronized (TechniqueTables.class) {
                tables = instance;
                if (tables == null) {
                    instance = tables = new TechniqueTables(server.getResourceManager());
                }
            }
        }
        return tables;
    }

    /** 学法/装备路径的容错形态：表不可用时返回 null（功法失效）并只记一次日志。 */
    public static TechniqueTables getOrNull(MinecraftServer server) {
        try {
            return get(server);
        } catch (RuntimeException e) {
            String message = String.valueOf(e.getMessage());
            if (!message.equals(loggedFailure)) {
                loggedFailure = message;
                LOGGER.error("combat tables unavailable（相同失败不再重复打印）: {}", message, e);
            }
            return null;
        }
    }

    public static void invalidate() {
        instance = null;
        loggedFailure = null;
    }

    public Technique technique(String id) {
        return techniques.get(id);
    }

    public Map<String, Technique> techniques() {
        return techniques;
    }

    /** 解析单部功法产物。包内可见以便用例直接喂真实产物——realm/world/production 的教训是：解析器与产物的漂移只在运行时发作。 */
    static Technique parse(JsonObject product) {
        String id = require(product, "id").getAsString();
        int elementMask = FiveElements.bitOf(stringOrEmpty(product, "element"));
        return new Technique(
                id,
                stringOrEmpty(product, "grade"),
                elementMask,
                stringOrEmpty(product, "required_realm"),
                product.has("required_stage") ? product.get("required_stage").getAsInt() : 1,
                maskOf(product.getAsJsonArray("required_spiritroot")),
                FiveElements.parseDeclared(stringOrEmpty(product, "affinity_rule")),
                require(product, "qi_rate_ratio").getAsDouble(),
                stringOrEmpty(product, "faction"));
    }

    private static int maskOf(com.google.gson.JsonArray elements) {
        if (elements == null) {
            return FiveElements.NONE;
        }
        int mask = FiveElements.NONE;
        for (JsonElement element : elements) {
            mask |= FiveElements.bitOf(element.getAsString());
        }
        return mask;
    }

    private static String stringOrEmpty(JsonObject product, String key) {
        return product.has(key) && !product.get(key).isJsonNull()
                ? product.get(key).getAsString()
                : "";
    }

    private static JsonElement require(JsonObject product, String key) {
        if (!product.has(key)) {
            throw new IllegalStateException(
                    "功法产物缺键 '" + key + "'（tables/techniques.csv 的列被改了？JSON_SCHEMA §4.2）");
        }
        return product.get(key);
    }

    private static JsonObject read(Resource resource) {
        try (InputStream stream = resource.open();
                InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "cannot read technique json " + resource.sourcePackId(), e);
        }
    }
}

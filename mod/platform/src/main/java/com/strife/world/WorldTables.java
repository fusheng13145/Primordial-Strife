package com.strife.world;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * world 侧运行时数值表：从服务端 datapack 读 DataGen 产物 {@code strife_worldgen/rules.json}（NUMBERS @@world
 * 直出），受管数值零字面量（AGENTS.md）——与 realm 的 {@code RealmTables} 同一通道与懒加载模式。
 *
 * <p>MVP 简化：首次访问时懒加载并缓存，{@code /reload} 不刷新（重进服务端即生效），与 RealmTables 同口径。
 */
public final class WorldTables {

    private static final Logger LOGGER = LoggerFactory.getLogger("strife/world");

    /** 草类方块的草药掉落概率：键 = 物品内容 ID 全名（strife:item_x），值 = 0–1 独立掷骰概率。 */
    private final Map<String, Double> herbGrassDropProb = new LinkedHashMap<>();

    private final Ambient ambient;

    /**
     * 灵气场参数（NUMBERS @@world 的 ambient_* 四项）。
     *
     * @param min 环境系数下限（&gt; 0，05 §2）
     * @param max 环境系数上限
     * @param regionChunks 粗粒度：多少区块一个区域值（03 §6）
     * @param refineWeight 细化层权重 [0,1]
     */
    public record Ambient(double min, double max, int regionChunks, double refineWeight) {}

    private static volatile WorldTables instance;

    private WorldTables(ResourceManager resources) {
        Resource resource =
                resources
                        .getResource(
                                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                        "strife", "strife_worldgen/rules.json"))
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "strife_worldgen/rules.json not in jar —"
                                                        + " WorldRulesGenerator 未跑？"));
        JsonObject rules = read(resource);
        JsonObject world = rules.getAsJsonObject("world");
        if (world == null) {
            throw new IllegalStateException("strife_worldgen/rules.json missing 'world' block");
        }
        JsonObject herbs = world.getAsJsonObject("herb_grass_drop_prob");
        if (herbs != null) {
            herbGrassDropProb.putAll(parseHerbs(herbs));
        }
        this.ambient = parseAmbient(world);
    }

    /**
     * 解析 NUMBERS @@world 的 ambient_* 四项。包内可见以便用例直接喂<b>真实产物</b>（world 与 realm 同一教训：
     * 解析器读错块/缺键的缺陷只会在运行时发作，而运行时路径在开服冒烟里未必被走到）。
     */
    static Ambient parseAmbient(JsonObject world) {
        return new Ambient(
                require(world, "ambient_qi_min").getAsDouble(),
                require(world, "ambient_qi_max").getAsDouble(),
                require(world, "ambient_region_chunks").getAsInt(),
                require(world, "ambient_refine_weight").getAsDouble());
    }

    /** 解析草药掉落概率表，并逐项校验落在 [0,1]（概率越界是内容错误，必须在加载期红掉）。 */
    static Map<String, Double> parseHerbs(JsonObject herbs) {
        Map<String, Double> parsed = new LinkedHashMap<>();
        herbs.entrySet()
                .forEach(
                        entry -> {
                            double prob = entry.getValue().getAsDouble();
                            if (prob < 0.0 || prob > 1.0) {
                                throw new IllegalStateException(
                                        "herb_grass_drop_prob['"
                                                + entry.getKey()
                                                + "'] = "
                                                + prob
                                                + " outside [0,1]");
                            }
                            parsed.put(entry.getKey(), prob);
                        });
        return parsed;
    }

    /** 取键；缺键时报出块名与键名（02 §5：内容加载失败必须报具体路径），而不是让后续 get 抛裸 NPE。 */
    private static JsonElement require(JsonObject block, String key) {
        if (!block.has(key)) {
            throw new IllegalStateException(
                    "strife_worldgen/rules.json 的 world 块缺键 '" + key + "'（NUMBERS @@world 改了键名？）");
        }
        return block.get(key);
    }

    /** 掉落路径的容错形态：表加载失败时返回空表（无掉落），并记日志。 */
    public static WorldTables getOrNull(MinecraftServer server) {
        try {
            return get(server);
        } catch (RuntimeException e) {
            LOGGER.error("world tables unavailable: {}", e.getMessage());
            return null;
        }
    }

    /** 懒加载入口；首次访问读服务端 datapack 资源。 */
    public static WorldTables get(MinecraftServer server) {
        WorldTables tables = instance;
        if (tables == null) {
            synchronized (WorldTables.class) {
                tables = instance;
                if (tables == null) {
                    instance = tables = new WorldTables(server.getResourceManager());
                }
            }
        }
        return tables;
    }

    /** 测试与 /reload 后强制重读。 */
    static WorldTables loadForTest(ResourceManager resources) {
        WorldTables tables = new WorldTables(resources);
        instance = tables;
        return tables;
    }

    public static void invalidate() {
        instance = null;
    }

    public Map<String, Double> herbGrassDropProb() {
        return Map.copyOf(herbGrassDropProb);
    }

    /** 灵气场参数（NUMBERS @@world）。 */
    public Ambient ambient() {
        return ambient;
    }

    private static JsonObject read(Resource resource) {
        try (InputStream stream = resource.open();
                InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonObject object = JsonParser.parseReader(reader).getAsJsonObject();
            if (object.get("@generated") == null) {
                throw new IllegalStateException(
                        "strife_worldgen/rules.json missing @generated header");
            }
            return object;
        } catch (Exception e) {
            throw new IllegalStateException("cannot read strife_worldgen/rules.json", e);
        }
    }
}

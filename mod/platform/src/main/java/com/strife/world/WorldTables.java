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

    private final Map<String, Double> secretRealmHerbDropProb = new LinkedHashMap<>();

    private final Ambient ambient;

    /** 上界维度（strife:upper_realm，ADR-021）的灵气参数：浓度区间独立，region/refine 沿用人界。 */
    private final Ambient upperAmbient;

    private final Ambient mijiAmbient;
    private final FireCrystal fireCrystal;

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
        JsonObject secretHerbs = world.getAsJsonObject("secret_realm_herb_drop_prob");
        if (secretHerbs != null) {
            this.secretRealmHerbDropProb.putAll(parseHerbs(secretHerbs));
        }
        this.ambient = parseAmbient(world);
        this.upperAmbient = parseUpperAmbient(world, this.ambient);
        this.mijiAmbient = parseMijiAmbient(world, this.upperAmbient);
        this.fireCrystal = parseFireCrystal(world);
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

    /**
     * 解析 NUMBERS @@world 的 {@code upper_qi_min/max}（上界维度，ADR-021）。region/refine 是「场的实现粒度」而非
     * 浓度语义，沿用人界值；浓度区间独立且校验 min ≤ max（写反是内容错误，加载期红掉）。
     *
     * <p><b>为什么 require 严格解析而不缺省回退</b>：DataGen 新鲜度门禁保证 rules.json 与 NUMBERS 同源（表加了键、
     * 产物必然跟上），此处宽松反而会把「漏跑 DataGen」的漂移吞成静默退化——回退语义留给调用方显式写，不在解析层偷。
     */
    static Ambient parseUpperAmbient(JsonObject world, Ambient overworld) {
        double min = require(world, "upper_qi_min").getAsDouble();
        double max = require(world, "upper_qi_max").getAsDouble();
        if (min > max) {
            throw new IllegalStateException(
                    "upper_qi_min(" + min + ") > upper_qi_max(" + max + ")——浓度区间写反");
        }
        return new Ambient(min, max, overworld.regionChunks(), overworld.refineWeight());
    }

    /**
     * 解析 NUMBERS @@world 的 {@code miji_qi_min/max}（秘境维度，H4 最小实装 [拟]）。浓度独立于上界（默认更浓—— 幽冥暗域"灵气浓郁而危险"，与
     * beast_miji 的妖兽密度配套构成"去秘境修炼"的循环）；region/refine 沿用上界值。
     */
    static Ambient parseMijiAmbient(JsonObject world, Ambient upper) {
        if (!world.has("miji_qi_min")) {
            return upper; // H4 值未定稿前退化为上界浓度——键是 [拟]，不因缺键红整个表
        }
        double min = require(world, "miji_qi_min").getAsDouble();
        double max = require(world, "miji_qi_max").getAsDouble();
        if (min > max) {
            throw new IllegalStateException(
                    "miji_qi_min(" + min + ") > miji_qi_max(" + max + ")——浓度区间写反");
        }
        return new Ambient(min, max, upper.regionChunks(), upper.refineWeight());
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

    /**
     * 秘境灵草掉落表（NUMBERS @@world secret_realm_herb_drop_prob，[拟]）：仅 miji_realm 的草丛生效—— mat_* 表里 {@code
     * source: secret_realm} 的落地口；天元果（epic）在此才有野外获取路径，智慧试炼靠它闭环。
     */
    public Map<String, Double> secretRealmHerbDropProb() {
        return Map.copyOf(secretRealmHerbDropProb);
    }

    /** 灵气场参数（NUMBERS @@world）。 */
    public Ambient ambient() {
        return ambient;
    }

    /** 上界维度灵气参数（{@code upper_qi_min/max}，ADR-021；region/refine 沿用人界）。 */
    public Ambient upperAmbient() {
        return upperAmbient;
    }

    /** 秘境维度灵气参数（{@code miji_qi_min/max}，H4 [拟]；键未定稿时=上界）。 */
    public Ambient mijiAmbient() {
        return mijiAmbient;
    }

    /** 上界灵晶引导异火的规则（{@code fires_crystal_*}，[拟]）。 */
    public FireCrystal fireCrystal() {
        return fireCrystal;
    }

    /** 灵晶引导规则：冷却秒 + 归一化权重表（none=灵气涣散；权重可为小数，逐项须 >0）。 */
    public record FireCrystal(long cooldownSec, Map<String, Double> weights) {
        /** 归一化抽取（纯函数）：total 为零/负时恒回 none（表配错不崩引导，退化为无事发生）。 */
        public String roll(java.util.function.DoubleSupplier rng) {
            double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
            if (total <= 0) {
                return "none";
            }
            double pick = rng.getAsDouble() * total;
            for (Map.Entry<String, Double> e : weights.entrySet()) {
                pick -= e.getValue();
                if (pick < 0) {
                    return e.getKey();
                }
            }
            return "none";
        }
    }

    static FireCrystal parseFireCrystal(JsonObject world) {
        if (!world.has("fires_crystal_cooldown_sec")) {
            return new FireCrystal(60L, defaultFireCrystalWeights()); // [拟] 键未定稿的显式回退
        }
        long cooldown = require(world, "fires_crystal_cooldown_sec").getAsLong();
        JsonObject weightsJson = world.getAsJsonObject("fires_crystal_weights");
        if (weightsJson == null || weightsJson.isEmpty()) {
            throw new IllegalStateException("fires_crystal_weights 缺失或为空——引导无从抽取");
        }
        Map<String, Double> weights = new LinkedHashMap<>();
        weightsJson
                .entrySet()
                .forEach(
                        e -> {
                            double w = e.getValue().getAsDouble();
                            if (w <= 0) {
                                throw new IllegalStateException(
                                        "fires_crystal_weights.'"
                                                + e.getKey()
                                                + "' = "
                                                + w
                                                + "——权重必须 >0");
                            }
                            weights.put(e.getKey(), w);
                        });
        return new FireCrystal(cooldown, Map.copyOf(weights));
    }

    private static Map<String, Double> defaultFireCrystalWeights() {
        return Map.of(
                "none",
                40.0,
                "fire_mu",
                24.0,
                "fire_dixin",
                18.0,
                "fire_xuanbing",
                12.0,
                "fire_chiyan",
                4.0,
                "fire_jiuyou",
                1.5,
                "fire_taiyang",
                0.5);
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

package com.strife.realm;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.strife.core.StrifeTime;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * realm 运行时数值表：从服务端 datapack 读 DataGen 产物（strife_realms 域 + 任务簿）， 受管数值零字面量（AGENTS.md）——NUMBERS.md →
 * DataGen → jar → 这里。
 *
 * <p>MVP 简化：首次访问时懒加载并缓存，{@code /reload} 不刷新（重进服务端即生效）。 正式的重载监听与热更属于 M1 收尾（A1-3 旁路的表读取走同一张表）。
 */
public final class RealmTables {

    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger("strife/realm");

    /** 单境界数据（strife_realms/<id>.json，§4.1 全字段）。 */
    public record RealmEntry(
            String id,
            int ordinal,
            int qiMax,
            int stageCount,
            int lifespanYears,
            double sitRate,
            String breakthroughKey) {}

    /** 单键突破成功率（NUMBERS §3）。 */
    public record BreakthroughRate(double base, double failStep, double floor) {}

    /** NUMBERS §2/§3/§4/§5/§6 里 realm 运行时需要的散值（rules.json，MVP 快速通道）。 */
    public record Rules(
            Map<String, BreakthroughRate> rates,
            double qiResetRatioMin,
            double qiResetRatioMax,
            double dashengResetYearsRatio,
            double interruptProgressKeep,
            int meditationTickIntervalTicks,
            long interruptCooldownTicks,
            double meditationInterruptMoveSqr,
            double qualityTier1,
            double qualityTier2,
            double qualityTier3,
            double qualityTier4,
            int rollWeightTier1,
            int rollWeightTier2,
            int rollWeightTier3,
            int rollWeightTier4) {}

    private static volatile RealmTables instance;

    private final Map<Integer, RealmEntry> realmsByOrdinal = new HashMap<>();
    private final Map<String, RealmEntry> realmsById = new HashMap<>();
    private final Rules rules;

    private RealmTables(ResourceManager resources) {
        Map<String, JsonObject> raw = new HashMap<>();
        Map<ResourceLocation, Resource> files =
                resources.listResources("strife_realms", path -> path.getPath().endsWith(".json"));
        files.forEach(
                (location, resource) -> {
                    JsonObject object = read(resource);
                    raw.put(object.get("id").getAsString(), object);
                });
        Rules parsedRules = parseRules(raw.remove("realm_rules"));
        this.rules = parsedRules;
        raw.forEach(
                (id, object) -> {
                    RealmEntry entry =
                            new RealmEntry(
                                    id,
                                    object.get("ordinal").getAsInt(),
                                    object.get("qi_max").getAsInt(),
                                    object.get("stage_count").getAsInt(),
                                    object.get("lifespan_years").getAsInt(),
                                    object.get("sit_rate").getAsDouble(),
                                    object.get("breakthrough_success_key").getAsString());
                    realmsByOrdinal.put(entry.ordinal(), entry);
                    realmsById.put(id, entry);
                });
        if (realmsByOrdinal.isEmpty()) {
            throw new IllegalStateException("strife_realms 域为空——DataGen 产物未进 jar？");
        }
    }

    /** tick 路径的容错形态：表加载失败时返回 null（tick 跳过），登录路径仍走 get() 显式报错。 */
    public static RealmTables getOrNull(MinecraftServer server) {
        try {
            return get(server);
        } catch (RuntimeException e) {
            LOGGER.error("realm tables unavailable: {}", e.getMessage());
            return null;
        }
    }

    /** 懒加载入口；首次访问读服务端 datapack 资源。 */
    public static RealmTables get(MinecraftServer server) {
        RealmTables tables = instance;
        if (tables == null) {
            synchronized (RealmTables.class) {
                tables = instance;
                if (tables == null) {
                    instance = tables = new RealmTables(server.getResourceManager());
                }
            }
        }
        return tables;
    }

    /** 测试与 /reload 后强制重读。 */
    static RealmTables loadForTest(ResourceManager resources) {
        RealmTables tables = new RealmTables(resources);
        instance = tables;
        return tables;
    }

    public static void invalidate() {
        instance = null;
    }

    public RealmEntry realm(int ordinal) {
        RealmEntry entry = realmsByOrdinal.get(ordinal);
        if (entry == null) {
            throw new IllegalStateException("no realm at ordinal " + ordinal);
        }
        return entry;
    }

    public int realmCount() {
        return realmsByOrdinal.size();
    }

    public RealmEntry realmById(String id) {
        return realmsById.get(id);
    }

    public BreakthroughRate rate(String breakthroughKey) {
        BreakthroughRate rate = rules.rates().get(breakthroughKey);
        if (rate == null) {
            throw new IllegalStateException(
                    "breakthrough key '" + breakthroughKey + "' not in rules.json");
        }
        return rate;
    }

    public Rules rules() {
        return rules;
    }

    /** 灵根品阶系数（1..4）；0/未知按中性 1.0（面板必须可解释，05 §2）。 */
    public double qualityCoefficient(int quality) {
        return switch (quality) {
            case 1 -> rules.qualityTier1();
            case 2 -> rules.qualityTier2();
            case 3 -> rules.qualityTier3();
            case 4 -> rules.qualityTier4();
            default -> 1.0;
        };
    }

    private static Rules parseRules(JsonObject rules) {
        if (rules == null) {
            throw new IllegalStateException(
                    "strife_realms/rules.json missing — RealmRulesGenerator 未跑？");
        }
        Map<String, BreakthroughRate> rates = new HashMap<>();
        rules.getAsJsonObject("breakthrough")
                .entrySet()
                .forEach(
                        entry -> {
                            JsonObject value = entry.getValue().getAsJsonObject();
                            rates.put(
                                    entry.getKey(),
                                    new BreakthroughRate(
                                            value.get("base").getAsDouble(),
                                            value.get("fail_step").getAsDouble(),
                                            value.get("floor").getAsDouble()));
                        });
        JsonObject cost = rules.getAsJsonObject("breakthrough_cost");
        JsonObject meditation = rules.getAsJsonObject("meditation");
        JsonObject spiritroot = rules.getAsJsonObject("spiritroot");
        JsonObject weights = spiritroot.getAsJsonObject("roll_weights");
        return new Rules(
                rates,
                cost.get("qi_reset_ratio_min").getAsDouble(),
                cost.get("qi_reset_ratio_max").getAsDouble(),
                cost.get("dasheng_reset_years_ratio").getAsDouble(),
                meditation.get("interrupt_progress_keep").getAsDouble(),
                meditation.get("tick_interval_ticks").getAsInt(),
                // NUMBERS 的冷却以秒计，结算在刻上：换算集中在这里，调用方不再各乘一次 20。
                StrifeTime.secondsToTicks(meditation.get("interrupt_cooldown_sec").getAsDouble()),
                meditation.get("interrupt_move_sqr").getAsDouble(),
                spiritroot.get("quality_tier_1").getAsDouble(),
                spiritroot.get("quality_tier_2").getAsDouble(),
                spiritroot.get("quality_tier_3").getAsDouble(),
                spiritroot.get("quality_tier_4").getAsDouble(),
                weights.get("tier_1").getAsInt(),
                weights.get("tier_2").getAsInt(),
                weights.get("tier_3").getAsInt(),
                weights.get("tier_4").getAsInt());
    }

    private static JsonObject read(Resource resource) {
        try (InputStream stream = resource.open();
                InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "cannot read realm datapack json " + resource.sourcePackId(), e);
        }
    }

    /** JsonElement 入口供测试用（不走 ResourceManager）。 */
    static BreakthroughRate rateFromJson(JsonElement value) {
        JsonObject object = value.getAsJsonObject();
        return new BreakthroughRate(
                object.get("base").getAsDouble(),
                object.get("fail_step").getAsDouble(),
                object.get("floor").getAsDouble());
    }
}

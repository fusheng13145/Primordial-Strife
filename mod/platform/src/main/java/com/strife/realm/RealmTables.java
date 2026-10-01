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
            String breakthroughKey,
            boolean tribulation) {}

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
            int rollWeightTier4,
            int dashengRealmDropStages,
            String dashengDebuffKey,
            double debuffAllStatDelta,
            long debuffDurationTicks,
            double affinityMatched,
            double affinityNeutral,
            double affinityConflict) {}

    private static volatile RealmTables instance;

    /** 上一次已打印过的失败信息（防 ERROR 刷屏）。 */
    private static volatile String loggedFailure;

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
                                    object.get("breakthrough_success_key").getAsString(),
                                    // tribulation 由 RealmsGenerator 从 unlocks 推导（含 tribulation
                                    // 键即为天劫境）：
                                    // 突破进入该境要渡劫，失败吃重伤。
                                    object.has("tribulation")
                                            && object.get("tribulation").getAsBoolean());
                    realmsByOrdinal.put(entry.ordinal(), entry);
                    realmsById.put(id, entry);
                });
        if (realmsByOrdinal.isEmpty()) {
            throw new IllegalStateException("strife_realms 域为空——DataGen 产物未进 jar？");
        }
    }

    /**
     * tick 路径的容错形态：表加载失败时返回 null（tick 跳过），登录路径与启动自检仍走 {@link #get} 显式报错。
     *
     * <p>失败只报一次：调用点在每玩家每刻，失败重试会把 ERROR 刷成每 tick 一条，把真正有用的那条埋掉。相同的失败信息在 {@link #invalidate()}
     * 之前不再重复打印。
     */
    public static RealmTables getOrNull(MinecraftServer server) {
        try {
            return get(server);
        } catch (RuntimeException e) {
            String message = String.valueOf(e.getMessage());
            if (!message.equals(loggedFailure)) {
                loggedFailure = message;
                LOGGER.error("realm tables unavailable（相同失败不再重复打印）: {}", message, e);
            }
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
        loggedFailure = null;
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

    /**
     * 功法属性 × 玩家灵根的亲和系数（NUMBERS §6 三档），判定规则在 {@link FiveElements}。
     *
     * @param techniqueElement 功法主属性位掩码；{@link FiveElements#NONE} = 无属性
     * @param rootMask 玩家五行位掩码
     * @param declaredFallback 内容表声明的亲和（仅当灵根未生成时兜底）
     */
    public double affinityCoefficient(
            int techniqueElement, int rootMask, FiveElements.Affinity declaredFallback) {
        return FiveElements.coefficient(
                FiveElements.affinity(techniqueElement, rootMask, declaredFallback),
                rules.affinityMatched(),
                rules.affinityNeutral(),
                rules.affinityConflict());
    }

    /**
     * 解析 {@code strife_realms/rules.json}。
     *
     * <p>包内可见以便用例直接喂<b>真实产物</b>（{@code content-base} 的 resources 在测试 classpath 上）：这套解析器
     * 此前从未被任何用例或冒烟跑到过，结果是一处"读错块"的缺陷潜伏到第一个玩家登录才发作——而发作形态是 NPE 被容错分支 吞掉、整个 realm 系统静默停工。
     */
    static Rules parseRules(JsonObject rules) {
        if (rules == null) {
            throw new IllegalStateException(
                    "strife_realms/rules.json missing — RealmRulesGenerator 未跑？");
        }
        Map<String, BreakthroughRate> rates = new HashMap<>();
        JsonObject breakthrough = block(rules, "breakthrough");
        breakthrough
                .entrySet()
                .forEach(
                        entry -> {
                            JsonObject value = entry.getValue().getAsJsonObject();
                            rates.put(
                                    entry.getKey(),
                                    new BreakthroughRate(
                                            number(value, "base", "breakthrough." + entry.getKey()),
                                            number(
                                                    value,
                                                    "fail_step",
                                                    "breakthrough." + entry.getKey()),
                                            number(
                                                    value,
                                                    "floor",
                                                    "breakthrough." + entry.getKey())));
                        });
        JsonObject cost = block(rules, "breakthrough_cost");
        JsonObject meditation = block(rules, "meditation");
        JsonObject spiritroot = block(rules, "spiritroot");
        // 寿元与大限在 NUMBERS 的 @@lifespan 块里，与突破代价分属两块：混读就是上面说的那处缺陷。
        JsonObject lifespan = block(rules, "lifespan");
        JsonObject weights = spiritroot.getAsJsonObject("roll_weights");
        return new Rules(
                rates,
                number(cost, "qi_reset_ratio_min", "breakthrough_cost"),
                number(cost, "qi_reset_ratio_max", "breakthrough_cost"),
                number(lifespan, "dasheng_reset_years_ratio", "lifespan"),
                number(meditation, "interrupt_progress_keep", "meditation"),
                (int) number(meditation, "tick_interval_ticks", "meditation"),
                // NUMBERS 的冷却以秒计，结算在刻上：换算集中在这里，调用方不再各乘一次 20。
                StrifeTime.secondsToTicks(
                        number(meditation, "interrupt_cooldown_sec", "meditation")),
                number(meditation, "interrupt_move_sqr", "meditation"),
                number(spiritroot, "quality_tier_1", "spiritroot"),
                number(spiritroot, "quality_tier_2", "spiritroot"),
                number(spiritroot, "quality_tier_3", "spiritroot"),
                number(spiritroot, "quality_tier_4", "spiritroot"),
                (int) number(weights, "tier_1", "spiritroot.roll_weights"),
                (int) number(weights, "tier_2", "spiritroot.roll_weights"),
                (int) number(weights, "tier_3", "spiritroot.roll_weights"),
                (int) number(weights, "tier_4", "spiritroot.roll_weights"),
                (int) number(lifespan, "dasheng_realm_drop_stages", "lifespan"),
                string(lifespan, "dasheng_debuff_key", "lifespan"),
                number(cost, "debuff_all_stat_delta", "breakthrough_cost"),
                StrifeTime.secondsToTicks(number(cost, "debuff_duration_sec", "breakthrough_cost")),
                // 五行亲和三档（NUMBERS §6）：功法倍率的第二个因子，判定见 FiveElements。
                number(spiritroot, "affinity_matched", "spiritroot"),
                number(spiritroot, "affinity_neutral", "spiritroot"),
                number(spiritroot, "affinity_conflict", "spiritroot"));
    }

    /** 取字符串键（内容 ID 类，如 debuff 键名）；缺键时同样报出"块.键"。 */
    private static String string(JsonObject block, String key, String blockName) {
        if (!block.has(key)) {
            throw new IllegalStateException(
                    "strife_realms/rules.json 的 "
                            + blockName
                            + " 块缺键 '"
                            + key
                            + "'（NUMBERS 改了键名？）");
        }
        return block.get(key).getAsString();
    }

    /** 取块；缺块时按 {@code 02 §5"内容加载失败必须 fail-fast 并报出具体路径"} 报出块名，而不是让后续 {@code get} 抛裸 NPE。 */
    private static JsonObject block(JsonObject rules, String name) {
        JsonObject value = rules.getAsJsonObject(name);
        if (value == null) {
            throw new IllegalStateException(
                    "strife_realms/rules.json 缺 @@"
                            + name
                            + " 块——NUMBERS 块名与 RealmRulesGenerator.BLOCKS 不一致（JSON_SCHEMA §4.12）");
        }
        return value;
    }

    /** 取数值键；缺键时报出"块.键"，让缺陷一眼可定位。 */
    private static double number(JsonObject block, String key, String blockName) {
        if (!block.has(key)) {
            throw new IllegalStateException(
                    "strife_realms/rules.json 的 "
                            + blockName
                            + " 块缺键 '"
                            + key
                            + "'（NUMBERS 改了键名？）");
        }
        return block.get(key).getAsDouble();
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

package com.strife.core.net;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * core 运行时数值表：从服务端 datapack 读 DataGen 产物 {@code data/strife/strife_core/rules.json}（JSON_SCHEMA
 * §4.12）。
 *
 * <p>这张表的存在本身就是一条纪律：03 §5/§6 的发包上限与 03 §4 的意图限速都是受管数值，网络层不许把它们写成字面量 （05 §1）。同步预算与限速表都在这里读一次，运行期只查表。
 *
 * <p>与 {@code RealmTables} 同一取舍：首次访问懒加载并缓存；表缺失时 tick 路径降级为"不同步"而不是崩服，登录路径显式报错。
 */
public final class StrifeCoreRules {

    /** 单位换算，不是游戏数值：限速表里唯一以"每分钟"计的是突破。 */
    private static final double SECONDS_PER_MINUTE = 60.0;

    /** S2C 同步预算（NUMBERS {@code @@limits}）。 */
    public record SyncBudget(
            double packetsPerSecondIdle, int maxPayloadBytes, long resyncIntervalNanos) {}

    private static volatile StrifeCoreRules instance;

    private final SyncBudget syncBudget;
    private final IntentRateLimiter intents;
    private final int intentArgsMaxBytes;
    private final long ticksPerYear;

    private StrifeCoreRules(ResourceManager resources) {
        JsonObject rules = loadRules(resources);
        JsonObject limits = require(rules, "limits");
        JsonObject rateLimits = require(rules, "rate_limits");
        JsonObject derived = require(rules, "derived");
        this.syncBudget =
                new SyncBudget(
                        limits.get("sync_packets_per_sec_idle").getAsDouble(),
                        limits.get("sync_payload_max_bytes").getAsInt(),
                        limits.get("sync_full_resync_sec").getAsLong() * 1_000_000_000L);
        this.intentArgsMaxBytes = limits.get("intent_args_max_bytes").getAsInt();
        this.intents = new IntentRateLimiter(intentRates(rateLimits));
        this.ticksPerYear = derived.get("ticks_per_year").getAsLong();
    }

    /** 意图限速表：NUMBERS 的键名 → 意图 id，频率统一折算成"每秒"。 */
    private static Map<String, Double> intentRates(JsonObject rateLimits) {
        Map<String, Double> rates = new LinkedHashMap<>();
        rates.put(IntentRateLimiter.CAST, rateLimits.get("cast_per_sec").getAsDouble());
        rates.put(IntentRateLimiter.SIT, rateLimits.get("sit_per_sec").getAsDouble());
        rates.put(IntentRateLimiter.QUEST, rateLimits.get("quest_per_sec").getAsDouble());
        rates.put(IntentRateLimiter.ARTIFACT, rateLimits.get("artifact_per_sec").getAsDouble());
        rates.put(
                IntentRateLimiter.BREAKTHROUGH,
                rateLimits.get("breakthrough_per_min").getAsDouble() / SECONDS_PER_MINUTE);
        return rates;
    }

    public static StrifeCoreRules get(MinecraftServer server) {
        StrifeCoreRules rules = instance;
        if (rules == null) {
            synchronized (StrifeCoreRules.class) {
                rules = instance;
                if (rules == null) {
                    instance = rules = new StrifeCoreRules(server.getResourceManager());
                }
            }
        }
        return rules;
    }

    /**
     * 不缓存的读取，给客户端用：客户端的资源管理器在启动早期可能还没就绪，缓存一份失败的解析会把整个会话锁死。 调用方读到自己要的标量后自行缓存（见 {@code
     * ClientDataBridge}）。
     */
    public static StrifeCoreRules of(ResourceManager resources) {
        return new StrifeCoreRules(resources);
    }

    /** tick 路径的容错形态：表读不出来就返回 null（本次不同步），登录路径仍走 {@link #get} 显式报错。 */
    public static StrifeCoreRules getOrNull(MinecraftServer server) {
        try {
            return get(server);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** {@code /reload} 或测试用：丢弃缓存，下次访问重读。 */
    public static void invalidate() {
        instance = null;
    }

    public SyncBudget syncBudget() {
        return syncBudget;
    }

    public IntentRateLimiter intents() {
        return intents;
    }

    public int intentArgsMaxBytes() {
        return intentArgsMaxBytes;
    }

    /** 1 修行年 = 多少游戏刻（NUMBERS §4 {@code seconds_per_year} × 20，生成期算好，两个模块共用一份）。 */
    public long ticksPerYear() {
        return ticksPerYear;
    }

    /** 寿元刻数 → 年（向下取整，与面板显示口径一致）。 */
    public long yearsOf(long lifespanTicks) {
        return lifespanTicks / ticksPerYear;
    }

    private static JsonObject loadRules(ResourceManager resources) {
        Map<ResourceLocation, Resource> files =
                resources.listResources("strife_core", path -> path.getPath().endsWith(".json"));
        if (files.isEmpty()) {
            throw new IllegalStateException("strife_core 域为空——CoreRulesGenerator 未跑或产物未进 jar");
        }
        for (Resource resource : files.values()) {
            try (InputStream stream = resource.open();
                    InputStreamReader reader =
                            new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            } catch (Exception e) {
                throw new IllegalStateException(
                        "cannot read core rules json " + resource.sourcePackId(), e);
            }
        }
        throw new IllegalStateException("strife_core 域没有可读文件");
    }

    private static JsonObject require(JsonObject rules, String key) {
        JsonObject block = rules.getAsJsonObject(key);
        if (block == null) {
            throw new IllegalStateException(
                    "strife_core/rules.json 缺 @@"
                            + key
                            + " 块——CoreRulesGenerator 的 BLOCKS 与消费者不一致");
        }
        return block;
    }
}

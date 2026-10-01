package com.strife.production;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.strife.core.StrifeTime;
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
 * production 侧内容表：从服务端 datapack 读 DataGen 产物 {@code strife_pills/*.json}（tables/pills.csv →
 * DataGen，字段契约见 JSON_SCHEMA §4.4）。
 *
 * <p>丹药的增益数值<b>只在这里读</b>，不写进玩家档：档里存的是"何时到期、嗑了几次"，加成每次从产物查。改一次丹药数值，
 * 老档立刻按新数值生效；反过来若把加成存进档，就多出一份永远与真相源漂移的第二真相。
 */
public final class ProductionTables {

    private static final Logger LOGGER = LoggerFactory.getLogger("strife/production");

    /** 丹药效果：目前只有两类（增益型与延寿型），按产物里出现的键区分。 */
    public sealed interface Effect {
        /** 修炼速率增益：{@code qi_rate_bonus} + {@code duration_sec} + 重复递减。 */
        record QiRate(double bonus, long durationTicks, double repeatStep, double repeatFloor)
                implements Effect {}

        /** 寿元延长：{@code lifespan_years_gain}，按境界封顶 {@code max_gain_per_realm}。 */
        record Lifespan(int yearsGain, int maxGainPerRealm) implements Effect {}
    }

    public record Pill(String id, Effect effect) {}

    private static volatile ProductionTables instance;

    private final Map<String, Pill> pills;

    private ProductionTables(ResourceManager resources) {
        Map<String, Pill> parsed = new LinkedHashMap<>();
        Map<ResourceLocation, Resource> files =
                resources.listResources("strife_pills", path -> path.getPath().endsWith(".json"));
        if (files.isEmpty()) {
            throw new IllegalStateException("strife_pills 域为空——PillGenerator 未跑或产物未进 jar");
        }
        files.values()
                .forEach(
                        resource -> {
                            Pill pill = parsePill(read(resource));
                            parsed.put(pill.id(), pill);
                        });
        this.pills = Map.copyOf(parsed);
    }

    public static ProductionTables get(MinecraftServer server) {
        ProductionTables tables = instance;
        if (tables == null) {
            synchronized (ProductionTables.class) {
                tables = instance;
                if (tables == null) {
                    instance = tables = new ProductionTables(server.getResourceManager());
                }
            }
        }
        return tables;
    }

    /** 服用/增益路径的容错形态：表不可用时返回 null（丹药失效）并只记一次日志。 */
    public static ProductionTables getOrNull(MinecraftServer server) {
        try {
            return get(server);
        } catch (RuntimeException e) {
            String message = String.valueOf(e.getMessage());
            if (!message.equals(loggedFailure)) {
                loggedFailure = message;
                LOGGER.error("production tables unavailable（相同失败不再重复打印）: {}", message, e);
            }
            return null;
        }
    }

    private static volatile String loggedFailure;

    public static void invalidate() {
        instance = null;
        loggedFailure = null;
    }

    public Pill pill(String id) {
        return pills.get(id);
    }

    public Map<String, Pill> pills() {
        return pills;
    }

    /** 解析单张丹方产物。包内可见以便用例直接喂真实产物——realm/world 的教训是：解析器与产物的漂移只在运行时发作。 */
    static Pill parsePill(JsonObject product) {
        String id = require(product, "id").getAsString();
        JsonObject effect = product.getAsJsonObject("effect");
        if (effect == null) {
            throw new IllegalStateException("丹方产物 " + id + " 缺 effect 块（NUMBERS @@pills 的展开）");
        }
        if (effect.has("qi_rate_bonus")) {
            return new Pill(
                    id,
                    new Effect.QiRate(
                            require(effect, "qi_rate_bonus").getAsDouble(),
                            Math.round(
                                    require(effect, "duration_sec").getAsDouble()
                                            * StrifeTime.TICKS_PER_SECOND),
                            require(effect, "repeat_step").getAsDouble(),
                            require(effect, "repeat_floor").getAsDouble()));
        }
        if (effect.has("lifespan_years_gain")) {
            return new Pill(
                    id,
                    new Effect.Lifespan(
                            require(effect, "lifespan_years_gain").getAsInt(),
                            require(effect, "max_gain_per_realm").getAsInt()));
        }
        throw new IllegalStateException(
                "丹方产物 " + id + " 的 effect 既不是增益型也不是延寿型（pills.csv 的 effect_key 指错了？）");
    }

    private static com.google.gson.JsonElement require(JsonObject block, String key) {
        if (!block.has(key)) {
            throw new IllegalStateException("丹药产物缺键 '" + key + "'（NUMBERS @@pills 改了键名？）");
        }
        return block.get(key);
    }

    private static JsonObject read(Resource resource) {
        try (InputStream stream = resource.open();
                InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            throw new IllegalStateException("cannot read pill json " + resource.sourcePackId(), e);
        }
    }
}

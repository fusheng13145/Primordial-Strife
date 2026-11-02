package com.strife.production;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 异火运行时表（EP1 丹药体系，ADR-038）：classpath 直读 DataGen 产物 {@code strife_alchemy/fires.json}（{@code
 * tables/fires.csv} 直出）。
 *
 * <p>四项火效全部由表驱动（AGENTS.md 受管数值零字面量——此前 {@code Alchemy.craft} 内联 FireDef 字面量即违规点，本类落地后移除）：
 *
 * <ul>
 *   <li>{@code success_bonus}：失手后的第二把火判定概率（调用方现行为）
 *   <li>{@code quality_shift}：产出品质偏置——从最低品阶产出的概率质量平移到最高品阶（{@link
 *       AlchemyRecipe#qualityShifted}，只动产出分布、不动成功/失败）
 *   <li>{@code waste_reduce}：废丹回收概率——失手时按此概率追回 1 个主材
 *   <li>{@code tolerance} / {@code affinity}：火候容差与五行亲和，契约已承载（heat_range 玩法属 UI 立项），本期不参与 roll
 * </ul>
 *
 * <p>物品映射：火种物品 {@code item_fire_<x>} → 表条目 {@code fire_<x>}；{@link #NORMAL}（fire_normal）= 无火种的凡火兜底。
 */
public final class AlchemyFires {

    private static final Logger LOGGER = LoggerFactory.getLogger("strife/production");

    private static final String PATH = "/data/strife/strife_alchemy/fires.json";

    /** 一条异火：全部数值来自 fires.csv（真相源链：fires.csv → FireGenerator → 本表）。 */
    public record Fire(
            String id,
            String name,
            double tolerance,
            double qualityShift,
            String affinity,
            double successBonus,
            double wasteReduce) {}

    /** 凡火兜底：背包无任何火种时按 fire_normal 生效（全零加成）。 */
    public static final Fire NORMAL = new Fire("fire_normal", "凡火", 0.0, 0.0, "none", 0.0, 0.0);

    private static volatile Map<String, Fire> cache;

    private AlchemyFires() {}

    /** 按表 ID 取火（fire_chiyan…）；未知 ID 回 null（调用方可读解释，不猜默认值）。 */
    public static Fire get(String fireId) {
        return load().get(fireId);
    }

    /** 按火种物品路径取火（{@code item_fire_chiyan} → fire_chiyan）；非火种路径回 null。 */
    public static Fire byItemId(String itemPath) {
        if (itemPath == null || !itemPath.startsWith("item_fire_")) {
            return null;
        }
        return get(itemPath.substring("item_".length()));
    }

    /** 全量快照（用例与 /strife recipe 面用；只读映射）。 */
    public static Map<String, Fire> snapshot() {
        return load();
    }

    /** 测试与 /reload 场景的显式重载。 */
    public static void invalidate() {
        cache = null;
    }

    private static Map<String, Fire> load() {
        Map<String, Fire> loaded = cache;
        if (loaded != null) {
            return loaded;
        }
        synchronized (AlchemyFires.class) {
            if (cache != null) {
                return cache;
            }
            cache = loaded = parse();
            return loaded;
        }
    }

    private static Map<String, Fire> parse() {
        try (InputStream stream = AlchemyFires.class.getResourceAsStream(PATH)) {
            if (stream == null) {
                throw new IllegalStateException(PATH + " not in jar（FireGenerator 产物缺失）");
            }
            JsonObject json =
                    JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                            .getAsJsonObject();
            Map<String, Fire> entries = new LinkedHashMap<>();
            for (var element : json.getAsJsonArray("entries")) {
                JsonObject e = element.getAsJsonObject();
                for (String key : e.keySet()) {
                    if (!FIELDS.contains(key)) {
                        throw new IllegalStateException(
                                "fires.json unknown field '" + key + "'（§1.2 规则 2 fail-fast）");
                    }
                }
                Fire fire =
                        new Fire(
                                e.get("id").getAsString(),
                                e.get("name").getAsString(),
                                e.get("tolerance").getAsDouble(),
                                e.get("quality_shift").getAsDouble(),
                                e.get("affinity").getAsString(),
                                e.get("success_bonus").getAsDouble(),
                                e.get("waste_reduce").getAsDouble());
                entries.put(fire.id(), fire);
            }
            if (!entries.containsKey("fire_normal")) {
                throw new IllegalStateException("fires.json 缺 fire_normal（凡火兜底语义失效）");
            }
            return java.util.Collections.unmodifiableMap(entries);
        } catch (Exception e) {
            LOGGER.error("异火表加载失败（{}）——按凡火兜底，火效全部失效", PATH, e);
            return Map.of("fire_normal", NORMAL);
        }
    }

    private static final java.util.Set<String> FIELDS =
            java.util.Set.of(
                    "id",
                    "name",
                    "tolerance",
                    "quality_shift",
                    "affinity",
                    "success_bonus",
                    "waste_reduce");
}

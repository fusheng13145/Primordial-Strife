package com.strife.production;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;

/**
 * 炼丹配方的纯数据核与概率抽签（production 统一配方机的第一类，docs/03 §1"MVP 只交付炼丹与炼器"； 契约 = tables/pills.csv 的 DataGen 产物
 * {@code data/strife/strife_pills/<id>.json}）。
 *
 * <p>概率口径（M2 准出"丹方概率符合蒙特卡洛期望"）：{@code outputs} 是离散分布——累积区间抽签， 落在任何输出区间之外（Σprob &lt; 1
 * 时）即<b>炼制失败、材料损失</b>（failure_output 列为空 = 无保底， 与 LORE"丹道九转十不成"的语感一致；保底语义引入时走表列，不走代码）。抽签随机源以 IntRng
 * 注入， 蒙特卡洛测试与真实运行共用同一条 roll 路径。
 *
 * <p>heat_range（火候区间）本期只做契约承载与校验（0 ≤ min ≤ max ≤ 1，V-RANGE 面向表），火候玩法 （炉温控制交互）属 UI 立项，不在框架内假装实现。
 */
public record AlchemyRecipe(
        String id, List<Material> materials, List<Output> outputs, double heatMin, double heatMax) {

    public record Material(String itemId, int count) {}

    /** 一个可能产出：{@code prob} 是权重（Σoutputs.prob ≤ 1，差额 = 失败概率）。 */
    public record Output(String itemId, int count, double prob, String quality) {}

    public AlchemyRecipe {
        materials = materials == null ? List.of() : List.copyOf(materials);
        outputs = outputs == null ? List.of() : List.copyOf(outputs);
        if (materials.isEmpty()) {
            throw new IllegalStateException("recipe '" + id + "' has no materials");
        }
        if (outputs.isEmpty()) {
            throw new IllegalStateException("recipe '" + id + "' has no outputs");
        }
        if (heatMin < 0 || heatMax > 1 || heatMin > heatMax) {
            throw new IllegalStateException(
                    "recipe '" + id + "' heat_range [" + heatMin + ";" + heatMax + "] illegal");
        }
        double sum = outputs.stream().mapToDouble(Output::prob).sum();
        if (sum > 1.0 + 1e-9) {
            throw new IllegalStateException("recipe '" + id + "' outputs prob sum " + sum + " > 1");
        }
    }

    /** 随机源最小接口（MC RandomSource 与测试的 java.util.Random 都满足方法引用）。 */
    public interface IntRng {
        int nextInt(int bound);
    }

    /** 一次炼制抽签：累积区间命中输出；未命中（失败）返回 empty——材料已在调用方扣除，这里只管"出什么"。 */
    public java.util.Optional<Output> roll(IntRng rng) {
        int roll = rng.nextInt(1000);
        long threshold = 0;
        for (Output output : outputs) {
            threshold += Math.round(output.prob() * 1000);
            if (roll < threshold) {
                return java.util.Optional.of(output);
            }
        }
        return java.util.Optional.empty();
    }

    /** 从产物 JSON 解析（严格未知字段，§1.2 规则 2）。 */
    public static AlchemyRecipe parse(JsonObject json) {
        for (String key : json.keySet()) {
            if (!ALLOWED.contains(key)) {
                throw new IllegalStateException(
                        "unknown field '" + key + "' in pill recipe（§1.2 规则 2 fail-fast）");
            }
        }
        String id = json.get("id").getAsString();
        List<Material> materials =
                json.getAsJsonArray("materials").asList().stream()
                        .map(
                                e -> {
                                    JsonObject m = e.getAsJsonObject();
                                    for (String k : m.keySet()) {
                                        if (!MATERIAL_KEYS.contains(k)) {
                                            throw new IllegalStateException(
                                                    "unknown field '"
                                                            + k
                                                            + "' in materials of '"
                                                            + id
                                                            + "'");
                                        }
                                    }
                                    return new Material(
                                            m.get("item_id").getAsString(),
                                            m.get("count").getAsInt());
                                })
                        .toList();
        List<Output> outputs =
                json.getAsJsonArray("outputs").asList().stream()
                        .map(
                                e -> {
                                    JsonObject o = e.getAsJsonObject();
                                    for (String k : o.keySet()) {
                                        if (!OUTPUT_KEYS.contains(k)) {
                                            throw new IllegalStateException(
                                                    "unknown field '"
                                                            + k
                                                            + "' in outputs of '"
                                                            + id
                                                            + "'");
                                        }
                                    }
                                    return new Output(
                                            o.get("item_id").getAsString(),
                                            o.get("count").getAsInt(),
                                            o.get("prob").getAsDouble(),
                                            o.has("quality") && !o.get("quality").isJsonNull()
                                                    ? o.get("quality").getAsString()
                                                    : null);
                                })
                        .toList();
        JsonArray heat =
                json.has("heat_range") && json.get("heat_range").isJsonArray()
                        ? json.getAsJsonArray("heat_range")
                        : null;
        double heatMin = heat != null && heat.size() > 0 ? heat.get(0).getAsDouble() : 0;
        double heatMax = heat != null && heat.size() > 1 ? heat.get(1).getAsDouble() : 1;
        return new AlchemyRecipe(id, materials, outputs, heatMin, heatMax);
    }

    private static final java.util.Set<String> ALLOWED =
            java.util.Set.of(
                    "@generated",
                    "content_format",
                    "id",
                    "quality_tier",
                    "pattern",
                    "core_slot",
                    "materials",
                    "heat_range",
                    "outputs",
                    "effect",
                    "effect_key",
                    "failure_output",
                    "price");
    private static final java.util.Set<String> MATERIAL_KEYS = java.util.Set.of("item_id", "count");
    private static final java.util.Set<String> OUTPUT_KEYS =
            java.util.Set.of("item_id", "count", "prob", "quality");
}

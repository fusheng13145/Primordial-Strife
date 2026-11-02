package com.strife.production;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;

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
        return rollList(outputs, rng);
    }

    /**
     * 对任意输出分布抽签（品质偏置后的分布与原分布共用同一条累积区间路径）。
     *
     * <p>包内可见：真实炼制走 {@link #roll}，偏置分布走本方法——两条路径不允许出现两套抽签数学。
     */
    static java.util.Optional<Output> rollList(List<Output> pool, IntRng rng) {
        int roll = rng.nextInt(1000);
        long threshold = 0;
        for (Output output : pool) {
            threshold += Math.round(output.prob() * 1000);
            if (roll < threshold) {
                return java.util.Optional.of(output);
            }
        }
        return java.util.Optional.empty();
    }

    /**
     * 品质偏置（fires.csv {@code quality_shift}，ADR-038）：把概率质量从<b>最低品阶</b>产出平移到<b>最高品阶</b>产出——
     * 只重排产出分布，成功/失败总质量（1−Σprob）不动，Σprob 构造性守恒。
     *
     * <p>规则：偏置量 = min(shift, 最低品阶概率合计)，在最低/最高品阶内部按原概率比例缩放（多产出同阶时不成偏袒）。 单一产 出、全同品阶、或任一产出品阶未知（null /
     * 表外值）→ 原样返回（fail-soft：偏置宁可不生效，不改变分布形状）。
     */
    static List<Output> qualityShifted(List<Output> pool, double shift) {
        if (shift <= 0 || pool.size() < 2) {
            return pool;
        }
        int minRank = Integer.MAX_VALUE;
        int maxRank = Integer.MIN_VALUE;
        for (Output output : pool) {
            Integer rank = output.quality() == null ? null : QUALITY_RANK.get(output.quality());
            if (rank == null) {
                return pool;
            }
            minRank = Math.min(minRank, rank);
            maxRank = Math.max(maxRank, rank);
        }
        if (minRank == maxRank) {
            return pool;
        }
        final int lowest = minRank;
        final int highest = maxRank;
        double lowSum =
                pool.stream().filter(o -> rank(o) == lowest).mapToDouble(Output::prob).sum();
        double highSum =
                pool.stream().filter(o -> rank(o) == highest).mapToDouble(Output::prob).sum();
        double moved = Math.min(shift, lowSum);
        if (moved <= 0) {
            return pool;
        }
        double lowFactor = (lowSum - moved) / lowSum;
        double highFactor = (highSum + moved) / highSum;
        List<Output> shifted = new java.util.ArrayList<>(pool.size());
        for (Output output : pool) {
            int r = rank(output);
            double prob = output.prob();
            if (r == lowest) {
                prob *= lowFactor;
            } else if (r == highest) {
                prob *= highFactor;
            }
            shifted.add(new Output(output.itemId(), output.count(), prob, output.quality()));
        }
        return List.copyOf(shifted);
    }

    private static int rank(Output output) {
        return QUALITY_RANK.get(output.quality());
    }

    /** 品阶全序（JSON_SCHEMA §2 品阶枚举）；表外品阶在 qualityShifted 里按 fail-soft 原样返回。 */
    private static final Map<String, Integer> QUALITY_RANK =
            Map.of("fan", 0, "di", 1, "tian", 2, "xian_qi", 3);

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

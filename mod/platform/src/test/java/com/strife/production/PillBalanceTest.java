package com.strife.production;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.strife.testing.ShippedProducts;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * P1-13 平衡复跑：全量丹方 × 种子化蒙特卡洛，与契约概率对账（G-5 定稿数值）。
 *
 * <p>与真实施放共用 {@link AlchemyRecipe#roll} 路径（ADR-020 真实产物）；断言按 3σ 容差—— 概率表错了这里红，随机抖动不会红。观察频数打印进
 * stdout，作为平衡报告的数据行（可复跑命令=本测试）。
 */
class PillBalanceTest {

    private static final int TRIALS = 200_000;
    private static final long SEED = 20261101L;

    @Test
    void allShippedPillsMatchContractProbabilities() {
        List<JsonObject> pills = ShippedProducts.allPills();
        assertTrue(pills.size() >= 4, "至少 4 张丹方（聚气/培元/延寿/玄玉）");

        for (JsonObject pillJson : pills) {
            AlchemyRecipe recipe = AlchemyRecipe.parse(pillJson);
            String id = recipe.id();
            Map<String, Long> observed = new java.util.HashMap<>();
            Random random = new Random(SEED);
            for (int i = 0; i < TRIALS; i++) {
                recipe.roll(random::nextInt)
                        .ifPresent(out -> observed.merge(out.itemId(), 1L, Long::sum));
            }
            // 对每个产物输出按 3σ 对账：|observed - n·p| ≤ 3·√(n·p·(1-p))
            double totalProb = 0.0;
            for (AlchemyRecipe.Output out : recipe.outputs()) {
                double expected = TRIALS * out.prob();
                double sigma = Math.sqrt(TRIALS * out.prob() * Math.max(1e-9, 1 - out.prob()));
                long seen = observed.getOrDefault(out.itemId(), 0L);
                totalProb += out.prob();
                assertTrue(
                        Math.abs(seen - expected) <= 3 * sigma,
                        String.format(
                                "%s 的 %s 频数 %d 偏离期望 %.0f 超过 3σ（σ=%.1f）——概率表或 roll 路径漂移",
                                id, out.itemId(), seen, expected, sigma));
                System.out.printf(
                        "balance %s %s observed=%d expected=%.0f sigma=%.1f%n",
                        id, out.itemId(), seen, expected, sigma);
            }
            assertTrue(
                    Math.abs(totalProb - 1.0) < 1e-9 || totalProb < 1.0,
                    "产物概率合计 ≤1（失手率=剩余）；玄玉/聚气/培元为 1.0");
        }
    }
}

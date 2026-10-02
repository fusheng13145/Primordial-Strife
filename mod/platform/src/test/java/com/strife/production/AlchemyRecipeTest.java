package com.strife.production;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.strife.production.AlchemyRecipe.IntRng;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 炼丹配方概率的蒙特卡洛验证（docs/07 §3 M2 准出："丹方概率符合蒙特卡洛期望"）。
 *
 * <p>与真实运行共用 {@link AlchemyRecipe#roll} 路径——这里测的是概率语义本身（累积区间抽签的分布收敛）， 不是"测试桩证明测试桩"。
 */
class AlchemyRecipeTest {

    /** 与 DataGen 产物同构的夹具（pill_peiyuan：0.85 主产物 / 0.15 副产物）。 */
    private static final String PEIYUAN =
            """
            {
              "id": "pill_peiyuan",
              "quality_tier": "di",
              "materials": [
                {"item_id": "item_yuejian", "count": 2},
                {"item_id": "item_ningxu", "count": 1}
              ],
              "heat_range": [0.4, 0.6],
              "outputs": [
                {"item_id": "pill_peiyuan", "count": 1, "prob": 0.85, "quality": "di"},
                {"item_id": "pill_juqi", "count": 2, "prob": 0.15, "quality": "fan"}
              ]
            }
            """;

    /** Σprob < 1 的夹具：差额 = 炼制失败概率（材料损失，无保底）。 */
    private static final String UNSAFE =
            """
            {
              "id": "pill_risky",
              "materials": [{"item_id": "item_x", "count": 1}],
              "heat_range": [0.1, 0.9],
              "outputs": [
                {"item_id": "pill_a", "count": 1, "prob": 0.5},
                {"item_id": "pill_b", "count": 1, "prob": 0.3}
              ]
            }
            """;

    @Test
    @DisplayName("蒙特卡洛 10 万炉：主/副产物频率收敛到表声明概率（±1%）")
    void montecarloConvergesToDeclaredProbabilities() {
        AlchemyRecipe recipe =
                AlchemyRecipe.parse(JsonParser.parseString(PEIYUAN).getAsJsonObject());
        Random random = new Random(20260921L);
        int trials = 100_000;
        int main = 0;
        int byproduct = 0;
        for (int i = 0; i < trials; i++) {
            var output = recipe.roll(random::nextInt);
            if (output.isEmpty()) {
                continue;
            }
            if (output.get().itemId().equals("pill_peiyuan")) {
                main++;
            } else {
                byproduct++;
            }
        }
        double mainFreq = (double) main / trials;
        double byFreq = (double) byproduct / trials;
        assertTrue(Math.abs(mainFreq - 0.85) < 0.01, "main " + mainFreq);
        assertTrue(Math.abs(byFreq - 0.15) < 0.01, "by " + byFreq);
    }

    @Test
    @DisplayName("Σprob < 1：未命中区间 = 炼制失败（材料损失，无静默保底）")
    void missingProbabilityIsAFailureNotASilentDefault() {
        AlchemyRecipe recipe =
                AlchemyRecipe.parse(JsonParser.parseString(UNSAFE).getAsJsonObject());
        Random random = new Random(7L);
        int trials = 100_000;
        int failure = 0;
        for (int i = 0; i < trials; i++) {
            if (recipe.roll(random::nextInt).isEmpty()) {
                failure++;
            }
        }
        double failureFreq = (double) failure / trials;
        // Σprob = 0.8 → 失败期望 0.2
        assertTrue(Math.abs(failureFreq - 0.2) < 0.01, "failure " + failureFreq);
    }

    @Test
    @DisplayName("roll 的随机源接口兼容 MC RandomSource（方法引用形态）")
    void rollAcceptsMethodReferenceShape() {
        AlchemyRecipe recipe =
                AlchemyRecipe.parse(JsonParser.parseString(PEIYUAN).getAsJsonObject());
        IntRng bounded = new Random(1L)::nextInt;
        for (int i = 0; i < 100; i++) {
            assertTrue(recipe.roll(bounded).isPresent());
        }
    }

    @Test
    @DisplayName("非法配方当场拒绝：概率和 > 1")
    void outputsProbSumAboveOneIsRejected() {
        String bad =
                """
                {"id": "x", "materials": [{"item_id": "a", "count": 1}],
                 "outputs": [{"item_id": "p", "count": 1, "prob": 0.7},
                             {"item_id": "q", "count": 1, "prob": 0.7}]}
                """;
        IllegalStateException e =
                assertThrows(
                        IllegalStateException.class,
                        () -> AlchemyRecipe.parse(JsonParser.parseString(bad).getAsJsonObject()));
        assertTrue(e.getMessage().contains("prob sum"), e.getMessage());
    }

    @Test
    @DisplayName("heat_range 越界当场拒绝（契约承载，玩法留 UI 立项）")
    void heatRangeOutsideUnitIntervalIsRejected() {
        String bad =
                """
                {"id": "x", "materials": [{"item_id": "a", "count": 1}],
                 "heat_range": [0.2, 1.7],
                 "outputs": [{"item_id": "p", "count": 1, "prob": 1.0}]}
                """;
        IllegalStateException e =
                assertThrows(
                        IllegalStateException.class,
                        () -> AlchemyRecipe.parse(JsonParser.parseString(bad).getAsJsonObject()));
        assertTrue(e.getMessage().contains("heat_range"), e.getMessage());
    }
}

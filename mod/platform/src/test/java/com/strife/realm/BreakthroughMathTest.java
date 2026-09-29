package com.strife.realm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 突破/小境界/灵根纯数学（docs/07 §7 A1-1/A1-4 公式层；数值全部由 RealmTables 注入）。 */
class BreakthroughMathTest {

    // ===== 突破成功率（NUMBERS §3 fail_step 语义）=====

    @Test
    void successRateStartsAtBase() {
        assertEquals(0.95, BreakthroughMath.successProbability(0.95, -0.05, 0, 0.40), 1e-9);
    }

    @Test
    void successRateDecaysWithAttempts() {
        assertEquals(0.85, BreakthroughMath.successProbability(0.95, -0.05, 2, 0.40), 1e-9);
        assertEquals(0.75, BreakthroughMath.successProbability(0.95, -0.05, 4, 0.40), 1e-9);
    }

    @Test
    void successRateFloorsAtFloor() {
        // 0.95 - 0.05×20 = -0.05 → floor 0.40
        assertEquals(0.40, BreakthroughMath.successProbability(0.95, -0.05, 20, 0.40), 1e-9);
    }

    @Test
    void entryBreakthroughHasNoDecay() {
        // bs_fanren_qili: fail_step 0.00（入门不卡人）
        assertEquals(0.95, BreakthroughMath.successProbability(0.95, 0.00, 9, 0.95), 1e-9);
    }

    // ===== 突破失败回退（NUMBERS @@breakthrough_cost）=====

    @Test
    void resetQiScalesWithRatio() {
        assertEquals(520, BreakthroughMath.resetQi(650, 0.80));
        assertEquals(390, BreakthroughMath.resetQi(650, 0.60));
        assertEquals(0, BreakthroughMath.resetQi(650, 0.0));
    }

    // ===== 小境界推导（NUMBERS §1 阈值等分口径）=====

    @Test
    void stageDerivationSplitsQiMaxEvenly() {
        // 练气 9 层，qi_max 230：每层 25.56
        assertEquals(1, BreakthroughMath.stageFor(0, 230, 9));
        assertEquals(1, BreakthroughMath.stageFor(25, 230, 9));
        assertEquals(2, BreakthroughMath.stageFor(26, 230, 9));
        assertEquals(9, BreakthroughMath.stageFor(230, 230, 9));
    }

    @Test
    void singleStageRealmsAreAlwaysStageOne() {
        assertEquals(1, BreakthroughMath.stageFor(999, 100, 1), "凡人 stage_count=1");
    }

    // ===== 灵根（docs/03 §8 UUID+seed 种子化）=====

    @Test
    void spiritRootSeedIsDeterministicPerUuidAndWorld() {
        var uuid = java.util.UUID.fromString("380df991-f603-344c-a090-369bad2a9245");
        assertEquals(
                BreakthroughMath.spiritRootSeed(uuid, 12345L),
                BreakthroughMath.spiritRootSeed(uuid, 12345L));
        assertTrue(
                BreakthroughMath.spiritRootSeed(uuid, 12345L)
                        != BreakthroughMath.spiritRootSeed(uuid, 54321L));
    }

    @Test
    void rollWeightsControlQualityDistribution() {
        // 全部权重压 tier1 → 恒单系天灵根
        BreakthroughMath.IntRng zero = bound -> 0;
        BreakthroughMath.SpiritRootResult result =
                BreakthroughMath.rollSpiritRoot(zero, 10, 0, 0, 0);
        assertEquals(1, result.quality());
        assertEquals(1, Integer.bitCount(result.elementsMask()), "天灵根单系");

        // 全部压 tier4 → 四或五灵根
        BreakthroughMath.SpiritRootResult mixed =
                BreakthroughMath.rollSpiritRoot(zero, 0, 0, 0, 10);
        assertEquals(4, mixed.quality());
        assertTrue(Integer.bitCount(mixed.elementsMask()) >= 4, "四/五灵根（杂灵根）");
    }

    @Test
    void qualityDistributionFollowsWeightsStatistically() {
        Random random = new Random(42);
        int tier1 = 0;
        int samples = 100_000;
        for (int i = 0; i < samples; i++) {
            // NUMBERS §6 roll_weights {tier_1: 2, tier_2: 8, tier_3: 25, tier_4: 65}
            if (BreakthroughMath.rollSpiritRoot(random::nextInt, 2, 8, 25, 65).quality() == 1) {
                tier1++;
            }
        }
        double rate = (double) tier1 / samples;
        assertTrue(rate > 0.015 && rate < 0.025, "2% 权重的天灵根频率实测 " + rate);
    }

    @Test
    void elementsAreDistinctBits() {
        Random random = new Random(7);
        for (int i = 0; i < 1000; i++) {
            int mask =
                    BreakthroughMath.rollSpiritRoot(random::nextInt, 2, 8, 25, 65).elementsMask();
            Set<Integer> seen = new HashSet<>();
            for (int bit = 0; bit < 5; bit++) {
                if ((mask & (1 << bit)) != 0) {
                    assertTrue(seen.add(bit), "同一五行出现两次：mask " + mask);
                }
            }
        }
    }
}

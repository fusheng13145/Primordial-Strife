package com.strife.realm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
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

    @Test
    void stageStepQiGivesTheThresholdOfTheNextStage() {
        // 练气 9 层，qi_max 230：从第 1 段推进一步 → 第 2 段起点 = floor(230×1/9)=25
        assertEquals(25, BreakthroughMath.stageStepQi(1, 230, 9));
        // 第 8 段推进 → 第 9 段起点 = floor(230×8/9)=204
        assertEquals(204, BreakthroughMath.stageStepQi(8, 230, 9));
        // 推进结果与 stageFor 往返一致：落在第 stage+1 段
        assertEquals(2, BreakthroughMath.stageFor(BreakthroughMath.stageStepQi(1, 230, 9), 230, 9));
        assertEquals(9, BreakthroughMath.stageFor(BreakthroughMath.stageStepQi(8, 230, 9), 230, 9));
    }

    @Test
    void stageStepQiAtFullStageGrantsBreakthroughEligibility() {
        // 满段推进 = 境界圆满（qi_max，可押注突破），不是溢出也不是不动
        assertEquals(230, BreakthroughMath.stageStepQi(9, 230, 9));
        // stage_count=1 的境界（凡人）任何推进都直达圆满
        assertEquals(100, BreakthroughMath.stageStepQi(1, 100, 1));
    }

    @Test
    void stageStepQiNeverDecreasesQi() {
        // 玩家修为已超下一档阈值（比如丹药/奖励先加过）时，推进不得把修为变少：
        // 装配层取 max(当前, 目标)，这里钉住数学侧"目标恒 ≥ 下一档起点"的边界
        for (int stage = 1; stage <= 8; stage++) {
            int next = BreakthroughMath.stageStepQi(stage, 230, 9);
            assertTrue(next >= (int) Math.floor(230.0 * stage / 9), "段 " + stage);
        }
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

    /**
     * 回归：抽五行必须是有限步的。定值 RNG 曾经把服务端主线程钉死在"抽到重复就重抽"的 while 上（本用例当初就是那个
     * 死循环的现场）；这里用一个调用次数受限的随机源把"拒绝采样又回来了"变成一条失败的断言，而不是一次挂起的 CI。
     */
    @Test
    void elementPickingIsBoundedEvenWithADegenerateRng() {
        AtomicInteger calls = new AtomicInteger();
        BreakthroughMath.IntRng bounded =
                bound -> {
                    if (calls.incrementAndGet() > 64) {
                        throw new AssertionError("抽签没有迭代上限：拒绝采样又回来了");
                    }
                    return 0;
                };

        BreakthroughMath.SpiritRootResult result =
                BreakthroughMath.rollSpiritRoot(bounded, 0, 0, 0, 10);

        assertEquals(4, result.quality());
        assertEquals(4, Integer.bitCount(result.elementsMask()), "四系仍需抽满 4 个不重复元素");
    }

    /** 五系杂灵根要抽满全部五行，掩码必须是 31（不接受 4 个就收工的边界错）。 */
    @Test
    void allFiveElementsArePickedWhenTheRollAsksForFive() {
        Random random = new Random(11);
        boolean sawFive = false;
        for (int i = 0; i < 200; i++) {
            BreakthroughMath.SpiritRootResult result =
                    BreakthroughMath.rollSpiritRoot(random::nextInt, 0, 0, 0, 10);
            int bits = Integer.bitCount(result.elementsMask());
            assertTrue(bits == 4 || bits == 5, "四/五灵根只允许 4 或 5 系，实际 " + bits);
            if (bits == 5) {
                assertEquals(0b11111, result.elementsMask());
                sawFive = true;
            }
        }
        assertTrue(sawFive, "200 次里至少应出现一次五系（quality 4 时 50% 概率取 5 系）");
    }
}

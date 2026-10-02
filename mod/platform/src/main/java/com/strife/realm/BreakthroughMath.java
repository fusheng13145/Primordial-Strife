package com.strife.realm;

/**
 * 突破/小境界/灵根生成的纯数学（MVP 快速通道的可测核，docs/07 §7 A1-4/A1-1 的公式层）。 全部输入来自 RealmTables（NUMBERS
 * 派生产物），这里零受管字面量。
 */
public final class BreakthroughMath {

    private BreakthroughMath() {}

    /** 本次突破成功率 = base + fail_step × 已失败次数，封顶 1.0、封底 floor（NUMBERS §3 fail_step 语义）。 */
    public static double successProbability(
            double base, double failStep, int attempts, double floor) {
        return Math.max(floor, Math.min(1.0, base + failStep * Math.max(0, attempts)));
    }

    /** 突破失败：修为回退至上限 × ratio（NUMBERS @@breakthrough_cost）。 */
    public static int resetQi(int qiMax, double ratio) {
        return (int) Math.floor(qiMax * Math.max(0.0, Math.min(1.0, ratio)));
    }

    /**
     * 小境界推导：阈值按 qi_max × i / stage_count 等分（NUMBERS §1 口径，不入表）， qi=0 记第 1 段；stage_count ≤ 1 时恒 1。
     */
    public static int stageFor(int qi, int qiMax, int stageCount) {
        if (stageCount <= 1) {
            return 1;
        }
        double perStage = (double) qiMax / stageCount;
        int stage = (int) Math.floor(qi / perStage) + 1;
        return Math.max(1, Math.min(stageCount, stage));
    }

    /**
     * realm_step 奖励（小境界推进一档）的目标修为：第 {@code stage + 1} 段的起点阈值 {@code qi_max × stage / stage_count}（与
     * {@link #stageFor} 同一等分口径）；已在满段时给到 {@code qi_max}——满段后的"下一档"就是境界圆满， 玩家由此获得主动押注突破的资格（押注本身仍由
     * breakthrough 意图显式发起，不自动突破）。
     */
    public static int stageStepQi(int stage, int qiMax, int stageCount) {
        if (stageCount <= 1 || stage >= stageCount) {
            return qiMax;
        }
        return (int) Math.floor((double) qiMax * stage / stageCount);
    }

    /** 灵根种子：UUID × 世界种子（docs/03 §8"UUID+seed 种子化，一次成型"）。 */
    public static long spiritRootSeed(java.util.UUID uuid, long worldSeed) {
        return uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits() ^ worldSeed;
    }

    /**
     * 品阶抽签：roll_weights（NUMBERS §6）累积分布；元素数按品阶 1→1 系、2→2、3→3、4→四/五。 元素位序 bit0 金 bit1 木 bit2 水 bit3
     * 火 bit4 土（StrifeData 契约口径）。
     */
    /** 随机源的最小接口：MC 的 RandomSource 与测试的 java.util.Random 都满足方法引用。 */
    public interface IntRng {
        int nextInt(int bound);
    }

    public static SpiritRootResult rollSpiritRoot(IntRng random, int w1, int w2, int w3, int w4) {
        int total = Math.max(1, w1 + w2 + w3 + w4);
        int roll = random.nextInt(total);
        int quality;
        if (roll < w1) {
            quality = 1;
        } else if (roll < w1 + w2) {
            quality = 2;
        } else if (roll < w1 + w2 + w3) {
            quality = 3;
        } else {
            quality = 4;
        }
        int elementCount =
                switch (quality) {
                    case 1 -> 1;
                    case 2 -> 2;
                    case 3 -> 3;
                    default -> 4 + random.nextInt(2);
                };
        return new SpiritRootResult(quality, pickElements(random, elementCount));
    }

    /**
     * 从五行里不放回地抽 {@code elementCount} 个，返回位掩码——partial Fisher–Yates，迭代次数恒为抽取个数。
     *
     * <p><b>刻意不用"抽到重复就再抽一次"的拒绝采样</b>：那是一个没有迭代上限的 {@code do/while}，碰上退化随机源（定值 RNG、 被外部状态污染的
     * RandomSource）会把服务端主线程永久钉死，玩家侧表现为服务器卡死而不是任何可诊断的错误。本方法对任意满足 {@code nextInt(bound)}
     * 契约的随机源都在有限步内返回（回归用例 {@code elementPickingIsBoundedEvenWithADegenerateRng}）。
     */
    private static int pickElements(IntRng random, int elementCount) {
        int[] pool = {0, 1, 2, 3, 4};
        int count = Math.max(1, Math.min(pool.length, elementCount));
        int mask = 0;
        for (int i = 0; i < count; i++) {
            int swap = i + random.nextInt(pool.length - i);
            int picked = pool[swap];
            pool[swap] = pool[i];
            pool[i] = picked;
            mask |= 1 << picked;
        }
        return mask;
    }

    public record SpiritRootResult(int quality, int elementsMask) {}
}

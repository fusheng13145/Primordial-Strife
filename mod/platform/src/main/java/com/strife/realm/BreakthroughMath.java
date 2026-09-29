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
        int mask = 0;
        for (int picked = 0; picked < elementCount; picked++) {
            int element;
            do {
                element = random.nextInt(5);
            } while ((mask & (1 << element)) != 0);
            mask |= 1 << element;
        }
        return new SpiritRootResult(quality, mask);
    }

    public record SpiritRootResult(int quality, int elementsMask) {}
}

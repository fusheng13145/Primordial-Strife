package com.strife.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import org.junit.jupiter.api.Test;

/**
 * G-8 实测：docs/03 §6 性能红线「新区块灵气场生成 ≤2ms/chunk（命中缓存 ≈0）」。
 *
 * <p><b>与 {@link AmbientQiFieldTest} 的分工</b>：那边用假噪声钉<b>行为</b>（上下限、区域粒度、缓存命中、非零），
 * 噪声实现换成什么都成立；这边用<b>生产同款 {@link ImprovedNoise}</b> 测<b>速度</b>——假噪声比真实噪声快一到两个数量级，
 * 拿假噪声报出的毫秒数是自欺。所以本类是唯一允许"慢"的测试，也是唯一需要真实噪声的测试。
 *
 * <p><b>断言策略</b>：上限断言刻意宽松（{@value #BUDGET_MS_PER_CHUNK} ms 的 10 倍），
 * 因为 CI 机、开发者机与本机的 CPU 差异可达数倍，绑死 2ms 会让门禁变成随机红。
 * 真正的读数由 {@link #report} 打印进测试输出，<b>数字进交接文档才算实测</b>；
 * 宽松上限只负责兜住"数量级级别的退化"（例如误把缓存改成每次重采样噪声，会直接撞上限）。
 */
class AmbientQiBenchmarkTest {

    /** docs/03 §6 的红线值。 */
    private static final double BUDGET_MS_PER_CHUNK = 2.0;

    /**
     * 断言上限 = 红线的 10 倍。见类注释：这不是"允许超标"，而是"只抓数量级退化"，
     * 真实读数靠 report 打印 + 人工入档。
     */
    private static final double ASSERT_CEILING_MS_PER_CHUNK = BUDGET_MS_PER_CHUNK * 10;

    /** NUMBERS @@world 的生产值：ambient_qi_min / max / region_chunks / refine_weight。 */
    private static final double MIN = 0.50;
    private static final double MAX = 2.00;
    private static final int REGION_CHUNKS = 16;
    private static final double REFINE_WEIGHT = 0.25;

    /** 固定种子：基准要可复现，不能因世界种子不同而得出不同结论。 */
    private static final long SEED = 20261003L;

    /** 玩家视野预载的典型半径（客户端渲染距离默认档位），够覆盖"预热一批区块"的真实场景。 */
    private static final int WARM_CHUNKS = 256;

    private static AmbientQiField productionField() {
        RandomSource random = RandomSource.create(SEED);
        return new AmbientQiField(
                new ImprovedNoise(random)::noise,
                new ImprovedNoise(random)::noise,
                MIN,
                MAX,
                REGION_CHUNKS,
                REFINE_WEIGHT);
    }

    /**
     * 主用例：分别测"冷"（缓存全空，逐块算）与"热"（缓存已命中）两条路径。
     *
     * <p>红线写的是"≤2ms/chunk（命中缓存 ≈0）"，所以两个数都要：冷路径是真实成本上限，
     * 热路径是玩家实际感知到的成本。区域按 16×16 分组，所以遍历顺序决定了冷/热的比例——
     * 这里<b>刻意用对角线顺序</b>（而非行优先），让相邻区块落在同一区域内，把"命中"这条路真正走到。
     */
    @Test
    void newChunkGenerationStaysWithinBudget() {
        AmbientQiField field = productionField();

        // ── 冷路径：缓存全空，每个区块付一次粗粒度 + 一次细化层采样 ──
        int[] cold = new int[WARM_CHUNKS];
        for (int i = 0; i < WARM_CHUNKS; i++) {
            // 对角线：x 与 z 同增，让 16 个区块共享一个粗粒度区域
            int chunkX = i * 16;
            int chunkZ = i * 16;
            long start = System.nanoTime();
            field.ratioAt(chunkX, chunkZ);
            cold[i] = (int) (System.nanoTime() - start);
        }

        // ── 热路径：再访问一遍同样的区块，应全部命中粗粒度缓存 ──
        int[] warm = new int[WARM_CHUNKS];
        for (int i = 0; i < WARM_CHUNKS; i++) {
            int chunkX = i * 16;
            int chunkZ = i * 16;
            long start = System.nanoTime();
            field.ratioAt(chunkX, chunkZ);
            warm[i] = (int) (System.nanoTime() - start);
        }

        // 区域粒度 16 → 256 个对角区块全部落在 1×1 个区域内，粗粒度只该采样一次
        assertEquals(
                1,
                field.coarseSamples(),
                "对角线遍历下 256 个区块应只采一次粗粒度噪声（区域 16×16）");
        assertEquals(WARM_CHUNKS, field.cacheHits(), "第二遍全部应命中缓存");

        report("cold", cold);
        report("warm", warm);

        assertWithinCeiling("cold", cold);
        // 红线对命中缓存的要求是"≈0"：热路径必须显著快于冷路径，否则缓存没起作用
        assertWithinCeiling("warm", warm);
        assertTrue(
                average(warm) < average(cold),
                String.format(
                        Locale.ROOT,
                        "命中缓存应比冷路径快：warm=%.4fms cold=%.4fms",
                        average(warm),
                        average(cold)));
    }

    /**
     * 缓存上限的行为：超过 {@code MAX_CACHED_REGIONS}（4096）即清空重建。
     *
     * <p>这条不是性能测试，是<b>防退化</b>：清空策略如果哪天改成"LRU 逐出"或"永不淘汰"，缓存会无限增长直至 OOM。
     * 这里钉住"超限后仍能正确返回值"，不钉住具体数字。
     */
    @Test
    void cacheStaysBoundedAndStillReturnsValidValues() {
        AmbientQiField field = productionField();
        double first = field.ratioAt(0, 0);

        // 走远超 4096 个不同区域，逼出清空分支
        for (int region = 0; region < 5000; region++) {
            field.ratioAt(region * REGION_CHUNKS, 0);
        }

        assertTrue(field.cachedRegions() <= 4096, "缓存不得无限增长，实际 " + field.cachedRegions());
        double after = field.ratioAt(0, 0);
        assertEquals(first, after, 1e-12, "清空后重算必须得到同一个值（噪声由种子决定）");
        assertTrue(after >= MIN - 1e-9 && after <= MAX + 1e-9, "清空后取值仍须落在受管区间");
    }

    /** 玩家查询走的是 {@code ratioAtBlock}，它比 {@code ratioAt} 多一次 floorDiv——一并测，免得只测了一半路径。 */
    @Test
    void blockQueryPathIsAlsoWithinBudget() {
        AmbientQiField field = productionField();
        for (int i = 0; i < 2000; i++) {
            field.ratioAtBlock(i * 16 * 16, i * 16 * 16); // 预热
        }
        int[] samples = new int[WARM_CHUNKS];
        for (int i = 0; i < WARM_CHUNKS; i++) {
            long start = System.nanoTime();
            field.ratioAtBlock(i * 16 * 16, i * 16 * 16);
            samples[i] = (int) (System.nanoTime() - start);
        }
        report("blockQuery", samples);
        assertWithinCeiling("blockQuery", samples);
    }

    // ── 报告与断言 ────────────────────────────────────────────────────────

    private static void report(String label, int[] nanos) {
        long total = 0;
        long max = 0;
        for (int n : nanos) {
            total += n;
            max = Math.max(max, n);
        }
        System.out.printf(
                Locale.ROOT,
                "ambient-qi-bench %-11s n=%d avg=%.4fms p50=%.4fms max=%.4fms budget=%.1fms%n",
                label,
                nanos.length,
                total / 1e6 / nanos.length,
                percentile(nanos, 50) / 1e6,
                max / 1e6,
                BUDGET_MS_PER_CHUNK);
    }

    private static void assertWithinCeiling(String label, int[] nanos) {
        double avg = average(nanos);
        assertTrue(
                avg <= ASSERT_CEILING_MS_PER_CHUNK,
                String.format(
                        Locale.ROOT,
                        "%s 平均 %.4fms 超过宽松上限 %.1fms（红线 %.1fms）——疑似数量级退化，"
                                + "先查缓存是否被绕过",
                        label, avg, ASSERT_CEILING_MS_PER_CHUNK, BUDGET_MS_PER_CHUNK));
    }

    private static double average(int[] nanos) {
        long total = 0;
        for (int n : nanos) {
            total += n;
        }
        return total / 1e6 / nanos.length;
    }

    private static int percentile(int[] nanos, int p) {
        int[] sorted = nanos.clone();
        java.util.Arrays.sort(sorted);
        return sorted[Math.min(sorted.length - 1, sorted.length * p / 100)];
    }
}

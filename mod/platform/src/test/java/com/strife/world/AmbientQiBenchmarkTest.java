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
     * 热路径是玩家实际感知到的成本。
     *
     * <p><b>遍历策略</b>：区域是 16×16 区块一块，{@code regionX = floorDiv(chunkX, 16)}。
     * 所以"冷"和"热"必须<b>分开造</b>，而且要先算清坐标落在哪个桶里：
     * <ul>
     *   <li>冷路径：{@code chunk = i * 16} → 第 i 个区域，每块都未命中（{@code coarseSamples == n}）；
     *   <li>热路径：{@code chunk = base + (i % 16)} → 全在第 base/16 个区域内，来回走（1 次采样 + n-1 次命中）。
     * </ul>
     * 初版用"对角线"想一次造两种，实际 {@code chunk=i*16} 落在第 i 个区域，256 块 = 256 个不同区域，
     * 区域内命中这条真正要验的东西根本没被走到；第二次改成 {@code i/16} 铺开也超预期（算出 16 实际 1）。
     * <b>教训：区域粒度是 16 时，"同一区域"只能靠 {@code % 16} 制造，靠 {@code / 16} 只会换区域。</b>
     */
    @Test
    void newChunkGenerationStaysWithinBudget() {
        AmbientQiField field = productionField();

        // ── 冷路径：每次都跳到新区域，粗粒度缓存必然未命中 ──
        int[] cold = new int[WARM_CHUNKS];
        for (int i = 0; i < WARM_CHUNKS; i++) {
            int chunkX = i * REGION_CHUNKS; // 区域 i
            int chunkZ = i * REGION_CHUNKS;
            long start = System.nanoTime();
            field.ratioAt(chunkX, chunkZ);
            cold[i] = (int) (System.nanoTime() - start);
        }
        // 冷路径每个区块一个新区域 → 粗粒度采样数 == 区块数
        assertEquals(WARM_CHUNKS, field.coarseSamples(), "跨区域遍历时每次都该是缓存未命中");

        // ── 热路径：<b>反复访问同一个区域</b>，粗粒度只该采一次、其余 255 次全命中 ──
        // 这是玩家真实处境：一个区域（16×16 区块）内走动，灵气值来自同一份粗粒度缓存。
        int[] warm = new int[WARM_CHUNKS];
        int baseX = WARM_CHUNKS * REGION_CHUNKS;
        int baseZ = WARM_CHUNKS * REGION_CHUNKS;
        int beforeHits = field.cacheHits();
        for (int i = 0; i < WARM_CHUNKS; i++) {
            // 在 baseX/baseZ 所在区域内来回走（floorDiv 后 regionX/regionZ 恒定）
            int chunkX = baseX + (i % REGION_CHUNKS);
            int chunkZ = baseZ + (i % REGION_CHUNKS);
            long start = System.nanoTime();
            field.ratioAt(chunkX, chunkZ);
            warm[i] = (int) (System.nanoTime() - start);
        }
        int newSamples = field.coarseSamples() - WARM_CHUNKS;
        int newHits = field.cacheHits() - beforeHits;
        assertEquals(1, newSamples, "反复走同一区域，粗粒度只应新增 1 次采样");
        assertEquals(WARM_CHUNKS - 1, newHits, "除首个区块外都该命中缓存");

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
        // 预热：跨区域铺一遍，让缓存进入"大部分命中"的状态（模拟玩家已在该区域活动过）
        for (int region = 0; region < 2000; region++) {
            field.ratioAtBlock(region * REGION_CHUNKS * 16, region * REGION_CHUNKS * 16);
        }
        int[] samples = new int[WARM_CHUNKS];
        for (int i = 0; i < WARM_CHUNKS; i++) {
            long start = System.nanoTime();
            // 传入方块坐标（×16 换成方块），再回到同一批区域上查询 → 应全部命中粗粒度缓存
            field.ratioAtBlock(i * REGION_CHUNKS * 16, i * REGION_CHUNKS * 16);
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

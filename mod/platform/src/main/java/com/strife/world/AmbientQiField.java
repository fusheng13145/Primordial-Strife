package com.strife.world;

import java.util.HashMap;
import java.util.Map;

/**
 * 灵气浓度场（docs/03 §8"灵气浓度场（区域环境系数）"、03 §6 的性能红线）。
 *
 * <p>结构照 03 §6 的口径分两层：
 *
 * <ul>
 *   <li><b>区域粗粒度</b>：{@code ambient_region_chunks}² 区块一个值（默认 16×16 区块），按区域坐标缓存——这是"这个片区
 *       灵气如何"的答案，也是缓存命中时 ≈0 成本的那一层；
 *   <li><b>细化层</b>：区块尺度的轻微起伏，权重 {@code ambient_refine_weight}，避免整片区域的修炼速率完全一样。
 * </ul>
 *
 * <p>取值一律夹在 NUMBERS {@code @@world.ambient_qi_min..max} 之间，且 <b>恒 &gt; 0</b>——05 §2 要求"任一系数为 0
 * 必须有可展示 的理由"，一个 0 系数的区域就是玩家永远练不动却看不出原因的死地。
 *
 * <p>噪声源以 {@link Noise} 注入：生产用 MC 的 {@code ImprovedNoise}（世界种子决定，同种子同结果），用例用确定性假噪声把
 * 边界（上下限、区域粒度、缓存命中）逐条钉住，不必依赖 MC 的噪声实现细节。
 */
public final class AmbientQiField {

    /** 噪声采样口。 */
    @FunctionalInterface
    public interface Noise {
        double sample(double x, double y, double z);
    }

    /** 粗粒度缓存上限（区域数）。超出即清空重建：缓存只影响性能，不影响取值，所以不需要精细淘汰策略。 */
    private static final int MAX_CACHED_REGIONS = 4096;

    /** Minecraft 固定 16 方块/区块（平台常量，不是受管数值）。 */
    private static final int BLOCKS_PER_CHUNK = 16;

    private final Noise coarseNoise;
    private final Noise fineNoise;
    private final double min;
    private final double max;
    private final int regionChunks;
    private final double refineWeight;

    private final Map<Long, Double> regionCache = new HashMap<>();
    private int coarseSamples;
    private int cacheHits;

    /**
     * @param min NUMBERS {@code ambient_qi_min}，必须 &gt; 0（05 §2）
     * @param max NUMBERS {@code ambient_qi_max}，必须 ≥ min
     * @param regionChunks NUMBERS {@code ambient_region_chunks}
     * @param refineWeight NUMBERS {@code ambient_refine_weight}，[0,1]
     */
    public AmbientQiField(
            Noise coarseNoise,
            Noise fineNoise,
            double min,
            double max,
            int regionChunks,
            double refineWeight) {
        if (!(min > 0.0)) {
            throw new IllegalArgumentException(
                    "ambient_qi_min must be > 0 (05 §2：0 系数必须有理由，NPC 无从解释) — got " + min);
        }
        if (!(max >= min)) {
            throw new IllegalArgumentException("ambient_qi_max " + max + " < min " + min);
        }
        if (regionChunks < 1) {
            throw new IllegalArgumentException(
                    "ambient_region_chunks must be >= 1 — got " + regionChunks);
        }
        if (!(refineWeight >= 0.0 && refineWeight <= 1.0)) {
            throw new IllegalArgumentException(
                    "ambient_refine_weight must be within [0,1] — got " + refineWeight);
        }
        this.coarseNoise = coarseNoise;
        this.fineNoise = fineNoise;
        this.min = min;
        this.max = max;
        this.regionChunks = regionChunks;
        this.refineWeight = refineWeight;
    }

    /** 区块坐标处的环境系数（乘进 05 §2 公式的那一项）。 */
    public double ratioAt(int chunkX, int chunkZ) {
        int regionX = Math.floorDiv(chunkX, regionChunks);
        int regionZ = Math.floorDiv(chunkZ, regionChunks);
        double coarse = coarseFor(regionX, regionZ);
        double fine = normalize(fineNoise.sample(chunkX + 0.5, 0.5, chunkZ + 0.5));
        double blended = coarse * (1.0 - refineWeight) + fine * refineWeight;
        return clamp01(blended) * (max - min) + min;
    }

    /** 方块坐标处的环境系数（同一区块内是同一个值——场是区域属性，不是逐方块属性）。 */
    public double ratioAtBlock(int blockX, int blockZ) {
        return ratioAt(
                Math.floorDiv(blockX, BLOCKS_PER_CHUNK), Math.floorDiv(blockZ, BLOCKS_PER_CHUNK));
    }

    public double min() {
        return min;
    }

    public double max() {
        return max;
    }

    /** 粗粒度噪声实际被采样过多少次（用例判"缓存命中 ≈0 成本"的证据）。 */
    public int coarseSamples() {
        return coarseSamples;
    }

    public int cacheHits() {
        return cacheHits;
    }

    public int cachedRegions() {
        return regionCache.size();
    }

    private double coarseFor(int regionX, int regionZ) {
        long key = ((long) regionX << 32) ^ (regionZ & 0xFFFFFFFFL);
        Double cached = regionCache.get(key);
        if (cached != null) {
            cacheHits++;
            return cached;
        }
        if (regionCache.size() >= MAX_CACHED_REGIONS) {
            regionCache.clear();
        }
        coarseSamples++;
        double value =
                normalize(
                        coarseNoise.sample(
                                regionX * (double) regionChunks + 0.5,
                                0.5,
                                regionZ * (double) regionChunks + 0.5));
        regionCache.put(key, value);
        return value;
    }

    private static double normalize(double noise) {
        return clamp01((noise + 1.0) * 0.5);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}

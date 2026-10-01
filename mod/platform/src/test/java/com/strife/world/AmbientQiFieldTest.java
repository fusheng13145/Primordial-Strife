package com.strife.world;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * 灵气场（docs/03 §6"区域粗粒度缓存 + 细化层"、§8 环境系数；NUMBERS @@world 的 ambient_* 四项）。
 *
 * <p>噪声以注入的假源替代 MC 的 ImprovedNoise：场的行为（上下限、区域粒度、缓存命中、非零）与噪声实现无关，用例只钉这些。
 */
class AmbientQiFieldTest {

    private static final double MIN = 0.50;
    private static final double MAX = 2.00;
    private static final int REGION = 16;

    /** 粗粒度随区域坐标变化（周期足够大，相邻区域值不同）；细化层随区块坐标变化。 */
    private static AmbientQiField field(double refineWeight) {
        AmbientQiField.Noise coarse = (x, y, z) -> Math.sin(x * 0.1);
        AmbientQiField.Noise fine = (x, y, z) -> Math.cos(z * 0.1);
        return new AmbientQiField(coarse, fine, MIN, MAX, REGION, refineWeight);
    }

    @Test
    void staysWithinTheConfiguredBandEverywhere() {
        AmbientQiField field = field(0.25);

        for (int chunkX = -600; chunkX <= 600; chunkX += 7) {
            for (int chunkZ = -600; chunkZ <= 600; chunkZ += 11) {
                double ratio = field.ratioAt(chunkX, chunkZ);
                assertTrue(
                        ratio >= MIN - 1e-9 && ratio <= MAX + 1e-9,
                        "环境系数越界：" + ratio + " @ " + chunkX + "," + chunkZ);
                assertTrue(ratio > 0.0, "05 §2：环境系数绝不为 0");
            }
        }
    }

    @Test
    void sameCoordinatesAlwaysGiveTheSameValue() {
        AmbientQiField first = field(0.25);
        AmbientQiField second = field(0.25);

        assertEquals(first.ratioAt(37, -91), second.ratioAt(37, -91), 1e-12);
    }

    @Test
    void blocksInsideOneChunkShareTheChunkValue() {
        AmbientQiField field = field(0.25);

        assertEquals(field.ratioAt(3, 4), field.ratioAtBlock(3 * 16 + 7, 4 * 16 + 15), 1e-12);
    }

    /** refineWeight = 0 时整片区域同值；区域之间必须不同，否则"灵气浓度场"名存实亡。 */
    @Test
    void zeroRefineWeightMakesTheWholeRegionUniform() {
        AmbientQiField field = field(0.0);

        double regionZero = field.ratioAt(0, 0);
        assertEquals(regionZero, field.ratioAt(15, 15), 1e-12, "同区域内的区块应当同值");
        assertNotEquals(regionZero, field.ratioAt(16, 0), 1e-9, "相邻区域应当不同");
    }

    @Test
    void refineWeightMakesNeighbouringChunksDifferInsideARegion() {
        AmbientQiField field = field(1.0);

        assertNotEquals(field.ratioAt(0, 0), field.ratioAt(1, 0), 1e-9);
    }

    /** 缓存判据：同一区域内重复取数只采样一次粗粒度噪声（03 §6"命中缓存 ≈0"）。 */
    @Test
    void coarseNoiseIsSampledOncePerRegion() {
        AtomicInteger samples = new AtomicInteger();
        AmbientQiField field =
                new AmbientQiField(
                        (x, y, z) -> {
                            samples.incrementAndGet();
                            return Math.sin(x * 0.1);
                        },
                        (x, y, z) -> 0.0,
                        MIN,
                        MAX,
                        REGION,
                        0.25);

        for (int i = 0; i < 50; i++) {
            field.ratioAt(i % REGION, i % REGION);
        }
        field.ratioAt(REGION + 1, 0);

        assertEquals(2, samples.get(), "两个区域各采样一次，其余走缓存");
        assertEquals(2, field.coarseSamples());
        assertEquals(49, field.cacheHits());
    }

    @Test
    void rejectsConfigurationThatWouldCreateADeadZone() {
        AmbientQiField.Noise zero = (x, y, z) -> 0.0;

        assertThrows(
                IllegalArgumentException.class,
                () -> new AmbientQiField(zero, zero, 0.0, MAX, REGION, 0.25),
                "ambient_qi_min = 0 必须拒绝：0 系数的区域玩家只会觉得练不动");
        assertThrows(
                IllegalArgumentException.class,
                () -> new AmbientQiField(zero, zero, 2.0, 0.5, REGION, 0.25),
                "max < min 是配置错误");
        assertThrows(
                IllegalArgumentException.class,
                () -> new AmbientQiField(zero, zero, MIN, MAX, 0, 0.25),
                "区域粒度必须 ≥ 1 区块");
        assertThrows(
                IllegalArgumentException.class,
                () -> new AmbientQiField(zero, zero, MIN, MAX, REGION, 1.5),
                "细化层权重必须落在 [0,1]");
        assertDoesNotThrow(() -> new AmbientQiField(zero, zero, MIN, MAX, REGION, 0.0));
    }
}

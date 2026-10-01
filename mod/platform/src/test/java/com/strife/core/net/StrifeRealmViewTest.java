package com.strife.core.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 面板视图的编解码与派生判定（docs/03 §5 拉取式）。
 *
 * <p>视图走 NBT 而不是逐字段编码，所以"加一个显示项忘了改对面"不会导致解包错位；用例钉的是往返一致与两个面板判定 （修为是否满、能否押注），因为按钮的置灰状态直接由它们决定。
 */
class StrifeRealmViewTest {

    private static StrifeRealmView sample() {
        return new StrifeRealmView(
                "qili",
                1,
                3,
                9,
                180,
                230,
                false,
                0.9,
                2,
                0.6,
                0.8,
                true,
                0,
                0.8,
                1.20,
                1.45,
                1.15,
                1.25,
                "tech_qingxin_jue",
                120L);
    }

    @Test
    void nbtRoundTripKeepsEveryField() {
        StrifeRealmView view = sample();

        assertEquals(view, StrifeRealmView.fromTag(view.toTag()));
    }

    @Test
    void missingFieldsDecodeToDefaultsInsteadOfFailing() {
        StrifeRealmView decoded = StrifeRealmView.fromTag(new net.minecraft.nbt.CompoundTag());

        assertEquals("", decoded.realmId());
        assertEquals(0, decoded.qiMax());
        assertEquals(0.0, decoded.successRate(), 1e-9);
        assertFalse(decoded.meditating());
        assertEquals(0L, decoded.lifespanYears());
        assertEquals(0.0, decoded.techniqueRatio(), 1e-9, "老客户端读新包时缺字段应为 0 而不是解析失败");
        assertEquals("", decoded.equippedTechnique());
    }

    @Test
    void canBreakthroughRequiresFullQiAndANextRealm() {
        StrifeRealmView notFull = sample();
        assertFalse(notFull.qiFull());
        assertFalse(notFull.canBreakthrough(), "修为未满不可押注");

        StrifeRealmView full =
                new StrifeRealmView(
                        "qili", 1, 9, 9, 230, 230, false, 0.9, 0, 0.6, 0.8, false, 0, 0.8, 1.20,
                        1.45, 1.15, 1.25, "", 120L);
        assertTrue(full.qiFull());
        assertTrue(full.canBreakthrough());

        StrifeRealmView finalRealm =
                new StrifeRealmView(
                        "dujie", 8, 1, 1, 67000, 67000, true, 0.0, 0, 0.6, 0.8, false, 0, 20.5,
                        1.40, 1.45, 1.0, 1.0, "", 12000L);
        assertTrue(finalRealm.qiFull());
        assertFalse(finalRealm.canBreakthrough(), "最高境界没有下一境可去");
    }

    /** qi 溢出上限（例如上限下调后旧档保留了大值）时，判定仍应是"可押注"而不是崩在比较上。 */
    @Test
    void qiAboveTheCeilingStillCountsAsFull() {
        StrifeRealmView overCap =
                new StrifeRealmView(
                        "qili", 1, 9, 9, 240, 230, false, 0.9, 0, 0.6, 0.8, false, 0, 0.8, 1.20,
                        1.45, 1.0, 1.0, "", 120L);

        assertTrue(overCap.qiFull());
        assertTrue(overCap.canBreakthrough());
    }
}

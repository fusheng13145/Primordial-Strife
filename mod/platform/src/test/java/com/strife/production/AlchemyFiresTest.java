package com.strife.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/**
 * 异火运行时表 ×【真实产物 fires.json】+ 品质偏置数学（ADR-038 丹药体系收口）。
 *
 * <p>契约：表值 = fires.csv 定稿值（零字面量落地的另一面——测试就是对产物的回归锚）； 品质偏置只重排产出分布（Σ守恒、失败质量不动、只动最低→最高品阶）。
 */
class AlchemyFiresTest {

    @Test
    void tableShipsAllSevenFiresWithContractValues() {
        var fires = AlchemyFires.snapshot();
        assertEquals(7, fires.size(), "fires.csv 七行（凡火+六异火）");

        assertEquals(0.03, fires.get("fire_dixin").successBonus(), 1e-9, "地心火成功率+3%");
        assertEquals(0.05, fires.get("fire_chiyan").qualityShift(), 1e-9, "赤炎品质偏置+5%");
        assertEquals(0.10, fires.get("fire_taiyang").qualityShift(), 1e-9, "太阳真火品质偏置+10%");
        assertEquals(0.05, fires.get("fire_xuanbing").wasteReduce(), 1e-9, "玄冰焰废丹回收5%");
        assertEquals(0.08, fires.get("fire_jiuyou").successBonus(), 1e-9, "九幽成功率+8%");
        assertEquals(0.05, fires.get("fire_mu").tolerance(), 1e-9, "木灵火容差+5%（契约承载未参与 roll）");
        assertEquals(0.0, fires.get("fire_normal").successBonus(), 1e-9, "凡火零加成");
    }

    @Test
    void itemIdMappingAndNormalFallback() {
        assertEquals("fire_chiyan", AlchemyFires.byItemId("item_fire_chiyan").id());
        assertNull(AlchemyFires.byItemId("item_yuejian"), "非火种物品不映射");
        assertNull(AlchemyFires.byItemId("item_fire_unknown"), "表外火种不映射（不猜默认值）");
        assertNull(AlchemyFires.byItemId(null));
        assertEquals(AlchemyFires.NORMAL, AlchemyFires.get("fire_normal"), "表内凡火与兜底常量值相等");
        assertNull(AlchemyFires.get("fire_nonexistent"));
    }

    // ═══ 品质偏置数学（qualityShifted 纯函数）═══

    private static AlchemyRecipe.Output out(String id, double prob, String quality) {
        return new AlchemyRecipe.Output(id, 1, prob, quality);
    }

    @Test
    void shiftMovesMassFromLowestToHighestTierOnly() {
        // di 0.85 / fan 0.15，shift 0.05：fan → 0.10，di → 0.90
        var pool = java.util.List.of(out("pill_a", 0.85, "di"), out("pill_b", 0.15, "fan"));
        var shifted = AlchemyRecipe.qualityShifted(pool, 0.05);
        assertEquals(0.90, shifted.get(0).prob(), 1e-9);
        assertEquals(0.10, shifted.get(1).prob(), 1e-9);
    }

    @Test
    void middleTierUntouchedAndSumConserved() {
        // tian 0.55 / di 0.30 / fan 0.15，shift 0.05：中间阶不动，Σ=1 构造性守恒
        var pool =
                java.util.List.of(
                        out("t", 0.55, "tian"), out("d", 0.30, "di"), out("f", 0.15, "fan"));
        var shifted = AlchemyRecipe.qualityShifted(pool, 0.05);
        assertEquals(0.60, shifted.get(0).prob(), 1e-9, "最高阶吸收");
        assertEquals(0.30, shifted.get(1).prob(), 1e-9, "中间阶不动");
        assertEquals(0.10, shifted.get(2).prob(), 1e-9, "最低阶让出");
        assertEquals(1.0, shifted.stream().mapToDouble(AlchemyRecipe.Output::prob).sum(), 1e-9);
    }

    @Test
    void failureMassIsNeverTouched() {
        // Σprob=0.6（失败 0.4）——偏置后失败质量必须原样
        var pool = java.util.List.of(out("hi", 0.4, "tian"), out("lo", 0.2, "fan"));
        var shifted = AlchemyRecipe.qualityShifted(pool, 0.05);
        assertEquals(0.6, shifted.stream().mapToDouble(AlchemyRecipe.Output::prob).sum(), 1e-9);
    }

    @Test
    void shiftCapsAtLowTierMass() {
        // fan 只剩 0.03 时 shift=0.10 也只能搬走 0.03，fan 清空、不越阶造负数
        var pool =
                java.util.List.of(
                        out("hi", 0.85, "tian"), out("lo", 0.03, "fan"), out("m", 0.12, "di"));
        var shifted = AlchemyRecipe.qualityShifted(pool, 0.10);
        assertEquals(0.0, shifted.get(1).prob(), 1e-9, "最低阶清空即封顶");
        assertEquals(0.88, shifted.get(0).prob(), 1e-9);
        assertEquals(1.0, shifted.stream().mapToDouble(AlchemyRecipe.Output::prob).sum(), 1e-9);
    }

    @Test
    void noOpCasesReturnIdentity() {
        var single = java.util.List.of(out("only", 1.0, "fan"));
        assertSame(single, AlchemyRecipe.qualityShifted(single, 0.05), "单产出无偏置");

        var sameTier = java.util.List.of(out("a", 0.6, "fan"), out("b", 0.4, "fan"));
        assertSame(sameTier, AlchemyRecipe.qualityShifted(sameTier, 0.05), "全同品阶无偏置");

        var unknown =
                java.util.List.of(out("a", 0.6, "di"), new AlchemyRecipe.Output("b", 1, 0.4, null));
        assertSame(unknown, AlchemyRecipe.qualityShifted(unknown, 0.05), "品阶未知 fail-soft");

        var pool = java.util.List.of(out("a", 0.85, "di"), out("b", 0.15, "fan"));
        assertSame(pool, AlchemyRecipe.qualityShifted(pool, 0.0), "零偏置原样");
        assertSame(pool, AlchemyRecipe.qualityShifted(pool, -0.05), "负偏置原样");
    }

    @Test
    void multiOutputSameTierScalesProportionally() {
        // 两个 fan 各 0.10，shift 0.05 → 各让出一半（0.05），按比例不偏袒
        var pool =
                java.util.List.of(
                        out("hi", 0.80, "di"), out("f1", 0.10, "fan"), out("f2", 0.10, "fan"));
        var shifted = AlchemyRecipe.qualityShifted(pool, 0.05);
        assertEquals(0.85, shifted.get(0).prob(), 1e-9);
        assertEquals(0.075, shifted.get(1).prob(), 1e-9);
        assertEquals(0.075, shifted.get(2).prob(), 1e-9);
    }

    /** 蒙特卡洛：赤炎(+0.05) 炼培元丹，di 频率与解析期望 0.90 对齐（±1% @ 20 万次）。 */
    @Test
    void monteCarloChiyanMatchesAnalyticExpectation() {
        var pool =
                java.util.List.of(out("pill_peiyuan", 0.85, "di"), out("pill_juqi", 0.15, "fan"));
        var shifted = AlchemyRecipe.qualityShifted(pool, 0.05);
        var rng = new java.util.Random(20261103);
        int trials = 200_000;
        int hits = 0;
        for (int i = 0; i < trials; i++) {
            var result = AlchemyRecipe.rollList(shifted, rng::nextInt);
            if (result.isPresent() && "di".equals(result.get().quality())) {
                hits++;
            }
        }
        double freq = hits / (double) trials;
        assertEquals(0.90, freq, 0.01, "赤炎偏置后 di 期望 0.90，实测 " + freq);
    }

    // ═══ 真实产物回归：全部丹方偏置后 Σ 不动 + 炉表值锚定 ═══

    /** 全部真实丹方 × 最大偏置（太阳真火 0.10）：Σprob 与品阶形状守恒——偏置不得造成任何丹方概率漂移。 */
    @Test
    void allRealRecipesKeepDistributionUnderMaxShift() {
        Alchemy.register();
        for (var recipe : Alchemy.recipes().values()) {
            var shifted = AlchemyRecipe.qualityShifted(recipe.outputs(), 0.10);
            assertEquals(
                    recipe.outputs().stream().mapToDouble(AlchemyRecipe.Output::prob).sum(),
                    shifted.stream().mapToDouble(AlchemyRecipe.Output::prob).sum(),
                    1e-9,
                    recipe.id() + " 偏置后 Σ 漂移");
        }
    }

    @Test
    void furnaceStatsMatchFurnacesJson() {
        var stats = StrifeAlchemyBlocks.furnaceStats();
        assertEquals(4, stats.size());
        var shi = stats.get("furnace_shi");
        assertNotNull(shi);
        assertEquals("青石炉", shi.name());
        assertEquals(0.00, shi.successBonus(), 1e-9);
        assertEquals("di", shi.qualityCap());
        assertEquals(0, shi.wasteRecycle());
        var long_ = stats.get("furnace_long");
        assertEquals(0.20, long_.successBonus(), 1e-9);
        assertEquals(2, long_.wasteRecycle());
        assertEquals(0.10, long_.doubleOutput(), 1e-9);
    }
}

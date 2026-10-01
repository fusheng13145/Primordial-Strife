package com.strife.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 四因子取数层（docs/05 §2 公式、03 §2 core 只读接口）。
 *
 * <p>注册是全局状态，所以每个用例后清空接线：否则"谁先注册"会变成用例之间的隐式依赖，而那种依赖在并行执行时表现为随机失败。
 */
class CultivationFactorsTest {

    @AfterEach
    void tearDown() {
        CultivationFactors.resetForTest();
    }

    @Test
    void everythingIsNeutralBeforeAnyModuleRegisters() {
        CultivationFactors.Coefficients coefficients = CultivationFactors.coefficients(null);

        assertEquals(1.0, coefficients.environment(), 1e-9);
        assertEquals(1.0, coefficients.technique(), 1e-9);
        assertEquals(1.0, coefficients.pill(), 1e-9);
        assertEquals(1.0, coefficients.product(), 1e-9);
        assertFalse(CultivationFactors.wiring().complete(), "三项都没接时不得声称已完整接线");
        assertTrue(CultivationFactors.wiring().describe().contains("environment=neutral"));
    }

    @Test
    void registeringOneFactorLeavesTheOthersNeutral() {
        CultivationFactors.registerEnvironment(player -> 1.45);

        CultivationFactors.Coefficients coefficients = CultivationFactors.coefficients(null);

        assertEquals(1.45, coefficients.environment(), 1e-9);
        assertEquals(1.0, coefficients.technique(), 1e-9, "world 不该顺手把功法倍率也填上");
        assertEquals(1.0, coefficients.pill(), 1e-9);
        assertTrue(CultivationFactors.wiring().environment());
        assertFalse(CultivationFactors.wiring().complete());
    }

    @Test
    void allThreeFactorsMultiplyIntoTheFormulaProduct() {
        CultivationFactors.registerEnvironment(player -> 1.5);
        CultivationFactors.registerTechnique(player -> 1.2);
        CultivationFactors.registerPill(player -> 1.25);

        CultivationFactors.Coefficients coefficients = CultivationFactors.coefficients(null);

        assertEquals(2.25, coefficients.product(), 1e-9);
        assertTrue(CultivationFactors.wiring().complete());
        assertTrue(CultivationFactors.wiring().describe().contains("technique=wired"));
    }

    @Test
    void duplicateRegistrationIsRejectedInsteadOfSilentlyOverriding() {
        CultivationFactors.registerEnvironment(player -> 1.2);

        assertThrows(
                IllegalStateException.class,
                () -> CultivationFactors.registerEnvironment(player -> 2.0),
                "两个模块抢同一项是设计错误；静默覆盖会让『谁生效』变成注册顺序问题");
    }

    @Test
    void nullSourceIsRejected() {
        assertThrows(
                IllegalArgumentException.class, () -> CultivationFactors.registerTechnique(null));
        assertThrows(IllegalArgumentException.class, () -> CultivationFactors.registerPill(null));
    }

    @Test
    void brokenProviderValuesFallBackToNeutral() {
        assertEquals(1.0, CultivationFactors.sanitize(0.0), 1e-9, "0 会让玩家打坐零收益且无从解释（05 §2）");
        assertEquals(1.0, CultivationFactors.sanitize(-2.0), 1e-9);
        assertEquals(1.0, CultivationFactors.sanitize(Double.NaN), 1e-9);
        assertEquals(1.0, CultivationFactors.sanitize(Double.POSITIVE_INFINITY), 1e-9);
        assertEquals(0.6, CultivationFactors.sanitize(0.6), 1e-9);
        assertEquals(2.0, CultivationFactors.sanitize(2.0), 1e-9);
    }

    @Test
    void brokenSourceValuesDoNotPoisonTheWholeProduct() {
        CultivationFactors.registerEnvironment(player -> 0.0);
        CultivationFactors.registerTechnique(player -> 1.2);
        CultivationFactors.registerPill(player -> Double.NaN);

        CultivationFactors.Coefficients coefficients = CultivationFactors.coefficients(null);

        assertEquals(1.2, coefficients.product(), 1e-9, "坏值回落成 1.0，好值照常相乘");
    }
}

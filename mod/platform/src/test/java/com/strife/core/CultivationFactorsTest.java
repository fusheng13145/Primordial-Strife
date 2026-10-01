package com.strife.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * 四因子取数层（docs/05 §2 公式、03 §2 core 只读接口）。
 *
 * <p>用例只钉两件与模块无关的事：中性默认值确实是 1.0（"没接入"不等于"某个编造值"），以及提供者给回的坏值（0 / NaN / 负数）会被挡下来而不是让玩家"打坐不涨修为"。
 */
class CultivationFactorsTest {

    @Test
    void neutralProviderKeepsEveryFactorAtOne() {
        CultivationFactors.Provider neutral = CultivationFactors.NEUTRAL;

        assertEquals(1.0, neutral.environment(null), 1e-9);
        assertEquals(1.0, neutral.technique(null), 1e-9);
        assertEquals(1.0, neutral.pill(null), 1e-9);
    }

    @Test
    void coefficientsMultiplyToTheFormulaProduct() {
        CultivationFactors.Coefficients coefficients =
                new CultivationFactors.Coefficients(1.5, 1.2, 1.25);

        assertEquals(2.25, coefficients.product(), 1e-9);
    }

    @Test
    void brokenProviderValuesFallBackToNeutral() {
        assertEquals(1.0, CultivationFactors.sanitize(0.0), 1e-9, "0 会让玩家打坐零收益且无从解释（05 §2）");
        assertEquals(1.0, CultivationFactors.sanitize(-2.0), 1e-9);
        assertEquals(1.0, CultivationFactors.sanitize(Double.NaN), 1e-9);
        assertEquals(1.0, CultivationFactors.sanitize(Double.POSITIVE_INFINITY), 1e-9);
    }

    @Test
    void validProviderValuesPassThrough() {
        assertEquals(0.6, CultivationFactors.sanitize(0.6), 1e-9);
        assertEquals(2.0, CultivationFactors.sanitize(2.0), 1e-9);
    }
}

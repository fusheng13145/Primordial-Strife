package com.strife.realm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * 五行生克与亲和（LORE §2.1 的关系表 + NUMBERS §6 的三档系数）。
 *
 * <p>用例把十条关系（五生五克）逐条钉住：这类表最容易出现"两行抄反"，而抄反的表现是玩家发现某两种属性莫名其妙特别慢。
 */
class FiveElementsTest {

    @Test
    void elementIdsAndBitsRoundTrip() {
        String[] ids = {"jin", "mu", "shui", "huo", "tu"};
        int[] bits = {
            FiveElements.JIN, FiveElements.MU, FiveElements.SHUI, FiveElements.HUO, FiveElements.TU
        };

        for (int i = 0; i < ids.length; i++) {
            assertEquals(bits[i], FiveElements.bitOf(ids[i]), ids[i]);
            assertEquals(ids[i], FiveElements.idOf(bits[i]), ids[i]);
        }
        assertEquals(FiveElements.NONE, FiveElements.bitOf("unknown"));
    }

    @Test
    void generationCycleIsJinShuiMuHuoTu() {
        assertEquals(FiveElements.SHUI, FiveElements.generatedBy(FiveElements.JIN));
        assertEquals(FiveElements.MU, FiveElements.generatedBy(FiveElements.SHUI));
        assertEquals(FiveElements.HUO, FiveElements.generatedBy(FiveElements.MU));
        assertEquals(FiveElements.TU, FiveElements.generatedBy(FiveElements.HUO));
        assertEquals(FiveElements.JIN, FiveElements.generatedBy(FiveElements.TU));
    }

    @Test
    void overcomingCycleIsJinMuTuShuiHuo() {
        assertEquals(FiveElements.MU, FiveElements.overcomeBy(FiveElements.JIN));
        assertEquals(FiveElements.TU, FiveElements.overcomeBy(FiveElements.MU));
        assertEquals(FiveElements.SHUI, FiveElements.overcomeBy(FiveElements.TU));
        assertEquals(FiveElements.HUO, FiveElements.overcomeBy(FiveElements.SHUI));
        assertEquals(FiveElements.JIN, FiveElements.overcomeBy(FiveElements.HUO));
    }

    @Test
    void sameElementIsMatched() {
        assertEquals(
                FiveElements.Affinity.MATCHED,
                FiveElements.affinity(FiveElements.JIN, FiveElements.JIN, null));
        assertEquals(
                FiveElements.Affinity.MATCHED,
                FiveElements.affinity(FiveElements.MU, FiveElements.JIN | FiveElements.MU, null),
                "灵根集合里有该属性即算同属");
    }

    @Test
    void overcomingInEitherDirectionIsConflict() {
        assertEquals(
                FiveElements.Affinity.CONFLICT,
                FiveElements.affinity(FiveElements.JIN, FiveElements.MU, null),
                "金克木：功法克灵根");
        assertEquals(
                FiveElements.Affinity.CONFLICT,
                FiveElements.affinity(FiveElements.MU, FiveElements.JIN, null),
                "金克木：灵根克功法，同样相克");
        assertEquals(
                FiveElements.Affinity.CONFLICT,
                FiveElements.affinity(FiveElements.HUO, FiveElements.JIN, null),
                "火克金");
    }

    @Test
    void generationAndUnrelatedPairsAreNeutral() {
        assertEquals(
                FiveElements.Affinity.NEUTRAL,
                FiveElements.affinity(FiveElements.JIN, FiveElements.SHUI, null),
                "金生水：相生只是不拖后腿，不给额外加成");
        assertEquals(
                FiveElements.Affinity.NEUTRAL,
                FiveElements.affinity(FiveElements.JIN, FiveElements.TU, null),
                "土生金：同样是相生，仍是中性");
    }

    @Test
    void conflictWinsOverGenerationWhenBothApply() {
        // 灵根同时含水（金生水）与木（金克木）：相克优先，否则"带一点相克属性"会被相生掩盖。
        assertEquals(
                FiveElements.Affinity.CONFLICT,
                FiveElements.affinity(FiveElements.JIN, FiveElements.SHUI | FiveElements.MU, null));
        // 灵根含火（火克金）与土（土生金）：同样是相克优先。
        assertEquals(
                FiveElements.Affinity.CONFLICT,
                FiveElements.affinity(FiveElements.JIN, FiveElements.HUO | FiveElements.TU, null));
    }

    @Test
    void missingInformationFallsBackInsteadOfGuessing() {
        assertEquals(
                FiveElements.Affinity.NEUTRAL,
                FiveElements.affinity(FiveElements.NONE, FiveElements.JIN, null),
                "功法无属性 → 中性");
        assertEquals(
                FiveElements.Affinity.MATCHED,
                FiveElements.affinity(
                        FiveElements.JIN, FiveElements.NONE, FiveElements.Affinity.MATCHED),
                "灵根未生成 → 用内容表声明的亲和兜底");
        assertEquals(
                FiveElements.Affinity.NEUTRAL,
                FiveElements.affinity(FiveElements.JIN, FiveElements.NONE, null),
                "既无灵根也无声明 → 中性");
    }

    @Test
    void coefficientUsesTheThreeNumbersTiers() {
        assertEquals(
                1.25,
                FiveElements.coefficient(FiveElements.Affinity.MATCHED, 1.25, 1.00, 0.60),
                1e-9);
        assertEquals(
                1.00,
                FiveElements.coefficient(FiveElements.Affinity.NEUTRAL, 1.25, 1.00, 0.60),
                1e-9);
        assertEquals(
                0.60,
                FiveElements.coefficient(FiveElements.Affinity.CONFLICT, 1.25, 1.00, 0.60),
                1e-9);
    }

    @Test
    void declaredRuleParsingIsTolerant() {
        assertEquals(FiveElements.Affinity.MATCHED, FiveElements.parseDeclared("matched"));
        assertEquals(FiveElements.Affinity.CONFLICT, FiveElements.parseDeclared("conflict"));
        assertEquals(FiveElements.Affinity.NEUTRAL, FiveElements.parseDeclared("neutral"));
        assertEquals(null, FiveElements.parseDeclared(""));
        assertEquals(null, FiveElements.parseDeclared(null));
        assertEquals(null, FiveElements.parseDeclared("nonsense"), "未知写法不猜，交给兜底");
    }
}

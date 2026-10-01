package com.strife.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 奖励缝的跨模块桥（docs/03 §2：quest 不能 import combat，所以奖励要靠 core 的注册口转交）。
 *
 * <p>关键口径：<b>没注册就是没有这条路</b>——调用返回 false 且不提供任何"默认实现"，因为默认实现只能是空操作， 而那正是"看起来发了其实没发"的来源。用例把这条钉住。
 */
class RewardBridgesTest {

    @AfterEach
    void tearDown() {
        RewardBridges.resetForTest();
    }

    @Test
    void withoutAGranterNothingIsGrantedAndTheBridgeSaysSo() {
        assertFalse(RewardBridges.techniqueWired());
        assertFalse(RewardBridges.spellWired());
        assertFalse(
                RewardBridges.grantTechnique(null, "tech_qingxin_jue"),
                "没有实现时不得假装发放成功——那会让任务看起来完成了而玩家手里什么都没有");
        assertFalse(RewardBridges.grantSpell(null, "spell_prologue_huodan"));
    }

    @Test
    void registeredGranterReceivesTheCall() {
        StringBuilder seen = new StringBuilder();
        RewardBridges.registerTechniqueGranter(
                (player, id) -> {
                    seen.append(id);
                    return true;
                });

        assertTrue(RewardBridges.techniqueWired());
        assertTrue(RewardBridges.grantTechnique(null, "tech_qingxin_jue"));
        assertTrue(seen.toString().contains("tech_qingxin_jue"));
    }

    @Test
    void aGranterThatRefusesIsReportedAsFailure() {
        RewardBridges.registerTechniqueGranter((player, id) -> false);

        assertFalse(
                RewardBridges.grantTechnique(null, "tech_qingxin_jue"), "门禁没过时调用方必须能区分『发了』与『没发』");
    }

    @Test
    void duplicateRegistrationIsRejected() {
        RewardBridges.registerSpellGranter((player, id) -> true);

        assertThrows(
                IllegalStateException.class,
                () -> RewardBridges.registerSpellGranter((player, id) -> true));
    }

    @Test
    void nullGranterIsRejected() {
        assertThrows(
                IllegalArgumentException.class, () -> RewardBridges.registerTechniqueGranter(null));
        assertThrows(
                IllegalArgumentException.class, () -> RewardBridges.registerSpellGranter(null));
    }
}

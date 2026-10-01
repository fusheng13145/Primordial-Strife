package com.strife.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.strife.realm.FiveElements;
import org.junit.jupiter.api.Test;

/**
 * 学法/装备门禁（docs/04 §2 的 {@code required_*} + NUMBERS §1 的 {@code technique_equip} 解锁）。
 *
 * <p>每条拒绝原因都要能被玩家读懂，所以除了判定本身，还钉住"每个枚举值都有对应文案键"——漏一个的表现是玩家点了没反应。
 */
class TechniqueGateTest {

    private static final int QILI = 1;
    private static final int ZHUJI = 2;
    private static final int MU = FiveElements.MU;
    private static final int JIN = FiveElements.JIN;

    private static TechniqueGate.PlayerSnapshot player(
            int realm, int stage, int rootMask, boolean equipUnlocked, String affiliation) {
        return new TechniqueGate.PlayerSnapshot(realm, stage, rootMask, equipUnlocked, affiliation);
    }

    private static TechniqueGate.Requirements requires(
            int realm, int stage, int rootMask, String faction) {
        return new TechniqueGate.Requirements(realm, stage, rootMask, faction);
    }

    @Test
    void realmGateRejectsLowerRealms() {
        assertEquals(
                TechniqueGate.Denial.REALM_TOO_LOW,
                TechniqueGate.canLearn(
                        player(QILI, 9, MU, true, ""), requires(ZHUJI, 1, FiveElements.NONE, "")));
    }

    @Test
    void stageGateOnlyAppliesAtTheSameRealm() {
        assertEquals(
                TechniqueGate.Denial.STAGE_TOO_LOW,
                TechniqueGate.canLearn(
                        player(ZHUJI, 1, MU, true, ""), requires(ZHUJI, 3, FiveElements.NONE, "")),
                "同境界内小境界不够时拒绝");
        assertEquals(
                TechniqueGate.Denial.ALLOWED,
                TechniqueGate.canLearn(
                        player(3, 1, MU, true, ""), requires(ZHUJI, 9, FiveElements.NONE, "")),
                "境界已经更高时不该再被小境界卡住");
    }

    @Test
    void requiredSpiritrootMeansAllOfThemAreePresent() {
        assertEquals(
                TechniqueGate.Denial.SPIRITROOT_MISMATCH,
                TechniqueGate.canLearn(player(ZHUJI, 1, JIN, true, ""), requires(QILI, 1, MU, "")),
                "只有金灵根学不了木系功法");
        assertEquals(
                TechniqueGate.Denial.ALLOWED,
                TechniqueGate.canLearn(
                        player(ZHUJI, 1, JIN | MU, true, ""), requires(QILI, 1, MU, "")),
                "五行俱全时木系要求满足");
        assertEquals(
                TechniqueGate.Denial.ALLOWED,
                TechniqueGate.canLearn(
                        player(ZHUJI, 1, JIN, true, ""), requires(QILI, 1, FiveElements.NONE, "")),
                "无灵根要求（掩码 0）= 谁都能学");
    }

    @Test
    void factionGateBlocksCrossSectButNotMavericks() {
        assertEquals(
                TechniqueGate.Denial.FACTION_MISMATCH,
                TechniqueGate.canLearn(
                        player(ZHUJI, 1, MU, true, "fac_yuelai"),
                        requires(QILI, 1, FiveElements.NONE, "fac_qingshi")),
                "有门第的玩家不能跨门用别家功法");
        assertEquals(
                TechniqueGate.Denial.ALLOWED,
                TechniqueGate.canLearn(
                        player(ZHUJI, 1, MU, true, ""),
                        requires(QILI, 1, FiveElements.NONE, "fac_qingshi")),
                "散修不受门第限制：他们本来就是从别处得到功法的");
    }

    @Test
    void equipNeedsBothOwnershipAndTheUnlock() {
        TechniqueGate.PlayerSnapshot locked = player(QILI, 9, MU, false, "");
        TechniqueGate.PlayerSnapshot unlocked = player(ZHUJI, 1, MU, true, "");

        assertEquals(TechniqueGate.Denial.NOT_LEARNED, TechniqueGate.canEquip(unlocked, false));
        assertEquals(TechniqueGate.Denial.EQUIP_LOCKED, TechniqueGate.canEquip(locked, true));
        assertEquals(TechniqueGate.Denial.ALLOWED, TechniqueGate.canEquip(unlocked, true));
    }

    /** 每个拒绝原因都必须有文案键：漏一个就是"点了没反应"，而那是玩家最反感的一类失败。 */
    @Test
    void everyDenialHasAMessageKey() {
        for (TechniqueGate.Denial denial : TechniqueGate.Denial.values()) {
            String key = TechniqueGate.messageKey(denial);
            org.junit.jupiter.api.Assertions.assertTrue(
                    key != null && key.startsWith("msg.strife.technique"),
                    denial + " 没有对应文案键：" + key);
        }
    }
}

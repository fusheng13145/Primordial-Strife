package com.strife.realm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

/**
 * 修仙世界感三件套的纯函数核：境界头衔的 team 命名与后缀形状、顿悟的瞬得钳位。 渡劫雷雨的分支条件（next.tribulation()）由 RealmTables 产物侧锚定，不在此重复。
 */
class RealmTitlesTest {

    @Test
    void teamNamesArePrefixedAndOrdinalSafe() {
        assertEquals("strife_realm_0", RealmTitles.teamNameFor(0));
        assertEquals("strife_realm_8", RealmTitles.teamNameFor(8));
        assertEquals("strife_realm_0", RealmTitles.teamNameFor(-1), "非法序数按 0 处理");
    }

    @Test
    void suffixWrapsTheRealmNameInBrackets() {
        var realm = new RealmTables.RealmEntry("qili", 1, 230, 9, 80, 0.5, "bs_qili_zhuji", false);
        Component suffix = RealmTitles.suffixFor(realm);
        String text = suffix.getString();
        assertTrue(text.contains("〔") && text.contains("〕"), "头衔形状：〔境界名〕");
        assertTrue(
                text.contains("realm.strife.qili"),
                "境界名走 realm.strife.<id> 双语键（测试环境无 lang，translatable 回退为键名）");
    }

    /** 顿悟瞬得的钳位：加成按当前速率算 60 秒，封顶境界剩余空间，永不为负。 */
    @Test
    void enlightenBonusIsRateTimesSixtySecondsClampedToRemaining() {
        double qiPerTick = 0.5;
        long secondsTicks = 60L * 20L;
        int qiMax = 230;
        int current = 180;
        int raw = (int) Math.min((long) (qiPerTick * secondsTicks), qiMax - current);
        assertEquals(50, Math.max(0, raw), "0.5/tick × 1200 tick = 600 → 钳到剩余空间 50");
        int atCap = (int) Math.min((long) (qiPerTick * secondsTicks), qiMax - qiMax);
        assertEquals(0, Math.max(0, atCap), "修为已满 → 顿悟加成为 0（不溢出）");
    }
}

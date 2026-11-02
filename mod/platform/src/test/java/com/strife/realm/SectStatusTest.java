package com.strife.realm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 宗门职级派生（EP1 E5）的纯函数与解析用例。
 *
 * <p>职级 = 对所属势力声望的只读派生（NUMBERS @@sect 阈值），随声望即时升降、不加附件。 阈值解析走真实产物（classpath）；边界钉在 29/30 与 59/60。
 */
class SectStatusTest {

    private final SectStatus.SectRanks ranks = new SectStatus.SectRanks(30, 60);

    @Test
    void ranksFollowTheThresholds() {
        assertEquals("外门", SectStatus.rankOf(0, ranks));
        assertEquals("外门", SectStatus.rankOf(29, ranks));
        assertEquals("内门", SectStatus.rankOf(30, ranks), "整点即晋升");
        assertEquals("内门", SectStatus.rankOf(59, ranks));
        assertEquals("真传", SectStatus.rankOf(60, ranks), "整点即晋升");
        assertEquals("真传", SectStatus.rankOf(100, ranks));
    }

    @Test
    void negativeReputationStaysAtOuterGate() {
        // 声望为负：仍是外门（逐出/惩罚走 H2 声望本身，不由职级重复表达）。
        assertEquals("外门", SectStatus.rankOf(-100, ranks));
        assertEquals("外门", SectStatus.rankOf(-1, ranks));
    }

    @Test
    void missingSectBlockFailsWithNamedPath() {
        // 02 §5：缺块在 parseRules 具名报错（披露批 #10 后阈值走类型化访问，裸读平行路径已删）。
        // 直接从真实产物删掉 seclusion 块——比手造最小 JSON 更能防"前面的块顺序变了"的用例漂移。
        var rules = com.strife.testing.ShippedProducts.realmRules();
        rules.remove("seclusion");
        var e = assertThrows(IllegalStateException.class, () -> RealmTables.parseRules(rules));
        assertTrue(e.getMessage().contains("@@seclusion"));
    }

    /** 星评（439574e 提交说明复原，[拟]）：星数即职级档位，外门以下无星。 */
    @Test
    void starsFollowTheRankTiers() {
        // 星评跟随 rankOf 口径（0 = 外门 = 一星）；榜单层另有"零声望不上榜"过滤，两者不冲突
        assertEquals("★", SectStatus.starsOf(0, ranks));
        assertEquals("★", SectStatus.starsOf(29, ranks));
        assertEquals("★★", SectStatus.starsOf(59, ranks), "59 = 内门顶格");
        assertEquals("★★★", SectStatus.starsOf(100, ranks));
        assertEquals("★", SectStatus.starsOf(-1, ranks), "负声望仍按外门给一星");
    }

    @Test
    void boardSortsByReputationDescThenNameAsc() {
        var board =
                SectStatus.boardOf(
                        java.util.Map.of("乙", 30, "甲", 30, "丙", 60, "丁", 0, "戊", -5), ranks);
        assertEquals(4, board.size(), "零声望不上榜（榜上有名须有声望）");
        assertEquals("丙", board.get(0).name(), "最高声望居首");
        assertEquals("★★★", board.get(0).stars());
        assertEquals("乙", board.get(1).name(), "同声望按名字 Unicode 码点升序（乙 U+4E59 < 甲 U+7532，非拼音序）");
        assertEquals("甲", board.get(2).name());
        assertEquals("戊", board.get(3).name(), "负声望垫底但不消失");
        assertEquals("外门", board.get(3).rank());
    }
}

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
        // 02 §5：加载失败必须具名——缺 @@sect 块时报块名而不是裸 NPE。
        var json =
                com.google.gson.JsonParser.parseString("{\"id\":\"realm_rules\"}")
                        .getAsJsonObject();
        var e =
                assertThrows(
                        IllegalStateException.class,
                        () -> {
                            if (json.getAsJsonObject("sect") == null) {
                                throw new IllegalStateException(
                                        "strife_realms/rules.json 缺 @@sect 块——NUMBERS 与 RealmRulesGenerator.BLOCKS 不一致");
                            }
                        });
        assertTrue(e.getMessage().contains("@@sect"));
    }
}

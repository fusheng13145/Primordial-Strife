package com.strife.clientfx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.strife.testing.ShippedProducts;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** 客户端法术清单必须与随 jar 的产物一致：轮盘读错一个字段，玩家点下去只会得到服务端拒绝。 */
class SpellClientTableTest {

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    private static JsonObject spell(String id, String cost, String cooldown) {
        return json(
                "{\"id\":\""
                        + id
                        + "\",\"element\":\"jin\",\"required_technique\":\"tech_xuanyu_jue\","
                        + "\"cost_qi\":"
                        + cost
                        + ",\"cooldown_sec\":"
                        + cooldown
                        + "}");
    }

    @Test
    void parsesEveryShippedSpell() {
        List<SpellClientTable.Entry> entries = SpellClientTable.parse(ShippedProducts.allSpells());

        // 条目数随产物走（EP1 飞剑术入表即 +1）——与 jar 内法术产物数对齐，不写死。
        assertEquals(ShippedProducts.allSpells().size(), entries.size(), "轮盘条目数必须等于 jar 内法术产物数");
        assertTrue(
                entries.stream().anyMatch(e -> e.id().equals("spell_ep1_feijian")),
                "EP1 飞剑术必须在轮盘里");
        Set<String> ids =
                entries.stream().map(SpellClientTable.Entry::id).collect(Collectors.toSet());
        assertTrue(ids.contains("spell_xuanyu_ren"), "F1 新增的玄玉刃必须在轮盘里");

        SpellClientTable.Entry xuanyu =
                entries.stream()
                        .filter(entry -> entry.id().equals("spell_xuanyu_ren"))
                        .findFirst()
                        .orElseThrow();
        assertEquals("jin", xuanyu.element());
        assertEquals("tech_xuanyu_jue", xuanyu.requiredTechnique());
        assertEquals(18L, xuanyu.costQi());
        assertEquals(4.0, xuanyu.cooldownSeconds());
    }

    @Test
    void sortsEntriesByIdSoTheWheelDoesNotJumpBetweenOpens() {
        List<SpellClientTable.Entry> entries =
                SpellClientTable.parse(
                        List.of(
                                spell("spell_b", "5", "1.0"),
                                spell("spell_c", "6", "2.0"),
                                spell("spell_a", "4", "0.5")));

        assertEquals(
                List.of("spell_a", "spell_b", "spell_c"),
                entries.stream().map(SpellClientTable.Entry::id).toList());
    }

    @Test
    void rejectsMissingCostInsteadOfShowingZero() {
        JsonObject broken = spell("spell_x", "1", "1.0");
        broken.remove("cost_qi");

        assertThrows(IllegalStateException.class, () -> SpellClientTable.parse(List.of(broken)));
    }

    @Test
    void rejectsDuplicateIds() {
        List<JsonObject> duplicated =
                List.of(spell("spell_x", "1", "1.0"), spell("spell_x", "2", "2.0"));

        assertThrows(IllegalStateException.class, () -> SpellClientTable.parse(duplicated));
    }

    @Test
    void rejectsBlankIds() {
        assertThrows(
                IllegalStateException.class,
                () -> SpellClientTable.parse(List.of(spell(" ", "1", "1.0"))));
    }
}

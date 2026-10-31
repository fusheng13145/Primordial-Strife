package com.strife.tools.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link NpcGenerator} 的契约用例：一行 NPC 摆放 → 一份 {@code strife_npcs} 产物，坐标为绝对格坐标， 供 quest
 * 侧按玩家邻近生成（世界生成布点仍归 M4）。
 */
class NpcGeneratorTest {

    private static TableSource.Record row(Map<String, String> values) {
        return new TableSource.Record("npcs.csv", values, 2);
    }

    private static TableSource source(Map<String, String> values) {
        return new TableSource(
                "npcs.csv",
                List.of("id", "dimension", "place", "x", "z"),
                List.of(row(values)),
                "sha256");
    }

    private static Map<String, String> valid() {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("id", "npc_qingshi_zhizhi");
        v.put("dimension", "minecraft:overworld");
        v.put("place", "qi_luoxia");
        v.put("x", "258");
        v.put("z", "252");
        return v;
    }

    @Test
    void tableFileIsThePlacementTable() {
        assertEquals("npcs.csv", new NpcGenerator().tableFile());
    }

    @Test
    void producesOneProductPerRowWithStableKeyOrder() {
        Product product = new NpcGenerator().generate(source(valid())).get(0);

        assertEquals("data/strife/strife_npcs/npc_qingshi_zhizhi.json", product.relativePath());
        String json = product.toJson();
        String expectedShape =
                "  \"id\": \"npc_qingshi_zhizhi\",\n"
                        + "  \"dimension\": \"minecraft:overworld\",\n"
                        + "  \"place\": \"qi_luoxia\",\n"
                        + "  \"x\": 258,\n"
                        + "  \"z\": 252";
        assertTrue(json.contains(expectedShape), "键序不符合插入序：\n" + json);
        assertTrue(json.contains("\"@generated\": \"from tables/npcs.csv @ sha256:"), json);
    }

    @Test
    void rejectsIdsThatAreNotNpcContentIds() {
        Map<String, String> v = valid();
        v.put("id", "qingshi_zhizhi");

        assertThrows(IllegalStateException.class, () -> new NpcGenerator().generate(source(v)));
    }

    @Test
    void rejectsAMissingPlace() {
        Map<String, String> v = valid();
        v.put("place", "");

        assertThrows(IllegalStateException.class, () -> new NpcGenerator().generate(source(v)));
    }

    @Test
    void rejectsCoordinatesThatAreNotIntegers() {
        Map<String, String> v = valid();
        v.put("x", "258.5");

        assertThrows(IllegalStateException.class, () -> new NpcGenerator().generate(source(v)));
    }

    @Test
    void rejectsUnknownDimensionNamespaces() {
        Map<String, String> v = valid();
        v.put("dimension", "custom:overworld");

        assertThrows(IllegalStateException.class, () -> new NpcGenerator().generate(source(v)));
    }
}

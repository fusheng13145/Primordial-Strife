package com.strife.tools.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link PlaceGenerator} 的契约用例：键序确定性（04 §5）、字段校验红脸（构建期报错比运行时静默退化强）。
 *
 * <p>不跑整条 DataGen，直接喂 {@link TableSource}——生成逻辑与产物形状的解耦断言，比端到端更快也更聚焦。
 */
class PlaceGeneratorTest {

    private static TableSource.Record row(Map<String, String> values) {
        return new TableSource.Record("places.csv", values, 2);
    }

    private static TableSource source(Map<String, String> values) {
        List<String> columns =
                List.of("id", "dimension", "name", "x", "y", "z", "radius", "qi_scale", "chapter");
        return new TableSource("places.csv", columns, List.of(row(values)), "sha256");
    }

    private static Map<String, String> valid() {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("id", "qi_luoxia");
        v.put("dimension", "minecraft:overworld");
        v.put("name", "落霞山麓");
        v.put("x", "256");
        v.put("y", "70");
        v.put("z", "256");
        v.put("radius", "320");
        v.put("qi_scale", "0.9");
        v.put("chapter", "prologue");
        return v;
    }

    @Test
    void producesOneJsonPerRowWithStableKeyOrder() {
        Product product = new PlaceGenerator().generate(source(valid())).get(0);
        assertEquals("data/strife/strife_places/qi_luoxia.json", product.relativePath());

        String json = product.toJson();
        // 键序必须固定（DataGenDeterminismTest 拦住 Map.of，这里确认插入序被保留）：
        // id → dimension → name → x → y → z → radius → qi_scale → chapter
        String expectedShape =
                "  \"id\": \"qi_luoxia\",\n"
                        + "  \"dimension\": \"minecraft:overworld\",\n"
                        + "  \"name\": \"落霞山麓\",\n"
                        + "  \"x\": 256,\n"
                        + "  \"y\": 70,\n"
                        + "  \"z\": 256,\n"
                        + "  \"radius\": 320,\n"
                        + "  \"qi_scale\": 0.9,\n"
                        + "  \"chapter\": \"prologue\"";
        assertTrue(json.contains(expectedShape), "键序不符合插入序：\n" + json);
    }

    @Test
    void chapterOmittedWhenBlank() {
        Map<String, String> v = valid();
        v.put("chapter", "");
        String json = new PlaceGenerator().generate(source(v)).get(0).toJson();
        assertTrue(json.contains("\"qi_scale\": 0.9"), json);
        assertTrue(!json.contains("chapter"), "chapter 留空应从产物中省略：\n" + json);
    }

    @Test
    void rejectsNonSnakeCaseId() {
        Map<String, String> v = valid();
        v.put("id", "落霞");
        assertThrows(IllegalStateException.class, () -> new PlaceGenerator().generate(source(v)));
    }

    @Test
    void rejectsUnknownDimensionNamespace() {
        Map<String, String> v = valid();
        v.put("dimension", "custom:overworld");
        assertThrows(IllegalStateException.class, () -> new PlaceGenerator().generate(source(v)));
    }

    @Test
    void rejectsNonPositiveQiScale() {
        Map<String, String> v = valid();
        v.put("qi_scale", "0");
        assertThrows(IllegalStateException.class, () -> new PlaceGenerator().generate(source(v)));
    }

    @Test
    void rejectsNonPositiveRadius() {
        Map<String, String> v = valid();
        v.put("radius", "0");
        assertThrows(IllegalStateException.class, () -> new PlaceGenerator().generate(source(v)));
    }

    @Test
    void rejectsNonIntegerCoordinates() {
        Map<String, String> v = valid();
        v.put("x", "abc");
        assertThrows(IllegalStateException.class, () -> new PlaceGenerator().generate(source(v)));
    }
}

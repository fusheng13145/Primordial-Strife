package com.strife.tools.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProductTest {

    private static Map<String, Object> fields() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("id", "fac_qingshi");
        fields.put("display_name_key", "faction.strife.fac_qingshi");
        fields.put("home_region", null);
        fields.put("relations", Map.of("fac_yuelai", "-80"));
        fields.put("tags", List.of("orthodox", "sword"));
        return fields;
    }

    @Test
    void headerComesFirstAndFieldOrderIsPreserved() {
        Product product =
                new Product("data/strife/strife_factions/fac_qingshi.json", fields(), "h");

        String json = product.toJson();

        // Asserted on the bytes, not through a parser: the key order is the point here.
        List<String> expectedOrder =
                List.of("@generated", "id", "display_name_key", "home_region", "relations", "tags");
        List<Integer> positions =
                expectedOrder.stream().map(key -> json.indexOf('"' + key + '"')).toList();

        assertTrue(positions.stream().allMatch(i -> i >= 0), "missing key in:\n" + json);
        assertEquals(
                positions.stream().sorted().toList(),
                positions,
                "product keys must appear in generator order for stable diffs (04 §5):\n" + json);
        assertEquals(0, json.indexOf("{\n  \"@generated\""), json);
    }

    @Test
    void writesMandatoryNullsExplicitlySoAbsenceIsNotSilent() {
        Product product = new Product("p", fields(), "h");

        JsonObject object = JsonParser.parseString(product.toJson()).getAsJsonObject();

        assertTrue(object.has("home_region"), "null must survive into the product");
        assertTrue(object.get("home_region").isJsonNull(), object.toString());
        assertEquals("orthodox", object.getAsJsonArray("tags").get(0).getAsString());
        assertEquals("-80", object.getAsJsonObject("relations").get("fac_yuelai").getAsString());
    }

    @Test
    void emitsTheSameBytesForTheSameInput() {
        Product product = new Product("p", fields(), "h");

        assertEquals(product.toJson(), product.toJson());
    }

    @Test
    void escapesTextThatWouldOtherwiseBreakTheDocument() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("note", "他说\"行\"\n下一行 \\ 反斜杠");

        String json = new Product("p", fields, "h").toJson();

        assertEquals(
                "他说\"行\"\n下一行 \\ 反斜杠",
                JsonParser.parseString(json).getAsJsonObject().get("note").getAsString(),
                json);
    }

    @Test
    void rejectsValueTypesItCannotWrite() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("when", new java.util.Date(0));

        IllegalStateException error =
                assertThrows(
                        IllegalStateException.class, () -> new Product("p", fields, "h").toJson());

        assertTrue(error.getMessage().contains("cannot serialize"), error.getMessage());
    }
}

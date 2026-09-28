package com.strife.tools.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RealmsGeneratorTest {

    private static final String NUMBERS =
            """
            @@realms
            ```yaml
            fanren:   { qi_max: 100,   stage_count: 1, lifespan_years: 80,   sit_rate: 0.50,  unlocks: [], breakthrough_success_key: bs_fanren_qili } # [拟]
            yuanying: { qi_max: 2650,  stage_count: 4, lifespan_years: 800,  sit_rate: 3.20,  unlocks: [tribulation, sect_join], breakthrough_success_key: bs_yuanying_huashen } # [拟]
            lianxu:   { qi_max: 13500, stage_count: 4, lifespan_years: 3000, sit_rate: 8.20,  unlocks: [unlock_placeholder_1], breakthrough_success_key: bs_placeholder_high_1 } # [拟][占位]
            ```
            """;

    private static NumbersSource numbers(Path root) throws IOException {
        Path content = root.resolve("content");
        Files.createDirectories(content);
        Files.writeString(content.resolve("NUMBERS.md"), NUMBERS);
        return NumbersSource.at(content);
    }

    @Test
    void writesOneProductPerRealmWithAllDerivedFields(@TempDir Path root) throws IOException {
        List<Product> products = new RealmsGenerator().generate(numbers(root));

        assertEquals(3, products.size());
        JsonObject fanren = JsonParser.parseString(products.get(0).toJson()).getAsJsonObject();
        JsonObject yuanying = JsonParser.parseString(products.get(1).toJson()).getAsJsonObject();
        JsonObject lianxu = JsonParser.parseString(products.get(2).toJson()).getAsJsonObject();

        assertEquals("data/strife/strife_realms/fanren.json", products.get(0).relativePath());
        assertEquals(0, fanren.get("ordinal").getAsInt(), "ordinal = written order (链序即真相)");
        assertEquals(1, yuanying.get("ordinal").getAsInt());
        assertEquals(2, lianxu.get("ordinal").getAsInt());
        assertTrue(
                yuanying.get("tribulation").getAsBoolean(),
                "tribulation derives from unlocks containing 'tribulation' (§4.1)");
        assertTrue(!fanren.get("tribulation").getAsBoolean());
        assertEquals("realm.strife.fanren", fanren.get("display_name_key").getAsString());
        assertTrue(
                lianxu.get("disabled_by_placeholder").getAsBoolean(),
                "the inline [占位] comment marker drives the placeholder flag");
        assertTrue(!fanren.get("disabled_by_placeholder").getAsBoolean());
        assertEquals(100, fanren.get("qi_max").getAsInt());
        assertEquals(0.50, fanren.get("sit_rate").getAsDouble(), 1e-9);
        assertTrue(
                products.get(0).generatedHeader().startsWith("from content/NUMBERS.md @ sha256:"),
                products.get(0).generatedHeader());
    }

    @Test
    void aRealmMissingARequiredFieldStopsTheRun(@TempDir Path root) throws IOException {
        Path content = root.resolve("content");
        Files.createDirectories(content);
        Files.writeString(content.resolve("NUMBERS.md"), NUMBERS.replace("sit_rate: 0.50,  ", ""));

        IllegalStateException error =
                assertThrows(
                        IllegalStateException.class,
                        () -> new RealmsGenerator().generate(NumbersSource.at(content)));

        assertTrue(error.getMessage().contains("sit_rate"), error.getMessage());
        assertTrue(error.getMessage().contains("fanren"), error.getMessage());
    }
}

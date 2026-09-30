package com.strife.tools.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldRulesGeneratorTest {

    @TempDir Path temp;

    @Test
    void emitsWorldBlockUnderStrifeWorldgenDomain() {
        NumbersSource numbers = numbersWithWorldBlock();
        List<Product> products = new WorldRulesGenerator().generate(numbers);
        assertEquals(1, products.size());
        Product product = products.get(0);
        assertEquals("data/strife/strife_worldgen/rules.json", product.relativePath());
        String json = product.toJson();
        assertTrue(json.contains("\"id\": \"world_rules\""), json);
        assertTrue(json.contains("\"herb_grass_drop_prob\""), json);
        assertTrue(json.contains("0.12"), json);
        assertTrue(json.contains("\"@generated\": \"from content/NUMBERS.md @ sha256:"), json);
    }

    @Test
    void failsLoudlyWhenWorldBlockMissing() {
        NumbersSource numbers = NumbersSource.at(temp);
        IllegalStateException e =
                assertThrows(
                        IllegalStateException.class,
                        () -> new WorldRulesGenerator().generate(numbers));
        assertTrue(e.getMessage().contains("@@world"), e.getMessage());
    }

    private NumbersSource numbersWithWorldBlock() {
        Path content = temp;
        try {
            Files.writeString(
                    content.resolve("NUMBERS.md"),
                    "@@world\n"
                            + "```yaml\n"
                            + "herb_grass_drop_prob: { item_ningxu: 0.12, item_duanxue: 0.05 }\n"
                            + "```\n");
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        return NumbersSource.at(content);
    }
}

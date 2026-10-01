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

    /**
     * The block is what the generator needs, so its absence must be reported by name. The truth
     * source exists here and simply carries another block — writing no file at all would take the
     * "file not found" branch instead and this test would assert against the wrong error (which is
     * exactly what it did before: it never created NUMBERS.md, so the message was always
     * "content/NUMBERS.md not found …" and the assertion could never hold).
     */
    @Test
    void failsLoudlyWhenWorldBlockMissing() {
        writeNumbers("@@meditation\n```yaml\ntick_interval_ticks: 40\n```\n");
        NumbersSource numbers = NumbersSource.at(temp);
        IllegalStateException e =
                assertThrows(
                        IllegalStateException.class,
                        () -> new WorldRulesGenerator().generate(numbers));
        assertTrue(e.getMessage().contains("@@world"), e.getMessage());
    }

    /** The other half of the same contract: no truth source at all names the file, not a block. */
    @Test
    void failsLoudlyWhenTruthSourceIsMissing() {
        NumbersSource numbers = NumbersSource.at(temp);
        IllegalStateException e =
                assertThrows(
                        IllegalStateException.class,
                        () -> new WorldRulesGenerator().generate(numbers));
        assertTrue(e.getMessage().contains("NUMBERS.md"), e.getMessage());
    }

    private NumbersSource numbersWithWorldBlock() {
        writeNumbers(
                "@@world\n"
                        + "```yaml\n"
                        + "herb_grass_drop_prob: { item_ningxu: 0.12, item_duanxue: 0.05 }\n"
                        + "```\n");
        return NumbersSource.at(temp);
    }

    private void writeNumbers(String body) {
        try {
            Files.writeString(temp.resolve("NUMBERS.md"), body);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}

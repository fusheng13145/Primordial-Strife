package com.strife.tools.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NumbersSourceTest {

    private static final String NUMBERS =
            """
            # NUMBERS.md — truth source
            ## 1. prose between blocks is ignored 链序：凡人 → 练气

            @@limits
            ```yaml
            growth_ratio_min: 1.8        # [锚] 下限
            budget_bytes: 2048
            ```

            @@combat
            ```yaml
            spell_cost_qi:      { light: 6,  medium: 18, heavy: 45 }        # [拟]
            spell_cooldown_sec: { light: 1.0, medium: 4.0, heavy: 12.0 }    # [拟]
            formula_basic:      { base: 6,  scale: realm_coeff }            # [拟]
            ```
            """;

    private static NumbersSource numbers(Path root, String content) throws IOException {
        Path contentRoot = root.resolve("content");
        Files.createDirectories(contentRoot);
        Files.writeString(contentRoot.resolve("NUMBERS.md"), content);
        return NumbersSource.at(contentRoot);
    }

    @Test
    void readsFlowMapsInWrittenOrder(@TempDir Path root) throws IOException {
        NumbersSource source = numbers(root, NUMBERS);

        var costQi = source.map("combat", "spell_cost_qi");

        assertEquals(List.of("light", "medium", "heavy"), List.copyOf(costQi.keySet()));
        assertEquals(18L, costQi.get("medium"));
    }

    @Test
    void coercesManagedNumbers(@TempDir Path root) throws IOException {
        NumbersSource source = numbers(root, NUMBERS);

        assertEquals(1.8, source.value("limits", "growth_ratio_min"));
        assertEquals(2048L, source.value("limits", "budget_bytes"));
    }

    @Test
    void keepsNonNumericValuesAsStrings(@TempDir Path root) throws IOException {
        NumbersSource source = numbers(root, NUMBERS);

        assertEquals("realm_coeff", source.map("combat", "formula_basic").get("scale"));
        assertEquals(6L, source.map("combat", "formula_basic").get("base"));
    }

    @Test
    void aMissingKeyNamesTheBlockAndTheKey(@TempDir Path root) throws IOException {
        NumbersSource source = numbers(root, NUMBERS);

        IllegalStateException error =
                assertThrows(IllegalStateException.class, () -> source.value("combat", "bogus"));

        assertTrue(error.getMessage().contains("@@combat"), error.getMessage());
        assertTrue(error.getMessage().contains("bogus"), error.getMessage());
    }

    @Test
    void aScalarLookupThroughMapFails(@TempDir Path root) throws IOException {
        NumbersSource source = numbers(root, NUMBERS);

        IllegalStateException error =
                assertThrows(
                        IllegalStateException.class,
                        () -> source.map("limits", "growth_ratio_min"));

        assertTrue(error.getMessage().contains("not a { k: v"), error.getMessage());
    }

    @Test
    void aMissingTruthSourceFailsOnFirstUseNotOnConstruction(@TempDir Path root) {
        NumbersSource source = NumbersSource.at(root.resolve("content"));

        IllegalStateException error =
                assertThrows(IllegalStateException.class, () -> source.value("combat", "x"));

        assertTrue(error.getMessage().contains("NUMBERS.md not found"), error.getMessage());
        assertTrue(error.getMessage().contains("A0-7"), error.getMessage());
    }

    @Test
    void anUnclosedFenceFailsLoudly(@TempDir Path root) throws IOException {
        NumbersSource source = numbers(root, "@@limits\n```yaml\ngrowth_ratio_min: 1.8\n");

        IllegalStateException error =
                assertThrows(IllegalStateException.class, () -> source.value("limits", "x"));

        assertTrue(error.getMessage().contains("never closed"), error.getMessage());
    }

    /** Derivation flags like [占位] live in inline comments (NUMBERS §0), so they stay readable. */
    @Test
    void exposesInlineCommentsPerEntry(@TempDir Path root) throws IOException {
        NumbersSource source =
                numbers(
                        root,
                        "@@realms\n"
                                + "```yaml\n"
                                + "fanren: { qi_max: 100 } # [拟]\n"
                                + "lianxu: { qi_max: 13500 } # [拟][占位]\n"
                                + "```\n");

        assertEquals("[拟]", source.comment("realms", "fanren"));
        assertEquals("[拟][占位]", source.comment("realms", "lianxu"));
    }

    @Test
    void nestedSequencesInsideFlowMapsSurvive(@TempDir Path root) throws IOException {
        NumbersSource source =
                numbers(
                        root,
                        "@@realms\n"
                                + "```yaml\n"
                                + "qili: { qi_max: 230, unlocks: [meditation, spell_cast] } # [拟]\n"
                                + "```\n");

        Object unlocks = source.map("realms", "qili").get("unlocks");

        assertEquals(List.of("meditation", "spell_cast"), unlocks);
    }

    @Test
    void generatedHeaderCarriesTheFileHash(@TempDir Path root) throws IOException {
        NumbersSource source = numbers(root, NUMBERS);

        String header = source.generatedHeader();

        assertTrue(header.startsWith("from content/NUMBERS.md @ sha256:"), header);
        assertEquals(
                64,
                header.substring("from content/NUMBERS.md @ sha256:".length()).length(),
                header);
        assertEquals(header, source.generatedHeader(), "the hash is stable within a run");
    }
}

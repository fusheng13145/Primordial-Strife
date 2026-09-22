package com.strife.tools.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.strife.tools.validator.ValidatorMain.Options;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ValidatorMainTest {

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private static Options productsOnly(Path dataRoot) {
        return new Options(dataRoot, null);
    }

    private static Options withTables(Path dataRoot, Path tablesRoot) {
        return new Options(dataRoot, tablesRoot);
    }

    @Test
    void flagsDuplicateIdsWithinSameDomain(@TempDir Path dataRoot) throws IOException {
        write(
                dataRoot.resolve("strife/strife_realms/realm_qi_condensation.json"),
                "{\"id\":\"realm_qi_condensation\"}");
        write(
                dataRoot.resolve("strife/strife_realms/duplicate.json"),
                "{\"id\":\"realm_qi_condensation\"}");

        List<String> problems = ValidatorMain.duplicateIds(productsOnly(dataRoot));

        assertEquals(
                1, problems.size(), () -> "expected exactly one duplicate report, got " + problems);
        assertTrue(problems.get(0).contains("realm_qi_condensation"), problems.get(0));
    }

    /** docs/04 §6 states IDs are unique 全域, and lang keys map one-to-one onto them (04 §4). */
    @Test
    void flagsSameIdUsedInTwoDomains(@TempDir Path dataRoot) throws IOException {
        write(
                dataRoot.resolve("strife/strife_realms/a.json"),
                "{\"id\":\"realm_qi_condensation\"}");
        write(
                dataRoot.resolve("strife/strife_techniques/b.json"),
                "{\"id\":\"realm_qi_condensation\"}");

        assertEquals(1, ValidatorMain.duplicateIds(productsOnly(dataRoot)).size());
    }

    @Test
    void acceptsDistinctIdsAcrossDomains(@TempDir Path dataRoot) throws IOException {
        write(
                dataRoot.resolve("strife/strife_realms/a.json"),
                "{\"id\":\"realm_qi_condensation\"}");
        write(dataRoot.resolve("strife/strife_techniques/b.json"), "{\"id\":\"tech_qingxin_jue\"}");

        assertEquals(List.of(), ValidatorMain.duplicateIds(productsOnly(dataRoot)));
    }

    @Test
    void treatsMissingDataRootAsEmpty(@TempDir Path dataRoot) {
        assertEquals(
                List.of(), ValidatorMain.duplicateIds(productsOnly(dataRoot.resolve("absent"))));
    }

    @Test
    void flagsDuplicateIdsInSourceTables(@TempDir Path root) throws IOException {
        Path tables = root.resolve("tables");
        write(tables.resolve("pills.csv"), "id,_note\npill_juqi,聚气丹\npill_peiyuan,培元丹\n");
        write(tables.resolve("techniques.csv"), "id,_note\npill_juqi,撞了丹药的 ID\n");

        List<String> problems =
                ValidatorMain.duplicateIds(withTables(root.resolve("data"), tables));

        assertEquals(1, problems.size(), problems::toString);
        assertTrue(problems.get(0).contains("pill_juqi"), problems.get(0));
        assertTrue(problems.get(0).contains("pills.csv:2"), problems.get(0));
        assertTrue(problems.get(0).contains("techniques.csv:2"), problems.get(0));
    }

    @Test
    void flagsCollisionBetweenTableAndGeneratedContent(@TempDir Path root) throws IOException {
        Path tables = root.resolve("tables");
        write(tables.resolve("spells.csv"), "id,_note\nspell_linghua,\n");
        write(root.resolve("data/strife/strife_techniques/x.json"), "{\"id\":\"spell_linghua\"}");

        assertEquals(
                1, ValidatorMain.duplicateIds(withTables(root.resolve("data"), tables)).size());
    }

    @Test
    void emptyTablesAreLegalButTheirHeaderIsNot(@TempDir Path root) throws IOException {
        Path tables = root.resolve("tables");
        write(tables.resolve("wars.csv"), "id,_note\n");
        write(tables.resolve("ores.csv"), "name,_note\niron,\n");

        List<String> problems =
                ValidatorMain.duplicateIds(withTables(root.resolve("data"), tables));

        assertEquals(1, problems.size(), problems::toString);
        assertTrue(problems.get(0).contains("ores.csv"), problems.get(0));
        assertTrue(problems.get(0).contains("must be 'id'"), problems.get(0));
    }

    /** Ledger tables reuse names on purpose (placeholder whitelist, rename migration). */
    @Test
    void ignoresLedgerTables(@TempDir Path root) throws IOException {
        Path tables = root.resolve("tables");
        write(
                tables.resolve("known-placeholders.csv"),
                "id,kind\nbs_placeholder_high_1,突破成功率键占位\n");
        write(tables.resolve("id_migration.csv"), "id,old_id\nbs_placeholder_high_1,x\n");

        assertEquals(
                List.of(), ValidatorMain.duplicateIds(withTables(root.resolve("data"), tables)));
    }

    @Test
    void flagsGeneratedHeaderNamingAMissingTable(@TempDir Path root) throws IOException {
        Path tables = root.resolve("tables");
        write(tables.resolve("pills.csv"), "id,_note\n");
        write(
                root.resolve("data/strife/strife_pills/pill_juqi.json"),
                "{\"@generated\":\"from tables/pill_recipes.csv @ sha256:1f3a\",\"id\":\"pill_juqi\"}");

        List<String> problems =
                ValidatorMain.staleGeneratedHeaders(withTables(root.resolve("data"), tables));

        assertEquals(1, problems.size(), problems::toString);
        assertTrue(problems.get(0).contains("pill_recipes.csv"), problems.get(0));
    }

    @Test
    void acceptsHeaderNamingAnExistingTable(@TempDir Path root) throws IOException {
        Path tables = root.resolve("tables");
        write(tables.resolve("pills.csv"), "id,_note\n");
        write(
                root.resolve("data/strife/strife_pills/pill_juqi.json"),
                "{\"@generated\":\"from tables/pills.csv @ sha256:1f3a\",\"id\":\"pill_juqi\"}");

        assertEquals(
                List.of(),
                ValidatorMain.staleGeneratedHeaders(withTables(root.resolve("data"), tables)));
    }

    @Test
    void requiresDataRootArgument() {
        assertThrows(IllegalArgumentException.class, () -> ValidatorMain.parseArgs(new String[0]));
    }

    @Test
    void rejectsMissingTablesRootInsteadOfSkippingIt(@TempDir Path root) {
        IllegalArgumentException error =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                ValidatorMain.parseArgs(
                                        new String[] {
                                            "--data-root",
                                            root.resolve("data").toString(),
                                            "--tables-root",
                                            root.resolve("absent").toString()
                                        }));
        assertTrue(error.getMessage().contains("not a directory"), error.getMessage());
    }
}

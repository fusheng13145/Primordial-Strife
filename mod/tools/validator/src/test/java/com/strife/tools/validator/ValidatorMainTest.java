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

    /**
     * DataGen writes a product for every row, so the ID legitimately appears in both places.
     * Counting raw occurrences would make a successful generation fail its own gate.
     */
    @Test
    void aProductAndTheRowItWasGeneratedFromAreOneDeclaration(@TempDir Path root)
            throws IOException {
        Path tables = root.resolve("tables");
        write(tables.resolve("factions.csv"), "id,_note\nfac_qingshi,\n");
        write(
                root.resolve("data/strife/strife_factions/fac_qingshi.json"),
                "{\"@generated\":\"from tables/factions.csv @ sha256:aa\",\"id\":\"fac_qingshi\"}");

        assertEquals(
                List.of(),
                ValidatorMain.duplicateIds(withTables(root.resolve("data"), tables)),
                "generated content must not collide with its own source row");
    }

    @Test
    void flagsGeneratedProductClaimingADifferentSourceTable(@TempDir Path root) throws IOException {
        Path tables = root.resolve("tables");
        write(tables.resolve("spells.csv"), "id,_note\nspell_linghua,\n");
        write(
                root.resolve("data/strife/strife_techniques/x.json"),
                "{\"@generated\":\"from tables/techniques.csv @ sha256:aa\",\"id\":\"spell_linghua\"}");

        List<String> problems =
                ValidatorMain.duplicateIds(withTables(root.resolve("data"), tables));

        assertEquals(1, problems.size(), problems::toString);
        assertTrue(problems.get(0).contains("independent sources"), problems.get(0));
    }

    @Test
    void flagsTwoGeneratedProductsForASingleRow(@TempDir Path root) throws IOException {
        Path tables = root.resolve("tables");
        write(tables.resolve("factions.csv"), "id,_note\nfac_qingshi,\n");
        write(
                root.resolve("data/strife/strife_factions/fac_qingshi.json"),
                "{\"@generated\":\"from tables/factions.csv @ sha256:aa\",\"id\":\"fac_qingshi\"}");
        write(
                root.resolve("data/strife/strife_factions/fac_qingshi_copy.json"),
                "{\"@generated\":\"from tables/factions.csv @ sha256:aa\",\"id\":\"fac_qingshi\"}");

        List<String> problems =
                ValidatorMain.duplicateIds(withTables(root.resolve("data"), tables));

        assertEquals(1, problems.size(), problems::toString);
        assertTrue(problems.get(0).contains("generated files for 1 source row"), problems.get(0));
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
    void acceptsAHeaderWhoseHashMatchesTheTable(@TempDir Path root) throws IOException {
        Path table = root.resolve("tables/pills.csv");
        write(table, "id,_note\n");
        write(
                root.resolve("data/strife/strife_pills/pill_juqi.json"),
                product("from tables/pills.csv @ sha256:" + sha256(table)));

        assertEquals(
                List.of(),
                ValidatorMain.staleGeneratedHeaders(
                        withTables(root.resolve("data"), root.resolve("tables"))));
    }

    /** The half of V-FRESH that catches an edited table with a stale, already-committed product. */
    @Test
    void flagsAProductWhoseSourceTableWasEdited(@TempDir Path root) throws IOException {
        Path table = root.resolve("tables/pills.csv");
        write(table, "id,price\npill_juqi,\n");
        write(
                root.resolve("data/strife/strife_pills/pill_juqi.json"),
                product("from tables/pills.csv @ sha256:" + "0".repeat(64)));

        List<String> problems =
                ValidatorMain.staleGeneratedHeaders(
                        withTables(root.resolve("data"), root.resolve("tables")));

        assertEquals(1, problems.size(), problems::toString);
        assertTrue(problems.get(0).contains("without regenerating"), problems.get(0));
        assertTrue(problems.get(0).contains("pills.csv"), problems.get(0));
    }

    @Test
    void flagsAHeaderThatCarriesNoSourceHash(@TempDir Path root) throws IOException {
        Path table = root.resolve("tables/pills.csv");
        write(table, "id,_note\n");
        write(
                root.resolve("data/strife/strife_pills/pill_juqi.json"),
                product("from tables/pills.csv"));

        List<String> problems =
                ValidatorMain.staleGeneratedHeaders(
                        withTables(root.resolve("data"), root.resolve("tables")));

        assertEquals(1, problems.size(), problems::toString);
        assertTrue(problems.get(0).contains("no source hash"), problems.get(0));
    }

    private static String product(String generated) {
        return "{\"@generated\":\"" + generated + "\",\"id\":\"pill_juqi\"}";
    }

    private static String sha256(Path file) throws IOException {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(Files.readAllBytes(file)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
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

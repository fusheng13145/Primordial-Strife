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
        return new Options(dataRoot, null, null, null);
    }

    private static Options withTables(Path dataRoot, Path tablesRoot) {
        return new Options(dataRoot, tablesRoot, null, null);
    }

    /** Writes a content/NUMBERS.md carrying the given @@blocks and returns the content root. */
    private static Path withNumbers(Path root, String blocks) throws IOException {
        Path content = root.resolve("content");
        write(content.resolve("NUMBERS.md"), blocks);
        return content;
    }

    private static final String TWO_REALMS =
            """
            @@limits
            ```yaml
            growth_ratio_min: 1.8        # [锚] 下限
            growth_ratio_max: 2.5        # [锚] 上限
            ```

            @@realms
            ```yaml
            fanren: { qi_max: 100, stage_count: 1 } # [拟]
            qili: { qi_max: 230, stage_count: 9 } # [拟]
            ```
            """;

    @Test
    void growthRatioInsideBoundsPasses(@TempDir Path root) throws IOException {
        Options options =
                new Options(root.resolve("data"), null, withNumbers(root, TWO_REALMS), null);

        assertEquals(List.of(), ValidatorMain.growthRatio(options));
    }

    @Test
    void growthRatioAboveTheDeclaredMaxFails(@TempDir Path root) throws IOException {
        Options options =
                new Options(
                        root.resolve("data"),
                        null,
                        withNumbers(root, TWO_REALMS.replace("qi_max: 230", "qi_max: 320")),
                        null);

        List<String> problems = ValidatorMain.growthRatio(options);

        assertEquals(1, problems.size(), problems::toString);
        assertTrue(problems.get(0).contains("qili/fanren"), problems.get(0));
        assertTrue(problems.get(0).contains("exceeds growth_ratio_max 2.5"), problems.get(0));
        assertTrue(problems.get(0).contains("NUMBERS §2"), problems.get(0));
    }

    @Test
    void growthRatioBelowTheDeclaredMinFails(@TempDir Path root) throws IOException {
        Options options =
                new Options(
                        root.resolve("data"),
                        null,
                        withNumbers(root, TWO_REALMS.replace("qi_max: 230", "qi_max: 150")),
                        null);

        List<String> problems = ValidatorMain.growthRatio(options);

        assertEquals(1, problems.size(), problems::toString);
        assertTrue(problems.get(0).contains("below growth_ratio_min 1.8"), problems.get(0));
    }

    /** The bounds live in the truth source (AGENTS.md: no managed numbers in code). */
    @Test
    void growthBoundsAreReadFromLimitsNotHardcoded(@TempDir Path root) throws IOException {
        String widened = TWO_REALMS.replace("growth_ratio_max: 2.5", "growth_ratio_max: 3.3");
        Options options = new Options(root.resolve("data"), null, withNumbers(root, widened), null);

        assertEquals(
                List.of(),
                ValidatorMain.growthRatio(options),
                "a bound the truth source widened must not be flagged against a baked-in 2.5");
    }

    @Test
    void missingLimitsBoundIsAProblem(@TempDir Path root) throws IOException {
        String noMin = TWO_REALMS.replace("growth_ratio_min: 1.8        # [锚] 下限\n", "");
        Options options = new Options(root.resolve("data"), null, withNumbers(root, noMin), null);

        List<String> problems = ValidatorMain.growthRatio(options);

        assertEquals(1, problems.size(), problems::toString);
        assertTrue(problems.get(0).contains("growth_ratio_min"), problems.get(0));
    }

    @Test
    void unparseableRealmEntryIsAProblemNotASilentSkip(@TempDir Path root) throws IOException {
        String broken = TWO_REALMS.replace("qili: { qi_max: 230, stage_count: 9 }", "qili ??? 230");
        Options options = new Options(root.resolve("data"), null, withNumbers(root, broken), null);

        List<String> problems = ValidatorMain.growthRatio(options);

        assertEquals(1, problems.size(), problems::toString);
        assertTrue(problems.get(0).contains("cannot parse @@realms entry"), problems.get(0));
    }

    @Test
    void zeroQiMaxCannotFormARatio(@TempDir Path root) throws IOException {
        String zeroed = TWO_REALMS.replace("qi_max: 100", "qi_max: 0");
        Options options = new Options(root.resolve("data"), null, withNumbers(root, zeroed), null);

        List<String> problems = ValidatorMain.growthRatio(options);

        assertEquals(1, problems.size(), problems::toString);
        assertTrue(problems.get(0).contains("cannot form a growth ratio"), problems.get(0));
    }

    @Test
    void aMissingNumbersFileSkipsInsteadOfFailing(@TempDir Path root) throws IOException {
        Options options = new Options(root.resolve("data"), null, root.resolve("content"), null);

        assertEquals(
                List.of(),
                ValidatorMain.growthRatio(options),
                "an unmerged truth source is a skip (with a printed notice), not a red build");
    }

    @Test
    void aPresentNumbersFileWithoutTheContractedBlockIsAProblem(@TempDir Path root)
            throws IOException {
        Options options =
                new Options(
                        root.resolve("data"),
                        null,
                        withNumbers(root, "@@realms\n```yaml\n```"),
                        null);

        List<String> problems = ValidatorMain.growthRatio(options);

        assertEquals(1, problems.size(), problems::toString);
        assertTrue(problems.get(0).contains("@@limits"), problems.get(0));
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

    // ===== V-TEXT lang coverage (docs/04 §4, C1-2 gate) =====

    private static Options withAssets(Path dataRoot, Path assetsRoot) {
        return new Options(dataRoot, null, null, assetsRoot);
    }

    private static void writeLang(Path root, String zh, String en) throws IOException {
        Path lang = root.resolve("assets/strife/lang");
        write(lang.resolve("zh_cn.json"), zh);
        write(lang.resolve("en_us.json"), en);
    }

    private static final String ZH_QILI = "{\"realm.strife.qili\": \"练气\"}";
    private static final String EN_QILI = "{\"realm.strife.qili\": \"Qi Condensation\"}";

    @Test
    void aMissingZhLangKeyStopsTheBuild(@TempDir Path root) throws IOException {
        write(root.resolve("data/strife/strife_realms/qili.json"), "{\"id\":\"qili\"}");
        writeLang(root, "{}", EN_QILI);

        List<String> problems =
                ValidatorMain.langCoverage(
                        withAssets(root.resolve("data"), root.resolve("assets")));

        assertEquals(1, problems.size(), problems::toString);
        assertTrue(problems.get(0).contains("realm.strife.qili"), problems.get(0));
        assertTrue(problems.get(0).contains("zh_cn"), problems.get(0));
    }

    @Test
    void aBlankEnUsPlaceholderIsAProblem(@TempDir Path root) throws IOException {
        write(root.resolve("data/strife/strife_realms/qili.json"), "{\"id\":\"qili\"}");
        writeLang(root, ZH_QILI, "{\"realm.strife.qili\": \"\"}");

        List<String> problems =
                ValidatorMain.langCoverage(
                        withAssets(root.resolve("data"), root.resolve("assets")));

        assertEquals(1, problems.size(), problems::toString);
        assertTrue(problems.get(0).contains("en_us"), problems.get(0));
    }

    @Test
    void fullCoveragePassesAndDomainsWithoutARuleAreSkipped(@TempDir Path root) throws IOException {
        write(root.resolve("data/strife/strife_realms/qili.json"), "{\"id\":\"qili\"}");
        // Quest/dialog key rules land with M3's generators — a quest product demands nothing yet.
        write(
                root.resolve("data/strife/strife_quests/prologue.json"),
                "{\"id\":\"quest_prologue_meditation_01\"}");
        writeLang(root, ZH_QILI, EN_QILI);

        assertEquals(
                List.of(),
                ValidatorMain.langCoverage(
                        withAssets(root.resolve("data"), root.resolve("assets"))));
    }

    @Test
    void langCoverageWithoutAssetsRootIsADeclaredSkip(@TempDir Path root) throws IOException {
        write(root.resolve("data/strife/strife_realms/qili.json"), "{\"id\":\"qili\"}");

        assertEquals(List.of(), ValidatorMain.langCoverage(productsOnly(root.resolve("data"))));
    }

    // ===== V-FRESH for content/-sourced products (realms, JSON_SCHEMA §4.1) =====

    @Test
    void verifiesAProductGeneratedFromTheNumbersSource(@TempDir Path root) throws IOException {
        Path content = root.resolve("content");
        write(content.resolve("NUMBERS.md"), "@@realms\n```yaml\nqili: { qi_max: 230 }\n```\n");
        write(
                root.resolve("data/strife/strife_realms/qili.json"),
                product(
                        "from content/NUMBERS.md @ sha256:"
                                + sha256(content.resolve("NUMBERS.md"))));

        assertEquals(
                List.of(),
                ValidatorMain.staleGeneratedHeaders(
                        new Options(root.resolve("data"), null, content, null)));
    }

    @Test
    void flagsANumbersEditWithoutRegeneration(@TempDir Path root) throws IOException {
        Path content = root.resolve("content");
        write(content.resolve("NUMBERS.md"), "@@realms\n```yaml\nqili: { qi_max: 230 }\n```\n");
        write(
                root.resolve("data/strife/strife_realms/qili.json"),
                product("from content/NUMBERS.md @ sha256:" + "0".repeat(64)));

        List<String> problems =
                ValidatorMain.staleGeneratedHeaders(
                        new Options(root.resolve("data"), null, content, null));

        assertEquals(1, problems.size(), problems::toString);
        assertTrue(problems.get(0).contains("content/NUMBERS.md"), problems.get(0));
        assertTrue(problems.get(0).contains("without regenerating"), problems.get(0));
    }

    /** Until content/ merges, the unverifiable state must be declarable, never silently green. */
    @Test
    void unverifiableNumbersProductsAreDeclaredNotSilent(@TempDir Path root) throws IOException {
        write(
                root.resolve("data/strife/strife_realms/qili.json"),
                product("from content/NUMBERS.md @ sha256:" + "0".repeat(64)));
        Options unverifiable = new Options(root.resolve("data"), null, null, null);

        assertTrue(
                ValidatorMain.hasUnverifiableNumbersProducts(unverifiable),
                "contentRoot absent + numbers-sourced products = must be declared");

        Path content = root.resolve("content");
        write(content.resolve("NUMBERS.md"), "@@realms\n```yaml\nqili: { qi_max: 230 }\n```\n");
        Options verifiable = new Options(root.resolve("data"), null, content, null);

        assertEquals(false, ValidatorMain.hasUnverifiableNumbersProducts(verifiable));
    }

    // ===== V-PROB 概率归一 (docs/04 §6) =====

    @Test
    void flagsOutputProbabilitiesThatDoNotSumToOne(@TempDir Path root) throws IOException {
        write(
                root.resolve("data/strife/strife_pills/pill_x.json"),
                "{\"id\":\"pill_x\",\"outputs\":[{\"item_id\":\"a\",\"prob\":0.9}]}");

        List<String> problems = ValidatorMain.probabilitySum(productsOnly(root.resolve("data")));

        assertEquals(1, problems.size(), problems::toString);
        assertTrue(problems.get(0).contains("0.9"), problems.get(0));
        assertTrue(problems.get(0).contains("outputs"), problems.get(0));
    }

    @Test
    void probabilitySumAcceptsOneWithinTolerance(@TempDir Path root) throws IOException {
        write(
                root.resolve("data/strife/strife_pills/pill_x.json"),
                "{\"id\":\"pill_x\",\"outputs\":[{\"prob\":0.7},{\"prob\":0.3}]}");

        assertEquals(List.of(), ValidatorMain.probabilitySum(productsOnly(root.resolve("data"))));
    }

    @Test
    void qualityProbsAreCheckedToo(@TempDir Path root) throws IOException {
        write(
                root.resolve("data/strife/strife_artifacts/art_x.json"),
                "{\"id\":\"art_x\",\"quality_probs\":[{\"quality_tier\":\"fan\",\"prob\":0.7},{\"prob\":0.25}]}");

        List<String> problems = ValidatorMain.probabilitySum(productsOnly(root.resolve("data")));

        assertEquals(1, problems.size(), problems::toString);
        assertTrue(problems.get(0).contains("quality_probs"), problems.get(0));
    }

    // ===== V-RANGE price 非负 (docs/03 §10 H1) =====

    @Test
    void flagsANegativePrice(@TempDir Path root) throws IOException {
        write(
                root.resolve("data/strife/strife_techniques/tech_x.json"),
                "{\"id\":\"tech_x\",\"price\":{\"item_id\":\"item_lingshi\",\"count\":-5}}");

        List<String> problems = ValidatorMain.numericRanges(productsOnly(root.resolve("data")));

        assertEquals(1, problems.size(), problems::toString);
        assertTrue(problems.get(0).contains("-5"), problems.get(0));
    }

    @Test
    void aZeroPriceIsAValueNotAMissingField(@TempDir Path root) throws IOException {
        write(
                root.resolve("data/strife/strife_pills/pill_x.json"),
                "{\"id\":\"pill_x\",\"price\":{\"item_id\":\"item_lingshi\",\"count\":0}}");

        assertEquals(List.of(), ValidatorMain.numericRanges(productsOnly(root.resolve("data"))));
    }

    // ===== V-RANGE truth-source sub-items (成功率域 / 寿元单调) =====

    private static final String RANGES =
            """
            @@limits
            ```yaml
            success_rate_min: 0.0        # [锚]
            success_rate_max: 1.0        # [锚]
            ```

            @@breakthrough
            ```yaml
            bs_a: { base: 0.95, fail_step: 0.00, floor: 0.95 } # [拟]
            bs_bad: { base: 1.50, fail_step: 0.00, floor: 0.40 } # [拟]
            ```

            @@realms
            ```yaml
            fanren: { qi_max: 100, lifespan_years: 80, unlocks: [] } # [拟]
            qili: { qi_max: 230, lifespan_years: 120, unlocks: [] } # [拟]
            lianxu: { qi_max: 13500, lifespan_years: 50, unlocks: [] } # [拟][占位]
            ```
            """;

    @Test
    void flagsASuccessRateOutsideTheDeclaredBounds(@TempDir Path root) throws IOException {
        Options options = new Options(root.resolve("data"), null, withNumbers(root, RANGES), null);

        List<String> problems = ValidatorMain.truthSourceRanges(options);

        assertEquals(1, problems.size(), problems::toString);
        assertTrue(problems.get(0).contains("bs_bad"), problems.get(0));
        assertTrue(problems.get(0).contains("outside success_rate bounds"), problems.get(0));
    }

    @Test
    void inBoundsRatesAndPlaceholderLifespanPass(@TempDir Path root) throws IOException {
        Options options =
                new Options(
                        root.resolve("data"),
                        null,
                        withNumbers(root, RANGES.replace("base: 1.50", "base: 0.50")),
                        null);

        assertEquals(
                List.of(),
                ValidatorMain.truthSourceRanges(options),
                "lianxu 的 lifespan 50<120 因 [占位] 放宽（JSON_SCHEMA §4.1）");
    }

    @Test
    void lifespanMustStrictlyIncreaseForNamedRealms(@TempDir Path root) throws IOException {
        String decreasing =
                RANGES.replace("base: 1.50", "base: 0.50")
                        .replace(
                                "qili: { qi_max: 230, lifespan_years: 120",
                                "qili: { qi_max: 230, lifespan_years: 60");
        Options options =
                new Options(root.resolve("data"), null, withNumbers(root, decreasing), null);

        List<String> problems = ValidatorMain.truthSourceRanges(options);

        assertEquals(1, problems.size(), problems::toString);
        assertTrue(problems.get(0).contains("does not increase"), problems.get(0));
        assertTrue(problems.get(0).contains("qili"), problems.get(0));
    }
}

package com.strife.tools.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TableSourceTest {

    private static TableSource read(Path dir, String fileName, String... lines) throws IOException {
        Files.createDirectories(dir);
        Path csv = dir.resolve(fileName);
        Files.write(csv, List.of(lines));
        return TableSource.read(csv);
    }

    @Test
    void dropsCommentColumnsAndMapsBlankCellsToNull(@TempDir Path dir) throws IOException {
        TableSource table =
                read(
                        dir,
                        "factions.csv",
                        "id,alignment,_说明,home_region",
                        "fac_qingshi,orthodox,青石宗,");

        assertEquals(List.of("id", "alignment", "home_region"), table.columns());
        TableSource.Record row = table.rows().get(0);
        assertEquals(2, row.line(), "line numbers are 1-based and include the header");
        assertEquals("fac_qingshi", row.id());
        assertEquals("orthodox", table.get("alignment", row));
        assertNull(table.get("home_region", row), "blank cell means no value, not empty string");
    }

    @Test
    void skipsBlankAndCommentLines(@TempDir Path dir) throws IOException {
        TableSource table =
                read(
                        dir,
                        "factions.csv",
                        "id,alignment",
                        "",
                        "# todo: yao faction",
                        "fac_x,neutral");

        assertEquals(1, table.rows().size());
        assertEquals("fac_x", table.rows().get(0).id());
    }

    @Test
    void requiresIdAsFirstHeaderColumn(@TempDir Path dir) {
        IllegalStateException error =
                assertThrows(
                        IllegalStateException.class,
                        () -> read(dir, "factions.csv", "alignment,id", "orthodox,fac_x"));

        assertTrue(
                error.getMessage().contains("first header column must be 'id'"),
                error.getMessage());
    }

    @Test
    void rejectsCommasInsideACell(@TempDir Path dir) {
        IllegalStateException error =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                read(
                                        dir,
                                        "factions.csv",
                                        "id,alignment,relations",
                                        "fac_x,neutral,a,b"));

        assertTrue(
                error.getMessage().contains("commas inside a cell are forbidden"),
                error.getMessage());
    }

    @Test
    void rejectsQuotedCellsAsOutsideTheContract(@TempDir Path dir) {
        IllegalStateException error =
                assertThrows(
                        IllegalStateException.class,
                        () -> read(dir, "factions.csv", "id,alignment", "\"fac_x\",neutral"));

        assertTrue(error.getMessage().contains("quoted cells"), error.getMessage());
    }

    @Test
    void namesTheMissingColumnInsteadOfReturningNull(@TempDir Path dir) throws IOException {
        TableSource table = read(dir, "factions.csv", "id,alignment", "fac_x,neutral");
        TableSource.Record row = table.rows().get(0);

        IllegalStateException error =
                assertThrows(IllegalStateException.class, () -> table.get("home_regoin", row));

        assertTrue(
                error.getMessage().contains("contract drift?")
                        && error.getMessage().contains("alignment"),
                error.getMessage());
    }

    @Test
    void readsSemicolonLists(@TempDir Path dir) throws IOException {
        TableSource table = read(dir, "factions.csv", "id,relations", "fac_x,()", "fac_y,a;b");

        assertEquals(
                List.of(),
                table.list("relations", table.rows().get(0)),
                "explicit () is an empty list (JSON_SCHEMA §2)");
        assertEquals(List.of("a", "b"), table.list("relations", table.rows().get(1)));
    }

    @Test
    void readsKeyValueMappingsInColumnOrder(@TempDir Path dir) throws IOException {
        TableSource table =
                read(dir, "factions.csv", "id,relations", "fac_x,yuelai=-80;qingshi=40");

        Map<String, String> relations = table.mapping("relations", table.rows().get(0));

        assertEquals(List.of("yuelai", "qingshi"), List.copyOf(relations.keySet()));
        assertEquals("-80", relations.get("yuelai"));
    }

    @Test
    void rejectsMalformedMappingEntries(@TempDir Path dir) {
        IllegalStateException error =
                assertThrows(
                        IllegalStateException.class,
                        () -> {
                            TableSource table =
                                    read(dir, "factions.csv", "id,relations", "fac_x,yuelai");
                            table.mapping("relations", table.rows().get(0));
                        });

        assertTrue(error.getMessage().contains("expects k=v entries"), error.getMessage());
    }

    @Test
    void refusesToGuessTheUnratifiedNestedGrammar(@TempDir Path dir) throws IOException {
        TableSource table = read(dir, "factions.csv", "id,relations", "fac_x,{a:1}|{b:2}");
        TableSource.Record row = table.rows().get(0);

        IllegalStateException error =
                assertThrows(
                        IllegalStateException.class, () -> table.requireScalar("relations", row));

        assertTrue(
                error.getMessage().contains("still [拟]")
                        && error.getMessage().contains("does not guess"),
                error.getMessage());
    }

    @Test
    void hashFollowsTheFileBytes(@TempDir Path dir) throws IOException {
        TableSource before = read(dir, "factions.csv", "id,alignment", "fac_x,neutral");
        TableSource after = read(dir, "factions.csv", "id,alignment", "fac_x,demonic");

        assertEquals(64, before.sha256().length(), "SHA-256 renders as 64 hex chars");
        assertTrue(before.sha256().matches("[0-9a-f]{64}"), before.sha256());
        assertTrue(!before.sha256().equals(after.sha256()), "editing a cell must change the hash");
        assertEquals(
                "from tables/factions.csv @ sha256:" + after.sha256(), after.generatedHeader());
    }
}

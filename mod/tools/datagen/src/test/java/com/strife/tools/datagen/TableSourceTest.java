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

    /**
     * The nested grammar is implemented for list/mapping/object columns (JSON_SCHEMA §2), but a
     * scalar column carrying it is a row written against the wrong column type — still a hard fail.
     */
    @Test
    void rejectsNestedGrammarInScalarColumns(@TempDir Path dir) throws IOException {
        TableSource table = read(dir, "factions.csv", "id,relations", "fac_x,{a:1}|{b:2}");
        TableSource.Record row = table.rows().get(0);

        IllegalStateException error =
                assertThrows(
                        IllegalStateException.class, () -> table.requireScalar("relations", row));

        assertTrue(
                error.getMessage().contains("scalar column")
                        && error.getMessage().contains("JSON_SCHEMA.md §2"),
                error.getMessage());
    }

    @Test
    void readsObjectArraysSeparatedByPipes(@TempDir Path dir) throws IOException {
        TableSource table =
                read(
                        dir,
                        "pills.csv",
                        "id,outputs",
                        "pill_x,item_id=a;count=1;prob=0.7;quality=fan|item_id=b;count=2;prob=0.3;quality=di");

        List<Map<String, String>> objects = table.objectList("outputs", table.rows().get(0));

        assertEquals(2, objects.size());
        assertEquals("a", objects.get(0).get("item_id"));
        assertEquals("0.7", objects.get(0).get("prob"));
        assertEquals("b", objects.get(1).get("item_id"));
        assertEquals("di", objects.get(1).get("quality"));
    }

    @Test
    void readsParenWrappedObjects(@TempDir Path dir) throws IOException {
        TableSource table =
                read(
                        dir,
                        "dialog_trees_prologue.csv",
                        "id,nodes",
                        "dlg_x,(id=d1;text_key=dialog.strife.d1;options=(text_key=a;next=d2))");

        List<Map<String, String>> objects = table.objectList("nodes", table.rows().get(0));

        assertEquals(1, objects.size());
        assertEquals("d1", objects.get(0).get("id"));
        assertEquals(
                "(text_key=a;next=d2)",
                objects.get(0).get("options"),
                "a nested value keeps its parens; unwrapping is the field contract's business");
    }

    /** 契约 §2: blank cell = 缺省 (null), () = explicit empty container — distinct states. */
    @Test
    void objectListKeepsAbsentAndExplicitlyEmptyApart(@TempDir Path dir) throws IOException {
        TableSource table = read(dir, "spells.csv", "id,effects", "spell_a,", "spell_b,()");

        assertNull(table.objectList("effects", table.rows().get(0)), "blank = absent = null");
        assertEquals(
                List.of(), table.objectList("effects", table.rows().get(1)), "() = empty array");
    }

    @Test
    void aSeparatorInsideParensDoesNotSplitTheCell(@TempDir Path dir) throws IOException {
        TableSource table =
                read(dir, "factions.csv", "id,relations", "fac_x,key=(a=1;b=2);other=3");

        Map<String, String> mapping = table.mapping("relations", table.rows().get(0));

        assertEquals("(a=1;b=2)", mapping.get("key"));
        assertEquals("3", mapping.get("other"));
    }

    @Test
    void rejectsObjectGrammarInsideAPlainList(@TempDir Path dir) throws IOException {
        TableSource table = read(dir, "techniques.csv", "id,passives", "tech_x,a;b|c");
        TableSource.Record row = table.rows().get(0);

        IllegalStateException error =
                assertThrows(IllegalStateException.class, () -> table.list("passives", row));

        assertTrue(error.getMessage().contains("object grammar"), error.getMessage());
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

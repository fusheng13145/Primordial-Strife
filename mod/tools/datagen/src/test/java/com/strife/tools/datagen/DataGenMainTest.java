package com.strife.tools.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DataGenMainTest {

    @Test
    void parsesBothRoots() {
        DataGenMain.Options options =
                DataGenMain.parse(
                        new String[] {
                            "--tables-root",
                            "tables",
                            "--resources-root",
                            "content-base/src/main/resources"
                        });

        assertEquals(Path.of("tables"), options.tablesRoot());
        assertEquals(Path.of("content-base/src/main/resources"), options.resourcesRoot());
    }

    @Test
    void rejectsMissingArguments() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DataGenMain.parse(new String[] {"--tables-root", "t"}));
    }

    @Test
    void collectsCsvFilesDeterministicallyAndIgnoresOthers(@TempDir Path tablesRoot)
            throws IOException {
        Files.createDirectories(tablesRoot.resolve("realm"));
        Files.writeString(tablesRoot.resolve("realm/realms.csv"), "id\n");
        Files.writeString(tablesRoot.resolve("realm/notes.txt"), "ignored");

        List<Path> found = DataGenMain.listCsv(tablesRoot);

        assertEquals(1, found.size(), () -> "only .csv belongs to DataGen, got " + found);
        assertTrue(found.get(0).endsWith("realms.csv"), found.toString());
    }

    @Test
    void returnsEmptyListWhenTablesRootIsAbsent(@TempDir Path tablesRoot) {
        assertEquals(List.of(), DataGenMain.listCsv(tablesRoot.resolve("absent")));
    }

    @Test
    void registersTheNpcPlacementGenerator() {
        assertTrue(
                DataGenMain.generators(NumbersSource.at(null)).stream()
                        .anyMatch(generator -> generator.tableFile().equals("npcs.csv")),
                "npcs.csv 已填行，必须注册生成器，否则 DataGen 会按「填了行却静默丢弃」报错");
    }

    @Test
    void reportsRowsThatNoGeneratorWasRegisteredFor(@TempDir Path root) throws IOException {
        write(tableDir(root), "factions.csv", "id,alignment", "fac_qingshi,orthodox");

        DataGenMain.Summary summary =
                DataGenMain.run(options(root), List.of(), List.of(), NumbersSource.at(null));

        assertEquals(
                1,
                summary.problems().size(),
                "filled rows with no generator must stop the run, got " + summary.problems());
        assertTrue(
                summary.problems().get(0).contains("no generator is registered"),
                summary.problems().get(0));
        assertTrue(summary.problems().get(0).contains("factions.csv"), summary.problems().get(0));
    }

    @Test
    void ledgerTablesCarryRowsWithoutAGenerator(@TempDir Path root) throws IOException {
        write(
                tableDir(root),
                "known-placeholders.csv",
                "id,kind,used_by_field",
                "bs_placeholder_high_1,突破成功率键占位,strife_realms:lianxu.breakthrough_success_key");

        assertEquals(
                List.of(),
                DataGenMain.run(options(root), List.of(), List.of(), NumbersSource.at(null))
                        .problems());
    }

    @Test
    void anEmptyUnregisteredTableIsNotYetAProblem(@TempDir Path root) throws IOException {
        write(tableDir(root), "factions.csv", "id,alignment");

        DataGenMain.Summary summary =
                DataGenMain.run(options(root), List.of(), List.of(), NumbersSource.at(null));

        assertEquals(List.of(), summary.problems());
        assertEquals(1, summary.tables());
        assertEquals(0, summary.rows());
    }

    @Test
    void writesOneDeterministicProductPerRow(@TempDir Path root) throws IOException {
        write(
                tableDir(root),
                "factions.csv",
                "id,alignment",
                "fac_qingshi,orthodox",
                "fac_yuelai,demonic");

        DataGenMain.Summary summary =
                DataGenMain.run(
                        options(root),
                        List.of(new IdEchoGenerator("factions.csv")),
                        List.of(),
                        NumbersSource.at(null));

        assertEquals(2, summary.products(), "the content index is metadata, not a content product");
        Path product = resourcesDir(root).resolve("data/strife/strife_factions/fac_qingshi.json");
        assertTrue(Files.isRegularFile(product), product.toString());
        String json = Files.readString(product);
        assertTrue(json.contains("\"id\": \"fac_qingshi\""), json);
        assertTrue(json.contains("\"@generated\": \"from tables/factions.csv @ sha256:"), json);
        // Re-running over identical tables must not move a single byte (04 §5 确定性产物).
        DataGenMain.run(
                options(root),
                List.of(new IdEchoGenerator("factions.csv")),
                List.of(),
                NumbersSource.at(null));
        assertEquals(json, Files.readString(product));
    }

    @Test
    void emitsAContentIndexListingEveryGeneratedProduct(@TempDir Path root) throws IOException {
        write(
                tableDir(root),
                "factions.csv",
                "id,alignment",
                "fac_yuelai,demonic",
                "fac_qingshi,orthodox");

        DataGenMain.run(
                options(root),
                List.of(new IdEchoGenerator("factions.csv")),
                List.of(),
                NumbersSource.at(null));

        Path indexFile = resourcesDir(root).resolve(DataGenMain.INDEX_PATH);
        assertTrue(Files.isRegularFile(indexFile), indexFile.toString());
        JsonObject index = JsonParser.parseString(Files.readString(indexFile)).getAsJsonObject();
        assertEquals("strife_index/1", index.get("content_format").getAsString());
        assertEquals(
                List.of(
                        "data/strife/strife_factions/fac_qingshi.json",
                        "data/strife/strife_factions/fac_yuelai.json"),
                strings(index.getAsJsonArray("products")),
                "index must be sorted and must not list itself");
        assertFalse(strings(index.getAsJsonArray("products")).contains(DataGenMain.INDEX_PATH));
    }

    @Test
    void contentIndexIsStableAcrossReruns(@TempDir Path root) throws IOException {
        write(tableDir(root), "factions.csv", "id,alignment", "fac_qingshi,orthodox");
        List<TableGenerator> generators = List.of(new IdEchoGenerator("factions.csv"));

        DataGenMain.run(options(root), generators, List.of(), NumbersSource.at(null));
        String first = Files.readString(resourcesDir(root).resolve(DataGenMain.INDEX_PATH));
        DataGenMain.run(options(root), generators, List.of(), NumbersSource.at(null));

        assertEquals(first, Files.readString(resourcesDir(root).resolve(DataGenMain.INDEX_PATH)));
    }

    private static List<String> strings(JsonArray array) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.getAsString()));
        return values;
    }

    /** Stands in for a real domain generator until those tables are contracted. */
    private record IdEchoGenerator(String tableFile) implements TableGenerator {

        @Override
        public List<Product> generate(TableSource source) {
            return source.rows().stream()
                    .map(
                            row -> {
                                Map<String, Object> fields = new LinkedHashMap<>();
                                fields.put("id", row.id());
                                return new Product(
                                        "data/strife/strife_factions/" + row.id() + ".json",
                                        fields,
                                        source.generatedHeader());
                            })
                    .toList();
        }
    }

    private static DataGenMain.Options options(Path root) {
        return new DataGenMain.Options(tableDir(root), resourcesDir(root), null);
    }

    private static Path tableDir(Path root) {
        return root.resolve("tables");
    }

    private static Path resourcesDir(Path root) {
        return root.resolve("resources");
    }

    private static void write(Path dir, String fileName, String... lines) throws IOException {
        Files.createDirectories(dir);
        Files.write(dir.resolve(fileName), List.of(lines));
    }
}

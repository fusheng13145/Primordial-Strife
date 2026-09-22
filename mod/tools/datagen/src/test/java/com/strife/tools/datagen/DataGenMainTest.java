package com.strife.tools.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    void reportsRowsThatNoGeneratorWasRegisteredFor(@TempDir Path root) throws IOException {
        write(tableDir(root), "factions.csv", "id,alignment", "fac_qingshi,orthodox");

        DataGenMain.Summary summary = DataGenMain.run(options(root), List.of());

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

        assertEquals(List.of(), DataGenMain.run(options(root), List.of()).problems());
    }

    @Test
    void anEmptyUnregisteredTableIsNotYetAProblem(@TempDir Path root) throws IOException {
        write(tableDir(root), "factions.csv", "id,alignment");

        DataGenMain.Summary summary = DataGenMain.run(options(root), List.of());

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
                DataGenMain.run(options(root), List.of(new IdEchoGenerator("factions.csv")));

        assertEquals(2, summary.products());
        Path product = resourcesDir(root).resolve("data/strife/strife_factions/fac_qingshi.json");
        assertTrue(Files.isRegularFile(product), product.toString());
        String json = Files.readString(product);
        assertTrue(json.contains("\"id\": \"fac_qingshi\""), json);
        assertTrue(json.contains("\"@generated\": \"from tables/factions.csv @ sha256:"), json);
        // Re-running over identical tables must not move a single byte (04 §5 确定性产物).
        DataGenMain.run(options(root), List.of(new IdEchoGenerator("factions.csv")));
        assertEquals(json, Files.readString(product));
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
        return new DataGenMain.Options(tableDir(root), resourcesDir(root));
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

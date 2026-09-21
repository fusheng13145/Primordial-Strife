package strife.tools.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
                            "--module-root",
                            "content-base/src/main/resources"
                        });

        assertEquals(Path.of("tables"), options.tablesRoot());
        assertEquals(Path.of("content-base/src/main/resources"), options.moduleRoot());
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
}

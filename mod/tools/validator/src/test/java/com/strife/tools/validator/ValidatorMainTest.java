package com.strife.tools.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void flagsDuplicateIdsWithinSameDomain(@TempDir Path dataRoot) throws IOException {
        write(
                dataRoot.resolve("strife/strife_realms/realm_qi_condensation.json"),
                "{\"id\":\"realm_qi_condensation\"}");
        write(
                dataRoot.resolve("strife/strife_realms/duplicate.json"),
                "{\"id\":\"realm_qi_condensation\"}");

        List<String> problems = ValidatorMain.duplicateIds(dataRoot);

        assertEquals(
                1, problems.size(), () -> "expected exactly one duplicate report, got " + problems);
        assertTrue(problems.get(0).contains("realm_qi_condensation"), problems.get(0));
    }

    @Test
    void acceptsDistinctIdsAcrossDomains(@TempDir Path dataRoot) throws IOException {
        write(
                dataRoot.resolve("strife/strife_realms/a.json"),
                "{\"id\":\"realm_qi_condensation\"}");
        write(
                dataRoot.resolve("strife/strife_techniques/b.json"),
                "{\"id\":\"realm_qi_condensation\"}");
        write(dataRoot.resolve("strife/strife_techniques/c.json"), "{\"id\":\"tech_qingxin_jue\"}");

        assertEquals(List.of(), ValidatorMain.duplicateIds(dataRoot));
    }

    @Test
    void treatsMissingDataRootAsEmpty(@TempDir Path dataRoot) {
        assertEquals(List.of(), ValidatorMain.duplicateIds(dataRoot.resolve("absent")));
    }

    @Test
    void requiresDataRootArgument() {
        assertThrows(
                IllegalArgumentException.class, () -> ValidatorMain.parseDataRoot(new String[0]));
    }
}

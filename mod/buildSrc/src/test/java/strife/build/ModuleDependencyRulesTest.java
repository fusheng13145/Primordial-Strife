package strife.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModuleDependencyRulesTest {

    private static void writeJava(Path file, String... lines) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, List.of(lines));
    }

    @Test
    void realmMayOnlyDependOnCoreAndItself() {
        assertEquals(Set.of("core", "realm"), ModuleDependencyRules.allowedTargets("realm"));
        assertTrue(ModuleDependencyRules.allowedTargets("combat").contains("realm"));
        assertTrue(ModuleDependencyRules.allowedTargets("combat").contains("combat"));
    }

    @Test
    void clientFxMayNotReachIntoLogicPackages() {
        assertEquals(Set.of("core", "client_fx"), ModuleDependencyRules.allowedTargets("client_fx"));
    }

    @Test
    void rejectsHorizontalDependencies(@TempDir Path srcRoot) throws IOException {
        writeJava(
                srcRoot.resolve("com/strife/realm/RealmReader.java"),
                "package com.strife.realm;",
                "public class RealmReader {}");
        writeJava(
                srcRoot.resolve("com/strife/realm/Breakthrough.java"),
                "package com.strife.realm;",
                "import com.strife.quest.QuestEngine;",
                "public class Breakthrough {}");

        List<String> violations = ModuleDependencyRules.checkSourceTree("platform", srcRoot);

        assertEquals(1, violations.size(), () -> "expected only the realm -> quest import, got " + violations);
        assertTrue(violations.get(0).contains("realm -> quest"), violations.get(0));
    }

    @Test
    void ignoresNonStrifeImportsAndUnrelatedPackages(@TempDir Path srcRoot) throws IOException {
        writeJava(
                srcRoot.resolve("com/strife/world/QiField.java"),
                "package com.strife.world;",
                "import com.strife.realm.RealmReader;",
                "import net.minecraft.core.BlockPos;",
                "public class QiField {}");

        assertEquals(List.of(), ModuleDependencyRules.checkSourceTree("platform", srcRoot));
    }

    @Test
    void forbidsJavaCodeInContentBase(@TempDir Path srcRoot) throws IOException {
        writeJava(srcRoot.resolve("com/strife/content/Leak.java"), "package com.strife.content;", "public class Leak {}");

        List<String> violations = ModuleDependencyRules.checkSourceTree("content-base", srcRoot);

        assertEquals(1, violations.size(), violations.toString());
        assertTrue(violations.get(0).contains("content-base must stay code-free"), violations.get(0));
    }

    @Test
    void rejectsUnknownPackageNames() {
        assertThrows(IllegalArgumentException.class, () -> ModuleDependencyRules.allowedTargets("economy"));
    }
}

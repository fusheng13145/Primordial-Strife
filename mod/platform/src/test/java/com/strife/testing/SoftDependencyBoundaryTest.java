package com.strife.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SoftDependencyBoundaryTest {

    private static final Path PROJECT_DIR = Path.of(System.getProperty("strife.projectDir", "."));
    private static final Path MODS_TOML =
            PROJECT_DIR.resolve("src/main/resources/META-INF/neoforge.mods.toml");

    @Test
    @DisplayName("mods.toml 把 Patchouli 与 JEI 声明为客户端可选依赖")
    void shippedMetadataDeclaresOptionalClientSoftDependencies() throws Exception {
        Map<String, Map<String, String>> blocks = dependencyBlocks(Files.readString(MODS_TOML));

        for (String modId : List.of("patchouli", "jei")) {
            Map<String, String> block = blocks.get(modId);
            assertNotNull(block, () -> "缺少 " + modId + " 依赖块，无库时无法表达软依赖边界");
            assertEquals("optional", block.get("type"), modId + " 必须是 optional");
            assertEquals("CLIENT", block.get("side"), modId + " 只在客户端生效");
            assertFalse(block.getOrDefault("versionRange", "").isBlank(), modId + " 需要版本范围");
        }
    }

    @Test
    @DisplayName("主源码没有对 JEI/Patchouli 的硬引用")
    void mainSourcesNeverHardReferenceSoftDependencies() throws Exception {
        assertEquals(List.of(), SoftDependencyGuard.scan(PROJECT_DIR.resolve("src/main/java")));

        // ADR-028 ③：compat 白名单正反样本——包内引库放行，包外依旧必红。
        assertTrue(
                SoftDependencyGuard.violations(
                                "com/strife/clientfx/compat/jei/StrifeJeiPlugin.java",
                                "package com.strife.clientfx.compat.jei;\n\nimport mezz.jei.api.JeiPlugin;\n")
                        .isEmpty(),
                "compat 包内允许引用 JEI（ADR-028 ③）");
        assertFalse(
                SoftDependencyGuard.violations(
                                "com/strife/clientfx/StrifeClientFx.java",
                                "package com.strife.clientfx;\n\nimport mezz.jei.api.JeiPlugin;\n")
                        .isEmpty(),
                "compat 包外引用 JEI 仍然必红");
    }

    @Test
    @DisplayName("守卫能抓到 import 与全限定引用")
    void guardDetectsImportAndFullyQualifiedReferences() {
        assertEquals(
                List.of("A.java:3 hard-references soft dependency mezz.jei"),
                SoftDependencyGuard.violations(
                        "A.java", "package a;\n\nimport mezz.jei.api.JeiPlugin;\n"));
        assertEquals(
                List.of("B.java:2 hard-references soft dependency vazkii.patchouli"),
                SoftDependencyGuard.violations(
                        "B.java",
                        "package b;\nvar api = vazkii.patchouli.api.PatchouliAPI.get();\n"));
    }

    @Test
    @DisplayName("守卫不误报原版与自家包")
    void guardAcceptsCleanSource() {
        assertEquals(
                List.of(),
                SoftDependencyGuard.violations(
                        "C.java",
                        "package com.strife.clientfx;\nimport net.minecraft.world.item.Item;\n"));
    }

    @Test
    @DisplayName("扫描根不存在时守卫报错而不是静默通过")
    void guardRejectsMissingSourceRoot() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> SoftDependencyGuard.scan(PROJECT_DIR.resolve("src/main/java-missing")));
    }

    private static Map<String, Map<String, String>> dependencyBlocks(String toml) {
        Map<String, Map<String, String>> blocks = new LinkedHashMap<>();
        Map<String, String> current = null;
        for (String raw : toml.split("\\R")) {
            String line = raw.trim();
            if (line.startsWith("[[dependencies.")) {
                current = new LinkedHashMap<>();
                continue;
            }
            if (line.startsWith("[") && current != null) {
                current = null;
                continue;
            }
            if (current == null || line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            current.put(key, value);
            if ("modId".equals(key)) {
                blocks.put(value, current);
            }
        }
        return blocks;
    }
}

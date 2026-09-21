package strife.build;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Enforces the package dependency chain of docs/03 §2:
 *
 * <pre>
 * combat / production / quest / world -&gt; realm -&gt; core
 * realm -&gt; core only; no horizontal dependencies; client_fx -&gt; nothing but core.
 * </pre>
 *
 * Checks static imports of {@code com.strife.<pkg>...} in Java sources.
 */
public final class ModuleDependencyRules {

    private static final Pattern IMPORT = Pattern.compile("^import\\s+(?:static\\s+)?(com\\.strife\\.[A-Za-z0-9_.]+)\\s*;");

    private static final Set<String> PACKAGES =
            Set.of("core", "realm", "combat", "production", "quest", "world", "client_fx");

    /** Allowed com.strife target packages per source package. */
    public static Set<String> allowedTargets(String sourcePackage) {
        return switch (sourcePackage) {
            case "core" -> Set.of("core");
            case "realm" -> Set.of("core", "realm");
            case "combat", "production", "quest", "world" -> Set.of("core", "realm", sourcePackage);
            case "client_fx" -> Set.of("core", "client_fx");
            default -> throw new IllegalArgumentException("unknown package: " + sourcePackage);
        };
    }

    /** Returns one violation string per illegal import ("file:line import -> module (allowed: ...)"). */
    public static List<String> checkSourceTree(String moduleName, Path javaSourceRoot) {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(javaSourceRoot)) {
            files.filter(p -> p.toString().endsWith(".java")).forEach(p -> checkFile(javaSourceRoot, p, violations));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if ("content-base".equals(moduleName)) {
            Path javaDir = javaSourceRoot.resolve("com");
            if (Files.exists(javaDir)) {
                violations.add("content-base must stay code-free (docs/02 §3): found " + javaDir);
            }
        }
        return violations;
    }

    private static void checkFile(Path root, Path file, List<String> violations) {
        String sourcePackage = packageNameOf(root, file);
        if (sourcePackage == null || !PACKAGES.contains(sourcePackage)) {
            return;
        }
        Set<String> allowed = allowedTargets(sourcePackage);
        List<String> lines;
        try {
            lines = Files.readAllLines(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        for (int i = 0; i < lines.size(); i++) {
            Matcher m = IMPORT.matcher(lines.get(i).trim());
            if (!m.find()) {
                continue;
            }
            String target = m.group(1);
            String[] parts = target.split("\\.");
            // com.strife.<pkg>...
            if (parts.length < 3 || !PACKAGES.contains(parts[2])) {
                continue;
            }
            if (!allowed.contains(parts[2])) {
                violations.add(
                        root.relativize(file) + ":" + (i + 1) + " " + sourcePackage + " -> " + parts[2]
                                + " (allowed: " + allowed + ")");
            }
        }
    }

    private static String packageNameOf(Path root, Path file) {
        Path relative = root.relativize(file);
        // <pkg>/.../Foo.java directly under source root only for the mod platform layout
        // com/strife/<pkg>/...: find "strife" segment then take the next directory.
        List<String> segments = new ArrayList<>();
        relative.forEach(s -> segments.add(s.toString()));
        int idx = segments.indexOf("strife");
        if (idx >= 0 && idx + 1 < segments.size() - 1) {
            return segments.get(idx + 1).toLowerCase(Locale.ROOT);
        }
        return null;
    }

    private ModuleDependencyRules() {}
}

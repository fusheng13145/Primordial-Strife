package strife.tools.datagen;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * DataGen entry point (docs/04 §5): tables/*.csv + content/NUMBERS.md -&gt; generated JSON under
 * content-base resources, with {@code @generated} headers and source hashes.
 *
 * <p>Generator registry is empty at A0-1: table shapes come from content/JSON_SCHEMA.md (C's M0
 * ticket), so the wiring here stays a thin CLI that validates arguments and reports what it would
 * consume. Real generators are added in A1/C1 as each table domain is contracted.
 */
public final class DataGenMain {

    public record Options(Path tablesRoot, Path moduleRoot) {}

    public static void main(String[] args) {
        Options options = parse(args);
        List<Path> tables = listCsv(options.tablesRoot());
        System.out.printf(
                "datagen: tables-root=%s module-root=%s csv-found=%d generators=0%n",
                options.tablesRoot(), options.moduleRoot(), tables.size());
    }

    static Options parse(String[] args) {
        String tables = null;
        String module = null;
        for (int i = 0; i < args.length - 1; i++) {
            switch (args[i]) {
                case "--tables-root" -> tables = args[++i];
                case "--module-root" -> module = args[++i];
                default -> {}
            }
        }
        if (tables == null || module == null) {
            throw new IllegalArgumentException(
                    "usage: datagen --tables-root <dir> --module-root <dir>");
        }
        return new Options(Path.of(tables), Path.of(module));
    }

    static List<Path> listCsv(Path tablesRoot) {
        List<Path> result = new ArrayList<>();
        if (!Files.isDirectory(tablesRoot)) {
            return result;
        }
        try (var stream = Files.walk(tablesRoot)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".csv"))
                    .sorted()
                    .forEach(result::add);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "cannot scan tables root " + tablesRoot + ": " + e.getMessage(), e);
        }
        return result;
    }

    private DataGenMain() {}
}

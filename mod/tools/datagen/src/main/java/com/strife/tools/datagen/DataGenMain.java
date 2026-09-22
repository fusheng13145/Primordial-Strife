package com.strife.tools.datagen;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DataGen entry point (docs/04 §5): tables/*.csv + content/NUMBERS.md -&gt; generated JSON under
 * content-base resources, with {@code @generated} headers and source hashes.
 *
 * <p>Every table under {@code tables/} is read, even ones with no generator, because that is the
 * only way to tell "nothing to do" apart from "someone filled this in and it was dropped".
 *
 * <p>NUMBERS.md is not consumed yet: the {@code @@blocks} conventions in 05 §1 need A's sign-off
 * (content/JSON_SCHEMA.md §8 待审项) before a parser can lock the key format.
 */
public final class DataGenMain {

    /**
     * Ledger tables that are input to other tools, not content (docs/04 §6 V-DUP scope): they carry
     * rows by design and must never be expected to have a generator.
     */
    private static final List<String> LEDGER_TABLES =
            List.of("known-placeholders.csv", "id_migration.csv");

    public record Options(Path tablesRoot, Path resourcesRoot) {}

    /**
     * @param problems unfixable-by-rerunning issues; a non-empty list means DataGen cannot produce
     *     a complete content pack from the current tables
     */
    public record Summary(
            int tables, int rows, int generators, int products, List<String> problems) {}

    /** Registered generators, one per contracted table domain. */
    public static List<TableGenerator> generators() {
        return List.of(new FactionGenerator());
    }

    public static void main(String[] args) {
        Options options = parse(args);
        Summary summary = run(options, generators());
        summary.problems().forEach(p -> System.err.println("datagen: " + p));
        System.out.printf(
                "datagen: tables-root=%s resources-root=%s tables=%d rows=%d generators=%d products=%d problems=%d%n",
                options.tablesRoot(),
                options.resourcesRoot(),
                summary.tables(),
                summary.rows(),
                summary.generators(),
                summary.products(),
                summary.problems().size());
        if (!summary.problems().isEmpty()) {
            System.exit(1);
        }
    }

    static Summary run(Options options, List<TableGenerator> registeredGenerators) {
        Map<String, TableGenerator> registered = new LinkedHashMap<>();
        for (TableGenerator generator : registeredGenerators) {
            registered.put(generator.tableFile(), generator);
        }
        List<Product> products = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        int tables = 0;
        int rows = 0;
        for (Path table : listCsv(options.tablesRoot())) {
            TableSource source = TableSource.read(table);
            tables++;
            rows += source.rows().size();
            TableGenerator generator = registered.get(source.fileName());
            if (generator == null) {
                if (!source.rows().isEmpty() && !LEDGER_TABLES.contains(source.fileName())) {
                    problems.add(
                            "tables/"
                                    + source.fileName()
                                    + " has "
                                    + source.rows().size()
                                    + " rows but no generator is registered in DataGenMain.generators()"
                                    + " — this content would silently not exist in game");
                }
                continue;
            }
            products.addAll(generator.generate(source));
        }
        write(products, options.resourcesRoot());
        return new Summary(tables, rows, registered.size(), products.size(), problems);
    }

    /** Writes each product under the resources root; order comes from the sorted table walk. */
    static void write(List<Product> products, Path resourcesRoot) {
        for (Product product : products) {
            Path target = resourcesRoot.resolve(product.relativePath());
            try {
                Files.createDirectories(target.getParent());
                Files.writeString(target, product.toJson());
            } catch (IOException e) {
                throw new UncheckedIOException("cannot write product " + target, e);
            }
        }
    }

    static Options parse(String[] args) {
        String tables = null;
        String resources = null;
        for (int i = 0; i < args.length - 1; i++) {
            switch (args[i]) {
                case "--tables-root" -> tables = args[++i];
                case "--resources-root" -> resources = args[++i];
                default -> {}
            }
        }
        if (tables == null || resources == null) {
            throw new IllegalArgumentException(
                    "usage: datagen --tables-root <dir> --resources-root <dir>");
        }
        return new Options(Path.of(tables), Path.of(resources));
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

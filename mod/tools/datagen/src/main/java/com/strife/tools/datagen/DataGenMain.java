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
 * <p>NUMBERS.md is consumed lazily through {@link NumbersSource}: generators only touch it when a
 * {@code *_key} cell actually needs a value, so a run over empty tables never demands the file —
 * relevant while {@code content/} (A0-7) is still unmerged.
 */
public final class DataGenMain {

    /**
     * Ledger tables that are input to other tools, not content (docs/04 §6 V-DUP scope): they carry
     * rows by design and must never be expected to have a generator.
     */
    private static final List<String> LEDGER_TABLES =
            List.of("known-placeholders.csv", "id_migration.csv");

    public record Options(Path tablesRoot, Path resourcesRoot, Path contentRoot) {}

    /**
     * @param problems unfixable-by-rerunning issues; a non-empty list means DataGen cannot produce
     *     a complete content pack from the current tables
     * @param notices things that were deliberately skipped (with a printed reason), never silent
     */
    public record Summary(
            int tables,
            int rows,
            int generators,
            int numbers,
            int numbersSkipped,
            int products,
            List<String> problems,
            List<String> notices) {}

    /** Registered generators, one per contracted table domain (一章一文件的域每章注册一次). */
    public static List<TableGenerator> generators(NumbersSource numbers) {
        return List.of(
                new FactionGenerator(),
                new TechniqueGenerator(),
                new SpellGenerator(numbers),
                new PillGenerator(numbers),
                new ArtifactGenerator(numbers),
                new QuestGenerator("quests_prologue.csv"),
                new QuestGenerator("quests_ch1.csv"),
                new DialogTreeGenerator("dialog_trees_prologue.csv"),
                new DialogTreeGenerator("dialog_trees_ch1.csv"),
                new DialogTextGenerator("prologue"),
                new DialogTextGenerator("ch1"));
    }

    /** Registered NUMBERS-driven generators (domains with no CSV table, JSON_SCHEMA §4.1). */
    public static List<NumbersGenerator> numbersGenerators() {
        return List.of(
                new RealmsGenerator(),
                new RealmRulesGenerator(),
                new WorldRulesGenerator(),
                new CoreRulesGenerator());
    }

    public static void main(String[] args) {
        Options options = parse(args);
        NumbersSource numbers = NumbersSource.at(options.contentRoot());
        Summary summary = run(options, generators(numbers), numbersGenerators(), numbers);
        summary.notices().forEach(n -> System.out.println("datagen: " + n));
        summary.problems().forEach(p -> System.err.println("datagen: " + p));
        System.out.printf(
                "datagen: tables-root=%s resources-root=%s content-root=%s tables=%d rows=%d generators=%d numbers=%d products=%d problems=%d%n",
                options.tablesRoot(),
                options.resourcesRoot(),
                options.contentRoot(),
                summary.tables(),
                summary.rows(),
                summary.generators(),
                summary.numbers(),
                summary.products(),
                summary.problems().size());
        if (!summary.problems().isEmpty()) {
            System.exit(1);
        }
    }

    static Summary run(
            Options options,
            List<TableGenerator> registeredGenerators,
            List<NumbersGenerator> registeredNumbersGenerators,
            NumbersSource numbers) {
        Map<String, TableGenerator> registered = new LinkedHashMap<>();
        for (TableGenerator generator : registeredGenerators) {
            registered.put(generator.tableFile(), generator);
        }
        List<Product> products = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        List<String> notices = new ArrayList<>();
        int tables = 0;
        int rows = 0;
        int numbersSkipped = 0;
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
        if (numbers.available()) {
            for (NumbersGenerator numbersGenerator : registeredNumbersGenerators) {
                products.addAll(numbersGenerator.generate(numbers));
            }
        } else {
            numbersSkipped = registeredNumbersGenerators.size();
            notices.add(
                    "NUMBERS-driven generation skipped — content/NUMBERS.md is not present yet"
                            + " (A0-7 truth source unmerged); "
                            + numbersSkipped
                            + " generator(s) arm themselves when it lands");
        }
        write(products, options.resourcesRoot());
        return new Summary(
                tables,
                rows,
                registered.size(),
                registeredNumbersGenerators.size() - numbersSkipped,
                numbersSkipped,
                products.size(),
                problems,
                notices);
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
        String content = null;
        for (int i = 0; i < args.length - 1; i++) {
            switch (args[i]) {
                case "--tables-root" -> tables = args[++i];
                case "--resources-root" -> resources = args[++i];
                case "--content-root" -> content = args[++i];
                default -> {}
            }
        }
        if (tables == null || resources == null) {
            throw new IllegalArgumentException(
                    "usage: datagen --tables-root <dir> --resources-root <dir> [--content-root <dir>]");
        }
        return new Options(
                Path.of(tables), Path.of(resources), content == null ? null : Path.of(content));
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

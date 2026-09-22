package com.strife.tools.validator;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Build-time content validator (docs/04 §6). Runs on plain JVM, never launches Minecraft.
 *
 * <p>Implemented so far: V-DUP (global ID uniqueness, checked on both generated content and the
 * source tables) and V-FRESH (the {@code @generated} header must name an existing table whose
 * current hash it carries). Reference existence, DAG connectivity, probability normalisation,
 * NUMBERS range checks and DSL legality are tracked by later tickets and register here as {@link
 * Check} implementations.
 */
public final class ValidatorMain {

    /**
     * Ledger tables whose first column is a record key, not a content ID (docs/04 §6 V-DUP scope):
     * placeholder whitelists and rename migrations intentionally reuse names that exist elsewhere.
     */
    private static final List<String> NON_CONTENT_TABLES =
            List.of("known-placeholders.csv", "id_migration.csv");

    @FunctionalInterface
    public interface Check {
        List<String> run(Options options);
    }

    record Options(Path dataRoot, Path tablesRoot) {}

    /**
     * Where an ID was declared: the table it came from plus a human-addressable location.
     *
     * @param origin the source table for generated content, or the file itself when a product has
     *     no {@code @generated} header (i.e. it was hand-edited, which 04 §1 forbids)
     * @param row true for a source table row, false for a generated product
     */
    private record Declaration(String origin, String location, boolean row) {}

    /**
     * Content IDs are unique across every domain (docs/04 §6 "重复 ID｜全域唯一"), because lang keys map
     * one-to-one onto them (04 §4) and a second meaning for the same key would silently win by load
     * order.
     *
     * <p>Rows and the products generated from them describe one declaration each, so duplicates are
     * counted per origin rather than per raw occurrence — otherwise a successful DataGen run would
     * make every generated file collide with its own source row, and the gate could never go green
     * once real content lands.
     */
    public static List<String> duplicateIds(Options options) {
        Map<String, List<Declaration>> seen = new LinkedHashMap<>();
        for (Path file : jsonFiles(options.dataRoot())) {
            JsonElement root = parse(file);
            if (!root.isJsonObject()) {
                continue;
            }
            JsonObject object = root.getAsJsonObject();
            if (!object.has("id") || !object.get("id").isJsonPrimitive()) {
                continue;
            }
            seen.computeIfAbsent(object.get("id").getAsString(), k -> new ArrayList<>())
                    .add(new Declaration(originOf(object, file), file.toString(), false));
        }
        List<String> problems = new ArrayList<>(tableIds(options.tablesRoot(), seen));
        seen.forEach(
                (id, declarations) -> {
                    problems.addAll(crossOriginClashes(id, declarations));
                    problems.addAll(withinOneOrigin(id, declarations));
                });
        return problems;
    }

    private static List<String> crossOriginClashes(String id, List<Declaration> declarations) {
        Map<String, List<String>> byOrigin = new LinkedHashMap<>();
        declarations.forEach(
                d ->
                        byOrigin.computeIfAbsent(d.origin(), k -> new ArrayList<>())
                                .add(d.location()));
        if (byOrigin.size() < 2) {
            return List.of();
        }
        StringBuilder where = new StringBuilder();
        byOrigin.forEach(
                (origin, locations) ->
                        where.append(where.isEmpty() ? "" : " vs ")
                                .append(origin)
                                .append(" ")
                                .append(locations));
        return List.of(
                "duplicate id '"
                        + id
                        + "' declared by "
                        + byOrigin.size()
                        + " independent sources: "
                        + where);
    }

    /** One row may generate exactly one product; anything beyond that is a second declaration. */
    private static List<String> withinOneOrigin(String id, List<Declaration> declarations) {
        Map<String, List<Declaration>> byOrigin = new LinkedHashMap<>();
        declarations.forEach(
                d -> byOrigin.computeIfAbsent(d.origin(), k -> new ArrayList<>()).add(d));
        List<String> problems = new ArrayList<>();
        byOrigin.forEach(
                (origin, group) -> {
                    List<String> rows =
                            group.stream()
                                    .filter(Declaration::row)
                                    .map(Declaration::location)
                                    .toList();
                    List<String> products =
                            group.stream()
                                    .filter(d -> !d.row())
                                    .map(Declaration::location)
                                    .toList();
                    if (rows.size() > 1) {
                        problems.add(
                                "duplicate id '"
                                        + id
                                        + "' declared "
                                        + rows.size()
                                        + " times within "
                                        + origin
                                        + " at "
                                        + rows);
                    }
                    if (products.size() > Math.max(rows.size(), 1)) {
                        problems.add(
                                "duplicate id '"
                                        + id
                                        + "' has "
                                        + products.size()
                                        + " generated files for "
                                        + rows.size()
                                        + " source row(s) in "
                                        + origin
                                        + " at "
                                        + products
                                        + " — a product was hand-copied, or the table row is gone"
                                        + " (04 §1: 产物永远不被手工编辑)");
                    }
                });
        return problems;
    }

    private static String originOf(JsonObject object, Path file) {
        if (!object.has("@generated") || !object.get("@generated").isJsonPrimitive()) {
            return file.toString();
        }
        String header = object.get("@generated").getAsString();
        int from = header.indexOf("tables/");
        return from < 0 ? file.toString() : header.substring(from).split(" ")[0];
    }

    /**
     * Source tables are read here because DataGen products may be absent (a missing generated file
     * must never turn V-DUP green while the table it comes from already collides).
     */
    private static List<String> tableIds(Path tablesRoot, Map<String, List<Declaration>> seen) {
        List<String> problems = new ArrayList<>();
        if (tablesRoot == null) {
            return problems;
        }
        for (Path table : csvFiles(tablesRoot)) {
            String fileName = table.getFileName().toString();
            if (NON_CONTENT_TABLES.contains(fileName)) {
                continue;
            }
            List<String> lines = readLines(table);
            if (lines.isEmpty()) {
                problems.add(
                        table + ": empty file, expected a header row whose first column is 'id'");
                continue;
            }
            List<String> header = splitRow(lines.get(0));
            if (header.isEmpty() || !"id".equals(header.get(0))) {
                problems.add(
                        table
                                + ":"
                                + "first header column is "
                                + (header.isEmpty() ? "<none>" : "'" + header.get(0) + "'")
                                + ", must be 'id' (content/JSON_SCHEMA.md §2)");
                continue;
            }
            for (int row = 1; row < lines.size(); row++) {
                String line = lines.get(row);
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                List<String> cells = splitRow(line);
                if (cells.isEmpty() || cells.get(0).isBlank()) {
                    problems.add(table + ":" + (row + 1) + ": row has no id");
                    continue;
                }
                seen.computeIfAbsent(cells.get(0), k -> new ArrayList<>())
                        .add(new Declaration("tables/" + fileName, table + ":" + (row + 1), true));
            }
        }
        return problems;
    }

    /**
     * V-FRESH (docs/04 §6 "产物新鲜"): every product must name a source table that still exists and
     * carry that table's current hash, so an edited table with an uncommitted regeneration cannot
     * pass.
     */
    public static List<String> staleGeneratedHeaders(Options options) {
        if (options.tablesRoot() == null) {
            return List.of();
        }
        List<String> problems = new ArrayList<>();
        for (Path file : jsonFiles(options.dataRoot())) {
            JsonElement root = parse(file);
            if (!root.isJsonObject()) {
                continue;
            }
            JsonObject object = root.getAsJsonObject();
            if (!object.has("@generated") || !object.get("@generated").isJsonPrimitive()) {
                continue;
            }
            String header = object.get("@generated").getAsString();
            int from = header.indexOf("tables/");
            if (from < 0) {
                problems.add(file + ": @generated header names no source table: '" + header + "'");
                continue;
            }
            String source = header.substring(from + "tables/".length()).split(" ")[0];
            Path table = options.tablesRoot().resolve(source);
            if (!Files.isRegularFile(table)) {
                problems.add(
                        file
                                + ": @generated names tables/"
                                + source
                                + ", which no longer exists — rerun DataGen (docs/04 §6 产物新鲜)");
                continue;
            }
            int marker = header.indexOf("sha256:");
            if (marker < 0) {
                problems.add(
                        file
                                + ": @generated names tables/"
                                + source
                                + " but carries no source hash, so freshness cannot be checked"
                                + " (docs/04 §5 requires it): '"
                                + header
                                + "'");
                continue;
            }
            String claimed = header.substring(marker + "sha256:".length()).trim();
            String actual = hashOf(table);
            if (!claimed.equals(actual)) {
                problems.add(
                        file
                                + ": @generated claims sha256:"
                                + claimed
                                + " but tables/"
                                + source
                                + " is now sha256:"
                                + actual
                                + " — the table changed without regenerating (docs/04 §1:"
                                + " 产物永远不被手工编辑)");
            }
        }
        return problems;
    }

    public static List<Check> checks() {
        return List.of(ValidatorMain::duplicateIds, ValidatorMain::staleGeneratedHeaders);
    }

    public static void main(String[] args) {
        Options options = parseArgs(args);
        List<String> problems = new ArrayList<>();
        int executed = 0;
        for (Check check : checks()) {
            problems.addAll(check.run(options));
            executed++;
        }
        problems.forEach(p -> System.err.println("validator: " + p));
        System.out.printf(
                "validator: data-root=%s tables-root=%s json-files=%d csv-files=%d checks=%d problems=%d%n",
                options.dataRoot(),
                options.tablesRoot(),
                countJson(options.dataRoot()),
                options.tablesRoot() == null ? 0 : csvFiles(options.tablesRoot()).size(),
                executed,
                problems.size());
        if (!problems.isEmpty()) {
            System.exit(1);
        }
    }

    static Options parseArgs(String[] args) {
        Path dataRoot = null;
        Path tablesRoot = null;
        for (int i = 0; i < args.length - 1; i++) {
            switch (args[i]) {
                case "--data-root" -> dataRoot = Path.of(args[++i]);
                case "--tables-root" -> tablesRoot = Path.of(args[++i]);
                default -> {}
            }
        }
        if (dataRoot == null) {
            throw new IllegalArgumentException(
                    "usage: validator --data-root <dir> [--tables-root <dir>]");
        }
        if (tablesRoot != null && !Files.isDirectory(tablesRoot)) {
            throw new IllegalArgumentException(
                    "--tables-root "
                            + tablesRoot
                            + " is not a directory (typo? CI must not skip the source tables)");
        }
        return new Options(dataRoot, tablesRoot);
    }

    /**
     * CSV cells carry no commas (tables/FILLING_GUIDE.md §1.1), so a plain split is the contract.
     */
    private static List<String> splitRow(String line) {
        List<String> cells = new ArrayList<>();
        for (String cell : line.split(",", -1)) {
            cells.add(cell.trim());
        }
        return cells;
    }

    /**
     * Recomputed here rather than imported from :tools:datagen, because the validator has to be
     * able to audit a content zip on its own — trusting the generator's own digest code would make
     * the check circular. Same algorithm, one field, deliberately duplicated.
     */
    private static String hashOf(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(file)));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot hash table " + file, e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 missing from this JVM", e);
        }
    }

    private static List<String> readLines(Path file) {
        try {
            return Files.readAllLines(file);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read table " + file, e);
        }
    }

    private static List<Path> csvFiles(Path root) {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        return walk(root).filter(p -> p.getFileName().toString().endsWith(".csv")).toList();
    }

    private static List<Path> jsonFiles(Path root) {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        return walk(root).filter(p -> p.getFileName().toString().endsWith(".json")).toList();
    }

    private static java.util.stream.Stream<Path> walk(Path root) {
        try (var stream = Files.walk(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(Path::toString))
                    .toList()
                    .stream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static int countJson(Path root) {
        return jsonFiles(root).size();
    }

    private static JsonElement parse(Path file) {
        try (var reader = Files.newBufferedReader(file)) {
            return JsonParser.parseReader(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read content file " + file, e);
        }
    }

    private ValidatorMain() {}
}

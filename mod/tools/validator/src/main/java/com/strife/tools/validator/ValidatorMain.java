package com.strife.tools.validator;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
 * source tables), V-FRESH (the {@code @generated} header must name an existing table whose current
 * hash it carries) and the growth-ratio sub-item of V-RANGE (adjacent-realm {@code qi_max} ratios,
 * with bounds read from NUMBERS §2 @@limits — see {@link #growthRatio(Options)}). Reference
 * existence, DAG connectivity, probability normalisation, the remaining V-RANGE sub-items, text
 * coverage and DSL legality are tracked by later tickets and register here as {@link Check}
 * implementations.
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

    record Options(Path dataRoot, Path tablesRoot, Path contentRoot, Path assetsRoot) {}

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

    /**
     * The source named by a product's {@code @generated} header: {@code tables/<file>.csv} or
     * {@code content/NUMBERS.md} (NUMBERS-derived domains like strife_realms, JSON_SCHEMA §4.1), or
     * {@code null} when there is no {@code from <source>} prefix at all (hand-edited, 04 §1). A
     * header that names a source but carries no {@code @ sha256:…} still parses here, so V-FRESH
     * can diagnose the missing hash instead of a vague "no source".
     */
    private static String headerSource(JsonObject object) {
        if (!object.has("@generated") || !object.get("@generated").isJsonPrimitive()) {
            return null;
        }
        String header = object.get("@generated").getAsString();
        if (!header.startsWith("from ")) {
            return null;
        }
        int at = header.indexOf(" @ ");
        String source =
                at < 0
                        ? header.substring("from ".length())
                        : header.substring("from ".length(), at);
        return source.trim();
    }

    private static String originOf(JsonObject object, Path file) {
        String source = headerSource(object);
        return source == null ? file.toString() : source;
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
     * V-FRESH (docs/04 §6 "产物新鲜"): every product must name a source that still exists and carry
     * that source's current hash, so an edited source with an uncommitted regeneration cannot pass.
     *
     * <p>Sources are {@code tables/<file>.csv} (resolved against {@code --tables-root}) and {@code
     * content/NUMBERS.md} (against {@code --content-root}). While the truth source is unmerged the
     * latter cannot be verified: {@link #main} prints a notice instead of failing, and the check
     * arms itself the moment {@code contentRoot} is wired — an unverified pass is never silent.
     */
    public static List<String> staleGeneratedHeaders(Options options) {
        List<String> problems = new ArrayList<>();
        for (Path file : jsonFiles(options.dataRoot())) {
            JsonElement root = parse(file);
            if (!root.isJsonObject()) {
                continue;
            }
            JsonObject object = root.getAsJsonObject();
            String source = headerSource(object);
            if (source == null) {
                if (object.has("@generated")) {
                    problems.add(
                            file
                                    + ": @generated header names no source (expected 'from "
                                    + "tables/<file> @ sha256:<hex>' or 'from content/NUMBERS.md"
                                    + " @ sha256:<hex>'): '"
                                    + object.get("@generated").getAsString()
                                    + "'");
                }
                continue;
            }
            Path sourceRoot = resolveSourceRoot(options, source);
            if (sourceRoot == null) {
                if (source.startsWith("content/")) {
                    continue; // content/ unmerged: main() prints the cannot-verify notice
                }
                problems.add(
                        file
                                + ": @generated names '"
                                + source
                                + "' but no root is wired for it (tables/ needs --tables-root,"
                                + " content/ needs --content-root)");
                continue;
            }
            Path sourceFile = sourceRoot.resolve(source.replaceFirst("^(tables|content)/", ""));
            if (!Files.isRegularFile(sourceFile)) {
                problems.add(
                        file
                                + ": @generated names "
                                + source
                                + ", which no longer exists — rerun DataGen (docs/04 §6 产物新鲜)");
                continue;
            }
            String header = object.get("@generated").getAsString();
            int marker = header.indexOf("sha256:");
            if (marker < 0) {
                problems.add(
                        file
                                + ": @generated names "
                                + source
                                + " but carries no source hash, so freshness cannot be checked"
                                + " (docs/04 §5 requires it): '"
                                + header
                                + "'");
                continue;
            }
            String claimed = header.substring(marker + "sha256:".length()).trim();
            String actual = hashOf(sourceFile);
            if (!claimed.equals(actual)) {
                problems.add(
                        file
                                + ": @generated claims sha256:"
                                + claimed
                                + " but "
                                + source
                                + " is now sha256:"
                                + actual
                                + " — the source changed without regenerating (docs/04 §1:"
                                + " 产物永远不被手工编辑)");
            }
        }
        return problems;
    }

    /** Root for a header source, or null when that root is not wired yet (content/ unmerged). */
    private static Path resolveSourceRoot(Options options, String source) {
        if (source.startsWith("tables/")) {
            return options.tablesRoot();
        }
        if (source.startsWith("content/")) {
            return options.contentRoot() != null && Files.isDirectory(options.contentRoot())
                    ? options.contentRoot()
                    : null;
        }
        return null;
    }

    /** True when any committed product was generated from the (possibly unmerged) truth source. */
    static boolean hasUnverifiableNumbersProducts(Options options) {
        if (options.contentRoot() != null
                && Files.isRegularFile(options.contentRoot().resolve("NUMBERS.md"))) {
            return false;
        }
        for (Path file : jsonFiles(options.dataRoot())) {
            JsonElement root = parse(file);
            if (root.isJsonObject() && isNumbersSourced(root.getAsJsonObject())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNumbersSourced(JsonObject object) {
        String source = headerSource(object);
        return source != null && source.startsWith("content/");
    }

    /**
     * V-TEXT lang coverage (docs/04 §4/§6, the C1-2 gate): every generated content ID must have its
     * registered lang key present and non-empty in zh_cn (源语言) and en_us (占位但不得空串).
     *
     * <p>Required keys are derived from the product's domain, using the prefix table registered in
     * JSON_SCHEMA §4.10 — quests/dialog trees are一章一文件 and get their key rules with M3's
     * generators, so they are skipped here for now. A content ID without a contract prefix would be
     * silent in game, which is exactly what this check exists to stop.
     */
    public static List<String> langCoverage(Options options) {
        Path assets = options.assetsRoot();
        if (assets == null || !Files.isDirectory(assets)) {
            return List.of();
        }
        Path zh = assets.resolve("strife/lang/zh_cn.json");
        Path en = assets.resolve("strife/lang/en_us.json");
        if (!Files.isRegularFile(zh) || !Files.isRegularFile(en)) {
            return List.of(
                    assets
                            + ": strife/lang/zh_cn.json and en_us.json must both exist once content"
                            + " products are generated (docs/04 §4: zh_cn 缺失 = 构建失败)");
        }
        Map<String, Object> zhKeys = langEntries(zh);
        Map<String, Object> enKeys = langEntries(en);
        List<String> problems = new ArrayList<>();
        for (Path file : jsonFiles(options.dataRoot())) {
            JsonElement root = parse(file);
            if (!root.isJsonObject()) {
                continue;
            }
            JsonObject object = root.getAsJsonObject();
            if (!object.has("id") || !object.get("id").isJsonPrimitive()) {
                continue;
            }
            String key = requiredLangKey(options.dataRoot(), file, object);
            if (key == null) {
                continue;
            }
            problems.addAll(langProblems(zhKeys, zh, key, "zh_cn"));
            problems.addAll(langProblems(enKeys, en, key, "en_us"));
        }
        return problems;
    }

    /**
     * The lang key a product demands, per the §4.10 prefix registry; null = no rule for this domain
     * yet (quests/dialog land with M3, non-strife namespaces use vanilla semantics).
     */
    private static String requiredLangKey(Path dataRoot, Path file, JsonObject object) {
        String path = dataRoot.relativize(file).toString().replace('\\', '/');
        String[] parts = path.split("/");
        if (parts.length < 3 || !"strife".equals(parts[0])) {
            return null;
        }
        String id = object.get("id").getAsString();
        return switch (parts[1]) {
            case "strife_realms" -> "realm.strife." + id;
            case "strife_techniques" -> "technique.strife." + id;
            case "strife_spells" -> "spell.strife." + id;
            case "strife_pills" -> "item.strife." + id;
            case "strife_artifacts" -> "artifact.strife." + id;
            case "strife_factions" ->
                    object.has("display_name_key")
                                    && object.get("display_name_key").isJsonPrimitive()
                            ? object.get("display_name_key").getAsString()
                            : null;
            default -> null;
        };
    }

    private static List<String> langProblems(
            Map<String, Object> entries, Path file, String key, String lang) {
        Object value = entries.get(key);
        if (value instanceof String s && !s.isBlank()) {
            return List.of();
        }
        return List.of(
                file
                        + ": lang key '"
                        + key
                        + "' is missing or blank in "
                        + lang
                        + " — every content ID maps one-to-one onto a lang key"
                        + " (docs/04 §4; en_us may be a placeholder but never an empty string)");
    }

    private static Map<String, Object> langEntries(Path file) {
        try (var reader = Files.newBufferedReader(file)) {
            JsonElement root = JsonParser.parseReader(reader);
            Map<String, Object> entries = new LinkedHashMap<>();
            if (root.isJsonObject()) {
                root.getAsJsonObject()
                        .entrySet()
                        .forEach(
                                e ->
                                        entries.put(
                                                e.getKey(),
                                                e.getValue().isJsonPrimitive()
                                                        ? e.getValue().getAsString()
                                                        : null));
            }
            return entries;
        } catch (IOException | com.google.gson.JsonSyntaxException e) {
            throw new IllegalStateException(
                    "cannot read lang file " + file + ": " + e.getMessage(), e);
        }
    }

    /**
     * V-RANGE growth-ratio sub-item (docs/04 §6; the A0-7 acceptance check): the {@code qi_max} of
     * adjacent realms in the NUMBERS §1 chain must stay inside the bounds NUMBERS itself declares
     * in §2 @@limits.
     *
     * <p>The bounds are managed numbers, so they are read from the truth source at run time and
     * never written here — a hardcoded 1.8/2.5 in this class would be exactly the "second copy of a
     * managed number" AGENTS.md forbids. When {@code content/NUMBERS.md} is not merged yet the
     * check has nothing to audit: {@link #main} prints a skip notice instead of failing, and the
     * gate arms itself the moment the file lands.
     */
    public static List<String> growthRatio(Options options) {
        Path numbers = NumbersBlocks.numbersFile(options.contentRoot());
        if (numbers == null) {
            return List.of();
        }
        List<String> problems = new ArrayList<>();
        List<NumbersBlocks.Line> limits;
        List<NumbersBlocks.Line> realms;
        try {
            limits = NumbersBlocks.yamlBlock(numbers, "limits");
            realms = NumbersBlocks.yamlBlock(numbers, "realms");
        } catch (IllegalStateException e) {
            return List.of(numbers + ": " + e.getMessage());
        }
        BigDecimal min = null;
        BigDecimal max = null;
        for (NumbersBlocks.Line line : limits) {
            String content = NumbersBlocks.stripComment(line.text()).trim();
            if (content.isEmpty() || content.startsWith("#")) {
                continue;
            }
            int colon = content.indexOf(':');
            String key = colon < 0 ? content : content.substring(0, colon).trim();
            String value = colon < 0 ? null : content.substring(colon + 1).trim();
            if ("growth_ratio_min".equals(key)) {
                min = parseBound(numbers, line, value, problems);
            } else if ("growth_ratio_max".equals(key)) {
                max = parseBound(numbers, line, value, problems);
            }
        }
        if (min == null) {
            problems.add(
                    numbers
                            + ": @@limits declares no growth_ratio_min, so the growth-ratio gate"
                            + " has no lower bound to enforce (NUMBERS §2)");
        }
        if (max == null) {
            problems.add(
                    numbers
                            + ": @@limits declares no growth_ratio_max, so the growth-ratio gate"
                            + " has no upper bound to enforce (NUMBERS §2)");
        }
        record Realm(String id, long qiMax) {}
        List<Realm> chain = new ArrayList<>();
        for (NumbersBlocks.Line line : realms) {
            String content = NumbersBlocks.stripComment(line.text()).trim();
            if (content.isEmpty() || content.startsWith("#")) {
                continue;
            }
            int colon = content.indexOf(':');
            if (colon <= 0 || !content.substring(colon + 1).trim().startsWith("{")) {
                problems.add(
                        numbers
                                + ":"
                                + line.number()
                                + ": cannot parse @@realms entry '"
                                + content
                                + "' — expected 'id: { qi_max: <int>, ... }' (NUMBERS §1)");
                continue;
            }
            String id = content.substring(0, colon).trim();
            String qiMax = flowValue(content.substring(colon + 1).trim(), "qi_max");
            if (qiMax == null) {
                problems.add(
                        numbers
                                + ":"
                                + line.number()
                                + ": @@realms entry '"
                                + id
                                + "' has no qi_max, so its growth ratio cannot be audited"
                                + " (NUMBERS §1)");
                continue;
            }
            try {
                chain.add(new Realm(id, Long.parseLong(qiMax)));
            } catch (NumberFormatException e) {
                problems.add(
                        numbers
                                + ":"
                                + line.number()
                                + ": @@realms entry '"
                                + id
                                + "' has a non-integer qi_max '"
                                + qiMax
                                + "' (NUMBERS §1)");
            }
        }
        for (int i = 1; i < chain.size(); i++) {
            Realm previous = chain.get(i - 1);
            Realm current = chain.get(i);
            if (previous.qiMax <= 0) {
                problems.add(
                        numbers
                                + ": @@realms '"
                                + previous.id
                                + "' has qi_max "
                                + previous.qiMax
                                + ", which cannot form a growth ratio (NUMBERS §1)");
                continue;
            }
            BigDecimal ratio =
                    BigDecimal.valueOf(current.qiMax)
                            .divide(BigDecimal.valueOf(previous.qiMax), 4, RoundingMode.HALF_UP);
            if (min != null && ratio.compareTo(min) < 0) {
                problems.add(
                        numbers
                                + ": growth ratio "
                                + current.id
                                + "/"
                                + previous.id
                                + " = "
                                + ratio
                                + " is below growth_ratio_min "
                                + min
                                + " (bounds from NUMBERS §2 @@limits; docs/05 §3)");
            }
            if (max != null && ratio.compareTo(max) > 0) {
                problems.add(
                        numbers
                                + ": growth ratio "
                                + current.id
                                + "/"
                                + previous.id
                                + " = "
                                + ratio
                                + " exceeds growth_ratio_max "
                                + max
                                + " (bounds from NUMBERS §2 @@limits; docs/05 §3)");
            }
        }
        return problems;
    }

    /** Reads one {@code k: v} pair out of a one-level flow mapping like {@code { qi_max: 100 }}. */
    private static String flowValue(String flowMapping, String key) {
        for (String part :
                flowMapping.replaceFirst("^\\{", "").replaceFirst("\\}\\s*$", "").split(",")) {
            int colon = part.indexOf(':');
            if (colon > 0 && part.substring(0, colon).trim().equals(key)) {
                return part.substring(colon + 1).trim();
            }
        }
        return null;
    }

    private static BigDecimal parseBound(
            Path numbers, NumbersBlocks.Line line, String value, List<String> problems) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException | NullPointerException e) {
            problems.add(
                    numbers
                            + ":"
                            + line.number()
                            + ": @@limits bound is not a number: '"
                            + value
                            + "' (NUMBERS §2)");
            return null;
        }
    }

    /**
     * V-PROB (docs/04 §6 概率归一): probability lists in products must sum to 1 within the contracted
     * tolerance of 1e-6 (JSON_SCHEMA §4.4 outputs / §4.5 quality_probs — the tolerance is a checker
     * parameter from the contract, not a managed game number). Domains without probability arrays
     * are skipped by shape.
     */
    public static List<String> probabilitySum(Options options) {
        List<String> problems = new ArrayList<>();
        for (Path file : jsonFiles(options.dataRoot())) {
            JsonElement root = parse(file);
            if (!root.isJsonObject()) {
                continue;
            }
            String field = probabilityField(options.dataRoot().relativize(file).toString());
            if (field == null) {
                continue;
            }
            JsonObject object = root.getAsJsonObject();
            if (!object.has(field) || !object.get(field).isJsonArray()) {
                continue;
            }
            double sum = 0;
            int entries = 0;
            for (JsonElement element : object.getAsJsonArray(field)) {
                if (element.isJsonObject() && element.getAsJsonObject().has("prob")) {
                    sum += element.getAsJsonObject().get("prob").getAsDouble();
                    entries++;
                }
            }
            if (entries > 0 && Math.abs(sum - 1.0) > 1e-6) {
                problems.add(
                        file
                                + ": "
                                + field
                                + " probabilities sum to "
                                + sum
                                + " across "
                                + entries
                                + " entries, must be 1 within 1e-6 (docs/04 §6 V-PROB)");
            }
        }
        return problems;
    }

    /** The probability array field a domain carries, or null when it carries none. */
    private static String probabilityField(String relativePath) {
        String path = relativePath.replace('\\', '/');
        String[] parts = path.split("/");
        if (parts.length < 2 || !"strife".equals(parts[0])) {
            return null;
        }
        return switch (parts[1]) {
            case "strife_pills" -> "outputs";
            case "strife_artifacts" -> "quality_probs";
            default -> null;
        };
    }

    /**
     * V-RANGE sub-items on products (docs/04 §6): H1 prices must be non-negative (docs/03 §10).
     * Further numeric ranges live in the truth source and are checked by {@link
     * #truthSourceRanges}.
     */
    public static List<String> numericRanges(Options options) {
        List<String> problems = new ArrayList<>();
        for (Path file : jsonFiles(options.dataRoot())) {
            JsonElement root = parse(file);
            if (!root.isJsonObject()) {
                continue;
            }
            JsonObject object = root.getAsJsonObject();
            if (!object.has("price") || !object.get("price").isJsonObject()) {
                continue;
            }
            JsonObject price = object.getAsJsonObject("price");
            if (!price.has("count") || !price.get("count").isJsonPrimitive()) {
                continue;
            }
            long count = price.get("count").getAsLong();
            if (count < 0) {
                problems.add(
                        file
                                + ": price count "
                                + count
                                + " is negative — H1 prices are non-negative (docs/03 §10,"
                                + " V-RANGE)");
            }
        }
        return problems;
    }

    /**
     * V-RANGE sub-items on the truth source itself (docs/04 §6), gated on content/ being merged
     * like V-GROWTH: success rates stay inside the success_rate bounds NUMBERS declares, and
     * lifespan_years must strictly increase along the realm chain — with the [占位] relaxation the
     * contract grants for the unnamed later realms (JSON_SCHEMA §4.1 disabled_by_placeholder).
     */
    public static List<String> truthSourceRanges(Options options) {
        Path numbers = NumbersBlocks.numbersFile(options.contentRoot());
        if (numbers == null) {
            return List.of();
        }
        List<String> problems = new ArrayList<>();
        List<NumbersBlocks.Line> limits;
        List<NumbersBlocks.Line> breakthrough;
        List<NumbersBlocks.Line> realms;
        try {
            limits = NumbersBlocks.yamlBlock(numbers, "limits");
            breakthrough = NumbersBlocks.yamlBlock(numbers, "breakthrough");
            realms = NumbersBlocks.yamlBlock(numbers, "realms");
        } catch (IllegalStateException e) {
            return List.of(numbers + ": " + e.getMessage());
        }
        BigDecimal rateMin = null;
        BigDecimal rateMax = null;
        for (NumbersBlocks.Line line : limits) {
            String content = NumbersBlocks.stripComment(line.text()).trim();
            if (content.isEmpty() || content.startsWith("#")) {
                continue;
            }
            int colon = content.indexOf(':');
            String key = colon < 0 ? content : content.substring(0, colon).trim();
            String value = colon < 0 ? null : content.substring(colon + 1).trim();
            if ("success_rate_min".equals(key)) {
                rateMin = parseBound(numbers, line, value, problems);
            } else if ("success_rate_max".equals(key)) {
                rateMax = parseBound(numbers, line, value, problems);
            }
        }
        if (rateMin == null || rateMax == null) {
            problems.add(
                    numbers
                            + ": @@limits declares no success_rate_min/max, so success rates"
                            + " cannot be range-checked (NUMBERS §2)");
        }
        for (NumbersBlocks.Line line : breakthrough) {
            String content = NumbersBlocks.stripComment(line.text()).trim();
            if (content.isEmpty() || content.startsWith("#")) {
                continue;
            }
            int colon = content.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = content.substring(0, colon).trim();
            for (String field : List.of("base", "floor")) {
                String raw = flowValue(content.substring(colon + 1).trim(), field);
                if (raw == null) {
                    continue;
                }
                try {
                    BigDecimal rate = new BigDecimal(raw);
                    if (rateMin != null && rate.compareTo(rateMin) < 0
                            || rateMax != null && rate.compareTo(rateMax) > 0) {
                        problems.add(
                                numbers
                                        + ":"
                                        + line.number()
                                        + ": @@breakthrough '"
                                        + key
                                        + "' has "
                                        + field
                                        + " "
                                        + rate
                                        + " outside success_rate bounds ["
                                        + rateMin
                                        + ", "
                                        + rateMax
                                        + "] (NUMBERS §2/§3, V-RANGE)");
                    }
                } catch (NumberFormatException e) {
                    problems.add(
                            numbers
                                    + ":"
                                    + line.number()
                                    + ": @@breakthrough '"
                                    + key
                                    + "' has a non-numeric "
                                    + field
                                    + " '"
                                    + raw
                                    + "' (NUMBERS §3)");
                }
            }
        }
        Long previousLifespan = null;
        String previousId = null;
        boolean previousPlaceholder = false;
        for (NumbersBlocks.Line line : realms) {
            String text = line.text();
            String content = NumbersBlocks.stripComment(text).trim();
            if (content.isEmpty() || content.startsWith("#")) {
                continue;
            }
            int colon = content.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String id = content.substring(0, colon).trim();
            String raw = flowValue(content.substring(colon + 1).trim(), "lifespan_years");
            if (raw == null) {
                continue; // V-GROWTH already reports unparseable realm entries
            }
            boolean placeholder = text.contains("[占位]");
            try {
                long lifespan = Long.parseLong(raw);
                if (previousLifespan != null
                        && !placeholder
                        && !previousPlaceholder
                        && lifespan <= previousLifespan) {
                    problems.add(
                            numbers
                                    + ":"
                                    + line.number()
                                    + ": @@realms '"
                                    + id
                                    + "' lifespan_years "
                                    + lifespan
                                    + " does not increase over '"
                                    + previousId
                                    + "' ("
                                    + previousLifespan
                                    + ") — 寿元随境界单调递增 (docs/05 §4, V-RANGE)");
                }
                previousLifespan = lifespan;
                previousId = id;
                previousPlaceholder = placeholder;
            } catch (NumberFormatException e) {
                problems.add(
                        numbers
                                + ":"
                                + line.number()
                                + ": @@realms '"
                                + id
                                + "' has a non-integer lifespan_years '"
                                + raw
                                + "' (NUMBERS §1)");
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
        List<String> notices = new ArrayList<>();
        int executed = 0;
        for (Check check : checks()) {
            problems.addAll(check.run(options));
            executed++;
        }
        if (NumbersBlocks.numbersFile(options.contentRoot()) != null) {
            problems.addAll(growthRatio(options));
            problems.addAll(truthSourceRanges(options));
            executed += 2;
        } else {
            notices.add(
                    "V-GROWTH / V-RANGE(truth source) skipped — content/NUMBERS.md is not present"
                            + " yet (A0-7 truth source unmerged); the checks arm themselves when it"
                            + " lands");
        }
        if (hasUnverifiableNumbersProducts(options)) {
            notices.add(
                    "V-FRESH cannot verify products generated from content/NUMBERS.md while the"
                            + " truth source is unmerged (A0-7) — freshness re-arms when it lands");
        }
        if (options.assetsRoot() != null && Files.isDirectory(options.assetsRoot())) {
            problems.addAll(langCoverage(options));
            executed++;
        } else {
            notices.add("V-TEXT skipped — no --assets-root given, lang coverage unchecked");
        }
        problems.addAll(probabilitySum(options));
        executed++;
        problems.addAll(numericRanges(options));
        executed++;
        notices.forEach(n -> System.out.println("validator: " + n));
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
        Path contentRoot = null;
        Path assetsRoot = null;
        for (int i = 0; i < args.length - 1; i++) {
            switch (args[i]) {
                case "--data-root" -> dataRoot = Path.of(args[++i]);
                case "--tables-root" -> tablesRoot = Path.of(args[++i]);
                case "--content-root" -> contentRoot = Path.of(args[++i]);
                case "--assets-root" -> assetsRoot = Path.of(args[++i]);
                default -> {}
            }
        }
        if (dataRoot == null) {
            throw new IllegalArgumentException(
                    "usage: validator --data-root <dir> [--tables-root <dir>] [--content-root"
                            + " <dir>] [--assets-root <dir>]");
        }
        if (tablesRoot != null && !Files.isDirectory(tablesRoot)) {
            throw new IllegalArgumentException(
                    "--tables-root "
                            + tablesRoot
                            + " is not a directory (typo? CI must not skip the source tables)");
        }
        return new Options(dataRoot, tablesRoot, contentRoot, assetsRoot);
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

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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 *
 * <p>V-NAME ({@link NameConventions}) enforces the docs/11 split — machine identifiers stay ASCII
 * pinyin, player-visible text must be Chinese — because Minecraft's {@code ResourceLocation} makes a
 * Chinese ID a load-time crash rather than a style choice.
 */
public final class ValidatorMain {

    /**
     * Ledger tables whose first column is a record key, not a content ID (docs/04 §6 V-DUP scope):
     * placeholder whitelists and rename migrations intentionally reuse names that exist elsewhere.
     */
    private static final List<String> NON_CONTENT_TABLES =
            List.of("known-placeholders.csv", "id_migration.csv");

    /**
     * 台账表的第一列是记录号而非内容 ID（docs/04 §6），命名门禁对它们只查"不含非 ASCII 字母"，不套内容 ID 形状规则。
     */
    static boolean isLedgerTable(String fileName) {
        return NON_CONTENT_TABLES.contains(fileName);
    }

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
                // 对话文本表的条目 id 与同章树的节点 id 是契约内的共享命名空间（JSON_SCHEMA §4.10：
                // "节点行 id = 节点 id"，key 派生 dialog.strife.<条目id>）——给它独立 key 空间，
                // 避免 V-DUP 把合法共享误判为跨表冲突；表内唯一性检查不受影响。
                String key = cells.get(0);
                if (fileName.startsWith("dialog_") && fileName.endsWith("_text.csv")) {
                    key = "dialog.strife." + key;
                }
                seen.computeIfAbsent(key, k -> new ArrayList<>())
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
        // realm_rules 是 NUMBERS 派生的配置文件（MVP realm 运行时数值表），不是内容 ID
        if ("strife_realms".equals(parts[1]) && "realm_rules".equals(id)) {
            return null;
        }
        return switch (parts[1]) {
            case "strife_realms" -> "realm.strife." + id;
            case "strife_techniques" -> "technique.strife." + id;
            case "strife_spells" -> "spell.strife." + id;
            case "strife_pills" -> "item.strife." + id;
            case "strife_artifacts" -> "artifact.strife." + id;
            // 对话域刻意无 lang 规则：文本走独立产物 dialog_text/<章>.json（§4.10 运行时通道，[拟]），
            // 服务端随 S2C 下发成品文本；text_key ↔ 文本表条目的对应由 DialogTextGenerator 覆盖校验 + V-REF 二期把守。
            case "dialog_trees", "dialog_text" -> null;
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

    static Map<String, Object> langEntries(Path file) {
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

    /** unlock_key 词表（JSON_SCHEMA §1.4 `[拟]`）——契约写明 Validator 白名单即源于此。 */
    private static final List<String> UNLOCK_KEYS =
            List.of(
                    "meditation",
                    "spiritroot_panel",
                    "cultivation_panel",
                    "technique_equip",
                    "spell_cast",
                    "pill_crafting",
                    "artifact_slot",
                    "item_refine",
                    "quest_line_ch1",
                    "tribulation",
                    "sect_join",
                    "ambient_qi_affinity",
                    "soul_scan",
                    "upper_realm_gate",
                    "ascension");

    /** tables/known-placeholders.csv 的 id 列（契约 §2：`[占位]` 键走白名单，禁止长期驻留）。 */
    private static Set<String> placeholderWhitelist(Options options) {
        Set<String> ids = new LinkedHashSet<>();
        if (options.tablesRoot() == null) {
            return ids;
        }
        for (Path table : csvFiles(options.tablesRoot())) {
            if (!"known-placeholders.csv".equals(table.getFileName().toString())) {
                continue;
            }
            List<String> lines = readLines(table);
            for (int row = 1; row < lines.size(); row++) {
                String line = lines.get(row);
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                List<String> cells = splitRow(line);
                if (!cells.isEmpty() && !cells.get(0).isBlank()) {
                    ids.add(cells.get(0));
                }
            }
        }
        return ids;
    }

    /**
     * V-DAG (docs/04 §6): quest chapters are well-formed DAGs — exactly one entry, prerequisites
     * and {@code fail_goto} stay inside the chapter, no cycles, and every quest is reachable from
     * the entry so nothing silently becomes unobtainable. The generator enforces the entry rule
     * too; the validator re-checks because content can also enter through the hot-update zip.
     */
    public static List<String> questDag(Options options) {
        List<String> problems = new ArrayList<>();
        for (Path file : jsonFiles(options.dataRoot())) {
            if (!isDomain(file, options, "strife_quests")) {
                continue;
            }
            JsonElement root = parse(file);
            if (!root.isJsonObject() || !root.getAsJsonObject().has("quests")) {
                continue;
            }
            Map<String, JsonObject> quests = new LinkedHashMap<>();
            List<String> entries = new ArrayList<>();
            for (JsonElement element : root.getAsJsonObject().getAsJsonArray("quests")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject quest = element.getAsJsonObject();
                String id =
                        quest.has("id") && quest.get("id").isJsonPrimitive()
                                ? quest.get("id").getAsString()
                                : null;
                if (id == null) {
                    problems.add(file + ": quest row without id (V-DAG)");
                    continue;
                }
                quests.put(id, quest);
                if (quest.has("entry")
                        && quest.get("entry").isJsonPrimitive()
                        && quest.get("entry").getAsBoolean()) {
                    entries.add(id);
                }
            }
            if (quests.isEmpty()) {
                continue;
            }
            if (entries.size() != 1) {
                problems.add(
                        file
                                + ": has "
                                + entries.size()
                                + " entry=true quests, exactly one required (docs/04 §6 V-DAG)");
            }
            Map<String, List<String>> children = new LinkedHashMap<>();
            Set<String> brokenDependencies = new LinkedHashSet<>();
            for (Map.Entry<String, JsonObject> quest : quests.entrySet()) {
                for (String prerequisite : stringArray(quest.getValue().get("prerequisites"))) {
                    if (!quests.containsKey(prerequisite)) {
                        problems.add(
                                file
                                        + ": quest '"
                                        + quest.getKey()
                                        + "' has a dangling prerequisite '"
                                        + prerequisite
                                        + "' (docs/04 §6 V-DAG)");
                        // 该节点的不可达已由这条根因解释，不再重复报衍生噪音
                        brokenDependencies.add(quest.getKey());
                    } else {
                        children.computeIfAbsent(prerequisite, k -> new ArrayList<>())
                                .add(quest.getKey());
                    }
                }
                for (String failGoto : failGotos(quest.getValue())) {
                    if (!quests.containsKey(failGoto)) {
                        problems.add(
                                file
                                        + ": quest '"
                                        + quest.getKey()
                                        + "' points fail_goto at '"
                                        + failGoto
                                        + "', which is not in this chapter (docs/04 §6 V-DAG)");
                    }
                }
            }
            // 环检测（Kahn 拓扑）：入度 = 章内有效前置数；成环时只报环本身，
            // 可达性抱怨都是它的衍生噪音。
            Map<String, Integer> indegree = new LinkedHashMap<>();
            for (String id : quests.keySet()) {
                indegree.put(id, 0);
            }
            for (List<String> kids : children.values()) {
                for (String kid : kids) {
                    indegree.merge(kid, 1, Integer::sum);
                }
            }
            Deque<String> ready = new ArrayDeque<>();
            for (Map.Entry<String, Integer> degree : indegree.entrySet()) {
                if (degree.getValue() == 0) {
                    ready.add(degree.getKey());
                }
            }
            int processed = 0;
            while (!ready.isEmpty()) {
                String current = ready.pop();
                processed++;
                for (String kid : children.getOrDefault(current, List.of())) {
                    if (indegree.merge(kid, -1, Integer::sum) == 0) {
                        ready.add(kid);
                    }
                }
            }
            if (processed < quests.size()) {
                problems.add(
                        file
                                + ": "
                                + (quests.size() - processed)
                                + " quest(s) form a prerequisite cycle (docs/04 §6 V-DAG)");
            } else if (entries.size() == 1) {
                Set<String> reachable = new LinkedHashSet<>();
                collectReachable(children, entries.get(0), reachable);
                for (String id : quests.keySet()) {
                    if (!reachable.contains(id) && !brokenDependencies.contains(id)) {
                        problems.add(
                                file
                                        + ": quest '"
                                        + id
                                        + "' is unreachable from the entry '"
                                        + entries.get(0)
                                        + "' — a missing prerequisite edge"
                                        + " (docs/04 §6 V-DAG)");
                    }
                }
            }
        }
        return problems;
    }

    private static List<String> failGotos(JsonObject quest) {
        List<String> targets = new ArrayList<>();
        String failGoto = stringOrNull(quest.get("fail_goto"));
        if (failGoto != null) {
            targets.add(failGoto);
        }
        if (quest.has("timer") && quest.get("timer").isJsonObject()) {
            String timerFailGoto = stringOrNull(quest.getAsJsonObject("timer").get("fail_goto"));
            if (timerFailGoto != null) {
                targets.add(timerFailGoto);
            }
        }
        return targets;
    }

    private static void collectReachable(
            Map<String, List<String>> children, String start, Set<String> reachable) {
        Deque<String> queue = new ArrayDeque<>();
        queue.push(start);
        while (!queue.isEmpty()) {
            String current = queue.pop();
            if (!reachable.add(current)) {
                continue;
            }
            List<String> kids = children.get(current);
            if (kids != null) {
                queue.addAll(kids);
            }
        }
    }

    /**
     * V-REF phase 1 (docs/04 §6 引用存在性): the reference surfaces whose registries already exist.
     * Unlock keys are checked against the §1.4 vocabulary; realm unlock lists, breakthrough keys
     * and quest breakthrough objectives against NUMBERS blocks (content-gated). References whose
     * registries do not exist yet (item_/npc_/spell_…) are phase 2. {@code [占位]} keys ride the
     * known-placeholders whitelist (JSON_SCHEMA §2), which this check finally reads.
     */
    public static List<String> referenceExistence(Options options) {
        Set<String> whitelist = placeholderWhitelist(options);
        Set<String> unlockKeys = new LinkedHashSet<>(UNLOCK_KEYS);
        unlockKeys.addAll(whitelist);
        Set<String> breakthroughKeys = new LinkedHashSet<>(whitelist);
        Path numbers = NumbersBlocks.numbersFile(options.contentRoot());
        List<String> problems = new ArrayList<>();
        if (numbers != null) {
            try {
                for (NumbersBlocks.Line line : NumbersBlocks.yamlBlock(numbers, "breakthrough")) {
                    String content = NumbersBlocks.stripComment(line.text()).trim();
                    if (content.isEmpty() || content.startsWith("#")) {
                        continue;
                    }
                    int colon = content.indexOf(':');
                    if (colon > 0) {
                        breakthroughKeys.add(content.substring(0, colon).trim());
                    }
                }
            } catch (IllegalStateException e) {
                problems.add(numbers + ": " + e.getMessage());
            }
        }
        for (Path file : jsonFiles(options.dataRoot())) {
            String domain = domainOf(file, options);
            JsonElement root = parse(file);
            if (domain == null || !root.isJsonObject()) {
                continue;
            }
            JsonObject object = root.getAsJsonObject();
            switch (domain) {
                case "strife_realms" -> {
                    if (object.has("unlocks") && object.get("unlocks").isJsonArray()) {
                        for (JsonElement key : object.getAsJsonArray("unlocks")) {
                            String value = stringOrNull(key);
                            if (value != null && !unlockKeys.contains(value)) {
                                problems.add(
                                        file
                                                + ": unlocks references '"
                                                + value
                                                + "', which is neither a JSON_SCHEMA §1.4"
                                                + " unlock_key nor in tables/known-placeholders.csv"
                                                + " (docs/04 §6 V-REF)");
                            }
                        }
                    }
                    String successKey = stringOrNull(object.get("breakthrough_success_key"));
                    if (numbers != null
                            && successKey != null
                            && !breakthroughKeys.contains(successKey)) {
                        problems.add(
                                file
                                        + ": breakthrough_success_key '"
                                        + successKey
                                        + "' has no entry in NUMBERS §3 @@breakthrough"
                                        + " and is not whitelisted (docs/04 §6 V-REF)");
                    }
                }
                case "strife_quests" -> {
                    if (!object.has("quests") || !object.get("quests").isJsonArray()) {
                        continue;
                    }
                    for (JsonElement element : object.getAsJsonArray("quests")) {
                        if (!element.isJsonObject()) {
                            continue;
                        }
                        JsonObject quest = element.getAsJsonObject();
                        for (JsonElement reward : stringArrayElement(quest.get("rewards"))) {
                            String unlockKey =
                                    stringOrNull(reward.getAsJsonObject().get("unlock_key"));
                            if (unlockKey != null && !unlockKeys.contains(unlockKey)) {
                                problems.add(
                                        file
                                                + ": quest '"
                                                + stringOrNull(quest.get("id"))
                                                + "' rewards unlock_key '"
                                                + unlockKey
                                                + "', which is neither a JSON_SCHEMA §1.4"
                                                + " unlock_key nor whitelisted (docs/04 §6 V-REF)");
                            }
                        }
                        if (numbers == null) {
                            continue;
                        }
                        for (JsonElement objective : stringArrayElement(quest.get("objectives"))) {
                            JsonObject target = objective.getAsJsonObject();
                            if (!"breakthrough".equals(stringOrNull(target.get("type")))) {
                                continue;
                            }
                            String key = stringOrNull(target.get("target"));
                            if (key != null && !breakthroughKeys.contains(key)) {
                                problems.add(
                                        file
                                                + ": quest '"
                                                + stringOrNull(quest.get("id"))
                                                + "' breakthrough objective targets '"
                                                + key
                                                + "', which has no entry in NUMBERS §3"
                                                + " @@breakthrough and is not whitelisted"
                                                + " (docs/04 §6 V-REF)");
                            }
                        }
                    }
                }
                default -> {}
            }
        }
        return problems;
    }

    private static boolean isDomain(Path file, Options options, String domain) {
        return domainOf(file, options) != null && domain.equals(domainOf(file, options));
    }

    /**
     * V-REF 第二期（docs/04 §6 / JSON_SCHEMA §7）：跨域内容 ID 引用校验——任务奖励与对话 effects 引用的 功法/法术/物品/任务/势力，任务目标的
     * item_/npc_ target，对话树的 npc，全部必须能落到源表主键、 lang 物品键或 LORE NPC 卡；缺口引用进 known-placeholders 白名单放行。
     *
     * <p>物品没有源表（物品注册在代码里，03 §1 production 职责），lang 的 {@code item.strife.<id>} 键是
     * "物品已注册且有显示名"的代理证据——V-TEXT 保证 lang 完整，V-REF 二期只负责引用面。
     */
    public static List<String> referenceExistencePhase2(Options options) {
        List<String> problems = new ArrayList<>();
        Set<String> whitelist = placeholderWhitelist(options);
        Set<String> techniqueIds = tableIdSet(options, "techniques.csv");
        Set<String> spellIds = tableIdSet(options, "spells.csv");
        Set<String> pillIds = tableIdSet(options, "pills.csv");
        Set<String> factionIds = tableIdSet(options, "factions.csv");
        Set<String> itemIds = langItemIds(options);
        itemIds.addAll(pillIds);
        java.util.Optional<Set<String>> npcIdsOptional = loreNpcIds(options);
        boolean checkNpc = npcIdsOptional.isPresent();
        Set<String> npcIds = npcIdsOptional.orElse(Set.of());
        Set<String> questIds = questIds(options);

        for (Path file : jsonFiles(options.dataRoot())) {
            String domain = domainOf(file, options);
            JsonElement root = parse(file);
            if (domain == null || !root.isJsonObject()) {
                continue;
            }
            JsonObject object = root.getAsJsonObject();
            switch (domain) {
                case "strife_quests" -> {
                    for (JsonElement element : arrayOrEmptyOf(object, "quests")) {
                        if (!element.isJsonObject()) {
                            continue;
                        }
                        JsonObject quest = element.getAsJsonObject();
                        String questId = stringOrNull(quest.get("id"));
                        String prefix = file + " quest '" + questId + "'";
                        for (JsonElement reward : arrayOrEmptyOf(quest, "rewards")) {
                            if (!reward.isJsonObject()) {
                                continue;
                            }
                            JsonObject rewardObject = reward.getAsJsonObject();
                            String type = stringOrNull(rewardObject.get("type"));
                            String id = stringOrNull(rewardObject.get("id"));
                            if (id == null) {
                                continue;
                            }
                            switch (type == null ? "" : type) {
                                case "item" ->
                                        checkId(
                                                problems,
                                                prefix + " rewards",
                                                "item",
                                                id,
                                                itemIds,
                                                whitelist);
                                case "technique" ->
                                        checkId(
                                                problems,
                                                prefix + " rewards",
                                                "technique",
                                                id,
                                                techniqueIds,
                                                whitelist);
                                case "spell" ->
                                        checkId(
                                                problems,
                                                prefix + " rewards",
                                                "spell",
                                                id,
                                                spellIds,
                                                whitelist);
                                case "reputation" ->
                                        checkId(
                                                problems,
                                                prefix + " rewards",
                                                "faction",
                                                id,
                                                factionIds,
                                                whitelist);
                                default -> {}
                            }
                        }
                        for (JsonElement objective : arrayOrEmptyOf(quest, "objectives")) {
                            if (!objective.isJsonObject()) {
                                continue;
                            }
                            JsonObject target = objective.getAsJsonObject();
                            String type = stringOrNull(target.get("type"));
                            String value = stringOrNull(target.get("target"));
                            if (value == null) {
                                continue;
                            }
                            if ("collect".equals(type) && value.startsWith("item_")) {
                                checkId(
                                        problems,
                                        prefix + " objectives",
                                        "item",
                                        value,
                                        itemIds,
                                        whitelist);
                            } else if (checkNpc
                                    && ("talk".equals(type) || "deliver".equals(type))
                                    && value.startsWith("npc_")) {
                                checkId(
                                        problems,
                                        prefix + " objectives",
                                        "npc",
                                        value,
                                        npcIds,
                                        whitelist);
                            }
                        }
                    }
                }
                case "dialog_trees" -> {
                    for (JsonElement treeElement : arrayOrEmptyOf(object, "trees")) {
                        if (!treeElement.isJsonObject()) {
                            continue;
                        }
                        JsonObject tree = treeElement.getAsJsonObject();
                        String treeId = stringOrNull(tree.get("id"));
                        String npc = stringOrNull(tree.get("npc"));
                        if (npc != null && checkNpc) {
                            checkId(
                                    problems,
                                    file + " tree '" + treeId + "'",
                                    "npc",
                                    npc,
                                    npcIds,
                                    whitelist);
                        }
                        for (JsonElement nodeElement : arrayOrEmptyOf(tree, "nodes")) {
                            if (!nodeElement.isJsonObject()) {
                                continue;
                            }
                            JsonObject node = nodeElement.getAsJsonObject();
                            String nodeWhere =
                                    file
                                            + " tree '"
                                            + treeId
                                            + "' node '"
                                            + stringOrNull(node.get("id"))
                                            + "'";
                            for (JsonElement optionElement : arrayOrEmptyOf(node, "options")) {
                                if (!optionElement.isJsonObject()) {
                                    continue;
                                }
                                for (JsonElement effect :
                                        arrayOrEmptyOf(
                                                optionElement.getAsJsonObject(), "effects")) {
                                    if (!effect.isJsonObject()) {
                                        continue;
                                    }
                                    JsonObject effectObject = effect.getAsJsonObject();
                                    String type = stringOrNull(effectObject.get("type"));
                                    String args = stringOrNull(effectObject.get("args"));
                                    if (args == null) {
                                        continue;
                                    }
                                    String[] parts = args.trim().split("[:,]", -1);
                                    String head = parts.length > 0 ? parts[0].trim() : "";
                                    switch (type == null ? "" : type) {
                                        case "give_item", "take_item" ->
                                                checkId(
                                                        problems, nodeWhere, "item", head, itemIds,
                                                        whitelist);
                                        case "start_quest" ->
                                                checkId(
                                                        problems, nodeWhere, "quest", head,
                                                        questIds, whitelist);
                                        case "reputation" ->
                                                checkId(
                                                        problems,
                                                        nodeWhere,
                                                        "faction",
                                                        head,
                                                        factionIds,
                                                        whitelist);
                                        default -> {}
                                    }
                                }
                            }
                        }
                    }
                }
                default -> {}
            }
        }
        return problems;
    }

    /** 引用落点检查：主键集合或白名单任一命中即通过（白名单必须挂 issue 号，禁止长期驻留）。 */
    private static void checkId(
            List<String> problems,
            String where,
            String kind,
            String id,
            Set<String> known,
            Set<String> whitelist) {
        if (!known.contains(id) && !whitelist.contains(id)) {
            problems.add(
                    where
                            + ": "
                            + kind
                            + " reference '"
                            + id
                            + "' has no source-row, lang key or LORE card and is not whitelisted"
                            + " (docs/04 §6 V-REF phase 2)");
        }
    }

    /** 单张源表的 id 主键集合（空表合法——章内容未写）。 */
    private static Set<String> tableIdSet(Options options, String fileName) {
        if (options.tablesRoot() == null) {
            return Set.of();
        }
        for (Path table : csvFiles(options.tablesRoot())) {
            if (!table.getFileName().toString().equals(fileName)) {
                continue;
            }
            List<String> lines = readLines(table);
            Set<String> ids = new LinkedHashSet<>();
            for (int row = 1; row < lines.size(); row++) {
                String line = lines.get(row);
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                List<String> cells = splitRow(line);
                if (!cells.isEmpty() && !cells.get(0).isBlank()) {
                    ids.add(cells.get(0).trim());
                }
            }
            return ids;
        }
        return Set.of();
    }

    /** zh_cn.json 里 {@code item.strife.<id>} 前缀的键 → 物品内容 ID 集合。 */
    private static Set<String> langItemIds(Options options) {
        Set<String> ids = new LinkedHashSet<>();
        Path lang =
                options.assetsRoot() == null
                        ? null
                        : options.assetsRoot().resolve("strife/lang/zh_cn.json");
        if (lang == null || !Files.isRegularFile(lang)) {
            return ids;
        }
        JsonElement root = parse(lang);
        if (root.isJsonObject()) {
            for (String key : root.getAsJsonObject().keySet()) {
                if (key.startsWith("item.strife.")) {
                    ids.add(key.substring("item.strife.".length()));
                }
            }
        }
        return ids;
    }

    /**
     * LORE.md §6 NPC 卡的 {@code npc_} id 集合。返回语义：absent = LORE 未在库（npc 引用校验随之跳过—— 此时报"全部 npc
     * 引用无主"是误导）；present（可能为空集）= LORE 在库，npc 引用必须逐个落到卡上或白名单。
     */
    private static java.util.Optional<Set<String>> loreNpcIds(Options options) {
        Path lore = options.contentRoot() == null ? null : options.contentRoot().resolve("LORE.md");
        if (lore == null || !Files.isRegularFile(lore)) {
            return java.util.Optional.empty();
        }
        Set<String> ids = new LinkedHashSet<>();
        for (String line : readLines(lore)) {
            String trimmed = line.stripLeading();
            if (!trimmed.startsWith("|") || !trimmed.contains("npc_")) {
                continue;
            }
            String[] cells = line.split("\\|");
            for (String cell : cells) {
                String value = cell.trim().replace("`", "");
                if (value.startsWith("npc_")) {
                    ids.add(value);
                    break;
                }
            }
        }
        return java.util.Optional.of(ids);
    }

    /** 全部任务产物里的任务 id 集合（start_quest 引用的落点）。 */
    private static Set<String> questIds(Options options) {
        Set<String> ids = new LinkedHashSet<>();
        for (Path file : jsonFiles(options.dataRoot())) {
            if (!"strife_quests".equals(domainOf(file, options))) {
                continue;
            }
            JsonElement root = parse(file);
            if (!root.isJsonObject()) {
                continue;
            }
            for (JsonElement element : arrayOrEmptyOf(root.getAsJsonObject(), "quests")) {
                if (element.isJsonObject()) {
                    String id = stringOrNull(element.getAsJsonObject().get("id"));
                    if (id != null) {
                        ids.add(id);
                    }
                }
            }
        }
        return ids;
    }

    /**
     * V-DSL（docs/04 §6，`[拟]` 冻结于 JSON_SCHEMA §4.7.1/§4.7 effects 补注）：quests 与 dialog_trees 的
     * conditions 表达式、对话 effects 的 type/args 契约、门链深度上限 ≥1。运行时解释器 fail-fast 兜底， 这里保证断链在构建期就红。
     */
    public static List<String> dslLegality(Options options) {
        List<String> problems = new ArrayList<>();
        for (Path file : jsonFiles(options.dataRoot())) {
            String domain = domainOf(file, options);
            JsonElement root = parse(file);
            if (domain == null || !root.isJsonObject()) {
                continue;
            }
            JsonObject object = root.getAsJsonObject();
            switch (domain) {
                case "strife_quests" -> {
                    for (JsonElement element : arrayOrEmptyOf(object, "quests")) {
                        if (!element.isJsonObject()) {
                            continue;
                        }
                        JsonObject quest = element.getAsJsonObject();
                        String conditions = stringOrNull(quest.get("conditions"));
                        if (conditions != null) {
                            problems.addAll(
                                    DslSyntax.validateCondition(
                                            conditions,
                                            file
                                                    + " quest '"
                                                    + stringOrNull(quest.get("id"))
                                                    + "'"));
                        }
                    }
                }
                case "dialog_trees" -> {
                    for (JsonElement treeElement : arrayOrEmptyOf(object, "trees")) {
                        if (!treeElement.isJsonObject()) {
                            continue;
                        }
                        JsonObject tree = treeElement.getAsJsonObject();
                        String treeId = stringOrNull(tree.get("id"));
                        String prefix = file + " tree '" + treeId + "'";
                        int maxDepth =
                                tree.has("max_depth_levels")
                                        ? tree.get("max_depth_levels").getAsInt()
                                        : 0;
                        if (maxDepth < 1) {
                            problems.add(
                                    prefix + ": max_depth_levels 必须 ≥1（§4.7 求值上限），实际 " + maxDepth);
                        }
                        for (JsonElement effect : arrayOrEmptyOf(tree, "effects")) {
                            problems.addAll(
                                    effectProblems(
                                            effect.getAsJsonObject(), prefix + " 树级 effect"));
                        }
                        for (JsonElement nodeElement : arrayOrEmptyOf(tree, "nodes")) {
                            if (!nodeElement.isJsonObject()) {
                                continue;
                            }
                            JsonObject node = nodeElement.getAsJsonObject();
                            String nodeId = stringOrNull(node.get("id"));
                            String nodeWhere = prefix + " node '" + nodeId + "'";
                            String conditions = stringOrNull(node.get("conditions"));
                            if (conditions != null) {
                                problems.addAll(DslSyntax.validateCondition(conditions, nodeWhere));
                            }
                            for (JsonElement optionElement : arrayOrEmptyOf(node, "options")) {
                                if (!optionElement.isJsonObject()) {
                                    continue;
                                }
                                JsonObject option = optionElement.getAsJsonObject();
                                String optionConditions = stringOrNull(option.get("conditions"));
                                if (optionConditions != null) {
                                    problems.addAll(
                                            DslSyntax.validateCondition(
                                                    optionConditions,
                                                    nodeWhere
                                                            + " 选项 '"
                                                            + stringOrNull(option.get("text_key"))
                                                            + "'"));
                                }
                                for (JsonElement effect : arrayOrEmptyOf(option, "effects")) {
                                    problems.addAll(
                                            effectProblems(
                                                    effect.getAsJsonObject(),
                                                    nodeWhere + " 选项 effect"));
                                }
                            }
                        }
                    }
                }
                default -> {}
            }
        }
        return problems;
    }

    private static List<String> effectProblems(JsonObject effect, String where) {
        List<String> problems = new ArrayList<>();
        String problem =
                DslSyntax.validateEffect(
                        stringOrNull(effect.get("type")), stringOrNull(effect.get("args")), where);
        if (problem != null) {
            problems.add(problem);
        }
        return problems;
    }

    private static List<JsonElement> arrayOrEmptyOf(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonArray()) {
            return List.of();
        }
        List<JsonElement> items = new ArrayList<>();
        for (JsonElement item : element.getAsJsonArray()) {
            items.add(item);
        }
        return items;
    }

    /** {@code data/strife/<domain>/<file>.json} 的域段，非该形态返回 null。 */
    private static String domainOf(Path file, Options options) {
        String relative = options.dataRoot().relativize(file).toString().replace('\\', '/');
        String[] parts = relative.split("/");
        return parts.length == 3 && "strife".equals(parts[0]) ? parts[1] : null;
    }

    private static List<String> stringArray(JsonElement element) {
        List<String> values = new ArrayList<>();
        if (element != null && element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                String value = stringOrNull(item);
                if (value != null) {
                    values.add(value);
                }
            }
        }
        return values;
    }

    private static List<JsonElement> stringArrayElement(JsonElement element) {
        List<JsonElement> items = new ArrayList<>();
        if (element != null && element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                if (item.isJsonObject()) {
                    items.add(item);
                }
            }
        }
        return items;
    }

    private static String stringOrNull(JsonElement element) {
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
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
        problems.addAll(questDag(options));
        executed++;
        problems.addAll(referenceExistence(options));
        executed++;
        problems.addAll(referenceExistencePhase2(options));
        executed++;
        problems.addAll(dslLegality(options));
        executed++;
        problems.addAll(probabilitySum(options));
        executed++;
        problems.addAll(numericRanges(options));
        executed++;
        problems.addAll(NameConventions.check(options));
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
    static List<String> splitRow(String line) {
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

    static List<String> readLines(Path file) {
        try {
            return Files.readAllLines(file);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read table " + file, e);
        }
    }

    static List<Path> csvFiles(Path root) {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        return walk(root).filter(p -> p.getFileName().toString().endsWith(".csv")).toList();
    }

    static List<Path> jsonFiles(Path root) {
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

    static JsonElement parse(Path file) {
        try (var reader = Files.newBufferedReader(file)) {
            return JsonParser.parseReader(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read content file " + file, e);
        }
    }

    private ValidatorMain() {}
}

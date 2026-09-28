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
 * Reader for {@code content/NUMBERS.md} @@blocks (content/JSON_SCHEMA.md §3): {@code *_key} columns
 * in source tables name entries here, and DataGen inlines the referenced values into the product
 * alongside the key — the table carries the key only, the product carries key + value, and runtime
 * reads the value. That keeps NUMBERS.md the single truth source while the shipped content stays
 * self-contained (the markdown itself never enters the jar).
 *
 * <p>Lazy on purpose: DataGen starts while {@code content/} (A0-7) may not be merged yet, and the
 * file is only demanded when a row actually needs a value — the error names the file and the key.
 *
 * <p>Grammar: exactly what NUMBERS.md uses today (NUMBERS §0 解析约定) — a {@code @@<id>} marker, one
 * {@code ```yaml} fence, and one-level flow mappings ({@code key: { k: v, ... }}), scalars or
 * {@code [a, b]} sequences, with inline {@code #} comments. Values coerce to Long, Double or
 * String; anything else fails loudly instead of being guessed.
 */
public final class NumbersSource {

    private final Path file;
    private Map<String, Map<String, Object>> blocks;
    private Map<String, Map<String, String>> comments;
    private String sha256;

    private NumbersSource(Path file) {
        this.file = file;
    }

    /**
     * Points at {@code contentRoot/NUMBERS.md}; null contentRoot defers the failure to first use.
     */
    public static NumbersSource at(Path contentRoot) {
        return new NumbersSource(contentRoot == null ? null : contentRoot.resolve("NUMBERS.md"));
    }

    /**
     * Whether the truth source is present. Generators that can only run with NUMBERS.md are skipped
     * with a printed notice while {@code content/} (A0-7) is unmerged — the same arm-on-merge
     * contract the validator's V-GROWTH uses.
     */
    public boolean available() {
        return file != null && Files.isRegularFile(file);
    }

    /** The whole @@block in written key order — for realms, the chain order is the truth (§4.1). */
    public Map<String, Object> block(String blockId) {
        Map<String, Object> entries = parse().get(blockId);
        if (entries == null) {
            throw new IllegalStateException(
                    file + ": no @@" + blockId + " block found (NUMBERS §0)");
        }
        return entries;
    }

    /**
     * The flow-mapping value at {@code <block>.<key>}, in written order (e.g.
     * combat.spell_cost_qi).
     */
    public Map<String, Object> map(String block, String key) {
        Object value = value(block, key);
        if (!(value instanceof Map)) {
            throw new IllegalStateException(
                    file
                            + ": @@"
                            + block
                            + " entry '"
                            + key
                            + "' is not a { k: v, ... } mapping — a *_key column references it as"
                            + " one (content/JSON_SCHEMA.md §3)");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) value;
        return map;
    }

    /** Any value at {@code <block>.<key>}, coerced (Long / Double / String / Map / List). */
    public Object value(String block, String key) {
        Map<String, Object> entries = parse().get(block);
        if (entries == null || !entries.containsKey(key)) {
            throw new IllegalStateException(
                    file
                            + ": @@"
                            + block
                            + " has no key '"
                            + key
                            + "' — a *_key column references it, so either the key is misspelled"
                            + " or NUMBERS.md is missing the entry (content/JSON_SCHEMA.md §3)");
        }
        return entries.get(key);
    }

    /**
     * The raw inline comment of an entry, without the leading {@code #}. Derivation flags like
     * {@code [占位]} live in comments (NUMBERS §0), so generators read this instead of inventing a
     * second field in the truth source.
     */
    public String comment(String block, String key) {
        parse();
        Map<String, String> byKey = comments.get(block);
        return byKey == null ? null : byKey.get(key);
    }

    /** Product header for NUMBERS-derived products — same freshness contract as table products. */
    public String generatedHeader() {
        parse();
        return "from content/NUMBERS.md @ sha256:" + sha256;
    }

    /** Parses the whole file once; empty until the first lookup actually needs it. */
    private Map<String, Map<String, Object>> parse() {
        if (blocks != null) {
            return blocks;
        }
        if (file == null || !Files.isRegularFile(file)) {
            throw new IllegalStateException(
                    "content/NUMBERS.md not found ("
                            + file
                            + ") — a *_key column needs the truth source to expand into the product"
                            + " (content/JSON_SCHEMA.md §3); merge it (A0-7) or point --content-root at it");
        }
        Map<String, Map<String, Object>> parsed = new LinkedHashMap<>();
        Map<String, Map<String, String>> parsedComments = new LinkedHashMap<>();
        String currentBlock = null;
        List<String> lines;
        try {
            lines = Files.readAllLines(file);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read truth source " + file, e);
        }
        for (int i = 0; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();
            if (currentBlock == null) {
                if (trimmed.startsWith("@@") && trimmed.length() > 2) {
                    currentBlock = trimmed.substring(2);
                    parsed.putIfAbsent(currentBlock, new LinkedHashMap<>());
                    parsedComments.putIfAbsent(currentBlock, new LinkedHashMap<>());
                }
                continue;
            }
            if (trimmed.equals("```")) {
                currentBlock = null;
                continue;
            }
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.equals("```yaml")) {
                continue;
            }
            String content = stripComment(trimmed).trim();
            int colon = content.indexOf(':');
            if (colon <= 0) {
                throw new IllegalStateException(
                        file + ":" + (i + 1) + ": cannot parse NUMBERS entry '" + content + "'");
            }
            String key = content.substring(0, colon).trim();
            String raw = content.substring(colon + 1).trim();
            parsed.get(currentBlock).put(key, coerce(file, i + 1, raw));
            int hash = trimmed.indexOf('#');
            if (hash >= 0) {
                parsedComments.get(currentBlock).put(key, trimmed.substring(hash + 1).trim());
            }
        }
        if (currentBlock != null) {
            throw new IllegalStateException(
                    file + ": @@" + currentBlock + " fence is never closed (NUMBERS §0)");
        }
        blocks = parsed;
        comments = parsedComments;
        sha256 = sha256Of(file);
        return blocks;
    }

    private static String sha256Of(Path file) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(Files.readAllBytes(file)));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot hash truth source " + file, e);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 missing from this JVM", e);
        }
    }

    private static String stripComment(String text) {
        int hash = text.indexOf('#');
        return hash < 0 ? text : text.substring(0, hash);
    }

    private static Object coerce(Path file, int line, String raw) {
        if (raw.startsWith("{") && raw.endsWith("}")) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (String part : splitFlow(raw.substring(1, raw.length() - 1))) {
                int colon = part.indexOf(':');
                if (colon <= 0) {
                    throw new IllegalStateException(
                            file + ":" + line + ": cannot parse mapping entry '" + part + "'");
                }
                map.put(
                        part.substring(0, colon).trim(),
                        coerce(file, line, part.substring(colon + 1).trim()));
            }
            return map;
        }
        if (raw.startsWith("[") && raw.endsWith("]")) {
            List<Object> list = new ArrayList<>();
            for (String part : splitFlow(raw.substring(1, raw.length() - 1))) {
                if (!part.isEmpty()) {
                    list.add(coerce(file, line, part));
                }
            }
            return list;
        }
        if (raw.matches("-?\\d+")) {
            return Long.parseLong(raw);
        }
        if (raw.matches("-?\\d+\\.\\d*([eE][-+]?\\d+)?|-?\\.\\d+([eE][-+]?\\d+)?")) {
            return Double.parseDouble(raw);
        }
        return raw;
    }

    /**
     * Splits a flow body on top-level commas: a comma inside a nested {@code [ … ]} or {@code { …
     * }} (realms carry {@code unlocks: [a, b, …]}) does not split the entry.
     */
    private static List<String> splitFlow(String body) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '[' || c == '{') {
                depth++;
            } else if (c == ']' || c == '}') {
                depth = Math.max(0, depth - 1);
            } else if (c == ',' && depth == 0) {
                parts.add(body.substring(start, i).trim());
                start = i + 1;
            }
        }
        parts.add(body.substring(start).trim());
        return parts;
    }
}

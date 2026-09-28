package com.strife.tools.datagen;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One source table read per docs/04 §5 and content/JSON_SCHEMA.md §2.
 *
 * <p>The cell grammar is the one §2 freezes ({@code [拟]}, changes are 破档): scalars, {@code ;}
 * lists, {@code k=v;} mappings, object arrays separated by {@code |}, inner nesting wrapped in
 * {@code ( )}, and {@code ()} as the explicit empty container — a blank cell always means "absent"
 * and never "empty". Parsing is paren-aware, so a {@code ;} or {@code |} inside {@code ( )} does
 * not split the cell. Scalars reject the nested grammar outright: if a scalar column carries it,
 * the row was written against the wrong column type and must fail loudly, not mis-parse.
 *
 * @param fileName table file name, as it appears in {@code @generated}
 * @param columns header columns with {@code _}-prefixed comment columns removed (04 §5)
 * @param rows records in file order, blank cells mapped to {@code null}
 * @param sha256 hex digest of the raw bytes, written into every product this table produced
 */
public record TableSource(String fileName, List<String> columns, List<Record> rows, String sha256) {

    /**
     * @param table file name owning this row, kept so errors name the table without the caller
     * @param values column name to trimmed value, {@code null} when the cell was blank
     * @param line 1-based line number in the source file
     */
    public record Record(String table, Map<String, String> values, int line) {

        public String id() {
            String id = values.get("id");
            if (id == null || id.isBlank()) {
                throw new IllegalStateException(table + ":" + line + ": row has no id");
            }
            return id;
        }
    }

    public static TableSource read(Path csv) {
        List<String> lines = readLines(csv);
        if (lines.isEmpty()) {
            throw new IllegalStateException(csv + ": empty file, expected a header row");
        }
        List<String> header = splitRow(lines.get(0), csv, 1);
        if (header.isEmpty() || !"id".equals(header.get(0))) {
            throw new IllegalStateException(
                    csv + ":1: first header column must be 'id', found " + header);
        }
        List<String> columns = new ArrayList<>();
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < header.size(); i++) {
            if (!header.get(i).startsWith("_")) {
                columns.add(header.get(i));
                indexes.add(i);
            }
        }
        List<Record> rows = new ArrayList<>();
        for (int row = 1; row < lines.size(); row++) {
            String line = lines.get(row);
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            List<String> cells = splitRow(line, csv, row + 1);
            if (cells.size() != header.size()) {
                throw new IllegalStateException(
                        csv
                                + ":"
                                + (row + 1)
                                + ": has "
                                + cells.size()
                                + " cells but the header has "
                                + header.size()
                                + " columns — commas inside a cell are forbidden"
                                + " (tables/FILLING_GUIDE.md §1.1), use ';' or '、' instead");
            }
            Map<String, String> values = new LinkedHashMap<>();
            for (int c = 0; c < indexes.size(); c++) {
                String cell = cells.get(indexes.get(c));
                values.put(columns.get(c), cell.isEmpty() ? null : cell);
            }
            rows.add(new Record(csv.getFileName().toString(), values, row + 1));
        }
        return new TableSource(csv.getFileName().toString(), columns, rows, sha256(csv));
    }

    /** Product header text (04 §5): source table plus its hash, so V-FRESH can compare. */
    public String generatedHeader() {
        return "from tables/" + fileName + " @ sha256:" + sha256;
    }

    public String requireScalar(String column, Record record) {
        String value = get(column, record);
        if (value == null) {
            return null;
        }
        if (value.indexOf('|') >= 0 || value.startsWith("(")) {
            throw new IllegalStateException(
                    fileName
                            + ":"
                            + record.line()
                            + ": column '"
                            + column
                            + "' is a scalar column but the cell carries nested grammar"
                            + " ('|' / '(' ) — content/JSON_SCHEMA.md §2 allows that only in"
                            + " list/mapping/object columns; scalars must be plain values.");
        }
        return value;
    }

    /**
     * Splits a {@code ;} list; blank and {@code ()} both yield an empty list here, so a generator
     * that must write {@code null} for "absent" checks {@link #requireScalar} first.
     */
    public List<String> list(String column, Record record) {
        String value = get(column, record);
        if (value == null || value.equals("()")) {
            return List.of();
        }
        List<String> parts = new ArrayList<>();
        for (String part : splitTopLevel(value, ';')) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.indexOf('|') >= 0 || trimmed.startsWith("(")) {
                throw new IllegalStateException(
                        fileName
                                + ":"
                                + record.line()
                                + ": column '"
                                + column
                                + "' is a ';' list but the item '"
                                + trimmed
                                + "' carries object grammar ('|' / '(' ) — object arrays belong"
                                + " in object columns (content/JSON_SCHEMA.md §2)");
            }
            parts.add(trimmed);
        }
        return parts;
    }

    /**
     * Parses a {@code k=v;k=v} mapping (JSON_SCHEMA §2); insertion order is preserved. Values are
     * kept verbatim — a {@code ( … )}-wrapped value still carries its parens, because unwrapping
     * semantics belong to the field contract, not to this reader.
     */
    public Map<String, String> mapping(String column, Record record) {
        String value = get(column, record);
        if (value == null || value.equals("()")) {
            return new LinkedHashMap<>();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String part : splitTopLevel(value, ';')) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq <= 0) {
                throw new IllegalStateException(
                        fileName
                                + ":"
                                + record.line()
                                + ": column '"
                                + column
                                + "' expects k=v entries, got '"
                                + trimmed
                                + "'");
            }
            result.put(trimmed.substring(0, eq).trim(), trimmed.substring(eq + 1).trim());
        }
        return result;
    }

    /**
     * Parses an object array ({@code k=v;k=v|k=v;k=v}, JSON_SCHEMA §2), one map per object in cell
     * order. Unlike {@link #list}, absence is preserved: a blank cell yields {@code null} (契约： 空格子
     * = 缺省) while {@code ()} yields an empty list — generators write the difference into the
     * product, because "field absent" and "explicitly empty" are distinct states there.
     */
    public List<Map<String, String>> objectList(String column, Record record) {
        String value = get(column, record);
        if (value == null) {
            return null;
        }
        if (value.equals("()")) {
            return List.of();
        }
        List<Map<String, String>> objects = new ArrayList<>();
        for (String part : splitTopLevel(value, '|')) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                throw new IllegalStateException(
                        fileName
                                + ":"
                                + record.line()
                                + ": column '"
                                + column
                                + "' has an empty object between '|' separators"
                                + " (content/JSON_SCHEMA.md §2)");
            }
            objects.add(parseObject(column, record, trimmed));
        }
        return objects;
    }

    /**
     * Parses an object-array VALUE that {@link #objectList} kept verbatim — the inner nesting of
     * the contract grammar ({@code ( … )} wrapping, JSON_SCHEMA §2). The whole value is the inner
     * array, so its own wrapping layer is stripped first: {@code (text_key=a;next=b)} is one
     * object, {@code ((a;b)|(c;d))} is two — the double wrap is what keeps the inner {@code |} from
     * colliding with the cell-level object separator.
     */
    public List<Map<String, String>> parseObjectArray(String column, Record record, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String body = raw.trim();
        if (body.startsWith("(") && body.endsWith(")")) {
            body = body.substring(1, body.length() - 1).trim();
        }
        List<Map<String, String>> objects = new ArrayList<>();
        for (String part : splitTopLevel(body, '|')) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                throw new IllegalStateException(
                        fileName
                                + ":"
                                + record.line()
                                + ": column '"
                                + column
                                + "' has an empty object between '|' separators"
                                + " (content/JSON_SCHEMA.md §2)");
            }
            objects.add(parseObject(column, record, trimmed));
        }
        return objects;
    }

    /** One object: optional surrounding parens, then {@code k=v} entries split on top-level ';'. */
    private Map<String, String> parseObject(String column, Record record, String text) {
        String body = text;
        if (body.startsWith("(") && body.endsWith(")")) {
            body = body.substring(1, body.length() - 1).trim();
        }
        Map<String, String> object = new LinkedHashMap<>();
        for (String part : splitTopLevel(body, ';')) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq <= 0) {
                throw new IllegalStateException(
                        fileName
                                + ":"
                                + record.line()
                                + ": column '"
                                + column
                                + "' expects k=v entries inside each object, got '"
                                + trimmed
                                + "'");
            }
            object.put(trimmed.substring(0, eq).trim(), trimmed.substring(eq + 1).trim());
        }
        if (object.isEmpty()) {
            throw new IllegalStateException(
                    fileName
                            + ":"
                            + record.line()
                            + ": column '"
                            + column
                            + "' has an object with no k=v entries (content/JSON_SCHEMA.md §2)");
        }
        return object;
    }

    /**
     * Splits at every unescaped {@code separator} that sits at parenthesis depth zero, so a
     * separator inside {@code ( … )} never cuts the cell.
     */
    private static List<String> splitTopLevel(String value, char separator) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')' && depth > 0) {
                depth--;
            } else if (c == separator && depth == 0) {
                parts.add(value.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(value.substring(start));
        return parts;
    }

    /** Raw cell value, {@code null} when blank; fails if the header has no such column. */
    public String get(String column, Record record) {
        if (!columns.contains(column)) {
            throw new IllegalStateException(
                    fileName
                            + ": header has no '"
                            + column
                            + "' column, contract drift?"
                            + " (JSON_SCHEMA §4 lists "
                            + columns
                            + ")");
        }
        return record.values().get(column);
    }

    private static List<String> splitRow(String line, Path csv, int number) {
        List<String> cells = new ArrayList<>();
        for (String cell : line.split(",", -1)) {
            cells.add(cell.trim());
        }
        if (cells.stream().anyMatch(cell -> cell.startsWith("\""))) {
            throw new IllegalStateException(
                    csv
                            + ":"
                            + number
                            + ": quoted cells are not supported by the contract grammar");
        }
        return cells;
    }

    private static List<String> readLines(Path file) {
        try {
            return Files.readAllLines(file);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read table " + file, e);
        }
    }

    private static String sha256(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(file)));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot hash table " + file, e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 missing from this JVM", e);
        }
    }
}

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
 * <p>Deliberately narrow: the cell grammar for nested objects ({@code |} and {@code ( )}) is still
 * marked {@code [拟]} in the contract, so this reader only understands scalars, {@code ;} lists and
 * {@code k=v;} mappings. Anything else fails loudly instead of guessing — a silently wrong product
 * is worse than a build that stops.
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
                            + "' uses nested grammar ('|' / '(' ) that is still [拟] in"
                            + " content/JSON_SCHEMA.md §2 — DataGen does not guess it."
                            + " Get the syntax ratified, then implement it here.");
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
        value = requireScalar(column, record);
        List<String> parts = new ArrayList<>();
        for (String part : value.split(";")) {
            if (!part.isBlank()) {
                parts.add(part.trim());
            }
        }
        return parts;
    }

    /** Parses a {@code k=v;k=v} mapping (JSON_SCHEMA §2); insertion order is preserved. */
    public Map<String, String> mapping(String column, Record record) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String part : list(column, record)) {
            int eq = part.indexOf('=');
            if (eq <= 0) {
                throw new IllegalStateException(
                        fileName
                                + ":"
                                + record.line()
                                + ": column '"
                                + column
                                + "' expects k=v entries, got '"
                                + part
                                + "'");
            }
            result.put(part.substring(0, eq).trim(), part.substring(eq + 1).trim());
        }
        return result;
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

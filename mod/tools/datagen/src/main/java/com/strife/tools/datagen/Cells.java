package com.strife.tools.datagen;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared cell conversions for the domain generators. Every failure names table, line and column — a
 * conversion error must be fixable from the message alone (docs/04 §6 报错口径). Whether a value is
 * *legal* (ranges, enums, references) stays with the Validator (04 §6); these helpers only decide
 * whether it can be turned into the contracted JSON type at all.
 */
final class Cells {

    private Cells() {}

    /**
     * A 必填 scalar; a blank cell stops the run instead of writing a silent null (JSON_SCHEMA §2).
     */
    static String required(
            TableSource source, TableSource.Record row, String column, String contract) {
        String value = source.requireScalar(column, row);
        if (value == null) {
            throw new IllegalStateException(
                    source.fileName()
                            + ":"
                            + row.line()
                            + ": column '"
                            + column
                            + "' is 必填 ("
                            + contract
                            + ") but the row leaves it blank — fill it or drop the row");
        }
        return value;
    }

    static Long longOf(TableSource source, TableSource.Record row, String column, String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    source.fileName()
                            + ":"
                            + row.line()
                            + ": column '"
                            + column
                            + "' expects an integer, got '"
                            + raw
                            + "'",
                    e);
        }
    }

    static Double doubleOf(TableSource source, TableSource.Record row, String column, String raw) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    source.fileName()
                            + ":"
                            + row.line()
                            + ": column '"
                            + column
                            + "' expects a number, got '"
                            + raw
                            + "'",
                    e);
        }
    }

    /** Member of a NUMBERS flow mapping, with the row named so the fix needs no code reading. */
    static Object fromNumbers(
            TableSource source,
            TableSource.Record row,
            NumbersSource numbers,
            String block,
            String mapKey,
            String tier) {
        Map<String, Object> table = numbers.map(block, mapKey);
        Object value = table.get(tier);
        if (value == null) {
            throw new IllegalStateException(
                    source.fileName()
                            + ":"
                            + row.line()
                            + ": key '"
                            + tier
                            + "' is not an entry of NUMBERS @@"
                            + block
                            + " "
                            + mapKey
                            + " (entries: "
                            + table.keySet()
                            + ") — content/JSON_SCHEMA.md §3");
        }
        return value;
    }

    /** Strict boolean cell: only the two literals are legal, blank is not false. */
    static Boolean bool(
            TableSource source, TableSource.Record row, String column, String contract) {
        String value = required(source, row, column, contract);
        return boolOf(source, row, column, value);
    }

    /**
     * Strict boolean for an object member's raw value ({@code optional=false} inside objectives).
     */
    static Boolean boolOf(TableSource source, TableSource.Record row, String column, String raw) {
        if ("true".equals(raw)) {
            return Boolean.TRUE;
        }
        if ("false".equals(raw)) {
            return Boolean.FALSE;
        }
        throw new IllegalStateException(
                source.fileName()
                        + ":"
                        + row.line()
                        + ": column '"
                        + column
                        + "' expects true or false, got '"
                        + raw
                        + "'");
    }

    /**
     * H1 price (JSON_SCHEMA §5): {@code item_id=<id>;count=<n>} or a blank cell for null. Whether
     * the count is non-negative is the Validator's V-RANGE call, not this conversion's.
     */
    static Map<String, Object> price(TableSource source, TableSource.Record row) {
        return price(source, row, "price");
    }

    /** Same H1 shape under a different column name ({@code price_reward} for quests). */
    static Map<String, Object> price(TableSource source, TableSource.Record row, String column) {
        if (source.get(column, row) == null) {
            return null;
        }
        Map<String, String> mapping = source.mapping(column, row);
        String item = mapping.get("item_id");
        String count = mapping.get("count");
        if (item == null || count == null) {
            throw new IllegalStateException(
                    source.fileName()
                            + ":"
                            + row.line()
                            + ": column '"
                            + column
                            + "' expects 'item_id=<id>;count=<n>' (JSON_SCHEMA §5"
                            + " H1), got '"
                            + source.get(column, row)
                            + "'");
        }
        Map<String, Object> price = new LinkedHashMap<>();
        price.put("item_id", item);
        price.put("count", longOf(source, row, column, count));
        return price;
    }
}

package com.strife.tools.datagen;

import java.util.List;
import java.util.Map;

/**
 * One generated content file (docs/04 §5): its path under {@code data/} plus the fields to write.
 *
 * <p>Serialization is hand-rolled rather than delegated to a JSON library on purpose. The contract
 * needs three things a default writer gets wrong: a fixed key order (so diffs stay readable and
 * "same input, same product" holds — 04 §5), {@code null} values written out explicitly (a
 * contract-mandatory field that is null must be visible in the product, not absent, so V-FMT can
 * tell "no value" from "generator forgot"), and the {@code @generated} header as the first key.
 *
 * @param relativePath path below the resources root, e.g. {@code
 *     data/strife/strife_factions/x.json}
 * @param fields document body in output order; values are String/Long/Boolean, or nested List/Map
 *     of the same
 * @param generatedHeader {@code @generated} text, from {@link TableSource#generatedHeader()}
 */
public record Product(String relativePath, Map<String, Object> fields, String generatedHeader) {

    /** Renders the document: header first, then fields in insertion order. */
    public String toJson() {
        StringBuilder out = new StringBuilder("{\n  \"@generated\": ");
        quote(out, generatedHeader);
        out.append(",\n");
        fields.forEach(
                (key, value) -> {
                    out.append("  ");
                    quote(out, key);
                    out.append(": ");
                    write(out, value);
                    out.append(",\n");
                });
        out.setLength(out.length() - 2);
        return out.append("\n}\n").toString();
    }

    private static void write(StringBuilder out, Object value) {
        switch (value) {
            case null -> out.append("null");
            case String s -> quote(out, s);
            case Boolean b -> out.append(b.booleanValue() ? "true" : "false");
            case Number n -> out.append(n.toString());
            case Map<?, ?> map -> {
                if (map.isEmpty()) {
                    out.append("{}");
                    return;
                }
                out.append("{\n");
                map.forEach(
                        (key, nested) -> {
                            out.append("    ");
                            quote(out, String.valueOf(key));
                            out.append(": ");
                            write(out, nested);
                            out.append(",\n");
                        });
                out.setLength(out.length() - 2);
                out.append("\n  }");
            }
            case List<?> list -> {
                if (list.isEmpty()) {
                    out.append("[]");
                    return;
                }
                out.append("[\n");
                for (Object item : list) {
                    out.append("    ");
                    write(out, item);
                    out.append(",\n");
                }
                out.setLength(out.length() - 2);
                out.append("\n  ]");
            }
            default ->
                    throw new IllegalStateException(
                            "cannot serialize " + value.getClass().getName() + " into a product");
        }
    }

    /** JSON string escaping (RFC 8259 control chars, quote and backslash). */
    private static void quote(StringBuilder out, String text) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}

package com.strife.tools.validator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal reader for {@code content/NUMBERS.md} @@blocks (NUMBERS §0 解析约定).
 *
 * <p>Deliberately a private copy and not a shared library: the validator must be able to audit the
 * truth source on its own, so it cannot trust a parser shipped with the generator that produced the
 * content — the same reasoning as {@link ValidatorMain}'s duplicated SHA-256. The grammar supported
 * here is exactly what NUMBERS.md uses today: a {@code @@<id>} marker line, a single {@code
 * ```yaml} fence, and inside it one-level flow mappings ({@code key: { k: v, ... }}), scalars and
 * inline {@code #} comments. Anything else fails loudly instead of being guessed.
 */
final class NumbersBlocks {

    /** A block line plus its 1-based line number in NUMBERS.md, so problems can name the spot. */
    record Line(int number, String text) {}

    private NumbersBlocks() {}

    /**
     * {@code contentRoot/NUMBERS.md}, or {@code null} when the truth source is not merged yet. A
     * missing truth source skips the checks that read it (with a printed notice) rather than
     * failing CI — the gate arms itself the moment the file lands.
     */
    static Path numbersFile(Path contentRoot) {
        if (contentRoot == null || !Files.isDirectory(contentRoot)) {
            return null;
        }
        Path file = contentRoot.resolve("NUMBERS.md");
        return Files.isRegularFile(file) ? file : null;
    }

    /**
     * Lines of the {@code ```yaml} fence that follows the {@code @@blockId} marker. A present
     * NUMBERS.md that lacks a contracted block is a broken truth source, so this throws — callers
     * turn that into a validator problem instead of silently auditing nothing.
     */
    static List<Line> yamlBlock(Path file, String blockId) {
        List<String> lines = readLines(file);
        int marker = -1;
        for (int i = 0; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();
            if (trimmed.startsWith("#")) {
                continue;
            }
            if (trimmed.equals("@@" + blockId)) {
                marker = i;
                break;
            }
        }
        if (marker < 0) {
            throw new IllegalStateException("no @@" + blockId + " block found (NUMBERS §0)");
        }
        int fence = -1;
        for (int i = marker + 1; i < lines.size(); i++) {
            if (lines.get(i).trim().equals("```yaml")) {
                fence = i;
                break;
            }
            if (!lines.get(i).isBlank()) {
                throw new IllegalStateException(
                        "@@"
                                + blockId
                                + " must be immediately followed by a ```yaml fence"
                                + " (NUMBERS §0), found '"
                                + lines.get(i).trim()
                                + "'");
            }
        }
        if (fence < 0) {
            throw new IllegalStateException("@@" + blockId + " has no ```yaml fence (NUMBERS §0)");
        }
        List<Line> block = new ArrayList<>();
        for (int i = fence + 1; i < lines.size(); i++) {
            String text = lines.get(i);
            if (text.trim().equals("```")) {
                return block;
            }
            block.add(new Line(i + 1, text));
        }
        throw new IllegalStateException("@@" + blockId + " fence is never closed (NUMBERS §0)");
    }

    /** Strips an inline {@code #} comment; values in NUMBERS blocks never contain '#'. */
    static String stripComment(String text) {
        int hash = text.indexOf('#');
        return hash < 0 ? text : text.substring(0, hash);
    }

    private static List<String> readLines(Path file) {
        try {
            return Files.readAllLines(file);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read truth source " + file, e);
        }
    }
}

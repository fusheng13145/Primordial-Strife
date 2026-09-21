package strife.tools.validator;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Build-time content validator (docs/04 §6). Runs on plain JVM, never launches Minecraft.
 *
 * <p>A0-1 scope: ID uniqueness across generated content files. Reference existence, DAG
 * connectivity, probability normalisation, NUMBERS range checks and DSL legality are tracked by
 * later tickets and register here as {@link Check} implementations.
 */
public final class ValidatorMain {

    @FunctionalInterface
    public interface Check {
        List<String> run(Path dataRoot);
    }

    /**
     * Namespace is the parent directory path under the data root, so IDs stay comparable per
     * domain.
     */
    public static List<String> duplicateIds(Path dataRoot) {
        Map<String, List<Path>> seen = new LinkedHashMap<>();
        for (Path file : jsonFiles(dataRoot)) {
            JsonElement root = parse(file);
            if (!root.isJsonObject()) {
                continue;
            }
            JsonObject object = root.getAsJsonObject();
            if (!object.has("id") || !object.get("id").isJsonPrimitive()) {
                continue;
            }
            String id =
                    dataRoot.relativize(file).getParent() + "#" + object.get("id").getAsString();
            seen.computeIfAbsent(id, k -> new ArrayList<>()).add(file);
        }
        List<String> problems = new ArrayList<>();
        seen.forEach(
                (id, files) -> {
                    if (files.size() > 1) {
                        problems.add(
                                "duplicate id '"
                                        + id
                                        + "' in "
                                        + files.stream().map(Path::toString).toList());
                    }
                });
        return problems;
    }

    public static List<Check> checks() {
        return List.of(ValidatorMain::duplicateIds);
    }

    public static void main(String[] args) {
        Path dataRoot = parseDataRoot(args);
        List<String> problems = new ArrayList<>();
        int executed = 0;
        for (Check check : checks()) {
            problems.addAll(check.run(dataRoot));
            executed++;
        }
        problems.forEach(p -> System.err.println("validator: " + p));
        System.out.printf(
                "validator: data-root=%s json-files=%d checks=%d problems=%d%n",
                dataRoot, countJson(dataRoot), executed, problems.size());
        if (!problems.isEmpty()) {
            System.exit(1);
        }
    }

    static Path parseDataRoot(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--data-root".equals(args[i])) {
                return Path.of(args[++i]);
            }
        }
        throw new IllegalArgumentException("usage: validator --data-root <dir>");
    }

    private static List<Path> jsonFiles(Path root) {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (var stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
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

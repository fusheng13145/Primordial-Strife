package com.strife.production;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 炼器运行表：只读内容索引列出的 {@code strife_artifacts} 产物，索引漏项由 V-INDEX 及加载失败暴露。 */
public final class ArtifactTables {

    private static final Logger LOGGER = LoggerFactory.getLogger("strife/production");

    private static final String CONTENT_INDEX_PATH = "data/strife/strife_index.json";
    private static final String ARTIFACT_PREFIX = "data/strife/strife_artifacts/";

    private static final Map<String, ArtifactRecipe> RECIPES = new LinkedHashMap<>();

    private ArtifactTables() {}

    public static synchronized void register() {
        if (!RECIPES.isEmpty()) {
            return;
        }
        RECIPES.putAll(loadRecipes(indexPaths()));
        LOGGER.info("strife production artifact recipes loaded: {}", RECIPES.keySet());
    }

    static List<String> indexPaths() {
        try (var stream = ArtifactTables.class.getResourceAsStream("/" + CONTENT_INDEX_PATH)) {
            if (stream == null) {
                throw new IllegalStateException(CONTENT_INDEX_PATH + " not in jar（DataGen 产物缺失）");
            }
            JsonObject index =
                    JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                            .getAsJsonObject();
            List<String> paths = new ArrayList<>();
            for (var element : index.getAsJsonArray("products")) {
                String path = element.getAsString();
                if (path.startsWith(ARTIFACT_PREFIX)) {
                    paths.add(path);
                }
            }
            if (paths.isEmpty()) {
                throw new IllegalStateException(
                        "strife_index lists no artifacts under " + ARTIFACT_PREFIX);
            }
            return paths;
        } catch (Exception e) {
            throw new IllegalStateException("cannot load " + CONTENT_INDEX_PATH, e);
        }
    }

    static Map<String, ArtifactRecipe> loadRecipes(List<String> paths) {
        Map<String, ArtifactRecipe> recipes = new LinkedHashMap<>();
        for (String path : paths) {
            try (var stream = ArtifactTables.class.getResourceAsStream("/" + path)) {
                if (stream == null) {
                    throw new IllegalStateException(path + " not in jar（DataGen 产物缺失）");
                }
                JsonObject product =
                        JsonParser.parseReader(
                                        new InputStreamReader(stream, StandardCharsets.UTF_8))
                                .getAsJsonObject();
                ArtifactRecipe recipe = ArtifactRecipe.parse(product);
                recipes.put(recipe.id(), recipe);
            } catch (Exception e) {
                throw new IllegalStateException("cannot load artifact recipe " + path, e);
            }
        }
        if (recipes.isEmpty()) {
            throw new IllegalStateException("strife_artifacts: no artifact products in jar");
        }
        return recipes;
    }

    public static Map<String, ArtifactRecipe> recipes() {
        return Map.copyOf(RECIPES);
    }
}

package com.strife.testing;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 测试用的"真实产物"入口：读 {@code content-base} 生成并随 jar 分发的那份 JSON。
 *
 * <p>为什么反复强调"真实"：这一层的缺陷形态是"解析器与产物对不上"，而手写的夹具永远与解析器同步演进，于是这类缺陷在夹具下 不可能出现。只有拿产物本身当输入，才能把"产物 / 解析器 /
 * 真相源"三者的漂移变成一条红用例。
 *
 * <p>产物在测试 classpath 上，是因为 platform 把 content-base 的 resources 挂进了 main srcDir（04 §1 的"进 jar"通道）。
 */
public final class ShippedProducts {

    private ShippedProducts() {}

    /** {@code data/strife/strife_realms/rules.json}（realm 运行时参数）。 */
    public static JsonObject realmRules() {
        return json("/data/strife/strife_realms/rules.json");
    }

    /** 单个境界产物，如 {@code yuanying}。 */
    public static JsonObject realm(String realmId) {
        return json("/data/strife/strife_realms/" + realmId + ".json");
    }

    /** {@code data/strife/strife_worldgen/rules.json}（world 运行时参数）。 */
    public static JsonObject worldRules() {
        return json("/data/strife/strife_worldgen/rules.json");
    }

    /** {@code data/strife/strife_core/rules.json}（core 运行时参数与派生值）。 */
    public static JsonObject coreRules() {
        return json("/data/strife/strife_core/rules.json");
    }

    /** 单张丹方产物，如 {@code pill_juqi}。 */
    public static JsonObject pill(String pillId) {
        return json("/data/strife/strife_pills/" + pillId + ".json");
    }

    /** 单部功法产物，如 {@code tech_qingxin_jue}。 */
    public static JsonObject technique(String techniqueId) {
        return json("/data/strife/strife_techniques/" + techniqueId + ".json");
    }

    /** 序章任务簿产物（{@code data/strife/strife_quests/prologue.json}，与运行时同一份）。 */
    public static JsonObject prologueQuests() {
        return json("/data/strife/strife_quests/prologue.json");
    }

    /** 单个矿石的 strife 侧契约镜像，如 {@code block_ore_lingyu}。 */
    public static JsonObject ore(String oreId) {
        return json("/data/strife/strife_ores/" + oreId + ".json");
    }

    /**
     * 全部矿石产物（枚举目录而非逐个 id 列死）。
     *
     * <p>为什么用枚举：矿石表是"策划随时可能加行"的资源表，把 id 写死在测试里等于给"新加了矿但没人验"留后门——新矿 落进 jar 却没有对应用例，仍然全绿。枚举目录后，新增矿石若缺
     * lang 词条、缺掉落表、缺方块常量，红的是这条用例。
     */
    public static List<JsonObject> allOres() {
        List<JsonObject> products = new ArrayList<>();
        for (String resource : listResources("/data/strife/strife_ores/")) {
            products.add(json("/data/strife/strife_ores/" + resource));
        }
        assertFalse(products.isEmpty(), "strife_ores 目录为空：OreGenerator 没跑或 ores.csv 被清空");
        return products;
    }

    /** 单个原版 loot 2 掉落表（矿石方块的破坏掉落），路径形如 {@code blocks/block_ore_lingyu}。 */
    public static JsonObject lootTable(String relativePath) {
        return json("/data/strife/loot_table/" + relativePath + ".json");
    }

    /** 单个原版 placed feature 产物（M4 世界生成的"放几条、多大、什么高度"）。 */
    public static JsonObject placedFeature(String oreId) {
        return json("/data/strife/worldgen/placed_feature/" + oreId + ".json");
    }

    /** 单个维度物理属性产物（{@code data/strife/dimension_type/<id>.json}，ADR-021）。 */
    public static JsonObject dimensionType(String dimensionId) {
        return json("/data/strife/dimension_type/" + dimensionId + ".json");
    }

    /** 单个维度本体产物（{@code data/strife/dimension/<id>.json}：type + generator）。 */
    public static JsonObject dimension(String dimensionId) {
        return json("/data/strife/dimension/" + dimensionId + ".json");
    }

    /** 单个自研群系产物（{@code data/strife/worldgen/biome/<id>.json}，ADR-021 世界设计）。 */
    public static JsonObject biome(String biomeId) {
        return json("/data/strife/worldgen/biome/" + biomeId + ".json");
    }

    /** 单个 configured feature 产物（上界装饰生成链）。 */
    public static JsonObject configuredFeature(String featureId) {
        return json("/data/strife/worldgen/configured_feature/" + featureId + ".json");
    }

    private static List<String> listResources(String dir) {
        List<String> names = new ArrayList<>();
        java.net.URL root = ShippedProducts.class.getResource(dir);
        if (root == null) {
            return names;
        }
        // 产物既在 jar 内也可能在构建目录的展开目录里，两种形态都要能列。
        try {
            Path path = Path.of(root.toURI());
            if (Files.isDirectory(path)) {
                try (Stream<Path> entries = Files.list(path)) {
                    entries.map(p -> p.getFileName().toString())
                            .filter(name -> name.endsWith(".json"))
                            .forEach(names::add);
                }
            } else {
                // jar 内：走 JarURLConnection 的条目枚举。JarFile 是 ZipFile 子类、实现了 Closeable，
                // 但 JarURLConnection 本身不是 AutoCloseable，所以只能显式 try-with-resources 它取出的 JarFile。
                JarURLConnection connection = (JarURLConnection) root.openConnection();
                String prefix = connection.getEntryName();
                if (prefix != null && !prefix.endsWith("/")) {
                    prefix = prefix + "/";
                }
                try (java.util.jar.JarFile jar = connection.getJarFile()) {
                    java.util.Enumeration<java.util.jar.JarEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        String name = entries.nextElement().getName();
                        if (name.startsWith(prefix) && name.endsWith(".json")) {
                            names.add(name.substring(prefix.length()));
                        }
                    }
                }
            }
        } catch (IOException | URISyntaxException e) {
            throw new UncheckedIOException("cannot list " + dir, new IOException(e));
        }
        names.sort(String::compareTo);
        return names;
    }

    private static JsonObject json(String path) {
        try (InputStream stream = ShippedProducts.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException(
                        path
                                + " 不在测试 classpath 上：platform 必须把 content-base 的 resources 挂进 main srcDir");
            }
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + path, e);
        }
    }
}

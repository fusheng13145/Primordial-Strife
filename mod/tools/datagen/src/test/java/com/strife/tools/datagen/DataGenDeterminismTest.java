package com.strife.tools.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 产物确定性门禁（docs/04 §5「同输入同产物」）。
 *
 * <p><b>为什么必须有这条测试</b>：{@code Map.of} / {@code Set.of} 返回的不可变集合，其迭代顺序由 JVM 启动时生成的随机
 * {@code SALT} 决定（{@code java.util.ImmutableCollections}），<b>同一份代码、同一份输入，两次运行会写出不同键序的
 * JSON</b>。这不是理论风险——上界维度 {@code dimension_type/upper_realm.json} 的 {@code monster_spawn_light_level}
 * 就真实漂移过一次（min/max 互换），表现为"什么都没改却有一行 diff"。
 *
 * <p><b>为什么用扫源码而不是行为断言</b>：这个缺陷在单个 JVM 进程内<b>不可复现</b>——进程内 {@code SALT} 固定，跑一百次顺序都一样。
 * 任何"生成两遍再比对"的用例都会稳定通过，是一条永远不会红的假门禁。它是跨进程缺陷，只能靠静态规则拦住。
 *
 * <p>允许 {@code Map.of()}（空表）：空集合没有顺序可言，写出来就是 {@code {}}。
 */
class DataGenDeterminismTest {

    /** 带参数的 {@code Map.of(...)} / {@code Set.of(...)}；空参 {@code Map.of()} 放行。 */
    private static final Pattern FORBIDDEN = Pattern.compile("(?:Map|Set)\\.of\\((?!\\))");

    /** 从本测试类的 class 位置反推模块根，避免依赖 Gradle 的 workingDir。 */
    private static Path moduleRoot() {
        try {
            Path self =
                    Path.of(
                            DataGenDeterminismTest.class
                                    .getProtectionDomain()
                                    .getCodeSource()
                                    .getLocation()
                                    .toURI());
            // .../mod/tools/datagen/build/classes/java/test → 上溯 4 层到模块根
            Path root = self;
            for (int i = 0; i < 4 && root.getParent() != null; i++) {
                root = root.getParent();
            }
            return root;
        } catch (Exception e) {
            return Path.of("");
        }
    }

    @Test
    void noOrderUnstableFactoriesInGenerators() {
        Path src = moduleRoot().resolve("src/main/java");
        assertTrue(Files.isDirectory(src), "找不到 datagen 源码根目录：" + src.toAbsolutePath());

        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(src)) {
            files.filter(p -> p.toString().endsWith(".java"))
                    .forEach(
                            p -> {
                                try {
                                    List<String> lines = Files.readAllLines(p);
                                    for (int i = 0; i < lines.size(); i++) {
                                        if (FORBIDDEN.matcher(lines.get(i)).find()) {
                                            violations.add(
                                                    moduleRoot().relativize(p)
                                                            + ":"
                                                            + (i + 1)
                                                            + " "
                                                            + lines.get(i).trim());
                                        }
                                    }
                                } catch (IOException e) {
                                    throw new UncheckedIOException(e);
                                }
                            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        assertEquals(
                List.of(),
                violations,
                "DataGen 不得用 Map.of/Set.of 构造产物字段——迭代顺序由 JVM 随机 SALT 决定，"
                        + "产物键序会跨运行漂移（04 §5 同输入同产物）。改用 LinkedHashMap/LinkedHashSet：\n"
                        + String.join("\n", violations));
    }

    @Test
    void productKeepsInsertionOrder() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("type", "minecraft:uniform");
        nested.put("min_inclusive", 0);
        nested.put("max_inclusive", 7);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("ambient_light", 0.5);
        fields.put("monster_spawn_light_level", nested);

        String json = new Product("x.json", fields, "h").toJson();
        assertTrue(
                json.indexOf("\"type\"") < json.indexOf("\"min_inclusive\""),
                "Product 必须按插入序输出，否则上层换用有序容器也救不回确定性：\n" + json);
        assertEquals(json, new Product("x.json", fields, "h").toJson());
    }
}

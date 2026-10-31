package com.strife.testing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** 扫描主源码里对客户端软依赖的硬引用；守卫本身用合成样本自测。 */
public final class SoftDependencyGuard {

    static final List<String> FORBIDDEN = List.of("mezz.jei", "vazkii.patchouli");

    public static List<String> violations(String relativePath, String source) {
        List<String> out = new ArrayList<>();
        for (String marker : FORBIDDEN) {
            int index = source.indexOf(marker);
            if (index < 0) {
                continue;
            }
            int line = 1;
            for (int i = 0; i < index; i++) {
                if (source.charAt(i) == '\n') {
                    line++;
                }
            }
            out.add(relativePath + ":" + line + " hard-references soft dependency " + marker);
        }
        return out;
    }

    public static List<String> scan(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("source root missing: " + root.toAbsolutePath());
        }
        List<String> out = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path path : files.filter(file -> file.toString().endsWith(".java")).toList()) {
                out.addAll(violations(root.relativize(path).toString(), Files.readString(path)));
            }
        }
        return out;
    }

    private SoftDependencyGuard() {}
}

package com.strife.realm;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * 测试用的"真实产物"入口：读 {@code content-base} 生成并随 jar 分发的那份 JSON。
 *
 * <p>为什么反复强调"真实"：这一层的缺陷形态是"解析器与产物对不上"，而夹具（手写的假 JSON）永远与解析器同步演进，
 * 于是这类缺陷在夹具下不可能出现。只有拿产物本身当输入，才能把"产物/解析器/真相源"三者的漂移变成一条红用例。
 *
 * <p>产物在测试 classpath 上是因为 platform 把 content-base 的 resources 挂进了 main srcDir（04 §1 的"进 jar"通道）。
 */
final class ShippedProducts {

    private ShippedProducts() {}

    static JsonObject realmRules() {
        return json("/data/strife/strife_realms/rules.json");
    }

    static JsonObject realm(String realmId) {
        return json("/data/strife/strife_realms/" + realmId + ".json");
    }

    static JsonObject coreRules() {
        return json("/data/strife/strife_core/rules.json");
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

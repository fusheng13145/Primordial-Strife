package com.strife.realm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * 运行时表解析器 ×【真实产物】的契约用例。
 *
 * <p>这一层此前没有任何用例覆盖，也没有任何测试跑过真实产物，于是"解析器读错块"这类缺陷可以一直潜伏：它在<b>第一个玩家 登录</b>时才发作，而发作形态是 NPE 被容错分支吞掉、整个
 * realm 系统静默停工（开服冒烟没有玩家，照不出来）。
 *
 * <p>所以这里刻意不造夹具，直接读 {@code content-base} 生成并随 jar 分发的那份 rules.json——它就在测试 classpath 上 （platform 把
 * content-base 的 resources 挂进了 main srcDir）。产物与解析器任何一侧漂移，这条用例都会红。
 */
class RealmTablesTest {

    private static JsonObject shippedRules() {
        try (InputStream in =
                RealmTablesTest.class.getResourceAsStream(
                        "/data/strife/strife_realms/rules.json")) {
            assertNotNull(
                    in,
                    "content-base 产物不在测试 classpath 上：platform 的 main resources 必须包含 content-base/src/main/resources");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void parsesTheShippedProductWithoutThrowing() {
        RealmTables.Rules rules = RealmTables.parseRules(shippedRules());

        assertNotNull(rules);
        assertEquals(9, rules.rates().size(), "九境各一条突破成功率键");
        assertEquals(0.90, rules.rates().get("bs_qili_zhuji").base(), 1e-9);
        assertEquals(-0.05, rules.rates().get("bs_qili_zhuji").failStep(), 1e-9);
        assertEquals(0.40, rules.rates().get("bs_qili_zhuji").floor(), 1e-9);
    }

    /** 寿元与大限来自 NUMBERS 的 @@lifespan 块：这条断言就是那处"读错块"缺陷的回归。 */
    @Test
    void readsDashengValuesFromTheLifespanBlock() {
        RealmTables.Rules rules = RealmTables.parseRules(shippedRules());

        assertEquals(0.05, rules.dashengResetYearsRatio(), 1e-9);
        assertEquals(1, rules.dashengRealmDropStages());
    }

    @Test
    void convertsSecondsToTicksForTheMeditationCooldown() {
        RealmTables.Rules rules = RealmTables.parseRules(shippedRules());

        assertEquals(40, rules.meditationTickIntervalTicks());
        assertEquals(600L, rules.interruptCooldownTicks(), "30 秒 × 20 刻");
        assertEquals(0.90, rules.interruptProgressKeep(), 1e-9);
        assertEquals(0.0004, rules.meditationInterruptMoveSqr(), 1e-12);
    }

    @Test
    void readsSpiritRootCoefficientsAndWeights() {
        RealmTables.Rules rules = RealmTables.parseRules(shippedRules());

        assertEquals(1.40, rules.qualityTier1(), 1e-9);
        assertEquals(0.70, rules.qualityTier4(), 1e-9);
        assertEquals(2, rules.rollWeightTier1());
        assertEquals(65, rules.rollWeightTier4());
    }

    @Test
    void missingBlockFailsLoudlyWithTheBlockName() {
        JsonObject broken = shippedRules().deepCopy();
        broken.remove("lifespan");

        IllegalStateException error =
                assertThrows(IllegalStateException.class, () -> RealmTables.parseRules(broken));

        assertTrue(error.getMessage().contains("@@lifespan"), error.getMessage());
    }

    @Test
    void missingKeyFailsLoudlyWithBlockAndKey() {
        JsonObject broken = shippedRules().deepCopy();
        broken.getAsJsonObject("lifespan").remove("dasheng_reset_years_ratio");

        IllegalStateException error =
                assertThrows(IllegalStateException.class, () -> RealmTables.parseRules(broken));

        assertTrue(error.getMessage().contains("dasheng_reset_years_ratio"), error.getMessage());
        assertTrue(error.getMessage().contains("lifespan"), error.getMessage());
    }
}

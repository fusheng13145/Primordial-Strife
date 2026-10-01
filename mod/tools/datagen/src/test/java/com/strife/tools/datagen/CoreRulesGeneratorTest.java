package com.strife.tools.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CoreRulesGeneratorTest {

    @TempDir Path temp;

    @Test
    void emitsLimitsAndRateLimitsUnderStrifeCoreDomain() {
        writeNumbers(
                "@@limits\n"
                        + "```yaml\n"
                        + "attachment_budget_bytes: 2048\n"
                        + "sync_packets_per_sec_idle: 5\n"
                        + "sync_payload_max_bytes: 32768\n"
                        + "sync_full_resync_sec: 30\n"
                        + "```\n"
                        + "@@rate_limits\n"
                        + "```yaml\n"
                        + "cast_per_sec: 5\n"
                        + "sit_per_sec: 2\n"
                        + "```\n"
                        + "@@lifespan\n"
                        + "```yaml\n"
                        + "seconds_per_year: 1200\n"
                        + "```\n");
        List<Product> products = new CoreRulesGenerator().generate(NumbersSource.at(temp));

        assertEquals(1, products.size());
        Product product = products.get(0);
        assertEquals("data/strife/strife_core/rules.json", product.relativePath());
        String json = product.toJson();
        assertTrue(json.contains("\"id\": \"core_rules\""), json);
        assertTrue(json.contains("\"limits\""), json);
        assertTrue(json.contains("\"rate_limits\""), json);
        assertTrue(json.contains("\"sync_packets_per_sec_idle\": 5"), json);
        assertTrue(json.contains("\"sit_per_sec\": 2"), json);
        // 派生值：1200 现实秒/修行年 × 20 刻/秒 = 24000 刻（精确值，不再受 0.000833 近似的影响）。
        assertTrue(json.contains("\"ticks_per_year\": 24000"), json);
        assertTrue(json.contains("\"@generated\": \"from content/NUMBERS.md @ sha256:"), json);
    }

    /** 派生值依赖 @lifespan；缺块时必须指名块而不是抛一个空指针。 */
    @Test
    void failsLoudlyWhenLifespanBlockIsMissing() {
        writeNumbers(
                "@@limits\n```yaml\nsync_packets_per_sec_idle: 5\n```\n"
                        + "@@rate_limits\n```yaml\ncast_per_sec: 5\n```\n");
        IllegalStateException e =
                assertThrows(
                        IllegalStateException.class,
                        () -> new CoreRulesGenerator().generate(NumbersSource.at(temp)));
        assertTrue(e.getMessage().contains("lifespan"), e.getMessage());
    }

    /**
     * The limits block is not optional: the sync framework reads its budget keys, so a truth source
     * without it must fail loudly by block name rather than ship a product missing half the table.
     */
    @Test
    void failsLoudlyWhenLimitsBlockMissing() {
        writeNumbers("@@rate_limits\n```yaml\ncast_per_sec: 5\n```\n");
        IllegalStateException e =
                assertThrows(
                        IllegalStateException.class,
                        () -> new CoreRulesGenerator().generate(NumbersSource.at(temp)));
        assertTrue(e.getMessage().contains("@@limits"), e.getMessage());
    }

    @Test
    void failsLoudlyWhenRateLimitsBlockMissing() {
        writeNumbers("@@limits\n```yaml\nsync_packets_per_sec_idle: 5\n```\n");
        IllegalStateException e =
                assertThrows(
                        IllegalStateException.class,
                        () -> new CoreRulesGenerator().generate(NumbersSource.at(temp)));
        assertTrue(e.getMessage().contains("@@rate_limits"), e.getMessage());
    }

    private void writeNumbers(String body) {
        try {
            Files.writeString(temp.resolve("NUMBERS.md"), body);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}

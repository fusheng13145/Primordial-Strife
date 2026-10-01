package com.strife.core.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class IntentRateLimiterTest {

    private static final long SECOND = 1_000_000_000L;

    /** 与 NUMBERS @@rate_limits 同形：cast 5/s、sit 2/s、quest 2/s、artifact 5/s、breakthrough 6/min。 */
    private static IntentRateLimiter limiter() {
        return new IntentRateLimiter(
                Map.of(
                        IntentRateLimiter.CAST, 5.0,
                        IntentRateLimiter.SIT, 2.0,
                        IntentRateLimiter.QUEST, 2.0,
                        IntentRateLimiter.ARTIFACT, 5.0,
                        IntentRateLimiter.BREAKTHROUGH, 6.0 / 60.0));
    }

    @Test
    void eachIntentGetsItsOwnBucket() {
        IntentRateLimiter limiter = limiter();

        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.allow(IntentRateLimiter.CAST, 0L));
        }
        assertFalse(limiter.allow(IntentRateLimiter.CAST, 0L));
        // 施法打空不该连累打坐：不同意图互不借令牌。
        assertTrue(limiter.allow(IntentRateLimiter.SIT, 0L));
    }

    @Test
    void unregisteredIntentIsDeniedAndRecorded() {
        IntentRateLimiter limiter = limiter();

        assertFalse(limiter.allow("teleport_home", 0L), "没配额的意图必须拒绝（fail-closed）");
        assertEquals(java.util.Set.of("teleport_home"), limiter.unknownIntents());
        assertEquals(
                java.util.Set.of("cast", "sit", "quest", "artifact", "breakthrough"),
                limiter.knownIntents());
        assertEquals(0.0, limiter.secondsUntilAvailable("teleport_home", 0L));
    }

    @Test
    void breakthroughIsBoundedPerMinute() {
        IntentRateLimiter limiter = limiter();

        assertTrue(limiter.allow(IntentRateLimiter.BREAKTHROUGH, 0L));
        for (int i = 0; i < 10; i++) {
            assertFalse(
                    limiter.allow(IntentRateLimiter.BREAKTHROUGH, 0L),
                    "连点突破不能刷 fail_step（NUMBERS §10 的存在理由）");
        }
        assertEquals(5.0, limiter.secondsUntilAvailable(IntentRateLimiter.BREAKTHROUGH, 0L), 1e-9);
        assertTrue(limiter.allow(IntentRateLimiter.BREAKTHROUGH, 5L * SECOND));
    }

    @Test
    void exposesConfiguredRatesForDiagnostics() {
        IntentRateLimiter limiter = limiter();
        assertEquals(5.0, limiter.ratePerSecond(IntentRateLimiter.CAST), 1e-9);
        assertEquals(0.1, limiter.ratePerSecond(IntentRateLimiter.BREAKTHROUGH), 1e-9);
        assertEquals(0.0, limiter.ratePerSecond("nope"), 1e-9);
    }
}

package com.strife.core.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TokenBucketTest {

    private static final long SECOND = 1_000_000_000L;

    @Test
    void allowsAFullBurstThenRefillsAtTheConfiguredRate() {
        TokenBucket bucket = new TokenBucket(5.0, 5.0);

        for (int i = 0; i < 5; i++) {
            assertTrue(bucket.tryConsume(0L), "第 " + (i + 1) + " 次突发应放行");
        }
        assertFalse(bucket.tryConsume(0L), "桶空了就该拒绝（这是限速生效，不是异常）");

        // 0.2s 后正好回填 1 个令牌（5/s）。
        assertTrue(bucket.tryConsume(SECOND / 5));
        assertFalse(bucket.tryConsume(SECOND / 5));
    }

    @Test
    void lowFrequencyIntentsOnlyAllowOneImmediateCall() {
        // 突破 = 6 次/分钟 = 0.1/s：容量取 max(1, rate) = 1，不允许"攒 6 次一起点"。
        TokenBucket bucket = new TokenBucket(0.1, 1.0);

        assertTrue(bucket.tryConsume(0L));
        assertFalse(bucket.tryConsume(0L));

        assertFalse(bucket.tryConsume(SECOND), "1 秒只回填 0.1 个令牌");
        assertTrue(bucket.tryConsume(10L * SECOND), "10 秒回填满 1 个");
    }

    @Test
    void reportsWaitingTimeForPlayerFacingRejection() {
        TokenBucket bucket = new TokenBucket(2.0, 1.0);
        assertTrue(bucket.tryConsume(0L));

        assertEquals(0.5, bucket.secondsUntilAvailable(0L), 1e-9);
        assertEquals(0.25, bucket.secondsUntilAvailable(SECOND / 4), 1e-9);
        assertEquals(0.0, bucket.secondsUntilAvailable(SECOND), 1e-9);
    }

    @Test
    void refillNeverExceedsCapacity() {
        TokenBucket bucket = new TokenBucket(5.0, 5.0);
        assertEquals(5.0, bucket.tokens(3_600L * SECOND), 1e-9);
    }

    @Test
    void backwardsClockDoesNotRefillTheBucket() {
        TokenBucket bucket = new TokenBucket(1.0, 1.0);
        assertTrue(bucket.tryConsume(SECOND));
        assertFalse(bucket.tryConsume(SECOND));

        // 时钟回拨：既不补令牌也不把 elapsed 变成负数灌满桶。
        assertFalse(bucket.tryConsume(SECOND / 2));
        assertFalse(bucket.tryConsume(0L));
    }

    @Test
    void rejectsNonsenseConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new TokenBucket(0.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucket(-1.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucket(1.0, 0.5));
    }
}

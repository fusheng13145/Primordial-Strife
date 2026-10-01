package com.strife.core.net;

/**
 * 令牌桶（docs/03 §4 限速手段）。纯逻辑、时钟由调用方注入（{@code nowNanos}），因此限速行为可以确定性地单测——不靠 {@code System.nanoTime()}
 * 打桩。
 *
 * <p>语义：桶满可突发，之后按 {@code ratePerSecond} 匀速回填。首次调用把桶注满（玩家刚进服不该被限速拦住）。
 *
 * <p>并发：只在服务端主线程使用（tick 与包处理都回到主线程），刻意不加锁——加锁会掩盖"从别的线程碰了它"的错误。
 */
public final class TokenBucket {

    private final double ratePerSecond;
    private final double capacity;
    private double tokens;
    private long lastRefillNanos;
    private boolean started;

    /**
     * @param ratePerSecond 稳态速率，必须 &gt; 0（0 或负数等于永久封禁，属配置错误，构造即失败）
     * @param capacity 桶容量（一次可突发的次数），必须 ≥ 1
     */
    public TokenBucket(double ratePerSecond, double capacity) {
        if (!(ratePerSecond > 0.0)) {
            throw new IllegalArgumentException("ratePerSecond must be > 0, got " + ratePerSecond);
        }
        if (capacity < 1.0) {
            throw new IllegalArgumentException("capacity must be >= 1, got " + capacity);
        }
        this.ratePerSecond = ratePerSecond;
        this.capacity = capacity;
        this.tokens = capacity;
    }

    /** 尝试取 1 个令牌；返回 false = 本次请求应被拒绝（不是异常，拒绝是正常路径）。 */
    public boolean tryConsume(long nowNanos) {
        return tryConsume(nowNanos, 1.0);
    }

    /** 尝试取 {@code permits} 个令牌（批量交付之类合并计费）。 */
    public boolean tryConsume(long nowNanos, double permits) {
        if (permits <= 0.0) {
            return true;
        }
        refill(nowNanos);
        if (tokens < permits) {
            return false;
        }
        tokens -= permits;
        return true;
    }

    /** 当前可用令牌数（诊断/审计用）。 */
    public double tokens(long nowNanos) {
        refill(nowNanos);
        return tokens;
    }

    /** 距下一个令牌可用还需多少秒；0 表示现在就能取。给玩家提示"操作过快，请稍候"用（03 §4 拒绝要可解释）。 */
    public double secondsUntilAvailable(long nowNanos) {
        refill(nowNanos);
        if (tokens >= 1.0) {
            return 0.0;
        }
        return (1.0 - tokens) / ratePerSecond;
    }

    public double ratePerSecond() {
        return ratePerSecond;
    }

    public double capacity() {
        return capacity;
    }

    private void refill(long nowNanos) {
        if (!started) {
            started = true;
            lastRefillNanos = nowNanos;
            return;
        }
        long elapsed = nowNanos - lastRefillNanos;
        if (elapsed <= 0L) {
            // 时钟回拨（或同一 tick 内重复调用）：不补也不扣，只把基准向前挪，避免负数时间把桶灌满。
            lastRefillNanos = nowNanos;
            return;
        }
        lastRefillNanos = nowNanos;
        tokens = Math.min(capacity, tokens + (elapsed / 1_000_000_000.0) * ratePerSecond);
    }
}

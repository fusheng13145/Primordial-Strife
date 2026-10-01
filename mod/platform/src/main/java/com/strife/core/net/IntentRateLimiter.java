package com.strife.core.net;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * C2S 意图限速表（docs/03 §4）：每个意图一个 {@link TokenBucket}，速率来自 NUMBERS {@code @@rate_limits} 经 DataGen 进
 * {@code strife_core/rules.json}（05 §1：限速参数属受管数值，代码里不得出现字面量）。
 *
 * <p><b>未登记的意图一律拒绝（fail-closed）</b>：03 §4 的红线是"每个 C2S 意图都必须校验"，一个没有配额的意图意味着有人
 * 加了包却没加限速，静默放行等于把红线交给运气。被拒的 id 记在 {@link #unknownIntents()} 里，测试与启动自检会发现。
 */
public final class IntentRateLimiter {

    /** 意图 id 常量（与 NUMBERS {@code @@rate_limits} 的键一一对应，映射在表读取层做）。 */
    public static final String CAST = "cast";

    public static final String SIT = "sit";
    public static final String QUEST = "quest";
    public static final String ARTIFACT = "artifact";
    public static final String BREAKTHROUGH = "breakthrough";

    /** 面板视图拉取（03 §5"改拉取式：C2S 请求 → S2C 响应"）：读操作同样要有配额，否则它就是一个无限量的服务端计算入口。 */
    public static final String PANEL = "panel";

    private final Map<String, TokenBucket> buckets;
    private final Set<String> unknown = new LinkedHashSet<>();

    /**
     * @param perSecondByIntent 意图 id → 每秒配额；桶容量取 {@code max(1, 速率)}——速率 ≥1/s 的意图允许 1 秒的突发， 低频意图（如每分
     *     6 次的突破 = 0.1/s）只允许 1 次立即执行，之后严格按速率回填
     */
    public IntentRateLimiter(Map<String, Double> perSecondByIntent) {
        Map<String, TokenBucket> built = new LinkedHashMap<>();
        perSecondByIntent.forEach(
                (intent, rate) -> built.put(intent, new TokenBucket(rate, Math.max(1.0, rate))));
        this.buckets = Map.copyOf(built);
    }

    /** 是否放行本次意图；未登记的意图返回 false 并记入 {@link #unknownIntents()}。 */
    public boolean allow(String intentId, long nowNanos) {
        TokenBucket bucket = buckets.get(intentId);
        if (bucket == null) {
            unknown.add(intentId);
            return false;
        }
        return bucket.tryConsume(nowNanos);
    }

    /** 被拒时给玩家的等待提示（秒）；未登记意图返回 0（拒绝原因是配置错误，不是"太快"）。 */
    public double secondsUntilAvailable(String intentId, long nowNanos) {
        TokenBucket bucket = buckets.get(intentId);
        return bucket == null ? 0.0 : bucket.secondsUntilAvailable(nowNanos);
    }

    /** 已登记的意图 id。 */
    public Set<String> knownIntents() {
        return buckets.keySet();
    }

    /** 收到过但没配额的意图 id（配置漏项的证据）。 */
    public Set<String> unknownIntents() {
        return Set.copyOf(unknown);
    }

    public double ratePerSecond(String intentId) {
        TokenBucket bucket = buckets.get(intentId);
        return bucket == null ? 0.0 : bucket.ratePerSecond();
    }
}

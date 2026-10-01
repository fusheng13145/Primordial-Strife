package com.strife.core.net;

import com.strife.core.StrifeData;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * S2C 推送泵：决定"这个 tick 该给这个玩家发什么"（docs/03 §5 的全部约束都落在这里）。
 *
 * <p>纯逻辑、时钟注入，因此 03 §5 的三条硬约束可以逐条单测，而不是靠"看代码觉得对"：
 *
 * <ol>
 *   <li>变更合并 + 只推变化字段 —— 无变化就 0 包（比"静止 ≤5 包/s"更省）；
 *   <li>静止时 ≤ {@code packetsPerSecond} 包/s —— 令牌桶封顶，且被拒时不丢内容，下个 tick 的差量自然包含它；
 *   <li>单包 < {@code maxPayloadBytes} —— 超限时按 {@link StrifeDelta#splitForBudget} 切片、跨 tick 逐片发。
 * </ol>
 *
 * <p>核心不变式：{@link #clientView()} 恒等于"客户端此刻手里那份数据"。它是逐片 {@code applyTo} 推出来的，
 * 不是直接赋成服务端当前值——否则排队中的分片会被静默丢掉，客户端就永远停在半份数据上。
 *
 * <p>另有一条周期性全量重同步：delta 链一旦漏一拍（丢包、重连、别的模块直接写了附件），错误会永久留在客户端镜像里， 全量快照是唯一的收敛手段。
 */
public final class StrifeSyncPump {

    private final int maxPayloadBytes;
    private final TokenBucket budget;
    private final long resyncIntervalNanos;

    private StrifeData clientView;
    private final Deque<StrifeDelta> queue = new ArrayDeque<>();
    private long lastFullSyncNanos;
    private int packetsSent;
    private int fullSyncsSent;

    /**
     * @param initial 客户端已知的数据（登录时给权威值，重连时给旧镜像）
     * @param packetsPerSecond NUMBERS {@code @@limits.sync_packets_per_sec_idle}
     * @param maxPayloadBytes NUMBERS {@code @@limits.sync_payload_max_bytes}
     * @param resyncIntervalNanos NUMBERS {@code @@limits.sync_full_resync_sec} 换算成纳秒
     */
    public StrifeSyncPump(
            StrifeData initial,
            double packetsPerSecond,
            int maxPayloadBytes,
            long resyncIntervalNanos,
            long nowNanos) {
        if (maxPayloadBytes < 1) {
            throw new IllegalArgumentException("maxPayloadBytes must be >= 1");
        }
        this.clientView = initial;
        // 容量刻意取 1，不给突发额度：同步包本身已经按构造合并过（每次内容都是"自上次发送以来的全部变化"），
        // 突发额度买不到任何额外时效，只会让 03 §6 的"静止 ≤5 包/s"按字面不再成立。
        this.budget = new TokenBucket(packetsPerSecond, 1.0);
        this.maxPayloadBytes = maxPayloadBytes;
        this.resyncIntervalNanos = resyncIntervalNanos;
        this.lastFullSyncNanos = nowNanos;
    }

    /**
     * 本 tick 要发的包（按序）。返回空列表 = 什么都不发（无变化，或令牌桶还没回填）。
     *
     * @param current 服务端当前的权威数据
     */
    public List<StrifeDelta> poll(StrifeData current, long nowNanos) {
        refillQueue(current);
        List<StrifeDelta> outgoing = new ArrayList<>();
        while (!queue.isEmpty() && budget.tryConsume(nowNanos)) {
            StrifeDelta part = queue.poll();
            clientView = part.applyTo(clientView);
            outgoing.add(part);
        }
        packetsSent += outgoing.size();
        if (queue.isEmpty() && dueForResync(nowNanos) && outgoing.isEmpty()) {
            StrifeDelta full = StrifeDelta.full(current);
            clientView = current;
            lastFullSyncNanos = nowNanos;
            fullSyncsSent++;
            packetsSent++;
            outgoing.add(full);
        }
        return outgoing;
    }

    /** 客户端此刻应有的数据（测试判据；生产侧不读它）。 */
    public StrifeData clientView() {
        return clientView;
    }

    /** 已发出的包数（诊断：03 §5 的"静止 ≤5 包/s"就用它测）。 */
    public int packetsSent() {
        return packetsSent;
    }

    /** 已发出的全量快照数（诊断）。 */
    public int fullSyncsSent() {
        return fullSyncsSent;
    }

    /** 队列长度（诊断）。 */
    public int queuedParts() {
        return queue.size();
    }

    private void refillQueue(StrifeData current) {
        if (!queue.isEmpty()) {
            return;
        }
        StrifeDelta delta = StrifeDelta.between(clientView, current);
        if (delta.isEmpty()) {
            return;
        }
        List<StrifeDelta> parts = delta.splitForBudget(maxPayloadBytes);
        queue.addAll(parts);
    }

    private boolean dueForResync(long nowNanos) {
        return resyncIntervalNanos > 0L && nowNanos - lastFullSyncNanos >= resyncIntervalNanos;
    }
}

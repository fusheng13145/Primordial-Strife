package com.strife.core.net;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * C2S 意图的分发点与唯一入口（docs/03 §4）。所有校验都在这里做，handler 只处理"校验通过之后"的业务。
 *
 * <p>为什么要有这一层：把限速/大小/登记校验散到各模块的 handler 里，等于让每个新意图都有一次"忘了校验"的机会， 而 03 §4 的红线是"每个 C2S
 * 意图都必须校验"。集中之后，漏项的表现是"意图没登记 → 一律拒绝"（fail-closed）， 而不是"悄悄放行一个没限速的包"。
 *
 * <p>审计：被拒的调用按计数 + debug 日志留痕，不写 info（tick 热路径禁 info 级以上日志，02 §5）。
 */
public final class StrifeIntents {

    /** 一次意图调用的载荷：玩家 + 参数袋。 */
    public record Invocation(ServerPlayer player, CompoundTag args) {}

    /** 意图处理契约：实现方只做业务，校验已在 {@link #dispatch} 完成。 */
    public interface Handler {
        void handle(Invocation invocation);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger("strife/core");

    private static final Map<String, Handler> HANDLERS = new LinkedHashMap<>();
    private static final AtomicLong REJECTED = new AtomicLong();
    private static final AtomicLong UNKNOWN = new AtomicLong();

    private StrifeIntents() {}

    /** 登记一个意图 id；重复登记直接失败（两个模块抢同一个 id 是设计错误，不是运行时状况）。 */
    public static synchronized void register(String intentId, Handler handler) {
        if (intentId == null || intentId.isBlank()) {
            throw new IllegalArgumentException("intent id must not be blank");
        }
        Handler previous = HANDLERS.putIfAbsent(intentId, handler);
        if (previous != null) {
            throw new IllegalStateException("intent '" + intentId + "' is already registered");
        }
    }

    /** 已登记的意图 id（启动自检与诊断用）。 */
    public static synchronized Set<String> registeredIds() {
        return Set.copyOf(HANDLERS.keySet());
    }

    public static long rejectedCount() {
        return REJECTED.get();
    }

    public static long unknownCount() {
        return UNKNOWN.get();
    }

    /** 仅测试用：清空登记表。 */
    static synchronized void clearForTest() {
        HANDLERS.clear();
        REJECTED.set(0L);
        UNKNOWN.set(0L);
    }

    /**
     * 服务端分发：大小 → 限速 → 登记，三者任一不过就丢弃。调用点必须在服务端主线程。
     *
     * <p>注意 {@code args} 允许为 null（有些意图没有参数），handler 自行判空。
     */
    public static void dispatch(ServerPlayer player, String intentId, CompoundTag args) {
        if (player == null || intentId == null) {
            return;
        }
        StrifeCoreRules rules = StrifeCoreRules.getOrNull(player.getServer());
        if (rules == null) {
            // 表读不出来时不同步、也不执行意图：宁可不响应，也不开一条没有限速的路。
            LOGGER.error("strife_core rules unavailable — intent '{}' dropped", intentId);
            return;
        }
        int argsBytes = args == null ? 0 : args.sizeInBytes();
        if (argsBytes > rules.intentArgsMaxBytes()) {
            REJECTED.incrementAndGet();
            LOGGER.debug(
                    "intent '{}' rejected: args {} bytes > limit {}",
                    intentId,
                    argsBytes,
                    rules.intentArgsMaxBytes());
            return;
        }
        if (!rules.intents().allow(intentId, System.nanoTime())) {
            REJECTED.incrementAndGet();
            double wait = rules.intents().secondsUntilAvailable(intentId, System.nanoTime());
            LOGGER.debug(
                    "intent '{}' rate limited (retry in {}s)",
                    intentId,
                    String.format("%.2f", wait));
            return;
        }
        Handler handler;
        synchronized (StrifeIntents.class) {
            handler = HANDLERS.get(intentId);
        }
        if (handler == null) {
            UNKNOWN.incrementAndGet();
            LOGGER.warn("intent '{}' has no registered handler — dropped", intentId);
            return;
        }
        handler.handle(new Invocation(player, args));
    }
}

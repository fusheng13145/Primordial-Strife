package com.strife.core.net;

import com.strife.core.StrifeAttachmentTypes;
import com.strife.core.StrifeData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * S2C 同步的服务端驱动（docs/03 §5）。
 *
 * <p>为什么是"每 tick 比对"而不是"让各模块上报脏标记"：模块直接写附件的地方会一直增加（realm 结算、quest 奖励、命令、 将来的
 * combat），任何一处忘了标记，客户端镜像就永久偏一格。逐 tick 比对只读 11 个字段，代价可忽略，换来的是
 * <b>"镜像必然收敛"这条性质不依赖任何模块的自觉</b>——而"不漏"正是同步框架唯一真正重要的事。
 *
 * <p>推送节奏全部交给 {@link StrifeSyncPump}：无变化 0 包、静止 ≤ NUMBERS 上限、超预算跨 tick 分片、周期性全量重同步。
 */
public final class StrifeSyncManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("strife/core");

    /** 只在服务端主线程访问；HashMap 足够，不需要并发容器。 */
    private static final Map<UUID, StrifeSyncPump> PUMPS = new HashMap<>();

    private StrifeSyncManager() {}

    /** 登录/重连：先发一份全量快照，再开一条按玩家计费的推送泵。 */
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            startTracking(player);
        }
    }

    private static void startTracking(ServerPlayer player) {
        StrifeCoreRules rules = StrifeCoreRules.getOrNull(player.getServer());
        if (rules == null) {
            LOGGER.error("strife_core rules unavailable — client mirror will not be initialised");
            return;
        }
        StrifeData data = player.getData(StrifeAttachmentTypes.PLAYER_DATA);
        StrifeNetwork.sendTo(player, new StrifeSnapshotPayload(data));
        StrifeCoreRules.SyncBudget budget = rules.syncBudget();
        PUMPS.put(
                player.getUUID(),
                new StrifeSyncPump(
                        data,
                        budget.packetsPerSecondIdle(),
                        budget.maxPayloadBytes(),
                        budget.resyncIntervalNanos(),
                        System.nanoTime()));
    }

    /** 退出：丢掉该玩家的推送状态，避免长期运行下的状态泄漏。 */
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PUMPS.remove(player.getUUID());
        }
    }

    /** 每刻：把 authoritative 数据推给客户端（无变化则一个包都不发）。 */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide) {
            return;
        }
        StrifeSyncPump pump = PUMPS.get(player.getUUID());
        if (pump == null) {
            // 数据在登录监听之前就 tick 过（重载/热更）时会走到这里：补一次初始化而不是丢帧。
            startTracking(player);
            pump = PUMPS.get(player.getUUID());
            if (pump == null) {
                return;
            }
        }
        StrifeData current = player.getData(StrifeAttachmentTypes.PLAYER_DATA);
        for (StrifeDelta part : pump.poll(current, System.nanoTime())) {
            StrifeNetwork.sendTo(player, new StrifeDeltaPayload(part));
        }
    }

    /** 当前被跟踪的玩家数（诊断/用例判据）。 */
    public static int trackedPlayers() {
        return PUMPS.size();
    }

    /** 强制丢弃某玩家的推送状态（数据版本变更/重连兜底）。 */
    public static void forget(UUID playerId) {
        PUMPS.remove(playerId);
    }
}

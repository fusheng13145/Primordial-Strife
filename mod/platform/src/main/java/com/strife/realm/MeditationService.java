package com.strife.realm;

import com.strife.core.MeditationState;
import com.strife.core.StrifeAttachmentTypes;
import com.strife.core.StrifeData;
import com.strife.core.StrifeTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 打坐服务（单一职责拆分，2026-11-11）：起止意图、时间片结算、打断惩罚、顿悟——修炼主链中 "打坐"这一件事的全部逻辑。会话结算入口由 {@link
 * CultivationHandler#tick} 编排调用；命令与意图 （sit 意图）走 {@link #toggleSit}。
 *
 * <p>顿悟（[拟]）是打坐域的内生事件：入定满 30 秒后每次结算 1/100 概率按当前速率瞬得 60 秒修为—— 一次性瞬得零新状态（不引入计时增益第二机制，与丹药增益共存不互斥）。
 */
public final class MeditationService {

    /** 每玩家的位移基准（判断"打坐中是否移动"）；纯瞬态，不入档。 */
    private static final Map<UUID, Position> POSITIONS = new ConcurrentHashMap<>();

    private record Position(double x, double y, double z) {}

    // 修仙世界感·顿悟参数（[拟]，随实机校准）
    private static final int ENLIGHTEN_ONE_IN = 100;
    private static final long ENLIGHTEN_MIN_MEDITATION_TICKS = 30L * StrifeTime.TICKS_PER_SECOND;
    private static final int ENLIGHTEN_SECONDS = 60;

    MeditationService() {}

    /** 打坐起止意图（03 §4）。处境不允许时给可读的原因，而不是静默失败。 */
    public static void toggleSit(ServerPlayer player) {
        RealmTables tables = RealmTables.getOrNull(player.server);
        if (tables == null) {
            return;
        }
        long now = player.server.getTickCount();
        StrifeData data = player.getData(StrifeAttachmentTypes.PLAYER_DATA);
        RealmTables.RealmEntry realm =
                tables.realm(Math.min(data.realmOrdinal(), tables.realmCount() - 1));
        MeditationState meditation = data.meditation();

        if (meditation.active()) {
            data = settle(player, data, realm, tables, now);
            CultivationHandler.write(player, data, d -> d.withMeditation(d.meditation().stop()));
            CultivationHandler.tell(player, Component.translatable("msg.strife.sit.stop"));
            RealmViewBuilder.pushTo(player);
            return;
        }
        if (meditation.onCooldown(now)) {
            CultivationHandler.tell(
                    player,
                    Component.translatable(
                            "msg.strife.sit.cooldown",
                            secondsOf(meditation.cooldownRemaining(now))));
            return;
        }
        if (!player.onGround() || player.isInWater() || player.isDeadOrDying()) {
            CultivationHandler.tell(player, Component.translatable("msg.strife.sit.blocked"));
            return;
        }
        CultivationHandler.write(player, data, d -> d.withMeditation(meditation.start(now)));
        CultivationHandler.tell(player, Component.translatable("msg.strife.sit.start"));
        RealmViewBuilder.pushTo(player);
    }

    /** 把会话结算到当前刻：只补发"应得 − 已发"的差额（时间片结算的核心，见 {@link MeditationMath}）。 */
    static StrifeData settle(
            ServerPlayer player,
            StrifeData data,
            RealmTables.RealmEntry realm,
            RealmTables tables,
            long now) {
        MeditationState meditation = data.meditation();
        double qiPerTick = CultivationHandler.qiPerTick(player, tables, realm, data);
        int earned = MeditationMath.earnedSince(meditation.startTick(), now, qiPerTick);
        int delta = MeditationMath.settlementDelta(earned, meditation.creditedQi());
        if (delta == 0) {
            return data;
        }
        MeditationMath.Applied applied = MeditationMath.apply(data.qi(), delta, realm.qiMax());
        int credited = meditation.creditedQi() + Math.max(0, applied.applied());
        // 修仙世界感·顿悟（[拟]）：入定满 30 秒后，每次结算 1/100 概率灵台清明——按当前速率瞬得 60 秒修为。
        // 一次性瞬得，零新状态（不引入计时增益的第二套机制，与丹药增益共存不互斥）。
        int bonusQi = 0;
        long meditatedTicks = now - meditation.startTick();
        if (meditatedTicks >= ENLIGHTEN_MIN_MEDITATION_TICKS
                && player.getRandom().nextInt(ENLIGHTEN_ONE_IN) == 0) {
            bonusQi =
                    (int)
                            Math.max(
                                    0L,
                                    Math.min(
                                            (long)
                                                    (qiPerTick
                                                            * ENLIGHTEN_SECONDS
                                                            * StrifeTime.TICKS_PER_SECOND),
                                            realm.qiMax() - applied.qi()));
        }
        if (bonusQi > 0) {
            CultivationHandler.tell(
                    player,
                    Component.translatable("msg.strife.enlightenment", bonusQi)
                            .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        final int finalQi = applied.qi() + bonusQi;
        return CultivationHandler.write(
                player,
                data,
                d ->
                        CultivationHandler.withQiAndStage(d, finalQi, realm)
                                .withMeditation(meditation.withCreditedQi(credited)));
    }

    /** 打断：本次会话所得按 NUMBERS §5 比例保留（不足则收回已发部分），并写入再入定冷却。 */
    static StrifeData interrupt(
            ServerPlayer player,
            StrifeData data,
            RealmTables.RealmEntry realm,
            RealmTables tables,
            long now) {
        MeditationState meditation = data.meditation();
        double qiPerTick = CultivationHandler.qiPerTick(player, tables, realm, data);
        int keptEarned =
                MeditationMath.earnedAfterInterrupt(
                        meditation.startTick(),
                        now,
                        qiPerTick,
                        tables.rules().interruptProgressKeep());
        int delta = MeditationMath.settlementDelta(keptEarned, meditation.creditedQi());
        MeditationMath.Applied applied = MeditationMath.apply(data.qi(), delta, realm.qiMax());
        long cooldownTicks = tables.rules().interruptCooldownTicks();
        StrifeData updated =
                CultivationHandler.withQiAndStage(data, applied.qi(), realm)
                        .withMeditation(meditation.interrupt(now, cooldownTicks));
        CultivationHandler.write(player, data, d -> updated);
        CultivationHandler.tell(
                player,
                Component.translatable("msg.strife.sit.interrupted", secondsOf(cooldownTicks))
                        .withStyle(ChatFormatting.GRAY));
        RealmViewBuilder.pushTo(player);
        return updated;
    }

    /** 打断判定：离地/入水/垂死/受伤/位移超阈值，任一成立即打断。 */
    static boolean interrupted(ServerPlayer player, boolean moved) {
        return moved
                || !player.onGround()
                || player.isInWater()
                || player.isDeadOrDying()
                || player.hurtTime > 0;
    }

    /** 与上一刻的位置比较（首刻没有基准，视为未移动）。 */
    static boolean movedBeyond(ServerPlayer player, double thresholdSqr) {
        Position previous =
                POSITIONS.put(
                        player.getUUID(),
                        new Position(player.getX(), player.getY(), player.getZ()));
        if (previous == null) {
            return false;
        }
        double dx = player.getX() - previous.x();
        double dy = player.getY() - previous.y();
        double dz = player.getZ() - previous.z();
        return dx * dx + dy * dy + dz * dz > thresholdSqr;
    }

    /** 登录收尾：残留会话停止（不结算不清冷却——离线不计修为），位移基准清空。 */
    static void clearSessionOnLogin(ServerPlayer player) {
        POSITIONS.remove(player.getUUID());
    }

    /** 登出：位移基准清空（结算由调用方完成）。 */
    static void clearSessionOnLogout(ServerPlayer player) {
        POSITIONS.remove(player.getUUID());
    }

    private static String secondsOf(long ticks) {
        return Long.toString(Math.max(0L, ticks) / StrifeTime.TICKS_PER_SECOND);
    }
}

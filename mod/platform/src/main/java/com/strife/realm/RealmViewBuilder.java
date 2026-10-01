package com.strife.realm;

import com.strife.core.CultivationFactors;
import com.strife.core.MeditationState;
import com.strife.core.StrifeAttachmentTypes;
import com.strife.core.StrifeData;
import com.strife.core.StrifeTime;
import com.strife.core.net.StrifeCoreRules;
import com.strife.core.net.StrifeNetwork;
import com.strife.core.net.StrifeRealmView;
import com.strife.core.net.StrifeRealmViewPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * 面板视图的生产者（docs/03 §5 拉取式 C2S→S2C）：把玩家当前的修炼处境算成一份 {@link StrifeRealmView} 发给客户端。
 *
 * <p>放在 realm 而不是 core 的理由：视图里的每一项都需要 realm 的表与公式（成功率、小境界、失败回退区间、四因子速率）。
 * 客户端不复制这些计算——复制出来的那份一定会与真相源漂移，而漂移的表现是"面板显示 90%，实际按 85% 判定"。
 *
 * <p>推送时机：登录（首屏）、玩家操作后（入定/出定/打断/突破）、以及客户端主动拉取（打开面板）。
 */
public final class RealmViewBuilder {

    private RealmViewBuilder() {}

    /** 算一份视图并发给玩家；表不可用时静默跳过（tick/操作路径不应该因为表缺失而崩）。 */
    public static void pushTo(ServerPlayer player) {
        StrifeRealmView view = build(player);
        if (view != null) {
            StrifeNetwork.sendTo(player, new StrifeRealmViewPayload(view));
        }
    }

    /** 组装视图；表不可用时返回 null。 */
    public static StrifeRealmView build(ServerPlayer player) {
        RealmTables tables = RealmTables.getOrNull(player.server);
        StrifeCoreRules core = StrifeCoreRules.getOrNull(player.server);
        if (tables == null || core == null) {
            return null;
        }
        StrifeData data = player.getData(StrifeAttachmentTypes.PLAYER_DATA);
        int ordinal = Math.min(Math.max(0, data.realmOrdinal()), tables.realmCount() - 1);
        RealmTables.RealmEntry realm = tables.realm(ordinal);
        boolean finalRealm = realm.ordinal() >= tables.realmCount() - 1;

        double successRate = 0.0;
        if (!finalRealm) {
            RealmTables.BreakthroughRate rate = tables.rate(realm.breakthroughKey());
            successRate =
                    BreakthroughMath.successProbability(
                            rate.base(),
                            rate.failStep(),
                            data.breakthroughAttempts(),
                            rate.floor());
        }

        long now = player.server.getTickCount();
        MeditationState meditation = data.meditation();
        CultivationFactors.Coefficients factors = CultivationFactors.coefficients(player);
        return new StrifeRealmView(
                realm.id(),
                realm.ordinal(),
                data.stage(),
                realm.stageCount(),
                data.qi(),
                realm.qiMax(),
                finalRealm,
                successRate,
                data.breakthroughAttempts(),
                tables.rules().qiResetRatioMin(),
                tables.rules().qiResetRatioMax(),
                meditation.active(),
                (int) (meditation.cooldownRemaining(now) / StrifeTime.TICKS_PER_SECOND),
                CultivationHandler.qiPerSecond(player, tables, realm, data),
                tables.qualityCoefficient(data.spiritrootQuality()),
                factors.environment(),
                factors.technique(),
                factors.pill(),
                data.techniques().equipped(),
                core.yearsOf(data.lifespanTicks()));
    }
}

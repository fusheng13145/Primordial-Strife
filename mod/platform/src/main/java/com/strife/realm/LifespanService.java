package com.strife.realm;

import com.strife.core.StrifeData;
import com.strife.core.StrifeTime;
import com.strife.core.net.StrifeCoreRules;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 寿元服务（单一职责拆分，2026-11-11）：修行年→刻换算、大限结算（境界跌落+寿元重置+虚弱）。
 *
 * <p>大限绝不删角色、绝不扣到凡人以下（{@link DashengMath} 用例钉住）；已在凡人时退无可退，寿元照旧 重置——这是"续命玩法"存在的意义，而不是把玩家逼进死循环。
 */
public final class LifespanService {

    LifespanService() {}

    /**
     * 修行年 → 游戏刻。换算率取自 NUMBERS §4（经 DataGen 产物 {@code strife_core/rules.json} 的 {@code
     * derived.ticks_per_year}），代码零字面量——这也是 realm 向 core 取数的少数场景之一（realm → core 是允许方向，03 §2）。
     */
    static long yearsToTicks(ServerPlayer player, long years) {
        return years * StrifeCoreRules.get(player.server).ticksPerYear();
    }

    /**
     * 大限结算（05 §4 / ADR-008 非破坏性）：境界回退 {@code dasheng_realm_drop_stages} 档 → 修为归零重来 → 残余寿元按 新境界年限 ×
     * {@code dasheng_reset_years_ratio} 重置 → 附加虚弱（{@code dasheng_debuff_key}）。
     *
     * <p>返回整份新数据（境界变了，不能只改一个字段）。
     */
    static StrifeData greatLimit(
            ServerPlayer player,
            StrifeData data,
            RealmTables.RealmEntry realm,
            RealmTables tables) {
        int stages = tables.rules().dashengRealmDropStages();
        int fallenOrdinal = Math.max(0, realm.ordinal() - Math.max(0, stages));
        RealmTables.RealmEntry fallen = tables.realm(fallenOrdinal);
        DashengMath.Outcome outcome =
                DashengMath.settle(
                        realm.ordinal(),
                        stages,
                        fallen.lifespanYears(),
                        tables.rules().dashengResetYearsRatio(),
                        StrifeCoreRules.get(player.server).ticksPerYear());

        StrifeData updated =
                data.withRealm(outcome.newOrdinal(), 1, outcome.qi(), outcome.lifespanTicks(), 0)
                        // 大限即出定：不清打坐状态的话，玩家会在虚弱中继续按旧会话结算修为。
                        .withMeditation(data.meditation().stop());
        CultivationHandler.write(player, data, d -> updated);

        RealmEffects.apply(player, RealmEffects.WEAK, tables.rules().debuffDurationTicks());
        long years = outcome.lifespanTicks() / StrifeCoreRules.get(player.server).ticksPerYear();
        if (outcome.demoted()) {
            player.displayClientMessage(
                    Component.translatable(
                                    "msg.strife.lifespan.limit_demoted",
                                    Component.translatable("realm.strife." + fallen.id()),
                                    years)
                            .withStyle(ChatFormatting.DARK_RED),
                    false);
        } else {
            player.displayClientMessage(
                    Component.translatable("msg.strife.lifespan.limit", years)
                            .withStyle(ChatFormatting.DARK_RED),
                    false);
        }
        RealmEvents.postDashengSettlement(
                player,
                realm.ordinal(),
                outcome.newOrdinal(),
                outcome.demoted(),
                outcome.lifespanTicks(),
                tables.rules().dashengDebuffKey());
        RealmViewBuilder.pushTo(player);
        return updated;
    }

    /** 按秒推进寿元（tick 编排调用；到点结算由编排器判）。 */
    static StrifeData tickSecond(ServerPlayer player, StrifeData data) {
        long remaining = data.lifespanTicks() - StrifeTime.TICKS_PER_SECOND;
        return CultivationHandler.write(
                player, data, d -> CultivationHandler.withLifespan(d, remaining));
    }
}

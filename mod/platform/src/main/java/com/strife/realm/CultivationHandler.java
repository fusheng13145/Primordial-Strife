package com.strife.realm;

import com.strife.core.CultivationFactors;
import com.strife.core.StrifeAttachmentTypes;
import com.strife.core.StrifeData;
import com.strife.core.StrifeTime;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 修炼主链的编排器（单一职责拆分，2026-11-11）：每刻按序调用寿元流动 → 心魔结算 → 打坐结算； 三个服务各管一域——{@link
 * MeditationService}（打坐/顿悟）、{@link BreakthroughService}（押注突破/ 渡劫/心魔入体）、{@link
 * LifespanService}（寿元/大限）。本类只保留编排与共享写档纪律（write/ withQiAndStage）与速率查询（面板与闭关命令消费）。
 *
 * <p>与旧版的三处实质差别（旧版自陈为"MVP 快速通道"）：
 *
 * <ol>
 *   <li><b>打坐不再是"潜行即打坐"</b>：状态由 03 §4 的 {@code sit} 意图显式起止，入档为 {@link MeditationState}，结算按时间戳 做差（03
 *       §3），打断有惩罚与冷却，主动出定不吃惩罚。
 *   <li><b>突破不再自动触发</b>：修为满只是"可以押注"的前提；是否突破由玩家决定（NUMBERS §10 因此才需要 breakthrough 限速）。
 *   <li><b>四因子公式写全</b>：环境/功法/丹药三项经 {@link CultivationFactors} 取（来源模块尚未落地时取中性 1.0）， 不再把 1.0
 *       内联进公式里假装它们不存在。
 * </ol>
 *
 * <p>数值全部来自 DataGen 产物（{@link RealmTables} + {@code strife_core/rules.json}），代码零受管字面量。
 */
public final class CultivationHandler {

    CultivationHandler() {}

    /** 每刻结算（{@link StrifeRealm} 在 PlayerTickEvent.Post 上调用）。 */
    static void tick(ServerPlayer player) {
        RealmTables tables = RealmTables.getOrNull(player.server);
        if (tables == null) {
            return;
        }
        long now = player.server.getTickCount();
        StrifeData data = player.getData(StrifeAttachmentTypes.PLAYER_DATA);
        if (data.spiritrootQuality() == 0) {
            data = SpiritRootGenerator.ensureGenerated(player, data, tables);
        }
        RealmTables.RealmEntry realm =
                tables.realm(Math.min(data.realmOrdinal(), tables.realmCount() - 1));

        // 1. 寿元：新玩家先按当前境界初始化，随后按秒结算（每刻回写附件 = 20 次/s 的无谓写档，秒级精度足够）
        RealmTables.RealmEntry active = realm;
        if (data.lifespanTicks() <= 0L) {
            data =
                    write(
                            player,
                            data,
                            d ->
                                    CultivationHandler.withLifespan(
                                            d,
                                            LifespanService.yearsToTicks(
                                                    player, realm.lifespanYears())));
        } else if (now % StrifeTime.TICKS_PER_SECOND == 0L) {
            long remaining = data.lifespanTicks() - StrifeTime.TICKS_PER_SECOND;
            if (remaining <= 0L) {
                data = LifespanService.greatLimit(player, data, realm, tables);
                // 大限可能让境界跌落：后面的打坐结算必须用新境界的上限与速率，否则会按旧境界继续涨修为。
                active = tables.realm(Math.min(data.realmOrdinal(), tables.realmCount() - 1));
                RealmTitles.apply(player);
            } else {
                data = write(player, data, d -> withLifespan(d, remaining));
            }
        }

        // 2. 心魔（ADR-040）：到期清空 + 活跃期环境反馈。时间基用 level().getGameTime()——心魔附件持久化
        //    expiryTick，服务器 tickCount 重启归零而 gameTime 连续，混用会让旧档的心魔在重启后"复活"。
        long gameTime = player.level().getGameTime();
        var demon = player.getData(StrifeAttachmentTypes.HEART_DEMON);
        if (demon.active()) {
            var settled = demon.expire(gameTime);
            if (settled != demon) {
                player.setData(StrifeAttachmentTypes.HEART_DEMON, settled);
                if (!settled.active()) {
                    tell(
                            player,
                            Component.literal("✦ 心魔随时间消散——道心恢复清明").withStyle(ChatFormatting.GREEN));
                }
            }
            if (settled.active() && gameTime % StrifeTime.TICKS_PER_SECOND == 0L) {
                // 活跃期视觉：每秒一撮紫色涡旋粒子（服务端 sendParticles，附近玩家同样可见——"心魔缠身"是外显的）
                player.serverLevel()
                        .sendParticles(
                                net.minecraft.core.particles.ParticleTypes.PORTAL,
                                player.getX(),
                                player.getY() + 1.0,
                                player.getZ(),
                                10,
                                0.4,
                                0.6,
                                0.4,
                                0.5);
            }
        }

        // 3. 打坐：先判打断（每刻都要判），再按 tick_interval_ticks 降频结算（03 §6 单玩家附加逻辑预算）
        boolean moved =
                MeditationService.movedBeyond(player, tables.rules().meditationInterruptMoveSqr());
        if (data.meditation().active()) {
            if (MeditationService.interrupted(player, moved)) {
                data = MeditationService.interrupt(player, data, active, tables, now);
            } else if (data.qi() >= active.qiMax()) {
                data = MeditationService.settle(player, data, active, tables, now);
                data = write(player, data, d -> d.withMeditation(d.meditation().stop()));
                tell(player, Component.translatable("msg.strife.sit.qi_full"));
            } else if (now % tables.rules().meditationTickIntervalTicks() == 0L) {
                RealmEvents.postSit(player, tables.rules().meditationTickIntervalTicks());
                data = MeditationService.settle(player, data, active, tables, now);
            }
        }
    }

    /**
     * 登录：NUMBERS §5 {@code offline_gain_allowed: false} → 离线时段不计修为，残留会话一律收尾（不结算、不清冷却）。
     *
     * <p>这段代码存在的意义是"断线不算挂机"：崩溃或强杀时不会有登出事件，会话会留在档里，如果登录时不收尾，下一次结算就会把 离线时长乘上速率发给玩家。
     */
    public static void onLogin(ServerPlayer player) {
        StrifeData data = player.getData(StrifeAttachmentTypes.PLAYER_DATA);
        if (data.meditation().active()) {
            write(player, data, d -> d.withMeditation(d.meditation().stop()));
        }
        MeditationService.clearSessionOnLogin(player);
    }

    /** 退出：在线时段照常结算，然后收尾（冷却保留在档里，重连后仍生效）。 */
    public static void onLogout(ServerPlayer player) {
        RealmTables tables = RealmTables.getOrNull(player.server);
        if (tables != null) {
            StrifeData data = player.getData(StrifeAttachmentTypes.PLAYER_DATA);
            RealmTables.RealmEntry realm =
                    tables.realm(Math.min(data.realmOrdinal(), tables.realmCount() - 1));
            if (data.meditation().active()) {
                data =
                        MeditationService.settle(
                                player, data, realm, tables, player.server.getTickCount());
                write(player, data, d -> d.withMeditation(d.meditation().stop()));
            }
        }
        MeditationService.clearSessionOnLogout(player);
    }

    /** 打坐起止意图（03 §4）——委托打坐服务。 */
    public static void toggleSit(ServerPlayer player) {
        MeditationService.toggleSit(player);
    }

    /** 主动押注突破——委托突破服务。 */
    public static void attemptBreakthrough(ServerPlayer player) {
        BreakthroughService.attemptBreakthrough(player);
    }

    /** 每秒修为速率：05 §2 四因子公式（基础速率 × 灵根 × 环境 × 功法 × 丹药），再乘 ADR-040 心魔惩罚。 */
    static double qiPerSecond(
            ServerPlayer player,
            RealmTables tables,
            RealmTables.RealmEntry realm,
            StrifeData data) {
        // 心魔活跃时按层折减（@@heart_demon.qi_rate_penalty）；到期状态由 tick() 的心魔结算步先清，
        // 这里只读不改——查询函数不携带写附件的副作用。
        double demonMultiplier =
                player.getData(StrifeAttachmentTypes.HEART_DEMON)
                        .qiRateMultiplier(tables.rules().heartDemonQiRatePenalty());
        return realm.sitRate()
                * tables.qualityCoefficient(data.spiritrootQuality())
                * CultivationFactors.coefficients(player).product()
                * demonMultiplier;
    }

    /** 单刻速率（结算用；换算集中在 {@link StrifeTime}）。 */
    static double qiPerTick(
            ServerPlayer player,
            RealmTables tables,
            RealmTables.RealmEntry realm,
            StrifeData data) {
        return StrifeTime.perSecondToPerTick(qiPerSecond(player, tables, realm, data));
    }

    /** 只改寿元（core 的 wither 替代手拼 13 个字段——漏一个字段是这类构造最常见的错法）。 */
    static StrifeData withLifespan(StrifeData data, long lifespan) {
        return data.withLifespanTicks(lifespan);
    }

    /** 改 qi 并同步推导小境界（NUMBERS §1：阈值不入表）。共享写档纪律：三个服务与编排器共用。 */
    static StrifeData withQiAndStage(StrifeData data, int qi, RealmTables.RealmEntry realm) {
        return data.withRealm(
                data.realmOrdinal(),
                BreakthroughMath.stageFor(qi, realm.qiMax(), realm.stageCount()),
                qi,
                data.lifespanTicks(),
                data.breakthroughAttempts());
    }

    /** 附件值对象不可变：改字段 = 构造新记录整体 setData 回写（core 契约）；未变则不写。 */
    static StrifeData write(
            ServerPlayer player, StrifeData current, Function<StrifeData, StrifeData> mutator) {
        StrifeData updated = mutator.apply(current);
        if (updated != current) {
            player.setData(StrifeAttachmentTypes.PLAYER_DATA, updated);
        }
        return updated;
    }

    /** 动作栏短消息（结算反馈口径：不打断聊天流）。 */
    static void tell(ServerPlayer player, Component message) {
        player.displayClientMessage(message, true);
    }
}

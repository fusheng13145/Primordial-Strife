package com.strife.realm;

import com.strife.core.CultivationFactors;
import com.strife.core.MeditationState;
import com.strife.core.StrifeAttachmentTypes;
import com.strife.core.StrifeData;
import com.strife.core.StrifeTime;
import com.strife.core.net.StrifeCoreRules;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

/**
 * 修炼主链的服务端结算：寿元流动 → 打坐（时间片结算 + 打断惩罚 + 冷却）→ 大限结算；突破改为玩家主动押注（{@link #attemptBreakthrough}）。
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

    /** 每玩家的位移基准（判断"打坐中是否移动"）；纯瞬态，不入档。 */
    private static final Map<UUID, Position> POSITIONS = new ConcurrentHashMap<>();

    private record Position(double x, double y, double z) {}

    private CultivationHandler() {}

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
                            d -> withLifespan(d, yearsToTicks(player, realm.lifespanYears())));
        } else if (now % StrifeTime.TICKS_PER_SECOND == 0L) {
            long remaining = data.lifespanTicks() - StrifeTime.TICKS_PER_SECOND;
            if (remaining <= 0L) {
                data = greatLimit(player, data, realm, tables);
                // 大限可能让境界跌落：后面的打坐结算必须用新境界的上限与速率，否则会按旧境界继续涨修为。
                active = tables.realm(Math.min(data.realmOrdinal(), tables.realmCount() - 1));
            } else {
                data = write(player, data, d -> withLifespan(d, remaining));
            }
        }

        // 2. 打坐：先判打断（每刻都要判），再按 tick_interval_ticks 降频结算（03 §6 单玩家附加逻辑预算）
        boolean moved = movedBeyond(player, tables.rules().meditationInterruptMoveSqr());
        if (data.meditation().active()) {
            if (interrupted(player, moved)) {
                data = interrupt(player, data, active, tables, now);
            } else if (data.qi() >= active.qiMax()) {
                data = settle(player, data, active, tables, now);
                data = write(player, data, d -> d.withMeditation(d.meditation().stop()));
                tell(player, Component.translatable("msg.strife.sit.qi_full"));
            } else if (now % tables.rules().meditationTickIntervalTicks() == 0L) {
                RealmEvents.postSit(player, tables.rules().meditationTickIntervalTicks());
                data = settle(player, data, active, tables, now);
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
        POSITIONS.remove(player.getUUID());
    }

    /** 退出：在线时段照常结算，然后收尾（冷却保留在档里，重连后仍生效）。 */
    public static void onLogout(ServerPlayer player) {
        RealmTables tables = RealmTables.getOrNull(player.server);
        if (tables != null) {
            StrifeData data = player.getData(StrifeAttachmentTypes.PLAYER_DATA);
            RealmTables.RealmEntry realm =
                    tables.realm(Math.min(data.realmOrdinal(), tables.realmCount() - 1));
            if (data.meditation().active()) {
                data = settle(player, data, realm, tables, player.server.getTickCount());
                write(player, data, d -> d.withMeditation(d.meditation().stop()));
            }
        }
        POSITIONS.remove(player.getUUID());
    }

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
            write(player, data, d -> d.withMeditation(d.meditation().stop()));
            tell(player, Component.translatable("msg.strife.sit.stop"));
            RealmViewBuilder.pushTo(player);
            return;
        }
        if (meditation.onCooldown(now)) {
            tell(
                    player,
                    Component.translatable(
                            "msg.strife.sit.cooldown",
                            secondsOf(meditation.cooldownRemaining(now))));
            return;
        }
        if (!player.onGround() || player.isInWater() || player.isDeadOrDying()) {
            tell(player, Component.translatable("msg.strife.sit.blocked"));
            return;
        }
        write(player, data, d -> d.withMeditation(meditation.start(now)));
        tell(player, Component.translatable("msg.strife.sit.start"));
        RealmViewBuilder.pushTo(player);
    }

    /** 主动押注突破（03 §4 / NUMBERS §10）。修为满只是前提，不再自动触发——"押注"是 05 §4 的核心体验，也是失败代价 （回退区间）能被玩家读懂的前提。 */
    public static void attemptBreakthrough(ServerPlayer player) {
        RealmTables tables = RealmTables.getOrNull(player.server);
        if (tables == null) {
            return;
        }
        StrifeData data = player.getData(StrifeAttachmentTypes.PLAYER_DATA);
        RealmTables.RealmEntry realm =
                tables.realm(Math.min(data.realmOrdinal(), tables.realmCount() - 1));
        if (realm.ordinal() >= tables.realmCount() - 1) {
            tell(player, Component.translatable("msg.strife.breakthrough.no_next"));
            return;
        }
        if (data.qi() < realm.qiMax()) {
            tell(
                    player,
                    Component.translatable(
                            "msg.strife.breakthrough.not_ready", data.qi(), realm.qiMax()));
            return;
        }
        // 突破即出定：押注时不能还挂在打坐状态上（否则失败回退后立刻又被自动结算，惩罚变得不可读）。
        data = write(player, data, d -> d.withMeditation(d.meditation().stop()));

        RealmTables.BreakthroughRate rate = tables.rate(realm.breakthroughKey());
        double probability =
                BreakthroughMath.successProbability(
                        rate.base(), rate.failStep(), data.breakthroughAttempts(), rate.floor());
        RandomSource random = player.getRandom();
        boolean success = random.nextDouble() < probability;

        if (success) {
            RealmTables.RealmEntry next = tables.realm(realm.ordinal() + 1);
            // 境界、小境界、修为、寿元、失败计数一并替换（withRealm 就是为"这五项必须一起改"存在的）。
            StrifeData advanced =
                    data.withRealm(
                            next.ordinal(), 1, 0, yearsToTicks(player, next.lifespanYears()), 0);
            write(player, data, d -> advanced);
            player.displayClientMessage(
                    Component.translatable(
                                    "msg.strife.breakthrough.success",
                                    Component.translatable("realm.strife." + next.id()),
                                    next.lifespanYears())
                            .withStyle(ChatFormatting.GOLD),
                    false);
            RealmEvents.postBreakthrough(player, realm.breakthroughKey(), true, 0);
        } else {
            double ratio =
                    tables.rules().qiResetRatioMin()
                            + random.nextDouble()
                                    * (tables.rules().qiResetRatioMax()
                                            - tables.rules().qiResetRatioMin());
            int reset = BreakthroughMath.resetQi(realm.qiMax(), ratio);
            StrifeData failed =
                    withQiAndStage(data, reset, realm)
                            .withMeditation(data.meditation())
                            .withAttempts(data.breakthroughAttempts() + 1);
            write(player, data, d -> failed);
            player.displayClientMessage(
                    Component.translatable("msg.strife.breakthrough.failure", reset, realm.qiMax())
                            .withStyle(ChatFormatting.RED),
                    false);
            // 渡劫境（产物 tribulation=true，由 unlocks 含 tribulation 推导）失败额外吃重伤：05 §4 的
            // "渡劫失败 = 突破失败代价 + 重伤虚弱"。数值与键名都来自 NUMBERS @@breakthrough_cost。
            if (tables.realm(realm.ordinal() + 1).tribulation()) {
                RealmEffects.apply(
                        player, RealmEffects.HEAVY_WOUND, tables.rules().debuffDurationTicks());
                tell(player, Component.translatable("msg.strife.tribulation.wounded"));
            }
            RealmEvents.postBreakthrough(
                    player, realm.breakthroughKey(), false, data.breakthroughAttempts() + 1);
        }
        RealmViewBuilder.pushTo(player);
    }

    /** 每秒修为速率：05 §2 四因子公式（基础速率 × 灵根 × 环境 × 功法 × 丹药）。 */
    static double qiPerSecond(
            ServerPlayer player,
            RealmTables tables,
            RealmTables.RealmEntry realm,
            StrifeData data) {
        return realm.sitRate()
                * tables.qualityCoefficient(data.spiritrootQuality())
                * CultivationFactors.coefficients(player).product();
    }

    /** 单刻速率（结算用；换算集中在 {@link StrifeTime}）。 */
    static double qiPerTick(
            ServerPlayer player,
            RealmTables tables,
            RealmTables.RealmEntry realm,
            StrifeData data) {
        return StrifeTime.perSecondToPerTick(qiPerSecond(player, tables, realm, data));
    }

    /** 把会话结算到当前刻：只补发"应得 − 已发"的差额（时间片结算的核心，见 {@link MeditationMath}）。 */
    private static StrifeData settle(
            ServerPlayer player,
            StrifeData data,
            RealmTables.RealmEntry realm,
            RealmTables tables,
            long now) {
        MeditationState meditation = data.meditation();
        double qiPerTick = qiPerTick(player, tables, realm, data);
        int earned = MeditationMath.earnedSince(meditation.startTick(), now, qiPerTick);
        int delta = MeditationMath.settlementDelta(earned, meditation.creditedQi());
        if (delta == 0) {
            return data;
        }
        MeditationMath.Applied applied = MeditationMath.apply(data.qi(), delta, realm.qiMax());
        int credited = meditation.creditedQi() + Math.max(0, applied.applied());
        return write(
                player,
                data,
                d ->
                        withQiAndStage(d, applied.qi(), realm)
                                .withMeditation(meditation.withCreditedQi(credited)));
    }

    /** 打断：本次会话所得按 NUMBERS §5 比例保留（不足则收回已发部分），并写入再入定冷却。 */
    private static StrifeData interrupt(
            ServerPlayer player,
            StrifeData data,
            RealmTables.RealmEntry realm,
            RealmTables tables,
            long now) {
        MeditationState meditation = data.meditation();
        double qiPerTick = qiPerTick(player, tables, realm, data);
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
                withQiAndStage(data, applied.qi(), realm)
                        .withMeditation(meditation.interrupt(now, cooldownTicks));
        write(player, data, d -> updated);
        tell(
                player,
                Component.translatable("msg.strife.sit.interrupted", secondsOf(cooldownTicks))
                        .withStyle(ChatFormatting.GRAY));
        RealmViewBuilder.pushTo(player);
        return updated;
    }

    /** 打断判定：离地/入水/垂死/受伤/位移超阈值，任一成立即打断。 */
    private static boolean interrupted(ServerPlayer player, boolean moved) {
        return moved
                || !player.onGround()
                || player.isInWater()
                || player.isDeadOrDying()
                || player.hurtTime > 0;
    }

    /** 与上一刻的位置比较（首刻没有基准，视为未移动）。 */
    private static boolean movedBeyond(ServerPlayer player, double thresholdSqr) {
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

    /**
     * 大限结算（05 §4 / ADR-008 非破坏性）：境界回退 {@code dasheng_realm_drop_stages} 档 → 修为归零重来 → 残余寿元按 新境界年限 ×
     * {@code dasheng_reset_years_ratio} 重置 → 附加虚弱（{@code dasheng_debuff_key}）。
     *
     * <p><b>绝不删角色、绝不扣到凡人以下</b>（{@link DashengMath} 用用例钉住这条）；已在凡人时退无可退，寿元照旧重置——
     * 这是"续命玩法"存在的意义，而不是把玩家逼进死循环。
     *
     * <p>返回整份新数据（境界变了，不能只改一个字段）。
     */
    private static StrifeData greatLimit(
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
        write(player, data, d -> updated);

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

    /**
     * 修行年 → 游戏刻。换算率取自 NUMBERS §4（经 DataGen 产物 {@code strife_core/rules.json} 的 {@code
     * derived.ticks_per_year}），代码零字面量——这也是 realm 向 core 取数的少数场景之一（realm → core 是允许方向，03 §2）。
     */
    private static long yearsToTicks(ServerPlayer player, long years) {
        return years * StrifeCoreRules.get(player.server).ticksPerYear();
    }

    private static void tell(ServerPlayer player, Component message) {
        player.displayClientMessage(message, true);
    }

    private static String secondsOf(long ticks) {
        return Long.toString(Math.max(0L, ticks) / StrifeTime.TICKS_PER_SECOND);
    }

    /** 只改寿元（core 的 wither 替代手拼 13 个字段——漏一个字段是这类构造最常见的错法）。 */
    private static StrifeData withLifespan(StrifeData data, long lifespan) {
        return data.withLifespanTicks(lifespan);
    }

    /** 改 qi 并同步推导小境界（NUMBERS §1：阈值不入表）。 */
    private static StrifeData withQiAndStage(
            StrifeData data, int qi, RealmTables.RealmEntry realm) {
        return data.withRealm(
                data.realmOrdinal(),
                BreakthroughMath.stageFor(qi, realm.qiMax(), realm.stageCount()),
                qi,
                data.lifespanTicks(),
                data.breakthroughAttempts());
    }

    /** 附件值对象不可变：改字段 = 构造新记录整体 setData 回写（core 契约）；未变则不写。 */
    private static StrifeData write(
            ServerPlayer player, StrifeData current, Function<StrifeData, StrifeData> mutator) {
        StrifeData updated = mutator.apply(current);
        if (updated != current) {
            player.setData(StrifeAttachmentTypes.PLAYER_DATA, updated);
        }
        return updated;
    }
}

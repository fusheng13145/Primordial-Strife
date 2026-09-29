package com.strife.realm;

import com.strife.core.StrifeAttachmentTypes;
import com.strife.core.StrifeData;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

/**
 * 修炼主链的每刻结算（docs/07 §7 A1-2/A1-3/A1-4 的 MVP 快速通道）： 寿元流动 → 打坐积修为（打断惩罚）→ 修为滴满自动尝试突破（MVP 简化，见下）→ 大限结算。
 *
 * <p>速率口径（05 §2 公式，MVP 版）：sit_rate × 灵根品阶系数；环境/功法/丹药三系数本期恒 1.0 （灵气场是 M4、功法装备属筑基解锁、丹药是 M2——中性 1.0
 * 面板可解释，05 §2"任一系数必须 可解释"）。sit_rate 单位按 NUMBERS §1 设计口径（分钟时长反推）为"修为/秒"，与该节注释 "修为/刻"矛盾——按设计口径落地，待
 * A/C 定（已挂账）。
 *
 * <p>MVP 简化清单（正式实现待 A 评审排期）：突破为修为滴满后自动尝试（无主动押注动作）； 大限只做寿元按比例重置（05 §4 的境界回退与虚弱 debuff 待排期）；打坐判定 = 潜行
 * + 站地。
 */
public final class CultivationHandler {

    /** 每玩家瞬态结算状态（pendingQi 浮点累加器；重启重算无损——qi 本体在附件里）。 */
    private record Ticking(double pendingQi, int crouchTicks, boolean wasMeditating) {}

    private static final Map<UUID, Ticking> TICKING = new ConcurrentHashMap<>();

    private CultivationHandler() {}

    static void tick(ServerPlayer player) {
        RealmTables tables = RealmTables.getOrNull(player.server);
        if (tables == null) {
            return;
        }
        StrifeData data = player.getData(StrifeAttachmentTypes.PLAYER_DATA);
        if (data.spiritrootQuality() == 0) {
            data = SpiritRootGenerator.ensureGenerated(player, data, tables);
        }
        RealmTables.RealmEntry realm =
                tables.realm(Math.min(data.realmOrdinal(), tables.realmCount() - 1));
        if (data.lifespanTicks() <= 0) {
            // 新玩家寿元未结算（core 默认 0）：按当前境界初始化（NUMBERS §1 lifespan_years）
            data =
                    write(
                            player,
                            data,
                            d ->
                                    withLifespan(
                                            d, realm.lifespanYears() * StrifeData.TICKS_PER_YEAR));
        }

        // 1. 寿元流动（打坐期间同样流逝，NUMBERS §4）
        long lifespan = data.lifespanTicks() - 1;
        if (lifespan <= 0) {
            data = write(player, data, d -> withLifespan(d, greatLimit(player, realm, tables)));
        } else {
            data = write(player, data, d -> withLifespan(d, lifespan));
        }

        // 2. 打坐（潜行 + 站地 + 非水中）
        boolean meditating = player.isCrouching() && player.onGround() && !player.isInWater();
        UUID id = player.getUUID();
        Ticking ticking = TICKING.computeIfAbsent(id, k -> new Ticking(0.0, 0, false));
        if (meditating) {
            int crouchTicks = ticking.crouchTicks() + 1;
            double pending = ticking.pendingQi();
            if (crouchTicks % tables.rules().meditationTickIntervalTicks() == 0) {
                double gain =
                        realm.sitRate()
                                * (tables.rules().meditationTickIntervalTicks() / 20.0)
                                * tables.qualityCoefficient(data.spiritrootQuality());
                pending += gain;
            }
            if (crouchTicks % tables.rules().meditationTickIntervalTicks() == 0) {
                RealmEvents.postSit(player, tables.rules().meditationTickIntervalTicks());
            }
            int whole = (int) pending;
            if (whole >= 1 && data.qi() < realm.qiMax()) {
                pending -= whole;
                data =
                        write(
                                player,
                                data,
                                d ->
                                        withStage(
                                                d.withQi(Math.min(realm.qiMax(), d.qi() + whole)),
                                                realm));
            }
            TICKING.put(id, new Ticking(pending, crouchTicks, true));
        } else {
            if (ticking.wasMeditating()) {
                int kept = (int) (data.qi() * tables.rules().interruptProgressKeep());
                if (kept != data.qi()) {
                    write(player, data, d -> d.withQi(kept));
                    player.displayClientMessage(
                            Component.literal("出定。气息回落，修为保住大半。").withStyle(ChatFormatting.GRAY),
                            true);
                }
            }
            TICKING.put(id, new Ticking(0.0, 0, false));
        }

        // 3. 修为滴满 → 自动尝试突破（MVP 简化：无主动押注动作）
        data = player.getData(StrifeAttachmentTypes.PLAYER_DATA);
        if (data.qi() >= realm.qiMax() && realm.ordinal() < tables.realmCount() - 1) {
            attemptBreakthrough(player, data, realm, tables);
        }
    }

    private static void attemptBreakthrough(
            ServerPlayer player,
            StrifeData data,
            RealmTables.RealmEntry realm,
            RealmTables tables) {
        RealmTables.BreakthroughRate rate = tables.rate(realm.breakthroughKey());
        double probability =
                BreakthroughMath.successProbability(
                        rate.base(), rate.failStep(), data.breakthroughAttempts(), rate.floor());
        RandomSource random = player.getRandom();
        if (random.nextDouble() < probability) {
            RealmTables.RealmEntry next = tables.realm(realm.ordinal() + 1);
            StrifeData updated =
                    new StrifeData(
                            data.dataVersion(),
                            next.ordinal(),
                            1,
                            0,
                            next.lifespanYears() * StrifeData.TICKS_PER_YEAR,
                            data.flags(),
                            data.spiritrootQuality(),
                            data.spiritrootElements(),
                            0,
                            data.affiliation(),
                            data.reputation());
            player.setData(StrifeAttachmentTypes.PLAYER_DATA, updated);
            player.displayClientMessage(
                    Component.literal("突破成功——你踏入了")
                            .append(Component.translatable("realm.strife." + next.id()))
                            .append(Component.literal("。寿元续至 " + next.lifespanYears() + " 年。"))
                            .withStyle(ChatFormatting.GOLD),
                    false);
            RealmEvents.postBreakthrough(player, realm.breakthroughKey());
        } else {
            double ratio =
                    tables.rules().qiResetRatioMin()
                            + random.nextDouble()
                                    * (tables.rules().qiResetRatioMax()
                                            - tables.rules().qiResetRatioMin());
            int reset = BreakthroughMath.resetQi(realm.qiMax(), ratio);
            StrifeData updated =
                    new StrifeData(
                            data.dataVersion(),
                            data.realmOrdinal(),
                            BreakthroughMath.stageFor(reset, realm.qiMax(), realm.stageCount()),
                            reset,
                            data.lifespanTicks(),
                            data.flags(),
                            data.spiritrootQuality(),
                            data.spiritrootElements(),
                            data.breakthroughAttempts() + 1,
                            data.affiliation(),
                            data.reputation());
            player.setData(StrifeAttachmentTypes.PLAYER_DATA, updated);
            player.displayClientMessage(
                    Component.literal("突破失败——气息紊乱，修为回落至 ")
                            .append(Component.literal(reset + " / " + realm.qiMax() + "。"))
                            .withStyle(ChatFormatting.RED),
                    false);
        }
    }

    /** 大限（05 §4，ADR-008 非破坏性）：MVP 只做寿元按比例重置；境界回退与虚弱 debuff 待 A 排期。 */
    private static long greatLimit(
            ServerPlayer player, RealmTables.RealmEntry realm, RealmTables tables) {
        long years =
                Math.max(
                        1,
                        (long) (realm.lifespanYears() * tables.rules().dashengResetYearsRatio()));
        player.displayClientMessage(
                Component.literal("大限已至。劫数加身，你侥幸续得 " + years + " 年阳寿……")
                        .withStyle(ChatFormatting.DARK_RED),
                false);
        return years * StrifeData.TICKS_PER_YEAR;
    }

    private static StrifeData withLifespan(StrifeData data, long lifespan) {
        return new StrifeData(
                data.dataVersion(),
                data.realmOrdinal(),
                data.stage(),
                data.qi(),
                lifespan,
                data.flags(),
                data.spiritrootQuality(),
                data.spiritrootElements(),
                data.breakthroughAttempts(),
                data.affiliation(),
                data.reputation());
    }

    /** 小境界由 qi 阈值等分推导（NUMBERS §1：阈值不入表）。 */
    private static StrifeData withStage(StrifeData data, RealmTables.RealmEntry realm) {
        return new StrifeData(
                data.dataVersion(),
                data.realmOrdinal(),
                BreakthroughMath.stageFor(data.qi(), realm.qiMax(), realm.stageCount()),
                data.qi(),
                data.lifespanTicks(),
                data.flags(),
                data.spiritrootQuality(),
                data.spiritrootElements(),
                data.breakthroughAttempts(),
                data.affiliation(),
                data.reputation());
    }

    /** 附件值对象不可变：改字段 = 构造新记录整体 setData 回写（core 契约）；未变则不写。 */
    private static StrifeData write(
            ServerPlayer player,
            StrifeData current,
            java.util.function.Function<StrifeData, StrifeData> mutator) {
        StrifeData updated = mutator.apply(current);
        if (updated != current) {
            player.setData(StrifeAttachmentTypes.PLAYER_DATA, updated);
        }
        return updated;
    }
}

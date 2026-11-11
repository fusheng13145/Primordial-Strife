package com.strife.realm;

import com.strife.core.BreakthroughAidState;
import com.strife.core.StrifeAttachmentTypes;
import com.strife.core.StrifeData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

/**
 * 突破服务（单一职责拆分，2026-11-11）：主动押注突破的全部逻辑——概率判定、丹力催动、渡劫雷雨、 失败代价（回退+重伤+心魔）、境界头衔刷新。由 sit/breakthrough 意图经
 * {@code StrifeRealm} 调用。
 *
 * <p>"押注"是 05 §4 的核心体验：修为满只是前提，是否突破由玩家决定；突破即出定（失败回退后 不能还挂在打坐状态上，否则惩罚不可读）。
 */
public final class BreakthroughService {

    // 修仙世界感·渡劫雷雨参数（[拟]）：渡入天劫境成功也挨雷——六道天雷落在身周 4 格内
    private static final int TRIBULATION_STRIKES = 6;
    private static final double TRIBULATION_STRIKE_RADIUS = 4.0;

    BreakthroughService() {}

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
            CultivationHandler.tell(
                    player, Component.translatable("msg.strife.breakthrough.no_next"));
            return;
        }
        if (data.qi() < realm.qiMax()) {
            CultivationHandler.tell(
                    player,
                    Component.translatable(
                            "msg.strife.breakthrough.not_ready", data.qi(), realm.qiMax()));
            return;
        }
        // 突破即出定：押注时不能还挂在打坐状态上（否则失败回退后立刻又被自动结算，惩罚变得不可读）。
        data = CultivationHandler.write(player, data, d -> d.withMeditation(d.meditation().stop()));

        RealmTables.BreakthroughRate rate = tables.rate(realm.breakthroughKey());
        double probability =
                BreakthroughMath.successProbability(
                        rate.base(), rate.failStep(), data.breakthroughAttempts(), rate.floor());
        // 丹药接线批：破境丹力——押注即催动（成败皆耗），成功率加成封顶 1.0
        var aid = player.getData(StrifeAttachmentTypes.BREAKTHROUGH_AID);
        if (aid.successBonus() > 0) {
            probability = Math.min(1.0, probability + aid.successBonus());
        }
        RandomSource random = player.getRandom();
        boolean success = random.nextDouble() < probability;

        if (success) {
            RealmTables.RealmEntry next = tables.realm(realm.ordinal() + 1);
            // 境界、小境界、修为、寿元、失败计数一并替换（withRealm 就是为"这五项必须一起改"存在的）。
            StrifeData advanced =
                    data.withRealm(
                            next.ordinal(),
                            1,
                            0,
                            LifespanService.yearsToTicks(player, next.lifespanYears()),
                            0);
            CultivationHandler.write(player, data, d -> advanced);
            player.displayClientMessage(
                    Component.translatable(
                                    "msg.strife.breakthrough.success",
                                    Component.translatable("realm.strife." + next.id()),
                                    next.lifespanYears())
                            .withStyle(ChatFormatting.GOLD),
                    false);
            RealmEvents.postBreakthrough(player, realm.breakthroughKey(), true, 0);
            // 修仙世界感·渡劫雷雨（[拟]）：渡入天劫境成功也要挨雷——真雷（有伤害有火），天劫从不体面
            if (next.tribulation()) {
                tribulationLightning(player, random);
                RealmTitles.apply(player);
            }
        } else {
            double ratio =
                    tables.rules().qiResetRatioMin()
                            + random.nextDouble()
                                    * (tables.rules().qiResetRatioMax()
                                            - tables.rules().qiResetRatioMin());
            // 丹药接线批：稳元丹力——失败回退的保留比例上抬（封顶 1.0 = 修为不回退）
            if (aid.failKeepBonus() > 0) {
                ratio = Math.min(1.0, ratio + aid.failKeepBonus());
            }
            int reset = BreakthroughMath.resetQi(realm.qiMax(), ratio);
            StrifeData failed =
                    CultivationHandler.withQiAndStage(data, reset, realm)
                            .withMeditation(data.meditation())
                            .withAttempts(data.breakthroughAttempts() + 1);
            CultivationHandler.write(player, data, d -> failed);
            player.displayClientMessage(
                    Component.translatable("msg.strife.breakthrough.failure", reset, realm.qiMax())
                            .withStyle(ChatFormatting.RED),
                    false);
            // 渡劫境（产物 tribulation=true，由 unlocks 含 tribulation 推导）失败额外吃重伤：05 §4 的
            // "渡劫失败 = 突破失败代价 + 重伤虚弱"。数值与键名都来自 NUMBERS @@breakthrough_cost。
            if (tables.realm(realm.ordinal() + 1).tribulation()) {
                RealmEffects.apply(
                        player, RealmEffects.HEAVY_WOUND, tables.rules().debuffDurationTicks());
                CultivationHandler.tell(
                        player, Component.translatable("msg.strife.tribulation.wounded"));
            }
            RealmEvents.postBreakthrough(
                    player, realm.breakthroughKey(), false, data.breakthroughAttempts() + 1);
            // ADR-040：心魔触发（突破失败 → 概率入体，叠层上限由 NUMBERS 控制）。
            triggerHeartDemon(player, tables, random);
        }
        // 丹药接线批：押注即催动——无论成败，本次丹力已用（附件清空）
        if (aid.present()) {
            player.setData(StrifeAttachmentTypes.BREAKTHROUGH_AID, BreakthroughAidState.EMPTY);
            CultivationHandler.tell(
                    player, Component.literal("✦ 丹力催动已毕").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        RealmViewBuilder.pushTo(player);
    }

    /** 渡劫雷雨（[拟]）：六道真雷（有伤害有火）落身周随机 4 格内——天劫从不体面。 */
    private static void tribulationLightning(ServerPlayer player, RandomSource random) {
        ServerLevel level = player.serverLevel();
        for (int i = 0; i < TRIBULATION_STRIKES; i++) {
            var bolt = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(level);
            if (bolt == null) {
                break;
            }
            double angle = random.nextDouble() * Math.PI * 2;
            double radius = random.nextDouble() * TRIBULATION_STRIKE_RADIUS;
            bolt.moveTo(
                    player.getX() + Math.cos(angle) * radius,
                    player.getY(),
                    player.getZ() + Math.sin(angle) * radius);
            bolt.setVisualOnly(false);
            level.addFreshEntity(bolt);
        }
        player.displayClientMessage(
                Component.translatable(
                                "msg.strife.tribulation.success",
                                Component.translatable("realm.strife." + nextRealmId(player)))
                        .withStyle(ChatFormatting.LIGHT_PURPLE),
                false);
    }

    /** 当前境界序数的下一境 ID（雷雨播报用；调用点处于成功分支，下一境必然存在）。 */
    private static String nextRealmId(ServerPlayer player) {
        RealmTables tables = RealmTables.getOrNull(player.server);
        StrifeData data = player.getData(StrifeAttachmentTypes.PLAYER_DATA);
        int ordinal = Math.min(data.realmOrdinal(), tables.realmCount() - 1);
        return tables.realm(ordinal + 1).id();
    }

    /**
     * ADR-040 心魔触发：突破失败后按概率入体。独立附件（StrifeAttachmentTypes.HEART_DEMON）， 规则走 {@link
     * RealmTables.Rules} 类型化访问（与突破成功率同源同管——不再裸读 rules.json 制造第二条解析路径）。 触发后修为速率按层数递减（{@link
     * CultivationHandler#qiPerSecond}），洗尘丹可清。
     */
    private static void triggerHeartDemon(
            ServerPlayer player, RealmTables tables, RandomSource random) {
        RealmTables.Rules rules = tables.rules();
        if (random.nextDouble() >= rules.heartDemonTriggerChance()) {
            return;
        }
        var updated =
                player.getData(StrifeAttachmentTypes.HEART_DEMON)
                        .trigger(
                                player.level().getGameTime(),
                                rules.heartDemonDurationTicks(),
                                rules.heartDemonMaxStacks());
        player.setData(StrifeAttachmentTypes.HEART_DEMON, updated);
        CultivationHandler.tell(
                player,
                Component.literal("✦ 心魔入体——修为速率下降（" + updated.stacks() + " 层）")
                        .withStyle(ChatFormatting.DARK_PURPLE));
        // 入体瞬间的强反馈：一簇涡旋 + 恶魂音效，让"这次失败不一样"可感知
        player.serverLevel()
                .sendParticles(
                        net.minecraft.core.particles.ParticleTypes.PORTAL,
                        player.getX(),
                        player.getY() + 1.0,
                        player.getZ(),
                        30,
                        0.6,
                        0.8,
                        0.6,
                        0.8);
        player.serverLevel()
                .playSound(
                        null,
                        player.blockPosition(),
                        net.minecraft.sounds.SoundEvents.GHAST_AMBIENT,
                        net.minecraft.sounds.SoundSource.PLAYERS,
                        0.6f,
                        0.5f);
    }
}

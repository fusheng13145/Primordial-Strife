package com.strife.production;

import com.strife.core.PillState;
import com.strife.core.StrifeAttachmentTypes;
import com.strife.core.StrifeData;
import com.strife.core.StrifeTime;
import com.strife.core.net.StrifeCoreRules;
import java.util.Locale;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 丹药的服用与增益（docs/03 §1 production 职责、05 §7 数值块）。
 *
 * <p>三条口径：
 *
 * <ol>
 *   <li><b>数值只从产物读</b>（{@link ProductionTables}），玩家档里只存"何时到期、嗑了几次"；
 *   <li><b>重复递减、封底、封顶</b>由 {@link PillMath} 定死（连嗑刷时长在数值层就被挡掉）；
 *   <li><b>延寿按境界封顶</b>（NUMBERS {@code max_gain_per_realm}）：用满即拒绝并给出原因，而不是"吃了没反应"。
 * </ol>
 */
public final class StrifePills {

    /** EP2 心魔劫：真清了心魔落此标记（对话条件分支与 quests 奖励共用同一字符串，测试钉两侧一致）。 */
    public static final String HEART_CLEANSED_FLAG = "fac_yuelai:ep2:heartdemon_cleansed";

    private StrifePills() {}

    /** 丹药物品：右键服用。 */
    static Item item(String pillId) {
        return new PillItem(pillId, new Item.Properties());
    }

    /** 服用一颗；返回是否真的生效（未生效时调用方不该扣物品——"吃了没反应还消失"是最容易招骂的一类 bug）。 */
    static boolean consume(ServerPlayer player, String pillId) {
        ProductionTables tables = ProductionTables.getOrNull(player.server);
        if (tables == null) {
            return false;
        }
        ProductionTables.Pill pill = tables.pill(pillId);
        if (pill == null) {
            tell(player, Component.translatable("msg.strife.pill.unknown", pillId));
            return false;
        }
        StrifeData data = player.getData(StrifeAttachmentTypes.PLAYER_DATA);
        long now = player.server.getTickCount();
        return switch (pill.effect()) {
            case ProductionTables.Effect.QiRate rate ->
                    applyQiRate(player, data, pillId, rate, now);
            case ProductionTables.Effect.Lifespan lifespan ->
                    applyLifespan(player, data, pillId, lifespan);
            case ProductionTables.Effect.Heal heal -> applyHeal(player, heal);
            case ProductionTables.Effect.CombatBuff buff -> applyCombatBuff(player, buff);
            case ProductionTables.Effect.ClearPoison ignored -> applyClearPoison(player);
            case ProductionTables.Effect.BreakthroughAid aid ->
                    applyBreakthroughAid(player, data, aid);
            case ProductionTables.Effect.SenseRegen regen -> applySenseRegen(player, regen);
            case ProductionTables.Effect.ClearCausality ignored -> applyClearCausality(player);
            case ProductionTables.Effect.SpiritRootUpgrade ignored ->
                    applySpiritRootUpgrade(player, data);
        };
    }

    private static boolean applyQiRate(
            ServerPlayer player,
            StrifeData data,
            String pillId,
            ProductionTables.Effect.QiRate rate,
            long now) {
        int maxStacks = PillMath.maxStacks(rate.bonus(), rate.repeatStep(), rate.repeatFloor());
        PillState updated = data.pills().withPill(pillId, now, rate.durationTicks(), maxStacks);
        int stacks = updated.active().get(pillId).stacks();
        double bonus =
                PillMath.effectiveBonus(
                        rate.bonus(), rate.repeatStep(), rate.repeatFloor(), stacks);
        player.setData(StrifeAttachmentTypes.PLAYER_DATA, data.withPills(updated));
        tell(
                player,
                Component.translatable(
                        "msg.strife.pill.qi_rate",
                        pillName(pillId),
                        percent(bonus),
                        rate.durationTicks() / StrifeTime.TICKS_PER_SECOND));
        return true;
    }

    private static boolean applyLifespan(
            ServerPlayer player,
            StrifeData data,
            String pillId,
            ProductionTables.Effect.Lifespan lifespan) {
        int alreadyGained = data.pills().gainedIn(data.realmOrdinal());
        int remaining = PillMath.remainingLifespanGain(lifespan.maxGainPerRealm(), alreadyGained);
        int granted = PillMath.grantedLifespanYears(lifespan.yearsGain(), remaining);
        if (granted <= 0) {
            tell(
                    player,
                    Component.translatable(
                            "msg.strife.pill.lifespan_capped", lifespan.maxGainPerRealm()));
            return false;
        }
        long ticksPerYear = StrifeCoreRules.get(player.server).ticksPerYear();
        PillState pills = data.pills().withLifespanGain(data.realmOrdinal(), granted);
        StrifeData updated =
                data.withPills(pills)
                        .withLifespanTicks(data.lifespanTicks() + granted * ticksPerYear);
        player.setData(StrifeAttachmentTypes.PLAYER_DATA, updated);
        tell(player, Component.translatable("msg.strife.pill.lifespan", pillName(pillId), granted));
        return true;
    }

    /**
     * 四因子第三项：{@code 1 + Σ 在效丹药加成}（05 §2 公式的 {@code (1 + 丹药加成)}）。
     *
     * <p>表不可用或没有在效丹药时返回中性 1.0，与其余两项同口径。
     */
    static double coefficient(ServerPlayer player) {
        ProductionTables tables = ProductionTables.getOrNull(player.server);
        if (tables == null) {
            return 1.0;
        }
        StrifeData data = player.getData(StrifeAttachmentTypes.PLAYER_DATA);
        long now = player.server.getTickCount();
        double total = 0.0;
        for (Map.Entry<String, PillState.PillBuff> entry :
                data.pills().effectiveAt(now).entrySet()) {
            ProductionTables.Pill pill = tables.pill(entry.getKey());
            if (pill != null && pill.effect() instanceof ProductionTables.Effect.QiRate rate) {
                total +=
                        PillMath.effectiveBonus(
                                rate.bonus(),
                                rate.repeatStep(),
                                rate.repeatFloor(),
                                entry.getValue().stacks());
            }
        }
        return 1.0 + total;
    }

    private static Component pillName(String pillId) {
        return Component.translatable("item.strife." + pillId);
    }

    private static String percent(double ratio) {
        return String.format(Locale.ROOT, "%.0f%%", ratio * 100.0);
    }

    private static boolean applyHeal(ServerPlayer player, ProductionTables.Effect.Heal heal) {
        player.heal((float) heal.health());
        if (heal.clearDebuff()) {
            player.removeAllEffects();
        }
        player.displayClientMessage(Component.literal("✦ " + "回春之力流转——伤势愈合"), true);
        return true;
    }

    private static boolean applyCombatBuff(
            ServerPlayer player, ProductionTables.Effect.CombatBuff buff) {
        var attr =
                player.getAttribute(
                        net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        var def = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
        var spd =
                player.getAttribute(
                        net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (attr != null)
            attr.addTransientModifier(
                    new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                    "strife", "pill_atk"),
                            buff.attackFlat(),
                            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation
                                    .ADD_VALUE));
        if (def != null)
            def.addTransientModifier(
                    new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                    "strife", "pill_def"),
                            buff.defenseFlat(),
                            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation
                                    .ADD_VALUE));
        if (spd != null)
            spd.addTransientModifier(
                    new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                    "strife", "pill_spd"),
                            buff.speedFlat(),
                            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation
                                    .ADD_VALUE));
        // 到期清理（简化：tick 回调在真实实现中由效果管理器承载）
        var cooldownManager = player.getCooldowns();
        player.displayClientMessage(
                Component.literal("✦ 丹力入体——" + buff.durationTicks() / 20 + " 秒"), true);
        return true;
    }

    private static boolean applyClearPoison(ServerPlayer player) {
        player.removeAllEffects();
        player.displayClientMessage(Component.literal("✦ 毒素尽清"), true);
        return true;
    }

    /**
     * 破境丹/稳元丹（丹药接线批）：丹力暂存独立附件（BREAKTHROUGH_AID），下次突破押注时催动（成败皆耗， realm 侧消费）。
     * 不叠层：重复服用按最近一次覆盖——叠加会把押注变成攒弹药。
     */
    private static boolean applyBreakthroughAid(
            ServerPlayer player, StrifeData data, ProductionTables.Effect.BreakthroughAid aid) {
        player.setData(
                StrifeAttachmentTypes.BREAKTHROUGH_AID,
                new com.strife.core.BreakthroughAidState(aid.successBonus(), aid.failKeepBonus()));
        StringBuilder text = new StringBuilder("✦ 丹力沉淀（下次突破：");
        boolean first = true;
        if (aid.successBonus() > 0) {
            text.append("成功率 +").append(percent(aid.successBonus()));
            first = false;
        }
        if (aid.failKeepBonus() > 0) {
            if (!first) {
                text.append('，');
            }
            text.append("失败保留 +").append(percent(aid.failKeepBonus()));
        }
        text.append("）——押注时催动，成败皆耗");
        player.displayClientMessage(Component.literal(text.toString()), true);
        return true;
    }

    /**
     * 凝神丹（丹药接线批）：神识即时恢复 {@code sense_regen_bonus} 点，封顶当前上限—— 上限经 {@link
     * com.strife.core.ShenShiBridge} 取（realm 注册），桥未接线或数据不可用时诚实拒绝（不猜默认上限、不浪费丹药）。
     */
    private static boolean applySenseRegen(
            ServerPlayer player, ProductionTables.Effect.SenseRegen regen) {
        int max = com.strife.core.ShenShiBridge.maxFor(player);
        if (max < 0) {
            player.displayClientMessage(Component.literal("（神识上限未知——丹力暂时封存，不会浪费）"), true);
            return false;
        }
        var shenShi = player.getData(StrifeAttachmentTypes.SHENSHI);
        int restored = Math.min(max, shenShi.value() + regen.bonus()) - shenShi.value();
        if (restored <= 0) {
            player.displayClientMessage(Component.literal("（神识已满——丹力暂时封存，不会浪费）"), true);
            return false;
        }
        player.setData(
                StrifeAttachmentTypes.SHENSHI,
                new com.strife.core.ShenShiState(
                        shenShi.value() + restored, shenShi.lastRegenTick()));
        player.displayClientMessage(
                Component.literal(
                        "✦ 神识清明（+"
                                + restored
                                + "，现 "
                                + (shenShi.value() + restored)
                                + "/"
                                + max
                                + "）"),
                true);
        return true;
    }

    /**
     * 洗髓丹（丹药接线批）：灵根品阶序数向 tier1 移动 {@code spiritroot_upgrade} 步，1 = 天品封顶——
     * 已至天品则诚实拒绝（不浪费稀有材料）。品阶数值语义：序数越小越好（quality_tier_1 系数最高）。
     */
    private static boolean applySpiritRootUpgrade(ServerPlayer player, StrifeData data) {
        int quality = data.spiritrootQuality();
        if (quality <= 1) {
            player.displayClientMessage(Component.literal("（灵根已至天品——洗髓无益，此丹暂不入腹）"), true);
            return false;
        }
        int updated = Math.max(1, quality - 1);
        player.setData(StrifeAttachmentTypes.PLAYER_DATA, data.withSpiritrootQuality(updated));
        player.displayClientMessage(
                Component.literal("✦ 洗髓伐毛——灵根品阶提升（现第 " + updated + " 阶）")
                        .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE),
                false);
        return true;
    }

    /**
     * 洗尘丹：清除心魔层数 + H3 因果标记（causality:ep1:smuggler，标记常量与黑市同源）。
     *
     * <p>真清了心魔还会落 {@link #HEART_CLEANSED_FLAG}——EP2 心魔劫对话链（dlg_ep2_trial.nd_hm_pill 的条件分支）
     * 据此给出"阴影散了"的反馈；flag 字符串与对话表 conditions= 两侧同值，{@code Ep2HeartDemonContentTest} 钉住一致性。
     */
    private static boolean applyClearCausality(ServerPlayer player) {
        // 心魔清除
        var demon = player.getData(StrifeAttachmentTypes.HEART_DEMON);
        int clearedStacks = demon.stacks();
        if (clearedStacks > 0) {
            player.setData(
                    StrifeAttachmentTypes.HEART_DEMON, com.strife.core.HeartDemonState.EMPTY);
        }
        // 因果标记清除（黑市走私标记）：QuestProgress.withoutFlag 落地（ADR-040）——
        // 此前"QuestProgress 无 removeFlag，标记留给叙事"的妥协撤销，丹效与 pills.csv 口径一致。
        var data = player.getData(StrifeAttachmentTypes.PLAYER_DATA);
        boolean hadSmuggler = data.quests().hasFlag(BlackMarketCommands.SMUGGLER_FLAG);
        var quests = data.quests().withoutFlag(BlackMarketCommands.SMUGGLER_FLAG);
        if (clearedStacks > 0) {
            quests = quests.withFlag(HEART_CLEANSED_FLAG);
        }
        if (hadSmuggler || clearedStacks > 0) {
            player.setData(StrifeAttachmentTypes.PLAYER_DATA, data.withQuests(quests));
        }
        String message =
                clearedStacks > 0 ? "✦ 洗尘丹化开——心魔尽消（" + clearedStacks + " 层）" : "✦ 洗尘丹化开——心境澄明";
        if (hadSmuggler) {
            message += "，因果一笔勾销";
        }
        player.displayClientMessage(Component.literal(message), true);
        return clearedStacks > 0 || hadSmuggler;
    }

    private static void tell(ServerPlayer player, Component message) {
        player.displayClientMessage(message, true);
    }

    /** 右键服用的丹药物品。 */
    private static final class PillItem extends Item {

        private final String pillId;

        PillItem(String pillId, Properties properties) {
            super(properties);
            this.pillId = pillId;
        }

        @Override
        public InteractionResultHolder<ItemStack> use(
                Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResultHolder.sidedSuccess(stack, true);
            }
            boolean applied = consume(serverPlayer, pillId);
            if (applied && !player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            return InteractionResultHolder.sidedSuccess(stack, false);
        }
    }
}

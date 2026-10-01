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

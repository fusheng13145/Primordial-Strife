package com.strife.production;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.strife.core.InventoryOps;
import java.util.Map;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 坊市命令通道（EP1 v1，ADR-032）：一口价买卖 + 灵石三档兑换。D-P0-2 纪律——命令永久保留， 摊位 UI 后置时这套语义原样复用。
 *
 * <p><b>防复制</b>：价格只来自 {@link MarketPrices}（生成器门禁 sell&lt;buy）；买入/回收全部 服务端权威（{@link InventoryOps}
 * 计数与扣除 → 原版背包入包），客户端上报的任何数量字段不参与结算； 未上架物品拒绝；灵石余量不足整单拒绝（不扣一半）。
 */
public final class MarketCommands {

    private MarketCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> subtree() {
        return Commands.literal("market")
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(
                        Commands.literal("buy")
                                .then(
                                        Commands.argument(
                                                        "item_id",
                                                        com.mojang.brigadier.arguments
                                                                .StringArgumentType.string())
                                                .executes(
                                                        ctx ->
                                                                buy(
                                                                        ctx.getSource(),
                                                                        com.mojang.brigadier
                                                                                .arguments
                                                                                .StringArgumentType
                                                                                .getString(
                                                                                        ctx,
                                                                                        "item_id"),
                                                                        1)))
                                .then(
                                        Commands.argument(
                                                        "item_id",
                                                        com.mojang.brigadier.arguments
                                                                .StringArgumentType.string())
                                                .then(
                                                        Commands.argument(
                                                                        "count",
                                                                        com.mojang.brigadier
                                                                                .arguments
                                                                                .IntegerArgumentType
                                                                                .integer(1))
                                                                .executes(
                                                                        ctx ->
                                                                                buy(
                                                                                        ctx
                                                                                                .getSource(),
                                                                                        com.mojang
                                                                                                .brigadier
                                                                                                .arguments
                                                                                                .StringArgumentType
                                                                                                .getString(
                                                                                                        ctx,
                                                                                                        "item_id"),
                                                                                        com.mojang
                                                                                                .brigadier
                                                                                                .arguments
                                                                                                .IntegerArgumentType
                                                                                                .getInteger(
                                                                                                        ctx,
                                                                                                        "count"))))))
                .then(
                        Commands.literal("sell")
                                .then(
                                        Commands.argument(
                                                        "item_id",
                                                        com.mojang.brigadier.arguments
                                                                .StringArgumentType.string())
                                                .executes(
                                                        ctx ->
                                                                sell(
                                                                        ctx.getSource(),
                                                                        com.mojang.brigadier
                                                                                .arguments
                                                                                .StringArgumentType
                                                                                .getString(
                                                                                        ctx,
                                                                                        "item_id"),
                                                                        1)))
                                .then(
                                        Commands.argument(
                                                        "item_id",
                                                        com.mojang.brigadier.arguments
                                                                .StringArgumentType.string())
                                                .then(
                                                        Commands.argument(
                                                                        "count",
                                                                        com.mojang.brigadier
                                                                                .arguments
                                                                                .IntegerArgumentType
                                                                                .integer(1))
                                                                .executes(
                                                                        ctx ->
                                                                                sell(
                                                                                        ctx
                                                                                                .getSource(),
                                                                                        com.mojang
                                                                                                .brigadier
                                                                                                .arguments
                                                                                                .StringArgumentType
                                                                                                .getString(
                                                                                                        ctx,
                                                                                                        "item_id"),
                                                                                        com.mojang
                                                                                                .brigadier
                                                                                                .arguments
                                                                                                .IntegerArgumentType
                                                                                                .getInteger(
                                                                                                        ctx,
                                                                                                        "count"))))))
                .then(Commands.literal("appraise").executes(ctx -> appraise(ctx.getSource())))
                .then(
                        Commands.literal("exchange")
                                .then(
                                        Commands.literal("up")
                                                .executes(ctx -> exchangeUp(ctx.getSource())))
                                .then(
                                        Commands.literal("down")
                                                .executes(ctx -> exchangeDown(ctx.getSource()))));
    }

    private static int list(CommandSourceStack source) {
        StringBuilder sb = new StringBuilder("坊市价目（下品灵石 买/卖）：");
        for (Map.Entry<String, MarketPrices.Quote> e : MarketPrices.snapshot().entrySet()) {
            sb.append('\n')
                    .append(e.getKey())
                    .append(' ')
                    .append(e.getValue().buyLingshi())
                    .append('/')
                    .append(e.getValue().sellLingshi());
        }
        source.sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    /** 命令包装：控制台拒绝后走玩家直参内核（内核与看板意图共用）。 */
    private static int buy(CommandSourceStack source, String itemId, int count) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            source.sendFailure(Component.literal("market 需要玩家上下文（不能由控制台执行）"));
            return 0;
        }
        return buyFor(player, itemId, count) ? 1 : 0;
    }

    /** 购入内核（玩家直参；看板意图与 /strife market buy 共用）。返回是否成交（false = 未扣任何资产）。 */
    static boolean buyFor(ServerPlayer player, String itemId, int count) {
        Optional<MarketPrices.Quote> quote = MarketPrices.quote(itemId);
        if (quote.isEmpty()) {
            player.displayClientMessage(Component.literal("（坊市未上架：" + itemId + "）"), true);
            return false;
        }
        int cost = quote.get().buyLingshi() * count;
        InventoryOps.PlayerContainers containers =
                new InventoryOps.PlayerContainers(
                        player.getInventory(), player.getEnderChestInventory());
        if (InventoryOps.countOwned(containers, "item_lingshi") < cost) {
            player.displayClientMessage(
                    Component.literal(
                            "（灵石不足：需 "
                                    + cost
                                    + "，现有 "
                                    + InventoryOps.countOwned(containers, "item_lingshi")
                                    + "）"),
                    true);
            return false;
        }
        long removed = InventoryOps.remove(containers, "item_lingshi", cost);
        if (removed < cost) {
            player.displayClientMessage(
                    Component.literal("（结算异常：实扣 " + removed + "/" + cost + "，已回滚数量校验）"), true);
            return false;
        }
        Item item = InventoryOps.resolve(itemId).orElse(null);
        if (item == null) {
            // 价格表有、注册表无：内容热更半途，按未上架处理（钱已扣则补偿——不吞玩家资产）。
            player.getInventory().add(new ItemStack(StrifeItems.LINGSHI.get(), (int) removed));
            player.displayClientMessage(Component.literal("（该物品暂未注册，已退回灵石）"), true);
            return false;
        }
        player.getInventory().add(new ItemStack(item, count));
        player.displayClientMessage(
                Component.literal("✦ 购入 " + itemId + " ×" + count + "（付灵石 " + cost + "）"), true);
        return true;
    }

    /** 命令包装：控制台拒绝后走玩家直参内核（内核与看板意图共用）。 */
    private static int sell(CommandSourceStack source, String itemId, int count) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            source.sendFailure(Component.literal("market 需要玩家上下文（不能由控制台执行）"));
            return 0;
        }
        return sellFor(player, itemId, count) ? 1 : 0;
    }

    /** 回收内核（玩家直参；看板意图与 /strife market sell 共用）。返回是否成交。 */
    static boolean sellFor(ServerPlayer player, String itemId, int count) {
        Optional<MarketPrices.Quote> quote = MarketPrices.quote(itemId);
        if (quote.isEmpty()) {
            player.displayClientMessage(Component.literal("（坊市未回收：" + itemId + "）"), true);
            return false;
        }
        InventoryOps.PlayerContainers containers =
                new InventoryOps.PlayerContainers(
                        player.getInventory(), player.getEnderChestInventory());
        long owned = InventoryOps.countOwned(containers, itemId);
        if (owned < count) {
            player.displayClientMessage(
                    Component.literal("（数量不足：现有 " + owned + "，需 " + count + "）"), true);
            return false;
        }
        long removed = InventoryOps.remove(containers, itemId, count);
        int payout = quote.get().sellLingshi() * (int) removed;
        player.getInventory().add(new ItemStack(StrifeItems.LINGSHI.get(), payout));
        player.displayClientMessage(
                Component.literal("✦ 回收 " + itemId + " ×" + removed + "（得灵石 " + payout + "）"),
                true);
        return true;
    }

    /** 鉴定阁（ADR-037 ③）：清点主背包+末影箱的未鉴定器胚，按件收费去除标记。 无未鉴定品=无事发生不扣费。 */
    private static int appraise(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        int cost = MarketPrices.appraiseCost();
        java.util.List<ItemStack> pending = new java.util.ArrayList<>();
        collectUnidentified(player.getInventory(), pending);
        collectUnidentified(player.getEnderChestInventory(), pending);
        if (pending.isEmpty()) {
            player.displayClientMessage(Component.literal("（没有需要鉴定的货）"), true);
            return 0;
        }
        int total = cost * pending.size();
        InventoryOps.PlayerContainers containers =
                new InventoryOps.PlayerContainers(
                        player.getInventory(), player.getEnderChestInventory());
        if (InventoryOps.countOwned(containers, "item_lingshi") < total) {
            player.displayClientMessage(
                    Component.literal(
                            "（鉴定费不足：需 " + total + "（" + pending.size() + " 件 × " + cost + "））"),
                    true);
            return 0;
        }
        InventoryOps.remove(containers, "item_lingshi", total);
        for (ItemStack stack : pending) {
            com.strife.core.ArtifactTags.markIdentified(stack);
        }
        player.displayClientMessage(
                Component.literal("✦ 鉴定阁：" + pending.size() + " 件货过了眼（灵石 " + total + "）——是真金不怕火"),
                true);
        return 1;
    }

    private static void collectUnidentified(
            net.minecraft.world.Container container, java.util.List<ItemStack> pending) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty() && com.strife.core.ArtifactTags.isUnidentified(stack)) {
                pending.add(stack);
            }
        }
    }

    /** 下品 → 高档：全量换算（余数留下品），不丢钱。 */
    private static int exchangeUp(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            source.sendFailure(Component.literal("market 需要玩家上下文（不能由控制台执行）"));
            return 0;
        }
        MarketPrices.Ratios ratios = MarketPrices.ratios();
        InventoryOps.PlayerContainers containers =
                new InventoryOps.PlayerContainers(
                        player.getInventory(), player.getEnderChestInventory());
        long owned = InventoryOps.countOwned(containers, "item_lingshi");
        int[] want = MarketPrices.breakdown((int) Math.min(owned, Integer.MAX_VALUE), ratios);
        if (want[0] == 0 && want[1] == 0) {
            player.displayClientMessage(
                    Component.literal(
                            "（不足兑换：中品需 " + ratios.tierZhong() + "，上品需 " + ratios.tierShang() + "）"),
                    true);
            return 0;
        }
        int consumed = want[0] * ratios.tierShang() + want[1] * ratios.tierZhong();
        long removed = InventoryOps.remove(containers, "item_lingshi", consumed);
        if (removed < consumed) {
            player.getInventory().add(new ItemStack(StrifeItems.LINGSHI.get(), (int) removed));
            player.displayClientMessage(Component.literal("（结算异常已回滚）"), true);
            return 0;
        }
        if (want[0] > 0) {
            player.getInventory().add(new ItemStack(StrifeItems.LINGSHI_SHANG.get(), want[0]));
        }
        if (want[1] > 0) {
            player.getInventory().add(new ItemStack(StrifeItems.LINGSHI_ZHONG.get(), want[1]));
        }
        player.displayClientMessage(
                Component.literal(
                        "✦ 兑换：上品 ×" + want[0] + "，中品 ×" + want[1] + "（余下品 " + want[2] + "）"),
                true);
        return 1;
    }

    /** 高档 → 下品（全部折回下品，回收随身高档）。 */
    private static int exchangeDown(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            source.sendFailure(Component.literal("market 需要玩家上下文（不能由控制台执行）"));
            return 0;
        }
        MarketPrices.Ratios ratios = MarketPrices.ratios();
        InventoryOps.PlayerContainers containers =
                new InventoryOps.PlayerContainers(
                        player.getInventory(), player.getEnderChestInventory());
        long zhong = InventoryOps.countOwned(containers, "item_lingshi_zhong");
        long shang = InventoryOps.countOwned(containers, "item_lingshi_shang");
        if (zhong == 0 && shang == 0) {
            player.displayClientMessage(Component.literal("（没有可折算的中/上品灵石）"), true);
            return 0;
        }
        InventoryOps.remove(containers, "item_lingshi_zhong", zhong);
        InventoryOps.remove(containers, "item_lingshi_shang", shang);
        int payout = (int) (zhong * ratios.tierZhong() + shang * ratios.tierShang());
        player.getInventory().add(new ItemStack(StrifeItems.LINGSHI.get(), payout));
        player.displayClientMessage(Component.literal("✦ 折算：得下品灵石 ×" + payout), true);
        return 1;
    }
}

package com.strife.production;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.strife.core.InventoryOps;
import com.strife.core.StrifeAttachmentTypes;
import com.strife.core.StrifeData;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 拍卖行命令（EP1，ADR-037 ①）：荷兰式降价拍——`/strife auction list|buy <拍品>`。 当前价由游戏时间确定性推导（{@link
 * AuctionHouse}），落槌即按当下价成交； 等得越久越便宜（触底即底价），多人同价各得各的（急售语义）。
 */
public final class AuctionCommands {

    private AuctionCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> subtree() {
        return Commands.literal("auction")
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(
                        Commands.literal("buy")
                                .then(
                                        Commands.argument("拍品", StringArgumentType.string())
                                                .executes(
                                                        ctx ->
                                                                buy(
                                                                        ctx.getSource(),
                                                                        StringArgumentType
                                                                                .getString(
                                                                                        ctx,
                                                                                        "拍品")))));
    }

    private static int list(CommandSourceStack source) {
        long gameTime = source.getLevel().getGameTime();
        StringBuilder sb = new StringBuilder("拍场（荷兰拍，每槽 2 时辰轮换）：");
        for (AuctionHouse.Lot lot : AuctionHouse.lots().values()) {
            boolean current = lot.equals(AuctionHouse.currentLot(gameTime));
            int price =
                    AuctionHouse.currentPrice(
                            lot, AuctionHouse.elapsedInSlot(gameTime), AuctionHouse.slotTicks());
            sb.append('\n')
                    .append(current ? "▶ " : "  ")
                    .append(lot.itemId())
                    .append(" 现价 ")
                    .append(price)
                    .append("（起 ")
                    .append(lot.start())
                    .append(" 底 ")
                    .append(lot.min())
                    .append("，每时辰 −")
                    .append(lot.drop())
                    .append('）');
        }
        long elapsed = AuctionHouse.elapsedInSlot(gameTime);
        sb.append('\n')
                .append("本槽剩余 ")
                .append(AuctionHouse.remainingShichen(elapsed))
                .append(" 时辰");
        source.sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int buy(CommandSourceStack source, String lotId) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        long gameTime = player.level().getGameTime();
        AuctionHouse.Lot lot = AuctionHouse.lots().get(lotId);
        if (lot == null) {
            source.sendFailure(Component.literal("（拍品不存在：" + lotId + "）"));
            return 0;
        }
        if (!lot.equals(AuctionHouse.currentLot(gameTime))) {
            source.sendFailure(
                    Component.literal("（" + lotId + " 不在本槽——等轮换，/strife auction list 看当前）"));
            return 0;
        }
        int price =
                AuctionHouse.currentPrice(
                        lot, AuctionHouse.elapsedInSlot(gameTime), AuctionHouse.slotTicks());
        InventoryOps.PlayerContainers containers =
                new InventoryOps.PlayerContainers(
                        player.getInventory(), player.getEnderChestInventory());
        if (InventoryOps.countOwned(containers, "item_lingshi") < price) {
            player.displayClientMessage(Component.literal("（灵石不足：需 " + price + "——等降价也是策略）"), true);
            return 0;
        }
        long removed = InventoryOps.remove(containers, "item_lingshi", price);
        if (removed < price) {
            player.displayClientMessage(Component.literal("（结算异常已回滚）"), true);
            return 0;
        }
        Item item = InventoryOps.resolve(lot.itemId()).orElse(null);
        if (item == null) {
            player.getInventory().add(new ItemStack(StrifeItems.LINGSHI.get(), (int) removed));
            player.displayClientMessage(Component.literal("（拍品暂未到岸，已退灵石）"), true);
            return 0;
        }
        player.getInventory().add(new ItemStack(item, 1));
        StrifeData data = player.getData(StrifeAttachmentTypes.PLAYER_DATA);
        Map<String, Integer> reputation = new HashMap<>(data.reputation());
        reputation.merge("fac_yuelai", 2, Integer::sum);
        player.setData(
                StrifeAttachmentTypes.PLAYER_DATA, data.withReputation(Map.copyOf(reputation)));
        player.displayClientMessage(
                Component.literal("✦ 落槌！" + lot.itemId() + " 以 " + price + " 灵石归你"), true);
        return 1;
    }
}

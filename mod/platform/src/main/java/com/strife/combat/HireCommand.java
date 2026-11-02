package com.strife.combat;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.strife.core.InventoryOps;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** 雇佣供奉（EP1，ADR-037 ②）：`/strife hire <分钟>`——灵石雇佣供奉武士。 */
public final class HireCommand {

    private static final int COST_PER_MINUTE = 2;

    private HireCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> subtree() {
        return Commands.literal("hire")
                .then(
                        Commands.argument("分钟", IntegerArgumentType.integer(1, 30))
                                .executes(
                                        ctx ->
                                                hire(
                                                        ctx.getSource(),
                                                        IntegerArgumentType.getInteger(
                                                                ctx, "分钟"))));
    }

    private static int hire(CommandSourceStack source, int minutes)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        int cost = COST_PER_MINUTE * minutes;
        var containers =
                new InventoryOps.PlayerContainers(
                        player.getInventory(), player.getEnderChestInventory());
        if (InventoryOps.countOwned(containers, "item_lingshi") < cost) {
            player.displayClientMessage(Component.literal("（灵石不足：需 " + cost + "）"), true);
            return 0;
        }
        var nearby =
                player.level()
                        .getEntitiesOfClass(
                                StrifeMercenary.class,
                                player.getBoundingBox().inflate(64),
                                m -> player.getUUID().equals(m.ownerUuid()));
        if (!nearby.isEmpty()) {
            player.displayClientMessage(Component.literal("（已有供奉在身）"), true);
            return 0;
        }
        InventoryOps.remove(containers, "item_lingshi", cost);
        var lingshiItem = InventoryOps.resolve("item_lingshi").orElse(null);

        var merc = StrifeCombatEntities.MERCENARY.get().create(player.level());
        if (merc == null) {
            if (lingshiItem != null) player.getInventory().add(new ItemStack(lingshiItem, cost));
            return 0;
        }
        merc.moveTo(
                player.getX() + 1,
                player.getY(),
                player.getZ() + 1,
                player.getYRot(),
                player.getXRot());
        merc.hire(player, minutes * 60 * 20);
        player.level().addFreshEntity(merc);
        player.displayClientMessage(Component.literal("✦ 供奉武士已雇（" + minutes + " 分钟）"), true);
        return 1;
    }
}

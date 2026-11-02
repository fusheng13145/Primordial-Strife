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

    /** 坊市雇佣圈半径（[拟] ADR-036 ②"坊市按次雇佣"的门禁落地，随实机校准）。 */
    static final int HIRE_PLACE_RADIUS = 48;

    private static final String FANGSHI_PLACE = "qi_fangshi";

    /** 坊市门禁（纯函数，包内可见供用例）：同维度 + 与坊市锚点水平距离在圈内。 ADR-036 ② 的"坊市按次雇佣"此前只有语义没有落点——雇供奉在哪都行，门禁是空的。 */
    static boolean inHirePlace(
            net.minecraft.resources.ResourceLocation playerDimension,
            double playerX,
            double playerZ,
            com.strife.core.PlaceBridges.PlaceCoords place,
            int radius) {
        if (place == null || !place.dimension().equals(playerDimension)) {
            return false;
        }
        double dx = playerX - (place.x() + 0.5);
        double dz = playerZ - (place.z() + 0.5);
        return dx * dx + dz * dz <= (double) radius * radius;
    }

    private static int hire(CommandSourceStack source, int minutes)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var place = com.strife.core.PlaceBridges.findPlace(player.server, FANGSHI_PLACE);
        if (!inHirePlace(
                player.level().dimension().location(),
                player.getX(),
                player.getZ(),
                place.orElse(null),
                HIRE_PLACE_RADIUS)) {
            player.displayClientMessage(Component.literal("（雇供奉须在文津坊市——找牙人张作保才作数）"), true);
            return 0;
        }
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

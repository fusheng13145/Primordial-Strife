package com.strife.realm;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.strife.core.ShenShiState;
import com.strife.core.StrifeAttachmentTypes;
import com.strife.core.StrifeData;
import java.util.Comparator;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;

/**
 * 神识探查命令（EP1，ADR-036 ⑦；**禁区披露批 #3**——realm 侧只读探查 + 附件读写，按 ADR-031 ① 单独批次+显著披露）。
 *
 * <p>`/strife sense`——消耗 NUMBERS {@code @@shenshi.sense_cost} 点神识， 列出 {@code sense_radius}
 * 格内全部生物（类型/距离/方位）。探查是神识的 v1 唯一消费面； UI 级（发光高亮/雷达）后置。
 */
public final class ShenShiCommand {

    private ShenShiCommand() {}

    /** 神识规则（NUMBERS @@shenshi → rules.json 的 shenshi 块）。 */
    record ShenShiRules(
            int maxBase, int maxPerOrdinal, int regenPerMinute, int senseCost, int senseRadius) {}

    static ShenShiRules shenShiRules(MinecraftServer server) {
        try {
            var resource =
                    server.getResourceManager()
                            .getResource(
                                    ResourceLocation.fromNamespaceAndPath(
                                            "strife", "strife_realms/rules.json"))
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "strife_realms/rules.json missing"));
            com.google.gson.JsonObject rules;
            try (var reader =
                    new java.io.InputStreamReader(
                            resource.open(), java.nio.charset.StandardCharsets.UTF_8)) {
                rules = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
            }
            com.google.gson.JsonObject block = rules.getAsJsonObject("shenshi");
            if (block == null) {
                throw new IllegalStateException("strife_realms/rules.json 缺 @@shenshi 块");
            }
            return new ShenShiRules(
                    block.get("max_base").getAsInt(),
                    block.get("max_per_ordinal").getAsInt(),
                    block.get("regen_per_minute").getAsInt(),
                    block.get("sense_cost").getAsInt(),
                    block.get("sense_radius").getAsInt());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("cannot read strife_realms/rules.json", e);
        }
    }

    /** 神识上限派生：base + ordinal × per_ordinal。纯函数。 */
    static int maxFor(int realmOrdinal, ShenShiRules rules) {
        return rules.maxBase() + rules.maxPerOrdinal() * realmOrdinal;
    }

    public static LiteralArgumentBuilder<CommandSourceStack> subtree() {
        return Commands.literal("sense").executes(ctx -> sense(ctx));
    }

    private static int sense(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ShenShiRules rules = shenShiRules(player.getServer());
        StrifeData data = player.getData(StrifeAttachmentTypes.PLAYER_DATA);
        ShenShiState shenShi =
                player.getData(StrifeAttachmentTypes.SHENSHI)
                        .regen(
                                player.level().getGameTime(),
                                maxFor(data.realmOrdinal(), rules),
                                rules.regenPerMinute());

        ShenShiState after = shenShi.consume(rules.senseCost());
        if (after == null) {
            context.getSource()
                    .sendFailure(
                            Component.literal(
                                    "（神识不足：需 "
                                            + rules.senseCost()
                                            + "，现有 "
                                            + shenShi.value()
                                            + "/"
                                            + maxFor(data.realmOrdinal(), rules)
                                            + "）"));
            return 0;
        }
        player.setData(StrifeAttachmentTypes.SHENSHI, after);

        double radius = rules.senseRadius();
        AABB box = player.getBoundingBox().inflate(radius);
        List<LivingEntity> entities =
                player.level()
                        .getEntitiesOfClass(
                                LivingEntity.class, box, e -> e != player && e.isAlive());
        entities.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));

        StringBuilder sb =
                new StringBuilder(
                        "✦ 神识扫过 "
                                + rules.senseRadius()
                                + " 格——感知到 "
                                + entities.size()
                                + " 个生灵（神识 "
                                + after.value()
                                + "/"
                                + maxFor(data.realmOrdinal(), rules)
                                + "）：");
        for (LivingEntity e : entities) {
            String type = e instanceof Monster ? "§c敌" : "§a友";
            String name =
                    e.hasCustomName()
                            ? e.getCustomName().getString()
                            : e.getType().getDescription().getString();
            long dist = Math.round(e.distanceTo(player));
            sb.append('\n')
                    .append(type)
                    .append(' ')
                    .append(name)
                    .append(" (")
                    .append(dist)
                    .append("格)");
        }
        if (entities.isEmpty()) {
            sb.append('\n').append("四周空无一物。");
        }
        context.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }
}

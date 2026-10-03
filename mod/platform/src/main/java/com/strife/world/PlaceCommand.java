package com.strife.world;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * {@code /strife world place …}：地点查询与导航（G-4 任务导航的 world 侧落地，同时直接收口 M4 准出「跨维度导航正确」）。
 *
 * <p>地点数据来自 DataGen 产物 {@code strife_places/*.json}（{@link Places} 懒加载）。三个子命令：
 *
 * <ul>
 *   <li>{@code list}：列出全部地点（id / 名称 / 维度 / 坐标）；
 *   <li>{@code info <id>}：单点详情；
 *   <li>{@code nav <id>}：以玩家当前位置为基准，算<b>水平方向</b>（N/E/S/W）+ 水平距离 + 高度差，并提示维度—— 不传送， 纯指引；
 *   <li>{@code goto <id>}：跨维度传送至地点中心（复用 {@link RealmCommand} 的「维度未注册即显式报错」口径）。
 * </ul>
 *
 * <p><b>为什么 world 侧实现而非 quest 侧</b>：03 §2 禁止 quest↔world 横向依赖；地点坐标/维度与跨维度传送都属 world 职责， 而任务目标只持有地点
 * ID 字符串。quest 侧「目标 → 地点」自动联动需要一道 realm/core 级 SPI（见 08 分册 ADR-023 提案），
 * 不在本回合权限内。本命令把导航能力做成可被玩家直接调用的兜底原语。
 */
final class PlaceCommand {

    private PlaceCommand() {}

    static LiteralArgumentBuilder<CommandSourceStack> subtree() {
        return Commands.literal("place")
                .executes(PlaceCommand::list)
                .then(Commands.literal("list").executes(PlaceCommand::list))
                .then(
                        Commands.literal("info")
                                .then(
                                        Commands.argument(
                                                        "id",
                                                        com.mojang.brigadier.arguments
                                                                .StringArgumentType.word())
                                                .executes(PlaceCommand::info)))
                .then(
                        Commands.literal("nav")
                                .then(
                                        Commands.argument(
                                                        "id",
                                                        com.mojang.brigadier.arguments
                                                                .StringArgumentType.word())
                                                .executes(PlaceCommand::nav)))
                .then(
                        Commands.literal("goto")
                                .then(
                                        Commands.argument(
                                                        "id",
                                                        com.mojang.brigadier.arguments
                                                                .StringArgumentType.word())
                                                .executes(PlaceCommand::go)));
    }

    private static List<Places.Place> all(CommandContext<CommandSourceStack> ctx) {
        return Places.getOrThrow(ctx.getSource().getServer());
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        for (Places.Place p : all(context)) {
            source.sendSuccess(
                    () ->
                            Component.literal(
                                            p.id()
                                                    + " · "
                                                    + p.name()
                                                    + " ["
                                                    + p.dimension().getPath()
                                                    + "] @ ("
                                                    + p.x()
                                                    + ", "
                                                    + p.y()
                                                    + ", "
                                                    + p.z()
                                                    + ")")
                                    .withStyle(ChatFormatting.GRAY),
                    false);
        }
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        String id = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "id");
        Places.Place place = find(all(context), id);
        if (place == null) {
            source.sendFailure(Component.literal("未知地点：" + id));
            return 0;
        }
        source.sendSuccess(
                () ->
                        Component.literal("地点 " + place.name() + "（" + place.id() + "）")
                                .withStyle(ChatFormatting.GOLD),
                false);
        source.sendSuccess(
                () ->
                        Component.literal(
                                        "维度 "
                                                + place.dimension()
                                                + "｜坐标 ("
                                                + place.x()
                                                + ", "
                                                + place.y()
                                                + ", "
                                                + place.z()
                                                + ")｜半径 "
                                                + place.radius()
                                                + " 方块｜灵气系数 "
                                                + place.qiScale())
                                .withStyle(ChatFormatting.GRAY),
                false);
        if (place.chapter() != null) {
            source.sendSuccess(
                    () ->
                            Component.literal("所属章节：" + place.chapter())
                                    .withStyle(ChatFormatting.GRAY),
                    false);
        }
        return 1;
    }

    private static int nav(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        String id = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "id");
        Places.Place place = find(all(context), id);
        if (place == null) {
            source.sendFailure(Component.literal("未知地点：" + id));
            return 0;
        }
        ResourceLocation here = player.level().dimension().location();
        if (!place.dimension().equals(here)) {
            // 跨维度：只指引，不传送——传送交给 goto（需要显式意图，且须校验目标维度已注册）。
            source.sendSuccess(
                    () ->
                            Component.literal(
                                            "目标 "
                                                    + place.name()
                                                    + " 在维度 "
                                                    + place.dimension()
                                                    + "，而你当前在 "
                                                    + here
                                                    + "。先切换维度：/strife world realm go "
                                                    + (place.dimension()
                                                                    .getNamespace()
                                                                    .equals("strife")
                                                            ? place.dimension().getPath()
                                                            : "overworld")
                                                    + "，再用 /strife world place goto "
                                                    + id)
                                    .withStyle(ChatFormatting.YELLOW),
                    false);
            return 1;
        }
        BlockPos pos = player.blockPosition();
        double dx = place.x() - pos.getX();
        double dz = place.z() - pos.getZ();
        double dy = place.y() - pos.getY();
        double horizontal = Math.hypot(dx, dz);
        String dir = compass(dx, dz);
        source.sendSuccess(
                () ->
                        Component.literal(
                                        "→ "
                                                + place.name()
                                                + "：方向 "
                                                + dir
                                                + "，水平距离 "
                                                + (int) horizontal
                                                + " 方块，高度差 "
                                                + (dy >= 0 ? "+" : "")
                                                + (int) dy)
                                .withStyle(ChatFormatting.GOLD),
                false);
        return 1;
    }

    private static int go(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        String id = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "id");
        Places.Place place = find(all(context), id);
        if (place == null) {
            source.sendFailure(Component.literal("未知地点：" + id));
            return 0;
        }
        ResourceKey<Level> target = ResourceKey.create(Registries.DIMENSION, place.dimension());
        ServerLevel targetLevel = player.getServer().getLevel(target);
        if (targetLevel == null) {
            source.sendFailure(
                    Component.literal(
                            "目标维度 "
                                    + place.dimension()
                                    + " 未在服务器注册——检查 jar 内 data/strife/dimension/ 产物是否齐全"));
            return 0;
        }
        if (player.level().dimension().equals(target)) {
            // 同维度：直接传送到地点中心（高度用地点 y）。
            player.teleportTo(
                    targetLevel,
                    place.x() + 0.5,
                    place.y(),
                    place.z() + 0.5,
                    player.getYRot(),
                    player.getXRot());
        } else {
            BlockPos spawn = targetLevel.getSharedSpawnPos();
            player.teleportTo(
                    targetLevel,
                    spawn.getX() + 0.5,
                    spawn.getY(),
                    spawn.getZ() + 0.5,
                    player.getYRot(),
                    player.getXRot());
        }
        source.sendSuccess(
                () ->
                        Component.literal(
                                        "已抵达 "
                                                + place.name()
                                                + "（"
                                                + place.dimension()
                                                + "）@ ["
                                                + place.x()
                                                + ", "
                                                + place.y()
                                                + ", "
                                                + place.z()
                                                + "]")
                                .withStyle(ChatFormatting.GOLD),
                false);
        return 1;
    }

    /** 水平偏移 → 罗盘方位（MC：+X 东、+Z 南）。 */
    private static String compass(double dx, double dz) {
        if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
            return "就在此处";
        }
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx > 0 ? "东(E)" : "西(W)";
        }
        return dz > 0 ? "南(S)" : "北(N)";
    }

    private static Places.Place find(List<Places.Place> all, String id) {
        for (Places.Place p : all) {
            if (p.id().equals(id)) {
                return p;
            }
        }
        return null;
    }
}

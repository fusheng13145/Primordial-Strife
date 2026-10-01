package com.strife.core;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.strife.core.net.StrifeCoreRules;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * core 统一注册的 {@code /strife} 命令树（03 分册 §9）。
 *
 * <p>本工单只落地 {@code info}（读玩家数据骨架）。其余兜底命令随各自模块进表： {@code quest reset}（M3）、{@code realm set}／{@code
 * lifespan set}（M1）、{@code balance reload}（M2）。 管理命令一律 {@code requiresPermission}，且执行结果写审计日志。
 */
public final class StrifeCommands {

    /**
     * 模块子命令挂载点（03 §9"其余兜底命令随各自模块进表"）：模块入口在构造期把自己的子树 builder 加进来， 服务器重建命令树时统一并入 {@code /strife}
     * 根——core 不反向 import 任何模块（03 §2 依赖链）。
     *
     * <p>禁区注记：本挂载点属 MVP 快速通道（quest 交付链 {@code /strife quest talk|deliver|status} 需要），随 owner
     * 授权提交，<b>待 A 评审</b>。
     */
    public static final java.util.concurrent.CopyOnWriteArrayList<
                    com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack>>
            MODULE_SUBTREES = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> root =
                Commands.literal(StrifeMod.MOD_ID);
        root.then(Commands.literal("info").executes(StrifeCommands::info));
        MODULE_SUBTREES.forEach(root::then);
        dispatcher.register(root);
    }

    private static int info(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.literal("info 需要玩家上下文（不能由控制台或命令方块执行）"));
            return 0;
        }

        StrifeData data = player.getData(StrifeAttachmentTypes.PLAYER_DATA);
        context.getSource()
                .sendSuccess(
                        () ->
                                Component.literal(
                                                "strife 玩家数据 (data_version="
                                                        + data.dataVersion()
                                                        + ")")
                                        .withStyle(ChatFormatting.GOLD),
                        false);
        context.getSource()
                .sendSuccess(
                        () ->
                                Component.literal(
                                        String.format(
                                                "境界 ordinal=%d 段=%d 修为=%d 灵根: 品阶=%d 五行位掩码=%d（bit0 金…bit4 土） 突破失败次数=%d",
                                                data.realmOrdinal(),
                                                data.stage(),
                                                data.qi(),
                                                data.spiritrootQuality(),
                                                data.spiritrootElements(),
                                                data.breakthroughAttempts())),
                        false);
        context.getSource()
                .sendSuccess(
                        () ->
                                Component.literal(
                                        lifespanLine(
                                                context.getSource().getServer(),
                                                data.lifespanTicks())),
                        false);
        context.getSource()
                .sendSuccess(
                        () ->
                                Component.literal(
                                        String.format(
                                                "标记位=%d 所属势力=%s 声望条目=%d",
                                                Long.bitCount(data.flags()),
                                                data.affiliation().isEmpty()
                                                        ? "散修"
                                                        : data.affiliation(),
                                                data.reputation().size())),
                        false);
        return 1;
    }

    private static String lifespanLine(MinecraftServer server, long lifespanTicks) {
        if (lifespanTicks <= 0L) {
            return "寿元=未初始化（realm 侧尚未结算，0 不代表 0 岁）";
        }
        // 换算率来自 NUMBERS §4 经 DataGen 产物（core 受管数值零字面量，05 §1）。
        long ticksPerYear = StrifeCoreRules.get(server).ticksPerYear();
        return String.format("寿元=%d 年（%d 刻）", lifespanTicks / ticksPerYear, lifespanTicks);
    }

    private StrifeCommands() {}
}

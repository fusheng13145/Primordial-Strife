package com.strife.quest;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.strife.quest.engine.QuestAdapter;
import com.strife.quest.engine.QuestBook;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * quest 兜底命令 {@code /strife quest …}（docs/03 §9"兜底命令随模块进表"）： 经 core 的 {@code
 * StrifeCommands.MODULE_SUBTREES} 挂载点并入根命令树。
 *
 * <ul>
 *   <li>{@code talk <npc>} / {@code deliver <npc>}：面向 NPC 的任务交互（MVP 无 NPC 实体，此为交互原语； 服务端校验 = 引擎的
 *       DAG 前置与目标匹配，不匹配静默无效）；限速 ≤2/s（NUMBERS quest_per_sec）属 core 令牌桶（M3），MVP 未拦——已知简化。
 *   <li>{@code status}：任务一览（状态 + 目标进度），兼作 playtest 的断链排查入口。
 *   <li>{@code track [on|off]}：任务追踪条开关（不带参数 = 切换；本会话生效）。
 * </ul>
 */
public final class QuestCommands {

    /** 挂入 {@code /strife} 根的 quest 子树（由 StrifeQuest 入口构造期注册）。 */
    public static LiteralArgumentBuilder<CommandSourceStack> subtree() {
        return Commands.literal("quest")
                .then(
                        Commands.literal("talk")
                                .then(
                                        Commands.argument("npc", StringArgumentType.word())
                                                .executes(
                                                        ctx ->
                                                                interact(
                                                                        ctx,
                                                                        QuestBook.ObjectiveType
                                                                                .TALK))))
                .then(
                        Commands.literal("deliver")
                                .then(
                                        Commands.argument("npc", StringArgumentType.word())
                                                .executes(
                                                        ctx ->
                                                                interact(
                                                                        ctx,
                                                                        QuestBook.ObjectiveType
                                                                                .DELIVER))))
                .then(Commands.literal("status").executes(QuestCommands::status))
                .then(
                        Commands.literal("track")
                                .executes(context -> track(context, null))
                                .then(
                                        Commands.literal("on")
                                                .executes(context -> track(context, Boolean.TRUE)))
                                .then(
                                        Commands.literal("off")
                                                .executes(
                                                        context -> track(context, Boolean.FALSE))));
    }

    /** {@code /strife quest track [on|off]}：追踪条开关（不带参数 = 切换）。 */
    private static int track(CommandContext<CommandSourceStack> context, Boolean enable)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        boolean enabled =
                enable == null
                        ? QuestTracker.toggle(player)
                        : QuestTracker.setEnabled(player, enable);
        context.getSource()
                .sendSuccess(() -> Component.literal("任务追踪：" + (enabled ? "开" : "关")), false);
        return 1;
    }

    private static int interact(
            CommandContext<CommandSourceStack> context, QuestBook.ObjectiveType type)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String target = StringArgumentType.getString(context, "npc");
        QuestAdapter.interact(player, type, target);
        context.getSource()
                .sendSuccess(
                        () ->
                                Component.literal(
                                                "已记录："
                                                        + (type == QuestBook.ObjectiveType.TALK
                                                                ? "对话"
                                                                : "交付")
                                                        + " → "
                                                        + target)
                                        .withStyle(ChatFormatting.GRAY),
                        false);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        for (String line : QuestAdapter.status(player).split("\n")) {
            if (!line.isBlank()) {
                context.getSource().sendSuccess(() -> Component.literal(line), false);
            }
        }
        return 1;
    }

    private QuestCommands() {}
}

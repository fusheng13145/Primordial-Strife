package com.strife.quest;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.strife.quest.dialog.DialogSessions;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * NPC 交互命令 {@code /strife npc …} 与 {@code /strife dialog …}（docs/03 §9 随模块进表）。
 *
 * <ul>
 *   <li>{@code npc spawn <npcId>}：在执行者位置摆放 NPC 实体（内容摆放与 playtest 的入口；世界生成布点属 M4）；
 *   <li>{@code dialog open <npcId>}：无实体打开对话（兜底原语，与右键同一服务端路径——对话不关心来自实体还是命令）。
 * </ul>
 */
public final class NpcCommands {

    /** 挂入 {@code /strife} 根的 npc/dialog 子树（由 StrifeQuest 入口构造期注册）。 */
    public static LiteralArgumentBuilder<CommandSourceStack> subtree() {
        return Commands.literal("npc")
                .then(
                        Commands.literal("spawn")
                                .then(
                                        Commands.argument("npcId", StringArgumentType.word())
                                                .executes(NpcCommands::spawn)))
                .then(
                        Commands.literal("dialog")
                                .then(
                                        Commands.literal("open")
                                                .then(
                                                        Commands.argument(
                                                                        "npcId",
                                                                        StringArgumentType.word())
                                                                .executes(
                                                                        NpcCommands::openDialog))));
    }

    private static int spawn(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String npcId = StringArgumentType.getString(context, "npcId");
        ServerLevel level = player.serverLevel();
        StrifeNpcEntity npc = StrifeEntities.NPC.get().create(level);
        if (npc == null) {
            context.getSource().sendFailure(Component.literal("NPC 实体创建失败"));
            return 0;
        }
        npc.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0f);
        npc.setNpcId(npcId);
        level.addFreshEntity(npc);
        context.getSource()
                .sendSuccess(
                        () -> Component.literal("已摆放 NPC：" + npcId).withStyle(ChatFormatting.GREEN),
                        true);
        return 1;
    }

    private static int openDialog(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String npcId = StringArgumentType.getString(context, "npcId");
        DialogSessions.open(player, npcId);
        return 1;
    }

    private NpcCommands() {}
}

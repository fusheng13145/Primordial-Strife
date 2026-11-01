package com.strife.quest;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 指引命令通道（P1-21，D12）：`/strife guide [域]`——无界面环境的全指引可达路径（D-P0-2：
 * 命令永久保留）。客户端《修真入门》面板（未来增强）读同一份产物，语义不分叉。
 *
 * <p>输出形态：域列表 → 域内条目 → 单条正文（成品中文，服务端权威下发，客户端零 lang 依赖）。
 */
public final class GuideCommands {

    private static final String SECTION_NAMES =
            "realm=境界, cultivation=修炼, combat=战斗, production=生产, quest=任务";

    private GuideCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> subtree() {
        var root = Commands.literal("guide").executes(ctx -> listSections(ctx.getSource()));
        for (String section : GuideLibrary.sections().keySet()) {
            root.then(
                    Commands.literal(section)
                            .executes(ctx -> listEntries(ctx.getSource(), section))
                            .then(
                                    Commands.argument(
                                                    "条目",
                                                    com.mojang.brigadier.arguments
                                                            .StringArgumentType.string())
                                            .executes(
                                                    ctx ->
                                                            showEntry(
                                                                    ctx.getSource(),
                                                                    section,
                                                                    com.mojang.brigadier.arguments
                                                                            .StringArgumentType
                                                                            .getString(
                                                                                    ctx, "条目")))));
        }
        return root;
    }

    private static int listSections(CommandSourceStack source) {
        source.sendSuccess(
                () -> Component.literal("《修真入门》五域：" + SECTION_NAMES + "——/strife guide <域> 看条目"),
                false);
        return GuideLibrary.sections().size();
    }

    private static int listEntries(CommandSourceStack source, String section) {
        var entries = GuideLibrary.sections().get(section);
        if (entries == null || entries.isEmpty()) {
            source.sendFailure(Component.literal("（该域暂无条目：" + section + "）"));
            return 0;
        }
        StringBuilder sb = new StringBuilder("【" + displayName(section) + "】");
        for (GuideLibrary.Entry entry : entries) {
            sb.append('\n')
                    .append(entry.title())
                    .append(" → /strife guide ")
                    .append(section)
                    .append(' ')
                    .append(entry.id());
        }
        source.sendSuccess(() -> Component.literal(sb.toString()), false);
        return entries.size();
    }

    private static int showEntry(CommandSourceStack source, String section, String entryId)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        for (GuideLibrary.Entry entry : GuideLibrary.sections().getOrDefault(section, List.of())) {
            if (entry.id().equals(entryId)) {
                player.displayClientMessage(
                        Component.literal("【" + entry.title() + "】" + entry.text()), false);
                return 1;
            }
        }
        source.sendFailure(
                Component.literal("（条目不存在：" + entryId + "——/strife guide " + section + " 看列表）"));
        return 0;
    }

    private static String displayName(String section) {
        return switch (section) {
            case "realm" -> "境界";
            case "cultivation" -> "修炼";
            case "combat" -> "战斗";
            case "production" -> "生产";
            case "quest" -> "任务";
            default -> section;
        };
    }
}

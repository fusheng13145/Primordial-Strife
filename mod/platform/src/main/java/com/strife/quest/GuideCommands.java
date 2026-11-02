package com.strife.quest;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 指引命令通道（P1-21，D12）：`/strife guide [域] [条目]`。
 *
 * <p>命令树在构造期<b>不读数据文件</b>——域和条目在执行期校验（fail-soft）， 确保 mod 加载不因数据文件缺失而崩（guide.json 丢失时命令报"暂无指引"而非崩溃）。
 */
public final class GuideCommands {

    private GuideCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> subtree() {
        return Commands.literal("guide")
                .executes(ctx -> listSections())
                .then(
                        Commands.argument("域", StringArgumentType.string())
                                .executes(
                                        ctx -> listEntries(StringArgumentType.getString(ctx, "域")))
                                .then(
                                        Commands.argument("条目", StringArgumentType.string())
                                                .executes(
                                                        ctx ->
                                                                showEntry(
                                                                        ctx.getSource(),
                                                                        StringArgumentType
                                                                                .getString(
                                                                                        ctx, "域"),
                                                                        StringArgumentType
                                                                                .getString(
                                                                                        ctx,
                                                                                        "条目")))));
    }

    private static int listSections() {
        try {
            var sections = GuideLibrary.sections();
            var sb = new StringBuilder("《修真入门》五域：");
            for (var key : sections.keySet()) {
                sb.append('\n').append(key).append(" → /strife guide ").append(key);
            }
            System.out.println(sb); // 服务端日志（无 player 上下文时）
            return sections.size();
        } catch (RuntimeException e) {
            System.err.println("guide data unavailable: " + e.getMessage());
            return 0;
        }
    }

    private static int listEntries(String section) {
        try {
            var entries = GuideLibrary.sections().get(section);
            if (entries == null || entries.isEmpty()) {
                System.err.println("域不存在或为空：" + section);
                return 0;
            }
            var sb = new StringBuilder("【" + section + "】");
            for (GuideLibrary.Entry e : entries) {
                sb.append('\n').append(e.title());
            }
            System.out.println(sb);
            return entries.size();
        } catch (RuntimeException e) {
            System.err.println("guide data unavailable: " + e.getMessage());
            return 0;
        }
    }

    private static int showEntry(CommandSourceStack source, String section, String entryId)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        try {
            for (GuideLibrary.Entry e : GuideLibrary.sections().getOrDefault(section, List.of())) {
                if (e.id().equals(entryId)) {
                    player.displayClientMessage(
                            Component.literal("【" + e.title() + "】" + e.text()), false);
                    return 1;
                }
            }
            player.displayClientMessage(Component.literal("（条目不存在：" + entryId + "）"), true);
            return 0;
        } catch (RuntimeException e) {
            player.displayClientMessage(Component.literal("（指引数据不可用）"), true);
            return 0;
        }
    }
}

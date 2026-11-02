package com.strife.realm;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.strife.core.StrifeAttachmentTypes;
import com.strife.core.StrifeData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 宗门职级派生（EP1 E5，ADR-032；**禁区披露批**——本类为 realm 侧只读派生，零写路径，按 ADR-031 ① 单独批次执行并显著披露）。
 *
 * <p>职级 = 对所属势力声望的**纯派生**（NUMBERS @@sect 阈值）：不加附件（03 §3 预算不动）、 不新增状态——H2
 * 声望向量是唯一事实，职级随声望即时升降。藏经阁类权限走对话条目的 reputation 条件显隐（既有 DSL），不经由此命令。
 */
public final class SectStatus {

    private SectStatus() {}

    /** 职级阈值（NUMBERS @@sect → strife_realms/rules.json 的 sect 块）。 */
    record SectRanks(int neimen, int zhenchuan) {}

    /** 从服务端 datapack 读阈值（与 {@code RealmTables} 同一资源、独立读取——不动 Rules record）； 缺块/缺键具名报错（02 §5）。 */
    static SectRanks sectRanks(net.minecraft.server.MinecraftServer server) {
        try {
            var resource =
                    server.getResourceManager()
                            .getResource(
                                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                            "strife", "strife_realms/rules.json"))
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "strife_realms/rules.json missing — RealmRulesGenerator 未跑？"));
            com.google.gson.JsonObject rules;
            try (var reader =
                    new java.io.InputStreamReader(
                            resource.open(), java.nio.charset.StandardCharsets.UTF_8)) {
                rules = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
            }
            com.google.gson.JsonObject sect = rules.getAsJsonObject("sect");
            if (sect == null) {
                throw new IllegalStateException(
                        "strife_realms/rules.json 缺 @@sect 块——NUMBERS 与 RealmRulesGenerator.BLOCKS 不一致");
            }
            return new SectRanks(
                    sect.get("rank_neimen").getAsInt(), sect.get("rank_zhenchuan").getAsInt());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("cannot read strife_realms/rules.json", e);
        }
    }

    /** 职级派生（纯函数，包内可见供用例）：外门 / 内门 / 真传。 */
    static String rankOf(int reputation, SectRanks ranks) {
        if (reputation >= ranks.zhenchuan()) {
            return "真传";
        }
        if (reputation >= ranks.neimen()) {
            return "内门";
        }
        return "外门";
    }

    public static LiteralArgumentBuilder<CommandSourceStack> subtree() {
        return Commands.literal("sect")
                .then(Commands.literal("status").executes(ctx -> status(ctx)))
                .then(
                        Commands.literal("ranking")
                                .executes(ctx -> ranking(ctx, ""))
                                .then(
                                        Commands.argument("宗门", StringArgumentType.word())
                                                .executes(
                                                        ctx ->
                                                                ranking(
                                                                        ctx,
                                                                        StringArgumentType
                                                                                .getString(
                                                                                        ctx,
                                                                                        "宗门")))));
    }

    /** 天骄榜一行：名字 + 声望 + 职级 + 星评（星数即职级档位，纯派生零状态）。 */
    record BoardRow(String name, int reputation, String rank, String stars) {}

    /** 星评（纯函数，[拟] 语义复原自 439574e 提交说明）：真传★★★ / 内门★★ / 外门★ / 无职级无星。 */
    static String starsOf(int reputation, SectRanks ranks) {
        return switch (rankOf(reputation, ranks)) {
            case "真传" -> "★★★";
            case "内门" -> "★★";
            case "外门" -> "★";
            default -> "";
        };
    }

    /** 天骄榜核（纯函数，包内可见供用例）：声望降序、同声望按名字升序；零声望不上榜（"榜上有名须有声望"）。 在线榜的诚实边界——离线玩家的附件不在此加载（那是全服扫描，另一档成本）。 */
    static List<BoardRow> boardOf(Map<String, Integer> reputations, SectRanks ranks) {
        List<BoardRow> rows = new ArrayList<>();
        reputations.forEach(
                (name, rep) -> {
                    if (rep != 0) {
                        rows.add(new BoardRow(name, rep, rankOf(rep, ranks), starsOf(rep, ranks)));
                    }
                });
        rows.sort(
                Comparator.comparingInt(BoardRow::reputation)
                        .reversed()
                        .thenComparing(BoardRow::name));
        return List.copyOf(rows);
    }

    private static int ranking(CommandContext<CommandSourceStack> context, String factionArg)
            throws CommandSyntaxException {
        ServerPlayer viewer = context.getSource().getPlayerOrException();
        StrifeData data = viewer.getData(StrifeAttachmentTypes.PLAYER_DATA);
        String faction = factionArg;
        if (faction == null || faction.isBlank()) {
            faction = data.affiliation();
            if (faction == null || faction.isBlank()) {
                faction = "fac_yuelai"; // 散修默认看月来渡（EP1/EP2 的坊市枢纽宗门）
            }
        }
        SectRanks ranks;
        try {
            ranks = sectRanks(viewer.getServer());
        } catch (RuntimeException e) {
            context.getSource().sendFailure(Component.literal("（职级数据不可用：" + e.getMessage() + "）"));
            return 0;
        }
        Map<String, Integer> reputations = new java.util.LinkedHashMap<>();
        for (ServerPlayer online : viewer.getServer().getPlayerList().getPlayers()) {
            int rep =
                    online.getData(StrifeAttachmentTypes.PLAYER_DATA)
                            .reputation()
                            .getOrDefault(faction, 0);
            reputations.put(online.getName().getString(), rep);
        }
        List<BoardRow> board = boardOf(reputations, ranks);
        StringBuilder sb =
                new StringBuilder("【天骄榜 · " + faction + "】（在线修士，共 " + board.size() + " 人上榜）");
        int place = 1;
        for (BoardRow row : board) {
            sb.append('\n')
                    .append(place++)
                    .append(". ")
                    .append(row.name())
                    .append(' ')
                    .append(row.reputation())
                    .append(' ')
                    .append(row.rank())
                    .append(row.stars());
        }
        if (board.isEmpty()) {
            sb.append("\n尚无人上榜——声望从坊市与委托里来。");
        }
        context.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        StrifeData data = player.getData(com.strife.core.StrifeAttachmentTypes.PLAYER_DATA);
        String affiliation = data.affiliation();
        if (affiliation == null || affiliation.isBlank()) {
            context.getSource()
                    .sendSuccess(
                            () -> Component.literal("【身份】散修——无宗门职级；声望向量见 /strife info"), false);
            return 1;
        }
        Map<String, Integer> rep = data.reputation();
        int own = rep.getOrDefault(affiliation, 0);
        String rank;
        try {
            rank = rankOf(own, sectRanks(player.getServer()));
        } catch (RuntimeException e) {
            context.getSource().sendFailure(Component.literal("（职级数据不可用：" + e.getMessage() + "）"));
            return 0;
        }
        StringBuilder sb = new StringBuilder("【" + affiliation + " · " + rank + "】声望 " + own);
        for (Map.Entry<String, Integer> e : rep.entrySet()) {
            if (!e.getKey().equals(affiliation)) {
                sb.append('\n').append(e.getKey()).append(" ").append(e.getValue());
            }
        }
        context.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }
}

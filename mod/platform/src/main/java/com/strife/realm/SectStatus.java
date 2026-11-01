package com.strife.realm;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.strife.core.StrifeData;
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
                .then(Commands.literal("status").executes(ctx -> status(ctx)));
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

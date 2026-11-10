package com.strife.realm;

import com.strife.core.StrifeData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/**
 * 境界头衔（修仙世界感批）：玩家的头顶名牌后缀显示当前境界——〔练气〕〔筑基〕〔元婴〕…… 金色，全服可见。
 *
 * <p>实现走原版 scoreboard team 的 player suffix（纯服务端，零客户端代码、零包协议）—— 一个境界一个 team（幂等复用），头衔随 {@link
 * CultivationHandler#attemptBreakthrough} 与登录/大限刷新。 方位称呼是修仙社交的第一印象：面对陌生修士，先看气。
 */
public final class RealmTitles {

    private RealmTitles() {}

    /** 每个境界一个 team（幂等复用；team 名前缀 strife_ 防与其他插件的 team 撞名）。 */
    static String teamNameFor(int realmOrdinal) {
        return "strife_realm_" + Math.max(0, realmOrdinal);
    }

    /** 头衔后缀：〔境界名〕（境界名走 realm.strife.<id> 双语键）。 */
    static Component suffixFor(RealmTables.RealmEntry realm) {
        return Component.literal("〔")
                .append(Component.translatable("realm.strife." + realm.id()))
                .append(Component.literal("〕"))
                .withStyle(ChatFormatting.GOLD);
    }

    /** 幂等应用：team 不存在则建，玩家不在该 team 则迁入。表不可用静默跳过（登录路径不因表缺失崩）。 */
    public static void apply(ServerPlayer player) {
        RealmTables tables = RealmTables.getOrNull(player.getServer());
        if (tables == null) {
            return;
        }
        StrifeData data = player.getData(com.strife.core.StrifeAttachmentTypes.PLAYER_DATA);
        int ordinal = Math.min(Math.max(0, data.realmOrdinal()), tables.realmCount() - 1);
        RealmTables.RealmEntry realm = tables.realm(ordinal);
        Scoreboard board = player.getServer().getScoreboard();
        PlayerTeam team = board.getPlayerTeam(teamNameFor(ordinal));
        if (team == null) {
            team = board.addPlayerTeam(teamNameFor(ordinal));
            team.setColor(ChatFormatting.GOLD);
        }
        team.setPlayerSuffix(suffixFor(realm));
        if (board.getPlayerTeam(player.getScoreboardName()) != team) {
            board.addPlayerToTeam(player.getScoreboardName(), team);
        }
    }
}

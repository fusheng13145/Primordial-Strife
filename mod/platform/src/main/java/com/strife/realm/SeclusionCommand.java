package com.strife.realm;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.strife.core.MeditationState;
import com.strife.core.StrifeAttachmentTypes;
import com.strife.core.StrifeData;
import com.strife.core.StrifeTime;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Monster;

/**
 * 闭关命令（EP1，ADR-034；**禁区披露批 #2**——按 ADR-031 ① 单独批次+显著披露）。语义（ADR-034）：
 *
 * <p>`/strife seclusion <时辰>`——①入定安全校验（{@code @@seclusion.safety_radius} 格内敌对生物拒入）；
 * ②修为按打坐四因子公式<b>一次性重放</b>闭关时长（{@link SeclusionMath} 复用 {@link MeditationMath}，
 * 不逐刻模拟、不开新成长通道）；③寿元同步流逝（tick 精确，"山中方七日"的代价面）；④打坐会话激活中 拒入（两套结算不能同时走）。无冷却——时间代价本身就是成本。
 */
public final class SeclusionCommand {

    private SeclusionCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> subtree() {
        return Commands.literal("seclusion")
                .then(
                        Commands.argument("时辰", IntegerArgumentType.integer(1))
                                .executes(
                                        ctx ->
                                                seclude(
                                                        ctx,
                                                        IntegerArgumentType.getInteger(
                                                                ctx, "时辰"))));
    }

    /** 闭关规则（NUMBERS @@seclusion → rules.json 的 seclusion 块）。 */
    record SeclusionRules(int shichenTicks, int maxShichen, int safetyRadius) {}

    /** 从 RealmTables 类型化读规则（披露批 #10 收口）——此前裸读 rules.json 手工解析。 表不可用回 null（命令层给可读失败）。 */
    static SeclusionRules seclusionRules(net.minecraft.server.MinecraftServer server) {
        RealmTables tables = RealmTables.getOrNull(server);
        if (tables == null) {
            return null;
        }
        var rules = tables.rules();
        return new SeclusionRules(
                rules.seclusionShichenTicks(),
                rules.seclusionMaxShichen(),
                rules.seclusionSafetyRadius());
    }

    private static int seclude(CommandContext<CommandSourceStack> context, int shichen)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        StrifeData data = player.getData(StrifeAttachmentTypes.PLAYER_DATA);
        SeclusionRules rules = seclusionRules(player.getServer());
        if (rules == null) {
            context.getSource().sendFailure(Component.literal("（闭关规则不可用——realm 表未加载）"));
            return 0;
        }

        int hostile =
                player.level()
                        .getEntitiesOfClass(
                                Monster.class,
                                player.getBoundingBox().inflate(rules.safetyRadius()))
                        .size();
        String rejection =
                SeclusionMath.rejectionReason(
                        data.meditation().active(), hostile, shichen, rules.maxShichen());
        if (rejection != null) {
            context.getSource().sendFailure(Component.literal(rejection));
            return 0;
        }
        long ticks = (long) shichen * rules.shichenTicks();
        if (data.lifespanTicks() <= ticks) {
            context.getSource().sendFailure(Component.literal("（剩余寿元不足以支撑这次闭关——去续命）"));
            return 0;
        }

        RealmTables tables = RealmTables.get(player.getServer());
        RealmTables.RealmEntry realm = tables.realm(data.realmOrdinal());
        double qiPerTick =
                StrifeTime.perSecondToPerTick(
                        CultivationHandler.qiPerSecond(player, tables, realm, data));
        int earned = SeclusionMath.earnedQi(qiPerTick, ticks);
        MeditationMath.Applied applied = MeditationMath.apply(data.qi(), earned, realm.qiMax());
        StrifeData updated =
                data.withRealm(
                                data.realmOrdinal(),
                                BreakthroughMath.stageFor(
                                        applied.qi(), realm.qiMax(), realm.stageCount()),
                                applied.qi(),
                                data.lifespanTicks() - SeclusionMath.lifespanTicks(ticks),
                                data.breakthroughAttempts())
                        .withMeditation(MeditationState.IDLE);
        player.setData(StrifeAttachmentTypes.PLAYER_DATA, updated);
        context.getSource()
                .sendSuccess(
                        () ->
                                Component.literal(
                                        "✦ 闭关结束（"
                                                + shichen
                                                + " 时辰）：修为 +"
                                                + applied.applied()
                                                + "（"
                                                + applied.qi()
                                                + "/"
                                                + realm.qiMax()
                                                + "）· 寿元 −"
                                                + SeclusionMath.lifespanYears(
                                                        SeclusionMath.lifespanTicks(ticks), 24000L)
                                                + " 年"),
                        false);
        return 1;
    }
}

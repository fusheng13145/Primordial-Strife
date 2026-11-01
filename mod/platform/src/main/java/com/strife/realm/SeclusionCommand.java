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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
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

    static SeclusionRules seclusionRules(MinecraftServer server) {
        try {
            var resource =
                    server.getResourceManager()
                            .getResource(
                                    ResourceLocation.fromNamespaceAndPath(
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
            com.google.gson.JsonObject block = rules.getAsJsonObject("seclusion");
            if (block == null) {
                throw new IllegalStateException(
                        "strife_realms/rules.json 缺 @@seclusion 块——NUMBERS 与 RealmRulesGenerator.BLOCKS 不一致");
            }
            return new SeclusionRules(
                    block.get("shichen_ticks").getAsInt(),
                    block.get("max_shichen").getAsInt(),
                    block.get("safety_radius").getAsInt());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("cannot read strife_realms/rules.json", e);
        }
    }

    private static int seclude(CommandContext<CommandSourceStack> context, int shichen)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        StrifeData data = player.getData(StrifeAttachmentTypes.PLAYER_DATA);
        SeclusionRules rules = seclusionRules(player.getServer());

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

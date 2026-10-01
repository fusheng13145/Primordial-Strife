package com.strife.combat;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.strife.core.StrifeAttachmentTypes;
import com.strife.core.StrifeData;
import com.strife.realm.RealmTables;
import com.strife.realm.UnlockBits;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 功法的学法 / 装备 / 生效倍率（docs/03 §1 combat 职责、05 §2 公式第四项）。
 *
 * <p>分层：判定在 {@link TechniqueGate}（纯函数，可单测），内容在 {@link TechniqueTables}（真实产物），本类只做 "取附件 → 判定 → 写回 +
 * 给玩家一句可读的话"。
 *
 * <p>学法与装备分开：任务奖励给的是"学会"，真正生效还要 {@code technique_equip} 解锁（STORY §4 #4 的口径：功法只给，
 * 装备在筑基之后）。这样"拿到了但还用不了"是一个能被解释的状态，而不是静默失效。
 */
public final class Techniques {

    private static final Logger LOGGER = LoggerFactory.getLogger("strife/combat");

    private static boolean loggedUnknownRealm;

    private Techniques() {}

    /** 学会一部功法（任务奖励与兜底命令的入口）；返回是否真的学会了。 */
    public static boolean learn(ServerPlayer player, String techniqueId) {
        TechniqueTables tables = TechniqueTables.getOrNull(player.server);
        RealmTables realmTables = RealmTables.getOrNull(player.server);
        if (tables == null || realmTables == null) {
            return false;
        }
        TechniqueTables.Technique technique = tables.technique(techniqueId);
        if (technique == null) {
            tell(player, "msg.strife.technique.denied.unknown", techniqueId);
            return false;
        }
        TechniqueGate.Denial denial =
                TechniqueGate.canLearn(snapshot(player), requirements(technique, realmTables));
        if (denial != TechniqueGate.Denial.ALLOWED) {
            tell(player, TechniqueGate.messageKey(denial), techniqueId);
            return false;
        }
        StrifeData data = player.getData(StrifeAttachmentTypes.PLAYER_DATA);
        if (data.techniques().knows(techniqueId)) {
            return false; // 幂等：任务奖励可能被重放，不该反复弹提示
        }
        player.setData(
                StrifeAttachmentTypes.PLAYER_DATA,
                data.withTechniques(data.techniques().learning(techniqueId)));
        tell(player, "msg.strife.technique.learned", techniqueId);
        return true;
    }

    /** 装备一部已学功法；返回是否装备成功。 */
    public static boolean equip(ServerPlayer player, String techniqueId) {
        TechniqueTables tables = TechniqueTables.getOrNull(player.server);
        if (tables == null || tables.technique(techniqueId) == null) {
            tell(player, "msg.strife.technique.denied.unknown", techniqueId);
            return false;
        }
        StrifeData data = player.getData(StrifeAttachmentTypes.PLAYER_DATA);
        TechniqueGate.Denial denial =
                TechniqueGate.canEquip(snapshot(player), data.techniques().knows(techniqueId));
        if (denial != TechniqueGate.Denial.ALLOWED) {
            tell(player, TechniqueGate.messageKey(denial), techniqueId);
            return false;
        }
        player.setData(
                StrifeAttachmentTypes.PLAYER_DATA,
                data.withTechniques(data.techniques().equipping(techniqueId)));
        tell(player, "msg.strife.technique.equipped", techniqueId);
        return true;
    }

    /** 卸下当前功法。 */
    public static boolean unequip(ServerPlayer player) {
        StrifeData data = player.getData(StrifeAttachmentTypes.PLAYER_DATA);
        if (data.techniques().equipped().isEmpty()) {
            tell(player, "msg.strife.technique.none_equipped", "");
            return false;
        }
        player.setData(
                StrifeAttachmentTypes.PLAYER_DATA,
                data.withTechniques(data.techniques().equipping("")));
        tell(player, "msg.strife.technique.unequipped", "");
        return true;
    }

    /**
     * 四因子第四项：功法倍率 = {@code qi_rate_ratio} × 五行亲和（05 §2 公式的 {@code 功法倍率}）。
     *
     * <p>未装备、表不可用、功法已从内容里删除时一律返回中性 1.0——与其余两项同口径。
     */
    static double coefficient(ServerPlayer player) {
        TechniqueTables tables = TechniqueTables.getOrNull(player.server);
        if (tables == null) {
            return 1.0;
        }
        StrifeData data = player.getData(StrifeAttachmentTypes.PLAYER_DATA);
        String equipped = data.techniques().equipped();
        if (equipped.isEmpty()) {
            return 1.0;
        }
        TechniqueTables.Technique technique = tables.technique(equipped);
        if (technique == null) {
            return 1.0;
        }
        RealmTables realmTables = RealmTables.getOrNull(player.server);
        double affinity =
                realmTables == null
                        ? 1.0
                        : realmTables.affinityCoefficient(
                                technique.elementMask(),
                                data.spiritrootElements(),
                                technique.declaredAffinity());
        return technique.qiRateRatio() * affinity;
    }

    /** 当前装备功法的判定明细（面板的四因子分解用；未装备时返回全 0）。 */
    public static EquippedInfo equippedInfo(ServerPlayer player) {
        TechniqueTables tables = TechniqueTables.getOrNull(player.server);
        StrifeData data = player.getData(StrifeAttachmentTypes.PLAYER_DATA);
        String equipped = data.techniques().equipped();
        if (tables == null || equipped.isEmpty()) {
            return new EquippedInfo("", 1.0, 1.0);
        }
        TechniqueTables.Technique technique = tables.technique(equipped);
        if (technique == null) {
            return new EquippedInfo(equipped, 1.0, 1.0);
        }
        RealmTables realmTables = RealmTables.getOrNull(player.server);
        double affinity =
                realmTables == null
                        ? 1.0
                        : realmTables.affinityCoefficient(
                                technique.elementMask(),
                                data.spiritrootElements(),
                                technique.declaredAffinity());
        return new EquippedInfo(equipped, technique.qiRateRatio(), affinity);
    }

    /** 面板展示用：装备的功法 id + 倍率 + 亲和。 */
    public record EquippedInfo(String techniqueId, double qiRateRatio, double affinity) {
        public double product() {
            return qiRateRatio * affinity;
        }
    }

    static TechniqueGate.PlayerSnapshot snapshot(ServerPlayer player) {
        StrifeData data = player.getData(StrifeAttachmentTypes.PLAYER_DATA);
        return new TechniqueGate.PlayerSnapshot(
                data.realmOrdinal(),
                data.stage(),
                data.spiritrootElements(),
                UnlockBits.has(data.flags(), "technique_equip"),
                data.affiliation());
    }

    private static TechniqueGate.Requirements requirements(
            TechniqueTables.Technique technique, RealmTables tables) {
        RealmTables.RealmEntry required = tables.realmById(technique.requiredRealm());
        if (required == null && !technique.requiredRealm().isEmpty() && !loggedUnknownRealm) {
            loggedUnknownRealm = true;
            LOGGER.warn(
                    "功法 '{}' 引用了不存在的境界 '{}'——境界门禁按无要求处理（V-REF 应拦下这类引用）",
                    technique.id(),
                    technique.requiredRealm());
        }
        return new TechniqueGate.Requirements(
                required == null ? 0 : required.ordinal(),
                technique.requiredStage(),
                technique.requiredRootMask(),
                technique.faction());
    }

    /** 命令子树：{@code /strife technique list|equip|unequip|learn}（learn 为 OP 兜底，03 §9）。 */
    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("technique")
                .executes(context -> list(context.getSource()))
                .then(Commands.literal("list").executes(context -> list(context.getSource())))
                .then(
                        Commands.literal("equip")
                                .then(
                                        Commands.argument("id", ResourceLocationArgument.id())
                                                .executes(
                                                        context ->
                                                                run(
                                                                        context.getSource(),
                                                                        ResourceLocationArgument
                                                                                .getId(
                                                                                        context,
                                                                                        "id")
                                                                                .getPath(),
                                                                        Techniques::equip))))
                .then(
                        Commands.literal("unequip")
                                .executes(
                                        context -> {
                                            ServerPlayer player = context.getSource().getPlayer();
                                            return player != null && unequip(player) ? 1 : 0;
                                        }))
                .then(
                        Commands.literal("learn")
                                .requires(source -> source.hasPermission(2))
                                .then(
                                        Commands.argument("id", ResourceLocationArgument.id())
                                                .executes(
                                                        context ->
                                                                run(
                                                                        context.getSource(),
                                                                        ResourceLocationArgument
                                                                                .getId(
                                                                                        context,
                                                                                        "id")
                                                                                .getPath(),
                                                                        Techniques::learn))));
    }

    private interface Action {
        boolean apply(ServerPlayer player, String techniqueId);
    }

    private static int run(CommandSourceStack source, String techniqueId, Action action) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("该命令需要玩家上下文"));
            return 0;
        }
        return action.apply(player, techniqueId) ? 1 : 0;
    }

    private static int list(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("该命令需要玩家上下文"));
            return 0;
        }
        StrifeData data = player.getData(StrifeAttachmentTypes.PLAYER_DATA);
        List<String> learned = new ArrayList<>(data.techniques().learned());
        learned.sort(String::compareTo);
        source.sendSuccess(
                () ->
                        Component.literal(
                                        "已学功法 "
                                                + learned.size()
                                                + " 部："
                                                + (learned.isEmpty()
                                                        ? "（无）"
                                                        : String.join("、", learned)))
                                .withStyle(ChatFormatting.GOLD),
                false);
        String equipped =
                data.techniques().equipped().isEmpty() ? "（未装备）" : data.techniques().equipped();
        EquippedInfo info = equippedInfo(player);
        source.sendSuccess(
                () ->
                        Component.literal(
                                "当前功法："
                                        + equipped
                                        + String.format(
                                                " 倍率 %.2f × 亲和 %.2f = %.2f",
                                                info.qiRateRatio(),
                                                info.affinity(),
                                                info.product())),
                false);
        return 1;
    }

    private static void tell(ServerPlayer player, String key, String techniqueId) {
        Component name =
                techniqueId == null || techniqueId.isEmpty()
                        ? Component.empty()
                        : Component.translatable("technique.strife." + techniqueId);
        player.displayClientMessage(Component.translatable(key, name), true);
    }

    /** 模块入口调用：把功法倍率接进四因子公式。 */
    static void registerFactor() {
        com.strife.core.CultivationFactors.registerTechnique(Techniques::coefficient);
        LOGGER.info("strife combat wired cultivation factor: technique=equipped technique ratio");
    }

    /** lang key 前缀校验用（内容 ID → 功法名 key）。 */
    public static String langKey(String techniqueId) {
        return "technique.strife." + techniqueId;
    }
}

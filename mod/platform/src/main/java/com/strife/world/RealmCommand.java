package com.strife.world;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * {@code /strife world realm}：跨维度导航（M4 准出「跨维度导航正确」的最小交付，ADR-021 授权后实现）。
 *
 * <p><b>为什么是命令而不是传送门</b>：M4 的验收语义是「维度间往返正确」——玩家过去、回得来、身上数据（修为/境界/ 附件）不丢。传送门方块是 M5+ 的内容件（EP2
 * 秘境复用），先用兜底命令把「维度存在、可抵达、可返回」这条链立起来， G-4 任务导航也能直接复用这里的维度 API（{@link #resolveTarget}）。
 *
 * <p><b>跨界数据为什么不用测</b>：修为等附件挂在玩家实体上，跨维度由原版实体迁移引擎保证（换 {@code ServerLevel} 不重建实体），M0 的 Attachment
 * Codec 往返用例已锁存储层——本命令的职责只有「把人送过去」，不越权碰数据。
 *
 * <p>输出只报维度与落点，<b>不读修为</b>——world 与 realm 同为灰区，横向依赖是 R13 明令禁止的（ADR-018 教训）。
 */
final class RealmCommand {

    private RealmCommand() {}

    /** 挂到 {@code /strife world} 下的子树（由 {@link StrifeWorld} 构造期注册进 core 的挂载点）。 */
    static LiteralArgumentBuilder<CommandSourceStack> subtree() {
        return Commands.literal("realm")
                .executes(RealmCommand::status)
                .then(
                        Commands.literal("go")
                                .then(Commands.literal("upper").executes(RealmCommand::goUpper))
                                .then(
                                        Commands.literal("overworld")
                                                .executes(RealmCommand::goOverworld)));
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(
                () ->
                        Component.literal(
                                        "当前维度 "
                                                + source.getLevel().dimension().location()
                                                + "；可去：/strife world realm go upper|overworld")
                                .withStyle(ChatFormatting.GOLD),
                false);
        return 1;
    }

    private static int goUpper(CommandContext<CommandSourceStack> context) {
        return go(context, StrifeWorld.UPPER_REALM, "upper");
    }

    private static int goOverworld(CommandContext<CommandSourceStack> context) {
        return go(context, Level.OVERWORLD, "overworld");
    }

    private static int go(
            CommandContext<CommandSourceStack> context, ResourceKey<Level> target, String name) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("跨维度导航需要玩家执行"));
            return 0;
        }
        ServerLevel targetLevel = player.getServer().getLevel(target);
        if (targetLevel == null) {
            // 数据包维度在服务器启动时注册；getLevel 为 null 意味着维度数据根本不在 classpath（产物缺失），
            // 这是安装破损而非运行时波动，显式红掉并指路，而不是静默失败让玩家以为「上界不存在」。
            source.sendFailure(
                    Component.literal(
                            "目标维度 "
                                    + target.location()
                                    + " 未在服务器注册——检查 jar 内 "
                                    + "data/strife/dimension/ 产物是否齐全（datagen 新鲜度）"));
            return 0;
        }
        if (player.level().dimension().equals(target)) {
            source.sendSuccess(
                    () ->
                            Component.literal("已在 " + target.location() + "，无需传送")
                                    .withStyle(ChatFormatting.YELLOW),
                    false);
            return 0;
        }
        BlockPos spawn = targetLevel.getSharedSpawnPos();
        double x = spawn.getX() + 0.5;
        double y = spawn.getY();
        double z = spawn.getZ() + 0.5;
        player.teleportTo(targetLevel, x, y, z, player.getYRot(), player.getXRot());
        source.sendSuccess(
                () ->
                        Component.literal(
                                        "已抵达 "
                                                + name
                                                + "（"
                                                + target.location()
                                                + "）@ ["
                                                + (int) x
                                                + ", "
                                                + (int) y
                                                + ", "
                                                + (int) z
                                                + "]")
                                .withStyle(ChatFormatting.GOLD),
                false);
        return 1;
    }

    /** 导航名 → 维度 key（G-4 任务导航复用的维度 API）。未知名字返回 null，由调用方报错—— 包内可见以便用例不依赖服务器即可覆盖全部分支。 */
    static ResourceKey<Level> resolveTarget(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "upper" -> StrifeWorld.UPPER_REALM;
            case "overworld" -> Level.OVERWORLD;
            default -> null;
        };
    }
}

package com.strife.quest;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 心性试炼台（2026-11-04）：右键开启 120 秒坚守（{@code quest_ep2_trial_will_01}）。
 *
 * <p>为什么需要这个方块：SURVIVE 目标此前**没有任何推进源**（枚举存在、无 report 调用——与凝神丹同款"声称已接线"）， 试炼任务在世界上不可完成。试炼台是 SURVIVE
 * 的唯一推进口：台上 1 秒记 1 坚守，离台即败，心魔周期性来袭。
 */
public final class TrialAltarBlock extends Block {

    /** 心性试炼任务（quest_ep2 的第三关）。 */
    static final String WILL_TRIAL_QUEST = "quest_ep2_trial_will_01";

    public TrialAltarBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            tryStart(serverPlayer, (ServerLevel) level, pos.immutable());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void tryStart(ServerPlayer player, ServerLevel level, BlockPos altar) {
        if (TrialSessions.active(player.getUUID())) {
            player.displayClientMessage(Component.literal("（试炼已在进行——站稳，莫离台）"), true);
            return;
        }
        var quests = player.getData(com.strife.core.StrifeAttachmentTypes.PLAYER_DATA).quests();
        if (!quests.activated().contains(WILL_TRIAL_QUEST)) {
            player.displayClientMessage(Component.literal("（先向玄接下三重试炼——此台只回应有备之人）"), true);
            return;
        }
        TrialSessions.start(player.getUUID(), level.getGameTime(), altar);
        player.displayClientMessage(
                Component.literal("✦ 心性试炼开始——在台上坚守 120 秒，心魔将至")
                        .withStyle(net.minecraft.ChatFormatting.DARK_PURPLE),
                false);
    }
}

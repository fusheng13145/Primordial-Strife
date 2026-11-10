package com.strife.production;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 黑市暗柜（StrifeUiBridge 第四屏的物理入口）：右键开黑市看板——价目、持有、因果警示。
 *
 * <p>暗柜嵌在坊市的阴影里（qi_fangshi 锚点偏移落位，看板家族第四成员）。
 */
public final class BlackMarketBlock extends Block {

    public BlackMarketBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            BlackMarketUi.openFor(serverPlayer);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}

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
 * 拍卖看板（StrifeUiBridge 第三屏的物理入口）：右键开拍卖看板——荷兰拍当前价、密封投标状态， 服务端组数据下发（坊市看板同款纪律）。
 *
 * <p>落位在文津渡锚点（qi_wenjiang，挑灯人守灯之处）——拍卖行在叙事里就是渡口的生意。
 */
public final class AuctionBoardBlock extends Block {

    public AuctionBoardBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            AuctionUi.openFor(serverPlayer);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}

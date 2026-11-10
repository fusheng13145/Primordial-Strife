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
 * 坊市告示牌（StrifeUiBridge 第二屏的物理入口）：右键开坊市看板——价目、持有量、兑换比、鉴定费， 服务端组数据下发（炉交互屏同款纪律）。
 *
 * <p>看板是"坊市的脸"：告示柱落位在文津坊市锚点（{@link MarketBoardPlacer}），牙人张守着它—— 价目墙从此实体化，不再是聊天栏里的一段文本。
 */
public final class MarketBoardBlock extends Block {

    public MarketBoardBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            MarketUi.openFor(serverPlayer);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}

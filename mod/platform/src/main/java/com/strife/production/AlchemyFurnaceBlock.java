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
 * 炼丹炉方块（ADR-038 交互层）：右键打开炉交互屏——服务端组数据经 StrifeUiPayload 下发， 客户端 clientfx 渲染（未接 clientfx 时右键 =
 * 无操作，{@code /strife craft pill} 命令通道兜底）。
 *
 * <p>刻意不做 BlockEntity/容器槽位：炼丹的语义是"配方 + 材料清单"而不是"格子摆料"（pills.csv 的 pattern 列是配方形状记录，
 * 不是操作界面），现有概率抽签口径不因交互层引入第二条判定路径。
 */
public final class AlchemyFurnaceBlock extends Block {

    public AlchemyFurnaceBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            AlchemyUi.openFor(serverPlayer);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}

package com.strife.production;

import com.strife.core.ProximityPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * 坊市告示柱落位器（2026-11-10）：玩家走近文津坊市锚点（qi_fangshi）时， 在地表幂等落成告示柱——柱顶挂看牌方块，牙人张守在旁边。
 *
 * <p>石门落位器同款ProximityPlacement 公共触发器（core）；幂等判据 = 挂牌方块（block_market_board）已在。
 */
public final class MarketBoardPlacer {

    private static final String FANGSHI_PLACE = "qi_fangshi";

    private MarketBoardPlacer() {}

    public static void register() {
        ProximityPlacement.register(
                "minecraft:overworld", "qi_fangshi", MarketBoardPlacer::ensureBoard);
    }

    /** 落位（幂等）：挂牌已在即跳过。几何（纯函数 {@link #boardOffsets}）：2 高石砖基柱 + 柱顶挂牌。 */
    static void ensureBoard(ServerLevel level, int anchorX, int anchorZ) {
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, anchorX, anchorZ);
        BlockPos origin = new BlockPos(anchorX, surfaceY, anchorZ);
        if (level.getBlockState(origin.above(2)).is(MarketBlocks.MARKET_BOARD.get())) {
            return;
        }
        BlockState pillar = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState lantern = Blocks.LANTERN.defaultBlockState();
        level.setBlockAndUpdate(origin, pillar);
        level.setBlockAndUpdate(origin.above(), pillar);
        level.setBlockAndUpdate(
                origin.above(2), MarketBlocks.MARKET_BOARD.get().defaultBlockState());
        level.setBlockAndUpdate(origin.above(3), lantern);
    }
}

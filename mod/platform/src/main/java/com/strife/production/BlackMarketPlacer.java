package com.strife.production;

import com.strife.core.ProximityPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** 黑市暗柜落位器（看板家族第四成员，2026-11-11）：玩家走近文津坊市锚点时， 在告示柱**东侧 4 格**幂等落成暗柜——明面上的价目墙旁边，阴影里就是暗柜（同一地点两个面）。 */
public final class BlackMarketPlacer {

    private static final int OFFSET_X = 4;

    private BlackMarketPlacer() {}

    public static void register() {
        ProximityPlacement.register(
                "minecraft:overworld", "qi_fangshi", BlackMarketPlacer::ensureCabinet);
    }

    /** 落位（幂等）：暗柜已在即跳过。几何：基座 + 暗柜 + 幽光魂灯笼。 */
    static void ensureCabinet(ServerLevel level, int anchorX, int anchorZ) {
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, anchorX, anchorZ);
        BlockPos origin = new BlockPos(anchorX, surfaceY, anchorZ);
        if (level.getBlockState(origin).is(BlackMarketBlocks.BLACK_MARKET.get())) {
            return;
        }
        BlockState base = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        level.setBlockAndUpdate(origin.below(), base);
        level.setBlockAndUpdate(origin, BlackMarketBlocks.BLACK_MARKET.get().defaultBlockState());
        level.setBlockAndUpdate(origin.above(), Blocks.SOUL_LANTERN.defaultBlockState());
    }
}

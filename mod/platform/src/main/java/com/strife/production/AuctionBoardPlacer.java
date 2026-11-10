package com.strife.production;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 拍卖看板落位器（2026-11-10）：玩家走近文津渡锚点（qi_wenjiang）时， 在地表幂等落成拍卖告示柱——柱顶挂拍卖看板方块。
 *
 * <p>坊市告示柱同款玩家 tick 近距模式；幂等判据 = 拍卖挂牌（block_auction_board）已在。
 */
public final class AuctionBoardPlacer {

    private static final int CHECK_INTERVAL_TICKS = 40;
    private static final double TRIGGER_RADIUS_SQR = 64.0 * 64.0;
    private static final String WENJIANG_PLACE = "qi_wenjiang";

    private AuctionBoardPlacer() {}

    public static void register() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(new AuctionBoardPlacer());
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            return;
        }
        if (player.tickCount % CHECK_INTERVAL_TICKS != 0) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (!level.dimension().location().toString().equals("minecraft:overworld")) {
            return;
        }
        var anchor = com.strife.core.PlaceBridges.findPlace(player.server, WENJIANG_PLACE);
        if (anchor.isEmpty()) {
            return;
        }
        var coords = anchor.get();
        double dx = player.getX() - (coords.x() + 0.5);
        double dz = player.getZ() - (coords.z() + 0.5);
        if (dx * dx + dz * dz > TRIGGER_RADIUS_SQR) {
            return;
        }
        ensureBoard(level, coords.x(), coords.z());
    }

    /** 落位（幂等）：挂牌已在即跳过。几何：基柱 2 + 挂牌 1 + 顶灯 1（boardOffsets 纯函数供测试）。 */
    static void ensureBoard(ServerLevel level, int anchorX, int anchorZ) {
        if (!level.isLoaded(new BlockPos(anchorX, 0, anchorZ))) {
            return;
        }
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, anchorX, anchorZ);
        BlockPos origin = new BlockPos(anchorX, surfaceY, anchorZ);
        if (level.getBlockState(origin.above(2)).is(AuctionBlocks.AUCTION_BOARD.get())) {
            return;
        }
        BlockState pillar = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState lantern = Blocks.LANTERN.defaultBlockState();
        level.setBlockAndUpdate(origin, pillar);
        level.setBlockAndUpdate(origin.above(), pillar);
        level.setBlockAndUpdate(
                origin.above(2), AuctionBlocks.AUCTION_BOARD.get().defaultBlockState());
        level.setBlockAndUpdate(origin.above(3), lantern);
    }

    /** 告示柱几何（纯函数供测试）：垂线四层——0/1 柱、2 挂牌、3 顶灯。 */
    static Map<Integer, String> boardOffsets() {
        return Map.of(0, "pillar", 1, "pillar", 2, "board", 3, "lantern");
    }
}

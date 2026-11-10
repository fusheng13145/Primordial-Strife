package com.strife.production;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** 黑市暗柜落位器（看板家族第四成员，2026-11-11）：玩家走近文津坊市锚点时， 在告示柱**东侧 4 格**幂等落成暗柜——明面上的价目墙旁边，阴影里就是暗柜（同一地点两个面）。 */
public final class BlackMarketPlacer {

    private static final int CHECK_INTERVAL_TICKS = 40;
    private static final double TRIGGER_RADIUS_SQR = 64.0 * 64.0;
    private static final int OFFSET_X = 4;

    private BlackMarketPlacer() {}

    public static void register() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(new BlackMarketPlacer());
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
        var anchor = com.strife.core.PlaceBridges.findPlace(player.server, "qi_fangshi");
        if (anchor.isEmpty()) {
            return;
        }
        var coords = anchor.get();
        double dx = player.getX() - (coords.x() + 0.5);
        double dz = player.getZ() - (coords.z() + 0.5);
        if (dx * dx + dz * dz > TRIGGER_RADIUS_SQR) {
            return;
        }
        ensureCabinet(level, coords.x() + OFFSET_X, coords.z());
    }

    /** 落位（幂等）：暗柜已在即跳过。几何：基座 + 暗柜 + 幽光魂灯笼。 */
    static void ensureCabinet(ServerLevel level, int anchorX, int anchorZ) {
        if (!level.isLoaded(new BlockPos(anchorX, 0, anchorZ))) {
            return;
        }
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

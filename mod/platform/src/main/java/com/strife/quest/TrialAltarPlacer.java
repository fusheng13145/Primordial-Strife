package com.strife.quest;

import com.strife.core.PlaceBridges;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 心性试炼台落位器（试炼台批）：玩家走近 {@code qi_trial_altar} 锚点（秘境出生台地）时， 在地表幂等落成 5×5 石砖高台 + 中央试炼台方块 +
 * 四角烛灯。石门落位器同款模式（世界模块的 {@code MijiGatePlacer}），锚点经 core PlaceBridges 取（quest 不 import world，03 §2）。
 */
public final class TrialAltarPlacer {

    private static final int CHECK_INTERVAL_TICKS = 40;
    private static final double TRIGGER_RADIUS_SQR = 64.0 * 64.0;
    private static final String ALTAR_PLACE = "qi_trial_altar";

    private TrialAltarPlacer() {}

    public static void register() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(new TrialAltarPlacer());
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
        if (!level.dimension().location().toString().equals("strife:miji_realm")) {
            return;
        }
        var anchor = PlaceBridges.findPlace(player.server, ALTAR_PLACE);
        if (anchor.isEmpty()) {
            return;
        }
        var coords = anchor.get();
        double dx = player.getX() - (coords.x() + 0.5);
        double dz = player.getZ() - (coords.z() + 0.5);
        if (dx * dx + dz * dz > TRIGGER_RADIUS_SQR) {
            return;
        }
        ensureAltar(level, coords.x(), coords.z());
    }

    /** 落位（幂等）：中央试炼台已在即跳过。 */
    static void ensureAltar(ServerLevel level, int anchorX, int anchorZ) {
        if (!level.isLoaded(new BlockPos(anchorX, 0, anchorZ))) {
            return;
        }
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, anchorX, anchorZ);
        BlockPos origin = new BlockPos(anchorX, surfaceY, anchorZ);
        if (level.getBlockState(origin.above()).is(TrialBlocks.TRIAL_ALTAR.get())) {
            return;
        }
        BlockState brick = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState lantern = Blocks.GLOWSTONE.defaultBlockState();
        // 5×5 高台（半宽 2）
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                level.setBlockAndUpdate(origin.offset(dx, 0, dz), brick);
                for (int dy = 1; dy <= 2; dy++) {
                    if (level.getBlockState(origin.offset(dx, -dy, dz)).isAir()) {
                        level.setBlockAndUpdate(origin.offset(dx, -dy, dz), brick);
                    }
                }
            }
        }
        for (int[] c : new int[][] {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
            level.setBlockAndUpdate(origin.offset(c[0], 1, c[1]), lantern);
        }
        level.setBlockAndUpdate(origin.above(), TrialBlocks.TRIAL_ALTAR.get().defaultBlockState());
    }
}

package com.strife.world;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 秘境入口石门落位器（H4 配套，2026-11-04）：玩家走近 {@code qi_miji_entry} 锚点时， 在地表按 {@link MijiGateGeometry}
 * 幂等落成石门——玄守门的"门"从此存在。
 *
 * <p>与 {@code NpcPlacementSpawner} 同款玩家 tick 近距模式（区块与实体就绪后判定可靠）；幂等判据 = 门芯灵晶
 * 已在（几何其余块被玩家拆了不重建——"被岁月破损的门"比自动复原的门更有秘境感）。
 */
public final class MijiGatePlacer {

    private static final int CHECK_INTERVAL_TICKS = 40;

    /** 触发半径（与 NPC 摆位的近距口径一致）。 */
    private static final double TRIGGER_RADIUS_SQR = 64.0 * 64.0;

    private static final String GATE_PLACE = "qi_miji_entry";

    private MijiGatePlacer() {}

    public static void register() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(new MijiGatePlacer());
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            return;
        }
        if (player.tickCount % CHECK_INTERVAL_TICKS != 0) {
            return;
        }
        Level level = player.level();
        if (!level.dimension().location().toString().equals("minecraft:overworld")) {
            return;
        }
        var anchor = Places.lookup(player.server, GATE_PLACE);
        if (anchor.isEmpty()) {
            return;
        }
        var coords = anchor.get();
        double dx = player.getX() - (coords.x() + 0.5);
        double dz = player.getZ() - (coords.z() + 0.5);
        if (dx * dx + dz * dz > TRIGGER_RADIUS_SQR) {
            return;
        }
        ensureGate((ServerLevel) level, coords.x(), coords.z());
    }

    /** 落位（幂等）：地表取高 → 门芯已存在即跳过 → 按几何铺块。包内可见供后续用例扩展。 */
    static void ensureGate(ServerLevel level, int anchorX, int anchorZ) {
        BlockPos anchor = new BlockPos(anchorX, 0, anchorZ);
        if (!level.isLoaded(anchor)) {
            return;
        }
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, anchorX, anchorZ);
        BlockPos origin = new BlockPos(anchorX, surfaceY, anchorZ);
        if (level.getBlockState(origin.above()).is(StrifeRealmBlocks.QI_CRYSTAL.get())) {
            return; // 门已立
        }
        BlockState platform = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        BlockState pillar = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState lantern = Blocks.GLOWSTONE.defaultBlockState();
        BlockState crystal = StrifeRealmBlocks.QI_CRYSTAL.get().defaultBlockState();
        BlockState base = Blocks.DEEPSLATE.defaultBlockState();
        for (Map.Entry<BlockPos, MijiGateGeometry.Material> e :
                MijiGateGeometry.blocksFor(origin.getX(), origin.getY(), origin.getZ())
                        .entrySet()) {
            BlockState state =
                    switch (e.getValue()) {
                        case PLATFORM -> platform;
                        case PILLAR -> pillar;
                        case LANTERN -> lantern;
                        case CRYSTAL -> crystal;
                        case BASE -> base;
                    };
            level.setBlockAndUpdate(e.getKey(), state);
        }
    }
}

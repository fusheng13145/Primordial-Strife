package com.strife.quest;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 把 {@code tables/npcs.csv} 的摆位落成世界实体：玩家走近时按地表高度生成，已存在则不重复生成。
 *
 * <p>为什么用玩家 tick 而不是区块加载事件：区块加载时实体是否已并入 level 取决于加载阶段， 在这个时点扫描容易把「存档里已有的 NPC」漏掉而重复生成；玩家 tick
 * 的时刻区块与实体都已就绪， 幂等判断可靠。检查每 40 刻一次，只对 64 格内的摆位做小范围实体扫描。
 *
 * <p>剧情 NPC 生成后设为无敌且不 despawn：它们不是战斗单位，死亡会让任务链软锁。
 */
final class NpcPlacementSpawner {

    static final int CHECK_INTERVAL_TICKS = 40;
    private static final int EXISTENCE_SCAN_RADIUS = 8;
    private static final Logger LOGGER = LoggerFactory.getLogger("strife/quest");
    private static boolean loadFailureLogged;

    private NpcPlacementSpawner() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(NpcPlacementSpawner::onPlayerTick);
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % CHECK_INTERVAL_TICKS != 0) {
            return;
        }
        List<NpcPlacements.Placement> placements;
        try {
            placements = NpcPlacements.getOrThrow(player.getServer());
        } catch (RuntimeException e) {
            if (!loadFailureLogged) {
                loadFailureLogged = true;
                LOGGER.error("world NPC placements unavailable: {}", e.getMessage());
            }
            return;
        }
        ServerLevel level = player.serverLevel();
        for (NpcPlacements.Placement placement : placements) {
            if (NpcPlacements.near(
                    placement, level.dimension().location(), player.getX(), player.getZ())) {
                spawnIfAbsent(level, placement);
            }
        }
    }

    private static void spawnIfAbsent(ServerLevel level, NpcPlacements.Placement placement) {
        BlockPos anchor = new BlockPos(placement.x(), 0, placement.z());
        if (!level.isLoaded(anchor)) {
            return;
        }
        AABB scanBox =
                new AABB(
                        placement.x() - EXISTENCE_SCAN_RADIUS,
                        level.getMinBuildHeight(),
                        placement.z() - EXISTENCE_SCAN_RADIUS,
                        placement.x() + EXISTENCE_SCAN_RADIUS + 1,
                        level.getMaxBuildHeight(),
                        placement.z() + EXISTENCE_SCAN_RADIUS + 1);
        boolean present =
                !level.getEntities(
                                StrifeEntities.NPC.get(),
                                scanBox,
                                npc -> placement.id().equals(npc.npcId()))
                        .isEmpty();
        if (present) {
            return;
        }
        StrifeNpcEntity npc = StrifeEntities.NPC.get().create(level);
        if (npc == null) {
            LOGGER.error("cannot create NPC entity for {}", placement.id());
            return;
        }
        int y =
                level.getHeight(
                        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, placement.x(), placement.z());
        npc.setNpcId(placement.id());
        npc.setInvulnerable(true);
        npc.setPersistenceRequired();
        npc.moveTo(placement.x() + 0.5, y, placement.z() + 0.5, 0.0f, 0.0f);
        if (!level.addFreshEntity(npc)) {
            LOGGER.error(
                    "cannot place world NPC {} at {} {} {}",
                    placement.id(),
                    placement.x(),
                    y,
                    placement.z());
            return;
        }
        LOGGER.info(
                "placed world NPC {} at {} {} {} (near {})",
                placement.id(),
                placement.x(),
                y,
                placement.z(),
                placement.place());
    }
}

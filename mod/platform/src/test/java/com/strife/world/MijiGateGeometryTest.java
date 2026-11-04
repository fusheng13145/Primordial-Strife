package com.strife.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/** 秘境入口石门的几何契约（纯函数）：形状是"石门的可辨识度"——门芯居中、四角柱灯、平台闭合、基座支撑。 形状错了玩家看到的就是一堆乱石头。 */
class MijiGateGeometryTest {

    @Test
    void gateHasCrystalCorePillarsAndFullPlatform() {
        var blocks = MijiGateGeometry.blocksFor(0, 64, 0);

        assertEquals(
                MijiGateGeometry.Material.CRYSTAL,
                blocks.get(new BlockPos(0, 65, 0)),
                "门芯灵晶在锚点地表正上方一格——落位器的幂等判据");
        assertEquals(
                45,
                blocks.values().stream()
                        .filter(m -> m == MijiGateGeometry.Material.PLATFORM)
                        .count(),
                "7×7 平台去四角（角位让给柱基）");
        assertEquals(
                12,
                blocks.values().stream().filter(m -> m == MijiGateGeometry.Material.PILLAR).count(),
                "四角柱各 3 高");
        assertEquals(
                4,
                blocks.values().stream()
                        .filter(m -> m == MijiGateGeometry.Material.LANTERN)
                        .count(),
                "柱顶四灯");
        assertEquals(
                147,
                blocks.values().stream().filter(m -> m == MijiGateGeometry.Material.BASE).count(),
                "基座 7×7×3 下填防悬空");
        assertEquals(45 + 12 + 4 + 1 + 147, blocks.size());
    }

    @Test
    void geometryIsOriginTranslatableAndBounded() {
        var atOrigin = MijiGateGeometry.blocksFor(0, 0, 0);
        int ox = 100;
        int oy = 64;
        int oz = -50;
        var shifted = MijiGateGeometry.blocksFor(ox, oy, oz);

        // 平移不变：以各自 origin 归一化后，相对形状与材质完全一致（绝对坐标必不同）
        var normalizedAtOrigin = new java.util.HashMap<BlockPos, MijiGateGeometry.Material>();
        atOrigin.forEach((pos, m) -> normalizedAtOrigin.put(pos, m));
        assertEquals(atOrigin.size(), shifted.size(), "同一几何在任意 origin 下块数一致");
        shifted.forEach(
                (pos, material) -> {
                    var relative = new BlockPos(pos.getX() - ox, pos.getY() - oy, pos.getZ() - oz);
                    assertEquals(
                            normalizedAtOrigin.get(relative),
                            material,
                            "相对位置 " + relative + " 的材质漂移");
                    assertTrue(
                            pos.getX() >= ox - MijiGateGeometry.HALF
                                    && pos.getX() <= ox + MijiGateGeometry.HALF
                                    && pos.getZ() >= oz - MijiGateGeometry.HALF
                                    && pos.getZ() <= oz + MijiGateGeometry.HALF
                                    && pos.getY() >= oy - MijiGateGeometry.BASE_DEPTH
                                    && pos.getY() <= oy + MijiGateGeometry.PILLAR_HEIGHT + 1,
                            "全部方块落在锚点包围盒内");
                });
    }

    @Test
    void crystalIsTheHighestCenterBlock() {
        var blocks = MijiGateGeometry.blocksFor(0, 0, 0);
        int maxY = blocks.keySet().stream().mapToInt(BlockPos::getY).max().orElseThrow();
        assertEquals(MijiGateGeometry.PILLAR_HEIGHT + 1, maxY, "柱顶灯与门芯所在层即最高层——门上无悬空结构");
    }
}

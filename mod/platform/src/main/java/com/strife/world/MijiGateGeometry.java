package com.strife.world;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;

/**
 * 秘境入口石门的几何（纯函数，零世界访问）：环形石台 + 四角柱灯 + 中央灵晶——朝向无关（玩家从任何方向走近都看到同一形态）。
 *
 * <p>为什么不是 StructureSet 等距生成：石门必须钉在 {@code qi_miji_entry} 锚点的精确坐标上（玄守的就是这一座门）， structure set 只能按
 * spacing/separation 撒点。块材是材质属性（04 §7），不进 NUMBERS。
 */
public final class MijiGateGeometry {

    /** 材质档：落位器据此取 BlockState（几何与材质分离，用例钉形状不钉方块）。 */
    public enum Material {
        PLATFORM,
        PILLAR,
        LANTERN,
        CRYSTAL,
        BASE
    }

    /** 平台半宽（7×7）。 */
    static final int HALF = 3;

    /** 柱高（平台以上）。 */
    static final int PILLAR_HEIGHT = 3;

    /** 基座下填深度（防悬崖边悬空）。 */
    static final int BASE_DEPTH = 3;

    private MijiGateGeometry() {}

    /**
     * 门的全量方块（绝对坐标——originX/Y/Z 是锚点地表点，测试钉平移不变性）：49 平台 + 4×3 柱 + 4 灯 + 1 灵晶 + 基座下填。 灵晶在
     * (0,+1,0)——石门的"门芯"， 落位器以它为幂等判据（已生成即跳过）。
     */
    public static Map<BlockPos, Material> blocksFor(int originX, int originY, int originZ) {
        Map<BlockPos, Material> blocks = new LinkedHashMap<>();
        for (int dx = -HALF; dx <= HALF; dx++) {
            for (int dz = -HALF; dz <= HALF; dz++) {
                boolean corner = Math.abs(dx) == HALF && Math.abs(dz) == HALF;
                if (!corner) {
                    blocks.put(
                            new BlockPos(originX + dx, originY, originZ + dz), Material.PLATFORM);
                }
            }
        }
        for (int[] c : new int[][] {{-HALF, -HALF}, {HALF, -HALF}, {-HALF, HALF}, {HALF, HALF}}) {
            for (int dy = 1; dy <= PILLAR_HEIGHT; dy++) {
                blocks.put(
                        new BlockPos(originX + c[0], originY + dy, originZ + c[1]),
                        Material.PILLAR);
            }
            blocks.put(
                    new BlockPos(originX + c[0], originY + PILLAR_HEIGHT + 1, originZ + c[1]),
                    Material.LANTERN);
        }
        blocks.put(new BlockPos(originX, originY + 1, originZ), Material.CRYSTAL);
        for (int dy = 1; dy <= BASE_DEPTH; dy++) {
            for (int dx = -HALF; dx <= HALF; dx++) {
                for (int dz = -HALF; dz <= HALF; dz++) {
                    blocks.put(
                            new BlockPos(originX + dx, originY - dy, originZ + dz), Material.BASE);
                }
            }
        }
        return java.util.Collections.unmodifiableMap(blocks);
    }
}

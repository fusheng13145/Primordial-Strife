package com.strife.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.strife.core.PlaceBridges;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** 雇佣坊市门禁（ADR-036 ②"坊市按次雇佣"的落点）：同维度 + 圈内。 纯函数——坐标与半径都是值对象，不碰服务器。 */
class HireGateTest {

    private static final ResourceLocation OVERWORLD =
            ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
    private static final PlaceBridges.PlaceCoords FANGSHI =
            new PlaceBridges.PlaceCoords(OVERWORLD, "文津坊市", -452, 80, -556);

    @Test
    void sameDimensionInsideRadiusPasses() {
        assertTrue(HireCommand.inHirePlace(OVERWORLD, -452, -556, FANGSHI, 48), "锚点上原地可雇");
        assertTrue(HireCommand.inHirePlace(OVERWORLD, -460, -560, FANGSHI, 48), "圈内可雇");
    }

    @Test
    void outsideRadiusOrWrongDimensionFails() {
        assertFalse(HireCommand.inHirePlace(OVERWORLD, -352, -556, FANGSHI, 48), "出圈拒雇");
        assertFalse(
                HireCommand.inHirePlace(
                        ResourceLocation.fromNamespaceAndPath("minecraft", "the_nether"),
                        -452,
                        -556,
                        FANGSHI,
                        48),
                "异维度拒雇——锚点坐标只在主世界有意义");
    }

    @Test
    void missingPlaceAnchorMeansNoHiring() {
        assertFalse(
                HireCommand.inHirePlace(OVERWORLD, -452, -556, null, 48),
                "places.csv 缺锚点 = 门禁默认拒绝（fail-closed，不缺角放行）");
    }
}

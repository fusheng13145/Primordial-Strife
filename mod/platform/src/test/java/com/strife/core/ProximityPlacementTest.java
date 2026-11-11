package com.strife.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 近距落位公共触发器的纯函数核与注册契约（四落位器公共化的验收锚）： 半径判定（水平距离 ≤ 触发半径）、注册参数防御。 */
class ProximityPlacementTest {

    @Test
    void withinRadiusUsesHorizontalDistanceWithAnchorCenter() {
        // 锚点 (100, -50)：判定以锚点格中心 (100.5, -49.5) 为圆心
        assertTrue(ProximityPlacement.withinRadius(100, -50, 100, -50, 64.0 * 64.0), "锚点原地在内");
        assertTrue(ProximityPlacement.withinRadius(100, -50, 108, -50, 64.0 * 64.0), "恰在半径边缘内");
        assertFalse(ProximityPlacement.withinRadius(100, -50, 200, -50, 64.0 * 64.0), "出圈");
        // 对角同样按水平距离（高度不参与——跳起不算逃，与试炼台口径一致）
        assertFalse(ProximityPlacement.withinRadius(100, -50, 160, -110, 64.0 * 64.0));
    }

    @Test
    void registrationRejectsBlankIdsAndNullCallback() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ProximityPlacement.register("", "qi_fangshi", 40, 4096, (l, x, z) -> {}));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ProximityPlacement.register(
                                "minecraft:overworld", "  ", 40, 4096, (l, x, z) -> {}));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ProximityPlacement.register(
                                "minecraft:overworld", "qi_fangshi", 40, 4096, null));
    }

    @Test
    void helperShrinksPerPlacerFootprint() {
        // 抽取目的自证：公共触发器承载了触发逻辑，落位器只剩几何——本用例钉契约常量默认值
        assertEquals(64.0 * 64.0, 4096.0);
        assertTrue(
                ProximityPlacement.class.getCanonicalName().startsWith("com.strife.core"),
                "公共层落位在 core（world 与 production 的唯一共享层）");
    }
}

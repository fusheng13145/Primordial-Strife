package com.strife.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 地点只读桥（ADR-023 / D-P0-3：core 定义接口，world 注册，quest 消费）的契约用例。
 *
 * <p>关键口径与 {@link RewardBridgesTest} 同源：<b>没注册就是没有这条路</b>——调用返回 empty 且不提供任何"默认实现"，
 * 因为默认实现只能是"永远查不到"，而那正是"看起来接了其实没接"的来源。用例把这条钉住。
 */
class PlaceBridgesTest {

    private static final ResourceLocation OVERWORLD =
            ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");

    @AfterEach
    void tearDown() {
        PlaceBridges.resetForTest();
    }

    @Test
    void withoutALookupNothingIsResolvedAndTheBridgeSaysSo() {
        assertFalse(PlaceBridges.placeWired());
        assertTrue(
                PlaceBridges.findPlace(null, "qi_luoxia").isEmpty(),
                "没有实现时不得编造地点——那会让追踪条画出一个不存在的方向");
        assertTrue(PlaceBridges.findPlace(null, null).isEmpty(), "空 ID 也不得打崩调用方");
    }

    @Test
    void registeredLookupReceivesTheCall() {
        PlaceBridges.registerPlaceLookup(
                (server, id) ->
                        "qi_luoxia".equals(id)
                                ? Optional.of(
                                        new PlaceBridges.PlaceCoords(
                                                OVERWORLD, "落霞山麓", 256, 70, 256))
                                : Optional.empty());

        assertTrue(PlaceBridges.placeWired());
        PlaceBridges.PlaceCoords coords = PlaceBridges.findPlace(null, "qi_luoxia").orElseThrow();
        assertEquals("落霞山麓", coords.name());
        assertEquals(OVERWORLD, coords.dimension());
        assertEquals(256, coords.x());
        assertTrue(
                PlaceBridges.findPlace(null, "qi_missing").isEmpty(),
                "未知地点必须返回 empty，调用方据此降级而不是画一个假方向");
        assertTrue(PlaceBridges.findPlace(null, null).isEmpty(), "空 ID 不得穿透到实现");
    }

    @Test
    void duplicateRegistrationIsRejected() {
        PlaceBridges.registerPlaceLookup((server, id) -> Optional.empty());

        assertThrows(
                IllegalStateException.class,
                () -> PlaceBridges.registerPlaceLookup((server, id) -> Optional.empty()),
                "两个模块抢同一注册项是设计错误，必须响而不是静默覆盖");
    }

    @Test
    void nullImplementationIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PlaceBridges.registerPlaceLookup(null),
                "null 注册会让 placeWired 误报已接线");
    }
}

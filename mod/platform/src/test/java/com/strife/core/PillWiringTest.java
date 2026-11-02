package com.strife.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.DataResult;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

/** 丹药接线批的 core 件契约：突破丹力附件的编解码与语义（不叠层/负值钳位）、神识上限公式唯一落点、 神识桥的注册/路由/未接线拒绝。 */
class PillWiringTest {

    // ═══ BreakthroughAidState ═══

    private static Tag encode(BreakthroughAidState state) {
        DataResult<Tag> result = BreakthroughAidState.CODEC.encodeStart(NbtOps.INSTANCE, state);
        return result.result().orElseThrow(() -> new AssertionError("encode failed: " + result));
    }

    @Test
    void breakthroughAidRoundTrips() {
        BreakthroughAidState state = new BreakthroughAidState(0.10, 0.10);
        DataResult<BreakthroughAidState> decoded =
                BreakthroughAidState.CODEC
                        .decode(NbtOps.INSTANCE, encode(state))
                        .map(pair -> pair.getFirst());
        assertEquals(state, decoded.result().orElseThrow());
    }

    @Test
    void negativeBonusesClampToZero() {
        assertEquals(0.0, new BreakthroughAidState(-0.5, 0.1).successBonus(), 1e-9);
        assertEquals(0.0, new BreakthroughAidState(0.1, -0.5).failKeepBonus(), 1e-9);
        assertFalse(new BreakthroughAidState(0, 0).present(), "全零 = 无丹力");
        assertTrue(new BreakthroughAidState(0, 0.1).present());
    }

    // ═══ ShenShiState.maxFor（公式唯一落点）═══

    @Test
    void shenShiMaxGrowsPerRealmOrdinal() {
        assertEquals(10, ShenShiState.maxFor(0, 10, 5), "凡人 = base");
        assertEquals(35, ShenShiState.maxFor(5, 10, 5));
        assertEquals(10, ShenShiState.maxFor(-1, 10, 5), "非法序数按 0 处理，不产生负上限");
    }

    // ═══ ShenShiBridge ═══
    // 桥的全局静态状态在每个用例前清空（resetForTest 包内可见）；路由路径需要真实 ServerPlayer，
    // 单测覆盖不了——运行时路由由凝神丹服用链验证，这里钉注册与拒绝语义。

    @org.junit.jupiter.api.BeforeEach
    void resetBridge() {
        ShenShiBridge.resetForTest();
    }

    @Test
    void unregisteredBridgeMeansNoDataNotADefault() {
        // 桥未接线回 -1：凝神丹据此诚实拒绝（不猜默认上限）——-1 是契约值不是错误；null 玩家同样 -1
        assertEquals(-1, ShenShiBridge.maxFor(null));
    }

    @Test
    void duplicateRegistrationIsADesignError() {
        ShenShiBridge.registerMaxProvider(player -> 1);
        assertThrows(
                IllegalStateException.class, () -> ShenShiBridge.registerMaxProvider(player -> 2));
        ShenShiBridge.resetForTest();
    }

    @Test
    void nullProviderRejectedAtRegistration() {
        assertThrows(IllegalArgumentException.class, () -> ShenShiBridge.registerMaxProvider(null));
    }
}

package com.strife.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/** 服务端驱动 UI 桥的契约：注册/路由/拒绝——没注册就等于没有这条路（命令通道兜底）。 */
class StrifeUiBridgeTest {

    @Test
    void routesDataToTheRegisteredOpener() {
        CompoundTag sent = new CompoundTag();
        sent.putString("k", "v");
        CompoundTag[] received = new CompoundTag[1];
        StrifeUiBridge.registerOpener(
                "test_screen",
                data -> {
                    received[0] = data;
                    return true;
                });

        assertTrue(StrifeUiBridge.open("test_screen", sent));
        assertEquals("v", received[0].getString("k"));
        assertTrue(StrifeUiBridge.wiredScreens().contains("test_screen"));
    }

    @Test
    void duplicateRegistrationIsADesignError() {
        StrifeUiBridge.registerOpener("dup_screen", data -> true);
        assertThrows(
                IllegalStateException.class,
                () -> StrifeUiBridge.registerOpener("dup_screen", data -> true),
                "两个屏抢同一个 id 必须在启动期炸出来");
    }

    @Test
    void unregisteredScreenMeansNoPath() {
        assertFalse(StrifeUiBridge.open("never_registered", new CompoundTag()));
        StrifeUiBridge.registerOpener("null_data_screen", data -> true);
        assertFalse(StrifeUiBridge.open("null_data_screen", null), "null 数据不开屏");
    }

    @Test
    void malformedRegistrationArgumentsAreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> StrifeUiBridge.registerOpener(null, d -> true));
        assertThrows(
                IllegalArgumentException.class,
                () -> StrifeUiBridge.registerOpener("  ", d -> true));
        assertThrows(
                IllegalArgumentException.class,
                () -> StrifeUiBridge.registerOpener("no_opener", null));
    }
}

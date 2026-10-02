package com.strife.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

/**
 * 跨维度导航的维度解析（{@link RealmCommand#resolveTarget}，G-4 任务导航复用的维度 API）。
 *
 * <p>不依赖服务器：ResourceKey 是纯值对象，导航名 → key 的分支在这里全量覆盖——上界 key 必须与 {@link StrifeWorld#UPPER_REALM}
 * 同源（写成别的 ID 时 {@code getLevel} 返回 null，玩家端表现为 「维度未注册」，这条用例让错误在门禁期就红）。
 */
class RealmCommandTest {

    @Test
    void upperResolvesToTheStrifeUpperRealm() {
        assertEquals(StrifeWorld.UPPER_REALM, RealmCommand.resolveTarget("upper"));
        assertEquals(
                ResourceLocation.fromNamespaceAndPath("strife", "upper_realm"),
                RealmCommand.resolveTarget("upper").location());
    }

    @Test
    void overworldResolvesToTheVanillaOverworld() {
        assertEquals(Level.OVERWORLD, RealmCommand.resolveTarget("overworld"));
    }

    @Test
    void namesAreCaseInsensitive() {
        assertEquals(StrifeWorld.UPPER_REALM, RealmCommand.resolveTarget("UPPER"));
        assertEquals(Level.OVERWORLD, RealmCommand.resolveTarget("OverWorld"));
    }

    @Test
    void unknownNamesReturnNullForTheCallerToReport() {
        assertNull(RealmCommand.resolveTarget("nether"));
        assertNull(RealmCommand.resolveTarget(""));
    }
}

package com.strife.core;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * 地点只读查询的跨模块桥（docs/03 §2：quest 与 world 不得横向依赖；ADR-023 / D-P0-3 的 SPI 落点）。
 *
 * <p>任务目标 {@code type=reach;target=<地点 ID>}（JSON_SCHEMA §4.17）需要地点坐标与维度做导航，但数据在 world 的 {@code
 * strife_places} 产物里，quest 够不到。照 {@link RewardBridges} 的做法，core 只定义结构与读写入口：world 注册实现， quest
 * 调用，谁也不认识谁。
 *
 * <p><b>只读</b>：接口只回答「这个地点在哪、叫什么」，不提供任何写路径。places 的另一个消费者（灵气地点差异化）不走此桥—— 那是 world 自己的内部闭环（{@code
 * CultivationFactors.registerEnvironment}）。
 *
 * <p><b>没注册就等于没有这条路</b>：调用返回 empty，调用方据此降级（不画导航行、显示名退回 region 键）；刻意不提供默认实现，
 * 因为默认实现只能是"永远查不到"——那正是本项目要清掉的那种"看起来接了其实没接"。
 */
public final class PlaceBridges {

    /**
     * 地点只读快照：维度 + 玩家可见名 + 坐标。
     *
     * <p>{@code name} 来自 places 表的中文 {@code name} 列（JSON_SCHEMA §4.17：地点少而固定，<b>不入 lang</b>、直接写表）——
     * 追踪条显示 reach 目标名必须用这里，不能退而造一套 {@code region.strife.*} 词条造成第二真相源。
     */
    public record PlaceCoords(ResourceLocation dimension, String name, int x, int y, int z) {}

    /** 按内容 ID 查地点；未知 ID / 数据不可用返回 empty（调用方决定怎么向玩家呈现）。 */
    @FunctionalInterface
    public interface PlaceLookup {
        Optional<PlaceCoords> find(MinecraftServer server, String placeId);
    }

    private static volatile PlaceLookup placeLookup;

    private PlaceBridges() {}

    /** 注册地点查询实现；重复注册是设计错误（两个模块抢同一项）。 */
    public static synchronized void registerPlaceLookup(PlaceLookup lookup) {
        if (lookup == null) {
            throw new IllegalArgumentException("place lookup must not be null");
        }
        if (placeLookup != null) {
            throw new IllegalStateException("place lookup already registered");
        }
        placeLookup = lookup;
    }

    /** 是否已有地点查询实现（调用方据此走降级路径）。 */
    public static boolean placeWired() {
        return placeLookup != null;
    }

    /** 查地点；未接入、未知 ID 或空 ID 都返回 empty（调用方负责降级，不抛错——导航是锦上添花，不能打崩追踪条）。 */
    public static Optional<PlaceCoords> findPlace(MinecraftServer server, String placeId) {
        PlaceLookup lookup = placeLookup;
        return lookup == null || placeId == null ? Optional.empty() : lookup.find(server, placeId);
    }

    /** 仅测试用：清空接线（注册是全局状态）。 */
    static synchronized void resetForTest() {
        placeLookup = null;
    }
}

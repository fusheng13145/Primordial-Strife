package com.strife.core;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 近距落位触发器（公共工具层，2026-11-11 抽取）：玩家走近地点锚点时，按地表高度幂等落成结构—— 此前石门/坊市告示柱/拍卖告示柱/黑市暗柜四个落位器各自复制了同一段触发逻辑（玩家
 * tick 降频、 维度守卫、PlaceBridges 锚点解析、半径判定、区块就绪），公共化后每个落位器只剩自己的几何。
 *
 * <p>为何放 core：落位器分属 world（石门）与 production（看板三柱），两模块共用的唯一合法层就是 core （03 §2
 * 依赖方向单向，ModuleDependencyRules 门禁强制）；锚点解析本就走 core 的 PlaceBridges 桥。
 *
 * <p>契约：{@code ensure} 在服务端主线程、锚点区块已加载时被调用——实现方只管几何与幂等（判据自选， 惯例 = 特征方块已在即跳过）；触发不保证顺序，多次触发必须无害。
 */
public final class ProximityPlacement {

    private static final int DEFAULT_INTERVAL_TICKS = 40;
    private static final double DEFAULT_RADIUS_SQR = 64.0 * 64.0;

    @FunctionalInterface
    public interface Ensure {
        /** 落位回调：锚点区块已加载。实现必须幂等（重复调用无害）。 */
        void place(ServerLevel level, int anchorX, int anchorZ);
    }

    private ProximityPlacement() {}

    /** 默认参数注册：40 刻降频、64 格触发半径。 */
    public static void register(String dimensionId, String placeId, Ensure ensure) {
        register(dimensionId, placeId, DEFAULT_INTERVAL_TICKS, DEFAULT_RADIUS_SQR, ensure);
    }

    /** 注册一个近距落位（dimensionId/placeId 均为内容字符串；锚点经 PlaceBridges 解析）。 */
    public static void register(
            String dimensionId,
            String placeId,
            int intervalTicks,
            double radiusSqr,
            Ensure ensure) {
        if (dimensionId == null || dimensionId.isBlank()) {
            throw new IllegalArgumentException("dimension id must not be blank");
        }
        if (placeId == null || placeId.isBlank()) {
            throw new IllegalArgumentException("place id must not be blank");
        }
        if (ensure == null) {
            throw new IllegalArgumentException("ensure callback must not be null");
        }
        NeoForge.EVENT_BUS.register(
                new Listener(dimensionId, placeId, intervalTicks, radiusSqr, ensure));
    }

    /** 半径判定（纯函数，用例直钉）：水平距离平方 ≤ 触发半径平方。 */
    public static boolean withinRadius(
            double playerX, double playerZ, double anchorX, double anchorZ, double radiusSqr) {
        double dx = playerX - (anchorX + 0.5);
        double dz = playerZ - (anchorZ + 0.5);
        return dx * dx + dz * dz <= radiusSqr;
    }

    /** 事件监听（每注册一份；零实例状态，触发逻辑全部无副作用直至回调）。 */
    private static final class Listener {
        private final String dimensionId;
        private final String placeId;
        private final int intervalTicks;
        private final double radiusSqr;
        private final Ensure ensure;

        Listener(
                String dimensionId,
                String placeId,
                int intervalTicks,
                double radiusSqr,
                Ensure ensure) {
            this.dimensionId = dimensionId;
            this.placeId = placeId;
            this.intervalTicks = intervalTicks;
            this.radiusSqr = radiusSqr;
            this.ensure = ensure;
        }

        @SubscribeEvent
        public void onPlayerTick(PlayerTickEvent.Post event) {
            if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
                return;
            }
            if (player.tickCount % intervalTicks != 0) {
                return;
            }
            ServerLevel level = player.serverLevel();
            if (!level.dimension().location().toString().equals(dimensionId)) {
                return;
            }
            var anchor = PlaceBridges.findPlace(player.server, placeId);
            if (anchor.isEmpty()) {
                return;
            }
            var coords = anchor.get();
            if (!ProximityPlacement.withinRadius(
                    player.getX(), player.getZ(), coords.x(), coords.z(), radiusSqr)) {
                return;
            }
            net.minecraft.core.BlockPos anchorPos =
                    new net.minecraft.core.BlockPos(coords.x(), 0, coords.z());
            if (!level.isLoaded(anchorPos)) {
                return;
            }
            ensure.place(level, coords.x(), coords.z());
        }
    }
}

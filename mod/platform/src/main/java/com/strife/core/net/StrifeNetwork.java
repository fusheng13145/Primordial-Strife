package com.strife.core.net;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 网络注册与收发出口（docs/03 §4/§5）。本类只做三件事：注册载荷、把收到的包派给纯逻辑、把出站包交给 NeoForge。
 *
 * <p>线程：两侧都经 {@link IPayloadContext#enqueueWork} 回到主线程再动游戏状态——NeoForge 的默认处理线程是网络线程， 在那里碰附件或
 * SavedData 是数据竞争，而且这类 bug 只在高延迟下偶发。
 *
 * <p>通道版本：{@link #PROTOCOL_VERSION} 是载荷契约版本（字段位序、包类型集合）。它变了就必须跟着改版本号，否则 老客户端会按旧位序解新包——那是最难查的一类联机事故。
 */
public final class StrifeNetwork {

    /** 载荷契约版本：新增包类型或改字段位序时 +1（{@link StrifeSyncField} 的注释里有同一条契约）。 */
    public static final String PROTOCOL_VERSION = "1";

    private StrifeNetwork() {}

    /** 在 mod 事件总线上注册全部载荷（由 {@code StrifeMod} 调用）。 */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(StrifeNetwork::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(
                StrifeSnapshotPayload.TYPE,
                StrifeSnapshotPayload.STREAM_CODEC,
                StrifeNetwork::onSnapshot);
        registrar.playToClient(
                StrifeDeltaPayload.TYPE, StrifeDeltaPayload.STREAM_CODEC, StrifeNetwork::onDelta);
        registrar.playToClient(
                StrifeRealmViewPayload.TYPE,
                StrifeRealmViewPayload.STREAM_CODEC,
                StrifeNetwork::onRealmView);
        registrar.playToServer(
                StrifeIntentPayload.TYPE,
                StrifeIntentPayload.STREAM_CODEC,
                StrifeNetwork::onIntent);
    }

    /** 客户端收到面板视图（拉取式响应）：整体替换镜像里的视图。 */
    private static void onRealmView(StrifeRealmViewPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> StrifeClientMirror.accept(payload.view()));
    }

    /** 客户端收到全量快照：整体替换镜像。 */
    private static void onSnapshot(StrifeSnapshotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> StrifeClientMirror.accept(payload.data()));
    }

    /** 客户端收到差量：施加到镜像上。 */
    private static void onDelta(StrifeDeltaPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> StrifeClientMirror.accept(payload.delta()));
    }

    /** 服务端收到意图：交给统一校验入口，不在这里做业务判断。 */
    private static void onIntent(StrifeIntentPayload payload, IPayloadContext context) {
        context.enqueueWork(
                () -> {
                    if (context.player() instanceof ServerPlayer player) {
                        StrifeIntents.dispatch(player, payload.intentId(), payload.args());
                    }
                });
    }

    /** 向单个玩家发包（服务端调用）。 */
    public static void sendTo(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }
}

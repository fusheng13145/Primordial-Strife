package com.strife.quest.dialog;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 对话载荷的注册与收发派发（docs/03 §4）。放 quest 而不放 core.net：core 是被依赖方，不能反向 import quest （03 §2 依赖方向）——协议版本沿用
 * {@code StrifeNetwork.PROTOCOL_VERSION}（同一条通道、同一个版本号，载荷类型集合变了 必须一起 +1）。
 *
 * <p>收发纪律与 core 同款：两侧都经 {@code enqueueWork} 回主线程再动会话状态；C2S 无会话的包直接丢弃（fail-closed）。
 */
public final class DialogNetwork {

    private DialogNetwork() {}

    /** 在 mod 事件总线上注册载荷与登出清理（由 {@code StrifeMod} 调用）。 */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(DialogNetwork::onRegisterPayloads);
        NeoForge.EVENT_BUS.addListener(DialogSessions::onLoggedOut);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(
                DialogPayloads.Open.TYPE, DialogPayloads.Open.STREAM_CODEC, DialogNetwork::onOpen);
        registrar.playToServer(
                DialogPayloads.Choose.TYPE,
                DialogPayloads.Choose.STREAM_CODEC,
                DialogNetwork::onChoose);
    }

    /** S2C：写 common 镜像（纯数据），client_fx 的 tick 轮询负责开/推进 Screen——与 StrifeClientMirror 同款隔离。 */
    private static void onOpen(DialogPayloads.Open payload, IPayloadContext context) {
        context.enqueueWork(() -> DialogClientMirror.accept(payload));
    }

    private static void onChoose(DialogPayloads.Choose payload, IPayloadContext context) {
        context.enqueueWork(
                () -> {
                    if (context.player() instanceof ServerPlayer player) {
                        DialogSessions.choose(player, payload.chooseIndex());
                    }
                });
    }
}

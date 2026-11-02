package com.strife.core.net;

import com.strife.core.StrifeMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C 数据屏信封（炉交互屏，ADR-038 交互层）：服务端组好数据下发，客户端按 {@code screenId} 路由到 {@link
 * com.strife.core.StrifeUiBridge} 注册的开启器——"客户端只是显示器"（03 §4）的 UI 落法。
 *
 * <p>与 {@link StrifeIntentPayload} 同一取舍：信封 + 数据袋而不是每屏一个包类型， 新屏不必改协议版本；代价是数据袋无 schema，因此发送侧的纪律是
 * <b>每个域自己钉住键名并用用例锚定</b>（AlchemyUiTest 对真实配方产物锚定 tag 形状）。
 */
public record StrifeUiPayload(String screenId, CompoundTag data) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StrifeUiPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(StrifeMod.MOD_ID, "ui_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StrifeUiPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8,
                    StrifeUiPayload::screenId,
                    ByteBufCodecs.COMPOUND_TAG,
                    StrifeUiPayload::data,
                    StrifeUiPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

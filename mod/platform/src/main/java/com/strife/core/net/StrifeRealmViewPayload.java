package com.strife.core.net;

import com.strife.core.StrifeMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C 面板视图包（03 §5"改拉取式：C2S 请求 → S2C 响应"）。
 *
 * <p>线格式用 NBT 而不是逐字段手写：视图字段会随面板需求增删，逐字段编码意味着每次加一个显示项都要同时改两侧的编解码顺序 并且把版本号 +1；NBT
 * 让新增字段天然向后兼容（缺字段读到默认值），协议只在"字段语义变更"时才需要动版本。
 */
public record StrifeRealmViewPayload(StrifeRealmView view) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StrifeRealmViewPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(StrifeMod.MOD_ID, "realm_view"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StrifeRealmViewPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.COMPOUND_TAG,
                    payload -> payload.view().toTag(),
                    tag -> new StrifeRealmViewPayload(StrifeRealmView.fromTag(tag)));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

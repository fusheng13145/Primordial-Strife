package com.strife.core.net;

import com.strife.core.StrifeData;
import com.strife.core.StrifeMod;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C 全量快照：登录/重连/周期性重同步时把权威数据整体推给客户端（docs/03 §5）。
 *
 * <p>线格式<b>直接复用存档用的 {@link StrifeData#CODEC}</b>，不另写一份网络 codec：两份 schema 一定会漂移，而漂移的表现是
 * "存档正常、联机字段错位"这种最难查的 bug。ByteBufCodecs 从 Codec 生成流式编码，两边天然同步。
 */
public record StrifeSnapshotPayload(StrifeData data) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StrifeSnapshotPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(StrifeMod.MOD_ID, "data_snapshot"));

    /** {@link StrifeData} 的流式编解码（快照与将来的拉取式响应共用）。 */
    public static final StreamCodec<RegistryFriendlyByteBuf, StrifeData> DATA_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(StrifeData.CODEC, NbtAccounter::unlimitedHeap);

    public static final StreamCodec<RegistryFriendlyByteBuf, StrifeSnapshotPayload> STREAM_CODEC =
            StreamCodec.composite(
                    DATA_CODEC, StrifeSnapshotPayload::data, StrifeSnapshotPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

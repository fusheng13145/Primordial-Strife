package com.strife.core.net;

import com.strife.core.StrifeMod;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C 字段差量（docs/03 §5）：只有变化的字段上行，静止时 0 包。
 *
 * <p>编码顺序必须与 {@link StrifeSyncField} 的枚举顺序一致——两侧都按同一枚举遍历掩码，因此"哪些槽位在包里"由掩码决定，
 * 不写任何下标常量。新增字段时只改枚举，编解码自动跟上。
 */
public record StrifeDeltaPayload(StrifeDelta delta) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StrifeDeltaPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(StrifeMod.MOD_ID, "data_delta"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StrifeDeltaPayload> STREAM_CODEC =
            StreamCodec.of(StrifeDeltaPayload::encode, StrifeDeltaPayload::decode);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, StrifeDeltaPayload payload) {
        StrifeDelta delta = payload.delta();
        buffer.writeVarLong(delta.mask());
        for (StrifeSyncField field : StrifeSyncField.values()) {
            if (field.scalar() && delta.has(field)) {
                buffer.writeVarLong(delta.scalarValues()[field.ordinal()]);
            }
        }
        buffer.writeBoolean(delta.has(StrifeSyncField.AFFILIATION));
        if (delta.has(StrifeSyncField.AFFILIATION)) {
            buffer.writeUtf(delta.affiliation() == null ? "" : delta.affiliation());
        }
        buffer.writeBoolean(delta.reputationPartial());
        buffer.writeMap(
                delta.reputation(),
                (buf, key) -> buf.writeUtf(key),
                (buf, value) -> buf.writeVarInt(value));
    }

    private static StrifeDeltaPayload decode(RegistryFriendlyByteBuf buffer) {
        long mask = buffer.readVarLong();
        long[] values = new long[StrifeSyncField.COUNT];
        for (StrifeSyncField field : StrifeSyncField.values()) {
            if (field.scalar() && (mask & field.bit()) != 0L) {
                values[field.ordinal()] = buffer.readVarLong();
            }
        }
        String affiliation = buffer.readBoolean() ? buffer.readUtf() : null;
        boolean reputationPartial = buffer.readBoolean();
        Map<String, Integer> reputation =
                buffer.readMap(buf -> buf.readUtf(), buf -> buf.readVarInt());
        return new StrifeDeltaPayload(
                new StrifeDelta(mask, values, affiliation, reputation, reputationPartial));
    }
}

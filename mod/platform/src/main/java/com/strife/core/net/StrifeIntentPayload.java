package com.strife.core.net;

import com.strife.core.StrifeMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S 意图包（docs/03 §4）：客户端只表达"我想做什么"，数值与判定全在服务端。
 *
 * <p>载荷刻意是"意图 id + 参数袋"的信封形态，而不是每件事一个包类型：新增意图不必改协议版本，也不必让 realm/quest 去碰 core 的注册管线（03 §2 跨模块只走事件或
 * core 接口）。代价是参数袋无 schema，因此服务端侧只有两条纪律：
 *
 * <ol>
 *   <li>意图 id 必须在 {@link StrifeIntents} 登记过，否则丢弃（fail-closed）；
 *   <li>参数袋大小受 NUMBERS {@code @@limits.intent_args_max_bytes} 约束，超限直接丢——03 §4"不得信任客户端上报"，
 *       连"客户端塞一个巨大 NBT"这种最低成本的上报也不能收。
 * </ol>
 */
public record StrifeIntentPayload(String intentId, CompoundTag args)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StrifeIntentPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(StrifeMod.MOD_ID, "intent"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StrifeIntentPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8,
                    StrifeIntentPayload::intentId,
                    ByteBufCodecs.COMPOUND_TAG,
                    StrifeIntentPayload::args,
                    StrifeIntentPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** 参数袋字节数（服务端拒收判据）。 */
    public int argsSizeBytes() {
        return args == null ? 0 : args.sizeInBytes();
    }
}

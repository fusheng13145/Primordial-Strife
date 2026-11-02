package com.strife.core;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * strife 数据附件类型注册（03 分册 §3）。
 *
 * <p>AttachmentType 必须注册到 {@link NeoForgeRegistries.Keys#ATTACHMENT_TYPES} 注册表 （NeoForge 21.1 起没有
 * attach 事件，注册表是唯一入口）。
 *
 * <p>玩家数据只此一个聚合附件 {@link #PLAYER_DATA}：所有子系统的玩家态都进 {@link StrifeData}，
 * 避免"同一玩家多个附件各自序列化"造成的档位不一致。世界级数据（灵气场缓存、宗门状态）走 {@code SavedData}； 例外见 {@link
 * #AUCTION_BIDS}（全服小状态沿附件管线，ADR-041 §5）。
 */
public final class StrifeAttachmentTypes {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, StrifeMod.MOD_ID);

    /**
     * 玩家侧聚合数据；序列化由 {@link StrifeData#CODEC} 负责（服务端权威，客户端只读镜像在 M1 接）。
     *
     * <p>{@code copyOnDeath()} 是<b>必须</b>的而非可选：NeoForge 21.1 在 {@code PlayerEvent.Clone} 里 只复制声明了
     * serializer 且 {@code copyOnDeath} 的附件，缺这一行就违反 MVP 用例 "死亡不掉修为但掉灵石"（07 分册 §4）。
     */
    /** 神识（EP1，ADR-036 ⑦）：独立附件——不动 StrifeData 2048B 预算。 */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ShenShiState>> SHENSHI =
            ATTACHMENT_TYPES.register(
                    "shenshi",
                    () ->
                            AttachmentType.builder(() -> ShenShiState.initial(10))
                                    .serialize(ShenShiState.CODEC)
                                    .copyOnDeath()
                                    .build());

    /** 心魔（EP2，ADR-040）：独立附件——突破失败触发，洗尘丹清除。 */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<HeartDemonState>>
            HEART_DEMON =
                    ATTACHMENT_TYPES.register(
                            "heart_demon",
                            () ->
                                    AttachmentType.builder(() -> HeartDemonState.EMPTY)
                                            .serialize(HeartDemonState.CODEC)
                                            .copyOnDeath()
                                            .build());

    /**
     * 拍卖密封投标（ADR-041 第三批）：全服态挂 overworld Level 附件（随 level.dat 持久化）。 上面 javadoc 说"世界级数据走
     * SavedData"是一般规则；这里是例外——出价状态与玩家附件同构（小状态 + codec）， 沿用附件管线省一套 SavedData I/O 样板，取舍已在 ADR-041 §5
     * 披露。
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<AuctionBids>>
            AUCTION_BIDS =
                    ATTACHMENT_TYPES.register(
                            "auction_bids",
                            () ->
                                    AttachmentType.builder(() -> AuctionBids.EMPTY)
                                            .serialize(AuctionBids.CODEC)
                                            .build());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<StrifeData>> PLAYER_DATA =
            ATTACHMENT_TYPES.register(
                    "player_data",
                    () ->
                            AttachmentType.builder(StrifeData::newPlayer)
                                    .serialize(StrifeData.CODEC)
                                    .copyOnDeath()
                                    .build());

    private StrifeAttachmentTypes() {}
}

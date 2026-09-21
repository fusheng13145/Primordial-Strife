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
 * 避免"同一玩家多个附件各自序列化"造成的档位不一致。世界级数据（灵气场缓存、宗门状态）走 {@code SavedData}，不在这里。
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

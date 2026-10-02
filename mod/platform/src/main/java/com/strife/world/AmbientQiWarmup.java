package com.strife.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.neoforged.neoforge.event.level.ChunkEvent;

/**
 * 灵气场区块预热（docs/03 §6 性能红线"新区块灵气场生成 ≤2ms/chunk"）。
 *
 * <p><b>为什么需要这个类</b>：红线写的是"<b>区块生成期</b>计算"，但在没有本挂钩的版本里，灵气场只在玩家查询 （{@link
 * StrifeWorld#environmentCoefficient}）时才惰性求值。后果是：玩家走向一片从未加载过的区域时，
 * 该区块的灵气值会在<b>查询那一刻</b>现算，粗粒度噪声采样落在查询线程上——这正是红线想避免的"生成期尖峰出现在别处"。
 *
 * <p><b>为什么挂在 {@code ChunkEvent.Load} 而不是 {@code ChunkDataEvent}</b>：后者在区块的 NBT/序列化数据阶段触发，
 * 那时区块可能仍未进入可查询状态；{@code Load} 是"区块已可被游戏逻辑访问"的时点，与红线的"新区块灵气场生成"语义一致。 只处理 {@link
 * ChunkEvent.Load#isNewChunk()} 为真的事件——重新加载既有区块（存档重开、区块卸载再加载）不需要预热， 那些值要么已在缓存里，要么重建也是懒路径。
 *
 * <p><b>成本</b>：一次 {@code ratioAt} 只做一次细化层噪声采样（粗粒度层按 16×16 区块缓存，区域内已缓存即命中）。
 * 预热把这次成本<b>提前</b>到区块加载期，之后玩家的查询走缓存命中（≈0），符合红线的"命中缓存 ≈0"。
 *
 * <p><b>失败不阻断</b>：表不可用时 {@link StrifeWorld#fieldFor} 返回 {@code null} 并只报一次 ERROR，
 * 本挂钩照常安静返回——世界生成期绝不能因为附加逻辑抛异常。
 */
final class AmbientQiWarmup {

    private AmbientQiWarmup() {}

    /** 区块加载时把该区块的环境系数算进缓存。事件在主线程触发，这里不做任何 IO。 */
    static void onChunkLoad(ChunkEvent.Load event) {
        if (!event.isNewChunk()) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return; // 客户端侧不参与：灵气是服务端权威（ADR-004）
        }
        AmbientQiField field = StrifeWorld.fieldFor(level);
        if (field == null) {
            return; // fieldFor 已记过一次 ERROR，这里不重复刷日志
        }
        ChunkAccess chunk = event.getChunk();
        field.ratioAt(chunk.getPos().x, chunk.getPos().z);
    }
}

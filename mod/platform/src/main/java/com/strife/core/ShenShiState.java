package com.strife.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * 神识状态（EP1，ADR-036 ⑦）：第二资源池——独立于修为的感知资源。
 *
 * <p>为什么独立附件而非塞进 StrifeData：神识有自己的恢复节拍（按现实分钟）、 不同的上限派生（境界+基础），且 2048B 预算内 StrifeData 已接近上限——
 * 独立附件更干净（03 §3：单玩家全部附件 ≤2048B，StrifeData 不加压）。
 *
 * <p>持久化由 Codec 落 NBT 随附件走，与 PillState/MeditationState 同模式。
 */
public record ShenShiState(int value, long lastRegenTick) {

    public static final Codec<ShenShiState> CODEC =
            RecordCodecBuilder.create(
                    inst ->
                            inst.group(
                                            Codec.INT
                                                    .fieldOf("value")
                                                    .forGetter(ShenShiState::value),
                                            Codec.LONG
                                                    .optionalFieldOf("last_regen_tick", 0L)
                                                    .forGetter(ShenShiState::lastRegenTick))
                                    .apply(inst, ShenShiState::new));

    /** 初始态：满值（首次登录给满，让玩家立刻能体验探查）。 */
    public static ShenShiState initial(int max) {
        return new ShenShiState(max, 0L);
    }

    /** 消耗后状态；不足返回 null（调用方拒绝）。 */
    public ShenShiState consume(int cost) {
        if (value < cost) {
            return null;
        }
        return new ShenShiState(value - cost, lastRegenTick);
    }

    /** 恢复后状态（自然回复，按分钟计，不超过上限）。 */
    public ShenShiState regen(long nowTick, int max, int perMinute) {
        long elapsed = nowTick - lastRegenTick;
        if (elapsed < 20L * 60) {
            return this; // 不足 1 分钟
        }
        int recovered = (int) (elapsed / (20L * 60)) * perMinute;
        if (recovered <= 0) {
            return this;
        }
        return new ShenShiState(Math.min(max, value + recovered), nowTick);
    }
}

package com.strife.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * 心魔状态（EP2，ADR-040）：突破失败时可能触发的心魔 debuff。
 *
 * <p>独立附件（同 ShenShiState 模式，不动 StrifeData 2048B 预算）。 叠层数制：连续突破失败累积最多 3 层，每层修为速率 -15%。
 * 洗尘丹（pill_wugou）可清除全部层数。
 */
public record HeartDemonState(int stacks, long expiryTick) {

    public static final Codec<HeartDemonState> CODEC =
            RecordCodecBuilder.create(
                    inst ->
                            inst.group(
                                            Codec.INT
                                                    .fieldOf("stacks")
                                                    .forGetter(HeartDemonState::stacks),
                                            Codec.LONG
                                                    .fieldOf("expiry_tick")
                                                    .forGetter(HeartDemonState::expiryTick))
                                    .apply(inst, HeartDemonState::new));

    public static HeartDemonState EMPTY = new HeartDemonState(0, 0L);

    /** 是否有心魔活跃。 */
    public boolean active() {
        return stacks > 0;
    }

    /** 触发/叠加一层心魔。 */
    public HeartDemonState trigger(long nowTick, long durationTicks, int maxStacks) {
        int newStacks = Math.min(maxStacks, stacks + 1);
        return new HeartDemonState(newStacks, nowTick + durationTicks);
    }

    /** 到期或清空。 */
    public HeartDemonState expire(long nowTick) {
        if (!active() || nowTick < expiryTick) {
            return this;
        }
        return EMPTY;
    }

    /** 洗尘丹清除。 */
    public HeartDemonState cleanse() {
        return EMPTY;
    }

    /** 修为速率乘数：1.0 - 0.15 × 层数（NUMBERS @@heart_demon.qi_rate_penalty=0.15）。 */
    public double qiRateMultiplier(double penaltyPerStack) {
        if (!active()) return 1.0;
        return Math.max(0.0, 1.0 - penaltyPerStack * stacks);
    }
}

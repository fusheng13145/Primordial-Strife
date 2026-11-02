package com.strife.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * 突破辅助丹力（破境丹/稳元丹，丹药接线批）：服用后暂存，下次突破押注时消费（成败皆耗）。
 *
 * <p>独立附件（心魔/神识同款取舍，不动 StrifeData 2048B 预算与同步位序契约）。 此前 applyBreakthroughAid 是"只给反馈不给数值"的存根（原注释自陈"v1
 * 简化"），本类是其数据落点。
 *
 * <p>不叠层：重复服用按最近一次覆盖——突破辅助是一次性押注增强，允许叠加会把"押注"变成"攒弹药"， 与 05 §4 的押注体验相悖。
 */
public record BreakthroughAidState(double successBonus, double failKeepBonus) {

    public static final BreakthroughAidState EMPTY = new BreakthroughAidState(0.0, 0.0);

    public static final Codec<BreakthroughAidState> CODEC =
            RecordCodecBuilder.create(
                    inst ->
                            inst.group(
                                            Codec.DOUBLE
                                                    .fieldOf("success_bonus")
                                                    .forGetter(BreakthroughAidState::successBonus),
                                            Codec.DOUBLE
                                                    .fieldOf("fail_keep_bonus")
                                                    .forGetter(BreakthroughAidState::failKeepBonus))
                                    .apply(inst, BreakthroughAidState::new));

    public BreakthroughAidState {
        successBonus = Math.max(0.0, successBonus);
        failKeepBonus = Math.max(0.0, failKeepBonus);
    }

    public boolean present() {
        return successBonus > 0.0 || failKeepBonus > 0.0;
    }
}

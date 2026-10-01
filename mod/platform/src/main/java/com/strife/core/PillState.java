package com.strife.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;

/**
 * 丹药增益状态（NUMBERS {@code @@pills}）：当前在效的丹药增益 + 本境界已通过丹药延长的寿元。
 *
 * <p>为什么把"增益"存成 {@code (截止刻, 重复次数)} 而不是存加成数值本身：加成数值是内容（写在 pills 表里），存在玩家档里就成了第二份
 * 真相——改一次丹药数值，老档还按旧数值生效。档里只存"何时到期、嗑了几次"，加成每次从产物查。
 *
 * <p>为什么寿元延长要按境界记账（{@code lifespanGained} / {@code lifespanGainedRealm}）：NUMBERS 的 {@code
 * max_gain_per_realm} 是"每个境界最多用丹药续多少年"，没有这个计数就只能按单次封顶，"堆延寿丹"会变成无限续命。 换境界（突破成功或大限跌落）时计数清零。
 *
 * <p>不入 S2C 同步包：客户端不读它。面板要显示的是服务端算好的<b>系数</b>（见 {@code StrifeRealmView} 的四因子分解），
 * 而不是这份原始状态——同步一份客户端用不上的映射只会占同步预算。
 *
 * @param active 丹药内容 ID → 增益（含到期刻与该药在当前窗口内的重复次数）
 * @param lifespanGainedRealm 上面那个计数所属的境界序号
 */
public record PillState(Map<String, PillBuff> active, int lifespanGained, int lifespanGainedRealm) {

    /** 单种丹药的增益：{@code stacks} = 当前窗口内第几次服用（0 = 第一次）。 */
    public record PillBuff(long untilTick, int stacks) {

        public static final Codec<PillBuff> CODEC =
                RecordCodecBuilder.create(
                        instance ->
                                instance.group(
                                                Codec.LONG
                                                        .fieldOf("until_tick")
                                                        .orElse(0L)
                                                        .forGetter(PillBuff::untilTick),
                                                Codec.INT
                                                        .fieldOf("stacks")
                                                        .orElse(0)
                                                        .forGetter(PillBuff::stacks))
                                        .apply(instance, PillBuff::new));

        /* 空块：本记录只承载数据，行为在 PillMath 里（纯函数更好测）。 */
    }

    public static final PillState EMPTY = new PillState(Map.of(), 0, -1);

    public static final Codec<PillState> CODEC =
            RecordCodecBuilder.create(
                    instance ->
                            instance.group(
                                            Codec.unboundedMap(Codec.STRING, PillBuff.CODEC)
                                                    .fieldOf("active")
                                                    .orElse(Map.of())
                                                    .forGetter(PillState::active),
                                            Codec.INT
                                                    .fieldOf("lifespan_gained")
                                                    .orElse(0)
                                                    .forGetter(PillState::lifespanGained),
                                            Codec.INT
                                                    .fieldOf("lifespan_gained_realm")
                                                    .orElse(-1)
                                                    .forGetter(PillState::lifespanGainedRealm))
                                    .apply(instance, PillState::new));

    public PillState {
        active = active == null ? Map.of() : Map.copyOf(active);
    }

    /** 仍在效的丹药（截止刻 &gt; 当前刻）。 */
    public Map<String, PillBuff> effectiveAt(long nowTick) {
        Map<String, PillBuff> live = new HashMap<>();
        active.forEach(
                (pillId, buff) -> {
                    if (buff.untilTick() > nowTick) {
                        live.put(pillId, buff);
                    }
                });
        return Map.copyOf(live);
    }

    /**
     * 服下一剂：已在效则 {@code stacks + 1} 并刷新到期刻；否则从 {@code stacks = 0} 开始。
     *
     * <p>{@code stacks} 上界由调用方按 NUMBERS 的 {@code repeat_floor} 算出（见 {@code PillMath.maxStacks}），
     * 这里只做非负保护。
     */
    public PillState withPill(String pillId, long nowTick, long durationTicks, int maxStacks) {
        Map<String, PillBuff> updated = new HashMap<>(active);
        PillBuff existing = updated.get(pillId);
        int stacks = existing != null && existing.untilTick() > nowTick ? existing.stacks() + 1 : 0;
        updated.put(
                pillId,
                new PillBuff(
                        nowTick + Math.max(1L, durationTicks),
                        Math.min(stacks, Math.max(0, maxStacks))));
        return new PillState(updated, lifespanGained, lifespanGainedRealm);
    }

    /** 记一次寿元延长；境界变了则从头计数（NUMBERS {@code max_gain_per_realm} 的记账口径）。 */
    public PillState withLifespanGain(int realmOrdinal, int years) {
        int base = lifespanGainedRealm == realmOrdinal ? lifespanGained : 0;
        return new PillState(active, Math.max(0, base + years), realmOrdinal);
    }

    /** 本境界已用丹药延长的年数（境界不符时按 0 计）。 */
    public int gainedIn(int realmOrdinal) {
        return lifespanGainedRealm == realmOrdinal ? lifespanGained : 0;
    }
}

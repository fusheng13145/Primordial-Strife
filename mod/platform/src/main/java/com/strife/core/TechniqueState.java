package com.strife.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.Set;

/**
 * 功法状态（docs/03 §1 combat 职责）：已学清单 + 当前装备的一部。
 *
 * <p>为什么"已学"与"装备"分开：03 §8 的洗髓规则要求"重置后不满足 {@code required} 的已学功法置灰并写明 {@code disabled_by}
 * 原因，禁止静默失效"——已学是资产，装备是当前生效项，两者混成一个集合就没法表达"学会了但用不了"。
 *
 * <p>为什么装备是单值而不是集合：M2 的功法是"路线"而非"栏位"（05 §2 只把一部功法的 {@code qi_rate_ratio} 计进速率），
 * 多功法并行的口径还没有拍板。单值是最小可验证的形态，扩展成集合是加字段而不是改语义。
 *
 * <p>不入 S2C 同步包：客户端不读原始状态，面板要显示的是服务端算好的功法倍率（见 {@code StrifeRealmView} 的四因子分解）。
 *
 * @param learned 已学功法内容 ID 集合
 * @param equipped 当前装备的功法内容 ID；空串 = 未装备
 */
public record TechniqueState(Set<String> learned, String equipped) {

    public static final TechniqueState EMPTY = new TechniqueState(Set.of(), "");

    public static final Codec<TechniqueState> CODEC =
            RecordCodecBuilder.create(
                    instance ->
                            instance.group(
                                            Codec.STRING
                                                    .listOf()
                                                    .fieldOf("learned")
                                                    .orElse(java.util.List.of())
                                                    .forGetter(
                                                            state ->
                                                                    java.util.List.copyOf(
                                                                            state.learned())),
                                            Codec.STRING
                                                    .fieldOf("equipped")
                                                    .orElse("")
                                                    .forGetter(TechniqueState::equipped))
                                    .apply(
                                            instance,
                                            (learned, equipped) ->
                                                    new TechniqueState(
                                                            new HashSet<>(learned), equipped)));

    public TechniqueState {
        learned = learned == null ? Set.of() : Set.copyOf(learned);
        equipped = equipped == null ? "" : equipped;
    }

    public boolean knows(String techniqueId) {
        return learned.contains(techniqueId);
    }

    /** 学会一部；已学过则为幂等空操作（任务奖励可能被重放）。 */
    public TechniqueState learning(String techniqueId) {
        if (techniqueId == null || techniqueId.isBlank() || learned.contains(techniqueId)) {
            return this;
        }
        Set<String> updated = new HashSet<>(learned);
        updated.add(techniqueId);
        return new TechniqueState(updated, equipped);
    }

    /** 装备一部（必须先学会）；传空串 = 卸下。 */
    public TechniqueState equipping(String techniqueId) {
        String target = techniqueId == null ? "" : techniqueId;
        if (!target.isEmpty() && !learned.contains(target)) {
            throw new IllegalArgumentException("cannot equip unlearned technique '" + target + "'");
        }
        return new TechniqueState(learned, target);
    }

    /** 洗髓/门禁变化后把不再满足条件的功法卸下（03 §8 禁止静默失效：调用方负责给出原因文案）。 */
    public TechniqueState unequipIf(java.util.function.Predicate<String> invalid) {
        if (equipped.isEmpty() || !invalid.test(equipped)) {
            return this;
        }
        return new TechniqueState(learned, "");
    }
}

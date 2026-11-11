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
 * @param proficiency 当前装备功法的熟练度（施法次数累积；未装备时无意义但保留数值——
 *                     切换功法不清零，回装延续熟练；append-only 字段，旧档缺省 0）
 */
public record TechniqueState(Set<String> learned, String equipped, int proficiency) {

    public static final TechniqueState EMPTY = new TechniqueState(Set.of(), "", 0);

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
                                                    .forGetter(TechniqueState::equipped),
                                            Codec.INT
                                                    .fieldOf("proficiency")
                                                    .orElse(0)
                                                    .forGetter(TechniqueState::proficiency))
                                    .apply(
                                            instance,
                                            (learned, equipped, proficiency) ->
                                                    new TechniqueState(
                                                            new HashSet<>(learned),
                                                            equipped,
                                                            Math.max(0, proficiency))));

    public TechniqueState {
        learned = learned == null ? Set.of() : Set.copyOf(learned);
        equipped = equipped == null ? "" : equipped;
        proficiency = Math.max(0, proficiency);
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
        return new TechniqueState(updated, equipped, proficiency);
    }

    /** 装备一部（必须先学会）；传空串 = 卸下。 */
    public TechniqueState equipping(String techniqueId) {
        String target = techniqueId == null ? "" : techniqueId;
        if (!target.isEmpty() && !learned.contains(target)) {
            throw new IllegalArgumentException("cannot equip unlearned technique '" + target + "'");
        }
        return new TechniqueState(learned, target, proficiency);
    }

    /** 洗髓/门禁变化后把不再满足条件的功法卸下（03 §8 禁止静默失效：调用方负责给出原因文案）。 */
    public TechniqueState unequipIf(java.util.function.Predicate<String> invalid) {
        if (equipped.isEmpty() || !invalid.test(equipped)) {
            return this;
        }
        return new TechniqueState(learned, "", proficiency);
    }

    /**
     * 熟练度累积（功法熟练度批）：施法成功一次 +1，只对当前装备功法累积——未装备 = 空操作
     * （不给"没练功法也在涨熟练"的暗收益）。上限由消费方（倍率封顶）控制，状态本身不设顶。
     */
    public TechniqueState withProficiencyGain() {
        if (equipped.isEmpty()) {
            return this;
        }
        return new TechniqueState(learned, equipped, proficiency + 1);
    }

    /** 切换功法时熟练度随装备走：学习/装备不重置（回装延续熟练），此方法仅语义自文档。 */
    public int proficiencyOf(String techniqueId) {
        return equipped.equals(techniqueId) ? proficiency : 0;
    }
}

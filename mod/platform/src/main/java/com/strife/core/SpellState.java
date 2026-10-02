package com.strife.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;

/**
 * 法术施放状态（03 §3：玩家数据走 StrifeData 聚合附件；NUMBERS §8 spell_cooldown_sec 的运行时载体）。
 *
 * <p>存档里只存"哪个法术、何时冷却截止"——冷却时长本身由法术产物定义，存数值等于多一份永远漂移的第二真相 （与丹药状态同一口径：存时间戳，不存参数）。键为法术内容 ID（{@code
 * spell_<章>_<语义>}）。
 *
 * @param cooldowns spellId → 冷却截止游戏刻（当前刻 ≥ 值即可再次施放）
 */
public record SpellState(Map<String, Long> cooldowns) {

    public static final SpellState EMPTY = new SpellState(Map.of());

    public static final Codec<SpellState> CODEC =
            RecordCodecBuilder.create(
                    instance ->
                            instance.group(
                                            Codec.unboundedMap(Codec.STRING, Codec.LONG)
                                                    .fieldOf("cooldowns")
                                                    .orElse(Map.of())
                                                    .forGetter(SpellState::cooldowns))
                                    .apply(instance, SpellState::new));

    public SpellState {
        cooldowns = cooldowns == null ? Map.of() : Map.copyOf(cooldowns);
    }

    /** 指定法术是否仍在冷却中。 */
    public boolean onCooldown(String spellId, long nowTick) {
        Long until = cooldowns.get(spellId);
        return until != null && nowTick < until;
    }

    /** 记录一次施放的冷却截止（新建值对象，不可变契约）。 */
    public SpellState withCooldown(String spellId, long untilTick) {
        Map<String, Long> updated = new HashMap<>(cooldowns);
        updated.put(spellId, untilTick);
        return new SpellState(updated);
    }
}

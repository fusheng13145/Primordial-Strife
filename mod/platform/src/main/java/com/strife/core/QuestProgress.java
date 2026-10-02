package com.strife.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 任务进度状态（docs/03 §3：玩家数据一律走挂在 Player 上的附件，与 {@link StrifeData} 同一聚合对象）。
 *
 * <p>为什么必须入附件而不是世界级 SavedData：任务进度是<b>玩家</b>的数据。挂在世界上会带来两个后果——死亡时不走 {@code
 * copyOnDeath}（进度与角色的生死脱钩），以及多人存档里所有玩家挤在一份世界数据里、按 UUID 手工索引。 03 §3 要求"所有附件走同一 StrifeData
 * 聚合对象、serialize/deserialize 往返一致"，这条状态此前是唯一的例外。
 *
 * <p>形状保持泛型（任务/目标键为内容 ID）：core 不认识任务语义，只保证它可序列化、可同步、可随玩家走。
 *
 * @param completed 已完成的 questId 集合
 * @param objectiveProgress questId → objectiveId → 进度值（已按目标 count 封顶，引擎保证）
 * @param activated start_quest 激活集（对话树等叙事钩子显式开门；激活任务跳过 prerequisites）
 * @param flags H3 因果标记（{@code <域>:<章>:<语义>} 冒号分段，03 §10 末口径）
 */
public record QuestProgress(
        Set<String> completed,
        Map<String, Map<String, Long>> objectiveProgress,
        Set<String> activated,
        Set<String> flags) {

    public static final QuestProgress EMPTY =
            new QuestProgress(Set.of(), Map.of(), Set.of(), Set.of());

    public static final Codec<QuestProgress> CODEC =
            RecordCodecBuilder.create(
                    instance ->
                            instance.group(
                                            Codec.STRING
                                                    .listOf()
                                                    .fieldOf("completed")
                                                    .orElse(List.of())
                                                    .forGetter(
                                                            progress ->
                                                                    List.copyOf(
                                                                            progress.completed())),
                                            Codec.unboundedMap(
                                                            Codec.STRING,
                                                            Codec.unboundedMap(
                                                                    Codec.STRING, Codec.LONG))
                                                    .fieldOf("objective_progress")
                                                    .orElse(Map.of())
                                                    .forGetter(QuestProgress::objectiveProgress),
                                            Codec.STRING
                                                    .listOf()
                                                    .fieldOf("activated")
                                                    .orElse(List.of())
                                                    .forGetter(
                                                            progress ->
                                                                    List.copyOf(
                                                                            progress.activated())),
                                            Codec.STRING
                                                    .listOf()
                                                    .fieldOf("flags")
                                                    .orElse(List.of())
                                                    .forGetter(
                                                            progress ->
                                                                    List.copyOf(progress.flags())))
                                    .apply(
                                            instance,
                                            (completed, objectiveProgress, activated, flags) ->
                                                    new QuestProgress(
                                                            Set.copyOf(completed),
                                                            copyProgress(objectiveProgress),
                                                            Set.copyOf(activated),
                                                            Set.copyOf(flags))));

    public QuestProgress {
        completed = completed == null ? Set.of() : Set.copyOf(completed);
        objectiveProgress = objectiveProgress == null ? Map.of() : copyProgress(objectiveProgress);
        activated = activated == null ? Set.of() : Set.copyOf(activated);
        flags = flags == null ? Set.of() : Set.copyOf(flags);
    }

    public boolean isCompleted(String questId) {
        return completed.contains(questId);
    }

    /** 某任务某目标的进度；未知按 0。 */
    public long progress(String questId, String objectiveId) {
        Map<String, Long> byObjective = objectiveProgress.get(questId);
        return byObjective == null ? 0L : byObjective.getOrDefault(objectiveId, 0L);
    }

    public boolean hasFlag(String flag) {
        return flags.contains(flag);
    }

    /** 追加一个 H3 标记（幂等）。 */
    public QuestProgress withFlag(String flag) {
        if (flag == null || flag.isBlank() || flags.contains(flag)) {
            return this;
        }
        Set<String> updated = new java.util.HashSet<>(flags);
        updated.add(flag);
        return new QuestProgress(completed, objectiveProgress, activated, updated);
    }

    private static Map<String, Map<String, Long>> copyProgress(
            Map<String, Map<String, Long>> source) {
        Map<String, Map<String, Long>> copy = new HashMap<>();
        source.forEach((questId, byObjective) -> copy.put(questId, Map.copyOf(byObjective)));
        return Map.copyOf(copy);
    }
}

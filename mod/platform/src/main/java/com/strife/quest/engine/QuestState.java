package com.strife.quest.engine;

import java.util.HashMap;
import java.util.Map;

/**
 * 单个玩家的任务进度（docs/07 §7 M3"进度入档"的纯数据核）。
 *
 * <p>持久化已由装配层接入玩家附件：{@link #completedIds()} / {@link #progressMap()} 供写入 {@code
 * com.strife.core.QuestProgress}（Codec 落 NBT 随 StrifeData 走），{@link #of(Set, Map)} 做反向恢复。 引擎本身不认识
 * Minecraft。键位约定： 任务/目标一律用内容 ID，进度值已按目标 count 封顶（引擎保证，存档里不会出现超量值）。
 */
public final class QuestState {

    private final Map<String, Map<String, Long>> objectiveProgress = new HashMap<>();
    private final Map<String, Boolean> completed = new HashMap<>();

    /** start_quest 激活集（对话树等叙事钩子显式开门；激活的任务跳过 prerequisites，conditions 仍生效）。 */
    private final java.util.Set<String> activated = new java.util.HashSet<>();

    /** 某任务某目标的当前进度；任务/目标未知按 0。 */
    public long progress(String questId, String objectiveId) {
        Map<String, Long> byObjective = objectiveProgress.get(questId);
        return byObjective == null ? 0L : byObjective.getOrDefault(objectiveId, 0L);
    }

    public boolean completed(String questId) {
        return completed.getOrDefault(questId, false);
    }

    void setProgress(String questId, String objectiveId, long value) {
        objectiveProgress.computeIfAbsent(questId, k -> new HashMap<>()).put(objectiveId, value);
    }

    void markCompleted(String questId) {
        completed.put(questId, true);
    }

    /** start_quest 的落点（幂等：重复激活是 no-op）。公开给装配层（对话树 effects 的唯一开门入口）。 */
    public void activate(String questId) {
        activated.add(questId);
    }

    public boolean isActivated(String questId) {
        return activated.contains(questId);
    }

    /** 交给持久化层的只读快照（深拷贝，防外部改坏引擎状态）。 */
    public Map<String, Object> snapshot() {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("completed", new HashMap<>(completed));
        Map<String, Object> progress = new HashMap<>();
        objectiveProgress.forEach(
                (questId, byObjective) -> progress.put(questId, new HashMap<>(byObjective)));
        snapshot.put("objective_progress", progress);
        return snapshot;
    }

    /** 已完成任务的 ID 集合（附件持久化用；{@code snapshot()} 的强类型视图）。 */
    public java.util.Set<String> completedIds() {
        java.util.Set<String> ids = new java.util.HashSet<>();
        completed.forEach(
                (questId, done) -> {
                    if (Boolean.TRUE.equals(done)) {
                        ids.add(questId);
                    }
                });
        return ids;
    }

    /** start_quest 激活集（附件持久化用；只读副本）。 */
    public java.util.Set<String> activatedIds() {
        return java.util.Set.copyOf(activated);
    }

    /** 目标进度表（附件持久化用；{@code snapshot()} 的强类型视图）。 */
    public Map<String, Map<String, Long>> progressMap() {
        Map<String, Map<String, Long>> copy = new HashMap<>();
        objectiveProgress.forEach(
                (questId, byObjective) -> copy.put(questId, Map.copyOf(byObjective)));
        return Map.copyOf(copy);
    }

    /** 从附件的强类型视图恢复（比 {@link #restore(Map)} 少一层装箱）。 */
    public static QuestState of(
            java.util.Set<String> completedIds, Map<String, Map<String, Long>> progress) {
        return of(completedIds, progress, java.util.Set.of());
    }

    /** 全量恢复（含 start_quest 激活集）。 */
    public static QuestState of(
            java.util.Set<String> completedIds,
            Map<String, Map<String, Long>> progress,
            java.util.Set<String> activatedIds) {
        QuestState state = new QuestState();
        if (completedIds != null) {
            completedIds.forEach(questId -> state.completed.put(questId, true));
        }
        if (progress != null) {
            progress.forEach(
                    (questId, byObjective) ->
                            state.objectiveProgress
                                    .computeIfAbsent(questId, k -> new HashMap<>())
                                    .putAll(byObjective));
        }
        if (activatedIds != null) {
            state.activated.addAll(activatedIds);
        }
        return state;
    }

    /** 从持久化快照恢复；键型不符在装配层被 Codec 挡住，这里只做最小防御。 */
    public static QuestState restore(Map<String, Object> data) {
        QuestState state = new QuestState();
        if (data == null) {
            return state;
        }
        Object completed = data.get("completed");
        if (completed instanceof Map<?, ?> byQuest) {
            byQuest.forEach(
                    (questId, flag) ->
                            state.completed.put(
                                    String.valueOf(questId), Boolean.TRUE.equals(flag)));
        }
        Object progress = data.get("objective_progress");
        if (progress instanceof Map<?, ?> byQuest) {
            byQuest.forEach(
                    (questId, byObjectiveRaw) -> {
                        if (byObjectiveRaw instanceof Map<?, ?> byObjective) {
                            byObjective.forEach(
                                    (objectiveId, value) -> {
                                        if (value instanceof Number number) {
                                            state.setProgress(
                                                    String.valueOf(questId),
                                                    String.valueOf(objectiveId),
                                                    number.longValue());
                                        }
                                    });
                        }
                    });
        }
        return state;
    }
}

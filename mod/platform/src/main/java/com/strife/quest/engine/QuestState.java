package com.strife.quest.engine;

import java.util.HashMap;
import java.util.Map;

/**
 * 单个玩家的任务进度（docs/07 §7 M3"进度入档"的纯数据核）。
 *
 * <p>持久化是装配层的事：玩家附件（NeoForge AttachmentType）由 core 侧接线落地后，把 {@link #snapshot()} / {@link
 * #restore(Map)} 接进 Codec——引擎本身不认识 Minecraft。键位约定： 任务/目标一律用内容 ID，进度值已按目标 count 封顶（引擎保证，存档里不会出现超量值）。
 */
public final class QuestState {

    private final Map<String, Map<String, Long>> objectiveProgress = new HashMap<>();
    private final Map<String, Boolean> completed = new HashMap<>();

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

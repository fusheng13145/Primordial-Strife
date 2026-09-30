package com.strife.quest.engine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.strife.quest.dsl.ConditionDsl;
import com.strife.quest.dsl.ConditionExpression;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 一章任务 DAG 的状态机（docs/07 §7 M3"DAG 存储与推进、幂等键"的引擎核）。
 *
 * <p>输入是 DataGen 产物 JSON（{@code data/strife/strife_quests/<章>.json}）——引擎信任 DataGen + V-DAG
 * 的结构校验，但仍然防御式地当场拒绝坏结构（未知目标/奖励类型、悬空前置、 零计数目标），因为热更 zip 会绕过构建期门禁直接进游戏。
 *
 * <p>职责边界：推进与完成判定、奖励与 flags_set 的发放计划、幂等；<b>不含</b> 事件订阅 （core 事件落地后的薄适配）、持久化（{@link
 * QuestState#snapshot()} 交给装配层）、 末影箱交付的背包扫描（缝实现细节）。
 */
public final class QuestBook {

    /** §1.4 objective_type 枚举——引擎按此订阅目标事件。 */
    public enum ObjectiveType {
        KILL,
        COLLECT,
        DELIVER,
        TALK,
        REACH,
        SIT,
        BREAKTHROUGH,
        CRAFT,
        PILL_CRAFT,
        ARTIFACT_CRAFT,
        ESCORT,
        SURVIVE;

        static ObjectiveType fromId(String id) {
            for (ObjectiveType type : values()) {
                if (type.name().toLowerCase(java.util.Locale.ROOT).equals(id)) {
                    return type;
                }
            }
            return null;
        }
    }

    /** §4.6 rewards.type 枚举。 */
    public enum RewardType {
        ITEM,
        QI,
        REALM_STEP,
        FLAG,
        UNLOCK,
        REPUTATION,
        SPELL,
        TECHNIQUE;

        static RewardType fromId(String id) {
            for (RewardType type : values()) {
                if (type.name().toLowerCase(java.util.Locale.ROOT).equals(id)) {
                    return type;
                }
            }
            return null;
        }
    }

    record ObjectiveSpec(
            String id, ObjectiveType type, String target, long count, boolean optional) {}

    record RewardSpec(RewardType type, String id, Long count, String unlockKey) {}

    record ReputationDelta(String factionId, int delta) {}

    record QuestSpec(
            String id,
            boolean entry,
            List<String> prerequisites,
            ConditionExpression conditions,
            List<ObjectiveSpec> objectives,
            List<RewardSpec> rewards,
            List<ReputationDelta> reputationDeltas,
            List<String> flagsSet,
            boolean hidden) {}

    /** 一次完成所发放的奖励记录（装配层写审计日志用）。 */
    public record AppliedReward(
            String questId, RewardType type, String id, Long count, String unlockKey) {}

    /** 一条库存对账补报计划（装配层把它转成一次 COLLECT report 事件）。 */
    public record CollectDelta(String questId, String target, long amount) {}

    private final String chapter;
    private final String entryId;
    private final Map<String, QuestSpec> quests = new LinkedHashMap<>();

    private QuestBook(String chapter, String entryId) {
        this.chapter = chapter;
        this.entryId = entryId;
    }

    /** 从 DataGen 产物解析；坏结构当场炸（防御热更 zip 绕过构建期门禁）。 */
    public static QuestBook parse(JsonObject product) {
        String chapter = string(product, "chapter", true);
        String entryId = string(product, "id", true);
        QuestBook book = new QuestBook(chapter, entryId);
        JsonArray questArray = array(product, "quests");
        boolean sawEntry = false;
        for (JsonElement element : questArray) {
            JsonObject json = object(element);
            QuestSpec quest = parseQuest(json, chapter);
            if (book.quests.containsKey(quest.id())) {
                throw new IllegalStateException(
                        "quest '" + quest.id() + "' declared twice in chapter " + chapter);
            }
            if (quest.entry()) {
                if (sawEntry) {
                    throw new IllegalStateException(
                            "second entry quest '" + quest.id() + "' in chapter " + chapter);
                }
                sawEntry = true;
                if (!entryId.equals(quest.id())) {
                    throw new IllegalStateException(
                            "product id '"
                                    + entryId
                                    + "' is not the entry quest '"
                                    + quest.id()
                                    + "'");
                }
            }
            book.quests.put(quest.id(), quest);
        }
        if (!sawEntry) {
            throw new IllegalStateException("chapter " + chapter + " has no entry quest");
        }
        // 前置引用闭包检查（V-DAG 也查，热更 zip 场景下这里再拦一道）
        for (QuestSpec quest : book.quests.values()) {
            for (String prerequisite : quest.prerequisites()) {
                if (!book.quests.containsKey(prerequisite)) {
                    throw new IllegalStateException(
                            "quest '"
                                    + quest.id()
                                    + "' has dangling prerequisite '"
                                    + prerequisite
                                    + "'");
                }
            }
        }
        return book;
    }

    private static QuestSpec parseQuest(JsonObject json, String chapter) {
        String id = string(json, "id", true);
        boolean entry = booleanValue(json, "entry");
        List<String> prerequisites = new ArrayList<>();
        for (JsonElement element : arrayOrEmpty(json, "prerequisites")) {
            prerequisites.add(string(element, "prerequisite", true));
        }
        ConditionExpression conditions = null;
        String rawConditions = string(json, "conditions", false);
        if (rawConditions != null) {
            try {
                conditions = ConditionDsl.parse(rawConditions);
            } catch (ConditionDsl.SyntaxException e) {
                throw new IllegalStateException(
                        "quest '"
                                + id
                                + "' in chapter "
                                + chapter
                                + " has an illegal condition: "
                                + e.getMessage(),
                        e);
            }
        }
        List<ObjectiveSpec> objectives = new ArrayList<>();
        boolean sawRequired = false;
        for (JsonElement element : arrayOrEmpty(json, "objectives")) {
            JsonObject objective = object(element);
            String typeId = string(objective, "type", true);
            ObjectiveType type = ObjectiveType.fromId(typeId);
            if (type == null) {
                throw new IllegalStateException(
                        "objective type '" + typeId + "' is not in JSON_SCHEMA §1.4");
            }
            long count = number(objective, "count");
            if (count <= 0) {
                throw new IllegalStateException(
                        "objective '"
                                + string(objective, "id", true)
                                + "' of quest '"
                                + id
                                + "' has count "
                                + count);
            }
            boolean optional = booleanValue(objective, "optional");
            if (!optional) {
                sawRequired = true;
            }
            objectives.add(
                    new ObjectiveSpec(
                            string(objective, "id", true),
                            type,
                            string(objective, "target", false),
                            count,
                            optional));
        }
        if (!sawRequired) {
            throw new IllegalStateException(
                    "quest '" + id + "' has no required objective — 完成条件为空（全部 optional）");
        }
        List<RewardSpec> rewards = new ArrayList<>();
        for (JsonElement element : arrayOrEmpty(json, "rewards")) {
            JsonObject reward = object(element);
            String typeId = string(reward, "type", true);
            RewardType type = RewardType.fromId(typeId);
            if (type == null) {
                throw new IllegalStateException(
                        "reward type '" + typeId + "' is not in JSON_SCHEMA §4.6");
            }
            Long count = null;
            if (reward.has("count") && reward.get("count").isJsonPrimitive()) {
                count = reward.get("count").getAsLong();
            }
            rewards.add(
                    new RewardSpec(
                            type,
                            string(reward, "id", false),
                            count,
                            string(reward, "unlock_key", false)));
        }
        List<ReputationDelta> deltas = new ArrayList<>();
        for (JsonElement element : arrayOrEmpty(json, "reputation_delta")) {
            JsonObject delta = object(element);
            deltas.add(
                    new ReputationDelta(
                            string(delta, "faction_id", true), delta.get("delta").getAsInt()));
        }
        List<String> flagsSet = new ArrayList<>();
        for (JsonElement element : arrayOrEmpty(json, "flags_set")) {
            flagsSet.add(string(element, "flag", true));
        }
        String repeatable = string(json, "repeatable", true);
        if (!"no".equals(repeatable)) {
            throw new IllegalStateException(
                    "quest '"
                            + id
                            + "' is repeatable="
                            + repeatable
                            + " — per_day/per_period 依赖日重置与 H5 周期，本期未支持（JSON_SCHEMA §4.6）");
        }
        return new QuestSpec(
                id,
                entry,
                prerequisites,
                conditions,
                objectives,
                rewards,
                deltas,
                flagsSet,
                booleanValue(json, "hidden"));
    }

    public String chapter() {
        return chapter;
    }

    public String entryId() {
        return entryId;
    }

    public Set<String> questIds() {
        return new LinkedHashSet<>(quests.keySet());
    }

    /**
     * 可接取 = 前置全部完成 + 自身未完成 + {@code conditions} 通过。conditions 存在而调用方没给 DSL 上下文时按 <b>不可接取</b>
     * 处理（fail-closed：进门槛没有"猜真"）。
     */
    public boolean isAvailable(
            QuestState state, String questId, ConditionExpression.Context dslContext) {
        QuestSpec quest = quests.get(questId);
        if (quest == null || state.completed(questId)) {
            return false;
        }
        for (String prerequisite : quest.prerequisites()) {
            if (!state.completed(prerequisite)) {
                return false;
            }
        }
        if (quest.conditions() != null) {
            if (dslContext == null) {
                return false;
            }
            return ConditionDsl.satisfies(quest.conditions(), dslContext);
        }
        return true;
    }

    public List<String> availableQuests(QuestState state, ConditionExpression.Context dslContext) {
        List<String> available = new ArrayList<>();
        for (String id : quests.keySet()) {
            if (isAvailable(state, id, dslContext)) {
                available.add(id);
            }
        }
        return available;
    }

    /**
     * 幂等推进：把一次目标事件（type + target，amount 为本次增量）记入所有可推进的任务。
     *
     * <ul>
     *   <li>目标匹配：类型相等且 target 相等；target 为 null 的目标（如"任意地点打坐"）只匹配 target 为 null 的事件；
     *   <li>进度按目标 count 封顶，永不溢出存档；
     *   <li>完成判定只含非 optional 目标；完成时经 sink 依次发放 rewards + reputation_delta + flags_set，随后才落完成态——sink
     *       抛异常则状态不落，调用方重试时重放（缝实现须幂等）；
     *   <li>重复事件对已完成任务是 no-op；
     *   <li>{@code dslContext} 供带 conditions 的任务做进入判定（可为 null，则带条件任务按 不可接取跳过——fail-closed）。
     * </ul>
     */
    public List<AppliedReward> report(
            ObjectiveType type,
            String target,
            long amount,
            QuestState state,
            RewardSink sink,
            ConditionExpression.Context dslContext) {
        List<AppliedReward> applied = new ArrayList<>();
        if (amount <= 0) {
            return applied;
        }
        for (QuestSpec quest : quests.values()) {
            if (state.completed(quest.id()) || !isAvailable(state, quest.id(), dslContext)) {
                continue;
            }
            for (ObjectiveSpec objective : quest.objectives()) {
                if (objective.type() != type || !targetsMatch(objective.target(), target)) {
                    continue;
                }
                long current = state.progress(quest.id(), objective.id());
                long capped = Math.min(objective.count(), current + amount);
                if (capped > current) {
                    state.setProgress(quest.id(), objective.id(), capped);
                }
            }
            // 完成判定不以"本次有推进"为门：sink 中途抛异常时进度可能已满，
            // 重报事件必须能重试发放（缝幂等 + 完成态恰好落一次共同保证幂等）
            if (isComplete(quest, state)) {
                applied.addAll(grant(quest, sink));
                state.markCompleted(quest.id());
            }
        }
        return applied;
    }

    private boolean isComplete(QuestSpec quest, QuestState state) {
        for (ObjectiveSpec objective : quest.objectives()) {
            if (!objective.optional()
                    && state.progress(quest.id(), objective.id()) < objective.count()) {
                return false;
            }
        }
        return true;
    }

    private List<AppliedReward> grant(QuestSpec quest, RewardSink sink) {
        List<AppliedReward> applied = new ArrayList<>();
        for (RewardSpec reward : quest.rewards()) {
            switch (reward.type()) {
                case ITEM ->
                        sink.giveItem(reward.id(), reward.count() == null ? 1 : reward.count());
                case QI -> sink.grantQi(reward.count() == null ? 0 : reward.count());
                case REALM_STEP -> sink.advanceRealmStep();
                case FLAG -> sink.setFlag(reward.id());
                case UNLOCK -> sink.unlock(reward.unlockKey());
                case REPUTATION ->
                        sink.addReputation(
                                reward.id(),
                                reward.count() == null ? 0 : reward.count().intValue());
                case SPELL -> sink.grantSpell(reward.id());
                case TECHNIQUE -> sink.grantTechnique(reward.id());
            }
            applied.add(
                    new AppliedReward(
                            quest.id(),
                            reward.type(),
                            reward.id(),
                            reward.count(),
                            reward.unlockKey()));
        }
        for (ReputationDelta delta : quest.reputationDeltas()) {
            sink.addReputation(delta.factionId(), delta.delta());
        }
        for (String flag : quest.flagsSet()) {
            sink.setFlag(flag);
        }
        return applied;
    }

    /**
     * 库存对账（COLLECT 目标的语义是"当前持有 ≥ count"，状态型而非事件累计型）：对每个可用任务的 COLLECT 目标，算出需要补报的增量 {@code min(count,
     * 持有量) − 已记进度}，只返回正增量。
     *
     * <p>装配层在登录、拾取、任何 report 之后循环调用本方法并逐条 report，直到返回空——这处理了 "奖励发放把物品送进背包、解锁下一环采集"的级联；无 target 的
     * COLLECT 目标（无此用法）跳过。
     */
    public List<CollectDelta> collectDeltas(
            QuestState state,
            ConditionExpression.Context dslContext,
            java.util.function.ToIntFunction<String> itemCount) {
        List<CollectDelta> deltas = new ArrayList<>();
        for (QuestSpec quest : quests.values()) {
            if (state.completed(quest.id()) || !isAvailable(state, quest.id(), dslContext)) {
                continue;
            }
            for (ObjectiveSpec objective : quest.objectives()) {
                if (objective.type() != ObjectiveType.COLLECT || objective.target() == null) {
                    continue;
                }
                long have = itemCount.applyAsInt(objective.target());
                long credited = state.progress(quest.id(), objective.id());
                long delta = Math.min(objective.count(), have) - credited;
                if (delta > 0) {
                    deltas.add(new CollectDelta(quest.id(), objective.target(), delta));
                }
            }
        }
        return deltas;
    }

    /**
     * 任务状态一览（导航 MVP 片）：每任务一行 {@code <状态> <id> [目标 进度/上限…]}，状态 ∈ 已完成/进行中/未解锁（fail-closed 同 {@link
     * #isAvailable}）。供 {@code /strife quest status} 与后续面板消费。
     */
    public String describe(QuestState state, ConditionExpression.Context dslContext) {
        StringBuilder text = new StringBuilder();
        for (QuestSpec quest : quests.values()) {
            if (state.completed(quest.id())) {
                text.append("已完成 ").append(quest.id());
            } else if (!isAvailable(state, quest.id(), dslContext)) {
                text.append("未解锁 ").append(quest.id());
            } else {
                text.append("进行中 ").append(quest.id());
                for (ObjectiveSpec objective : quest.objectives()) {
                    long progress =
                            Math.min(state.progress(quest.id(), objective.id()), objective.count());
                    text.append(
                            String.format(
                                    "  %s %d/%d",
                                    objective.type().name().toLowerCase(java.util.Locale.ROOT),
                                    progress,
                                    objective.count()));
                }
            }
            if (quest.hidden()) {
                text.append("（隐藏）");
            }
            text.append('\n');
        }
        return text.toString();
    }

    /** target 语义：null（如任意地点打坐）只匹配 null 事件；非空按内容 ID 全等。 */
    private static boolean targetsMatch(String objectiveTarget, String eventTarget) {
        if (objectiveTarget == null) {
            return eventTarget == null;
        }
        return objectiveTarget.equals(eventTarget);
    }

    // ===== 解析助手（报错带路径语义，配合 docs/04 §6 报错口径） =====

    private static String string(JsonElement element, String what, boolean required) {
        if (element.isJsonPrimitive()) {
            String value = element.getAsString();
            if (!value.isBlank()) {
                return value;
            }
        }
        if (required) {
            throw new IllegalStateException("missing required string '" + what + "'");
        }
        return null;
    }

    private static String string(JsonObject object, String field, boolean required) {
        if (object.has(field) && object.get(field).isJsonPrimitive()) {
            String value = object.get(field).getAsString();
            if (!value.isBlank()) {
                return value;
            }
        }
        if (required) {
            throw new IllegalStateException("missing required field '" + field + "'");
        }
        return null;
    }

    private static boolean booleanValue(JsonObject object, String field) {
        return object.has(field)
                && object.get(field).isJsonPrimitive()
                && object.get(field).getAsBoolean();
    }

    private static long number(JsonObject object, String field) {
        if (object.has(field) && object.get(field).isJsonPrimitive()) {
            return object.get(field).getAsLong();
        }
        throw new IllegalStateException("missing required number field '" + field + "'");
    }

    private static JsonArray array(JsonObject object, String field) {
        if (object.has(field) && object.get(field).isJsonArray()) {
            return object.getAsJsonArray(field);
        }
        throw new IllegalStateException("missing required array field '" + field + "'");
    }

    private static JsonArray arrayOrEmpty(JsonObject object, String field) {
        if (object.has(field) && object.get(field).isJsonArray()) {
            return object.getAsJsonArray(field);
        }
        if (object.has(field) && !object.get(field).isJsonNull()) {
            throw new IllegalStateException("field '" + field + "' must be an array or null");
        }
        return new JsonArray();
    }

    private static JsonObject object(JsonElement element) {
        if (element.isJsonObject()) {
            return element.getAsJsonObject();
        }
        throw new IllegalStateException("expected a JSON object, found " + element);
    }
}

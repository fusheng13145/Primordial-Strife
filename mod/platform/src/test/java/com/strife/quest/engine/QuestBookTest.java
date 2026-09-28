package com.strife.quest.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.strife.quest.dsl.ConditionExpression;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * QuestEngine 纯逻辑核的用例矩阵（docs/07 §7 M3 验收：DAG 推进 / 幂等键 / 奖励缝）。 语义基准：JSON_SCHEMA §4.6 产物形态 + 本核
 * Javadoc 的推进契约。
 */
class QuestBookTest {

    private static final String CHAPTER =
            """
            {
              "id": "quest_a_01",
              "chapter": "prologue",
              "quests": [
                {"id": "quest_a_01", "entry": true, "prerequisites": [],
                 "objectives": [{"id": "1", "type": "talk", "target": "npc_x", "count": 1, "optional": false}],
                 "rewards": [{"type": "flag", "id": "fac_qingshi:prologue:met_elder"}],
                 "flags_set": ["prologue:quest:met"],
                 "repeatable": "no", "hidden": false},
                {"id": "quest_b_01", "entry": false, "prerequisites": ["quest_a_01"],
                 "objectives": [
                   {"id": "1", "type": "sit", "count": 3, "optional": false},
                   {"id": "2", "type": "collect", "target": "item_ningxu", "count": 6, "optional": false},
                   {"id": "3", "type": "kill", "count": 2, "optional": true}],
                 "rewards": [
                   {"type": "item", "id": "item_lingshi", "count": 3},
                   {"type": "qi", "count": 40},
                   {"type": "unlock", "unlock_key": "meditation"},
                   {"type": "reputation", "id": "fac_qingshi", "count": 10}],
                 "reputation_delta": [{"faction_id": "fac_yuelai", "delta": -5}],
                 "repeatable": "no", "hidden": false},
                {"id": "quest_c_01", "entry": false, "prerequisites": ["quest_b_01"],
                 "conditions": "realm>=qili",
                 "objectives": [{"id": "1", "type": "breakthrough", "target": "bs_qili_zhuji", "count": 1, "optional": false}],
                 "rewards": [
                   {"type": "technique", "id": "tech_qingxin_jue"},
                   {"type": "spell", "id": "spell_prologue_huodan"},
                   {"type": "realm_step", "count": 1}],
                 "repeatable": "no", "hidden": false}
              ]
            }
            """;

    private static QuestBook book() {
        JsonObject product = JsonParser.parseString(CHAPTER).getAsJsonObject();
        return QuestBook.parse(product);
    }

    /** 录制型 sink：断言"该给什么"全落在这，不碰任何 Minecraft 类。 */
    private static final class RecordingSink implements RewardSink {

        final List<String> items = new ArrayList<>();
        final List<Long> qi = new ArrayList<>();
        int realmSteps;
        final List<String> spells = new ArrayList<>();
        final List<String> techniques = new ArrayList<>();
        final List<String> flags = new ArrayList<>();
        final List<String> unlocks = new ArrayList<>();
        final List<String> reputation = new ArrayList<>();
        private final boolean throwOnItem;

        RecordingSink() {
            this(false);
        }

        RecordingSink(boolean throwOnItem) {
            this.throwOnItem = throwOnItem;
        }

        @Override
        public void giveItem(String itemId, long count) {
            if (throwOnItem) {
                throw new IllegalStateException("背包满了（演练用异常）");
            }
            items.add(itemId + "x" + count);
        }

        @Override
        public void grantQi(long amount) {
            qi.add(amount);
        }

        @Override
        public void advanceRealmStep() {
            realmSteps++;
        }

        @Override
        public void grantSpell(String spellId) {
            spells.add(spellId);
        }

        @Override
        public void grantTechnique(String techniqueId) {
            techniques.add(techniqueId);
        }

        @Override
        public void setFlag(String key) {
            flags.add(key);
        }

        @Override
        public void unlock(String unlockKey) {
            unlocks.add(unlockKey);
        }

        @Override
        public void addReputation(String factionId, int delta) {
            reputation.add(factionId + ":" + delta);
        }
    }

    private static ConditionExpression.Context dslContext(int realm) {
        ConditionExpression.Context context =
                new ConditionExpression.Context() {
                    @Override
                    public int realm() {
                        return realm;
                    }

                    @Override
                    public boolean flag(String key) {
                        return false;
                    }

                    @Override
                    public int itemCount(String itemId) {
                        return 0;
                    }

                    @Override
                    public int reputation(String factionId) {
                        return 0;
                    }

                    @Override
                    public boolean questDone(String questId) {
                        return false;
                    }

                    @Override
                    public boolean affinity(String element) {
                        return false;
                    }

                    @Override
                    public int realmOrdinal(String realmId) {
                        return "qili".equals(realmId) ? 1 : "zhuji".equals(realmId) ? 2 : 0;
                    }

                    @Override
                    public int subStage() {
                        return 1;
                    }

                    @Override
                    public int luck() {
                        return 0;
                    }
                };
        return context;
    }

    @Test
    void parsesChapterEntryAndQuestIds() {
        QuestBook book = book();

        assertEquals("prologue", book.chapter());
        assertEquals("quest_a_01", book.entryId());
        assertEquals(java.util.Set.of("quest_a_01", "quest_b_01", "quest_c_01"), book.questIds());
    }

    @Test
    void entryCompletesAndGrantsFlagPlusFlagsSet() {
        QuestBook book = book();
        QuestState state = new QuestState();
        RecordingSink sink = new RecordingSink();

        List<QuestBook.AppliedReward> applied =
                book.report(QuestBook.ObjectiveType.TALK, "npc_x", 1, state, sink, null);

        assertEquals(1, applied.size());
        assertEquals(QuestBook.RewardType.FLAG, applied.get(0).type());
        assertTrue(state.completed("quest_a_01"));
        assertTrue(sink.flags.contains("fac_qingshi:prologue:met_elder"), "奖励 type=flag");
        assertTrue(sink.flags.contains("prologue:quest:met"), "flags_set 完成时一并置位");
    }

    @Test
    void repeatedEventsOnCompletedQuestAreNoOps() {
        QuestBook book = book();
        QuestState state = new QuestState();
        RecordingSink sink = new RecordingSink();

        book.report(QuestBook.ObjectiveType.TALK, "npc_x", 1, state, sink, null);
        List<QuestBook.AppliedReward> again =
                book.report(QuestBook.ObjectiveType.TALK, "npc_x", 1, state, sink, null);

        assertTrue(again.isEmpty(), "幂等键：完成判定恰好一次");
        assertEquals(2, sink.flags.size(), "重复事件不重复置位");
    }

    @Test
    void progressCapsAtCountAndLockedQuestsDoNotProgress() {
        QuestBook book = book();
        QuestState state = new QuestState();
        RecordingSink sink = new RecordingSink();

        // b 还锁着（a 未完成）：collect 事件不推进
        book.report(QuestBook.ObjectiveType.COLLECT, "item_ningxu", 6, state, sink, null);
        assertEquals(0, state.progress("quest_b_01", "2"));

        book.report(QuestBook.ObjectiveType.TALK, "npc_x", 1, state, sink, null);
        book.report(QuestBook.ObjectiveType.COLLECT, "item_ningxu", 6, state, sink, null);

        assertEquals(6, state.progress("quest_b_01", "2"));
        assertFalse(state.completed("quest_b_01"), "sit 还差着");
        // 再报 6 个：进度封顶 6，不溢出，也不重复发放
        List<QuestBook.AppliedReward> again =
                book.report(QuestBook.ObjectiveType.COLLECT, "item_ningxu", 6, state, sink, null);
        assertTrue(again.isEmpty());
        assertEquals(6, state.progress("quest_b_01", "2"));
    }

    @Test
    void completionRequiresOnlyNonOptionalObjectives() {
        QuestBook book = book();
        QuestState state = new QuestState();
        RecordingSink sink = new RecordingSink();

        book.report(QuestBook.ObjectiveType.TALK, "npc_x", 1, state, sink, null);
        book.report(QuestBook.ObjectiveType.SIT, null, 3, state, sink, null);
        List<QuestBook.AppliedReward> applied =
                book.report(QuestBook.ObjectiveType.COLLECT, "item_ningxu", 6, state, sink, null);

        assertTrue(state.completed("quest_b_01"), "kill 是 optional，不挡完成");
        assertEquals(4, applied.size());
        assertTrue(sink.items.contains("item_lingshix3"));
        assertTrue(sink.qi.contains(40L));
        assertTrue(sink.unlocks.contains("meditation"));
        assertTrue(sink.reputation.contains("fac_qingshi:10"), "rewards 型声望");
        assertTrue(sink.reputation.contains("fac_yuelai:-5"), "reputation_delta 型声望");
    }

    @Test
    void nullTargetObjectiveOnlyMatchesNullTargetEvents() {
        QuestBook book = book();
        QuestState state = new QuestState();
        RecordingSink sink = new RecordingSink();

        book.report(QuestBook.ObjectiveType.TALK, "npc_x", 1, state, sink, null);
        book.report(QuestBook.ObjectiveType.SIT, "somewhere", 1, state, sink, null);

        assertEquals(0, state.progress("quest_b_01", "1"), "带定位的事件不匹配任意地点目标");
    }

    @Test
    void zeroAmountEventsAreNoOps() {
        QuestBook book = book();
        QuestState state = new QuestState();
        RecordingSink sink = new RecordingSink();

        List<QuestBook.AppliedReward> applied =
                book.report(QuestBook.ObjectiveType.TALK, "npc_x", 0, state, sink, null);

        assertTrue(applied.isEmpty());
        assertFalse(state.completed("quest_a_01"));
    }

    @Test
    void conditionsGateAvailabilityFailClosedWithoutContext() {
        QuestBook book = book();
        QuestState state = new QuestState();
        RecordingSink sink = new RecordingSink();

        book.report(QuestBook.ObjectiveType.TALK, "npc_x", 1, state, sink, null);
        book.report(QuestBook.ObjectiveType.SIT, null, 3, state, sink, null);
        book.report(QuestBook.ObjectiveType.COLLECT, "item_ningxu", 6, state, sink, null);

        // b 完成后 c 可接取条件是 realm>=qili：不给 DSL 上下文 = fail-closed，不可接取
        assertFalse(book.isAvailable(state, "quest_c_01", null));
        List<QuestBook.AppliedReward> blocked =
                book.report(
                        QuestBook.ObjectiveType.BREAKTHROUGH,
                        "bs_qili_zhuji",
                        1,
                        state,
                        sink,
                        null);
        assertTrue(blocked.isEmpty());
        assertEquals(0, state.progress("quest_c_01", "1"));
    }

    @Test
    void conditionsEvaluateWhenContextIsGiven() {
        QuestBook book = book();
        QuestState state = new QuestState();
        RecordingSink sink = new RecordingSink();

        book.report(QuestBook.ObjectiveType.TALK, "npc_x", 1, state, sink, null);
        book.report(QuestBook.ObjectiveType.SIT, null, 3, state, sink, null);
        book.report(QuestBook.ObjectiveType.COLLECT, "item_ningxu", 6, state, sink, null);
        List<QuestBook.AppliedReward> applied =
                book.report(
                        QuestBook.ObjectiveType.BREAKTHROUGH,
                        "bs_qili_zhuji",
                        1,
                        state,
                        sink,
                        dslContext(1));

        assertTrue(state.completed("quest_c_01"));
        assertTrue(sink.techniques.contains("tech_qingxin_jue"));
        assertTrue(sink.spells.contains("spell_prologue_huodan"));
        assertEquals(1, sink.realmSteps);
        assertEquals(3, applied.size());
    }

    @Test
    void sinkFailureLeavesStateUnmarkedForReplay() {
        QuestBook book = book();
        QuestState state = new QuestState();
        RecordingSink failing = new RecordingSink(true);

        book.report(QuestBook.ObjectiveType.TALK, "npc_x", 1, state, new RecordingSink(), null);
        book.report(QuestBook.ObjectiveType.SIT, null, 3, state, new RecordingSink(), null);

        assertThrows(
                IllegalStateException.class,
                () ->
                        book.report(
                                QuestBook.ObjectiveType.COLLECT,
                                "item_ningxu",
                                6,
                                state,
                                failing,
                                null));
        assertFalse(state.completed("quest_b_01"), "完成态必须等 sink 成功才落");
        // 重试：进度已满也必须能再次触发发放（否则任务永久卡死）
        List<QuestBook.AppliedReward> retried =
                book.report(
                        QuestBook.ObjectiveType.COLLECT,
                        "item_ningxu",
                        6,
                        state,
                        new RecordingSink(),
                        null);
        assertTrue(state.completed("quest_b_01"), "重试后完成");
        assertEquals(4, retried.size());
    }

    @Test
    void snapshotRestoresProgressForAPersistenceAdapter() {
        QuestBook book = book();
        QuestState state = new QuestState();
        RecordingSink sink = new RecordingSink();
        book.report(QuestBook.ObjectiveType.TALK, "npc_x", 1, state, sink, null);
        book.report(QuestBook.ObjectiveType.SIT, null, 2, state, sink, null);

        QuestState restored = QuestState.restore(state.snapshot());

        assertTrue(restored.completed("quest_a_01"));
        assertEquals(2, restored.progress("quest_b_01", "1"));
        // 恢复后的状态在引擎里继续可用
        RecordingSink continued = new RecordingSink();
        book.report(QuestBook.ObjectiveType.SIT, null, 1, restored, continued, null);
        assertEquals(3, restored.progress("quest_b_01", "1"));
    }

    // ===== 解析防御（热更 zip 绕过构建期门禁时的最后一道） =====

    private static IllegalStateException parseError(String product) {
        return assertThrows(
                IllegalStateException.class,
                () -> QuestBook.parse(JsonParser.parseString(product).getAsJsonObject()));
    }

    @Test
    void rejectsUnknownObjectiveType() {
        IllegalStateException error =
                parseError(CHAPTER.replace("\"type\": \"talk\"", "\"type\": \"fly\""));
        assertTrue(error.getMessage().contains("not in JSON_SCHEMA"), error.getMessage());
    }

    @Test
    void rejectsZeroCountObjective() {
        IllegalStateException error =
                parseError(
                        CHAPTER.replace(
                                "\"count\": 1, \"optional\": false",
                                "\"count\": 0, \"optional\": false"));
        assertTrue(error.getMessage().contains("count 0"), error.getMessage());
    }

    @Test
    void rejectsDuplicateQuestIds() {
        IllegalStateException error =
                parseError(CHAPTER.replace("\"quest_b_01\"", "\"quest_a_01\""));
        assertTrue(error.getMessage().contains("declared twice"), error.getMessage());
    }

    @Test
    void rejectsDanglingPrerequisite() {
        IllegalStateException error =
                parseError(
                        CHAPTER.replace(
                                "\"prerequisites\": [\"quest_b_01\"]",
                                "\"prerequisites\": [\"ghost\"]"));
        assertTrue(error.getMessage().contains("dangling prerequisite"), error.getMessage());
    }

    @Test
    void rejectsRepeatableQuestsThisPhase() {
        IllegalStateException error =
                parseError(
                        CHAPTER.replace("\"repeatable\": \"no\"", "\"repeatable\": \"per_day\""));
        assertTrue(error.getMessage().contains("per_day"), error.getMessage());
    }

    @Test
    void rejectsAQuestWithOnlyOptionalObjectives() {
        String product =
                CHAPTER.replace(
                                "\"type\": \"sit\", \"count\": 3, \"optional\": false",
                                "\"type\": \"sit\", \"count\": 3, \"optional\": true")
                        .replace(
                                "\"type\": \"collect\", \"target\": \"item_ningxu\", \"count\": 6, \"optional\": false",
                                "\"type\": \"collect\", \"target\": \"item_ningxu\", \"count\": 6, \"optional\": true");
        IllegalStateException error = parseError(product);
        assertTrue(error.getMessage().contains("no required objective"), error.getMessage());
    }

    @Test
    void rejectsIllegalConditionAtParse() {
        IllegalStateException error = parseError(CHAPTER.replace("realm>=qili", "mana>=5"));
        assertTrue(error.getMessage().contains("illegal condition"), error.getMessage());
    }
}

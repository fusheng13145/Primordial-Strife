package com.strife.quest.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.strife.quest.dsl.ConditionExpression;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 悬赏 re-arm 语义（ADR-033）：完成 → 立即重新可接、进度清零、奖励重新可发。
 *
 * <p>非 repeatable 任务的"奖励一次"幂等不受影响（同批回归）；`repeatable=yes` 仍 fail-fast。
 */
class ReArmBountyTest {

    private static QuestBook book(String json) {
        return QuestBook.parse(JsonParser.parseString(json).getAsJsonObject());
    }

    private static final String RE_ARM =
            """
            {"id":"quest_ep1_bounty_tier1_01","chapter":"ep1","quests":[
              {"id":"quest_ep1_bounty_tier1_01","entry":true,
               "objectives":[{"id":"1","type":"kill","count":2,"optional":false}],
               "rewards":[{"type":"item","id":"item_lingshi","count":6}],"repeatable":"re-arm"}]}
            """;

    private static final class Run {
        final QuestBook book = book(RE_ARM);
        final QuestState state = QuestState.of(Set.of(), Map.of());
        final RecordingSink sink = new RecordingSink();

        int report(long kills) {
            int before = sink.itemCount();
            book.report(QuestBook.ObjectiveType.KILL, null, kills, state, sink, ctx());
            return sink.itemCount() - before;
        }

        boolean completed() {
            return state.completed("quest_ep1_bounty_tier1_01");
        }
    }

    /** 记账 sink（lingshi 计数）。 */
    private static final class RecordingSink implements RewardSink {
        long lingshi;

        @Override
        public void giveItem(String itemId, long count) {
            if ("item_lingshi".equals(itemId)) {
                lingshi += count;
            }
        }

        int itemCount() {
            return (int) lingshi;
        }

        @Override
        public void grantQi(long amount) {}

        @Override
        public void advanceRealmStep() {}

        @Override
        public void grantSpell(String spellId) {}

        @Override
        public void grantTechnique(String techniqueId) {}

        @Override
        public void setFlag(String key) {}

        @Override
        public void unlock(String unlockKey) {}

        @Override
        public void addReputation(String factionId, int delta) {}
    }

    private static ConditionExpression.Context ctx() {
        return new ConditionExpression.Context() {
            @Override
            public int realm() {
                return 0;
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
                return 0;
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
    }

    @Test
    void reArmQuestReoffersImmediatelyAfterCompletion() {
        Run run = new Run();
        assertEquals(6, run.report(2), "首轮完成：灵石 ×6");
        assertFalse(run.completed(), "re-arm 不落完成态");
        // 立即重新可接：再杀 1 只进度 1/2，不重复发奖。
        assertEquals(0, run.report(1));
        assertFalse(run.completed());
        assertEquals(6, run.sink.lingshi, "未再完成前不得再发");
        // 第二轮完成：再发一次——刷取成本即怪物强度（ADR-033 语义）。
        assertEquals(6, run.report(1), "第二轮完成再发灵石 ×6");
        assertFalse(run.completed());
        assertEquals(12, run.sink.lingshi);
    }

    @Test
    void singleShotQuestsKeepTheirIdempotence() {
        QuestBook book =
                book(
                        """
                {"id":"quest_once_01","chapter":"ep1","quests":[
                  {"id":"quest_once_01","entry":true,
                   "objectives":[{"id":"1","type":"kill","count":2,"optional":false}],
                   "rewards":[{"type":"item","id":"item_lingshi","count":6}],"repeatable":"no"}]}
                """);
        var state = QuestState.of(Set.of(), Map.of());
        var sink = new RecordingSink();
        book.report(QuestBook.ObjectiveType.KILL, null, 2, state, sink, ctx());
        assertEquals(6, sink.lingshi);
        book.report(QuestBook.ObjectiveType.KILL, null, 5, state, sink, ctx());
        assertEquals(6, sink.lingshi, "no 口径的奖励一次幂等不得被 re-arm 波及");
        assertTrue(state.completed("quest_once_01"));
    }

    @Test
    void legacyRepeatableYesStillFailsFast() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        book(
                                """
                {"id":"quest_x","chapter":"ep1","quests":[
                  {"id":"quest_x","entry":true,
                   "objectives":[{"id":"1","type":"kill","count":1,"optional":false}],
                   "rewards":[],"repeatable":"yes"}]}
                """),
                "per_* 语义不冒领（ADR-033）");
    }

    @Test
    void shippedEp1BountiesAreReArm() throws java.io.IOException {
        // ep1 产物不在既有装具里——直接读 jar 内资源核对三档悬赏的 repeatable 值。
        String raw;
        try (var stream = getClass().getResourceAsStream("/data/strife/strife_quests/ep1.json")) {
            raw =
                    new String(
                            java.util.Objects.requireNonNull(stream, "ep1 quest product missing")
                                    .readAllBytes(),
                            java.nio.charset.StandardCharsets.UTF_8);
        }
        var product = com.google.gson.JsonParser.parseString(raw);
        assertTrue(
                product.toString().contains("\"repeatable\": \"re-arm\"")
                        || product.toString().contains("\"repeatable\":\"re-arm\""),
                "三档悬赏必须以 re-arm 入产物");
    }
}

package com.strife.quest.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.strife.testing.ShippedProducts;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 多章任务簿（2026-11-06 章节接线）：序章 / ch1 / ep2 三种真实产物一起装载后，追踪条、推进与可用性都不再只认序章。
 *
 * <p>这些用例的失败形态是"内容明明在 jar 里，游戏里却永远不推进"——本章接线前的真实状态。
 */
class QuestBooksTest {

    private static QuestBooks shipped() {
        return QuestBooks.of(
                List.of(
                        QuestBook.parse(ShippedProducts.prologueQuests()),
                        QuestBook.parse(ShippedProducts.ch1Quests()),
                        QuestBook.parse(ShippedProducts.ep2Quests())));
    }

    /** 只记发放结果的最小 sink（奖励落地不属于本层）。 */
    private static final class RecordingSink implements RewardSink {

        final List<String> items = new ArrayList<>();

        @Override
        public void giveItem(String itemId, long count) {
            items.add(itemId + ":" + count);
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

    @Test
    void chaptersLoadInPrologueThenChapterOrder() {
        assertEquals(
                List.of("prologue", "ch1", "ep2"),
                shipped().books().stream().map(QuestBook::chapter).toList());
    }

    @Test
    void trackerFallsThroughToChapterOneOnceThePrologueIsDone() {
        QuestBooks books = shipped();
        QuestState state = QuestState.of(books.books().get(0).questIds(), Map.of());

        QuestBook.QuestView view = books.trackable(state, null).orElseThrow();

        assertEquals("quest_ch1_pingcang_survey_01", view.id());
        assertEquals(
                List.of("1", "2"),
                view.objectives().stream().map(QuestBook.ObjectiveView::id).toList());
        assertEquals(
                List.of(QuestBook.ObjectiveType.REACH, QuestBook.ObjectiveType.COLLECT),
                view.objectives().stream().map(QuestBook.ObjectiveView::type).toList());
        assertEquals("qi_wenjiang", view.objectives().get(0).target());
        assertEquals("item_yaocai", view.objectives().get(1).target());
    }

    @Test
    void surviveReportReachesTheEp2WillTrialAndItsReward() {
        QuestBooks books = shipped();
        QuestState state =
                QuestState.of(
                        Set.of(
                                "quest_ep2_trial_entry_01",
                                "quest_ep2_trial_courage_01",
                                "quest_ep2_trial_wisdom_01"),
                        Map.of());
        RecordingSink sink = new RecordingSink();

        books.report(QuestBook.ObjectiveType.SURVIVE, null, 120, state, sink, null);

        assertTrue(state.completed("quest_ep2_trial_will_01"), "SURVIVE 120 秒必须能收束心性试炼——试炼台的唯一推进口");
        assertEquals(List.of("item_lingshi:15"), sink.items, "心性试炼奖励按表发放");
        assertEquals(
                0,
                state.progress("quest_ch1_pingcang_reckon_01", "1"),
                "未满足前置的 ch1 守台任务不得被同一事件顺带推进");
    }

    @Test
    void availabilityFollowsPrerequisitesNotTheActivationSet() {
        QuestBooks books = shipped();
        QuestState before =
                QuestState.of(
                        Set.of("quest_ep2_trial_entry_01", "quest_ep2_trial_courage_01"), Map.of());

        assertFalse(
                books.isAvailable(before, "quest_ep2_trial_will_01", null),
                "智慧试炼未完成时试炼台必须拒绝——前置链是真实门槛");

        QuestState after =
                QuestState.of(
                        Set.of(
                                "quest_ep2_trial_entry_01",
                                "quest_ep2_trial_courage_01",
                                "quest_ep2_trial_wisdom_01"),
                        Map.of());
        assertTrue(
                books.isAvailable(after, "quest_ep2_trial_will_01", null),
                "前置齐全即可接——不能要求 start_quest 激活（当前内容没有 start_quest）");
    }
}

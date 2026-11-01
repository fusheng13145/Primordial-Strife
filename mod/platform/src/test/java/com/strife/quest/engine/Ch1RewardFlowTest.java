package com.strife.quest.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.strife.quest.dsl.ConditionExpression;
import com.strife.quest.engine.QuestBook.ObjectiveType;
import com.strife.testing.ShippedProducts;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 第一章全链路奖励缝 ×【真实产物】（P1-04）。
 *
 * <p>与 {@code ProloguePlaythroughTest} 同口径：七节点按 DAG 走通、奖励沿链到账、终点收束；
 * 本用例额外钉两条第一章特有纪律——<b>奖励幂等</b>（同一目标重复上报不重复发放）与<b>告别收束</b> （farewell 是链尾，完成即章节无可接任务）。数值 [拟] 随 G-5
 * 校准，形状是钉子。
 */
class Ch1RewardFlowTest {

    /** 记账式奖励缝：只记数不做真实发放（对账用）。 */
    private static final class RecordingSink implements RewardSink {
        final Map<String, Long> items = new HashMap<>();
        long qi;
        final List<String> flags = new ArrayList<>();
        final List<String> unlocks = new ArrayList<>();
        final List<String> reputation = new ArrayList<>();
        final List<String> techniques = new ArrayList<>();
        final List<String> spells = new ArrayList<>();

        @Override
        public void giveItem(String itemId, long count) {
            items.merge(itemId, count, Long::sum);
        }

        @Override
        public void grantQi(long amount) {
            qi += amount;
        }

        @Override
        public void advanceRealmStep() {}

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

    private static final class Playthrough {
        final QuestBook book = QuestBook.parse(ShippedProducts.ch1Quests());
        final QuestState state = QuestState.of(java.util.Set.of(), Map.of());
        final RecordingSink sink = new RecordingSink();
        final Map<String, Integer> bag = new HashMap<>();

        void report(ObjectiveType type, String target, long amount) {
            book.report(type, target, amount, state, sink, context());
        }

        /** 装配层 syncCollect 同款对账循环（背包由虚拟库存供给）。 */
        void syncCollect() {
            for (int guard = 0; guard < 16; guard++) {
                List<QuestBook.CollectDelta> deltas =
                        book.collectDeltas(state, context(), itemId -> bag.getOrDefault(itemId, 0));
                if (deltas.isEmpty()) {
                    return;
                }
                for (QuestBook.CollectDelta delta : deltas) {
                    report(ObjectiveType.COLLECT, delta.target(), delta.amount());
                }
            }
            throw new AssertionError("COLLECT 对账 16 轮未收敛——级联发放可能成环");
        }

        boolean completed(String questId) {
            return state.completed(questId);
        }
    }

    private static ConditionExpression.Context context() {
        return new ConditionExpression.Context() {
            @Override
            public int realm() {
                return 1;
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
                return 1;
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

    /** 把整章推到 farewell 完成态（各用例共用）。 */
    private Playthrough walkToTheEnd() {
        Playthrough run = new Playthrough();

        // 勘脉：抵达文津渡 + 采妖材×3
        run.report(ObjectiveType.REACH, "qi_wenjiang", 1);
        for (int i = 0; i < 3; i++) {
            run.bag.merge("item_yaocai", 1, Integer::sum);
            run.report(ObjectiveType.COLLECT, "item_yaocai", 1);
        }
        assertTrue(run.completed("quest_ch1_pingcang_survey_01"), "勘脉");

        // 借势：挑灯人消息买卖
        run.report(ObjectiveType.TALK, "npc_yuelai_deng", 1);
        assertTrue(run.completed("quest_ch1_pingcang_offer_01"), "借势");

        // 担责：向苏药农交付
        run.report(ObjectiveType.DELIVER, "npc_yaonong_su", 1);
        assertTrue(run.completed("quest_ch1_pingcang_debt_01"), "担责");

        // 清剿妖兽群 ×5
        for (int i = 0; i < 5; i++) {
            run.report(ObjectiveType.KILL, null, 1);
        }
        assertTrue(run.completed("quest_ch1_pingcang_raid_01"), "清剿");

        // 守台 180 刻（timer 失败回自身——成功路径不走 fail_goto）
        run.report(ObjectiveType.SURVIVE, null, 180);
        assertTrue(run.completed("quest_ch1_pingcang_reckon_01"), "守台");

        // 金丹门槛：灵石 ×20（对账进包）+ 突破
        run.bag.merge("item_lingshi", 20, Integer::sum);
        run.syncCollect();
        run.report(ObjectiveType.BREAKTHROUGH, "bs_zhuji_jindan", 1);
        assertTrue(run.completed("quest_ch1_jindan_gate_01"), "金丹门槛");

        // 告别：挑灯人兑现门路
        run.report(ObjectiveType.TALK, "npc_yuelai_deng", 1);
        assertTrue(run.completed("quest_ch1_farewell_01"), "告别");
        return run;
    }

    @Test
    @DisplayName("七节点按 DAG 顺序走通，终点收束为空")
    void chapterWalksThroughAndClosesOut() {
        Playthrough run = walkToTheEnd();

        assertTrue(
                run.book.trackable(QuestState.of(stateOf(run), Map.of()), null).isEmpty(),
                "告别完成后第一章应无可接任务");
    }

    @Test
    @DisplayName("奖励沿链到账：借势给解锁与修为，担责给灵石与声望，告别给门路与标记")
    void rewardsLandAlongTheChain() {
        Playthrough run = walkToTheEnd();

        // 全链修为：勘脉 80 + 借势 120 + 守台 200
        assertEquals(400L, run.sink.qi, "勘脉 80 + 借势 120 + 守台 200");
        assertTrue(run.sink.unlocks.contains("quest_line_ch1"), "借势解锁 ch1 任务线标记");

        // 青石观声望 +5（担责）
        assertTrue(run.sink.reputation.contains("fac_qingshi:5"), "担责声望增量");

        // 告别：月来渡声望 +5 + favor 标记；灵石总账 = 担责 10 + 告别 10
        assertEquals(20L, run.sink.items.getOrDefault("item_lingshi", 0L), "担责 10 + 告别 10");
        assertTrue(run.sink.reputation.contains("fac_yuelai:5"), "告别声望增量");
        assertTrue(run.sink.flags.contains("fac_yuelai:ch1:favor_earned"), "告别收束标记");
        // flag 总账：survey_done + war_rumor×2（守台与金丹门槛各发一次）+ favor_earned
        assertEquals(4, run.sink.flags.size(), "flag 发放次数实账");
        assertEquals(
                java.util.Set.of(
                        "fac_qingshi:ch1:survey_done",
                        "fac_qingshi:ch1:war_rumor",
                        "fac_yuelai:ch1:favor_earned"),
                new java.util.HashSet<>(run.sink.flags),
                "flag 集合实账（war_rumor 去重后 3 个）");
    }

    @Test
    @DisplayName("奖励幂等：告别完成后再报同一 talk，灵石/声望/标记不再翻倍")
    void rewardsAreIdempotent() {
        Playthrough run = walkToTheEnd();
        long lingshiBefore = run.sink.items.getOrDefault("item_lingshi", 0L);
        long qiBefore = run.sink.qi;
        int repBefore = run.sink.reputation.size();
        int flagsBefore = run.sink.flags.size();

        // 重复上报（对话再走一次 / 事件重放）——已完成的任务必须忽略
        run.report(ObjectiveType.TALK, "npc_yuelai_deng", 1);
        run.report(ObjectiveType.TALK, "npc_yaonong_su", 1);

        assertEquals(lingshiBefore, run.sink.items.getOrDefault("item_lingshi", 0L), "灵石不得重复发");
        assertEquals(qiBefore, run.sink.qi, "修为不得重复发");
        assertEquals(repBefore, run.sink.reputation.size(), "声望不得重复记");
        assertEquals(flagsBefore, run.sink.flags.size(), "标记不得重复发");
    }

    @Test
    @DisplayName("告别选项带 quest_done 门控（产物级契约；求值语义由 ConditionDslTest 覆盖）")
    void farewellOptionCarriesTheQuestDoneGate() {
        com.google.gson.JsonObject welcomeNode = null;
        // 产物形状：一章一文件，trees[] = 树对象（id/npc/root/nodes…），节点在 tree.nodes[]。
        for (var bookProduct : ShippedProducts.allDialogTrees()) {
            for (var e : bookProduct.getAsJsonArray("trees")) {
                com.google.gson.JsonObject tree = e.getAsJsonObject();
                if (!"dlg_ch1_offer"
                        .equals(tree.get("id") == null ? null : tree.get("id").getAsString())) {
                    continue;
                }
                for (var n : tree.getAsJsonArray("nodes")) {
                    com.google.gson.JsonObject node = n.getAsJsonObject();
                    if ("nd_offer_welcome"
                            .equals(node.get("id") == null ? null : node.get("id").getAsString())) {
                        welcomeNode = node;
                    }
                }
            }
        }
        assertTrue(welcomeNode != null, "挑灯人根节点必须存在");
        boolean hasGatedFarewellOption = false;
        for (var opt : welcomeNode.getAsJsonArray("options")) {
            com.google.gson.JsonObject option = opt.getAsJsonObject();
            var conditions = option.get("conditions");
            if (conditions != null
                    && conditions.getAsString().contains("quest_done(quest_ch1_jindan_gate_01)")) {
                hasGatedFarewellOption = true;
                assertEquals("nd_offer_farewell", option.get("next").getAsString(), "门控选项必须指向告别节点");
            }
        }
        assertTrue(hasGatedFarewellOption, "告别选项必须带 quest_done 门控（金丹前不可见）");
    }

    private static java.util.Set<String> stateOf(Playthrough run) {
        return java.util.Set.copyOf(completedIds(run));
    }

    private static List<String> completedIds(Playthrough run) {
        return List.of(
                "quest_ch1_pingcang_survey_01",
                "quest_ch1_pingcang_offer_01",
                "quest_ch1_pingcang_debt_01",
                "quest_ch1_pingcang_raid_01",
                "quest_ch1_pingcang_reckon_01",
                "quest_ch1_jindan_gate_01",
                "quest_ch1_farewell_01");
    }
}

package com.strife.quest.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.strife.quest.dsl.ConditionExpression;
import com.strife.testing.ShippedProducts;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 序章十节点全链路实走（docs/07 §4"新手引导 ≤45 分钟序章"的自动化部分）：拿<b>随 jar 分发的真实 prologue.json</b>
 * 当剧本，按玩家视角把十节点从"读碑文"走到"择宗门"，全部事件经 {@link QuestBook#report} 与 COLLECT 对账推进——与装配层 {@code
 * QuestAdapter} 走的是同一条代码路径，只是把 Minecraft 依赖换成了录制器。
 *
 * <p>这条测试把三类"单节点单测测不出"的故障钉住：① DAG 前置序写反（后节点比前节点先可做）； ② 级联对账断了（herb 环奖励的灵石进不了背包，prepare 环的 collect
 * 永远差 1）；③ 奖励缝漏发（功法/解锁在 中途节点丢了，通关时才发现背包是空的）。
 */
class ProloguePlaythroughTest {

    /** 录制型 sink：所有奖励缝的出料都记下来；giveItem 同步写入虚拟背包（真实装配层语义：发放即进包）。 */
    private static final class RecordingSink implements RewardSink {
        final List<String> items = new ArrayList<>();
        final List<Long> qi = new ArrayList<>();
        int realmSteps;
        final List<String> spells = new ArrayList<>();
        final List<String> techniques = new ArrayList<>();
        final List<String> flags = new ArrayList<>();
        final List<String> unlocks = new ArrayList<>();
        final List<String> reputation = new ArrayList<>();
        private final Map<String, Integer> bag;

        RecordingSink(Map<String, Integer> bag) {
            this.bag = bag;
        }

        @Override
        public void giveItem(String itemId, long count) {
            items.add(itemId + "x" + count);
            bag.merge(itemId, (int) Math.min(count, Integer.MAX_VALUE), Integer::sum);
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

    private static final class Playthrough {
        final QuestBook book = QuestBook.parse(ShippedProducts.prologueQuests());
        final QuestState state = new QuestState();
        final Map<String, Integer> bag = new HashMap<>();
        final RecordingSink sink = new RecordingSink(bag);

        void report(QuestBook.ObjectiveType type, String target, long amount) {
            book.report(type, target, amount, state, sink, context());
        }

        /** 装配层 syncCollect 的同款对账循环（背包由测试的虚拟库存供给）。 */
        void syncCollect() {
            for (int guard = 0; guard < 16; guard++) {
                List<QuestBook.CollectDelta> deltas =
                        book.collectDeltas(state, context(), itemId -> bag.getOrDefault(itemId, 0));
                if (deltas.isEmpty()) {
                    return;
                }
                for (QuestBook.CollectDelta delta : deltas) {
                    report(QuestBook.ObjectiveType.COLLECT, delta.target(), delta.amount());
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
    @DisplayName("序章十节点按 DAG 顺序全链路走通，终点 = sect_chosen 标记")
    void allTenNodesCompleteInDagOrder() {
        Playthrough run = new Playthrough();

        // 玩家视角的完整序章（十节点 = STORY §4 的十行）：
        // #1 读碑文 → 长老对话（脚手架在装配层自动触发，这里显式走同一入口）
        run.report(QuestBook.ObjectiveType.TALK, "npc_qingshi_zhizhi", 1);
        assertTrue(run.completed("quest_prologue_root_read_01"), "节点1 读碑文");

        // #2 初次打坐 60 刻
        run.report(QuestBook.ObjectiveType.SIT, null, 60);
        assertTrue(run.completed("quest_prologue_meditation_01"), "节点2 打坐");

        // #3 采药 ×6（逐次拾取，模拟真实背包增量）
        for (int i = 0; i < 6; i++) {
            run.bag.merge("item_ningxu", 1, Integer::sum);
            run.report(QuestBook.ObjectiveType.COLLECT, "item_ningxu", 1);
        }
        assertTrue(run.completed("quest_prologue_herb_pick_01"), "节点3 采药");

        // #4 交付药农 → 奖励 tech_qingxin_jue
        run.report(QuestBook.ObjectiveType.DELIVER, "npc_yaonong_su", 1);
        assertTrue(run.completed("quest_prologue_deliver_01"), "节点4 交付");

        // #5 突破练气（前置只有 #2——DAG 允许与 #3/#4 并行）
        run.report(QuestBook.ObjectiveType.BREAKTHROUGH, "bs_fanren_qili", 1);
        assertTrue(run.completed("quest_prologue_breakthrough_qi_01"), "节点5 突破练气");

        // #6 长老讲故事
        run.report(QuestBook.ObjectiveType.TALK, "npc_qingshi_zhizhi", 1);
        assertTrue(run.completed("quest_prologue_elder_story_01"), "节点6 长老故事");

        // #7 九层：打坐 240 + 击杀 3
        run.report(QuestBook.ObjectiveType.SIT, null, 240);
        for (int i = 0; i < 3; i++) {
            run.report(QuestBook.ObjectiveType.KILL, null, 1);
        }
        assertTrue(run.completed("quest_prologue_nine_layers_01"), "节点7 九层");

        // #8 备筑基：断血 ×2 + 灵石 ×1。灵石来自 #3 的奖励（herb 环发了 item_lingshi×3），
        // 断血假设由击杀掉落——直接进背包后走对账（与登录对账同一机制）
        run.bag.merge("item_duanxue", 2, Integer::sum);
        run.syncCollect();
        assertTrue(run.completed("quest_prologue_prepare_zhuji_01"), "节点8 备筑基（级联对账）");

        // #9 突破筑基
        run.report(QuestBook.ObjectiveType.BREAKTHROUGH, "bs_qili_zhuji", 1);
        assertTrue(run.completed("quest_prologue_breakthrough_zhuji_01"), "节点9 突破筑基");

        // #10 择宗门
        run.report(QuestBook.ObjectiveType.TALK, "npc_taishang_wu", 1);
        assertTrue(run.completed("quest_prologue_sect_choice_01"), "节点10 择宗门");

        // 终点标记 + 全章账目：序章 5 个 H3 标记（met_elder/first_qi/saw_debt/ready_to_zhuji/sect_chosen）
        assertTrue(run.sink.flags.contains("prologue:sect_chosen"), "通关标记必须发出");
        assertEquals(
                5, run.sink.flags.size(), "序章恰好 5 个 flag 奖励，多记/漏记都是 DAG 或缝的故障: " + run.sink.flags);
        assertEquals(
                Set.of(
                        "fac_qingshi:prologue:met_elder",
                        "prologue:first_qi",
                        "fac_qingshi:prologue:saw_debt",
                        "prologue:ready_to_zhuji",
                        "prologue:sect_chosen"),
                new java.util.HashSet<>(run.sink.flags));
    }

    @Test
    @DisplayName("全链路奖励对账：功法在 #4 到手，三项战斗/炼丹解锁在 #9 到手")
    void rewardsLandAlongTheChain() {
        Playthrough run = new Playthrough();
        run.report(QuestBook.ObjectiveType.TALK, "npc_qingshi_zhizhi", 1);
        run.report(QuestBook.ObjectiveType.SIT, null, 60);
        for (int i = 0; i < 6; i++) {
            run.bag.merge("item_ningxu", 1, Integer::sum);
            run.report(QuestBook.ObjectiveType.COLLECT, "item_ningxu", 1);
        }
        run.report(QuestBook.ObjectiveType.DELIVER, "npc_yaonong_su", 1);

        // 序章唯一的功法奖励必须在 #4 落地（这是"grantTechnique 只弹提示不发"那个历史缺陷的绝杀断言）
        assertTrue(
                run.sink.techniques.contains("tech_qingxin_jue"),
                "交付完成后功法 tech_qingxin_jue 必须经奖励缝真实发放，实际: " + run.sink.techniques);

        // 打坐与突破的修为奖励逐笔到达
        assertEquals(List.of(40L, 60L), run.sink.qi, "#2 与 #4 的修为奖励");

        // 走完全链路后再验 #9 的三个解锁
        run.report(QuestBook.ObjectiveType.BREAKTHROUGH, "bs_fanren_qili", 1);
        run.report(QuestBook.ObjectiveType.TALK, "npc_qingshi_zhizhi", 1);
        run.report(QuestBook.ObjectiveType.SIT, null, 240);
        for (int i = 0; i < 3; i++) {
            run.report(QuestBook.ObjectiveType.KILL, null, 1);
        }
        run.bag.merge("item_duanxue", 2, Integer::sum);
        run.syncCollect();
        run.report(QuestBook.ObjectiveType.BREAKTHROUGH, "bs_qili_zhuji", 1);
        run.report(QuestBook.ObjectiveType.TALK, "npc_taishang_wu", 1);

        Set<String> unlockSet = new LinkedHashSet<>(run.sink.unlocks);
        assertTrue(
                unlockSet.containsAll(
                        Set.of(
                                "meditation",
                                "cultivation_panel",
                                "spiritroot_panel",
                                "technique_equip",
                                "spell_cast",
                                "pill_crafting")),
                "六个解锁键必须全部经奖励缝发出，实际: " + unlockSet);
        assertTrue(run.sink.realmSteps == 0, "序章没有 realm_step 奖励，缝不得被误触");
    }
}

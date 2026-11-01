package com.strife.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.strife.quest.engine.QuestBook;
import com.strife.quest.engine.QuestState;
import com.strife.testing.ShippedProducts;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 第一章 ×【真实产物】的全链路用例（P1-03）。
 *
 * <p>与 {@code ProloguePlaythroughTest} 同口径：章节按 prerequisites 链式推进（勘脉 → 消息买卖 → 担责 → 清剿 → 守台 → 金丹门槛 →
 * 告别），talk/deliver 目标的 NPC 必须有摆放（产物×产物对齐——追踪条导航 经地点桥靠这份数据）。数值引用随 G-5 校准，链路形状与 ID 是本用例的钉子。
 */
class Ch1PlaythroughTest {

    private static QuestBook ch1() {
        return QuestBook.parse(ShippedProducts.ch1Quests());
    }

    @Test
    void chapterWalksTheFullChainThroughFarewell() {
        // 链条顺序（STORY §5）：survey → offer → debt → raid → reckon → jindan_gate → farewell。
        String[] chain = {
            "quest_ch1_pingcang_survey_01",
            "quest_ch1_pingcang_offer_01",
            "quest_ch1_pingcang_debt_01",
            "quest_ch1_pingcang_raid_01",
            "quest_ch1_pingcang_reckon_01",
            "quest_ch1_jindan_gate_01",
            "quest_ch1_farewell_01",
        };

        java.util.HashSet<String> done = new java.util.HashSet<>();
        for (String expected : chain) {
            QuestBook.QuestView view =
                    ch1().trackable(QuestState.of(done, java.util.Map.of()), null).orElseThrow();
            assertEquals(expected, view.id(), "链式推进到 " + expected + " 时 trackable 应指向它");
            done.add(expected);
        }

        // 全链走完：trackable 指向支线（可选内容仍开放——世界不为玩家而转，但为玩家留门），
        // 支线也做完才真正清空。
        QuestBook.QuestView afterMain =
                ch1().trackable(QuestState.of(done, java.util.Map.of()), null).orElseThrow();
        assertEquals("quest_ch1_garden_side_01", afterMain.id(), "主链收束后应轮到表尾支线（药圃的账）");

        done.add("quest_ch1_garden_side_01");
        QuestBook.QuestView ledger =
                ch1().trackable(QuestState.of(done, java.util.Map.of()), null).orElseThrow();
        assertEquals("quest_ch1_ledger_side_01", ledger.id(), "第二条支线（渡口的暗账）");

        done.add("quest_ch1_ledger_side_01");
        assertTrue(
                ch1().trackable(QuestState.of(done, java.util.Map.of()), null).isEmpty(),
                "主链 + 支线全部完成后第一章清空");
    }

    @Test
    void skippingSideQuestsNeverBlocksTheMainChain() {
        // P1-05 判据：跳过支线仍可通关——主链每一步 trackable 都指向主线任务（表序保证支线不插队）。
        String[] mainChain = {
            "quest_ch1_pingcang_survey_01",
            "quest_ch1_pingcang_offer_01",
            "quest_ch1_pingcang_debt_01",
            "quest_ch1_pingcang_raid_01",
            "quest_ch1_pingcang_reckon_01",
            "quest_ch1_jindan_gate_01",
            "quest_ch1_farewell_01",
        };
        java.util.HashSet<String> done = new java.util.HashSet<>();
        for (String expected : mainChain) {
            QuestBook.QuestView view =
                    ch1().trackable(QuestState.of(done, java.util.Map.of()), null).orElseThrow();
            assertEquals(expected, view.id(), "主链推进不得被支线抢占：" + expected);
            done.add(expected);
        }
        // 主链收束后支线仍可回头补做（可错过 ≠ 永久关闭）。
        assertEquals(
                "quest_ch1_garden_side_01",
                ch1().trackable(QuestState.of(done, java.util.Map.of()), null).orElseThrow().id(),
                "支线在主链完成后仍开放");
    }

    @Test
    void talkAndDeliverTargetsHavePlacements() {
        // 产物×产物：任务里出现的 npc_ 目标必须在 strife_npcs 摆放产物里——否则追踪条没坐标、
        // 世界里没有可对话的实体（V-REF 查表内引用，跨表 NPC 存在性由本用例钉住）。
        Set<String> placed = new java.util.HashSet<>();
        for (com.google.gson.JsonObject placement : ShippedProducts.allNpcPlacements()) {
            placed.add(placement.get("id").getAsString());
        }
        assertTrue(placed.contains("npc_yuelai_deng"), "挑灯人必须有摆放（ch1 offer/告别）");
        assertTrue(placed.contains("npc_yaonong_su"), "苏药农必须有摆放（ch1 debt 交付）");
    }

    @Test
    void farewellNodeIsTheChainTailWithTalkObjective() {
        // 直接对产物断言（QuestBook 只暴露 trackable 视图，不暴露全量行视图）。
        com.google.gson.JsonObject farewell = null;
        for (com.google.gson.JsonElement e : ShippedProducts.ch1Quests().getAsJsonArray("quests")) {
            com.google.gson.JsonObject q = e.getAsJsonObject();
            if (q.get("id").getAsString().equals("quest_ch1_farewell_01")) {
                farewell = q;
            }
        }
        assertTrue(farewell != null, "告别节点必须存在");
        assertEquals(
                "quest_ch1_jindan_gate_01",
                farewell.get("prerequisites").getAsString(),
                "告别挂在金丹门槛之后——章节收束语义");
        com.google.gson.JsonArray objectives = farewell.getAsJsonArray("objectives");
        assertEquals(1, objectives.size());
        com.google.gson.JsonObject objective = objectives.get(0).getAsJsonObject();
        assertEquals("talk", objective.get("type").getAsString());
        assertEquals("npc_yuelai_deng", objective.get("target").getAsString());
    }

    @Test
    void chapterProductsShipWithGeneratedMarker() {
        assertTrue(
                ShippedProducts.ch1Quests()
                        .get("@generated")
                        .getAsString()
                        .contains("quests_ch1.csv"),
                "第一章产物必须由契约表直出（防手写夹具混入）");
    }
}

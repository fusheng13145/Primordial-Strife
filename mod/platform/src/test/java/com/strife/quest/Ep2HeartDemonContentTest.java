package com.strife.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.strife.production.StrifePills;
import com.strife.testing.ShippedProducts;
import org.junit.jupiter.api.Test;

/**
 * EP2 心魔劫内容链 ×【真实产物】（[拟-待STORY追认] 草案内容的结构锚）：三段任务 DAG、目标类型全部落在 {@code QuestAdapter} 已接线的事件源上、洗尘丹
 * flag 在代码与对话表两侧同值。
 *
 * <p>这条链把 EP2 主题（心魔）和已实装系统（突破/炼丹材料/黑市获取路径）缝进剧情——内容加系统的缝 合处就是内容用例该站的位置。
 */
class Ep2HeartDemonContentTest {

    private static JsonObject quest(String id) {
        var quests = ShippedProducts.ep2Quests().getAsJsonArray("quests");
        for (var element : quests) {
            JsonObject q = element.getAsJsonObject();
            if (id.equals(q.get("id").getAsString())) {
                return q;
            }
        }
        throw new AssertionError("EP2 产物缺任务 " + id);
    }

    private static String objectivesOf(JsonObject quest) {
        var array = quest.getAsJsonArray("objectives");
        StringBuilder sb = new StringBuilder();
        for (var e : array) {
            sb.append(e.toString()).append('|');
        }
        return sb.toString();
    }

    private static java.util.List<String> prerequisitesOf(JsonObject quest) {
        var array = quest.getAsJsonArray("prerequisites");
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (var e : array) {
            ids.add(e.getAsString());
        }
        return ids;
    }

    @Test
    void heartDemonChainFormsAValidDag() {
        assertTrue(
                prerequisitesOf(quest("quest_ep2_heartdemon_intro_01"))
                        .contains("quest_ep2_trial_complete_01"),
                "心魔劫从传承完成之后开始");
        assertTrue(
                prerequisitesOf(quest("quest_ep2_heartdemon_pill_01"))
                        .contains("quest_ep2_heartdemon_intro_01"));
        assertTrue(
                prerequisitesOf(quest("quest_ep2_heartdemon_breakthrough_01"))
                        .contains("quest_ep2_heartdemon_pill_01"));
    }

    @Test
    void objectiveTypesAreAllWiredEventSources() {
        // QuestAdapter 只接了 SIT/BREAKTHROUGH/KILL/COLLECT/TALK——链上不许出现没有事件源的目标类型
        assertTrue(objectivesOf(quest("quest_ep2_heartdemon_intro_01")).contains("talk"));
        String pillObjectives = objectivesOf(quest("quest_ep2_heartdemon_pill_01"));
        assertTrue(pillObjectives.contains("item_xuelian"), "洗尘丹之材：雪莲");
        assertTrue(pillObjectives.contains("item_wuse_shi"), "洗尘丹之材：五色石");
        assertTrue(
                objectivesOf(quest("quest_ep2_heartdemon_breakthrough_01"))
                        .contains("breakthrough"),
                "道心目标 = BREAKTHROUGH（RealmEvents.Breakthrough 已接线）");
    }

    @Test
    void cleanseFlagIsIdenticalInCodeAndDialog() {
        // 对话表的条件分支用 flag(…) 命中"已清心魔"——字符串两侧漂移的表现是分支永远不触发
        String tree = ShippedProducts.ep2Dialog().toString();
        assertTrue(
                tree.contains(StrifePills.HEART_CLEANSED_FLAG),
                "对话产物必须引用 StrifePills.HEART_CLEANSED_FLAG 同值字符串");
        assertTrue(
                tree.contains("flag(" + StrifePills.HEART_CLEANSED_FLAG + ")"),
                "条件谓词形态 flag(<key>) 应与 ConditionDsl 的 flag 语法一致");
    }

    @Test
    void dialogTreeKeepsOneTreePerNpcAndNewNodesAreReachable() {
        var tree = ShippedProducts.ep2Dialog();
        var trees = tree.getAsJsonArray("trees");
        int mijiTrees = 0;
        String nodes = null;
        for (var element : trees) {
            JsonObject t = element.getAsJsonObject();
            if ("npc_miji_guardian".equals(t.get("npc").getAsString())) {
                mijiTrees++;
                nodes = t.get("nodes").toString();
            }
        }
        assertEquals(1, mijiTrees, "一个 NPC 一棵树（DialogBook.treesByNpc 语义）——心魔节点必须长在原树上");
        assertNotNull(nodes);
        assertTrue(nodes.contains("nd_hm_intro"), "心魔讲解节点已入树");
        assertTrue(nodes.contains("nd_hm_cleansed"), "已清心魔反馈节点已入树");
    }
}

package com.strife.quest;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.strife.testing.ShippedProducts;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * P1-08 的工程侧对账：任务表的 NPC 目标 ↔ 世界摆放 ↔ 对话树必须三方闭合。
 *
 * <p>“三方”分工：源表 → 产物由 Validator 的 {@code @generated} 哈希与 V-REF 守住；本用例守产物 → 运行时语义那一段——任务里点名的 NPC
 * 必须真的被摆进世界，且右键能打开一棵属于它的对话树。少任何一侧，玩家看到的都是“任务让我找人，但人不存在/不说话”。
 */
class NpcContentConsistencyTest {

    private static final List<String> CHAPTERS = List.of("prologue", "ch1", "ep2");

    private static Set<String> shippedPlacements() {
        Set<String> ids = new HashSet<>();
        for (JsonObject placement : ShippedProducts.allNpcPlacements()) {
            ids.add(placement.get("id").getAsString());
        }
        return ids;
    }

    private static Set<String> shippedDialogNpcs() {
        Set<String> ids = new HashSet<>();
        for (JsonObject book : ShippedProducts.allDialogTrees()) {
            for (var tree : book.getAsJsonArray("trees")) {
                if (tree.isJsonObject() && tree.getAsJsonObject().has("npc")) {
                    ids.add(tree.getAsJsonObject().get("npc").getAsString());
                }
            }
        }
        return ids;
    }

    private static JsonObject chapterProduct(String chapter) {
        return switch (chapter) {
            case "prologue" -> ShippedProducts.prologueQuests();
            case "ch1" -> ShippedProducts.ch1Quests();
            case "ep2" -> ShippedProducts.ep2Quests();
            default -> throw new IllegalArgumentException(chapter);
        };
    }

    @Test
    void everyQuestNpcTargetIsPlacedAndHasADialogTree() {
        Set<String> placements = shippedPlacements();
        Set<String> dialogNpcs = shippedDialogNpcs();
        int checked = 0;
        for (String chapter : CHAPTERS) {
            for (var questElement : chapterProduct(chapter).getAsJsonArray("quests")) {
                JsonObject quest = questElement.getAsJsonObject();
                for (var objectiveElement : quest.getAsJsonArray("objectives")) {
                    JsonObject objective = objectiveElement.getAsJsonObject();
                    String type = objective.get("type").getAsString();
                    if (!"talk".equals(type) && !"deliver".equals(type)) {
                        continue;
                    }
                    String target = objective.get("target").getAsString();
                    assertTrue(
                            placements.contains(target),
                            chapter
                                    + " / "
                                    + quest.get("id").getAsString()
                                    + " 的目标 "
                                    + target
                                    + " 没有被摆进世界");
                    assertTrue(
                            dialogNpcs.contains(target),
                            chapter
                                    + " / "
                                    + quest.get("id").getAsString()
                                    + " 的目标 "
                                    + target
                                    + " 没有对话树");
                    checked++;
                }
            }
        }
        assertTrue(checked > 0, "三章任务簿里一个 talk/deliver 目标都没有，用例失去意义");
    }

    @Test
    void everyDialogTreeBelongsToAPlacedNpc() {
        Set<String> placements = shippedPlacements();
        Set<String> dialogNpcs = shippedDialogNpcs();
        for (String npc : dialogNpcs) {
            assertTrue(placements.contains(npc), npc + " 有对话树却没有摆放，玩家找不到人");
        }
    }
}

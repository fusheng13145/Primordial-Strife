package com.strife.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.strife.core.PlaceBridges;
import com.strife.quest.engine.QuestBook;
import com.strife.quest.engine.QuestState;
import com.strife.testing.ShippedProducts;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * 任务追踪条的纯逻辑：当前任务选择、必做目标进度，以及 NPC / 地点（reach）目标的地面导航。
 *
 * <p>任务簿用 jar 内真实序章产物；导航断言用合成坐标（真实 NPC 坐标已由 {@code NpcPlacementsTest} 守住半径与维度， 地点桥的产物侧契约由 {@code
 * PlacesTest} 守住）。
 */
class QuestTrackerTest {

    private static final ResourceLocation OVERWORLD = ResourceLocation.parse("minecraft:overworld");

    private static QuestBook shippedPrologue() {
        return QuestBook.parse(ShippedProducts.prologueQuests());
    }

    private static QuestBook synthetic(String chapterJson) {
        JsonObject product = JsonParser.parseString(chapterJson).getAsJsonObject();
        return QuestBook.parse(product);
    }

    @Test
    void trackableStartsAtTheEntryTalkObjective() {
        QuestBook.QuestView view =
                shippedPrologue().trackable(QuestState.of(Set.of(), Map.of()), null).orElseThrow();

        assertEquals("quest_prologue_root_read_01", view.id());
        assertEquals(1, view.objectives().size());
        QuestBook.ObjectiveView objective = view.objectives().get(0);
        assertEquals(QuestBook.ObjectiveType.TALK, objective.type());
        assertEquals("npc_qingshi_zhizhi", objective.target());
        assertEquals(0L, objective.progress());
        assertEquals(1L, objective.count());
    }

    @Test
    void trackableMovesToTheNextQuestOnceCompleted() {
        QuestState state =
                QuestState.of(
                        Set.of("quest_prologue_root_read_01"),
                        Map.of("quest_prologue_meditation_01", Map.of("1", 30L)));

        QuestBook.QuestView view = shippedPrologue().trackable(state, null).orElseThrow();

        assertEquals("quest_prologue_meditation_01", view.id());
        assertEquals(QuestBook.ObjectiveType.SIT, view.objectives().get(0).type());
        assertEquals(30L, view.objectives().get(0).progress());
        assertEquals(60L, view.objectives().get(0).count());
    }

    @Test
    void trackableSkipsHiddenQuests() {
        QuestBook book =
                synthetic(
                        """
                        {"id":"quest_hidden_01","chapter":"prologue","quests":[
                          {"id":"quest_hidden_01","entry":true,"hidden":true,
                           "objectives":[{"id":"1","type":"talk","target":"npc_x","count":1,"optional":false}],
                           "rewards":[],"repeatable":"no"},
                          {"id":"quest_visible_01","entry":false,"hidden":false,
                           "objectives":[{"id":"1","type":"sit","count":2,"optional":false}],
                           "rewards":[],"repeatable":"no"}]}
                        """);

        QuestBook.QuestView view =
                book.trackable(QuestState.of(Set.of(), Map.of()), null).orElseThrow();

        assertEquals("quest_visible_01", view.id(), "隐藏任务不得出现在追踪条上");
    }

    @Test
    void trackableOmitsOptionalObjectives() {
        QuestBook book =
                synthetic(
                        """
                        {"id":"quest_a_01","chapter":"prologue","quests":[
                          {"id":"quest_a_01","entry":true,
                           "objectives":[
                             {"id":"1","type":"talk","target":"npc_x","count":1,"optional":false},
                             {"id":"2","type":"kill","count":3,"optional":true}],
                           "rewards":[],"repeatable":"no"}]}
                        """);

        QuestBook.QuestView view =
                book.trackable(QuestState.of(Set.of(), Map.of()), null).orElseThrow();

        assertEquals(1, view.objectives().size(), "可选的击杀奖励不该占追踪条");
        assertEquals("1", view.objectives().get(0).id());
    }

    @Test
    void progressRatioCountsOnlyRequiredObjectives() {
        QuestBook book =
                synthetic(
                        """
                        {"id":"quest_a_01","chapter":"prologue","quests":[
                          {"id":"quest_a_01","entry":true,
                           "objectives":[
                             {"id":"1","type":"sit","count":2,"optional":false},
                             {"id":"2","type":"collect","target":"item_x","count":6,"optional":false},
                             {"id":"3","type":"kill","count":100,"optional":true}],
                           "rewards":[],"repeatable":"no"}]}
                        """);
        QuestState state =
                QuestState.of(Set.of(), Map.of("quest_a_01", Map.of("1", 1L, "2", 3L, "3", 90L)));

        QuestBook.QuestView view = book.trackable(state, null).orElseThrow();

        assertEquals(0.5, view.progressRatio(), 1e-9);
    }

    @Test
    void navigationUsesHorizontalDistanceAndCompassKey() {
        List<NpcPlacements.Placement> placements =
                List.of(new NpcPlacements.Placement("npc_x", OVERWORLD, "qi_luoxia", 100, 100));

        QuestTracker.Navigation southEast =
                QuestTracker.navigationFor("npc_x", placements, OVERWORLD, 97, 96).orElseThrow();
        assertTrue(southEast.sameDimension());
        assertEquals(5L, southEast.distanceBlocks());
        assertEquals("gui.strife.tracker.dir.se", southEast.directionKey());

        QuestTracker.Navigation north =
                QuestTracker.navigationFor("npc_x", placements, OVERWORLD, 100, 105).orElseThrow();
        assertEquals(5L, north.distanceBlocks());
        assertEquals("gui.strife.tracker.dir.n", north.directionKey());
    }

    @Test
    void navigationReportsCrossDimensionInsteadOfADirection() {
        List<NpcPlacements.Placement> placements =
                List.of(new NpcPlacements.Placement("npc_x", OVERWORLD, "qi_luoxia", 100, 100));

        QuestTracker.Navigation nav =
                QuestTracker.navigationFor(
                                "npc_x",
                                placements,
                                ResourceLocation.parse("strife:upper_realm"),
                                100,
                                100)
                        .orElseThrow();

        assertFalse(nav.sameDimension());
        assertEquals(OVERWORLD, nav.dimension());
        assertEquals("", nav.directionKey());
    }

    @Test
    void navigationIgnoresNonNpcTargetsAndUnknownPlacements() {
        List<NpcPlacements.Placement> placements =
                List.of(new NpcPlacements.Placement("npc_x", OVERWORLD, "qi_luoxia", 100, 100));

        assertTrue(
                QuestTracker.navigationFor("item_x", placements, OVERWORLD, 0, 0).isEmpty(),
                "物品目标没有坐标，不应给出方向");
        assertTrue(
                QuestTracker.navigationFor("npc_missing", placements, OVERWORLD, 0, 0).isEmpty(),
                "未摆放的 NPC 不应编造坐标");
        assertTrue(
                QuestTracker.navigationFor(null, placements, OVERWORLD, 0, 0).isEmpty(),
                "无目标（如任意地点打坐）不应给出方向");
    }

    @Test
    void navigationReportsHereWhenOnTheSpot() {
        List<NpcPlacements.Placement> placements =
                List.of(new NpcPlacements.Placement("npc_x", OVERWORLD, "qi_luoxia", 100, 100));

        Optional<QuestTracker.Navigation> nav =
                QuestTracker.navigationFor("npc_x", placements, OVERWORLD, 100, 100);

        assertEquals("gui.strife.tracker.dir.here", nav.orElseThrow().directionKey());
        assertEquals(0L, nav.orElseThrow().distanceBlocks());
    }

    @Test
    void navigationResolvesReachTargetsThroughThePlaceBridge() {
        // G-4 / ADR-023 三态之一：同维度——地点桥给出坐标，导航与 NPC 同口径（水平距离 + 八向罗盘）。
        PlaceBridges.PlaceCoords wenjiang =
                new PlaceBridges.PlaceCoords(OVERWORLD, "文津渡", -512, 64, -512);
        PlaceBridges.PlaceLookup lookup =
                (server, id) -> "qi_wenjiang".equals(id) ? Optional.of(wenjiang) : Optional.empty();

        QuestTracker.Navigation nav =
                QuestTracker.navigationFor(
                                "qi_wenjiang", List.of(), OVERWORLD, -503, -503, lookup, null)
                        .orElseThrow();
        assertTrue(nav.sameDimension());
        assertEquals(13L, nav.distanceBlocks(), "hypot(9,9)=12.7，四舍五入 13");
        assertEquals("gui.strife.tracker.dir.nw", nav.directionKey());
    }

    @Test
    void navigationReportsCrossDimensionForPlaceTargets() {
        // 三态之二：跨维度——不画方位，只显示目标维度。
        ResourceLocation upper = ResourceLocation.parse("strife:upper_realm");
        PlaceBridges.PlaceLookup lookup =
                (server, id) -> Optional.of(new PlaceBridges.PlaceCoords(upper, "上界某地", 0, 64, 0));

        QuestTracker.Navigation nav =
                QuestTracker.navigationFor("qi_x", List.of(), OVERWORLD, 0, 0, lookup, null)
                        .orElseThrow();
        assertFalse(nav.sameDimension());
        assertEquals(upper, nav.dimension());
        assertEquals("", nav.directionKey());
    }

    @Test
    void navigationDegradesGracefullyForPlaceTargets() {
        // 三态之三 + 未接线：未知地点、桥没注册、非地点目标，都不编造方向。
        PlaceBridges.PlaceLookup lookup = (server, id) -> Optional.empty();

        assertTrue(
                QuestTracker.navigationFor("qi_unknown", List.of(), OVERWORLD, 0, 0, lookup, null)
                        .isEmpty(),
                "未知地点不应编造方向");
        assertTrue(
                QuestTracker.navigationFor("qi_wenjiang", List.of(), OVERWORLD, 0, 0, null, null)
                        .isEmpty(),
                "桥未接线时不得假装能导航——没注册就是没有这条路");
        assertTrue(
                QuestTracker.navigationFor("item_x", List.of(), OVERWORLD, 0, 0, lookup, null)
                        .isEmpty(),
                "物品目标没有坐标语义");
    }

    @Test
    void reachTargetDisplayNameComesFromThePlaceTable() {
        // JSON_SCHEMA §4.17：地点 name 不入 lang、直接写表——显示名必须来自桥，而不是再造 region.strife.* 词条。
        Component resolved =
                QuestTracker.targetName(
                        "qi_wenjiang",
                        (server, id) ->
                                Optional.of(
                                        new PlaceBridges.PlaceCoords(OVERWORLD, "文津渡", 0, 0, 0)),
                        null);
        assertEquals(Component.literal("文津渡"), resolved, "追踪条应显示地点表中文名，而不是 region.strife.* 原始键");

        // 桥未接线（查不到）：退回 region 键（旧行为，键受 V-TEXT 覆盖）。
        Component fallback =
                QuestTracker.targetName("qi_wenjiang", (server, id) -> Optional.empty(), null);
        assertTrue(
                fallback.getContents() instanceof TranslatableContents translatable
                        && "region.strife.qi_wenjiang".equals(translatable.getKey()),
                "降级路径必须可预期，不能变成编造的名字");
    }
}

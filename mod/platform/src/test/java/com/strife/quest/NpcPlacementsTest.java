package com.strife.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.strife.testing.ShippedProducts;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * NPC 摆放产物的解析与随包数据不变量：摆放必须落在同名地点半径内，且每个被摆出来的 NPC 都有对话树。
 *
 * <p>这些断言拿真实 jar 产物当输入——只有这样才能把「表里加了行、产物没进包」与「坐标写到了另一片地图」 变成构建期红灯，而不是等玩家走到虚空里才发现没人。
 */
class NpcPlacementsTest {

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    void parsesAbsolutePlacementCoordinates() {
        NpcPlacements.Placement placement =
                NpcPlacements.fromJson(
                        json(
                                "{\"id\":\"npc_qingshi_zhizhi\","
                                        + "\"dimension\":\"minecraft:overworld\","
                                        + "\"place\":\"qi_luoxia\",\"x\":258,\"z\":252}"));

        assertEquals("npc_qingshi_zhizhi", placement.id());
        assertEquals(ResourceLocation.parse("minecraft:overworld"), placement.dimension());
        assertEquals("qi_luoxia", placement.place());
        assertEquals(258, placement.x());
        assertEquals(252, placement.z());
    }

    @Test
    void rejectsIdsThatAreNotNpcContentIds() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        NpcPlacements.fromJson(
                                json(
                                        "{\"id\":\"zhizhi\","
                                                + "\"dimension\":\"minecraft:overworld\","
                                                + "\"place\":\"qi_luoxia\",\"x\":1,\"z\":2}")));
    }

    @Test
    void rejectsUnknownDimensionNamespaces() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        NpcPlacements.fromJson(
                                json(
                                        "{\"id\":\"npc_x\","
                                                + "\"dimension\":\"custom:overworld\","
                                                + "\"place\":\"qi_luoxia\",\"x\":1,\"z\":2}")));
    }

    @Test
    void nearMeansSameDimensionAndInsideTheRange() {
        NpcPlacements.Placement placement =
                new NpcPlacements.Placement(
                        "npc_x",
                        ResourceLocation.parse("minecraft:overworld"),
                        "qi_luoxia",
                        100,
                        100);

        assertTrue(
                NpcPlacements.near(
                        placement, ResourceLocation.parse("minecraft:overworld"), 100, 160),
                "同一维度、距离 60 格（<64）应当命中");
        assertFalse(
                NpcPlacements.near(
                        placement, ResourceLocation.parse("minecraft:overworld"), 100, 165),
                "距离 65 格（>64）不应触发生成");
        assertFalse(
                NpcPlacements.near(
                        placement, ResourceLocation.parse("strife:upper_realm"), 100, 100),
                "跨维度不应触发生成");
    }

    @Test
    void everyShippedPlacementSitsInsideItsPlaceRadius() {
        List<JsonObject> placements = ShippedProducts.allNpcPlacements();
        Set<String> ids = new HashSet<>();
        for (JsonObject placement : placements) {
            String id = placement.get("id").getAsString();
            assertTrue(ids.add(id), "重复摆放：" + id);

            JsonObject place = ShippedProducts.place(placement.get("place").getAsString());
            assertEquals(
                    place.get("dimension").getAsString(),
                    placement.get("dimension").getAsString(),
                    id + " 的维度与地点不一致");
            long dx = (long) placement.get("x").getAsInt() - place.get("x").getAsInt();
            long dz = (long) placement.get("z").getAsInt() - place.get("z").getAsInt();
            long radius = place.get("radius").getAsInt();
            assertTrue(
                    dx * dx + dz * dz <= radius * radius, id + " 落在 " + place.get("id") + " 半径外");
        }
    }

    @Test
    void everyShippedPlacementHasADialogTree() {
        Set<String> dialogNpcs = new HashSet<>();
        for (JsonObject book : ShippedProducts.allDialogTrees()) {
            for (var tree : book.getAsJsonArray("trees")) {
                if (tree.isJsonObject() && tree.getAsJsonObject().has("npc")) {
                    dialogNpcs.add(tree.getAsJsonObject().get("npc").getAsString());
                }
            }
        }

        for (JsonObject placement : ShippedProducts.allNpcPlacements()) {
            String id = placement.get("id").getAsString();
            assertTrue(dialogNpcs.contains(id), id + " 被摆进世界却没有对话树，右键只会得到一句空回复");
        }
    }
}

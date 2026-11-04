package com.strife.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.strife.testing.ShippedProducts;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * 秘境/上界产出物 ×【真实产物】（ADR-036 ⑨ 产出钩子收尾）：
 *
 * <ul>
 *   <li>秘境灵草表（NUMBERS @@world secret_realm_herb_drop_prob → WorldTables）——天元果在此才有野外获取路径，
 *       智慧试炼"寻找天元果"靠它闭环；表缺失 = 任务永久不可完成
 *   <li>biome_modifier 产物——miji_biome 此前零刷怪零矿点（"秘境深处有妖兽盘踞"是空话），本批三份手写补齐
 * </ul>
 */
class SecretRealmYieldTest {

    @Test
    void secretRealmHerbTableShipsTianguoAsTheWisdomTrialPath() {
        JsonObject world = ShippedProducts.worldRules().getAsJsonObject("world");
        assertNotNull(
                world.getAsJsonObject("secret_realm_herb_drop_prob"),
                "shipped world rules 缺 secret_realm_herb_drop_prob——NUMBERS 块没进产物");
        var secret = WorldTables.parseHerbs(world.getAsJsonObject("secret_realm_herb_drop_prob"));
        assertTrue(secret.containsKey("item_tianguo"), "天元果必须进秘境灵草表——否则智慧试炼不可完成");
        assertTrue(secret.containsKey("item_xuelian"), "雪莲的秘境获取路径");
        assertTrue(secret.get("item_tianguo") > 0.0 && secret.get("item_tianguo") <= 0.1);
        // 秘境表是主世界表的补充而不是复本：秘境专属品不稀释主世界掉落（妖丹两界共有属既有口径）
        var overworld = WorldTables.parseHerbs(world.getAsJsonObject("herb_grass_drop_prob"));
        assertTrue(
                secret.keySet().stream()
                        .noneMatch(k -> overworld.containsKey(k) && !k.equals("item_yaodan")),
                "除妖丹外，秘境灵草不应与主世界草丛表重叠");
    }

    private static JsonObject biomeModifier(String name) {
        String path = "/data/strife/neoforge/biome_modifier/" + name + ".json";
        try (InputStream stream = SecretRealmYieldTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path + " 不在 jar——手写产物未随 content-base 打包");
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (Exception e) {
            throw new AssertionError("cannot read " + path, e);
        }
    }

    @Test
    void mijiBiomeSpawnsBeastsForTheCourageTrial() {
        JsonObject miji = biomeModifier("beast_miji");
        assertEquals("neoforge:add_spawns", miji.get("type").getAsString());
        assertEquals("strife:miji_biome", miji.get("biomes").getAsString());
        var spawners = miji.getAsJsonArray("spawners");
        assertTrue(spawners.size() >= 3, "秘境至少三类妖兽（勇气试炼'秘境深处有妖兽盘踞'的实体支撑）");
        StringBuilder types = new StringBuilder();
        spawners.forEach(
                e -> types.append(e.getAsJsonObject().get("type").getAsString()).append(','));
        assertTrue(types.toString().contains("strife:monster"));
    }

    /**
     * 结晶矿点注入路径（2026-11-04 RCON 冒烟抓到的崩溃回归锚）：biomes.csv 的 ores= 列由 BiomeGenerator 直接写进群系
     * features[6]——若再叠一份 add_features modifier（ore_miji/ore_upper）就是 Feature order cycle，区块生成期崩服。
     * 本用例钉"群系产物带矿、且不再有重复 modifier"。
     */
    @Test
    void qiCrystalOreLivesInBiomeFeaturesNotInModifiers() throws Exception {
        for (String biome : new String[] {"miji_biome", "upper_realm_biome"}) {
            try (InputStream stream =
                    SecretRealmYieldTest.class.getResourceAsStream(
                            "/data/strife/worldgen/biome/" + biome + ".json")) {
                assertNotNull(stream, biome + " 产物缺失");
                JsonObject root =
                        JsonParser.parseReader(
                                        new InputStreamReader(stream, StandardCharsets.UTF_8))
                                .getAsJsonObject();
                boolean hasCrystal = false;
                for (var step : root.getAsJsonArray("features")) {
                    for (var feature : step.getAsJsonArray()) {
                        if ("strife:block_qi_crystal".equals(feature.getAsString())) {
                            hasCrystal = true;
                        }
                    }
                }
                assertTrue(hasCrystal, biome + " 的 features[underground_ores] 必须带结晶矿");
            }
        }
        try (InputStream modDir =
                SecretRealmYieldTest.class.getResourceAsStream(
                        "/data/strife/neoforge/biome_modifier/ore_miji.json")) {
            assertNull(
                    modDir, "ore_miji/ore_upper modifier 不得复活——与群系内建矿重复 = Feature order cycle 崩服");
        }
    }

    /** placed feature 必须真实存在（引用漂移的表现是区块生成期静默跳过，矿点永远不出现）。 */
    @Test
    void referencedPlacedFeatureShipsInTheJar() {
        try (InputStream stream =
                SecretRealmYieldTest.class.getResourceAsStream(
                        "/data/strife/worldgen/placed_feature/block_qi_crystal.json")) {
            assertNotNull(stream, "strife:block_qi_crystal placed feature 缺失");
            JsonObject feature =
                    JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                            .getAsJsonObject();
            assertEquals("strife:block_qi_crystal", feature.get("feature").getAsString());
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}

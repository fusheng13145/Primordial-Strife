package com.strife.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    @Test
    void qiCrystalOreGeneratesInMijiAndUpperRealms() {
        // biomes.csv 秘境行注记'结晶矿点同上界'——两维度都挂 block_qi_crystal placed feature
        for (String name : new String[] {"ore_miji", "ore_upper"}) {
            JsonObject ore = biomeModifier(name);
            assertEquals("neoforge:add_features", ore.get("type").getAsString());
            assertEquals("strife:block_qi_crystal", ore.get("features").getAsString());
            assertEquals("underground_ores", ore.get("step").getAsString());
        }
        String mijiBiomes = biomeModifier("ore_miji").get("biomes").getAsString();
        String upperBiomes = biomeModifier("ore_upper").get("biomes").getAsString();
        assertEquals("strife:miji_biome", mijiBiomes);
        assertEquals("strife:upper_realm_biome", upperBiomes);
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

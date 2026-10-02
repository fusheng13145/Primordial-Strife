package com.strife.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.strife.testing.ShippedProducts;
import org.junit.jupiter.api.Test;

/**
 * 上界世界设计 ×【真实产物】的契约用例（「灵霄青冥」群系 + 灵气结晶生成链）。
 *
 * <p>生成链四环互引，任何一环断开都是静默的（结晶不长、或原版红屏）： {@code dimension → biome(strife:upper_realm) →
 * placed(strife:block_qi_crystal) → configured(block_match end_stone)}。
 * 本类沿链逐环断言，把世界设计钉在门禁里而不是运行期祈祷。
 */
class UpperRealmWorldTest {

    private static final String BIOME_ID = "upper_realm";
    private static final String CRYSTAL_ID = "block_qi_crystal";

    @Test
    void biomeCarriesTheLingXiaoPalette() {
        JsonObject effects = ShippedProducts.biome(BIOME_ID).getAsJsonObject("effects");

        // 「灵霄青冥」配色（tables/biomes.csv 真相源）：天青天空 + 淡金雾 + 青玉草色。
        // 若这里变回 0（the_end 的纯黑天空），说明有人把群系引用改回了借来的末地——世界设计回退，红。
        assertEquals(0x9BD7E8, effects.get("sky_color").getAsInt(), "天青天空");
        assertEquals(0xEFDFB0, effects.get("fog_color").getAsInt(), "淡金雾");
        assertEquals(0xA8E0C0, effects.get("grass_color").getAsInt(), "青玉草色");
    }

    @Test
    void biomeSpawnsQiCrystalThroughTheOreStep() {
        JsonObject biome = ShippedProducts.biome(BIOME_ID);

        // features[6] = UNDERGROUND_ORES 步：自研群系直引 placed feature（不走 biome_modifier）。
        assertEquals(
                "strife:" + CRYSTAL_ID,
                biome.getAsJsonArray("features").get(6).getAsJsonArray().get(0).getAsString());
    }

    @Test
    void crystalChainIsSelfConsistent() {
        JsonObject placed = ShippedProducts.placedFeature(CRYSTAL_ID);
        JsonObject configured = ShippedProducts.configuredFeature(CRYSTAL_ID);

        // placed → configured 同名互引；每区块 6 串、全高 0..256（浮岛高度域）。
        assertEquals("strife:" + CRYSTAL_ID, placed.get("feature").getAsString());
        assertEquals(6, placed.getAsJsonArray("placement").get(0).get("count").getAsInt());

        // configured → block_match 精确匹配 end_stone（end_stone 不在 stone_ore_replaceables tag，
        // 走矿石那条 tag_match 会静默一个都不替换——1.21.1 实证），state 指向注册常量同 ID 的方块。
        JsonObject target =
                configured
                        .getAsJsonObject("config")
                        .getAsJsonArray("targets")
                        .get(0)
                        .getAsJsonObject();
        assertEquals(
                "minecraft:block_match",
                target.getAsJsonObject("target").get("predicate_type").getAsString());
        assertEquals(
                "minecraft:end_stone", target.getAsJsonObject("target").get("block").getAsString());
        assertEquals(
                "strife:" + StrifeRealmBlocks.QI_CRYSTAL_ID,
                target.getAsJsonObject("state").get("Name").getAsString());
    }

    @Test
    void upperRealmIsAQuietRealm() {
        JsonObject biome = ShippedProducts.biome(BIOME_ID);

        // 灵界清净：无降水、spawners 全空（怪物随 EP 排期）、无 carvers。
        assertEquals(false, biome.get("has_precipitation").getAsBoolean());
        JsonObject spawners = biome.getAsJsonObject("spawners");
        assertTrue(
                spawners.asMap().values().stream().allMatch(v -> v.getAsJsonArray().isEmpty()),
                "上界不应有自然刷怪");
        assertTrue(biome.getAsJsonObject("carvers").asMap().isEmpty(), "上界无洞穴雕刻");
    }
}

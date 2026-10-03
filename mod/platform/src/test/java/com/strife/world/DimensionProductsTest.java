package com.strife.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.strife.testing.ShippedProducts;
import org.junit.jupiter.api.Test;

/**
 * 上界维度产物 ×【真实产物】的契约用例（ADR-021，{@code tables/dimensions.csv} → DataGen → 两份维度 JSON）。
 *
 * <p>维度链的断点全是静默的：{@code dimension} 缺失时 {@code /execute in strife:upper_realm} 无效、 {@code
 * dimension_type} 缺失时维度加载即红屏、generator 引用错噪声时上界直接是空壳——本用例把 "产物存在 + 链内互引一致 + 关键物理属性符合设定"钉死在门禁里。
 */
class DimensionProductsTest {

    private static final String DIMENSION_ID = "upper_realm";

    @Test
    void dimensionChainIsSelfConsistent() {
        JsonObject type = ShippedProducts.dimensionType(DIMENSION_ID);
        JsonObject dimension = ShippedProducts.dimension(DIMENSION_ID);

        // 维度本体引用的 type 必须是同 id 的 strife 维度（链内互引，引用错名字时原版红屏而非报缺文件）。
        assertEquals("strife:" + DIMENSION_ID, dimension.get("type").getAsString());
        assertEquals(
                "minecraft:noise",
                dimension.getAsJsonObject("generator").get("type").getAsString());
    }

    @Test
    void generatorBorrowsVanillaEndAssets() {
        JsonObject generator = ShippedProducts.dimension(DIMENSION_ID).getAsJsonObject("generator");

        // 地形借原版噪声（end_islands 浮岛），群系用自研「灵霄青冥」（biomes.csv 产物）——
        // 若写回 minecraft:the_end 等于退回末地复制品观感，这条用例把世界设计钉住。
        assertEquals("minecraft:end_islands", generator.get("settings").getAsString());
        JsonObject biomeSource = generator.getAsJsonObject("biome_source");
        assertEquals("minecraft:fixed", biomeSource.get("type").getAsString());
        assertEquals("strife:upper_realm_biome", biomeSource.get("biome").getAsString());
    }

    /**
     * 物理属性红线（tables/dimensions.csv 与生成器注释里的设定）：上界是「宜居灵界」——可放重生锚（跨界锚点）、 不可用床（不设重生点）、坐标
     * 1:1（导航坐标语义）、暗天渲染但环境光补足可视度。
     */
    @Test
    void physicalPropertiesMatchTheUpperRealmDesign() {
        JsonObject type = ShippedProducts.dimensionType(DIMENSION_ID);

        assertTrue(type.get("respawn_anchor_works").getAsBoolean(), "上界必须可放重生锚（跨界锚点设定）");
        assertEquals(false, type.get("bed_works").getAsBoolean(), "上界不可用床（与 the_end 同口径）");
        assertEquals("minecraft:the_end", type.get("effects").getAsString(), "天空渲染走 the_end 效果");
        assertEquals(1.0, type.get("coordinate_scale").getAsDouble(), 1e-9, "坐标 1:1");
        assertEquals(0.5, type.get("ambient_light").getAsDouble(), 1e-9, "环境光 0.5（表列真相源）");
        assertTrue(
                type.get("@generated").getAsString().contains("dimensions.csv"), "必须是 DataGen 产物");
    }
}

package com.strife.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.strife.testing.ShippedProducts;
import org.junit.jupiter.api.Test;

/**
 * world 运行时参数 ×【真实产物】的契约用例（NUMBERS {@code @@world} → DataGen → {@code strife_worldgen/rules.json}）。
 *
 * <p>与 realm 同一教训：解析器与产物之间的漂移只在运行时发作，而运行时路径在开服冒烟里未必被走到（那里没有玩家打坐）。 所以这里直接拿产物当输入，把"产物 / 解析器 /
 * 真相源"三者的对齐钉死在门禁里。
 */
class WorldTablesTest {

    private static JsonObject worldBlock() {
        return ShippedProducts.worldRules().getAsJsonObject("world");
    }

    @Test
    void parsesAmbientParametersFromTheShippedProduct() {
        WorldTables.Ambient ambient = WorldTables.parseAmbient(worldBlock());

        assertEquals(0.50, ambient.min(), 1e-9);
        assertEquals(2.00, ambient.max(), 1e-9);
        assertEquals(16, ambient.regionChunks());
        assertEquals(0.25, ambient.refineWeight(), 1e-9);
    }

    @Test
    void parsesHerbDropProbabilitiesFromTheShippedProduct() {
        var herbs = WorldTables.parseHerbs(worldBlock().getAsJsonObject("herb_grass_drop_prob"));

        assertEquals(0.12, herbs.get("item_ningxu"), 1e-9);
        assertEquals(0.05, herbs.get("item_duanxue"), 1e-9);
    }

    @Test
    void missingAmbientKeyNamesTheBlockAndKey() {
        JsonObject broken = worldBlock().deepCopy();
        broken.remove("ambient_refine_weight");

        IllegalStateException error =
                assertThrows(IllegalStateException.class, () -> WorldTables.parseAmbient(broken));

        assertTrue(error.getMessage().contains("ambient_refine_weight"), error.getMessage());
        assertTrue(error.getMessage().contains("world"), error.getMessage());
    }

    @Test
    void probabilityOutsideUnitRangeIsRejectedAtLoadTime() {
        JsonObject broken = worldBlock().deepCopy();
        broken.getAsJsonObject("herb_grass_drop_prob").addProperty("item_ningxu", 1.5);

        IllegalStateException error =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                WorldTables.parseHerbs(
                                        broken.getAsJsonObject("herb_grass_drop_prob")));

        assertTrue(error.getMessage().contains("item_ningxu"), error.getMessage());
    }

    /**
     * 端到端接线：产物里的四项参数必须真的能建出一个合法的场（含 {@code ambient_qi_min > 0} 这条 05 §2 的硬口径）。
     * 单独解析通过、但建场失败的情况必须在这里红掉，而不是等到玩家打坐时才发现。
     */
    @Test
    void shippedParametersCanBuildALiveField() {
        WorldTables.Ambient ambient = WorldTables.parseAmbient(worldBlock());
        AmbientQiField field =
                new AmbientQiField(
                        (x, y, z) -> Math.sin(x * 0.1),
                        (x, y, z) -> Math.cos((x + z) * 0.1),
                        ambient.min(),
                        ambient.max(),
                        ambient.regionChunks(),
                        ambient.refineWeight());

        double ratio = field.ratioAt(0, 0);
        assertTrue(ratio >= ambient.min() && ratio <= ambient.max(), "系数应落在表定区间内：" + ratio);
        assertTrue(ratio > 0.0, "环境系数不得为 0");
    }
}

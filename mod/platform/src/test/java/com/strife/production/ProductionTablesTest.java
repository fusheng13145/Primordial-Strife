package com.strife.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.strife.testing.ShippedProducts;
import org.junit.jupiter.api.Test;

/**
 * 丹方产物 × 运行时解析器的契约用例（tables/pills.csv → DataGen → {@code strife_pills/*.json}）。
 *
 * <p>用真实产物而不是夹具：丹方表是内容侧最常改的一张表，而"解析器与产物对不上"的缺陷只有拿产物当输入才会红。
 */
class ProductionTablesTest {

    @Test
    void parsesTheQiRatePillFromTheShippedProduct() {
        ProductionTables.Pill pill = ProductionTables.parsePill(ShippedProducts.pill("pill_juqi"));

        assertEquals("pill_juqi", pill.id());
        ProductionTables.Effect.QiRate rate =
                assertInstanceOf(ProductionTables.Effect.QiRate.class, pill.effect());
        assertEquals(0.25, rate.bonus(), 1e-9);
        assertEquals(6000L, rate.durationTicks(), "300 秒 × 20 刻");
        assertEquals(0.05, rate.repeatStep(), 1e-9);
        assertEquals(0.10, rate.repeatFloor(), 1e-9);
    }

    @Test
    void parsesTheShortHighDosePillFromTheShippedProduct() {
        ProductionTables.Pill pill =
                ProductionTables.parsePill(ShippedProducts.pill("pill_peiyuan"));

        ProductionTables.Effect.QiRate rate =
                assertInstanceOf(ProductionTables.Effect.QiRate.class, pill.effect());
        assertEquals(0.60, rate.bonus(), 1e-9);
        assertEquals(120L * 20L, rate.durationTicks());
    }

    @Test
    void parsesTheLifespanPillFromTheShippedProduct() {
        ProductionTables.Pill pill =
                ProductionTables.parsePill(ShippedProducts.pill("pill_yanshou"));

        ProductionTables.Effect.Lifespan lifespan =
                assertInstanceOf(ProductionTables.Effect.Lifespan.class, pill.effect());
        assertEquals(10, lifespan.yearsGain());
        assertEquals(30, lifespan.maxGainPerRealm(), "NUMBERS §7 的单境界封顶");
    }

    @Test
    void missingEffectBlockIsRejectedWithThePillId() {
        JsonObject broken = ShippedProducts.pill("pill_juqi").deepCopy();
        broken.remove("effect");

        IllegalStateException error =
                assertThrows(IllegalStateException.class, () -> ProductionTables.parsePill(broken));

        assertTrue(error.getMessage().contains("pill_juqi"), error.getMessage());
    }

    @Test
    void unknownEffectShapeIsRejectedInsteadOfSilentlyIgnored() {
        JsonObject broken = ShippedProducts.pill("pill_juqi").deepCopy();
        broken.add("effect", new JsonObject());

        IllegalStateException error =
                assertThrows(IllegalStateException.class, () -> ProductionTables.parsePill(broken));

        assertTrue(error.getMessage().contains("pill_juqi"), error.getMessage());
    }

    /** 每张丹方都必须能被解析：内容侧加一行丹药而代码读不出来的情况，必须在门禁里红掉。 */
    @Test
    void everyShippedPillParses() {
        for (String pillId : new String[] {"pill_juqi", "pill_peiyuan", "pill_yanshou"}) {
            ProductionTables.Pill pill = ProductionTables.parsePill(ShippedProducts.pill(pillId));
            assertEquals(pillId, pill.id());
        }
    }
}

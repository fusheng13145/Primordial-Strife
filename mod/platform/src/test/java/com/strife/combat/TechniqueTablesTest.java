package com.strife.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.strife.realm.FiveElements;
import com.strife.testing.ShippedProducts;
import org.junit.jupiter.api.Test;

/**
 * 功法产物 × 运行时解析器的契约用例（tables/techniques.csv → DataGen → {@code strife_techniques/*.json}）。
 *
 * <p>用真实产物而不是夹具：功法表是内容侧最常改的表之一，而"解析器与产物对不上"的缺陷只有拿产物当输入才会红。
 */
class TechniqueTablesTest {

    @Test
    void parsesTheEntryTechniqueFromTheShippedProduct() {
        TechniqueTables.Technique technique =
                TechniqueTables.parse(ShippedProducts.technique("tech_qingxin_jue"));

        assertEquals("tech_qingxin_jue", technique.id());
        assertEquals("xuan", technique.grade());
        assertEquals(FiveElements.MU, technique.elementMask());
        assertEquals("qili", technique.requiredRealm());
        assertEquals(1, technique.requiredStage());
        assertEquals(FiveElements.MU, technique.requiredRootMask());
        assertEquals(FiveElements.Affinity.MATCHED, technique.declaredAffinity());
        assertEquals(1.15, technique.qiRateRatio(), 1e-9);
        assertEquals("fac_qingshi", technique.faction());
    }

    @Test
    void parsesAFireTechniqueWithItsOwnElementAndRatio() {
        TechniqueTables.Technique technique =
                TechniqueTables.parse(ShippedProducts.technique("tech_duanhu_gong"));

        assertEquals(FiveElements.HUO, technique.elementMask());
        assertEquals(1.10, technique.qiRateRatio(), 1e-9);
    }

    /** 每一部功法都必须能被解析：内容侧加一行而代码读不出来，必须在门禁里红掉。 */
    @Test
    void everyShippedTechniqueParses() {
        for (String id :
                new String[] {
                    "tech_qingxin_jue",
                    "tech_duanhu_gong",
                    "tech_jinsha_jue",
                    "tech_renshui_zhenjie",
                    "tech_houtu_gong"
                }) {
            TechniqueTables.Technique technique =
                    TechniqueTables.parse(ShippedProducts.technique(id));
            assertEquals(id, technique.id());
            assertTrue(
                    technique.qiRateRatio() > 0.0 && technique.qiRateRatio() <= 4.0,
                    id + " 的 qi_rate_ratio 越界：" + technique.qiRateRatio());
        }
    }

    @Test
    void missingRatioIsRejectedWithTheColumnName() {
        JsonObject broken = ShippedProducts.technique("tech_qingxin_jue").deepCopy();
        broken.remove("qi_rate_ratio");

        IllegalStateException error =
                assertThrows(IllegalStateException.class, () -> TechniqueTables.parse(broken));

        assertTrue(error.getMessage().contains("qi_rate_ratio"), error.getMessage());
    }

    @Test
    void unknownElementIsTreatedAsNoElementInsteadOfCrashing() {
        JsonObject broken = ShippedProducts.technique("tech_qingxin_jue").deepCopy();
        broken.addProperty("element", "lei");

        TechniqueTables.Technique technique = TechniqueTables.parse(broken);

        assertEquals(FiveElements.NONE, technique.elementMask(), "未知属性（变异灵根属 EP2）按无属性处理");
    }
}

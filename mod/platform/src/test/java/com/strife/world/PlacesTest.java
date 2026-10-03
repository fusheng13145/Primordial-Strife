package com.strife.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.strife.testing.ShippedProducts;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * 地点运行时表 ×【真实产物】的契约用例（{@code tables/places.csv} → {@code strife_places/*.json}）。
 *
 * <p>与 WorldTablesTest 同口径：解析器读错块/缺键的缺陷只在运行时发作，这里直接拿产物当输入，把 「产物 / 解析器 / 真相源」三者对齐钉死。同时覆盖 {@link
 * Places#placeAt} 的归属判定（半径 / 维度 / 最近优先）。
 */
class PlacesTest {

    @Test
    void parsesShippedLuoxiaProduct() {
        JsonObject json = ShippedProducts.place("qi_luoxia");
        Places.Place p = Places.fromJson(json);

        assertEquals("qi_luoxia", p.id());
        assertEquals("落霞山麓", p.name());
        assertEquals(
                ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"), p.dimension());
        assertEquals(256, p.x());
        assertEquals(70, p.y());
        assertEquals(256, p.z());
        assertEquals(320, p.radius());
        assertEquals(0.9, p.qiScale(), 1e-9);
        assertEquals("prologue", p.chapter());
    }

    @Test
    void parsesTaixuWithoutChapter() {
        JsonObject json = ShippedProducts.place("qi_taixu");
        Places.Place p = Places.fromJson(json);
        assertEquals("qi_taixu", p.id());
        assertEquals(2.0, p.qiScale(), 1e-9);
        assertNull(p.chapter(), "太虚干脉无所属章节，chapter 应为空");
    }

    @Test
    void placeAtReturnsNullOutsideRadius() {
        List<Places.Place> all = List.of(luoxia());
        ResourceLocation overworld =
                ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
        // (256,256) 是中心，半径 320；(2000,2000) 远在圈外。
        assertNull(Places.placeAt(all, overworld, 2000, 2000));
        // 圈内、同维度应命中。
        assertEquals("qi_luoxia", Places.placeAt(all, overworld, 300, 300).id());
    }

    @Test
    void placeAtIgnoresOtherDimensions() {
        List<Places.Place> all = List.of(luoxia());
        ResourceLocation upper = ResourceLocation.fromNamespaceAndPath("strife", "upper_realm");
        assertNull(Places.placeAt(all, upper, 256, 256), "地点属于主世界，上界坐标不命中");
    }

    @Test
    void placeAtPicksNearestWhenOverlapping() {
        // 两个重叠地点：大圈(半径448) 与 小圈(半径100)，玩家在 (10,10)。
        Places.Place wide =
                new Places.Place(
                        "qi_wide",
                        ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
                        "wide",
                        0,
                        64,
                        0,
                        448,
                        1.0,
                        null);
        Places.Place narrow =
                new Places.Place(
                        "qi_narrow",
                        ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
                        "narrow",
                        10,
                        64,
                        10,
                        100,
                        2.0,
                        null);
        List<Places.Place> all = List.of(wide, narrow);
        ResourceLocation overworld =
                ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
        assertEquals("qi_narrow", Places.placeAt(all, overworld, 10, 10).id(), "重叠时取更近（更小）的圈");
    }

    @Test
    void spiritFieldOverrideIsRealFeatureNotJustComment() {
        // 灵气地点差异化的「数据基础」：每个地点的 qi_scale 确实进入了产物，且落在合法区间。
        for (String id :
                List.of("qi_luoxia", "qi_wenjiang", "qi_shiguo", "qi_yunmeng", "qi_taixu")) {
            double scale = ShippedProducts.place(id).get("qi_scale").getAsDouble();
            assertTrue(scale > 0 && scale <= 10, id + " qi_scale 越界：" + scale);
        }
    }

    private static Places.Place luoxia() {
        return new Places.Place(
                "qi_luoxia",
                ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
                "落霞山麓",
                256,
                70,
                256,
                320,
                0.9,
                "prologue");
    }
}

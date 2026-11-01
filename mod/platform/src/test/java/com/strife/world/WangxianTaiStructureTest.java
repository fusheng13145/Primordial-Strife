package com.strife.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.strife.testing.ShippedProducts;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

/**
 * 上界望仙台 ×【真实产物】的契约用例（P1-15，ADR-022 code 路线同款）。
 *
 * <p>与 {@link SectStructuresTest} 同口径：双产物互引、注册同 ID、spacing&gt;separation 全部钉死——
 * 结构链断一处，游戏内就是"/locate 一直找不到"的静默缺陷。
 */
class WangxianTaiStructureTest {

    private static final String ID = SectStructures.WANGXIAN_TAI_ID;

    @Test
    void structureTypeMatchesTheRegisteredKey() {
        assertEquals("strife:" + ID, ShippedProducts.structure(ID).get("type").getAsString());
    }

    @Test
    void spawnsInTheUpperRealmBiome() {
        JsonObject structure = ShippedProducts.structure(ID);
        String biomes = structure.getAsJsonArray("biomes").toString();

        // 上界维度借用 the_end 群系（ADR-021）——biome 过滤是结构归属维度的唯一手段，
        // 群系写错（如主世界群系）= 上界永远找不到望仙台。
        assertTrue(biomes.contains("minecraft:the_end"), biomes);
        assertTrue(!biomes.contains("minecraft:plains"), "不得落入主世界群系：" + biomes);
        assertTrue(!biomes.contains("strife:"), "不自研群系阶段不得指向 strife: 群系：" + biomes);
    }

    @Test
    void structureSetPlacementIsValid() {
        JsonObject placement = ShippedProducts.structureSet(ID).getAsJsonObject("placement");

        int spacing = placement.get("spacing").getAsInt();
        int separation = placement.get("separation").getAsInt();
        assertTrue(
                spacing > separation,
                "spacing(" + spacing + ") 必须 > separation(" + separation + ")");
        assertEquals("minecraft:random_spread", placement.get("type").getAsString());
        assertEquals(
                "strife:" + ID,
                ShippedProducts.structureSet(ID)
                        .getAsJsonArray("structures")
                        .get(0)
                        .getAsJsonObject()
                        .get("structure")
                        .getAsString());
    }

    @Test
    void productsComeFromTheContractTable() {
        assertTrue(
                ShippedProducts.structure(ID)
                        .get("@generated")
                        .getAsString()
                        .contains("structures.csv"),
                "必须是 DataGen 产物（防手写夹具混入）");
        assertTrue(
                ShippedProducts.structureSet(ID)
                        .get("@generated")
                        .getAsString()
                        .contains("structures.csv"));
    }

    @Test
    void pieceGeometryCoversPlatformAndSupport() {
        WangxianTaiPiece piece = new WangxianTaiPiece(new BlockPos(100, 70, -200));
        int[] dims = WangxianTaiPiece.dimensions();
        int half = dims[0];
        int height = dims[1];
        int support = dims[2];

        BoundingBox box = piece.getBoundingBox();
        BlockPos origin = piece.origin();

        assertEquals(origin.getX() - half, box.minX());
        assertEquals(origin.getX() + half, box.maxX());
        assertEquals(origin.getY() - support, box.minY(), "支撑漏了台子会悬空");
        assertEquals(origin.getY() + height, box.maxY());
        assertEquals(origin, piece.getLocatorPosition(), "/locate 返回的点应是台心");
        assertTrue(half == 4 && height == 4 && support == 6, "望仙台设计尺寸（9×9 / 柱高 4 / 支撑 6）");
    }
}

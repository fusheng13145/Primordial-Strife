package com.strife.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.strife.testing.ShippedProducts;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

/**
 * 宗门结构 ×【真实产物】的契约用例（JSON_SCHEMA §4.13，ADR-022 code 路线）。
 *
 * <p>结构链也是静默断链：{@code structure} 与 {@code structure_set} 缺一、或 {@code type} 与注册 key
 * 不一致，游戏内表现为"结构列表里有名字但永远找不到"（{@code /locate} 一直报找不到）。这里把 双产物互引与注册同 ID 钉死在门禁里。
 */
class SectStructuresTest {

    private static final String ID = SectStructures.SECT_HALL_ID;

    @Test
    void structureTypeMatchesTheRegisteredKey() {
        // type 必须与注册 key 完全一致（注册在 SectStructures，产物在 tables/structures.csv）——
        // 两者漂移时原版按 type 找不到 codec，结构静默不生成。
        assertEquals("strife:" + ID, ShippedProducts.structure(ID).get("type").getAsString());
    }

    @Test
    void codeStyleStructureHasNoJigsawFields() {
        JsonObject structure = ShippedProducts.structure(ID);

        // code 式（原版 ruined_portal 同机制）没有 start_pool / size——写上这两个 jigsaw 专属字段
        // 等于凭记忆改契约，且引用的 template pool 不存在时同样静默跳过。
        assertTrue(!structure.has("start_pool"), "code 式结构不应有 start_pool");
        assertTrue(!structure.has("size"), "code 式结构不应有 size（jigsaw 专属）");
        assertEquals("surface_structures", structure.get("step").getAsString());
        assertEquals("beard_thin", structure.get("terrain_adaptation").getAsString());
    }

    @Test
    void spawnsInOverworldBiomes() {
        JsonObject structure = ShippedProducts.structure(ID);
        String biomes = structure.getAsJsonArray("biomes").toString();

        // 宗门是主世界建筑：群系列必须落在原版主世界群系（借原版，禁 strife: 自研群系——
        // 上界自研群系给主世界结构用会指向不存在的内容）。
        assertTrue(biomes.contains("minecraft:plains"), biomes);
        assertTrue(biomes.contains("minecraft:forest"), biomes);
        assertTrue(!biomes.contains("strife:"), biomes);
    }

    @Test
    void structureSetPlacementIsValid() {
        JsonObject set = ShippedProducts.structureSet(ID);
        JsonObject placement = set.getAsJsonObject("placement");

        // separation >= spacing 时原版视作无效而不生成——这条必须落在 DataGen（构建期红），
        // 因为运行期玩家只会看到"我跑了一万格没找到宗门"。
        int spacing = placement.get("spacing").getAsInt();
        int separation = placement.get("separation").getAsInt();
        assertTrue(
                spacing > separation,
                "spacing(" + spacing + ") 必须 > separation(" + separation + ")");
        assertEquals("minecraft:random_spread", placement.get("type").getAsString());
        assertTrue(placement.has("salt"), "salt 缺失会让分布退化为固定值");
        assertEquals(
                "strife:" + ID,
                set.getAsJsonArray("structures")
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
    void hallPieceGeometryCoversThePlatform() {
        SectHallPiece piece = new SectHallPiece(new BlockPos(100, 70, -200));
        int[] dims = SectHallPiece.dimensions();
        int half = dims[0];
        int height = dims[1];
        int support = dims[2];

        BoundingBox box = piece.getBoundingBox();
        BlockPos origin = piece.origin();

        // 包围盒必须同时覆盖地基（13×13）与向下支撑（6 格）——支撑漏了厅堂会悬空。
        assertEquals(origin.getX() - half, box.minX());
        assertEquals(origin.getX() + half, box.maxX());
        assertEquals(origin.getY() - support, box.minY());
        assertEquals(origin.getY() + height, box.maxY());
        assertEquals(origin, piece.getLocatorPosition(), "/locate 返回的点应是厅堂中心");
        assertTrue(half == 6 && height == 8 && support == 6, "厅堂设计尺寸（13×13 / 高 8 / 支撑 6）");
    }
}

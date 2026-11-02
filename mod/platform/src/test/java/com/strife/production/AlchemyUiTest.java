package com.strife.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.strife.core.InventoryOps;
import com.strife.core.StrifeUiBridge;
import com.strife.testing.ShippedProducts;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 炉交互屏数据形状 ×【真实配方产物】（ADR-038 交互层）：服务端组的数据就是客户端渲染的全部—— schema 键与形状在这里锚定，clientfx 与服务端任何一侧拼错键都会在这里红。
 */
class AlchemyUiTest {

    @BeforeAll
    static void loadRealRecipes() {
        Alchemy.register();
    }

    private static InventoryOps.PlayerContainers containersWith(String itemId, int count) {
        SimpleContainer main = new SimpleContainer(41);
        Item item = InventoryOps.resolve(itemId).orElseThrow();
        if (count > 0) {
            main.setItem(0, new ItemStack(item, count));
        }
        return new InventoryOps.PlayerContainers(main, new SimpleContainer(27));
    }

    @Test
    void recipeRowCarriesMaterialsHaveNeedAndCraftable() {
        AlchemyRecipe recipe = Alchemy.recipes().get("pill_peiyuan");
        var rich = AlchemyUi.row(recipe, containersWith("item_yuejian", 5));
        var poor = AlchemyUi.row(recipe, containersWith("item_yuejian", 1));

        assertEquals("pill_peiyuan", rich.getString(StrifeUiBridge.Keys.PILL));
        assertFalse(rich.getBoolean(StrifeUiBridge.Keys.CRAFTABLE), "缺凝神丹 = 不可炼制");
        assertFalse(poor.getBoolean(StrifeUiBridge.Keys.CRAFTABLE), "只有月见草也缺凝神丹，不可炼制");

        // 材料行形状：item/need/have 三键，rich 侧月见草 have=5
        boolean foundYuejian = false;
        for (Tag element : rich.getList(StrifeUiBridge.Keys.MATERIALS, Tag.TAG_COMPOUND)) {
            CompoundTag material = (CompoundTag) element;
            if ("item_yuejian".equals(material.getString(StrifeUiBridge.Keys.ITEM))) {
                foundYuejian = true;
                assertEquals(2, material.getInt(StrifeUiBridge.Keys.NEED));
                assertEquals(5L, material.getLong(StrifeUiBridge.Keys.HAVE));
            }
        }
        assertTrue(foundYuejian, "培元丹必须列月见草");
    }

    @Test
    void fullyStockedRowIsCraftableWithOutputProbabilities() {
        AlchemyRecipe recipe = Alchemy.recipes().get("pill_peiyuan");
        var containers =
                new InventoryOps.PlayerContainers(new SimpleContainer(41), new SimpleContainer(27));
        int slot = 0;
        for (AlchemyRecipe.Material material : recipe.materials()) {
            containers
                    .main()
                    .setItem(
                            slot++,
                            new ItemStack(
                                    InventoryOps.resolve(material.itemId()).orElseThrow(),
                                    material.count()));
        }
        CompoundTag row = AlchemyUi.row(recipe, containers);

        assertTrue(row.getBoolean(StrifeUiBridge.Keys.CRAFTABLE), "材料齐 = 可炼制");
        double probSum = 0;
        int outputs = 0;
        for (Tag element : row.getList(StrifeUiBridge.Keys.OUTPUTS, Tag.TAG_COMPOUND)) {
            CompoundTag output = (CompoundTag) element;
            probSum += output.getDouble(StrifeUiBridge.Keys.PROB);
            outputs++;
            assertFalse(output.getString(StrifeUiBridge.Keys.ITEM).isEmpty());
        }
        assertEquals(2, outputs, "培元丹两档产出（本品+降级品）");
        assertEquals(1.0, probSum, 1e-9, "产出概率合计 1.0（V-PROB 同一口径）");
    }

    @Test
    void fireEffectSummaryMatchesFiresCsvSemantics() {
        assertEquals("", AlchemyUi.describe(AlchemyFires.get("fire_normal")), "凡火无效果摘要");
        assertEquals("成功率+3%", AlchemyUi.describe(AlchemyFires.get("fire_dixin")));
        assertEquals("成功率+8% 品质偏置+3%", AlchemyUi.describe(AlchemyFires.get("fire_jiuyou")));
        assertEquals("品质偏置+10%", AlchemyUi.describe(AlchemyFires.get("fire_taiyang")));
        assertEquals("废丹回收5%", AlchemyUi.describe(AlchemyFires.get("fire_xuanbing")));
    }

    /** 限速链：NUMBERS craft_per_sec → 产物（本用例锚产物）→ 意图常量（键名对应）。 */
    @Test
    void craftIntentIsRateLimitedByShippedRules() {
        var rateLimits = ShippedProducts.coreRules().getAsJsonObject("rate_limits");
        assertTrue(
                rateLimits.has("craft_per_sec"),
                "shipped core rules 缺 craft_per_sec——NUMBERS 块没进产物");
        assertEquals(2, rateLimits.get("craft_per_sec").getAsInt());
        assertEquals("craft_pill", com.strife.core.net.IntentRateLimiter.CRAFT);
        assertEquals(
                StrifeUiBridge.Keys.ALCHEMY_SCREEN,
                "alchemy",
                "screenId 与 clientfx 注册键同源（StrifeUiBridge.Keys）");
    }
}

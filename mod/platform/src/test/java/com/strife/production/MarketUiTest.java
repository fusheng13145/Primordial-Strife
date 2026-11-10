package com.strife.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.strife.core.InventoryOps;
import com.strife.core.StrifeUiBridge;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** 坊市看板数据形状 ×【真实价目产物】（StrifeUiBridge 第二屏）：行序确定（回收价降序、同价 ID 升序）、 持有量计数、兑换比/鉴定费随价目进包——键漂移在这里红。 */
class MarketUiTest {

    @BeforeAll
    static void sanity() {
        // 价目表必须非空——空表看板就是空屏（价目批的门禁假设）
        assertFalse(MarketPrices.snapshot().isEmpty());
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
    void boardRowsAreSortedByBuyDescThenItemIdAscWithOwnedCounts() {
        var containers = containersWith("item_ningxu", 3);
        CompoundTag root = MarketUi.build(containers);

        // 头部：兑换比 + 鉴定费进包（值随价目产物，>0 断言防空表静默）
        assertTrue(root.getInt(StrifeUiBridge.Keys.EXCHANGE_ZHONG) > 0);
        assertTrue(root.getInt(StrifeUiBridge.Keys.EXCHANGE_SHANG) > 0);
        assertTrue(root.getInt(StrifeUiBridge.Keys.APPRAISE_FEE) > 0);

        // 行：价目全量、回收价降序、同价 ID 升序、owned 与背包一致
        var rows = root.getList(StrifeUiBridge.Keys.PRICES, Tag.TAG_COMPOUND);
        assertEquals(MarketPrices.snapshot().size(), rows.size(), "行数 = 价目条目数");
        String prevId = null;
        int prevBuy = Integer.MAX_VALUE;
        for (var element : rows) {
            CompoundTag row = (CompoundTag) element;
            String itemId = row.getString(StrifeUiBridge.Keys.ITEM);
            int buy = row.getInt(StrifeUiBridge.Keys.BUY);
            assertTrue(prevBuy >= buy, "买入价降序");
            if (prevId != null && prevBuy == buy) {
                assertTrue(prevId.compareTo(itemId) <= 0, "同价按 ID 升序");
            }
            if ("item_ningxu".equals(itemId)) {
                assertEquals(3L, row.getLong(StrifeUiBridge.Keys.OWNED), "持有量与背包一致");
            }
            assertTrue(
                    row.getInt(StrifeUiBridge.Keys.SELL) < row.getInt(StrifeUiBridge.Keys.BUY),
                    itemId + " 卖<买（坊市无套利口径）");
            prevId = itemId;
            prevBuy = buy;
        }
    }

    @Test
    void marketSchemaKeysAreStrifePrefixedValues() {
        assertEquals("market", StrifeUiBridge.Keys.MARKET_SCREEN);
        assertEquals("prices", StrifeUiBridge.Keys.PRICES);
        assertEquals("buy", StrifeUiBridge.Keys.BUY);
        assertEquals("sell", StrifeUiBridge.Keys.SELL);
    }
}

package com.strife.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.strife.core.InventoryOps;
import com.strife.core.StrifeUiBridge;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

/** 黑市看板数据形状 ×【真实暗柜价目产物】（StrifeUiBridge 第四屏）：行序（价降序、同价 ID 升序）、 因果警示进包、持有量口径——第四屏是模板化落地的验收样例。 */
class BlackMarketUiTest {

    @Test
    void boardRowsAreSortedByPriceDescWithCautionBanner() {
        var containers =
                new InventoryOps.PlayerContainers(new SimpleContainer(41), new SimpleContainer(27));
        CompoundTag root = BlackMarketUi.build(containers);

        // 警示横幅：因果/声望代价一句话进包（看板顶部紫色警示行的数据源）
        String caution = root.getString(StrifeUiBridge.Keys.CAUTION);
        assertTrue(caution.contains("因果"), "警示必须提因果——黑市的代价不是差价");
        assertTrue(caution.contains("洗尘丹"), "警示必须给出路");

        // 行：价目全量、价格降序、同价 ID 升序
        var rows = root.getList(StrifeUiBridge.Keys.PRICES, Tag.TAG_COMPOUND);
        assertEquals(BlackMarketCommands.snapshot().size(), rows.size(), "行数 = 暗柜条目数");
        List<Integer> prices = new ArrayList<>();
        String prevId = null;
        int prevPrice = Integer.MAX_VALUE;
        for (var element : rows) {
            CompoundTag row = (CompoundTag) element;
            String itemId = row.getString(StrifeUiBridge.Keys.ITEM);
            int price = row.getInt(StrifeUiBridge.Keys.BUY);
            assertTrue(price > 0, itemId + " 价格必须为正");
            assertTrue(prevPrice >= price, "价格降序");
            if (prevId != null && prevPrice == price) {
                assertTrue(prevId.compareTo(itemId) <= 0, "同价按 ID 升序");
            }
            prices.add(price);
            prevId = itemId;
            prevPrice = price;
        }
        // 贵货在顶：首行价格 = 全表最高
        assertEquals(
                prices.stream().mapToInt(Integer::intValue).max().orElseThrow(),
                prices.get(0),
                "首行 = 最贵赃货");
    }

    @Test
    void ownedCountComesFromTheContainers() {
        // 抽一件价目表里的货进背包 → owned 计数反映（看板行与命令买卖共用同一计数口径）
        var itemId = BlackMarketCommands.snapshot().keySet().stream().findFirst().orElseThrow();
        Item item = InventoryOps.resolve(itemId).orElseThrow();
        var containers =
                new InventoryOps.PlayerContainers(new SimpleContainer(41), new SimpleContainer(27));
        containers.main().setItem(0, new ItemStack(item, 2));
        CompoundTag root = BlackMarketUi.build(containers);
        boolean found = false;
        for (var element : root.getList(StrifeUiBridge.Keys.PRICES, Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) element;
            if (itemId.equals(row.getString(StrifeUiBridge.Keys.ITEM))) {
                assertEquals(2L, row.getLong(StrifeUiBridge.Keys.OWNED), "持有量进行");
                found = true;
            }
        }
        assertTrue(found, "背包中的暗柜货必须有对应行");
    }

    @Test
    void marketBoardSchemaKeysStayStrifePrefixed() {
        assertEquals("blackmarket", StrifeUiBridge.Keys.BLACKMARKET_SCREEN);
        assertEquals("caution", StrifeUiBridge.Keys.CAUTION);
        // 看板家族四屏 screenId 不重名（StrifeUiBridge 路由唯一性）
        assertEquals(
                4,
                java.util.Set.of(
                                StrifeUiBridge.Keys.ALCHEMY_SCREEN,
                                StrifeUiBridge.Keys.MARKET_SCREEN,
                                StrifeUiBridge.Keys.AUCTION_SCREEN,
                                StrifeUiBridge.Keys.BLACKMARKET_SCREEN)
                        .size());
    }

    @Test
    void snapshotIsNeverEmptyForTheBoard() {
        assertFalse(BlackMarketCommands.snapshot().isEmpty(), "空暗柜 = 空屏（价目批门禁假设）");
        // 折扣语义在行内保留：全部条目价 < 明市买价或为黑市专属（既有 BlackMarketTest 锚定，这里只防回归）
        BlackMarketCommands.snapshot().forEach((id, price) -> assertTrue(price > 0, id + " 价格为正"));
    }
}

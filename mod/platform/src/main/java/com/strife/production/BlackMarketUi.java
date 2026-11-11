package com.strife.production;

import com.strife.core.InventoryOps;
import com.strife.core.net.StrifeNetwork;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;

/**
 * 黑市看板的数据构建（StrifeUiBridge 第四屏）：暗柜价目 + 顶部因果警示——同一纪律（服务端算好，客户端只画）。
 *
 * <p>tag schema（production 自钉，{@code BlackMarketUiTest} 锚定）：
 *
 * <pre>
 * root: {
 *   caution: string                       # 顶部警示文案（因果/声望代价一句话）
 *   prices: [ { item, buy, owned } ]      # 按价格降序（贵货在顶）
 * }
 * </pre>
 */
public final class BlackMarketUi {

    private BlackMarketUi() {}

    /** 右键暗柜：组数据并下发（行过滤为当日库存，走商轮换）。 */
    public static void openFor(ServerPlayer player) {
        var containers =
                new InventoryOps.PlayerContainers(
                        player.getInventory(), player.getEnderChestInventory());
        StrifeNetwork.sendTo(
                player,
                new com.strife.core.net.StrifeUiPayload(
                        com.strife.core.StrifeUiBridge.Keys.BLACKMARKET_SCREEN,
                        build(containers, player.level().getGameTime())));
    }

    /** 组数据（纯函数，包内可见供用例直测）。 */
    static CompoundTag build(InventoryOps.PlayerContainers containers, long gameTime) {
        CompoundTag root = new CompoundTag();
        root.putString(com.strife.core.StrifeUiBridge.Keys.CAUTION, "赃货折扣——每笔落因果标记，正道声望受损。洗尘丹可勾销。");
        root.putLong(
                com.strife.core.StrifeUiBridge.Keys.LINGSHI,
                InventoryOps.countOwned(containers, "item_lingshi"));
        record Row(String itemId, int price) {}
        List<Row> entries = new ArrayList<>();
        BlackMarketCommands.stockFor(BlackMarketCommands.gameDayOf(gameTime))
                .forEach((itemId, price) -> entries.add(new Row(itemId, price)));
        entries.sort(Comparator.comparingInt(Row::price).reversed().thenComparing(Row::itemId));
        ListTag rows = new ListTag();
        for (Row entry : entries) {
            CompoundTag row = new CompoundTag();
            row.putString(com.strife.core.StrifeUiBridge.Keys.ITEM, entry.itemId());
            row.putInt(com.strife.core.StrifeUiBridge.Keys.BUY, entry.price());
            row.putLong(
                    com.strife.core.StrifeUiBridge.Keys.OWNED,
                    InventoryOps.countOwned(containers, entry.itemId()));
            rows.add(row);
        }
        root.put(com.strife.core.StrifeUiBridge.Keys.PRICES, rows);
        return root;
    }

    /** 行取数辅助（clientfx 只碰 tag）。 */
    public static java.util.List<Tag> tags(CompoundTag root, String key) {
        ListTag list = root.getList(key, Tag.TAG_COMPOUND);
        java.util.List<Tag> out = new java.util.ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            out.add(list.get(i));
        }
        return out;
    }
}

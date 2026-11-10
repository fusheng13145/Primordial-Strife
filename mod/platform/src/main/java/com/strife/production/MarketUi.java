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
 * 坊市看板的数据构建（StrifeUiBridge 第二屏）：服务端把价目与"你手里有多少"一次性算好下发， 客户端只渲染——与炉交互屏同一纪律（03 §4"客户端只是显示器"）。
 *
 * <p>tag schema（production 自钉，{@code MarketUiTest} 对真实价目产物锚定）：
 *
 * <pre>
 * root: {
 *   exchange_zhong / exchange_shang: int   # 三档兑换比（面板头显示）
 *   appraise_fee: int                      # 鉴定阁每件费用
 *   prices: [ { item, buy, sell, owned } ] # 按回收价降序、同价按内容 ID 升序
 * }
 * </pre>
 */
public final class MarketUi {

    private MarketUi() {}

    /** 右键告示牌：组数据并下发（screenId 走 StrifeUiBridge.Keys.MARKET_SCREEN）。 */
    public static void openFor(ServerPlayer player) {
        var containers =
                new InventoryOps.PlayerContainers(
                        player.getInventory(), player.getEnderChestInventory());
        StrifeNetwork.sendTo(
                player,
                new com.strife.core.net.StrifeUiPayload(
                        com.strife.core.StrifeUiBridge.Keys.MARKET_SCREEN, build(containers)));
    }

    /** 组数据（纯函数，包内可见供用例直测）：纯读价目表 + 背包计数，无 I/O、无玩家依赖。 */
    static CompoundTag build(InventoryOps.PlayerContainers containers) {
        CompoundTag root = new CompoundTag();
        root.putInt(
                com.strife.core.StrifeUiBridge.Keys.EXCHANGE_ZHONG,
                MarketPrices.ratios().tierZhong());
        root.putInt(
                com.strife.core.StrifeUiBridge.Keys.EXCHANGE_SHANG,
                MarketPrices.ratios().tierShang());
        root.putInt(com.strife.core.StrifeUiBridge.Keys.APPRAISE_FEE, MarketPrices.appraiseCost());
        // 行内记录：Quote 本体无 itemId，排序需要——局部 record（不进 MarketPrices 公共面）
        record PriceRow(String itemId, int buy, int sell) {}
        List<PriceRow> entries = new ArrayList<>();
        MarketPrices.snapshot()
                .forEach(
                        (itemId, quote) ->
                                entries.add(
                                        new PriceRow(
                                                itemId, quote.buyLingshi(), quote.sellLingshi())));
        // 回收价降序（值钱的在上看板顶部），同价按内容 ID 升序（展示顺序确定）
        entries.sort(
                Comparator.comparingInt(PriceRow::buy).reversed().thenComparing(PriceRow::itemId));
        ListTag rows = new ListTag();
        for (PriceRow entry : entries) {
            CompoundTag row = new CompoundTag();
            row.putString(com.strife.core.StrifeUiBridge.Keys.ITEM, entry.itemId());
            row.putInt(com.strife.core.StrifeUiBridge.Keys.BUY, entry.buy());
            row.putInt(com.strife.core.StrifeUiBridge.Keys.SELL, entry.sell());
            row.putLong(
                    com.strife.core.StrifeUiBridge.Keys.OWNED,
                    InventoryOps.countOwned(containers, entry.itemId()));
            rows.add(row);
        }
        root.put(com.strife.core.StrifeUiBridge.Keys.PRICES, rows);
        return root;
    }

    /** 行取数辅助（clientfx 侧不碰 production 类型，只碰 tag）。 */
    public static java.util.List<Tag> tags(CompoundTag root, String key) {
        ListTag list = root.getList(key, Tag.TAG_COMPOUND);
        java.util.List<Tag> out = new java.util.ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            out.add(list.get(i));
        }
        return out;
    }
}

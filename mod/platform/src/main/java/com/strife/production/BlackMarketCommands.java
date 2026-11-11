package com.strife.production;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.strife.core.InventoryOps;
import com.strife.core.StrifeAttachmentTypes;
import com.strife.core.StrifeData;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 黑市命令（EP1，ADR-036 ②）：赃货折扣的第二价目段——只卖不收（销赃后置）。
 *
 * <p><b>黑市的代价是因果不是差价</b>：每笔买入 ①落 H3 因果标记 {@code causality:ep1:smuggler}
 * （首次Permanent，弃暗投明的叙事钩子）②青石观声望 −1（正道看在眼里）。价格与门禁由 BlackMarketGenerator 构建期把关。
 */
public final class BlackMarketCommands {

    /** H3 走私因果标记：黑市写入、洗尘丹清除（StrifePills）——同包共享，字符串只此一份。 */
    static final String SMUGGLER_FLAG = "causality:ep1:smuggler";

    private static final int REPUTATION_COST_PER_BUY = -1;

    // 走商轮换（[拟]）：暗柜每天只摆 STOCK_COUNT 件（价目里的滑动窗口），按游戏日确定性推导
    // （荷兰拍同款零持久化承诺）——"今天暗柜有什么"成为走商体验；全量价目口径留给测试与清单。
    static final int STOCK_COUNT = 8;
    static final long GAME_DAY_TICKS = 24000L;

    private BlackMarketCommands() {}

    /** 黑市价目（item_id → 下品灵石单价）。 */
    public static Optional<Integer> price(String itemId) {
        return Optional.ofNullable(load().get(itemId));
    }

    public static Map<String, Integer> snapshot() {
        return load();
    }

    /**
     * 当日暗柜库存（纯函数，[拟]）：按游戏日确定性推导——价目 ID 排序后取滑动窗口 （起点 = 日序 × STOCK_COUNT 对全量取模，环绕取件），窗口每日滑动 → 全量价目在
     * ⌈size/count⌉ 天内全部露面（公平轮换，无永久压箱底）。同一游戏日两次取数完全一致。
     */
    static java.util.Map<String, Integer> stockFor(long gameDay) {
        var all = new java.util.TreeMap<>(load());
        int size = all.size();
        if (size <= STOCK_COUNT) {
            return java.util.Collections.unmodifiableMap(all);
        }
        int start = (int) (Math.floorMod(gameDay * STOCK_COUNT, size));
        var picked = new java.util.LinkedHashMap<String, Integer>();
        for (int i = 0; i < STOCK_COUNT; i++) {
            String id = all.keySet().stream().skip((start + i) % size).findFirst().orElseThrow();
            picked.put(id, all.get(id));
        }
        return java.util.Collections.unmodifiableMap(picked);
    }

    /** 当日游戏日序（纯时间推导：gameTime / 24000）。 */
    static long gameDayOf(long gameTime) {
        return Math.floorDiv(gameTime, GAME_DAY_TICKS);
    }

    /** 当日是否在售（纯函数）：走商轮换的成员判定——buyFor 与看板行过滤共用同一谓词。 */
    static boolean inStock(long gameTime, String itemId) {
        return stockFor(gameDayOf(gameTime)).containsKey(itemId);
    }

    public static LiteralArgumentBuilder<CommandSourceStack> subtree() {
        return Commands.literal("blackmarket")
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(
                        Commands.literal("buy")
                                .then(
                                        Commands.argument(
                                                        "item_id",
                                                        com.mojang.brigadier.arguments
                                                                .StringArgumentType.string())
                                                .executes(
                                                        ctx ->
                                                                buy(
                                                                        ctx.getSource(),
                                                                        com.mojang.brigadier
                                                                                .arguments
                                                                                .StringArgumentType
                                                                                .getString(
                                                                                        ctx,
                                                                                        "item_id"),
                                                                        1))
                                                .then(
                                                        Commands.argument(
                                                                        "count",
                                                                        IntegerArgumentType.integer(
                                                                                1))
                                                                .executes(
                                                                        ctx ->
                                                                                buy(
                                                                                        ctx
                                                                                                .getSource(),
                                                                                        com.mojang
                                                                                                .brigadier
                                                                                                .arguments
                                                                                                .StringArgumentType
                                                                                                .getString(
                                                                                                        ctx,
                                                                                                        "item_id"),
                                                                                        IntegerArgumentType
                                                                                                .getInteger(
                                                                                                        ctx,
                                                                                                        "count"))))));
    }

    private static int list(CommandSourceStack source) {
        StringBuilder sb = new StringBuilder("黑市价目（下品灵石，概不退换）：");
        for (Map.Entry<String, Integer> e : snapshot().entrySet()) {
            sb.append('\n').append(e.getKey()).append(' ').append(e.getValue());
        }
        source.sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    /** 命令包装：控制台拒绝后走玩家直参内核（内核与黑市看板意图共用）。 */
    private static int buy(CommandSourceStack source, String itemId, int count)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        return buyFor(player, itemId, count) ? 1 : 0;
    }

    /** 购入内核（玩家直参；黑市看板意图与命令共用，含因果标记与正道声望代价）。返回是否成交。 */
    static boolean buyFor(ServerPlayer player, String itemId, int count) {
        Integer price = price(itemId).orElse(null);
        if (price == null) {
            player.displayClientMessage(Component.literal("（黑市没有这件货：" + itemId + "）"), true);
            return false;
        }
        // 走商轮换（[拟]）：今日暗柜没摆的货不卖——命令与看板意图同门槛（fail-closed）
        if (!inStock(player.level().getGameTime(), itemId)) {
            player.displayClientMessage(Component.literal("（今天暗柜没摆这件——天亮再来，或问问别的渠道）"), true);
            return false;
        }
        int cost = price * count;
        InventoryOps.PlayerContainers containers =
                new InventoryOps.PlayerContainers(
                        player.getInventory(), player.getEnderChestInventory());
        if (InventoryOps.countOwned(containers, "item_lingshi") < cost) {
            player.displayClientMessage(Component.literal("（灵石不足：需 " + cost + "）"), true);
            return false;
        }
        long removed = InventoryOps.remove(containers, "item_lingshi", cost);
        if (removed < cost) {
            player.displayClientMessage(Component.literal("（结算异常已回滚）"), true);
            return false;
        }
        Item item = InventoryOps.resolve(itemId).orElse(null);
        if (item == null) {
            player.getInventory().add(new ItemStack(StrifeItems.LINGSHI.get(), (int) removed));
            player.displayClientMessage(Component.literal("（该货暂未到岸，已退灵石）"), true);
            return false;
        }
        ItemStack bought = new ItemStack(item, count);
        // ADR-037 ③：黑市货未鉴定——鉴定阁去除标记前，品阶与加成对玩家隐藏。
        com.strife.core.ArtifactTags.markUnidentified(bought);
        player.getInventory().add(bought);

        // 代价：H3 因果标记（首次永久）+ 正道声望负增量（每笔）。
        StrifeData data = player.getData(StrifeAttachmentTypes.PLAYER_DATA);
        if (!data.quests().hasFlag(SMUGGLER_FLAG)) {
            data = data.withQuests(data.quests().withFlag(SMUGGLER_FLAG));
            player.displayClientMessage(Component.literal("（因果 +1：这笔买卖，有人记下了）"), true);
        }
        Map<String, Integer> reputation = new java.util.HashMap<>(data.reputation());
        reputation.merge("fac_qingshi", REPUTATION_COST_PER_BUY, Integer::sum);
        data = data.withReputation(Map.copyOf(reputation));
        player.setData(StrifeAttachmentTypes.PLAYER_DATA, data);

        player.displayClientMessage(
                Component.literal("✦ 黑市交割 " + itemId + " ×" + count + "（灵石 " + cost + "）"), true);
        return true;
    }

    private static volatile Map<String, Integer> cache;

    private static Map<String, Integer> load() {
        Map<String, Integer> cached = cache;
        if (cached != null) {
            return cached;
        }
        synchronized (BlackMarketCommands.class) {
            if (cache != null) {
                return cache;
            }
            try (InputStream stream =
                    Objects.requireNonNull(
                            BlackMarketCommands.class.getResourceAsStream(
                                    "/data/strife/strife_blackmarket/market.json"),
                            "strife_blackmarket/market.json not in jar — BlackMarketGenerator 未跑？")) {
                com.google.gson.JsonObject product =
                        com.google.gson.JsonParser.parseReader(
                                        new InputStreamReader(stream, StandardCharsets.UTF_8))
                                .getAsJsonObject();
                Map<String, Integer> entries = new LinkedHashMap<>();
                for (var e : product.getAsJsonArray("entries")) {
                    var o = e.getAsJsonObject();
                    entries.put(o.get("item_id").getAsString(), o.get("price_lingshi").getAsInt());
                }
                cache = java.util.Collections.unmodifiableMap(entries);
                return cache;
            } catch (Exception e) {
                throw new IllegalStateException("cannot read strife_blackmarket/market.json", e);
            }
        }
    }

    /** 测试与 /reload 后清缓存。 */
    static void invalidate() {
        cache = null;
    }

    /** 供 ResourceLocation 引用一致性检查（产物路径单一事实）。 */
    static ResourceLocation productPath() {
        return ResourceLocation.fromNamespaceAndPath("strife", "strife_blackmarket/market.json");
    }
}

package com.strife.production;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.strife.core.StrifeTime;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 拍卖行定价核（EP1，ADR-037 ①：荷兰式降价拍）。
 *
 * <p><b>当前价由游戏时间确定性推导，零持久化状态</b>——轮换、降价、触底全部可由 (gameTime, 产物) 重算；多买家各按当下价成交（"急售"语义，ADR-037 ① 显式收敛）。
 * 有状态密封投标 = 第三批。
 */
public final class AuctionHouse {

    /** 一件拍品。 */
    public record Lot(String id, String itemId, int start, int min, int drop) {}

    private record Catalog(Map<String, Lot> lots, int slotShichen) {}

    private static volatile Catalog catalog;

    private AuctionHouse() {}

    /** 轮换：当前槽位对应的拍品（按表序循环）。 */
    public static Lot currentLot(long gameTime) {
        Catalog c = load();
        int index = (int) ((gameTime / slotTicks()) % c.lots().size());
        return c.lots().values().stream().skip(index).findFirst().orElseThrow();
    }

    /** 当前槽位已流逝刻数（用于剩余时间展示）。 */
    public static long elapsedInSlot(long gameTime) {
        return gameTime % slotTicks();
    }

    /** 当前价：起拍价按流逝时辰逐次降幅，触底即 {@code min}。纯函数（包内可见供用例直测）。 */
    static int currentPrice(Lot lot, long elapsedTicks, long slotTicks) {
        long perShichen = slotTicks / 2; // slot_shichen=2：每槽 2 时辰
        long elapsedShichen = elapsedTicks / perShichen;
        long dropped = elapsedShichen * lot.drop();
        return (int) Math.max(lot.min(), Math.max(0L, lot.start() - dropped));
    }

    /** 便捷：当前槽位拍品的当前价。 */
    public static Optional<Integer> currentPriceFor(long gameTime) {
        Lot lot = currentLot(gameTime);
        return Optional.of(currentPrice(lot, elapsedInSlot(gameTime), slotTicks()));
    }

    /** 槽位时长（刻）= slot_shichen × 12000（与时辰换算同源）。 */
    public static long slotTicks() {
        return load().slotShichen() * 12000L;
    }

    /** 全目录快照（list 命令用）。 */
    public static Map<String, Lot> lots() {
        return load().lots();
    }

    private static Catalog load() {
        Catalog cached = catalog;
        if (cached != null) {
            return cached;
        }
        synchronized (AuctionHouse.class) {
            if (catalog != null) {
                return catalog;
            }
            try (InputStream stream =
                    Objects.requireNonNull(
                            AuctionHouse.class.getResourceAsStream(
                                    "/data/strife/strife_auction/auction.json"),
                            "strife_auction/auction.json not in jar — AuctionGenerator 未跑？")) {
                JsonObject product =
                        JsonParser.parseReader(
                                        new InputStreamReader(stream, StandardCharsets.UTF_8))
                                .getAsJsonObject();
                Map<String, Lot> lots = new LinkedHashMap<>();
                int slotShichen =
                        product.get("slot_shichen") == null
                                ? 2
                                : product.get("slot_shichen").getAsInt();
                for (var e : product.getAsJsonArray("lots")) {
                    JsonObject o = e.getAsJsonObject();
                    Lot lot =
                            new Lot(
                                    o.get("id").getAsString(),
                                    o.get("item_id").getAsString(),
                                    o.get("start_lingshi").getAsInt(),
                                    o.get("min_lingshi").getAsInt(),
                                    o.get("drop_lingshi").getAsInt());
                    lots.put(lot.id(), lot);
                }
                catalog = new Catalog(java.util.Collections.unmodifiableMap(lots), slotShichen);
                return catalog;
            } catch (Exception e) {
                throw new IllegalStateException("cannot read strife_auction/auction.json", e);
            }
        }
    }

    /** 测试与 /reload 后清缓存。 */
    static void invalidate() {
        catalog = null;
    }

    /** 供命令层显示时辰余量（StrifeTime 换算一致性）。 */
    static long remainingShichen(long elapsedTicks) {
        return Math.max(0L, (slotTicks() - elapsedTicks) / StrifeTime.TICKS_PER_SECOND / 600L);
    }
}

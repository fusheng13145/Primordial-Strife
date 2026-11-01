package com.strife.production;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 坊市定价与三档兑换的运行时表（EP1，ADR-032）：classpath 直读 DataGen 产物 {@code strife_prices/prices.json}（{@code
 * tables/prices.csv} + NUMBERS {@code @@market} 直出）。
 *
 * <p>价格语义由生成器构建期把关（sell&lt;buy、非负、唯一），本类只「读出来并保持形状」； 未知物品回 empty（未上架=不可交易，调用方给玩家可读解释）。兑换比走服务端权威——
 * 三档换算只认这里，命令层不得自带常数（AGENTS.md 受管数值零字面量）。
 */
public final class MarketPrices {

    private static final Logger LOGGER = LoggerFactory.getLogger("strife/production");

    /** 一条定价：买入价（玩家付出）与回收价（玩家得到），下品灵石计价。 */
    public record Quote(int buyLingshi, int sellLingshi) {}

    /** 三档兑换比：中品/上品对下品的倍率（NUMBERS @@market）。 */
    public record Ratios(int tierZhong, int tierShang) {}

    private static volatile Cache cache;

    private record Cache(Map<String, Quote> entries, Ratios ratios, int appraiseCost) {}

    private MarketPrices() {}

    /** 鉴定阁每件手续费（NUMBERS @@market.appraise_cost）。 */
    public static int appraiseCost() {
        return load().appraiseCost();
    }

    /** 全量价目快照（/strife market list 用；只读映射）。 */
    public static Map<String, Quote> snapshot() {
        return load().entries();
    }

    /** 按内容 ID 取定价；未上架返回 empty。 */
    public static Optional<Quote> quote(String itemId) {
        return Optional.ofNullable(load().entries().get(itemId));
    }

    public static Ratios ratios() {
        return load().ratios();
    }

    /**
     * 把下品灵石总量换算成三档数量（下品入 {@code breakdown[2]}，余数保底）。
     *
     * <p>包内可见供用例直测：兑换是纯算术，契约在「不丢钱」——任何输入下 {@code shang*shangRatio + zhong*zhongRatio + xia == 输入}。
     */
    static int[] breakdown(int lingshi, Ratios ratios) {
        int shang = lingshi / ratios.tierShang();
        int rest = lingshi - shang * ratios.tierShang();
        int zhong = rest / ratios.tierZhong();
        int xia = rest - zhong * ratios.tierZhong();
        return new int[] {shang, zhong, xia};
    }

    private static Cache load() {
        Cache cached = cache;
        if (cached != null) {
            return cached;
        }
        synchronized (MarketPrices.class) {
            if (cache != null) {
                return cache;
            }
            cache = parse();
            return cache;
        }
    }

    private static Cache parse() {
        try (InputStream stream =
                Objects.requireNonNull(
                        MarketPrices.class.getResourceAsStream(
                                "/data/strife/strife_prices/prices.json"),
                        "strife_prices/prices.json not in jar — PriceGenerator 未跑？")) {
            JsonObject product =
                    JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                            .getAsJsonObject();
            if (product.get("@generated") == null) {
                throw new IllegalStateException(
                        "strife_prices/prices.json missing @generated header");
            }
            JsonObject ratiosJson = product.getAsJsonObject("ratios");
            Ratios ratios =
                    new Ratios(
                            requireInt(ratiosJson, "tier_zhong"),
                            requireInt(ratiosJson, "tier_shang"));
            if (ratios.tierZhong() <= 0 || ratios.tierShang() <= ratios.tierZhong()) {
                throw new IllegalStateException(
                        "market ratios invalid: tier_shang 必须 > tier_zhong > 0（否则兑换会丢钱）");
            }
            Map<String, Quote> entries = new LinkedHashMap<>();
            JsonArray array = product.getAsJsonArray("entries");
            for (JsonElement e : array) {
                JsonObject entry = e.getAsJsonObject();
                entries.put(
                        entry.get("item_id").getAsString(),
                        new Quote(
                                entry.get("buy_lingshi").getAsInt(),
                                entry.get("sell_lingshi").getAsInt()));
            }
            LOGGER.info(
                    "strife market prices loaded: {} entries, ratios {}/{}",
                    entries.size(),
                    ratios.tierZhong(),
                    ratios.tierShang());
            int appraiseCost =
                    ratiosJson.has("appraise_cost") ? requireInt(ratiosJson, "appraise_cost") : 5;
            return new Cache(Map.copyOf(entries), ratios, appraiseCost);
        } catch (Exception e) {
            throw new IllegalStateException("cannot read strife_prices/prices.json", e);
        }
    }

    private static int requireInt(JsonObject object, String key) {
        if (!object.has(key)) {
            throw new IllegalStateException("strife_prices/prices.json 缺键 '" + key + "'");
        }
        return object.get(key).getAsInt();
    }

    /** 测试与 /reload 后强制重读。 */
    static void invalidate() {
        cache = null;
    }

    /** 仅供测试断言条目数。 */
    static int size() {
        return load().entries().size();
    }

    /** 仅供测试：构造指定缓存（换算用例不依赖产物重生成）。 */
    static void setForTest(Map<String, Quote> entries, Ratios ratios) {
        cache = new Cache(new HashMap<>(entries), ratios, 5);
    }
}

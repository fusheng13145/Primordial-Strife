package com.strife.core;

import net.minecraft.nbt.CompoundTag;

/**
 * 服务端驱动 UI 的跨侧桥（炉交互屏，ADR-038 交互层）：数据屏（炼丹界面、此后的坊市/拍卖看板）由服务端组数据、 经 {@code
 * com.strife.core.net.StrifeUiPayload} 下发，clientfx 按 screenId 注册开启器渲染—— 照 {@link GuideUiBridge}
 * 的注册口范式：拥有方（clientfx）注册实现，需求方调用。
 *
 * <p><b>没注册就等于没有这条路</b>：调用返回 false（专用服/无 clientfx 环境右键炉 = 无操作， 炼制走 {@code /strife craft pill}
 * 命令通道——命令与面板同源，与 D12 ④ 同纪律）。
 *
 * <p>数据信封是 {@link CompoundTag}：core 不 import production（03 §2），各域自己定义 tag 内的 schema 并在
 * 自己的文档里钉住键名；bridge 只负责"按 screenId 找到开启器"。
 */
public final class StrifeUiBridge {

    @FunctionalInterface
    public interface Opener {
        /** 用服务端数据开启界面；返回是否真的开了（未注册的 screenId 回 false）。 */
        boolean open(CompoundTag data);
    }

    private static volatile java.util.Map<String, Opener> openers = java.util.Map.of();

    private StrifeUiBridge() {}

    /** 注册一个 screenId 的开启器；重复注册是设计错误（两个屏抢同一个 id）。 */
    public static synchronized void registerOpener(String screenId, Opener opener) {
        if (screenId == null || screenId.isBlank()) {
            throw new IllegalArgumentException("screen id must not be blank");
        }
        if (opener == null) {
            throw new IllegalArgumentException("opener must not be null");
        }
        java.util.Map<String, Opener> updated = new java.util.LinkedHashMap<>(openers);
        Opener previous = updated.putIfAbsent(screenId, opener);
        if (previous != null) {
            throw new IllegalStateException("ui opener '" + screenId + "' already registered");
        }
        openers = java.util.Collections.unmodifiableMap(updated);
    }

    /** 已登记的 screenId（启动自检与诊断用）。 */
    public static java.util.Set<String> wiredScreens() {
        return openers.keySet();
    }

    /** 客户端开启数据屏；未接入回 false（调用方不提示——命令通道兜底）。 */
    public static boolean open(String screenId, CompoundTag data) {
        Opener opener = openers.get(screenId);
        return opener != null && data != null && opener.open(data);
    }

    /**
     * 数据屏的共享 schema 键（ArtifactTags 同款取舍：组数据的域与渲染屏的 clientfx 都要引用，而 clientfx 只依赖
     * core——字符串常量只能落在本类，谁定义到各自模块谁就把依赖方向写反了）。 各域在自己文档里钉键名语义，这里只防"两侧拼错字符串"。
     */
    public static final class Keys {

        /** 炼丹屏（production AlchemyUi 组数据 / clientfx AlchemyScreen 渲染，ADR-038 交互层）。 */
        public static final String ALCHEMY_SCREEN = "alchemy";

        public static final String FURNACE = "furnace";
        public static final String FIRE = "fire";
        public static final String FIRE_EFFECTS = "fire_effects";
        public static final String RECIPES = "recipes";
        public static final String PILL = "pill";
        public static final String CRAFTABLE = "craftable";
        public static final String MATERIALS = "materials";
        public static final String ITEM = "item";
        public static final String NEED = "need";
        public static final String HAVE = "have";
        public static final String OUTPUTS = "outputs";
        public static final String COUNT = "count";
        public static final String PROB = "prob";

        /** 坊市看板（production MarketUi 组数据 / clientfx MarketBoardScreen 渲染，2026-11-10）。 */
        public static final String MARKET_SCREEN = "market";

        public static final String PRICES = "prices";
        public static final String BUY = "buy";
        public static final String SELL = "sell";
        public static final String OWNED = "owned";
        public static final String EXCHANGE_ZHONG = "exchange_zhong";
        public static final String EXCHANGE_SHANG = "exchange_shang";
        public static final String APPRAISE_FEE = "appraise_fee";

        /** 拍卖看板（production AuctionUi 组数据 / clientfx AuctionBoardScreen 渲染，2026-11-10）。 */
        public static final String AUCTION_SCREEN = "auction";

        public static final String LOTS = "lots";
        public static final String LOT = "lot";
        public static final String PRICE = "price";
        public static final String MIN = "min";
        public static final String REMAIN = "remain_shichen";
        public static final String CURRENT = "current";
        public static final String MY_BIDS = "my_bids";
        public static final String AMOUNT = "amount";
        public static final String STATUS = "status";
        public static final String PAYOUTS = "payouts";
        public static final String LINGSHI = "lingshi";

        /** 黑市看板（production BlackMarketUi 组数据 / clientfx BlackMarketScreen 渲染，2026-11-11）。 */
        public static final String BLACKMARKET_SCREEN = "blackmarket";

        public static final String CAUTION = "caution";

        private Keys() {}
    }
}

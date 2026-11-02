package com.strife.core;

import net.minecraft.server.level.ServerPlayer;

/**
 * 神识上限的跨模块桥（丹药接线批）：上限公式 {@code maxBase + maxPerOrdinal × 境界序数} 的输入在 realm 表里， 而 production
 * 的凝神丹需要它算"恢复到上限"——production 不 import realm（03 §2），照 {@link RewardBridges}
 * 注册口范式：拥有方（realm）注册实现，需求方调用。
 *
 * <p><b>没注册就等于没有这条数据</b>：调用回 -1，凝神丹据此拒绝服用（"神识上限未知"），不猜一个默认上限。
 */
public final class ShenShiBridge {

    @FunctionalInterface
    public interface MaxProvider {
        /** 该玩家当前的神识上限；数据不可用时回 -1。 */
        int maxFor(ServerPlayer player);
    }

    private static volatile MaxProvider provider;

    private ShenShiBridge() {}

    /** 注册神识上限来源；重复注册是设计错误。 */
    public static synchronized void registerMaxProvider(MaxProvider maxProvider) {
        if (maxProvider == null) {
            throw new IllegalArgumentException("shenshi max provider must not be null");
        }
        if (provider != null) {
            throw new IllegalStateException("shenshi max provider already registered");
        }
        provider = maxProvider;
    }

    /** 当前神识上限；未接线或数据不可用回 -1（调用方不作默认值猜测）。 */
    public static int maxFor(ServerPlayer player) {
        MaxProvider maxProvider = provider;
        if (maxProvider == null || player == null) {
            return -1;
        }
        return maxProvider.maxFor(player);
    }

    /** 仅测试用：清空接线。 */
    static synchronized void resetForTest() {
        provider = null;
    }
}

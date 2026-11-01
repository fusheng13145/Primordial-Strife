package com.strife.core;

import net.minecraft.world.item.ItemStack;

/**
 * 入门指引 UI 的跨侧桥（P1-21，D12）：指引物品在 production 注册（use 触发）， 面板在 clientfx 渲染——production 不 import
 * clientfx（03 §2），照 {@link RewardBridges} 的注册口范式：拥有方（clientfx）注册实现，需求方调用。
 *
 * <p><b>没注册就等于没有这条路</b>：调用返回 false（指引物品在专用服/无 clientfx 环境 右键 = 无操作，指引内容走 {@code /strife guide}
 * 命令通道——D12 ④ 命令与面板同源）。
 */
public final class GuideUiBridge {

    @FunctionalInterface
    public interface UiOpener {
        boolean open(ItemStack stack);
    }

    private static volatile UiOpener opener;

    private GuideUiBridge() {}

    /** 注册客户端面板开启实现；重复注册是设计错误。 */
    public static synchronized void registerUiOpener(UiOpener uiOpener) {
        if (uiOpener == null) {
            throw new IllegalArgumentException("guide ui opener must not be null");
        }
        if (opener != null) {
            throw new IllegalStateException("guide ui opener already registered");
        }
        opener = uiOpener;
    }

    /** 是否已接 UI（自检用）。 */
    public static boolean wired() {
        return opener != null;
    }

    /** 客户端开启指引面板；未接入返回 false（调用方不作任何提示——命令通道兜底）。 */
    public static boolean openClient(ItemStack stack) {
        UiOpener uiOpener = opener;
        return uiOpener != null && uiOpener.open(stack);
    }

    /** 仅测试用：清空接线。 */
    static synchronized void resetForTest() {
        opener = null;
    }
}

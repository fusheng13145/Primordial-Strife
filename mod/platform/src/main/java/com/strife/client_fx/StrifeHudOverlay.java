package com.strife.client_fx;

import com.strife.core.StrifeData;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 修仙 HUD（docs/07 §7 B0 面板空壳的第一块可见物）：屏幕左下角常驻境界 / 修为 / 寿元 / 灵根。 数据读取与降级规则在 {@link
 * ClientDataBridge}；详情面板见 {@link StrifePanelScreen}。
 */
public final class StrifeHudOverlay {

    private static final int COLOR = 0xFFFFFFFF;

    private StrifeHudOverlay() {}

    static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        StrifeData data = ClientDataBridge.authoritative(minecraft);
        if (data == null) {
            return;
        }
        Component cultivation =
                Component.empty()
                        .append(
                                Component.translatable(
                                        "gui.strife.hud.realm", ClientDataBridge.realmName(data)))
                        .append(" · ")
                        .append(Component.translatable("gui.strife.hud.qi", data.qi()));
        Component condition =
                Component.empty()
                        .append(
                                Component.translatable(
                                        "gui.strife.hud.lifespan",
                                        data.lifespanTicks() / StrifeData.TICKS_PER_YEAR))
                        .append(" · ")
                        .append(
                                Component.translatable(
                                        "gui.strife.hud.spiritroot",
                                        ClientDataBridge.spiritroot(data)));
        int x = 4;
        int y = graphics.guiHeight() - 44;
        graphics.drawString(minecraft.font, cultivation, x, y, COLOR);
        graphics.drawString(minecraft.font, condition, x, y + 10, COLOR);
    }
}

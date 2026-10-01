package com.strife.client_fx;

import com.strife.core.StrifeData;
import com.strife.core.net.StrifeClientMirror;
import com.strife.core.net.StrifeRealmView;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 修仙 HUD（docs/07 §7 B0 面板空壳的第一块可见物）：屏幕左下角常驻境界 / 修为 / 寿元 / 灵根，并在"打坐中 / 可突破"时多一行状态。 数据读取与降级规则在 {@link
 * ClientDataBridge}；详情面板见 {@link StrifePanelScreen}。
 */
public final class StrifeHudOverlay {

    private static final int COLOR = 0xFFFFFFFF;
    private static final int HIGHLIGHT_COLOR = 0xFF9ADCC8;

    private StrifeHudOverlay() {}

    static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        StrifeData data = ClientDataBridge.mirror();
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
        long lifespanYears = ClientDataBridge.lifespanYears(data);
        Component lifespan =
                lifespanYears < 0L
                        ? Component.translatable("gui.strife.hud.lifespan_unknown")
                        : Component.translatable("gui.strife.hud.lifespan", lifespanYears);
        Component condition =
                Component.empty()
                        .append(lifespan)
                        .append(" · ")
                        .append(
                                Component.translatable(
                                        "gui.strife.hud.spiritroot",
                                        ClientDataBridge.spiritroot(data)));
        int x = 4;
        int y = graphics.guiHeight() - 44;
        graphics.drawString(minecraft.font, cultivation, x, y, COLOR);
        graphics.drawString(minecraft.font, condition, x, y + 10, COLOR);
        StrifeRealmView view = StrifeClientMirror.realmView();
        if (view != null && (view.meditating() || view.canBreakthrough())) {
            // 常驻状态行只在"有可行动的事"时出现：打坐中、或修为已满可押注。
            Component status =
                    view.meditating()
                            ? Component.translatable("gui.strife.hud.sitting")
                            : Component.translatable("gui.strife.hud.breakthrough_ready");
            graphics.drawString(minecraft.font, status, x, y + 20, HIGHLIGHT_COLOR);
        }
    }
}

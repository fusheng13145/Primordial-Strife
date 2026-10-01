package com.strife.client_fx;

import com.strife.core.StrifeData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 修炼面板（docs/07 §7 B1-1 的详情壳）：默认 K 键打开，展示 HUD 之外的完整玩家侧数据—— 境界与小境界、修为、寿元、灵根五行与品阶、突破失败累计、所属势力与 H2
 * 声望向量。
 *
 * <p>数据来源与降级同 {@link ClientDataBridge}：单机读权威附件；无镜像时面板打开但只给 一行说明，不显示编造数值。打坐打断表现与实时刷新依赖 M1 的结算与同步，属于
 * B1-1 后半。
 */
public final class StrifePanelScreen extends net.minecraft.client.gui.screens.Screen {

    private static final int COLOR = 0xFFFFFFFF;
    private static final int TITLE_COLOR = 0xFF9ADCC8;
    private static final int BACKGROUND = 0xF00E1016;
    private static final int BORDER = 0xFF2E4A40;

    public StrifePanelScreen() {
        super(Component.translatable("gui.strife.panel.title"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Minecraft minecraft = this.minecraft;
        List<Component> lines = new ArrayList<>(panelLines(minecraft));
        int textWidth = lines.stream().mapToInt(line -> minecraft.font.width(line)).max().orElse(0);
        int titleWidth = minecraft.font.width(title);
        int boxWidth = Math.max(textWidth, titleWidth) + 20;
        int boxHeight = lines.size() * 12 + 34;
        int left = (this.width - boxWidth) / 2;
        int top = (this.height - boxHeight) / 2;

        graphics.fill(left, top, left + boxWidth, top + boxHeight, BACKGROUND);
        graphics.renderOutline(left, top, boxWidth, boxHeight, BORDER);
        graphics.drawCenteredString(minecraft.font, title, this.width / 2, top + 8, TITLE_COLOR);
        int y = top + 24;
        for (Component line : lines) {
            graphics.drawString(minecraft.font, line, left + 10, y, COLOR);
            y += 12;
        }
    }

    private static List<Component> panelLines(Minecraft minecraft) {
        StrifeData data = ClientDataBridge.mirror();
        List<Component> lines = new ArrayList<>();
        if (data == null) {
            lines.add(Component.translatable("gui.strife.panel.no_data"));
            return lines;
        }
        lines.add(
                Component.translatable("gui.strife.panel.realm", ClientDataBridge.realmName(data)));
        lines.add(Component.translatable("gui.strife.panel.stage", data.stage()));
        lines.add(Component.translatable("gui.strife.hud.qi", data.qi()));
        long lifespanYears = ClientDataBridge.lifespanYears(data);
        lines.add(
                lifespanYears < 0L
                        ? Component.translatable(
                                "gui.strife.panel.lifespan_unknown", data.lifespanTicks())
                        : Component.translatable(
                                "gui.strife.panel.lifespan", lifespanYears, data.lifespanTicks()));
        lines.add(
                Component.translatable(
                        "gui.strife.panel.spiritroot_full", ClientDataBridge.spiritroot(data)));
        lines.add(
                Component.translatable(
                        "gui.strife.panel.breakthrough_attempts", data.breakthroughAttempts()));
        lines.add(
                Component.translatable(
                        "gui.strife.panel.affiliation", ClientDataBridge.affiliation(data)));
        List<Component> reputation = ClientDataBridge.reputationLines(data);
        lines.addAll(
                reputation.isEmpty()
                        ? List.of(Component.translatable("gui.strife.panel.reputation.none"))
                        : reputation);
        return lines;
    }
}

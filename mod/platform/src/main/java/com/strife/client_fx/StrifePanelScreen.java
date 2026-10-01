package com.strife.client_fx;

import com.strife.core.StrifeData;
import com.strife.core.net.IntentRateLimiter;
import com.strife.core.net.StrifeClientMirror;
import com.strife.core.net.StrifeRealmView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 修炼面板（docs/07 §7 B1-1）：默认 K 键打开，展示 HUD 之外的完整玩家侧数据，并给出两个动作入口（打坐起止、押注突破）。
 *
 * <p>数据来源分工明确：
 *
 * <ul>
 *   <li>身份类数据（境界名/修为/寿元/灵根/声望）来自 {@link StrifeClientMirror} 的权威镜像（03 §5 同步包）；
 *   <li>处境类数据（修为上限、本次成功率、失败回退区间、当前速率、打坐与冷却状态）来自服务端计算的 {@link StrifeRealmView} （03 §5 拉取式）——客户端不复制任何
 *       realm 的表与公式。
 * </ul>
 *
 * <p>打开面板即发一次 {@code panel} 意图拉取视图；按钮只发意图，判定在服务端（03 §4）。无镜像时只显示一行说明，不显示编造数值。
 */
public final class StrifePanelScreen extends Screen {

    private static final int COLOR = 0xFFFFFFFF;
    private static final int TITLE_COLOR = 0xFF9ADCC8;
    private static final int BACKGROUND = 0xF00E1016;
    private static final int BORDER = 0xFF2E4A40;
    private static final int MIN_WIDTH = 260;
    private static final int LINE_HEIGHT = 12;
    private static final int BUTTON_HEIGHT = 20;

    private Button sitButton;
    private Button breakthroughButton;
    private boolean viewRequested;

    public StrifePanelScreen() {
        super(Component.translatable("gui.strife.panel.title"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        sitButton =
                Button.builder(
                                Component.translatable("key.strife.sit"),
                                button -> ClientIntents.send(IntentRateLimiter.SIT))
                        .bounds(0, 0, 120, BUTTON_HEIGHT)
                        .build();
        breakthroughButton =
                Button.builder(
                                Component.translatable("key.strife.breakthrough"),
                                button -> ClientIntents.send(IntentRateLimiter.BREAKTHROUGH))
                        .bounds(0, 0, 120, BUTTON_HEIGHT)
                        .build();
        addRenderableWidget(sitButton);
        addRenderableWidget(breakthroughButton);
        if (!viewRequested) {
            viewRequested = true;
            ClientIntents.send(IntentRateLimiter.PANEL);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Minecraft minecraft = this.minecraft;
        if (minecraft == null) {
            return;
        }
        List<Component> lines = panelLines();
        int textWidth = lines.stream().mapToInt(line -> minecraft.font.width(line)).max().orElse(0);
        int boxWidth = Math.max(MIN_WIDTH, Math.max(textWidth, minecraft.font.width(title)) + 20);
        int boxHeight = lines.size() * LINE_HEIGHT + 34 + BUTTON_HEIGHT + 12;
        int left = (this.width - boxWidth) / 2;
        int top = (this.height - boxHeight) / 2;

        graphics.fill(left, top, left + boxWidth, top + boxHeight, BACKGROUND);
        graphics.renderOutline(left, top, boxWidth, boxHeight, BORDER);
        graphics.drawCenteredString(minecraft.font, title, this.width / 2, top + 8, TITLE_COLOR);
        int y = top + 24;
        for (Component line : lines) {
            graphics.drawString(minecraft.font, line, left + 10, y, COLOR);
            y += LINE_HEIGHT;
        }

        layoutButtons(left, boxWidth, top + boxHeight - BUTTON_HEIGHT - 8);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void layoutButtons(int left, int boxWidth, int buttonY) {
        int gap = 6;
        int buttonWidth = (boxWidth - 20 - gap) / 2;
        sitButton.setPosition(left + 10, buttonY);
        sitButton.setWidth(buttonWidth);
        breakthroughButton.setPosition(left + 10 + buttonWidth + gap, buttonY);
        breakthroughButton.setWidth(buttonWidth);

        StrifeRealmView view = StrifeClientMirror.realmView();
        // 修为未满时按钮置灰，但服务端仍会校验——界面只是提前把"按了也没用"告诉玩家。
        breakthroughButton.active = view != null && view.canBreakthrough();
    }

    private static List<Component> panelLines() {
        StrifeData data = ClientDataBridge.mirror();
        List<Component> lines = new ArrayList<>();
        if (data == null) {
            lines.add(Component.translatable("gui.strife.panel.no_data"));
            return lines;
        }
        lines.add(
                Component.translatable("gui.strife.panel.realm", ClientDataBridge.realmName(data)));

        StrifeRealmView view = StrifeClientMirror.realmView();
        if (view == null) {
            lines.add(Component.translatable("gui.strife.panel.view_pending"));
        } else {
            lines.add(
                    Component.translatable(
                            "gui.strife.panel.stage_progress", view.stage(), view.stageCount()));
            lines.add(
                    Component.translatable(
                            "gui.strife.panel.qi_progress", view.qi(), view.qiMax()));
        }

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

        if (view != null) {
            lines.add(
                    Component.translatable(
                            "gui.strife.panel.success_rate",
                            percent(view.successRate()),
                            view.attempts()));
            lines.add(
                    Component.translatable(
                            "gui.strife.panel.failure_cost",
                            percent(view.qiResetRatioMin()),
                            percent(view.qiResetRatioMax())));
            lines.add(Component.translatable("gui.strife.panel.rate", format(view.qiPerSecond())));
            // 05 §2 要求"四项系数可展开"（07 §7 B1-2 的系数分解）：面板把乘数逐项摊开，玩家才可能自己看懂
            // "为什么这里练得慢"，而不是面对一个孤零零的速率数字。
            lines.add(
                    Component.translatable(
                            "gui.strife.panel.factor_breakdown",
                            format(view.spiritrootRatio()),
                            format(view.environmentRatio()),
                            format(view.techniqueRatio()),
                            format(view.pillRatio())));
            lines.add(
                    view.equippedTechnique().isEmpty()
                            ? Component.translatable("gui.strife.panel.technique_none")
                            : Component.translatable(
                                    "gui.strife.panel.technique_equipped",
                                    Component.translatable(
                                            "technique.strife." + view.equippedTechnique())));
            lines.add(stateLine(view));
        } else {
            lines.add(
                    Component.translatable(
                            "gui.strife.panel.breakthrough_attempts", data.breakthroughAttempts()));
        }

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

    private static Component stateLine(StrifeRealmView view) {
        if (view.meditating()) {
            return Component.translatable("gui.strife.panel.state_sitting");
        }
        if (view.cooldownSeconds() > 0) {
            return Component.translatable(
                    "gui.strife.panel.state_cooldown", view.cooldownSeconds());
        }
        if (view.finalRealm()) {
            return Component.translatable("gui.strife.panel.final_realm");
        }
        if (view.canBreakthrough()) {
            return Component.translatable(
                    "gui.strife.panel.can_breakthrough",
                    Component.translatable("key.strife.breakthrough"));
        }
        return Component.translatable("gui.strife.panel.cannot_breakthrough");
    }

    private static String percent(double ratio) {
        return String.format(Locale.ROOT, "%.0f%%", ratio * 100.0);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}

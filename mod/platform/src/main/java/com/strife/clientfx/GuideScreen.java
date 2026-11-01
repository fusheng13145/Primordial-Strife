package com.strife.clientfx;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 《修真入门》面板（P1-21，D12）：五域指引的只读滚动视图——条目从 {@link ClientGuideTable}（jar 产物）直读，与命令通道同源不分叉。
 *
 * <p>交互刻意最小：滚轮/方向键滚动、Esc 关闭。指引是"翻书"，不是操作台。
 */
public final class GuideScreen extends Screen {

    private static final int PANEL_WIDTH = 260;
    private static final int PANEL_HEIGHT = 150;
    private static final int LINE_HEIGHT = 11;
    private static final int TITLE_COLOR = 0xFF9ADCC8;
    private static final int SECTION_COLOR = 0xFFE8C874;
    private static final int TEXT_COLOR = 0xFFE0E0E0;

    private final ClientGuideTable.Entry[] lines =
            ClientGuideTable.load().toArray(new ClientGuideTable.Entry[0]);
    private final java.util.Map<ClientGuideTable.Entry, String> sectionTitles =
            new java.util.HashMap<>();
    private int scroll;

    public GuideScreen() {
        super(Component.literal("修真入门"));
        String lastSection = "";
        for (ClientGuideTable.Entry entry : ClientGuideTable.load()) {
            if (!entry.section().equals(lastSection)) {
                lastSection = entry.section();
            }
            sectionTitles.put(entry, entry.sectionTitle());
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int left = this.width / 2 - PANEL_WIDTH / 2;
        int top = this.height / 2 - PANEL_HEIGHT / 2;
        graphics.fill(
                left - 6, top - 6, left + PANEL_WIDTH + 6, top + PANEL_HEIGHT + 6, 0xF00E1016);
        graphics.fill(left - 6, top - 6, left + PANEL_WIDTH + 6, top - 5, 0xFF2E4A40);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, top - 1, TITLE_COLOR);

        int lineCount = PANEL_HEIGHT / LINE_HEIGHT;
        int maxScroll = Math.max(0, lines.length + sectionTitles.size() - lineCount);
        scroll = Math.min(scroll, maxScroll);

        int y = top + 12;
        int skipped = 0;
        for (ClientGuideTable.Entry entry : lines) {
            if (skipped++ < scroll) {
                continue;
            }
            if (y > top + PANEL_HEIGHT - 8) {
                break;
            }
            if (!sectionTitles.get(entry).equals(headerShown(entry))) {
                // 每域首条前画域标题（简化：域切换即画）。
            }
            graphics.drawString(
                    this.font,
                    "§6" + sectionTitles.get(entry) + " · §f" + entry.title(),
                    left + 2,
                    y,
                    TEXT_COLOR,
                    false);
            for (String wrapped : wrap(entry.text())) {
                y += LINE_HEIGHT;
                if (y > top + PANEL_HEIGHT - 8) {
                    break;
                }
                graphics.drawString(this.font, wrapped, left + 8, y, TEXT_COLOR, false);
            }
            y += LINE_HEIGHT + 3;
        }
        if (maxScroll > 0) {
            graphics.drawCenteredString(
                    this.font,
                    "滚轮翻页 " + (scroll + 1) + "/" + (maxScroll + 1),
                    this.width / 2,
                    top + PANEL_HEIGHT + 10,
                    0xFF9AA5A0);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private String headerShown(ClientGuideTable.Entry entry) {
        return entry.sectionTitle();
    }

    /** 正文按 30 全角宽度近似折行（屏宽 260−16 ≈ 244px ÷ 8px/字 ≈ 30 字）。 */
    private List<String> wrap(String text) {
        List<String> out = new java.util.ArrayList<>();
        for (String segment : text.split("；")) {
            var sb = new StringBuilder();
            for (int i = 0; i < segment.length(); i++) {
                sb.append(segment.charAt(i));
                if (sb.length() >= 30) {
                    out.add(sb.toString());
                    sb.setLength(0);
                }
            }
            if (sb.length() > 0) {
                out.add(sb.toString());
            }
        }
        return out;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY < 0) {
            scroll++;
        } else if (scrollY > 0) {
            scroll = Math.max(0, scroll - 1);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN) {
            scroll++;
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_UP) {
            scroll = Math.max(0, scroll - 1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}

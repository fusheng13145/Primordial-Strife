package com.strife.clientfx;

import com.strife.core.StrifeUiBridge.Keys;
import com.strife.core.net.IntentRateLimiter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * 黑市看板（StrifeUiBridge 第四屏）：暗柜价目 + 顶部因果警示——买按钮发 blackmarket 意图（服务端复判 + 落因果），
 * 本屏只画与转发。暗色主题（灰紫底/幽光字）——地方不对劲，看板也该不对劲。
 */
public final class BlackMarketScreen extends Screen {

    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 216;
    private static final int ROW_HEIGHT = 22;
    private static final int HEADER_HEIGHT = 44;

    private static final int BACKGROUND = 0xF0140F1A;
    private static final int BORDER = 0xFF3A2E4A;
    private static final int TITLE_COLOR = 0xFFB89AE0;
    private static final int TEXT_COLOR = 0xFFD8D0E8;
    private static final int MUTED_COLOR = 0xFF8A85A0;
    private static final int WARN_COLOR = 0xFFC88AE0;
    private static final int OWNED_COLOR = 0xFF9AE0B8;
    private static final int SELECT_COLOR = 0xFF2E264A;

    private final CompoundTag data;
    private final List<PriceRow> rows = new ArrayList<>();
    private String caution;
    private int scroll;
    private int selected = -1;
    private Button buyButton;

    private record PriceRow(String itemId, int price, long owned) {}

    public BlackMarketScreen(CompoundTag data) {
        super(Component.translatable("gui.strife.blackmarket.title"));
        this.data = data == null ? new CompoundTag() : data;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        caution = data.getString(Keys.CAUTION);
        rows.clear();
        for (var element : data.getList(Keys.PRICES, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            if (element instanceof CompoundTag row) {
                rows.add(
                        new PriceRow(
                                row.getString(Keys.ITEM),
                                row.getInt(Keys.BUY),
                                row.getLong(Keys.OWNED)));
            }
        }
        clampScroll();
        int left = this.width / 2 - PANEL_WIDTH / 2;
        int top = this.height / 2 - PANEL_HEIGHT / 2;
        buyButton =
                addRenderableWidget(
                        Button.builder(
                                        Component.translatable("gui.strife.market.buy"),
                                        button -> buySelected())
                                .bounds(left + 12, top + PANEL_HEIGHT - 24, 70, 18)
                                .build());
        addRenderableWidget(
                Button.builder(
                                Component.translatable("gui.strife.alchemy.close"),
                                button -> onClose())
                        .bounds(left + PANEL_WIDTH - 102, top + PANEL_HEIGHT - 24, 90, 18)
                        .build());
    }

    private void buySelected() {
        if (selected < 0 || selected >= rows.size()) {
            return;
        }
        CompoundTag args = new CompoundTag();
        args.putString("item", rows.get(selected).itemId());
        args.putInt("count", 1);
        ClientIntents.send(IntentRateLimiter.BLACKMARKET, args);
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int left = this.width / 2 - PANEL_WIDTH / 2;
        int top = this.height / 2 - PANEL_HEIGHT / 2;
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, BACKGROUND);
        graphics.renderOutline(left, top, PANEL_WIDTH, PANEL_HEIGHT, BORDER);

        graphics.drawString(this.font, this.title, left + 10, top + 6, TITLE_COLOR, false);
        graphics.fill(left + 8, top + 19, left + PANEL_WIDTH - 8, top + 20, BORDER);
        graphics.drawString(
                this.font, caution == null ? "" : caution, left + 10, top + 24, WARN_COLOR, false);
        graphics.drawString(
                this.font,
                Component.translatable("gui.strife.blackmarket.colheader").getString(),
                left + 10,
                top + 36,
                MUTED_COLOR,
                false);

        int listTop = top + HEADER_HEIGHT;
        int listBottom = top + PANEL_HEIGHT - 30;
        graphics.enableScissor(left + 4, listTop, left + PANEL_WIDTH - 4, listBottom);
        for (int i = 0; i < rows.size(); i++) {
            int rowTop = listTop + (i - scroll) * ROW_HEIGHT;
            if (rowTop + ROW_HEIGHT < listTop || rowTop > listBottom) {
                continue;
            }
            PriceRow row = rows.get(i);
            if (i == selected) {
                graphics.fill(
                        left + 4,
                        rowTop,
                        left + PANEL_WIDTH - 4,
                        rowTop + ROW_HEIGHT - 3,
                        SELECT_COLOR);
            }
            String name = Component.translatable("item.strife." + row.itemId()).getString();
            int nameColor = row.owned() > 0 ? OWNED_COLOR : TEXT_COLOR;
            graphics.drawString(this.font, name, left + 10, rowTop + 2, nameColor, false);
            graphics.drawString(
                    this.font,
                    Component.translatable("gui.strife.blackmarket.row", row.price(), row.owned())
                            .getString(),
                    left + 10,
                    rowTop + 13,
                    MUTED_COLOR,
                    false);
        }
        graphics.disableScissor();

        buyButton.active = selected >= 0 && rows.get(selected).owned() >= 0;
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int left = this.width / 2 - PANEL_WIDTH / 2;
        int top = this.height / 2 - PANEL_HEIGHT / 2;
        int listTop = top + HEADER_HEIGHT;
        if (mouseX >= left && mouseX <= left + PANEL_WIDTH && mouseY >= listTop) {
            int index = scroll + (int) ((mouseY - listTop) / ROW_HEIGHT);
            if (index >= 0 && index < rows.size()) {
                selected = index;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll += scrollY > 0 ? -1 : 1;
        clampScroll();
        return true;
    }

    private void clampScroll() {
        int visible = Math.max(1, (PANEL_HEIGHT - HEADER_HEIGHT - 30) / ROW_HEIGHT);
        scroll = Math.max(0, Math.min(scroll, rows.size() - visible));
    }
}

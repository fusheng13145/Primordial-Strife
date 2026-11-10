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
 * 坊市看板（StrifeUiBridge 第二屏）：渲染服务端下发的价目与持有量——买卖决策全在服务端复判（intent → buyFor/sellFor），
 * 本屏只画与转发。每行两个按钮（买1/卖1），交易节奏由 market_per_sec 限速兜底。
 */
public final class MarketBoardScreen extends Screen {

    private static final int PANEL_WIDTH = 310;
    private static final int PANEL_HEIGHT = 226;
    private static final int ROW_HEIGHT = 24;
    private static final int HEADER_HEIGHT = 40;
    private static final int BUTTON_W = 34;

    private static final int BACKGROUND = 0xF00E1016;
    private static final int BORDER = 0xFF4A3A2E;
    private static final int TITLE_COLOR = 0xFFE0B878;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int MUTED_COLOR = 0xFF9AA5A0;
    private static final int OK_COLOR = 0xFF6FBF73;
    private static final int SELECT_COLOR = 0xFF4A3A2E;

    private final CompoundTag data;
    private final List<PriceRow> rows = new ArrayList<>();
    private int exchangeZhong;
    private int exchangeShang;
    private int appraiseFee;
    private int scroll;
    private int selected = -1;
    private Button buyButton;
    private Button sellButton;

    private record PriceRow(String itemId, int buy, int sell, long owned) {}

    public MarketBoardScreen(CompoundTag data) {
        super(Component.translatable("gui.strife.market.title"));
        this.data = data == null ? new CompoundTag() : data;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        exchangeZhong = data.getInt(Keys.EXCHANGE_ZHONG);
        exchangeShang = data.getInt(Keys.EXCHANGE_SHANG);
        appraiseFee = data.getInt(Keys.APPRAISE_FEE);
        rows.clear();
        for (var element : data.getList(Keys.PRICES, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            if (element instanceof CompoundTag row) {
                rows.add(
                        new PriceRow(
                                row.getString(Keys.ITEM),
                                row.getInt(Keys.BUY),
                                row.getInt(Keys.SELL),
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
                                        button -> trade("buy"))
                                .bounds(left + 12, top + PANEL_HEIGHT - 24, 70, 18)
                                .build());
        sellButton =
                addRenderableWidget(
                        Button.builder(
                                        Component.translatable("gui.strife.market.sell"),
                                        button -> trade("sell"))
                                .bounds(left + 88, top + PANEL_HEIGHT - 24, 70, 18)
                                .build());
        addRenderableWidget(
                Button.builder(
                                Component.translatable("gui.strife.alchemy.close"),
                                button -> onClose())
                        .bounds(left + PANEL_WIDTH - 102, top + PANEL_HEIGHT - 24, 90, 18)
                        .build());
    }

    private void trade(String action) {
        if (selected < 0 || selected >= rows.size()) {
            return;
        }
        PriceRow row = rows.get(selected);
        CompoundTag args = new CompoundTag();
        args.putString("action", action);
        args.putString("item", row.itemId());
        args.putInt("count", 1);
        ClientIntents.send(IntentRateLimiter.MARKET, args);
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int left = this.width / 2 - PANEL_WIDTH / 2;
        int top = this.height / 2 - PANEL_HEIGHT / 2;
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, BACKGROUND);
        graphics.renderOutline(left, top, PANEL_WIDTH, PANEL_HEIGHT, BORDER);

        graphics.drawString(this.font, this.title, left + 10, top + 6, TITLE_COLOR, false);
        graphics.drawString(
                this.font,
                Component.translatable(
                                "gui.strife.market.header",
                                exchangeZhong,
                                exchangeShang,
                                appraiseFee)
                        .getString(),
                left + 10,
                top + 20,
                MUTED_COLOR,
                false);
        graphics.drawString(
                this.font,
                Component.translatable("gui.strife.market.colheader").getString(),
                left + 10,
                top + 32,
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
            int nameColor = row.owned() > 0 ? OK_COLOR : TEXT_COLOR;
            String name = Component.translatable("item.strife." + row.itemId()).getString();
            graphics.drawString(this.font, name, left + 10, rowTop + 2, nameColor, false);
            String priceLine = "买 " + row.buy() + " / 卖 " + row.sell() + " · 持有 " + row.owned();
            graphics.drawString(this.font, priceLine, left + 10, rowTop + 13, MUTED_COLOR, false);
        }
        graphics.disableScissor();

        boolean hasSelection = selected >= 0 && selected < rows.size();
        buyButton.active = hasSelection;
        sellButton.active = hasSelection && rows.get(selected).owned() > 0;
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

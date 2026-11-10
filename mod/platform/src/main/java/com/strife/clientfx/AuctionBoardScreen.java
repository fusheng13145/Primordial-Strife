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

/** 拍卖看板（StrifeUiBridge 第三屏）：荷兰拍当前价（时间推导、开屏即算）+ 我的密封投标状态。 购买按钮发 auction 意图（服务端复判一切——本屏只画与转发）。 */
public final class AuctionBoardScreen extends Screen {

    private static final int PANEL_WIDTH = 310;
    private static final int PANEL_HEIGHT = 226;
    private static final int ROW_HEIGHT = 26;
    private static final int HEADER_HEIGHT = 34;

    private static final int BACKGROUND = 0xF00E1016;
    private static final int BORDER = 0xFF2E3A4A;
    private static final int TITLE_COLOR = 0xFF9AC8E0;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int MUTED_COLOR = 0xFF9AA5A0;
    private static final int CURRENT_COLOR = 0xFFE0B878;
    private static final int SELECT_COLOR = 0xFF26364A;
    private static final int WON_COLOR = 0xFF6FBF73;
    private static final int ACTIVE_COLOR = 0xFFE0B878;

    private final CompoundTag data;
    private final List<LotRow> rows = new ArrayList<>();
    private final List<BidRow> myBids = new ArrayList<>();
    private int payouts;
    private int scroll;
    private int selected = -1;
    private Button buyButton;
    private Button bidButton;
    private net.minecraft.client.gui.components.EditBox bidAmount;

    private record LotRow(
            String lotId, String itemId, int price, int min, long remain, boolean current) {}

    private record BidRow(String lotId, int amount, String status) {}

    public AuctionBoardScreen(CompoundTag data) {
        super(Component.translatable("gui.strife.auctionboard.title"));
        this.data = data == null ? new CompoundTag() : data;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        rows.clear();
        for (var element : data.getList(Keys.LOTS, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            if (element instanceof CompoundTag row) {
                rows.add(
                        new LotRow(
                                row.getString(Keys.LOT),
                                row.getString(Keys.ITEM),
                                row.getInt(Keys.PRICE),
                                row.getInt(Keys.MIN),
                                row.getLong(Keys.REMAIN),
                                row.getBoolean(Keys.CURRENT)));
            }
        }
        myBids.clear();
        for (var element : data.getList(Keys.MY_BIDS, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            if (element instanceof CompoundTag row) {
                myBids.add(
                        new BidRow(
                                row.getString(Keys.LOT),
                                row.getInt(Keys.AMOUNT),
                                row.getString(Keys.STATUS)));
            }
        }
        payouts = data.getInt(Keys.PAYOUTS);
        clampScroll();
        int left = this.width / 2 - PANEL_WIDTH / 2;
        int top = this.height / 2 - PANEL_HEIGHT / 2;
        buyButton =
                addRenderableWidget(
                        Button.builder(
                                        Component.translatable("gui.strife.auctionboard.buy"),
                                        button -> buySelected())
                                .bounds(left + 12, top + PANEL_HEIGHT - 24, 90, 18)
                                .build());
        // 密封投标入口（ADR-041 交互闭环）：金额框 + 出价钮——服务端复判底价/灵石/当前槽，客户端只做可用性提示
        bidAmount =
                addRenderableWidget(
                        new net.minecraft.client.gui.components.EditBox(
                                this.font,
                                left + 108,
                                top + PANEL_HEIGHT - 24,
                                64,
                                18,
                                Component.translatable("gui.strife.auctionboard.amount_hint")));
        bidAmount.setMaxLength(9);
        bidAmount.setHint(Component.translatable("gui.strife.auctionboard.amount_hint"));
        bidAmount.setFilter(
                value -> value.chars().allMatch(Character::isDigit) && value.length() <= 9);
        addRenderableWidget(bidAmount);
        bidButton =
                addRenderableWidget(
                        Button.builder(
                                        Component.translatable("gui.strife.auctionboard.bid"),
                                        button -> bidSelected())
                                .bounds(left + 178, top + PANEL_HEIGHT - 24, 56, 18)
                                .build());
        addRenderableWidget(
                Button.builder(
                                Component.translatable("gui.strife.alchemy.close"),
                                button -> onClose())
                        .bounds(left + PANEL_WIDTH - 102, top + PANEL_HEIGHT - 24, 90, 18)
                        .build());
        if (payouts > 0) {
            addRenderableWidget(
                    Button.builder(
                                    Component.translatable(
                                            "gui.strife.auctionboard.claim", payouts),
                                    button -> claim())
                            .bounds(left + PANEL_WIDTH / 2 - 60, top + PANEL_HEIGHT - 24, 120, 18)
                            .build());
        }
    }

    private void buySelected() {
        if (selected < 0 || selected >= rows.size()) {
            return;
        }
        CompoundTag args = new CompoundTag();
        args.putString("action", "buy");
        args.putString("lot", rows.get(selected).lotId());
        ClientIntents.send(IntentRateLimiter.AUCTION, args);
        onClose();
    }

    /** 密封投标：金额框取数 → 意图（服务端复判；非法/空金额按钮本就不可用）。 */
    private void bidSelected() {
        if (selected < 0 || selected >= rows.size()) {
            return;
        }
        String raw = bidAmount.getValue().trim();
        if (raw.isEmpty() || !raw.chars().allMatch(Character::isDigit)) {
            return;
        }
        CompoundTag args = new CompoundTag();
        args.putString("action", "bid");
        args.putString("lot", rows.get(selected).lotId());
        args.putInt("amount", Integer.parseInt(raw));
        ClientIntents.send(IntentRateLimiter.AUCTION, args);
        onClose();
    }

    private void claim() {
        CompoundTag args = new CompoundTag();
        args.putString("action", "claim");
        ClientIntents.send(IntentRateLimiter.AUCTION, args);
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
                Component.translatable("gui.strife.auctionboard.header").getString(),
                left + 10,
                top + 20,
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
            LotRow row = rows.get(i);
            if (i == selected) {
                graphics.fill(
                        left + 4,
                        rowTop,
                        left + PANEL_WIDTH - 4,
                        rowTop + ROW_HEIGHT - 3,
                        SELECT_COLOR);
            }
            int color = row.current() ? CURRENT_COLOR : MUTED_COLOR;
            String marker = row.current() ? "▶ " : "  ";
            String name = Component.translatable("item.strife." + row.itemId()).getString();
            graphics.drawString(
                    this.font,
                    marker + name + "（底 " + row.min() + "）",
                    left + 10,
                    rowTop + 2,
                    color,
                    false);
            String priceLine =
                    Component.translatable("gui.strife.auctionboard.row", row.price(), row.remain())
                            .getString();
            graphics.drawString(this.font, priceLine, left + 10, rowTop + 14, TEXT_COLOR, false);
        }
        graphics.disableScissor();

        if (!myBids.isEmpty()) {
            BidRow last = myBids.get(myBids.size() - 1);
            int statusColor =
                    "won".equals(last.status())
                            ? WON_COLOR
                            : "active".equals(last.status()) ? ACTIVE_COLOR : MUTED_COLOR;
            graphics.drawString(
                    this.font,
                    Component.translatable(
                                    "gui.strife.auctionboard.mybid",
                                    myBids.size(),
                                    last.lotId(),
                                    last.amount(),
                                    last.status())
                            .getString(),
                    left + 10,
                    listBottom + 2,
                    statusColor,
                    false);
        }

        boolean currentSelected = selected >= 0 && rows.get(selected).current();
        buyButton.active = currentSelected;
        boolean numeric =
                bidAmount != null
                        && !bidAmount.getValue().isBlank()
                        && bidAmount.getValue().chars().allMatch(Character::isDigit);
        bidButton.active = currentSelected && numeric;
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

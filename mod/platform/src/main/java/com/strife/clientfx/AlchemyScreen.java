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
 * 炉交互屏（ADR-038 交互层）：渲染服务端下发的配方清单——材料够不够、成功率多少全是服务端判好的结果， 本屏只画与转发（03 §4"客户端只是显示器"）。
 *
 * <p>交互：滚轮滚列表 / 点行选中 / 底部"炼制"按钮发 {@code craft_pill} 意图（服务端重新校验一切并沿用 /strife craft pill 的同一条 {@code
 * Alchemy.craft} 路径，反馈走聊天栏）。屏上不显示火候区间——heat 玩法未实装，不假装。
 */
public final class AlchemyScreen extends Screen {

    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 220;
    private static final int ROW_HEIGHT = 40;
    private static final int HEADER_HEIGHT = 40;

    private static final int BACKGROUND = 0xF00E1016;
    private static final int BORDER = 0xFF2E4A40;
    private static final int TITLE_COLOR = 0xFF9ADCC8;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int MUTED_COLOR = 0xFF9AA5A0;
    private static final int OK_COLOR = 0xFF6FBF73;
    private static final int WARN_COLOR = 0xFFE06C5A;
    private static final int SELECT_COLOR = 0xFF3A5A4A;

    private final CompoundTag data;
    private final List<RecipeRow> rows = new ArrayList<>();
    private String furnace = "";
    private String fire = "";
    private String fireEffects = "";
    private int scroll;
    private int selected = -1;
    private Button craftButton;

    private record MaterialLine(String text, boolean enough) {}

    private record RecipeRow(
            String pillId,
            String name,
            String outputs,
            List<MaterialLine> materials,
            boolean craftable) {}

    public AlchemyScreen(CompoundTag data) {
        super(Component.translatable("gui.strife.alchemy.title"));
        this.data = data == null ? new CompoundTag() : data;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        parse();
        int left = this.width / 2 - PANEL_WIDTH / 2;
        int top = this.height / 2 - PANEL_HEIGHT / 2;
        craftButton =
                addRenderableWidget(
                        Button.builder(
                                        Component.translatable("gui.strife.alchemy.craft"),
                                        button -> craftSelected())
                                .bounds(left + 12, top + PANEL_HEIGHT - 24, 90, 18)
                                .build());
        addRenderableWidget(
                Button.builder(
                                Component.translatable("gui.strife.alchemy.close"),
                                button -> onClose())
                        .bounds(left + PANEL_WIDTH - 102, top + PANEL_HEIGHT - 24, 90, 18)
                        .build());
    }

    private void parse() {
        furnace = data.getString(Keys.FURNACE);
        fire = data.getString(Keys.FIRE);
        fireEffects = data.getString(Keys.FIRE_EFFECTS);
        rows.clear();
        for (var element : data.getList(Keys.RECIPES, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            if (!(element instanceof CompoundTag row)) {
                continue;
            }
            String pillId = row.getString(Keys.PILL);
            List<MaterialLine> materials = new ArrayList<>();
            for (var m : row.getList(Keys.MATERIALS, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                if (!(m instanceof CompoundTag mt)) {
                    continue;
                }
                String itemId = mt.getString(Keys.ITEM);
                long have = mt.getLong(Keys.HAVE);
                int need = mt.getInt(Keys.NEED);
                materials.add(
                        new MaterialLine(
                                Component.translatable("item.strife." + itemId).getString()
                                        + " "
                                        + have
                                        + "/"
                                        + need,
                                have >= need));
            }
            StringBuilder outputs = new StringBuilder();
            for (var o : row.getList(Keys.OUTPUTS, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                if (!(o instanceof CompoundTag ot)) {
                    continue;
                }
                if (outputs.length() > 0) {
                    outputs.append(" / ");
                }
                outputs.append(
                        Component.translatable("item.strife." + ot.getString(Keys.ITEM))
                                .getString());
                int count = ot.getInt(Keys.COUNT);
                if (count > 1) {
                    outputs.append("×").append(count);
                }
                outputs.append(" ")
                        .append(String.format("%.0f", ot.getDouble(Keys.PROB) * 100))
                        .append("%");
            }
            rows.add(
                    new RecipeRow(
                            pillId,
                            Component.translatable("item.strife." + pillId).getString(),
                            outputs.toString(),
                            materials,
                            row.getBoolean(Keys.CRAFTABLE)));
        }
        clampScroll();
    }

    private void craftSelected() {
        if (selected < 0 || selected >= rows.size()) {
            return;
        }
        RecipeRow row = rows.get(selected);
        CompoundTag args = new CompoundTag();
        args.putString(Keys.PILL, row.pillId());
        ClientIntents.send(IntentRateLimiter.CRAFT, args);
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int left = this.width / 2 - PANEL_WIDTH / 2;
        int top = this.height / 2 - PANEL_HEIGHT / 2;
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, BACKGROUND);
        graphics.renderOutline(left, top, PANEL_WIDTH, PANEL_HEIGHT, BORDER);

        // 头部：炉 + 异火状态（服务端判好的文字，客户端零业务）
        graphics.drawString(this.font, this.title, left + 10, top + 6, TITLE_COLOR, false);
        String header = furnace.isEmpty() ? "无炉加成" : furnace;
        graphics.drawString(this.font, header, left + 10, top + 18, MUTED_COLOR, false);
        String fireLine =
                fire.isEmpty()
                        ? "凡火（无异火）"
                        : fire + (fireEffects.isEmpty() ? "" : " · " + fireEffects);
        graphics.drawString(this.font, fireLine, left + 10, top + 29, MUTED_COLOR, false);

        // 列表（滚动裁剪：手动按可视范围画）
        int listTop = top + HEADER_HEIGHT;
        int listBottom = top + PANEL_HEIGHT - 30;
        graphics.enableScissor(left + 4, listTop, left + PANEL_WIDTH - 4, listBottom);
        for (int i = 0; i < rows.size(); i++) {
            int rowTop = listTop + (i - scroll) * ROW_HEIGHT;
            if (rowTop + ROW_HEIGHT < listTop || rowTop > listBottom) {
                continue;
            }
            RecipeRow row = rows.get(i);
            if (i == selected) {
                graphics.fill(
                        left + 4,
                        rowTop,
                        left + PANEL_WIDTH - 4,
                        rowTop + ROW_HEIGHT - 4,
                        SELECT_COLOR);
            }
            int lineColor = row.craftable() ? OK_COLOR : WARN_COLOR;
            graphics.drawString(this.font, row.name(), left + 10, rowTop + 2, lineColor, false);
            int x = left + 10;
            int line = rowTop + 14;
            for (MaterialLine material : row.materials()) {
                String text = material.text();
                graphics.drawString(
                        this.font,
                        text,
                        x,
                        line,
                        material.enough() ? MUTED_COLOR : WARN_COLOR,
                        false);
                x += this.font.width(text) + 8;
            }
            graphics.drawString(
                    this.font, "出 " + row.outputs(), left + 10, rowTop + 25, TEXT_COLOR, false);
        }
        graphics.disableScissor();

        craftButton.active = selected >= 0 && rows.get(selected).craftable();
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

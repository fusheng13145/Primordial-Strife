package com.strife.clientfx;

import com.strife.core.net.IntentRateLimiter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** 法宝面板：独立 GUI 槽位展示副手所持法宝，激发只发 {@code artifact} 意图（判定全在服务端）。 */
public final class ArtifactScreen extends Screen {

    private static final int PANEL_WIDTH = 232;
    private static final int PANEL_HEIGHT = 156;
    private static final int SLOT_SIZE = 20;

    private static final int BACKGROUND = 0xF00E1016;
    private static final int BORDER = 0xFF2E4A40;
    private static final int TITLE_COLOR = 0xFF9ADCC8;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int MUTED_COLOR = 0xFF9AA5A0;
    private static final int WARN_COLOR = 0xFFE06C5A;
    private static final int BAR_BACKGROUND = 0xFF2A2E36;
    private static final int BAR_FILL = 0xFF6FBF73;

    private ArtifactPanelModel model = ArtifactPanelModel.fromTags(null);
    private ItemStack stack = ItemStack.EMPTY;
    private Button castButton;

    public ArtifactScreen() {
        super(Component.translatable("gui.strife.artifact.title"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            stack = minecraft.player.getOffhandItem();
            CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
            CompoundTag tag = custom == null ? null : custom.copyTag();
            model = ArtifactPanelModel.fromTags(tag);
        }
        int left = this.width / 2 - PANEL_WIDTH / 2;
        int top = this.height / 2 - PANEL_HEIGHT / 2;
        castButton =
                addRenderableWidget(
                        Button.builder(
                                        Component.translatable("gui.strife.artifact.cast"),
                                        button -> cast())
                                .bounds(left + PANEL_WIDTH / 2 - 40, top + 118, 80, 20)
                                .build());
        castButton.active = model.present() && !model.broken() && !model.activeSpell().isEmpty();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x66000000);
        int left = this.width / 2 - PANEL_WIDTH / 2;
        int top = this.height / 2 - PANEL_HEIGHT / 2;
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, BACKGROUND);
        graphics.renderOutline(left, top, PANEL_WIDTH, PANEL_HEIGHT, BORDER);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, top + 10, TITLE_COLOR);

        int slotLeft = left + 16;
        int slotTop = top + 28;
        graphics.fill(slotLeft, slotTop, slotLeft + SLOT_SIZE, slotTop + SLOT_SIZE, BAR_BACKGROUND);
        graphics.renderOutline(slotLeft, slotTop, SLOT_SIZE, SLOT_SIZE, BORDER);
        graphics.drawString(
                this.font,
                Component.translatable("gui.strife.artifact.slot"),
                slotLeft + SLOT_SIZE + 8,
                slotTop + 2,
                MUTED_COLOR);

        if (!model.present()) {
            graphics.drawString(
                    this.font,
                    Component.translatable("gui.strife.artifact.empty"),
                    slotLeft,
                    slotTop + SLOT_SIZE + 14,
                    WARN_COLOR);
        } else {
            graphics.renderItem(stack, slotLeft + 2, slotTop + 2);
            renderDetails(graphics, slotLeft + SLOT_SIZE + 8, slotTop + 14);
        }
        graphics.drawCenteredString(
                this.font,
                Component.translatable("gui.strife.artifact.hint"),
                this.width / 2,
                top + PANEL_HEIGHT - 12,
                MUTED_COLOR);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderDetails(GuiGraphics graphics, int x, int y) {
        graphics.drawString(
                this.font,
                Component.translatable("artifact.strife." + model.artifactId()),
                x,
                y,
                TEXT_COLOR);
        graphics.drawString(
                this.font,
                Component.translatable("quality.strife." + model.quality()),
                x,
                y + 12,
                MUTED_COLOR);
        if (model.activeSpell().isEmpty()) {
            graphics.drawString(
                    this.font,
                    Component.translatable("gui.strife.artifact.no_active"),
                    x,
                    y + 26,
                    MUTED_COLOR);
        } else {
            graphics.drawString(
                    this.font,
                    Component.translatable(
                            "gui.strife.artifact.active",
                            Component.translatable("spell.strife." + model.activeSpell())),
                    x,
                    y + 26,
                    MUTED_COLOR);
        }

        int barLeft = x;
        int barTop = y + 46;
        int barWidth = PANEL_WIDTH - (x - (this.width / 2 - PANEL_WIDTH / 2)) - 16;
        graphics.fill(barLeft, barTop, barLeft + barWidth, barTop + 6, BAR_BACKGROUND);
        int filled = (int) Math.round(barWidth * model.durabilityRatio());
        if (filled > 0) {
            graphics.fill(barLeft, barTop, barLeft + filled, barTop + 6, BAR_FILL);
        }
        Component durability =
                model.legacy()
                        ? Component.translatable("gui.strife.artifact.legacy")
                        : Component.translatable(
                                "gui.strife.artifact.durability",
                                model.durability(),
                                model.maxDurability());
        int color = model.broken() ? WARN_COLOR : MUTED_COLOR;
        graphics.drawString(this.font, durability, barLeft, barTop + 10, color);
        if (model.broken()) {
            graphics.drawString(
                    this.font,
                    Component.translatable("gui.strife.artifact.broken"),
                    barLeft,
                    barTop + 22,
                    WARN_COLOR);
        }
    }

    private void cast() {
        CompoundTag args = new CompoundTag();
        args.putString("action", "cast");
        ClientIntents.send(IntentRateLimiter.ARTIFACT, args);
        onClose();
    }
}

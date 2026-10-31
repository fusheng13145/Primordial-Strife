package com.strife.clientfx;

import com.strife.core.StrifeData;
import com.strife.core.net.IntentRateLimiter;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 施法轮盘：四向卡片展示随 jar 的法术清单，点击只发 {@code cast} 意图。
 *
 * <p>解锁、灵气、冷却与功法要求全由服务端判定；客户端只把"我想放哪个"送出去，并提前把冷却/灵气不足标灰—— 标灰是提示，不是放行条件。
 */
public final class SpellWheelScreen extends Screen {

    private static final Logger LOGGER = LoggerFactory.getLogger("strife/clientfx");

    private static final int CARD_WIDTH = 104;
    private static final int CARD_HEIGHT = 36;
    private static final int HUB_WIDTH = 64;
    private static final int HUB_HEIGHT = 40;
    private static final int MIN_RADIUS = 88;

    private static final int BACKGROUND = 0xF00E1016;
    private static final int BORDER = 0xFF2E4A40;
    private static final int BORDER_HOVER = 0xFF9ADCC8;
    private static final int TITLE_COLOR = 0xFF9ADCC8;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int MUTED_COLOR = 0xFF9AA5A0;
    private static final int WARN_COLOR = 0xFFE06C5A;

    private final List<SpellClientTable.Entry> spells;
    private final boolean loadFailed;
    private List<SpellWheelLayout.Card> cards = List.of();
    private int hovered = -1;

    public SpellWheelScreen() {
        super(Component.translatable("gui.strife.spells.title"));
        List<SpellClientTable.Entry> loaded = List.of();
        boolean failed = false;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            try {
                loaded = SpellClientTable.load(minecraft.getResourceManager());
            } catch (RuntimeException e) {
                failed = true;
                LOGGER.warn("cannot load spell wheel: {}", e.getMessage());
            }
        }
        this.spells = loaded;
        this.loadFailed = failed;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        int radius = Math.max(MIN_RADIUS, Math.min(100, Math.min(this.width, this.height) / 4));
        this.cards =
                SpellWheelLayout.cards(
                        spells.size(),
                        this.width / 2,
                        this.height / 2,
                        CARD_WIDTH,
                        CARD_HEIGHT,
                        radius);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x66000000);
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        hovered = SpellWheelLayout.hitTest(cards, mouseX, mouseY);

        renderHub(graphics, centerX, centerY);
        if (spells.isEmpty()) {
            graphics.drawCenteredString(
                    this.font,
                    Component.translatable(
                            loadFailed ? "gui.strife.spells.no_data" : "gui.strife.spells.empty"),
                    centerX,
                    centerY + HUB_HEIGHT / 2 + 16,
                    MUTED_COLOR);
        } else {
            renderCards(graphics);
        }
        graphics.drawCenteredString(
                this.font,
                Component.translatable("gui.strife.spells.hint"),
                centerX,
                this.height - 24,
                MUTED_COLOR);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderHub(GuiGraphics graphics, int centerX, int centerY) {
        graphics.fill(
                centerX - HUB_WIDTH / 2,
                centerY - HUB_HEIGHT / 2,
                centerX + HUB_WIDTH / 2,
                centerY + HUB_HEIGHT / 2,
                BACKGROUND);
        graphics.renderOutline(
                centerX - HUB_WIDTH / 2, centerY - HUB_HEIGHT / 2, HUB_WIDTH, HUB_HEIGHT, BORDER);
        graphics.drawCenteredString(this.font, this.title, centerX, centerY - 12, TITLE_COLOR);
        StrifeData data = ClientDataBridge.mirror();
        if (data != null) {
            graphics.drawCenteredString(
                    this.font,
                    Component.translatable("gui.strife.spells.qi", data.qi()),
                    centerX,
                    centerY + 3,
                    MUTED_COLOR);
        }
    }

    private void renderCards(GuiGraphics graphics) {
        StrifeData data = ClientDataBridge.mirror();
        long now =
                this.minecraft != null && this.minecraft.level != null
                        ? this.minecraft.level.getGameTime()
                        : 0L;
        for (SpellWheelLayout.Card card : cards) {
            SpellClientTable.Entry entry = spells.get(card.index());
            int border = card.index() == hovered ? BORDER_HOVER : BORDER;
            graphics.fill(
                    card.left(),
                    card.top(),
                    card.left() + card.width(),
                    card.top() + card.height(),
                    BACKGROUND);
            graphics.fill(
                    card.left(),
                    card.top(),
                    card.left() + 4,
                    card.top() + card.height(),
                    elementColor(entry.element()));
            graphics.renderOutline(card.left(), card.top(), card.width(), card.height(), border);
            graphics.drawString(
                    this.font,
                    Component.translatable("spell.strife." + entry.id()),
                    card.left() + 12,
                    card.top() + 6,
                    TEXT_COLOR);

            long remaining = remainingTicks(data, entry, now);
            Component detail;
            int detailColor = MUTED_COLOR;
            if (remaining > 0L) {
                detail =
                        Component.translatable(
                                "gui.strife.spells.detail_cooldown",
                                elementName(entry.element()),
                                (remaining + 19L) / 20L);
            } else {
                detail =
                        Component.translatable(
                                "gui.strife.spells.detail",
                                elementName(entry.element()),
                                entry.costQi());
                if (data != null && data.qi() < entry.costQi()) {
                    detailColor = WARN_COLOR;
                }
            }
            graphics.drawString(this.font, detail, card.left() + 12, card.top() + 21, detailColor);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int hit = SpellWheelLayout.hitTest(cards, mouseX, mouseY);
            if (hit >= 0 && hit < spells.size()) {
                cast(spells.get(hit));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void cast(SpellClientTable.Entry entry) {
        CompoundTag args = new CompoundTag();
        args.putString("spell", entry.id());
        ClientIntents.send(IntentRateLimiter.CAST, args);
        onClose();
    }

    private static long remainingTicks(
            StrifeData data, SpellClientTable.Entry entry, long nowTick) {
        if (data == null) {
            return 0L;
        }
        Long until = data.spells().cooldowns().get(entry.id());
        return until == null ? 0L : Math.max(0L, until - nowTick);
    }

    private static Component elementName(String element) {
        return Component.translatable("element.strife." + element);
    }

    private static int elementColor(String element) {
        return switch (element) {
            case "jin" -> 0xFFE8C46A;
            case "mu" -> 0xFF6FBF73;
            case "shui" -> 0xFF6FA8DC;
            case "huo" -> 0xFFE06C5A;
            case "tu" -> 0xFFB08968;
            default -> 0xFF9ADCC8;
        };
    }
}

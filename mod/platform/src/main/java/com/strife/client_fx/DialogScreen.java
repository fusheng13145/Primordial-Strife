package com.strife.client_fx;

import com.strife.core.net.DialogClientMirror;
import com.strife.core.net.DialogPayloads;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * 对话界面（docs/07 §7 M3"对话 UI"）：说话人 + 正文 + 可见选项按钮。
 *
 * <p>显示的数据全部来自 {@link DialogClientMirror}（服务端已求值的成品文本，03 §4 服务端权威）——本屏没有树结构、
 * 不知道被过滤的选项，改内存也改不出"看不见的选项"。选择经 C2S 包回服务端重放；推进时服务端再推新镜像， {@link #tick()} 轮询版本号重建按钮。
 *
 * <p>Esc/完成按钮触发 {@link #onClose()} → 发 {@code chooseIndex=-1} 清服务端会话（terminated 镜像除外——
 * 那是服务端先清的，回包是无人接收的空操作）。
 */
public final class DialogScreen extends Screen {

    private static final int OPTION_HEIGHT = 20;
    private static final int OPTION_GAP = 4;

    private long lastRevision = -1L;
    private DialogPayloads.Open payload;
    private List<net.minecraft.util.FormattedCharSequence> wrappedText = List.of();
    private final List<Button> optionButtons = new ArrayList<>();

    public DialogScreen(long revision) {
        super(Component.literal("对话"));
        this.lastRevision = revision;
        this.payload = DialogClientMirror.current();
    }

    @Override
    protected void init() {
        rebuild();
    }

    /** 每 tick 轮询镜像版本：服务端推进/重发都会 +1，变了就重建（与面板轮询 StrifeClientMirror 同款）。 */
    @Override
    public void tick() {
        DialogPayloads.Open current = DialogClientMirror.current();
        if (current == null || current.terminated()) {
            // 服务端已收尾（对话结束/会话丢失）：静默关 UI，不再回包
            this.payload = null;
            this.minecraft.setScreen(null);
            return;
        }
        if (DialogClientMirror.revision() != lastRevision) {
            lastRevision = DialogClientMirror.revision();
            this.payload = current;
            this.clearWidgets();
            this.optionButtons.clear();
            rebuild();
        }
    }

    private void rebuild() {
        if (payload == null) {
            return;
        }
        int panelWidth = Math.min(360, this.width - 40);
        int left = (this.width - panelWidth) / 2;
        this.wrappedText =
                List.copyOf(this.font.split(Component.literal(payload.text()), panelWidth - 16));

        List<String> options = payload.optionTexts();
        int optionsHeight = options.size() * (OPTION_HEIGHT + OPTION_GAP) - OPTION_GAP;
        int optionTop = this.height - 28 - optionsHeight;
        for (int index = 0; index < options.size(); index++) {
            final int chosen = index;
            Button button =
                    Button.builder(
                                    Component.literal(options.get(index)),
                                    b -> {
                                        // 点击即上送；UI 刷新等服务端镜像（乐观置灰防双击重发）
                                        b.active = false;
                                        net.neoforged.neoforge.network.PacketDistributor
                                                .sendToServer(new DialogPayloads.Choose(chosen));
                                    })
                            .bounds(
                                    left + 8,
                                    optionTop + index * (OPTION_HEIGHT + OPTION_GAP),
                                    panelWidth - 16,
                                    OPTION_HEIGHT)
                            .build();
            this.optionButtons.add(button);
            addRenderableWidget(button);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 半透明底：正文可读优先，背景继续渲染（对话时世界仍在眼前）
        graphics.fillGradient(0, 0, this.width, this.height, 0xB0_10_08_08, 0xE0_10_08_08);
        super.render(graphics, mouseX, mouseY, partialTick);

        if (payload == null) {
            return;
        }
        int panelWidth = Math.min(360, this.width - 40);
        int left = (this.width - panelWidth) / 2;
        int y = 28;
        if (!payload.speaker().isEmpty()) {
            MutableComponent speaker =
                    Component.literal(payload.speaker())
                            .withStyle(net.minecraft.ChatFormatting.GOLD);
            graphics.drawCenteredString(this.font, speaker, this.width / 2, y, 0xFFD700);
            y += 14;
        }
        for (net.minecraft.util.FormattedCharSequence line : wrappedText) {
            graphics.drawString(this.font, line, left + 8, y, 0xFFFFFF, true);
            y += 11;
        }
    }

    @Override
    public void onClose() {
        // 玩家主动关闭（Esc）：通知服务端清会话。terminated 推送导致的关闭走 tick() 的 setScreen(null)，
        // 不经这里，所以这里总是"玩家动作"语义。
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(DialogPayloads.Choose.CLOSE);
        DialogClientMirror.clear();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        // 单人打开对话不暂停世界（多人与专用服本来就不暂停；统一行为）。
        return false;
    }
}

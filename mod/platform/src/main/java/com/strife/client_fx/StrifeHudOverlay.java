package com.strife.client_fx;

import com.strife.core.StrifeAttachmentTypes;
import com.strife.core.StrifeData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 修仙 HUD（docs/07 §7 B0 面板空壳的第一块可见物）：屏幕左下角常驻境界 / 修为 / 寿元 / 灵根。
 *
 * <p>数据纪律（docs/03 服务端权威）：客户端附件镜像要等 M1 的 A1-5 S2C 同步框架，因此这里在 <b>单机</b>下直接读 integrated server
 * 持有的权威附件——渲染线程读 int/long 字段对显示足够； 读不到权威镜像（专用服、未同步）就不画，<b>绝不显示编造的数值</b>。同步上线后本类的读取路径 整体换成客户端镜像。
 */
public final class StrifeHudOverlay {

    /**
     * 显示层临时映射：ordinal → 境界内容 ID，顺序即 NUMBERS §1 链序，仅用于把序号拼成 lang key。 待接入（M1）：同步框架由服务端下发 id
     * 后删除此表——链序的真相只允许存在于 NUMBERS。
     */
    private static final List<String> REALM_CHAIN =
            List.of(
                    "fanren",
                    "qili",
                    "zhuji",
                    "jindan",
                    "yuanying",
                    "huashen",
                    "lianxu",
                    "heti",
                    "dujie");

    /** 五行位掩码：bit0 金 bit1 木 bit2 水 bit3 火 bit4 土（StrifeData 契约口径）。 */
    private static final String[] ELEMENT_KEYS = {"jin", "mu", "shui", "huo", "tu"};

    private static final int COLOR = 0xFFFFFFFF;

    private StrifeHudOverlay() {}

    static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer client = minecraft.player;
        if (client == null) {
            return;
        }
        StrifeData data = authoritative(minecraft, client);
        if (data == null) {
            return;
        }
        Component cultivation =
                Component.empty()
                        .append(Component.translatable("gui.strife.hud.realm", realmName(data)))
                        .append(" · ")
                        .append(Component.translatable("gui.strife.hud.qi", data.qi()));
        Component condition =
                Component.empty()
                        .append(
                                Component.translatable(
                                        "gui.strife.hud.lifespan",
                                        data.lifespanTicks() / StrifeData.TICKS_PER_YEAR))
                        .append(" · ")
                        .append(
                                Component.translatable(
                                        "gui.strife.hud.spiritroot", spiritroot(data)));
        int x = 4;
        int y = graphics.guiHeight() - 44;
        graphics.drawString(minecraft.font, cultivation, x, y, COLOR);
        graphics.drawString(minecraft.font, condition, x, y + 10, COLOR);
    }

    /** 单机下的权威读取：integrated server 的玩家实体持有序列化与服务端结算的唯一真相。 没有它（专用服 / 同步未接）返回 null，HUD 静默不画。 */
    private static StrifeData authoritative(Minecraft minecraft, LocalPlayer client) {
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            return null;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(client.getUUID());
        return player == null ? null : player.getData(StrifeAttachmentTypes.PLAYER_DATA);
    }

    /** ordinal 越界时退回序号显示，绝不因此崩 HUD。 */
    private static Component realmName(StrifeData data) {
        if (data.realmOrdinal() < 0 || data.realmOrdinal() >= REALM_CHAIN.size()) {
            return Component.literal("#" + data.realmOrdinal());
        }
        return Component.translatable("realm.strife." + REALM_CHAIN.get(data.realmOrdinal()));
    }

    /** 五行列表 + 品阶；灵根未生成（服务端 A1-1 逻辑未落地）显示“未生成”。 */
    private static Component spiritroot(StrifeData data) {
        if (data.spiritrootQuality() <= 0 || data.spiritrootElements() == 0) {
            return Component.translatable("gui.strife.hud.none");
        }
        List<Component> elements = new ArrayList<>();
        for (int bit = 0; bit < ELEMENT_KEYS.length; bit++) {
            if ((data.spiritrootElements() & (1 << bit)) != 0) {
                elements.add(Component.translatable("element.strife." + ELEMENT_KEYS[bit]));
            }
        }
        MutableComponent names = Component.empty();
        for (int i = 0; i < elements.size(); i++) {
            if (i > 0) {
                names = names.append("/");
            }
            names = names.append(elements.get(i));
        }
        if (data.spiritrootQuality() > 0) {
            names =
                    names.append(" · ")
                            .append(
                                    Component.translatable(
                                            "gui.strife.hud.quality", data.spiritrootQuality()));
        }
        return names;
    }
}

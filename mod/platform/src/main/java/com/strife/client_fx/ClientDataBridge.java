package com.strife.client_fx;

import com.strife.core.StrifeAttachmentTypes;
import com.strife.core.StrifeData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * client_fx 的服务端权威数据桥：HUD 与面板共用的一条读取路径。
 *
 * <p>数据纪律（docs/03 服务端权威）：客户端附件镜像要等 M1 的 A1-5 S2C 同步框架，因此在 <b>单机</b>下直接读 integrated server
 * 持有的权威附件——渲染线程读 int/long 字段对显示足够； 读不到权威镜像（专用服、未同步）返回 null，调用方静默降级，<b>绝不显示编造的数值</b>。
 * 同步上线后本类整体换成客户端镜像读取。
 */
public final class ClientDataBridge {

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

    private ClientDataBridge() {}

    /** 单机下的权威读取：integrated server 的玩家实体持有序列化与服务端结算的唯一真相。 没有它（专用服 / 同步未接）返回 null。 */
    public static StrifeData authoritative(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null) {
            return null;
        }
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            return null;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(minecraft.player.getUUID());
        return player == null ? null : player.getData(StrifeAttachmentTypes.PLAYER_DATA);
    }

    /** ordinal 越界时退回序号显示，绝不因此崩 UI。 */
    public static Component realmName(StrifeData data) {
        if (data.realmOrdinal() < 0 || data.realmOrdinal() >= REALM_CHAIN.size()) {
            return Component.literal("#" + data.realmOrdinal());
        }
        return Component.translatable("realm.strife." + REALM_CHAIN.get(data.realmOrdinal()));
    }

    /** 五行列表 + 品阶；灵根未生成（服务端 A1-1 逻辑未落地）显示"未生成"。 */
    public static Component spiritroot(StrifeData data) {
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

    /** 空串 = 散修（StrifeData 契约口径）。 */
    public static Component affiliation(StrifeData data) {
        if (data.affiliation() == null || data.affiliation().isBlank()) {
            return Component.translatable("gui.strife.panel.affiliation.none");
        }
        return Component.translatable("faction.strife." + data.affiliation());
    }

    /** H2 声望向量逐势力一行；空向量显示"无记录"。 */
    public static List<Component> reputationLines(StrifeData data) {
        List<Component> lines = new ArrayList<>();
        data.reputation()
                .forEach(
                        (faction, value) ->
                                lines.add(
                                        Component.translatable(
                                                "gui.strife.panel.reputation",
                                                Component.translatable("faction.strife." + faction),
                                                value)));
        return lines;
    }
}

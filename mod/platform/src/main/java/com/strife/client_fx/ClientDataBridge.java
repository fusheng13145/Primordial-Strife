package com.strife.client_fx;

import com.strife.core.StrifeData;
import com.strife.core.content.StrifeOrdinalIndex;
import com.strife.core.net.StrifeClientMirror;
import com.strife.core.net.StrifeCoreRules;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * client_fx 的服务端权威数据桥：HUD 与面板共用的一条读取路径。
 *
 * <p>数据来源是 {@link StrifeClientMirror}——服务端经 S2C 推送的镜像（docs/03 §5）。客户端既不读本地附件，也不读 integrated
 * server，因此<b>专用服与单机走的是同一条路径</b>，面板在两种环境下的表现一致（此前"只在单机可见"的做法已随 A1-5 落地移除）。
 *
 * <p>读不到镜像（还没收到快照）时返回 null，调用方静默降级为"暂无数据"：<b>绝不显示编造的数值</b>（03 §4 服务端权威的显示侧纪律）。
 *
 * <p>境界名与寿元年数都由内容层还原，不在客户端留第二份真相：序号经 {@link StrifeOrdinalIndex} 查产物得 ID，换算率来自 NUMBERS §4 的 DataGen
 * 产物。
 */
public final class ClientDataBridge {

    /** 境界域（DataGen 产物目录名，JSON_SCHEMA §4.12）。 */
    private static final String REALM_DOMAIN = "strife_realms";

    /** 五行位掩码：bit0 金 bit1 木 bit2 水 bit3 火 bit4 土（StrifeData 契约口径）。 */
    private static final String[] ELEMENT_KEYS = {"jin", "mu", "shui", "huo", "tu"};

    /** 客户端侧的换算率缓存（-1 = 未读到；资源管理器就绪后读一次即可）。 */
    private static long ticksPerYear = -1L;

    private ClientDataBridge() {}

    /** 服务端权威镜像；未同步时 null。 */
    public static StrifeData mirror() {
        return StrifeClientMirror.getOrNull();
    }

    /**
     * 1 修行年 = 多少刻。客户端读自己的产物副本（与服务端同一份 jar 内容），读不到时返回 -1 让调用方降级—— 绝不退回一个写死的 24000，那会在 NUMBERS
     * 调整时长曲线后静默显示错的年数。
     */
    public static long ticksPerYear() {
        if (ticksPerYear > 0L) {
            return ticksPerYear;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return -1L;
        }
        try {
            ticksPerYear = StrifeCoreRules.of(minecraft.getResourceManager()).ticksPerYear();
        } catch (RuntimeException e) {
            return -1L;
        }
        return ticksPerYear;
    }

    /** 寿元刻数 → 年；换算率不可用时返回 -1（调用方显示"未知"而不是编一个数）。 */
    public static long lifespanYears(StrifeData data) {
        long perYear = ticksPerYear();
        return perYear <= 0L ? -1L : data.lifespanTicks() / perYear;
    }

    /**
     * 境界名：ordinal 经内容索引还原成内容 ID 再拼 lang key。链序的真相只在 NUMBERS——客户端不再维护副本， 加一个境界不需要改这里（旧实现写死了一张九境表）。
     */
    public static Component realmName(StrifeData data) {
        String id = realmId(data.realmOrdinal());
        if (id == null) {
            return Component.literal("#" + data.realmOrdinal());
        }
        return Component.translatable("realm.strife." + id);
    }

    private static String realmId(int ordinal) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return null;
        }
        try {
            return StrifeOrdinalIndex.of(minecraft.getResourceManager(), REALM_DOMAIN)
                    .idAt(ordinal);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 五行列表 + 品阶；灵根未生成时显示"未生成"。 */
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

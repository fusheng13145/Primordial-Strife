package com.strife.quest.dialog;

import com.strife.core.StrifeMod;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 对话网络的载荷（docs/03 §4）。放 quest.dialog 而不是 core.net：core 是被依赖方，不能反向 import quest （03 §2 依赖方向）；注册由
 * {@code DialogNetwork} 自行监听 {@code RegisterPayloadHandlersEvent} 完成。
 *
 * <p>服务端权威口径：S2C 只发"显示什么"（节点正文与可见选项的<b>已渲染文本</b>），不发树结构、不发被过滤的选项； C2S 只发"我选了第几个/我关了"，选项求值全部在服务端重放。
 */
public final class DialogPayloads {

    /** S2C：打开/推进到某节点。文本是服务端查产物后的成品（客户端零 lang 依赖）。 */
    public record Open(
            String npcId,
            String treeId,
            String nodeId,
            String speaker,
            String text,
            List<String> optionTexts)
            implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<Open> TYPE =
                new CustomPacketPayload.Type<>(
                        ResourceLocation.fromNamespaceAndPath(StrifeMod.MOD_ID, "dialog_open"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Open> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8,
                        Open::npcId,
                        ByteBufCodecs.STRING_UTF8,
                        Open::treeId,
                        ByteBufCodecs.STRING_UTF8,
                        Open::nodeId,
                        ByteBufCodecs.STRING_UTF8,
                        Open::speaker,
                        ByteBufCodecs.STRING_UTF8,
                        Open::text,
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),
                        Open::optionTexts,
                        Open::new);

        /** 空说话人显示为空串（UI 不渲染名牌行）。 */
        public static final Open TERMINATED = new Open("", "", "", "", "", List.of());

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public boolean terminated() {
            return treeId.isEmpty();
        }

        /** 防御拷贝：载荷一出站就不可变。 */
        public Open {
            optionTexts = optionTexts == null ? List.of() : List.copyOf(optionTexts);
        }
    }

    /** C2S：玩家动作。{@code chooseIndex < 0} 表示关闭对话；否则是可见选项列表的下标。 */
    public record Choose(int chooseIndex) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<Choose> TYPE =
                new CustomPacketPayload.Type<>(
                        ResourceLocation.fromNamespaceAndPath(StrifeMod.MOD_ID, "dialog_choose"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Choose> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, Choose::chooseIndex, Choose::new);

        public static final Choose CLOSE = new Choose(-1);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private DialogPayloads() {}

    /** 选项文本列表的空集便捷值。 */
    public static List<String> noOptions() {
        return new ArrayList<>(0);
    }
}

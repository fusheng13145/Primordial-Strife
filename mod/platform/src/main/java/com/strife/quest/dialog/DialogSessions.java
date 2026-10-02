package com.strife.quest.dialog;

import com.strife.core.net.DialogPayloads.Open;
import com.strife.core.net.StrifeNetwork;
import com.strife.quest.dialog.DialogBook.TreeSpec;
import com.strife.quest.dialog.DialogRunner.EffectSink;
import com.strife.quest.engine.QuestAdapter;
import com.strife.quest.engine.QuestBook;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * 对话会话的服务端权威管理（docs/03 §4"数值与判定全在服务端"）。
 *
 * <p>会话状态（当前树/当前节点/深度）只存在服务端：客户端只报"我选了第几个选项/我关了"，选项的可见性过滤与 effects
 * 求值全部在服务端重放——客户端没有树结构，改包也最多让服务端抛"回显不一致"。
 *
 * <p>对话与任务共用同一 DSL 上下文与 RewardSink（{@link QuestAdapter#adapter}）：对话 effects 的
 * flag/reputation/item/start_quest 与任务奖励走同一条持久化链，不存在第二套记账。
 */
public final class DialogSessions {

    private static final Map<UUID, DialogRunner.Session> SESSIONS = new ConcurrentHashMap<>();

    private DialogSessions() {}

    /** 玩家右键 NPC（或命令）打开对话：起会话并发首个节点。NPC 无对话树时给一句可见提示。 */
    public static void open(ServerPlayer player, String npcId) {
        DialogBook book = DialogLibrary.bookByNpc(player.server, npcId);
        if (book == null) {
            player.displayClientMessage(Component.literal("（" + npcId + " 没有什么要说的）"), true);
            return;
        }
        DialogRunner.Session session =
                DialogRunner.open(book, npcId, QuestAdapter.adapter(player).dslContext());
        if (session == null) {
            return;
        }
        SESSIONS.put(player.getUUID(), session);
        push(player, session);
    }

    /** 玩家选择/关闭（C2S 的服务端入口；不在会话中的包一律丢弃——fail-closed）。 */
    public static void choose(ServerPlayer player, int optionIndex) {
        DialogRunner.Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        if (optionIndex < 0) {
            close(player);
            return;
        }
        QuestAdapter adapter = QuestAdapter.adapter(player);
        DialogRunner.ChooseOutcome outcome =
                DialogRunner.choose(session, optionIndex, adapter.dslContext(), sink(adapter));
        // effects 不走 report 保存链：无论对话是否结束，本步的 flag/声望/物品/激活改动统一落盘
        adapter.flush();
        if (outcome.ended()) {
            SESSIONS.remove(player.getUUID());
            // 与 NPC 说完一段对话 = 任务语义的 talk；同报 deliver（report 只对真正有 deliver 目标的
            // 任务生效，无匹配是 no-op）——"带齐东西来对话"即交付完成，物品流转由对话树 take_item 声明。
            QuestAdapter.interact(player, QuestBook.ObjectiveType.TALK, session.tree().npc());
            QuestAdapter.interact(player, QuestBook.ObjectiveType.DELIVER, session.tree().npc());
            StrifeNetwork.sendTo(player, Open.TERMINATED);
        } else {
            push(player, session);
        }
    }

    /** 玩家主动关闭（Esc 或 UI 按钮）：只清服务端会话，无回包。 */
    public static void close(ServerPlayer player) {
        SESSIONS.remove(player.getUUID());
    }

    /** 登出清理（会话不跨登录存活——对话不是可以挂机的状态）。 */
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SESSIONS.remove(event.getEntity().getUUID());
    }

    /** 服务的打开入口只服务命令与实体；NPC 实体把 npcId 直接传进来。 */
    public static boolean hasActiveSession(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    /** 推送当前节点：服务端查文本成品，客户端零 lang 依赖。 */
    private static void push(ServerPlayer player, DialogRunner.Session session) {
        TreeSpec tree = session.tree();
        DialogBook.NodeSpec node = session.node();
        List<DialogBook.OptionSpec> visible =
                DialogRunner.visibleOptions(session, QuestAdapter.adapter(player).dslContext());
        List<String> optionTexts = new ArrayList<>();
        for (DialogBook.OptionSpec option : visible) {
            optionTexts.add(text(player.server, session, option.textKey()));
        }
        String speakerName =
                node.speaker() == null || node.speaker().isBlank() ? "" : node.speaker();
        StrifeNetwork.sendTo(
                player,
                new Open(
                        tree.npc(),
                        tree.id(),
                        node.id(),
                        speakerName,
                        text(player.server, session, node.textKey()),
                        optionTexts));
    }

    /** effects 落地缝：任务 RewardSink 同一套记账，对话结束后显式 flush（不走 report 保存链）。 */
    private static EffectSink sink(QuestAdapter adapter) {
        return new EffectSink() {
            @Override
            public void setFlag(String key) {
                adapter.setFlag(key);
            }

            @Override
            public void addReputation(String factionId, int delta) {
                adapter.addReputation(factionId, delta);
            }

            @Override
            public void giveItem(String itemId, long count) {
                adapter.giveItem(itemId, count);
            }

            @Override
            public boolean takeItem(String itemId, long count) {
                if (countOwned(player(), itemId) < count) {
                    return false;
                }
                long remaining = count;
                // 先主背包后末影箱（与 COLLECT 对账的 countOwned 同一覆盖面）
                remaining -= removeFrom(player().getInventory(), itemId, remaining);
                if (remaining > 0) {
                    remaining -= removeFrom(player().getEnderChestInventory(), itemId, remaining);
                }
                return remaining <= 0;
            }

            @Override
            public void startQuest(String questId) {
                adapter.startQuest(questId);
            }

            @Override
            public void completeNode(String key) {
                adapter.setFlag(key);
            }

            @Override
            public void playSound(String soundId, float volume, float pitch) {
                ResourceId sound = ResourceId.of(soundId);
                player().level()
                        .playSound(
                                null,
                                player().blockPosition(),
                                net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.get(
                                        sound.location()),
                                net.minecraft.sounds.SoundSource.NEUTRAL,
                                volume,
                                pitch);
            }

            @Override
            public void teleport(double x, double y, double z, String dimension) {
                ServerPlayer p = player();
                if (dimension == null || dimension.isBlank()) {
                    p.teleportTo(x, y, z);
                    return;
                }
                MinecraftServer server = p.server;
                net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> key =
                        net.minecraft.resources.ResourceKey.create(
                                net.minecraft.core.registries.Registries.DIMENSION,
                                ResourceId.of(dimension).location());
                net.minecraft.server.level.ServerLevel target = server.getLevel(key);
                if (target == null) {
                    throw new IllegalStateException("teleport dimension not found: " + dimension);
                }
                p.teleportTo(target, x, y, z, java.util.Set.of(), p.getYRot(), p.getXRot());
            }

            private ServerPlayer player() {
                return adapter.player();
            }
        };
    }

    /** "namespace:path" 最小解析（sound/dimension 字符串参数的公共形态）。 */
    private static final class ResourceId {
        private final net.minecraft.resources.ResourceLocation location;

        private ResourceId(net.minecraft.resources.ResourceLocation location) {
            this.location = location;
        }

        static ResourceId of(String raw) {
            String value = raw.trim();
            if (!value.contains(":")) {
                value = "strife:" + value;
            }
            return new ResourceId(net.minecraft.resources.ResourceLocation.parse(value));
        }

        net.minecraft.resources.ResourceLocation location() {
            return location;
        }
    }

    private static String text(
            MinecraftServer server, DialogRunner.Session session, String textKey) {
        return DialogLibrary.text(server, session.book().chapter(), textKey);
    }

    /** 某物品在容器里的持有量（与 QuestAdapter 的对账口径一致：主背包 + 末影箱）。 */
    private static long countOwned(ServerPlayer player, String itemId) {
        net.minecraft.world.item.Item item = resolveItem(itemId);
        if (item == null) {
            return 0;
        }
        return countIn(player.getInventory(), item)
                + countIn(player.getEnderChestInventory(), item);
    }

    private static net.minecraft.world.item.Item resolveItem(String itemId) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getOptional(
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                "strife", itemId))
                .orElse(null);
    }

    private static long countIn(
            net.minecraft.world.Container container, net.minecraft.world.item.Item item) {
        long count = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            net.minecraft.world.item.ItemStack stack = container.getItem(i);
            if (stack.getItem() == item) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** 从容器扣除至多 amount 个指定物品；返回实际扣除数（物品未注册按 0——热更内容不炸运行时）。 */
    private static long removeFrom(
            net.minecraft.world.Container container, String itemId, long amount) {
        net.minecraft.world.item.Item item = resolveItem(itemId);
        if (item == null) {
            return 0;
        }
        long removed = 0;
        for (int i = 0; i < container.getContainerSize() && removed < amount; i++) {
            net.minecraft.world.item.ItemStack stack = container.getItem(i);
            if (stack.getItem() != item) {
                continue;
            }
            int take = (int) Math.min(stack.getCount(), amount - removed);
            stack.shrink(take);
            removed += take;
        }
        return removed;
    }
}

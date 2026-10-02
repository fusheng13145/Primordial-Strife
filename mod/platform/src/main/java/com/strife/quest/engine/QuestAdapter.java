package com.strife.quest.engine;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.strife.core.QuestProgress;
import com.strife.core.RewardBridges;
import com.strife.core.StrifeAttachmentTypes;
import com.strife.core.StrifeData;
import com.strife.quest.dsl.ConditionDsl;
import com.strife.quest.dsl.ConditionExpression;
import com.strife.realm.RealmEvents;
import com.strife.realm.RealmTables;
import com.strife.realm.UnlockBits;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * QuestEngine 的 realm 装配层（docs/07 §7 M3 事件订阅 + 进度入档）： 把登录 / 打坐 / 突破 / 击杀 / 拾取 /
 * 命令交互（talk/deliver）喂进 {@link QuestBook#report}，奖励经 {@link RewardSink} 落地，COLLECT 目标经库存对账（{@link
 * QuestBook#collectDeltas}）推进。
 *
 * <p>持久化：任务进度（已完成集合 + 目标进度 + H3 因果标记）随 {@link StrifeData} 玩家附件走（03 §3：玩家数据一律走附件， copyOnDeath
 * 让进度随角色生死）。装配层<b>无跨事件缓存</b>——每个事件入口从附件重建 {@link QuestState}，改动经 {@link #save()}
 * 整体写回；这条"读-改-写"链对玩家是串行的（服务端主线程），不存在并发写丢。
 *
 * <p>MVP 脚手架：序章节点 1（talk npc_qingshi_zhizhi）没有 NPC 实体，登录即自动推进； 其余 talk/deliver 节点用 {@code /strife
 * quest talk|deliver <npc>} 交互（正式实现换成 NPC 对话事件与交付 UI，M3/M4）。 击杀口径：任意生物（妖兽实体与专属掉落是 M2）。
 */
public final class QuestAdapter implements RewardSink {

    private static final String SCAFFOLD_FLAG = "quest_scaffold_talk_done";

    private final ServerPlayer player;
    private final QuestState state;
    private final Set<String> h3Flags;

    private QuestAdapter(ServerPlayer player, QuestState state, Set<String> h3Flags) {
        this.player = player;
        this.state = state;
        this.h3Flags = h3Flags;
    }

    // ===== 事件入口（StrifeQuest 入口构造时注册；realm 事件经 RealmEvents 下游订阅） =====

    public static void register(net.neoforged.bus.api.IEventBus modEventBus) {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(QuestAdapter::onRealmSit);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                QuestAdapter::onRealmBreakthrough);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(QuestAdapter::onPlayerJoin);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(QuestAdapter::onLivingDeath);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(QuestAdapter::onItemPickup);
    }

    private static void onRealmSit(RealmEvents.SitMeditated event) {
        if (!event.player().level().isClientSide) {
            adapter(event.player()).report(QuestBook.ObjectiveType.SIT, null, event.ticks());
        }
    }

    private static void onRealmBreakthrough(RealmEvents.Breakthrough event) {
        // 只有成功的突破才算任务进度：事件现在显式携带 success（旧口径"收到事件=突破成功"会把失败也记成完成）。
        if (!event.player().level().isClientSide && event.success()) {
            adapter(event.player())
                    .report(QuestBook.ObjectiveType.BREAKTHROUGH, event.breakthroughKey(), 1);
        }
    }

    private static void onPlayerJoin(
            net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !player.level().isClientSide) {
            onLogin(player);
        }
    }

    /** 击杀推进（序章 #7 kill×3；MVP 口径：任意生物死亡且凶手为玩家即计——妖兽实体是 M2，占位口径）。 */
    private static void onLivingDeath(
            net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player
                && !player.level().isClientSide) {
            adapter(player).reportAndSync(QuestBook.ObjectiveType.KILL, null, 1);
        }
    }

    /**
     * 拾取推进（COLLECT 的即时反馈；Post = 拾取已成功，计数不虚报。与 {@link #syncCollect} 对账互补 不重复——report
     * 先记拾取量，对账补的是持有量与已记进度的差值）。
     */
    private static void onItemPickup(
            net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent.Post event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || player.level().isClientSide) {
            return;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(event.getCurrentStack().getItem());
        if ("strife".equals(itemId.getNamespace())) {
            adapter(player)
                    .reportAndSync(
                            QuestBook.ObjectiveType.COLLECT,
                            itemId.getPath(),
                            event.getCurrentStack().getCount());
        }
    }

    static void onLogin(ServerPlayer player) {
        QuestAdapter adapter = adapter(player);
        if (!adapter.h3Flags.contains(SCAFFOLD_FLAG)) {
            // MVP 脚手架：登录自动完成序章节点 1（教面板读法），正式 NPC 到位后撤掉
            adapter.report(QuestBook.ObjectiveType.TALK, "npc_qingshi_zhizhi", 1);
            adapter.h3Flags.add(SCAFFOLD_FLAG);
            adapter.save();
        }
        // 登录即对账一轮：离线间拿到/被奖励的物品可能正好补齐 COLLECT 目标
        adapter.syncCollect();
    }

    /** 每个事件入口都从附件重建装配层：任务进度现在是玩家数据（03 §3），不再有世界级缓存可复用。 */
    private static QuestAdapter adapter(ServerPlayer player) {
        QuestProgress progress = player.getData(StrifeAttachmentTypes.PLAYER_DATA).quests();
        QuestState state = QuestState.of(progress.completed(), progress.objectiveProgress());
        return new QuestAdapter(player, state, new HashSet<>(progress.flags()));
    }

    /** {@code /strife quest talk|deliver} 的落地：一次面向目标 NPC 的交互事件（target 按内容 ID 全等匹配）。 */
    public static void interact(ServerPlayer player, QuestBook.ObjectiveType type, String target) {
        adapter(player).reportAndSync(type, target, 1);
    }

    /** {@code /strife quest status} 的落地：任务一览（状态 + 目标进度），供命令与后续面板消费。 */
    public static String status(ServerPlayer player) {
        QuestAdapter instance = adapter(player);
        return prologueBook(player.server).describe(instance.state, instance.dslContext());
    }

    private void report(QuestBook.ObjectiveType type, String target, long amount) {
        List<QuestBook.AppliedReward> applied =
                prologueBook(player.server).report(type, target, amount, state, this, dslContext());
        save();
        announceCompleted(applied);
    }

    /** 事件入口统一走"推进 + 对账"：奖励发放可能解锁下一环采集（如 #3 奖励的灵石进背包）。 */
    private void reportAndSync(QuestBook.ObjectiveType type, String target, long amount) {
        report(type, target, amount);
        syncCollect();
    }

    /** COLLECT 对账循环：补报直到收敛（级联上限给足链长，正常两三轮即空）。 */
    private void syncCollect() {
        QuestBook book = prologueBook(player.server);
        for (int guard = 0; guard < 16; guard++) {
            List<QuestBook.CollectDelta> deltas =
                    book.collectDeltas(state, dslContext(), itemId -> countOwned(player, itemId));
            if (deltas.isEmpty()) {
                return;
            }
            for (QuestBook.CollectDelta delta : deltas) {
                report(QuestBook.ObjectiveType.COLLECT, delta.target(), delta.amount());
            }
        }
    }

    private void announceCompleted(List<QuestBook.AppliedReward> applied) {
        java.util.LinkedHashSet<String> questIds = new java.util.LinkedHashSet<>();
        applied.forEach(reward -> questIds.add(reward.questId()));
        for (String questId : questIds) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("✦ 任务完成：")
                            .withStyle(net.minecraft.ChatFormatting.GOLD)
                            .append(
                                    net.minecraft.network.chat.Component.literal(questId)
                                            .withStyle(net.minecraft.ChatFormatting.YELLOW)),
                    false);
        }
    }

    /** 任务目标里物品的持有量：主背包 + 末影箱（03 §8"任务物品支持末影箱内交付"的计数口径）。物品未注册 按 0（未进 jar 的热更内容不炸运行时）。 */
    private static int countOwned(ServerPlayer player, String itemId) {
        Item item =
                BuiltInRegistries.ITEM
                        .getOptional(ResourceLocation.fromNamespaceAndPath("strife", itemId))
                        .orElse(null);
        if (item == null) {
            return 0;
        }
        return countIn(player.getInventory(), item)
                + countIn(player.getEnderChestInventory(), item);
    }

    private static int countIn(net.minecraft.world.Container container, Item item) {
        int count = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.getItem() == item) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** 序章任务簿由 quest 模块自己从 datapack 加载（realm 不反向依赖 quest）。 */
    private static QuestBook prologueBook(MinecraftServer server) {
        QuestBook book = PROLOGUE;
        if (book == null) {
            synchronized (QuestAdapter.class) {
                book = PROLOGUE;
                if (book == null) {
                    try (var stream =
                                    server.getResourceManager()
                                            .getResource(
                                                    ResourceLocation.fromNamespaceAndPath(
                                                            "strife",
                                                            "strife_quests/prologue.json"))
                                            .orElseThrow(
                                                    () ->
                                                            new IllegalStateException(
                                                                    "strife_quests/prologue.json not in jar"))
                                            .open();
                            var reader =
                                    new java.io.InputStreamReader(
                                            stream, java.nio.charset.StandardCharsets.UTF_8)) {
                        JsonObject object = JsonParser.parseReader(reader).getAsJsonObject();
                        PROLOGUE = book = QuestBook.parse(object);
                    } catch (Exception e) {
                        throw new IllegalStateException("cannot load prologue quest book", e);
                    }
                }
            }
        }
        return book;
    }

    private static volatile QuestBook PROLOGUE;

    private void save() {
        StrifeData data = player.getData(StrifeAttachmentTypes.PLAYER_DATA);
        player.setData(
                StrifeAttachmentTypes.PLAYER_DATA,
                data.withQuests(
                        new QuestProgress(state.completedIds(), state.progressMap(), h3Flags)));
    }

    /** 条件 DSL 的玩家侧上下文：realm/灵根亲和/flag 全部读玩家附件，物品走库存实时计数。 */
    private ConditionExpression.Context dslContext() {
        StrifeData data = player.getData(StrifeAttachmentTypes.PLAYER_DATA);
        QuestState questState = state;
        Set<String> flags = h3Flags;
        StrifeData immutable = data;
        return new ConditionExpression.Context() {
            @Override
            public int realm() {
                return immutable.realmOrdinal();
            }

            @Override
            public boolean flag(String key) {
                return flags.contains(key);
            }

            @Override
            public int itemCount(String itemId) {
                return countOwned(player, itemId);
            }

            @Override
            public int reputation(String factionId) {
                return immutable.reputation().getOrDefault(factionId, 0);
            }

            @Override
            public boolean questDone(String questId) {
                return questState.completed(questId);
            }

            @Override
            public boolean affinity(String element) {
                return switch (element) {
                    case "jin" -> (immutable.spiritrootElements() & 1) != 0;
                    case "mu" -> (immutable.spiritrootElements() & 2) != 0;
                    case "shui" -> (immutable.spiritrootElements() & 4) != 0;
                    case "huo" -> (immutable.spiritrootElements() & 8) != 0;
                    case "tu" -> (immutable.spiritrootElements() & 16) != 0;
                    default -> false;
                };
            }

            @Override
            public int realmOrdinal(String realmId) {
                RealmTables.RealmEntry entry = RealmTables.get(player.server).realmById(realmId);
                if (entry == null) {
                    throw new ConditionDsl.EvaluationException(
                            "unknown realm id '" + realmId + "'");
                }
                return entry.ordinal();
            }

            @Override
            public int subStage() {
                return immutable.stage();
            }

            @Override
            public int luck() {
                return 0; // H6 预留，本期恒 0
            }
        };
    }

    // ===== RewardSink（引擎算"该给什么"，这里落地） =====

    @Override
    public void giveItem(String itemId, long count) {
        // 注册名 = 内容 ID 全名（strife:item_ningxu），与任务表 target / lang key / 丹方 materials 同一口径
        Item item =
                BuiltInRegistries.ITEM
                        .getOptional(ResourceLocation.fromNamespaceAndPath("strife", itemId))
                        .orElse(null);
        if (item == null) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                            "（奖励 " + itemId + " 的物品尚未注册，production 物品注册后补发）"),
                    false);
            return;
        }
        player.getInventory().add(new ItemStack(item, (int) Math.min(count, Integer.MAX_VALUE)));
    }

    @Override
    public void grantQi(long amount) {
        // 必须 wither 链：11 参兼容构造会把打坐/丹药/功法/任务四块状态静默清空——
        // 玩家正在打坐时交付任务奖励，修为涨了但整个打坐会话连同任务进度一起蒸发。
        StrifeData data = player.getData(StrifeAttachmentTypes.PLAYER_DATA);
        player.setData(
                StrifeAttachmentTypes.PLAYER_DATA,
                data.withQi((int) Math.min(Integer.MAX_VALUE, (long) data.qi() + amount)));
    }

    @Override
    public void advanceRealmStep() {
        throw new UnsupportedOperationException("realm_step 奖励待 realm 大限/闭关细则（05 §4）落地");
    }

    @Override
    public void grantSpell(String spellId) {
        // combat 领域（M2）落地前记日志，奖励缺口可见
        player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("（法术 " + spellId + " 待 M2 战斗系统）"),
                false);
    }

    @Override
    public void grantTechnique(String techniqueId) {
        // 跨模块走 core 的奖励桥（quest 不能 import combat，03 §2）：没接上时明确说出来，不假装发过。
        if (!RewardBridges.grantTechnique(player, techniqueId)) {
            player.displayClientMessage(
                    Component.translatable(
                            RewardBridges.techniqueWired()
                                    ? "msg.strife.reward.technique_denied"
                                    : "msg.strife.reward.technique_unwired",
                            Component.translatable("technique.strife." + techniqueId)),
                    false);
        }
    }

    @Override
    public void setFlag(String key) {
        h3Flags.add(key);
    }

    @Override
    public void unlock(String unlockKey) {
        StrifeData data = player.getData(StrifeAttachmentTypes.PLAYER_DATA);
        player.setData(
                StrifeAttachmentTypes.PLAYER_DATA,
                data.withFlags(UnlockBits.with(data.flags(), unlockKey)));
    }

    @Override
    public void addReputation(String factionId, int delta) {
        StrifeData data = player.getData(StrifeAttachmentTypes.PLAYER_DATA);
        Map<String, Integer> reputation = new HashMap<>(data.reputation());
        reputation.merge(factionId, delta, Integer::sum);
        player.setData(
                StrifeAttachmentTypes.PLAYER_DATA, data.withReputation(Map.copyOf(reputation)));
    }

    // 旧的 ProgressSavedData（世界级 SavedData）已删除：任务进度是玩家数据，现在随 StrifeData 附件走
    // （03 §3 的 copyOnDeath 与"同一聚合对象"两条口径由此同时满足）。
}

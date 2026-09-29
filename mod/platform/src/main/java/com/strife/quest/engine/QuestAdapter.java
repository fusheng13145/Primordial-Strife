package com.strife.quest.engine;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.strife.core.StrifeAttachmentTypes;
import com.strife.core.StrifeData;
import com.strife.quest.dsl.ConditionDsl;
import com.strife.quest.dsl.ConditionExpression;
import com.strife.realm.RealmEvents;
import com.strife.realm.RealmTables;
import com.strife.realm.UnlockBits;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * QuestEngine 的 realm 装配层（docs/07 §7 M3 事件订阅 + 进度入档的 MVP 快速通道）： 把登录 / 打坐 / 突破三类事件喂进 {@link
 * QuestBook#report}，奖励经 {@link RewardSink} 落地。
 *
 * <p>持久化：MVP 用世界级 SavedData 挂 per-玩家快照（正式实现应换成玩家附件， 待 core 接线后排期——SavedData 不走
 * copyOnDeath，死亡后任务进度保留是 MVP 的已知取舍）。
 *
 * <p>MVP 脚手架：序章节点 1（talk npc_qingshi_zhizhi）没有 NPC 实体，登录即自动推进—— 正式实现换成 NPC 对话事件（M3）。任务链在节点
 * 3（collect item_ningxu）因物品系统 未落地（M2）而暂停，属预期。
 */
public final class QuestAdapter implements RewardSink {

    private static final String DATA_NAME = "strife_quest_progress";
    private static final String SCAFFOLD_FLAG = "quest_scaffold_talk_done";

    private final ServerPlayer player;
    private final QuestState state;
    private final Set<String> h3Flags;
    private final ProgressSavedData saved;

    private QuestAdapter(
            ServerPlayer player, QuestState state, Set<String> h3Flags, ProgressSavedData saved) {
        this.player = player;
        this.state = state;
        this.h3Flags = h3Flags;
        this.saved = saved;
    }

    // ===== 事件入口（StrifeQuest 入口构造时注册；realm 事件经 RealmEvents 下游订阅） =====

    public static void register(net.neoforged.bus.api.IEventBus modEventBus) {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(QuestAdapter::onRealmSit);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                QuestAdapter::onRealmBreakthrough);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(QuestAdapter::onPlayerJoin);
    }

    private static void onRealmSit(RealmEvents.SitMeditated event) {
        if (!event.player().level().isClientSide) {
            adapter(event.player()).report(QuestBook.ObjectiveType.SIT, null, event.ticks());
        }
    }

    private static void onRealmBreakthrough(RealmEvents.Breakthrough event) {
        if (!event.player().level().isClientSide) {
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

    static void onLogin(ServerPlayer player) {
        ProgressSavedData saved = ProgressSavedData.get(player.server);
        QuestAdapter adapter =
                new QuestAdapter(
                        player,
                        saved.stateOf(player.getUUID()),
                        saved.flagsOf(player.getUUID()),
                        saved);
        if (saved.marked(player.getUUID(), SCAFFOLD_FLAG)) {
            return;
        }
        // MVP 脚手架：登录自动完成序章节点 1（教面板读法），正式 NPC 到位后撤掉
        adapter.report(QuestBook.ObjectiveType.TALK, "npc_qingshi_zhizhi", 1);
        saved.mark(player.getUUID(), SCAFFOLD_FLAG);
        adapter.save();
    }

    private static QuestAdapter adapter(ServerPlayer player) {
        ProgressSavedData saved = ProgressSavedData.get(player.server);
        return new QuestAdapter(
                player, saved.stateOf(player.getUUID()), saved.flagsOf(player.getUUID()), saved);
    }

    private void report(QuestBook.ObjectiveType type, String target, long amount) {
        prologueBook(player.server).report(type, target, amount, state, this, dslContext());
        save();
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
        saved.store(player.getUUID(), state.snapshot(), h3Flags);
        saved.setDirty();
    }

    /** 条件 DSL 的玩家侧上下文：realm/灵根亲和读附件，flag 读 SavedData，物品 MVP 恒 0。 */
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
                return 0; // MVP：背包计数等物品系统（M2）接通
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
        Item item =
                BuiltInRegistries.ITEM
                        .getOptional(
                                ResourceLocation.fromNamespaceAndPath(
                                        "strife", itemId.substring("item_".length())))
                        .orElse(null);
        if (item == null) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                            "（奖励 " + itemId + " 的物品尚未注册，M2 物品系统落地后补发）"),
                    false);
            return;
        }
        player.getInventory().add(new ItemStack(item, (int) Math.min(count, Integer.MAX_VALUE)));
    }

    @Override
    public void grantQi(long amount) {
        StrifeData data = player.getData(StrifeAttachmentTypes.PLAYER_DATA);
        player.setData(
                StrifeAttachmentTypes.PLAYER_DATA,
                new StrifeData(
                        data.dataVersion(),
                        data.realmOrdinal(),
                        data.stage(),
                        (int) Math.min(Integer.MAX_VALUE, data.qi() + amount),
                        data.lifespanTicks(),
                        data.flags(),
                        data.spiritrootQuality(),
                        data.spiritrootElements(),
                        data.breakthroughAttempts(),
                        data.affiliation(),
                        data.reputation()));
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
        player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("（功法 " + techniqueId + " 待 M1 装备系统）"),
                false);
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
                new StrifeData(
                        data.dataVersion(),
                        data.realmOrdinal(),
                        data.stage(),
                        data.qi(),
                        data.lifespanTicks(),
                        UnlockBits.with(data.flags(), unlockKey),
                        data.spiritrootQuality(),
                        data.spiritrootElements(),
                        data.breakthroughAttempts(),
                        data.affiliation(),
                        data.reputation()));
    }

    @Override
    public void addReputation(String factionId, int delta) {
        StrifeData data = player.getData(StrifeAttachmentTypes.PLAYER_DATA);
        Map<String, Integer> reputation = new HashMap<>(data.reputation());
        reputation.merge(factionId, delta, Integer::sum);
        player.setData(
                StrifeAttachmentTypes.PLAYER_DATA,
                new StrifeData(
                        data.dataVersion(),
                        data.realmOrdinal(),
                        data.stage(),
                        data.qi(),
                        data.lifespanTicks(),
                        data.flags(),
                        data.spiritrootQuality(),
                        data.spiritrootElements(),
                        data.breakthroughAttempts(),
                        data.affiliation(),
                        reputation));
    }

    // ===== 世界级 SavedData（MVP 持久化；正式实现换玩家附件，见类注） =====

    public static final class ProgressSavedData extends SavedData {

        private final Map<UUID, Map<String, Object>> snapshots = new HashMap<>();
        private final Map<UUID, Set<String>> flags = new HashMap<>();
        private final Set<String> marks = new HashSet<>();

        public static ProgressSavedData get(MinecraftServer server) {
            return server.overworld()
                    .getDataStorage()
                    .computeIfAbsent(
                            new SavedData.Factory<>(
                                    ProgressSavedData::new, ProgressSavedData::load, null),
                            DATA_NAME);
        }

        QuestState stateOf(UUID playerId) {
            return QuestState.restore(snapshots.get(playerId));
        }

        Set<String> flagsOf(UUID playerId) {
            return flags.computeIfAbsent(playerId, k -> new HashSet<>());
        }

        boolean marked(UUID playerId, String mark) {
            return marks.contains(playerId + ":" + mark);
        }

        void mark(UUID playerId, String mark) {
            marks.add(playerId + ":" + mark);
        }

        void store(UUID playerId, Map<String, Object> snapshot, Set<String> h3Flags) {
            snapshots.put(playerId, snapshot);
            flags.put(playerId, h3Flags);
        }

        @Override
        public CompoundTag save(
                CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
            snapshots.forEach(
                    (playerId, snapshot) -> {
                        CompoundTag playerTag = new CompoundTag();
                        @SuppressWarnings("unchecked")
                        Map<String, Boolean> completed =
                                (Map<String, Boolean>) snapshot.getOrDefault("completed", Map.of());
                        CompoundTag completedTag = new CompoundTag();
                        completed.forEach(completedTag::putBoolean);
                        playerTag.put("completed", completedTag);
                        @SuppressWarnings("unchecked")
                        Map<String, Map<String, Long>> progress =
                                (Map<String, Map<String, Long>>)
                                        snapshot.getOrDefault("objective_progress", Map.of());
                        CompoundTag progressTag = new CompoundTag();
                        progress.forEach(
                                (questId, byObjective) -> {
                                    CompoundTag questTag = new CompoundTag();
                                    byObjective.forEach(
                                            (objectiveId, value) ->
                                                    questTag.putLong(objectiveId, value));
                                    progressTag.put(questId, questTag);
                                });
                        playerTag.put("progress", progressTag);
                        tag.put(playerId.toString(), playerTag);
                    });
            CompoundTag flagTags = new CompoundTag();
            flags.forEach(
                    (playerId, set) -> {
                        ListTag list = new ListTag();
                        set.forEach(
                                key -> {
                                    CompoundTag keyTag = new CompoundTag();
                                    keyTag.putString("key", key);
                                    list.add(keyTag);
                                });
                        flagTags.put(playerId.toString(), list);
                    });
            tag.put("flags", flagTags);
            ListTag markList = new ListTag();
            marks.forEach(
                    mark -> {
                        CompoundTag markTag = new CompoundTag();
                        markTag.putString("mark", mark);
                        markList.add(markTag);
                    });
            tag.put("marks", markList);
            return tag;
        }

        private static ProgressSavedData load(
                CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
            ProgressSavedData data = new ProgressSavedData();
            for (String playerId : tag.getAllKeys()) {
                if (playerId.equals("flags") || playerId.equals("marks")) {
                    continue;
                }
                CompoundTag playerTag = tag.getCompound(playerId);
                Map<String, Object> snapshot = new HashMap<>();
                Map<String, Boolean> completed = new HashMap<>();
                CompoundTag completedTag = playerTag.getCompound("completed");
                for (String questId : completedTag.getAllKeys()) {
                    completed.put(questId, completedTag.getBoolean(questId));
                }
                snapshot.put("completed", completed);
                Map<String, Map<String, Long>> progress = new HashMap<>();
                CompoundTag progressTag = playerTag.getCompound("progress");
                for (String questId : progressTag.getAllKeys()) {
                    Map<String, Long> byObjective = new HashMap<>();
                    CompoundTag questTag = progressTag.getCompound(questId);
                    for (String objectiveId : questTag.getAllKeys()) {
                        byObjective.put(objectiveId, questTag.getLong(objectiveId));
                    }
                    progress.put(questId, byObjective);
                }
                snapshot.put("objective_progress", progress);
                data.snapshots.put(UUID.fromString(playerId), snapshot);
            }
            CompoundTag flagTags = tag.getCompound("flags");
            for (String playerId : flagTags.getAllKeys()) {
                Set<String> set = new HashSet<>();
                ListTag list = flagTags.getList(playerId, Tag.TAG_COMPOUND);
                for (int i = 0; i < list.size(); i++) {
                    set.add(list.getCompound(i).getString("key"));
                }
                data.flags.put(UUID.fromString(playerId), set);
            }
            ListTag markList = tag.getList("marks", Tag.TAG_COMPOUND);
            for (int i = 0; i < markList.size(); i++) {
                data.marks.add(markList.getCompound(i).getString("mark"));
            }
            return data;
        }
    }
}

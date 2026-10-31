package com.strife.quest;

import com.strife.quest.engine.QuestAdapter;
import com.strife.quest.engine.QuestBook;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent.BossBarColor;
import net.minecraft.world.BossEvent.BossBarOverlay;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 任务追踪条：用玩家专属 BossBar 常驻显示「当前任务 + 必做目标进度 + NPC 目标方向」。
 *
 * <p>为什么是服务端 BossBar 而不是自绘 HUD：clientfx 只能依赖 core，quest 状态不在现有 S2C delta 里；把新载荷塞进 core 会动禁区
 * 契约。BossBar 是原版服务端原语（客户端零改动），且数据源仍是服务端权威任务状态——客户端不复制任务表，也不自行判断"当前任务"。
 *
 * <p>导航只覆盖 {@code npc_} 目标：坐标来自 quest 自己的 {@code strife_npcs} 产物；{@code reach} 的地点坐标属 world， 自动联动待
 * ADR-023 的 SPI 裁决。距离是水平距离，高度不参与（与 {@code /strife world place nav} 同口径）。
 */
public final class QuestTracker {

    static final int REFRESH_INTERVAL_TICKS = 20;

    private static final Logger LOGGER = LoggerFactory.getLogger("strife/quest");
    private static final Map<UUID, Tracked> TRACKED = new HashMap<>();
    private static boolean loadFailureLogged;

    /** NPC 目标相对玩家的地面导航结果；跨维度时 {@code directionKey} 为空。 */
    record Navigation(
            boolean sameDimension,
            long distanceBlocks,
            String directionKey,
            ResourceLocation dimension) {}

    private static final class Tracked {
        private final ServerBossEvent bar;
        private boolean enabled = true;
        private String lastName = "";
        private float lastProgress = -1.0f;

        private Tracked(ServerBossEvent bar) {
            this.bar = bar;
        }
    }

    private QuestTracker() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(QuestTracker::onLoggedIn);
        NeoForge.EVENT_BUS.addListener(QuestTracker::onLoggedOut);
        NeoForge.EVENT_BUS.addListener(QuestTracker::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(QuestTracker::onServerStopping);
    }

    /** 命令入口：切换本会话的追踪条开关（默认开；开关不落档，重进恢复默认）。 */
    public static boolean toggle(ServerPlayer player) {
        Tracked tracked = TRACKED.get(player.getUUID());
        return setEnabled(player, tracked == null || !tracked.enabled);
    }

    public static boolean setEnabled(ServerPlayer player, boolean enabled) {
        Tracked tracked =
                TRACKED.computeIfAbsent(
                        player.getUUID(),
                        id ->
                                new Tracked(
                                        new ServerBossEvent(
                                                Component.empty(),
                                                BossBarColor.GREEN,
                                                BossBarOverlay.PROGRESS)));
        tracked.bar.addPlayer(player);
        tracked.enabled = enabled;
        refresh(player);
        return enabled;
    }

    private static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Tracked tracked =
                new Tracked(
                        new ServerBossEvent(
                                Component.empty(), BossBarColor.GREEN, BossBarOverlay.PROGRESS));
        tracked.bar.addPlayer(player);
        TRACKED.put(player.getUUID(), tracked);
        refresh(player);
    }

    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Tracked tracked = TRACKED.remove(player.getUUID());
        if (tracked != null) {
            tracked.bar.removePlayer(player);
        }
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        TRACKED.values().forEach(tracked -> tracked.bar.removeAllPlayers());
        TRACKED.clear();
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % REFRESH_INTERVAL_TICKS != 0) {
            return;
        }
        refresh(player);
    }

    private static void refresh(ServerPlayer player) {
        Tracked tracked = TRACKED.get(player.getUUID());
        if (tracked == null) {
            return;
        }
        if (!tracked.enabled) {
            hide(tracked);
            return;
        }
        Optional<QuestBook.QuestView> current;
        try {
            current = QuestAdapter.trackable(player);
        } catch (RuntimeException e) {
            logLoadFailure(e);
            hide(tracked);
            return;
        }
        if (current.isEmpty()) {
            hide(tracked);
            return;
        }
        QuestBook.QuestView view = current.get();
        Component name = barName(view, player);
        String flat = name.getString();
        if (!flat.equals(tracked.lastName)) {
            tracked.lastName = flat;
            tracked.bar.setName(name);
        }
        float progress = (float) view.progressRatio();
        if (Math.abs(progress - tracked.lastProgress) > 1e-4f) {
            tracked.lastProgress = progress;
            tracked.bar.setProgress(progress);
        }
        if (!tracked.bar.isVisible()) {
            tracked.bar.setVisible(true);
        }
    }

    private static void hide(Tracked tracked) {
        if (tracked.bar.isVisible()) {
            tracked.bar.setVisible(false);
        }
    }

    private static Component barName(QuestBook.QuestView view, ServerPlayer player) {
        MutableComponent name =
                Component.translatable("gui.strife.tracker.title").withStyle(ChatFormatting.AQUA);
        for (QuestBook.ObjectiveView objective : view.objectives()) {
            name.append(" · ").append(objectiveLine(objective));
        }
        navigationLine(view, player).ifPresent(line -> name.append(" · ").append(line));
        return name;
    }

    private static Component objectiveLine(QuestBook.ObjectiveView objective) {
        Component target = targetName(objective.target());
        return switch (objective.type()) {
            case TALK ->
                    Component.translatable(
                            "gui.strife.tracker.talk",
                            target,
                            objective.progress(),
                            objective.count());
            case DELIVER ->
                    Component.translatable(
                            "gui.strife.tracker.deliver",
                            target,
                            objective.progress(),
                            objective.count());
            case COLLECT ->
                    Component.translatable(
                            "gui.strife.tracker.collect",
                            target,
                            objective.progress(),
                            objective.count());
            case SIT ->
                    Component.translatable(
                            "gui.strife.tracker.sit", objective.progress(), objective.count());
            case BREAKTHROUGH ->
                    Component.translatable(
                            "gui.strife.tracker.breakthrough",
                            objective.progress(),
                            objective.count());
            case KILL ->
                    objective.target() == null
                            ? Component.translatable(
                                    "gui.strife.tracker.kill_any",
                                    objective.progress(),
                                    objective.count())
                            : Component.translatable(
                                    "gui.strife.tracker.kill",
                                    target,
                                    objective.progress(),
                                    objective.count());
            case REACH ->
                    Component.translatable(
                            "gui.strife.tracker.reach",
                            target,
                            objective.progress(),
                            objective.count());
            default ->
                    Component.translatable(
                            "gui.strife.tracker.generic",
                            target,
                            objective.progress(),
                            objective.count());
        };
    }

    private static Component targetName(String target) {
        if (target == null) {
            return Component.empty();
        }
        if (target.startsWith("npc_")) {
            return Component.translatable("npc.strife." + target);
        }
        if (target.startsWith("item_") || target.startsWith("pill_")) {
            return Component.translatable("item.strife." + target);
        }
        if (target.startsWith("block_")) {
            return Component.translatable("block.strife." + target);
        }
        if (target.startsWith("qi_")) {
            return Component.translatable("region.strife." + target);
        }
        return Component.literal(target);
    }

    private static Optional<Component> navigationLine(
            QuestBook.QuestView view, ServerPlayer player) {
        List<NpcPlacements.Placement> placements;
        try {
            placements = NpcPlacements.getOrThrow(player.getServer());
        } catch (RuntimeException e) {
            logLoadFailure(e);
            return Optional.empty();
        }
        ResourceLocation dimension = player.level().dimension().location();
        for (QuestBook.ObjectiveView objective : view.objectives()) {
            Optional<Navigation> nav =
                    navigationFor(
                            objective.target(),
                            placements,
                            dimension,
                            player.getX(),
                            player.getZ());
            if (nav.isPresent()) {
                return Optional.of(navigationLine(nav.get()));
            }
        }
        return Optional.empty();
    }

    private static Component navigationLine(Navigation nav) {
        if (!nav.sameDimension()) {
            return Component.translatable(
                    "gui.strife.tracker.nav_dimension",
                    Component.literal(nav.dimension().toString()));
        }
        if (nav.distanceBlocks() == 0L) {
            return Component.translatable("gui.strife.tracker.nav_here");
        }
        return Component.translatable(
                "gui.strife.tracker.nav",
                nav.distanceBlocks(),
                Component.translatable(nav.directionKey()));
    }

    static Optional<Navigation> navigationFor(
            String target,
            List<NpcPlacements.Placement> placements,
            ResourceLocation playerDimension,
            double playerX,
            double playerZ) {
        if (target == null || !target.startsWith("npc_")) {
            return Optional.empty();
        }
        for (NpcPlacements.Placement placement : placements) {
            if (!placement.id().equals(target)) {
                continue;
            }
            if (!placement.dimension().equals(playerDimension)) {
                return Optional.of(new Navigation(false, 0L, "", placement.dimension()));
            }
            double dx = placement.x() - playerX;
            double dz = placement.z() - playerZ;
            long distance = Math.round(Math.hypot(dx, dz));
            return Optional.of(
                    new Navigation(
                            true,
                            distance,
                            distance == 0L ? "gui.strife.tracker.dir.here" : directionKey(dx, dz),
                            placement.dimension()));
        }
        return Optional.empty();
    }

    /** 八向罗盘：0° 为北（-z），顺时针每 45° 一档。 */
    private static String directionKey(double dx, double dz) {
        int sector = (int) Math.round(Math.toDegrees(Math.atan2(dx, -dz)) / 45.0);
        return switch (((sector % 8) + 8) % 8) {
            case 0 -> "gui.strife.tracker.dir.n";
            case 1 -> "gui.strife.tracker.dir.ne";
            case 2 -> "gui.strife.tracker.dir.e";
            case 3 -> "gui.strife.tracker.dir.se";
            case 4 -> "gui.strife.tracker.dir.s";
            case 5 -> "gui.strife.tracker.dir.sw";
            case 6 -> "gui.strife.tracker.dir.w";
            default -> "gui.strife.tracker.dir.nw";
        };
    }

    private static void logLoadFailure(RuntimeException e) {
        if (!loadFailureLogged) {
            loadFailureLogged = true;
            LOGGER.error("quest tracker data unavailable: {}", e.getMessage());
        }
    }
}

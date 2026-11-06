package com.strife.quest;

import com.strife.quest.engine.QuestAdapter;
import com.strife.quest.engine.QuestBook;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 心性试炼会话（试炼台批）：谁在哪个台上坚守到第几秒。
 *
 * <p>会话是服务端内存态（离线/重启即散——重开一局即可，不需要持久化"半场试炼"）； SURVIVE 推进经 {@code
 * QuestAdapter.reportAndSync}（本任务目标计数的唯一推进口）。心魔来袭按 {@link TrialMath#isWaveTick} 节拍 在台缘生成
 * strife:monster（实体经注册表按 ID 取——quest 不 import combat，03 §2）。
 */
public final class TrialSessions {

    private record Session(long startGameTime, BlockPos altar, long lastWaveSeconds) {}

    private static final Map<UUID, Session> ACTIVE = new ConcurrentHashMap<>();
    private static final int MAX_MONSTERS = 6;
    private static final ResourceLocation MONSTER_ID =
            ResourceLocation.fromNamespaceAndPath("strife", "monster");

    private TrialSessions() {}

    public static void register() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(new TrialSessions());
    }

    static boolean active(UUID playerId) {
        return ACTIVE.containsKey(playerId);
    }

    static void start(UUID playerId, long nowGameTime, BlockPos altar) {
        ACTIVE.put(playerId, new Session(nowGameTime, altar, 0L));
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Session session = ACTIVE.get(player.getUUID());
        if (session == null || player.tickCount % 20L != 0L) {
            return;
        }
        long now = level.getGameTime();
        long elapsed = TrialMath.secondsElapsed(session.startGameTime(), now);
        if (TrialMath.leftPlatform(session.altar(), player.getX(), player.getY(), player.getZ())) {
            ACTIVE.remove(player.getUUID());
            player.displayClientMessage(
                    Component.literal("✗ 离台即败——心性未定。（右键试炼台可再开）")
                            .withStyle(net.minecraft.ChatFormatting.RED),
                    false);
            return;
        }
        if (TrialMath.isWaveTick(elapsed)) {
            spawnWave(level, session.altar());
            player.displayClientMessage(Component.literal("✦ 心魔来袭——" + elapsed + "/120 秒"), false);
        }
        // reportAndSync 是私有推进口——公开面 interact(=1 次) 恰是"1 秒 1 进度"的语义
        QuestAdapter.interact(player, QuestBook.ObjectiveType.SURVIVE, null);
        if (elapsed >= TrialMath.TARGET_SECONDS) {
            ACTIVE.remove(player.getUUID());
            player.displayClientMessage(
                    Component.literal("✦ 心性试炼圆满——不动如山，传承认你")
                            .withStyle(net.minecraft.ChatFormatting.GOLD),
                    false);
        }
    }

    /** 台缘心魔一波（2 只，落地即索敌——压坚守节奏，不拆台）。 */
    private static void spawnWave(ServerLevel level, BlockPos altar) {
        EntityType<?> monster = BuiltInRegistries.ENTITY_TYPE.get(MONSTER_ID);
        if (countMonsters(level, altar) >= MAX_MONSTERS) {
            return;
        }
        for (int i = 0; i < 2; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            double x = altar.getX() + 0.5 + Math.cos(angle) * (TrialMath.RADIUS - 1);
            double z = altar.getZ() + 0.5 + Math.sin(angle) * (TrialMath.RADIUS - 1);
            int y =
                    level.getHeight(
                            net.minecraft.world.level.levelgen.Heightmap.Types
                                    .MOTION_BLOCKING_NO_LEAVES,
                            (int) x,
                            (int) z);
            var spawned = monster.create(level);
            if (spawned == null) {
                return;
            }
            spawned.moveTo(x, y, z, level.random.nextFloat() * 360.0F, 0.0F);
            level.addFreshEntity(spawned);
        }
    }

    /** 台上心魔数量（只认 strife:monster，避免误清其他妖兽）。 */
    private static int countMonsters(ServerLevel level, BlockPos altar) {
        return level.getEntitiesOfClass(
                        LivingEntity.class,
                        new AABB(
                                altar.getX() - 16,
                                altar.getY() - 8,
                                altar.getZ() - 16,
                                altar.getX() + 16,
                                altar.getY() + 16,
                                altar.getZ() + 16),
                        e ->
                                e.isAlive()
                                        && BuiltInRegistries.ENTITY_TYPE
                                                .getKey(e.getType())
                                                .equals(MONSTER_ID))
                .size();
    }

    /** 登出清会话（离线重连重开一局）。 */
    static void clear(UUID playerId) {
        ACTIVE.remove(playerId);
    }

    @SubscribeEvent
    public void onLoggedOut(
            net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        clear(event.getEntity().getUUID());
    }
}

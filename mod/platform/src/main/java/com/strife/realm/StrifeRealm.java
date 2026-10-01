package com.strife.realm;

import com.strife.core.StrifeMod;
import com.strife.core.net.IntentRateLimiter;
import com.strife.core.net.StrifeCoreRules;
import com.strife.core.net.StrifeIntents;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * realm 分侧入口（docs/03 §7 的模块入口模式）。
 *
 * <p>本入口只做事件编排与意图登记：登录/登出/每刻的生命周期挂到游戏总线，C2S 意图（打坐起止、突破、面板拉取）登记进 core 的分发入口 {@link
 * StrifeIntents}——校验、限速、大小闸门都在 core 侧统一做（03 §4），本类只接"校验已过"的业务调用。
 *
 * <p>与 core 的 {@code @Mod} 并列同 ID：NeoForge 会依次构造同 MOD 的多个入口（client_fx 分侧入口已在库内验证），serverJar 剔除
 * client_fx 不影响本类（realm 是双端安全的纯服务端逻辑，客户端构造的监听器按 side 守卫直接空转）。
 */
@Mod(value = StrifeMod.MOD_ID)
public final class StrifeRealm {

    private static final Logger LOGGER = LoggerFactory.getLogger("strife/realm");

    public StrifeRealm(IEventBus modEventBus, ModContainer container) {
        LOGGER.info(
                "strife realm entry constructed (version {})", container.getModInfo().getVersion());
        NeoForge.EVENT_BUS.addListener(this::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        registerIntents();
    }

    /**
     * 启动自检：把"内容/解析缺陷"从"玩家登录后静默不工作"提前成启动日志里的一条 ERROR（02 §5：内容加载失败必须报出具体路径）。
     *
     * <p>刻意只报错不崩服：正式服不该因为一个数据包键缺失而起不来。但这条 ERROR 会同时进入运维日志与 CI 的无头开服冒烟 （冒烟断言"无未解释的
     * ERROR"），所以它不会被当成背景噪声忽略。
     */
    private void onServerStarted(ServerStartedEvent event) {
        try {
            RealmTables tables = RealmTables.get(event.getServer());
            StrifeCoreRules.get(event.getServer());
            LOGGER.info(
                    "strife realm tables loaded: {} realms, {} breakthrough rates",
                    tables.realmCount(),
                    tables.rules().rates().size());
        } catch (RuntimeException e) {
            LOGGER.error("strife realm tables failed to load —— 修炼主链不可用: {}", e.getMessage(), e);
        }
    }

    /** 意图登记：id 取自 core 的常量（与 NUMBERS {@code @@rate_limits} 的键一一对应）。 */
    private static void registerIntents() {
        StrifeIntents.register(
                IntentRateLimiter.SIT,
                invocation -> CultivationHandler.toggleSit(invocation.player()));
        StrifeIntents.register(
                IntentRateLimiter.BREAKTHROUGH,
                invocation -> CultivationHandler.attemptBreakthrough(invocation.player()));
        StrifeIntents.register(
                IntentRateLimiter.PANEL,
                invocation -> RealmViewBuilder.pushTo(invocation.player()));
    }

    private void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && !player.level().isClientSide) {
            CultivationHandler.tick(player);
        }
    }

    private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !player.level().isClientSide) {
            CultivationHandler.onLogin(player);
            RealmViewBuilder.pushTo(player);
        }
    }

    private void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CultivationHandler.onLogout(player);
        }
    }
}

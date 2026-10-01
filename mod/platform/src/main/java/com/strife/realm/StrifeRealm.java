package com.strife.realm;

import com.strife.core.StrifeMod;
import com.strife.core.net.IntentRateLimiter;
import com.strife.core.net.StrifeIntents;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
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
        registerIntents();
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

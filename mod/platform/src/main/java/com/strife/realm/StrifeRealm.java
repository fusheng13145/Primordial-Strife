package com.strife.realm;

import com.strife.core.StrifeMod;
import com.strife.core.net.IntentRateLimiter;
import com.strife.core.net.StrifeCoreRules;
import com.strife.core.net.StrifeIntents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
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
        // 负面状态走注册总线（注册期），玩法事件走游戏总线（03 §2）。
        RealmEffects.EFFECTS.register(modEventBus);
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
            verifyEffectsRegistered(event, tables);
        } catch (RuntimeException e) {
            LOGGER.error("strife realm tables failed to load —— 修炼主链不可用: {}", e.getMessage(), e);
        }
    }

    /**
     * 负面状态的运行时契约检查：NUMBERS 点名的键必须真的在注册表里存在。
     *
     * <p>代码侧的 id 是编译期常量，真相源侧的键名是数据——两者分叉时不会崩、不会报错，只是<b>大限什么都不中</b>。这条检查把 那种"静默无效"变成启动日志里的一条 ERROR。
     */
    private static void verifyEffectsRegistered(ServerStartedEvent event, RealmTables tables) {
        var effects = event.getServer().registryAccess().registryOrThrow(Registries.MOB_EFFECT);
        for (String effectId :
                new String[] {tables.rules().dashengDebuffKey(), RealmEffects.HEAVY_WOUND_ID}) {
            boolean present =
                    effects.containsKey(
                            ResourceLocation.fromNamespaceAndPath(StrifeMod.MOD_ID, effectId));
            if (!present) {
                LOGGER.error("负面状态 '{}' 未注册——NUMBERS 的键名与代码注册的 id 不一致，该结算将静默无效", effectId);
            }
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

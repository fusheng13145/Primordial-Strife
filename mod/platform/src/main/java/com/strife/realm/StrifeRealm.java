package com.strife.realm;

import com.strife.core.StrifeMod;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * realm 分侧入口（docs/03 §7 的模块入口模式，MVP 快速通道——<b>待 A 评审</b>）。
 *
 * <p>本入口只做事件编排：登录（灵根生成/任务脚手架）与每刻修炼结算。业务逻辑在 {@link CultivationHandler}/{@link
 * SpiritRootGenerator}，数值全部来自 {@link RealmTables}（DataGen 产物）。core 一行未动——附件注册与命令树仍在 core。
 *
 * <p>与 core 的 {@code @Mod} 并列同 ID：NeoForge 会依次构造同 MOD 的多个入口（client_fx 分侧 已在库内验证），serverJar 剔除
 * client_fx 不影响本类（realm 是双端安全的纯服务端逻辑， 客户端构造的监听器按 side 守卫直接空转）。
 */
@Mod(value = StrifeMod.MOD_ID)
public final class StrifeRealm {

    private static final Logger LOGGER = LoggerFactory.getLogger("strife/realm");

    public StrifeRealm(IEventBus modEventBus, ModContainer container) {
        LOGGER.info(
                "strife realm entry constructed (MVP fast-track, pending A's review, version {})",
                container.getModInfo().getVersion());
        NeoForge.EVENT_BUS.addListener(this::onPlayerTick);
    }

    private void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && !player.level().isClientSide) {
            CultivationHandler.tick(player);
        }
    }
}

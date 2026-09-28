package com.strife.client_fx;

import com.strife.core.StrifeMod;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * client_fx 分侧入口（docs/03 §7）。
 *
 * <p>{@code dist = CLIENT} 让专用服务端根本不构造本类；serverJar 又整体剔除 {@code com/strife/client_fx/**}（platform
 * build.gradle 的 exclude 规则先于本包落地）——两道隔离由 CI 的 headless 开服冒烟验证：若 client 类被服务端加载，冒烟当场红。
 *
 * <p>本类只做客户端注册编排：HUD 层进 {@link RegisterGuiLayersEvent}。数据读取与显示规则在 {@link
 * StrifeHudOverlay}（服务端权威：单机读 integrated server 的权威附件，专用服上不画）。
 */
@Mod(value = StrifeMod.MOD_ID, dist = Dist.CLIENT)
public final class StrifeClientFx {

    private static final Logger LOGGER = LoggerFactory.getLogger("strife/client_fx");

    public StrifeClientFx(IEventBus modEventBus, ModContainer container) {
        LOGGER.info(
                "strife client_fx entry constructed (version {})",
                container.getModInfo().getVersion());
        modEventBus.addListener(this::onRegisterGuiLayers);
    }

    private void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(StrifeMod.MOD_ID, "cultivation_hud"),
                StrifeHudOverlay::render);
    }
}

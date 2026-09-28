package com.strife.client_fx;

import com.mojang.blaze3d.platform.InputConstants;
import com.strife.core.StrifeMod;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * client_fx 分侧入口（docs/03 §7）。
 *
 * <p>{@code dist = CLIENT} 让专用服务端根本不构造本类；serverJar 又整体剔除 {@code com/strife/client_fx/**}（platform
 * build.gradle 的 exclude 规则先于本包落地）——两道隔离由 CI 的 headless 开服冒烟验证：若 client 类被服务端加载，冒烟当场红。
 *
 * <p>本类只做客户端注册编排：HUD 层进 {@link RegisterGuiLayersEvent}，面板按键进 {@link RegisterKeyMappingsEvent}（默认
 * K，冲突上下文限游戏内），开屏动作挂在客户端 tick。数据读取与显示规则在 {@link ClientDataBridge}（服务端权威：单机读 integrated server
 * 的权威附件，专用服上不显示）。
 */
@Mod(value = StrifeMod.MOD_ID, dist = Dist.CLIENT)
public final class StrifeClientFx {

    private static final Logger LOGGER = LoggerFactory.getLogger("strife/client_fx");

    /** 详情面板开关键（docs/07 §7 B1-1）；键位可在游戏内控制设置里改。 */
    private static final KeyMapping OPEN_PANEL =
            new KeyMapping(
                    "key.strife.panel",
                    KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_K,
                    "key.categories.strife");

    public StrifeClientFx(IEventBus modEventBus, ModContainer container) {
        LOGGER.info(
                "strife client_fx entry constructed (version {})",
                container.getModInfo().getVersion());
        modEventBus.addListener(this::onRegisterGuiLayers);
        modEventBus.addListener(this::onRegisterKeyMappings);
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
    }

    private void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(StrifeMod.MOD_ID, "cultivation_hud"),
                StrifeHudOverlay::render);
    }

    private void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_PANEL);
    }

    private void onClientTick(ClientTickEvent.Post event) {
        while (OPEN_PANEL.consumeClick()) {
            var minecraft = net.minecraft.client.Minecraft.getInstance();
            if (minecraft.screen == null && minecraft.player != null) {
                minecraft.setScreen(new StrifePanelScreen());
            }
        }
    }
}

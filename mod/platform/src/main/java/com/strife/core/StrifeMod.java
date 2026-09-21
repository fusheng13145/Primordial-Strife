package com.strife.core;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Platform entry point (A0-1 骨架).
 *
 * <p>本类只做注册编排：附件类型经注册表进 {@link StrifeAttachmentTypes}，命令树进 {@link StrifeCommands}（03 分册
 * §9）。业务逻辑一律不写在这里。
 */
@Mod(StrifeMod.MOD_ID)
public final class StrifeMod {

    public static final String MOD_ID = "strife";

    private static final Logger LOGGER = LoggerFactory.getLogger("strife/core");

    public StrifeMod(IEventBus modEventBus, ModContainer container) {
        LOGGER.info(
                "strife platform entry constructed (version {})",
                container.getModInfo().getVersion());
        StrifeAttachmentTypes.ATTACHMENT_TYPES.register(modEventBus);
        // RegisterCommandsEvent is fired on the game bus whenever Commands is rebuilt.
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        StrifeCommands.register(event.getDispatcher());
    }
}

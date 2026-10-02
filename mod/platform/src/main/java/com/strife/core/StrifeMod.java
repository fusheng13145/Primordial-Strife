package com.strife.core;

import com.strife.core.net.StrifeNetwork;
import com.strife.core.net.StrifeSyncManager;
import com.strife.quest.StrifeEntities;
import com.strife.quest.StrifeNpcEntity;
import com.strife.quest.dialog.DialogNetwork;
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
        // quest 域实体（NPC：对话载体）与对话网络（quest 不能让 core 反向 import，注册各自挂 mod 总线）。
        StrifeEntities.ENTITY_TYPES.register(modEventBus);
        modEventBus.addListener(StrifeNpcEntity::onAttributes);
        DialogNetwork.register(modEventBus);
        // 载荷注册在 mod 总线（注册期），玩法事件在游戏总线（03 §2）。
        StrifeNetwork.register(modEventBus);
        // RegisterCommandsEvent is fired on the game bus whenever Commands is rebuilt.
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        // S2C 同步（03 §5）：登录发全量快照，每刻按预算推差量。
        NeoForge.EVENT_BUS.addListener(StrifeSyncManager::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(StrifeSyncManager::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(StrifeSyncManager::onPlayerTick);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        StrifeCommands.register(event.getDispatcher());
    }
}

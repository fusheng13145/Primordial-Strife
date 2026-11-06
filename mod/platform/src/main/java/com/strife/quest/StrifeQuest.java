package com.strife.quest;

import com.strife.core.StrifeMod;
import com.strife.quest.engine.QuestAdapter;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * quest 分侧入口：把 QuestAdapter 的事件订阅挂上总线，并把 {@code /strife quest} 子树经 core 挂载点并入命令树。
 *
 * <p>任务引擎与持久化在 {@code com.strife.quest.engine}；quest → realm → core 的依赖链由 checkImports 强制。
 */
@Mod(value = StrifeMod.MOD_ID)
public final class StrifeQuest {

    private static final Logger LOGGER = LoggerFactory.getLogger("strife/quest");

    public StrifeQuest(IEventBus modEventBus, ModContainer container) {
        // P1-21（D12）：入门指引命令通道——与未来客户端面板同读 strife_guide 产物。
        com.strife.core.StrifeCommands.MODULE_SUBTREES.add(GuideCommands.subtree());
        LOGGER.info(
                "strife quest entry constructed (version {})", container.getModInfo().getVersion());
        QuestAdapter.register(modEventBus);
        // 数据表驱动的世界 NPC 摆放：玩家走近时安全地表生成，已存在则跳过。
        NpcPlacementSpawner.register();
        // 任务追踪条：服务端 BossBar，显示当前必做目标与 NPC 目标方向。
        QuestTracker.register();
        // quest 域实体（NPC：对话载体）与对话网络——注册编排留在 quest 侧入口，
        // core 不 import 任何模块（03 §2 依赖方向；实体/协议都是 quest 域对象）。
        StrifeEntities.ENTITY_TYPES.register(modEventBus);
        // 试炼台批：心性试炼的物理锚点（SURVIVE 目标的唯一推进口在此）。
        TrialBlocks.BLOCKS.register(modEventBus);
        TrialBlocks.ITEMS.register(modEventBus);
        TrialSessions.register();
        TrialAltarPlacer.register();
        modEventBus.addListener(StrifeNpcEntity::onAttributes);
        com.strife.quest.dialog.DialogNetwork.register(modEventBus);
        // 兜底命令随模块进表（03 §9）：经 core 挂载点并入 /strife 根
        com.strife.core.StrifeCommands.MODULE_SUBTREES.add(QuestCommands.subtree());
        com.strife.core.StrifeCommands.MODULE_SUBTREES.add(NpcCommands.subtree());
    }
}

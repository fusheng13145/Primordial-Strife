package com.strife.quest;

import com.strife.core.StrifeMod;
import com.strife.quest.engine.QuestAdapter;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * quest 分侧入口（docs/03 §7 模块入口模式，MVP 快速通道——<b>待 A 评审</b>）。
 *
 * <p>只做一件事：把 QuestAdapter 的 realm 事件订阅挂上总线。任务引擎与持久化在 {@code com.strife.quest.engine}；本类没有业务逻辑。quest
 * → realm → core 的依赖链 由 checkImports 强制（realm 事件经 RealmEvents 下游订阅，模块边界不反向）。
 */
@Mod(value = StrifeMod.MOD_ID)
public final class StrifeQuest {

    private static final Logger LOGGER = LoggerFactory.getLogger("strife/quest");

    public StrifeQuest(IEventBus modEventBus, ModContainer container) {
        LOGGER.info(
                "strife quest entry constructed (MVP fast-track, pending A's review, version {})",
                container.getModInfo().getVersion());
        QuestAdapter.register(modEventBus);
    }
}

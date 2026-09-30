package com.strife.world;

import com.strife.core.StrifeMod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * world 分侧入口（docs/03 §7 模块入口模式，MVP 快速通道——<b>待 A 评审</b>）。
 *
 * <p>MVP 只编排一件事：草类方块破坏的草药掉落（{@link HerbDrops}）。灵气浓度场/矿石 placement/宗门结构 是 M4
 * 工单，到位后本入口再挂各自的监听器——本类没有业务逻辑。
 */
@Mod(value = StrifeMod.MOD_ID)
public final class StrifeWorld {

    private static final Logger LOGGER = LoggerFactory.getLogger("strife/world");

    public StrifeWorld(IEventBus modEventBus, ModContainer container) {
        LOGGER.info(
                "strife world entry constructed (MVP fast-track, pending A's review, version {})",
                container.getModInfo().getVersion());
        NeoForge.EVENT_BUS.addListener(HerbDrops::onBreakBlock);
    }
}

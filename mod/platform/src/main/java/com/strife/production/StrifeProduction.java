package com.strife.production;

import com.strife.core.StrifeMod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * production 分侧入口（docs/03 §7 模块入口模式，MVP 快速通道——<b>待 A 评审</b>）。
 *
 * <p>MVP 只做一件事：把 {@link StrifeItems} 的注册表挂上 mod 总线。统一配方机（炼丹/炼器）是 M2 工单， 到位后本入口再编排其监听器——本类没有业务逻辑，与
 * realm/quest 分侧入口同一形状。
 */
@Mod(value = StrifeMod.MOD_ID)
public final class StrifeProduction {

    private static final Logger LOGGER = LoggerFactory.getLogger("strife/production");

    public StrifeProduction(IEventBus modEventBus, ModContainer container) {
        LOGGER.info(
                "strife production entry constructed (MVP fast-track, pending A's review, version"
                        + " {})",
                container.getModInfo().getVersion());
        StrifeItems.ITEMS.register(modEventBus);
    }
}

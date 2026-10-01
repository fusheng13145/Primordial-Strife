package com.strife.production;

import com.strife.core.CultivationFactors;
import com.strife.core.StrifeMod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * production 分侧入口（docs/03 §7 模块入口模式）。
 *
 * <p>本入口编排两件事：注册物品（材料与可服用的丹药），以及把<b>丹药加成</b>接进 05 §2 的四因子公式 （{@link
 * CultivationFactors#registerPill}）——realm 侧一行不用改。统一配方机（炼丹/炼器）是 M2 工单。
 */
@Mod(value = StrifeMod.MOD_ID)
public final class StrifeProduction {

    private static final Logger LOGGER = LoggerFactory.getLogger("strife/production");

    public StrifeProduction(IEventBus modEventBus, ModContainer container) {
        LOGGER.info(
                "strife production entry constructed (version {})",
                container.getModInfo().getVersion());
        StrifeItems.ITEMS.register(modEventBus);
        CultivationFactors.registerPill(StrifePills::coefficient);
        LOGGER.info("strife production wired cultivation factor: pill=consumable buffs");
    }
}

package com.strife.combat;

import com.strife.core.RewardBridges;
import com.strife.core.StrifeCommands;
import com.strife.core.StrifeMod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * combat 分侧入口（docs/03 §7 模块入口模式）。
 *
 * <p>职责编排：功法倍率接进四因子公式、授功法奖励缝、{@code /strife technique} 命令子树、 法术产物表与 cast 意图（M2"法术弹道系统 +
 * 施法限速"）、combat 域实体注册（弹道/妖兽）。
 */
@Mod(value = StrifeMod.MOD_ID)
public final class StrifeCombat {

    private static final Logger LOGGER = LoggerFactory.getLogger("strife/combat");

    public StrifeCombat(IEventBus modEventBus, ModContainer container) {
        LOGGER.info(
                "strife combat entry constructed (version {})",
                container.getModInfo().getVersion());
        Techniques.registerFactor();
        RewardBridges.registerTechniqueGranter(Techniques::learn);
        StrifeCommands.MODULE_SUBTREES.add(Techniques.command());
        StrifeCommands.MODULE_SUBTREES.add(Spells.command());
        // M2：法术弹道系统（产物表 + cast 意图）与 combat 域实体（弹道/妖兽）。
        StrifeCombatEntities.ENTITY_TYPES.register(modEventBus);
        modEventBus.addListener(StrifeMonster::onAttributes);
        Spells.register();
    }
}

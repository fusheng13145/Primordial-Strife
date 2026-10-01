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
 * <p>本模块此前<b>一行代码都没有</b>（03 §1 的职责表与依赖断言里一直留着它的位置）。本入口是它的第一行：把功法倍率接进 四因子公式、把授功法的奖励缝补上、挂上 {@code
 * /strife technique} 命令子树。
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
    }
}

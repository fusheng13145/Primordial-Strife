package com.strife.production;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.strife.core.CultivationFactors;
import com.strife.core.StrifeCommands;
import com.strife.core.StrifeMod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * production 分侧入口（docs/03 §7 模块入口模式）。
 *
 * <p>本入口编排三件事：注册物品（材料与可服用的丹药）、把<b>丹药加成</b>接进 05 §2 的四因子公式 （{@link
 * CultivationFactors#registerPill}）、统一配方机第一类——炼丹（{@link Alchemy}：产物表 + {@code /strife craft pill}
 * 命令，docs/07 §7 M2）。炼器（artifacts 表空）随内容填充后接同一框架。
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
        Alchemy.register();
        StrifeCommands.MODULE_SUBTREES.add(craftCommand());
        StrifeCommands.MODULE_SUBTREES.add(Alchemy.recipeCommand());
        LOGGER.info("strife production wired cultivation factor: pill=consumable buffs");
    }

    /** {@code /strife craft pill <id>}：统一配方机的炼丹入口（兜底命令，03 §9）。 */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack>
            craftCommand() {
        return Commands.literal("craft")
                .then(
                        Commands.literal("pill")
                                .then(
                                        Commands.argument("pillId", StringArgumentType.word())
                                                .executes(
                                                        ctx -> {
                                                            var player =
                                                                    ctx.getSource()
                                                                            .getPlayerOrException();
                                                            Alchemy.craft(
                                                                    player,
                                                                    StringArgumentType.getString(
                                                                            ctx, "pillId"));
                                                            return 1;
                                                        })));
    }
}

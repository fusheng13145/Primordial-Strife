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
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * production 分侧入口（docs/03 §7 模块入口模式）。
 *
 * <p>本入口编排四件事：注册物品（材料与可服用的丹药）、把<b>丹药加成</b>接进 05 §2 的四因子公式 （{@link
 * CultivationFactors#registerPill}）、统一配方机第一类——炼丹（{@link Alchemy}）、第二类——炼器（{@link
 * ArtifactForge}）。两者共用材料计数与扣除口径，产物查询挂在同一 {@code /strife recipe} 根下。
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
        ArtifactTables.register();
        NeoForge.EVENT_BUS.addListener(ArtifactForge::onTooltip);
        StrifeCommands.MODULE_SUBTREES.add(craftCommand());
        // EP1 经济原语（ADR-032）：坊市一口价 + 灵石三档兑换。
        StrifeCommands.MODULE_SUBTREES.add(MarketCommands.subtree());
        // EP1（ADR-036 ②）：黑市——赃货折扣第二价目段，代价是因果与声望。
        StrifeCommands.MODULE_SUBTREES.add(BlackMarketCommands.subtree());
        StrifeCommands.MODULE_SUBTREES.add(Alchemy.recipeCommand());
        LOGGER.info("strife production wired cultivation factor: pill=consumable buffs");
    }

    /** {@code /strife craft pill|artifact <id>}：统一配方机的兜底命令（03 §9）。 */
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
                                                        })))
                .then(
                        Commands.literal("artifact")
                                .then(
                                        Commands.argument("artifactId", StringArgumentType.word())
                                                .executes(
                                                        ctx -> {
                                                            var player =
                                                                    ctx.getSource()
                                                                            .getPlayerOrException();
                                                            ArtifactForge.craft(
                                                                    player,
                                                                    StringArgumentType.getString(
                                                                            ctx, "artifactId"));
                                                            return 1;
                                                        })))
                .then(
                        Commands.literal("repair")
                                .executes(
                                        ctx -> {
                                            ArtifactRepair.repair(
                                                    ctx.getSource().getPlayerOrException());
                                            return 1;
                                        }));
    }
}

package com.strife.production;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.strife.core.CultivationFactors;
import com.strife.core.StrifeCommands;
import com.strife.core.StrifeMod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
        // EP1（ADR-038）：炼丹炉方块——站附近使用炼丹命令吃到阶位加成。
        StrifeAlchemyBlocks.BLOCKS.register(modEventBus);
        StrifeAlchemyBlocks.ITEMS.register(modEventBus);
        // 坊市看板批：告示牌（右键开看板）+ 锚点落位 + 看板意图。
        MarketBlocks.BLOCKS.register(modEventBus);
        MarketBlocks.ITEMS.register(modEventBus);
        MarketBoardPlacer.register();
        // 拍卖看板批：看板家族第三成员（文津渡锚点）+ 荷兰拍购买意图。
        AuctionBlocks.BLOCKS.register(modEventBus);
        AuctionBlocks.ITEMS.register(modEventBus);
        AuctionBoardPlacer.register();
        // 黑市看板批：看板家族第四成员（坊市暗柜）+ 赃货购买意图（代价即因果）。
        BlackMarketBlocks.BLOCKS.register(modEventBus);
        BlackMarketBlocks.ITEMS.register(modEventBus);
        BlackMarketPlacer.register();
        com.strife.core.net.StrifeIntents.register(
                com.strife.core.net.IntentRateLimiter.BLACKMARKET,
                invocation -> {
                    var args = invocation.args();
                    if (args == null) {
                        return;
                    }
                    String item = args.getString("item");
                    if (item.isEmpty()) {
                        return;
                    }
                    int count = Math.max(1, Math.min(64, args.getInt("count")));
                    BlackMarketCommands.buyFor(invocation.player(), item, count);
                });
        com.strife.core.net.StrifeIntents.register(
                com.strife.core.net.IntentRateLimiter.AUCTION,
                invocation -> {
                    var args = invocation.args();
                    if (args == null) {
                        return;
                    }
                    String action = args.getString("action");
                    if ("claim".equals(action)) {
                        String claimed = AuctionBidding.dispense(invocation.player());
                        if (claimed == null) {
                            invocation
                                    .player()
                                    .displayClientMessage(Component.literal("（暗箱里没有你的东西）"), true);
                        }
                        return;
                    }
                    String lot = args.getString("lot");
                    if ("bid".equals(action) && !lot.isEmpty()) {
                        // 密封投标（ADR-041）：金额全额托管由 AuctionBidding.bid 校验（底价/灵石/当前槽）
                        int amount = Math.max(1, args.getInt("amount"));
                        AuctionBidding.bid(invocation.player(), lot, amount);
                        return;
                    }
                    if (!"buy".equals(action) || lot.isEmpty()) {
                        return;
                    }
                    AuctionCommands.buyFor(invocation.player(), lot);
                });
        com.strife.core.net.StrifeIntents.register(
                com.strife.core.net.IntentRateLimiter.MARKET,
                invocation -> {
                    var args = invocation.args();
                    if (args == null) {
                        return;
                    }
                    String action = args.getString("action");
                    String item = args.getString("item");
                    int count = Math.max(1, Math.min(64, args.getInt("count")));
                    if (item.isEmpty() || action.isEmpty()) {
                        return;
                    }
                    ServerPlayer player = invocation.player();
                    if ("buy".equals(action)) {
                        MarketCommands.buyFor(player, item, count);
                    } else if ("sell".equals(action)) {
                        MarketCommands.sellFor(player, item, count);
                    }
                });
        CultivationFactors.registerPill(StrifePills::coefficient);
        Alchemy.register();
        ArtifactTables.register();
        NeoForge.EVENT_BUS.addListener(ArtifactForge::onTooltip);
        StrifeCommands.MODULE_SUBTREES.add(craftCommand());
        // EP1 经济原语（ADR-032）：坊市一口价 + 灵石三档兑换。
        StrifeCommands.MODULE_SUBTREES.add(MarketCommands.subtree());
        // EP1（ADR-036 ②）：黑市——赃货折扣第二价目段，代价是因果与声望。
        StrifeCommands.MODULE_SUBTREES.add(BlackMarketCommands.subtree());
        // EP1（ADR-037 ①）：拍卖行——荷兰式降价拍，价格确定性推导零持久化。
        StrifeCommands.MODULE_SUBTREES.add(AuctionCommands.subtree());
        StrifeCommands.MODULE_SUBTREES.add(Alchemy.recipeCommand());
        // EP1（ADR-038 交互层）：炉交互屏的炼制意图——与 /strife craft pill 同一业务口，同一份限速。
        com.strife.core.net.StrifeIntents.register(
                com.strife.core.net.IntentRateLimiter.CRAFT,
                invocation ->
                        Alchemy.craft(
                                invocation.player(),
                                invocation.args() == null
                                        ? ""
                                        : invocation
                                                .args()
                                                .getString(
                                                        com.strife.core.StrifeUiBridge.Keys.PILL)));
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

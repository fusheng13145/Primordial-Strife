package com.strife.production;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * production 侧看板方块注册（坊市看板批，2026-11-10）：告示牌是看板的物理入口， 与炼丹炉（{@link
 * StrifeAlchemyBlocks}）同为"方块即界面入口"的家族成员。
 */
public final class MarketBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, "strife");
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, "strife");

    /** 坊市告示牌：右键开看板。 */
    public static final DeferredHolder<Block, Block> MARKET_BOARD =
            BLOCKS.register(
                    "block_market_board",
                    () ->
                            new MarketBoardBlock(
                                    BlockBehaviour.Properties.of()
                                            .mapColor(MapColor.COLOR_ORANGE)
                                            .strength(2.0F, 4.0F)
                                            .sound(SoundType.BAMBOO_WOOD)
                                            .pushReaction(PushReaction.DESTROY)));

    static {
        ITEMS.register(
                "block_market_board",
                () -> new BlockItem(MARKET_BOARD.get(), new Item.Properties()));
    }

    private MarketBlocks() {}
}

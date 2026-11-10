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

/** production 侧拍卖看板方块注册（看板家族第三成员，2026-11-10）：与坊市告示牌同族， 挂在文津渡——拍卖行是渡口的生意。 */
public final class AuctionBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, "strife");
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, "strife");

    /** 拍卖看板：右键开拍卖屏。 */
    public static final DeferredHolder<Block, Block> AUCTION_BOARD =
            BLOCKS.register(
                    "block_auction_board",
                    () ->
                            new AuctionBoardBlock(
                                    BlockBehaviour.Properties.of()
                                            .mapColor(MapColor.COLOR_LIGHT_BLUE)
                                            .strength(2.0F, 4.0F)
                                            .sound(SoundType.BAMBOO_WOOD)
                                            .pushReaction(PushReaction.DESTROY)));

    static {
        ITEMS.register(
                "block_auction_board",
                () -> new BlockItem(AUCTION_BOARD.get(), new Item.Properties()));
    }

    private AuctionBlocks() {}
}

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

/** production 侧黑市看板方块注册（看板家族第四成员，2026-11-11）：暗柜色——灰紫、哑光、幽光烛。 */
public final class BlackMarketBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, "strife");
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, "strife");

    /** 黑市暗柜：右键开黑市看板。 */
    public static final DeferredHolder<Block, Block> BLACK_MARKET =
            BLOCKS.register(
                    "block_black_market",
                    () ->
                            new BlackMarketBlock(
                                    BlockBehaviour.Properties.of()
                                            .mapColor(MapColor.COLOR_LIGHT_GRAY)
                                            .strength(2.0F, 4.0F)
                                            .sound(SoundType.DEEPSLATE)
                                            .pushReaction(PushReaction.DESTROY)));

    static {
        ITEMS.register(
                "block_black_market",
                () -> new BlockItem(BLACK_MARKET.get(), new Item.Properties()));
    }

    private BlackMarketBlocks() {}
}

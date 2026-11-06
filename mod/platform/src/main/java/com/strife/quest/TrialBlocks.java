package com.strife.quest;

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
 * quest 侧方块注册（试炼台，2026-11-04）：心性试炼的物理锚点。
 *
 * <p>方块为什么放 quest：试炼台的右键语义是"开启心性试炼"——判定与推进全在 quest（SURVIVE 目标的唯一推进源就在 {@link TrialSessions}），放
 * world 会让 quest 的推进器被世界模块调用（03 §2 依赖方向反了）。
 */
public final class TrialBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, "strife");
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, "strife");

    /** 心性试炼台：右键开启 120 秒坚守。 */
    public static final DeferredHolder<Block, Block> TRIAL_ALTAR =
            BLOCKS.register(
                    "block_trial_altar",
                    () ->
                            new TrialAltarBlock(
                                    BlockBehaviour.Properties.of()
                                            .mapColor(MapColor.COLOR_PURPLE)
                                            .strength(3.0F, 6.0F)
                                            .sound(SoundType.DEEPSLATE)
                                            .pushReaction(PushReaction.BLOCK)));

    static {
        ITEMS.register(
                "block_trial_altar", () -> new BlockItem(TRIAL_ALTAR.get(), new Item.Properties()));
    }

    private TrialBlocks() {}
}

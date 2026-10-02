package com.strife.world;

import com.strife.core.StrifeMod;
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
 * 上界维度专属方块（M4 世界设计，ADR-021；03 §1 world 职责"上界维度"）。
 *
 * <p><b>灵气结晶 {@code block_qi_crystal}</b>：上界浮岛里的发光矿藏——它承担的是「一眼认出这是仙界」 的世界设计职责：青金晶面贴图 +
 * 自发光（lightEmission 10），长在 end_stone 浮岛里（{@code tables/realm_decor.csv} 生成链），材质属性不属于受管数值。发光上限取 10
 * 而非 15：要的是「幽幽灵光」 而不是火把级照明——亮度是观感设计，调它走 git 改代码，不走 NUMBERS（与矿石硬度同口径）。
 *
 * <p><b>为什么任意工具可挖</b>：结晶脆（AMETHYST 声），不走 {@code requiresCorrectToolForDrops()}——
 * 玩家初登上界空手就能采到第一份灵气资源，「采灵」是修仙世界的基础直觉；掉落自身（M4 简化，结晶 碎片化产物随 EP2 秘境排期）。爆炸会直接摧毁（{@code
 * PushReaction.DESTROY} 同矿石）。
 */
public final class StrifeRealmBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, StrifeMod.MOD_ID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, StrifeMod.MOD_ID);

    /** 结晶内容 ID（供测试与生成链对照：产物 configured 的 Name 必须等于 {@code "strife:" + 本值}）。 */
    static final String QI_CRYSTAL_ID = "block_qi_crystal";

    /** 灵气结晶：上界专属发光矿藏（M4 世界设计核心辨识物）。 */
    public static final DeferredHolder<Block, Block> QI_CRYSTAL =
            BLOCKS.register(
                    QI_CRYSTAL_ID,
                    () ->
                            new Block(
                                    BlockBehaviour.Properties.of()
                                            .mapColor(MapColor.COLOR_CYAN)
                                            .lightLevel(state -> 10)
                                            .strength(1.5F, 3.0F)
                                            .sound(SoundType.AMETHYST)
                                            .pushReaction(PushReaction.DESTROY)));

    static {
        ITEMS.register(QI_CRYSTAL_ID, () -> new BlockItem(QI_CRYSTAL.get(), new Item.Properties()));
    }

    private StrifeRealmBlocks() {}
}

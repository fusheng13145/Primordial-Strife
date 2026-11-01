package com.strife.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * 望仙台建筑件（P1-15）：9×9 开阔石台——上界浮岛上的可辨识地标。
 *
 * <p>形制：端石砖台面 + 四角柱（柱顶灵石灯）+ 中央仙坛（上供灵气结晶 {@link StrifeRealmBlocks#QI_CRYSTAL}）。
 * 不设墙不设顶——"台"是眺望上界的地方，封闭盒子上界感尽失。块材是材质属性（04 §7 / docs/10 §4.6），不进 NUMBERS。
 *
 * <p>地基下填 6 格端石支撑：浮岛边缘薄，防止 {@code beard_thin} 削坡后台子悬空。
 */
public final class WangxianTaiPiece extends StructurePiece {

    /** 半宽（台面 9×9）。 */
    private static final int HALF = 4;

    /** 柱顶灯的高度（台面以上）。 */
    private static final int HEIGHT = 4;

    /** 向下支撑深度（防悬空）。 */
    private static final int SUPPORT_DEPTH = 6;

    private final BlockPos origin;

    public WangxianTaiPiece(BlockPos origin) {
        super(
                SectStructures.WANGXIAN_TAI_PIECE.get(),
                0,
                new BoundingBox(
                        origin.getX() - HALF,
                        origin.getY() - SUPPORT_DEPTH,
                        origin.getZ() - HALF,
                        origin.getX() + HALF,
                        origin.getY() + HEIGHT,
                        origin.getZ() + HALF));
        this.origin = origin;
    }

    /** 存档反序列化入口（与 {@link SectHallPiece} 同款两参签名）。 */
    public WangxianTaiPiece(StructurePieceSerializationContext context, CompoundTag tag) {
        this(new BlockPos(tag.getInt("OX"), tag.getInt("OY"), tag.getInt("OZ")));
    }

    @Override
    protected void addAdditionalSaveData(
            StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("OX", origin.getX());
        tag.putInt("OY", origin.getY());
        tag.putInt("OZ", origin.getZ());
    }

    @Override
    public void postProcess(
            WorldGenLevel level,
            StructureManager manager,
            ChunkGenerator generator,
            RandomSource random,
            BoundingBox chunkBox,
            ChunkPos chunkPos,
            BlockPos reference) {
        BlockState brick = Blocks.END_STONE_BRICKS.defaultBlockState();
        BlockState endStone = Blocks.END_STONE.defaultBlockState();
        BlockState lamp = Blocks.GLOWSTONE.defaultBlockState();
        BlockState crystal = StrifeRealmBlocks.QI_CRYSTAL.get().defaultBlockState();

        int x0 = origin.getX() - HALF;
        int z0 = origin.getZ() - HALF;
        int x1 = origin.getX() + HALF;
        int z1 = origin.getZ() + HALF;
        int y = origin.getY();

        // 1) 台面 + 2) 防悬空支撑（四角 + 中心向下 6 格端石）
        generateBox(level, chunkBox, x0, y, z0, x1, y, z1, brick, brick, false);
        fillSupport(level, chunkBox, x0, z0, y, endStone);
        fillSupport(level, chunkBox, x1, z0, y, endStone);
        fillSupport(level, chunkBox, x0, z1, y, endStone);
        fillSupport(level, chunkBox, x1, z1, y, endStone);
        fillSupport(level, chunkBox, origin.getX(), origin.getZ(), y, endStone);

        // 3) 四角柱（1×1，y+1..y+3）+ 柱顶灵石灯（y+4）
        int[] corners = {x0, x1};
        for (int px : corners) {
            for (int pz : corners) {
                generateBox(level, chunkBox, px, y + 1, pz, px, y + 3, pz, brick, brick, false);
                placeBlock(level, lamp, px, y + HEIGHT, pz, chunkBox);
            }
        }

        // 4) 中央仙坛：一层端石砖 + 灵气结晶（登台即见的"望仙"信物）
        placeBlock(level, brick, origin.getX(), y + 1, origin.getZ(), chunkBox);
        placeBlock(level, crystal, origin.getX(), y + 2, origin.getZ(), chunkBox);
    }

    private void fillSupport(
            WorldGenLevel level,
            BoundingBox chunkBox,
            int x,
            int z,
            int surfaceY,
            BlockState fill) {
        generateBox(
                level,
                chunkBox,
                x,
                surfaceY - SUPPORT_DEPTH,
                z,
                x,
                surfaceY - 1,
                z,
                fill,
                fill,
                false);
    }

    @Override
    public BlockPos getLocatorPosition() {
        return new BlockPos(origin.getX(), origin.getY(), origin.getZ());
    }

    /** 仅测试可见：建筑件原点。 */
    BlockPos origin() {
        return origin;
    }

    /** 仅测试可见：望仙台设计尺寸（半宽、柱高、支撑深度）。 */
    static int[] dimensions() {
        return new int[] {HALF, HEIGHT, SUPPORT_DEPTH};
    }
}

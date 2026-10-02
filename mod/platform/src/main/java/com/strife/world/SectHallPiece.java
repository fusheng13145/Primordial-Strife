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
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * 宗门厅堂的单个建筑件（ADR-022 code 路线：几何由代码现场生成，无 {@code .nbt}）。
 *
 * <p><b>本期形态</b>（规则化厅堂骨架，13×13 平面 / 高 8）：石砖平台地基 → 四角石柱 → 带门洞的墙体 → 外扩屋檐 + 平顶 → 内部灵石台与四盏灯 →
 * 门洞上方一块灵气结晶作"宗门灵石匾"。 形态属真相源 {@code LORE.md} §区域卡（待 C），本期只把<b>机制</b>立起来；后续换 jigsaw + 资产时 结构 key
 * 不变，存档兼容。
 *
 * <p><b>为什么要地基支撑柱</b>：{@code terrain_adaptation: beard_thin} 只在建筑正下方补很薄的一层，
 * 落在斜坡或水边时厅堂仍会悬空——这里显式往地下填 6 格石柱（四角 + 中心），把"浮空宗门"这种 一眼可见的破损堵在生成期。
 *
 * <p><b>为什么全用原版方块</b>：宗门主题资产（灵石灯/匾额/宗门纹样）属 04 §7 美术管线，本期只有 {@code block_qi_crystal}
 * 一个自研方块可用，其余用原版石砖/深色橡木/荧石，先保证"有建筑"这个事实 成立，不把 M4 收尾押在美术产出上。
 */
public final class SectHallPiece extends StructurePiece {

    /** 半宽与半深（厅堂 13×13）。 */
    private static final int HALF = 6;

    /** 地面以上高度。 */
    private static final int HEIGHT = 8;

    /** 向下支撑深度（防悬空）。 */
    private static final int SUPPORT_DEPTH = 6;

    private final BlockPos origin;

    public SectHallPiece(BlockPos origin) {
        super(
                SectStructures.SECT_HALL_PIECE.get(),
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

    /** 存档反序列化入口（{@code StructurePieceType} 的函数式签名就是这两参，1.21.1 实证）。 */
    public SectHallPiece(StructurePieceSerializationContext context, CompoundTag tag) {
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
        BlockState brick = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState plank = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        BlockState log = Blocks.DARK_OAK_LOG.defaultBlockState();
        BlockState lamp = Blocks.GLOWSTONE.defaultBlockState();
        BlockState plaque = StrifeRealmBlocks.QI_CRYSTAL.get().defaultBlockState();

        int x0 = origin.getX() - HALF;
        int z0 = origin.getZ() - HALF;
        int x1 = origin.getX() + HALF;
        int z1 = origin.getZ() + HALF;
        int y = origin.getY();

        // 1) 地基平台
        generateBox(level, chunkBox, x0, y, z0, x1, y, z1, brick, brick, false);
        // 2) 防悬空支撑：四角 + 中心向下填石
        fillSupport(level, chunkBox, x0, z0, y, stone);
        fillSupport(level, chunkBox, x1, z0, y, stone);
        fillSupport(level, chunkBox, x0, z1, y, stone);
        fillSupport(level, chunkBox, x1, z1, y, stone);
        fillSupport(level, chunkBox, origin.getX(), origin.getZ(), y, stone);

        // 3) 四角柱（2×2，y+1..y+6）
        int[] corners = {x0, x1 - 1};
        for (int px : corners) {
            for (int pz : corners) {
                generateBox(
                        level, chunkBox, px, y + 1, pz, px + 1, y + 6, pz + 1, brick, brick, false);
            }
        }

        // 4) 墙体（y+1..y+5），南面留 3 宽门洞（y+1..y+3）
        generateBox(level, chunkBox, x0, y + 1, z0, x1, y + 5, z0, brick, brick, false); // 北墙
        generateBox(level, chunkBox, x0, y + 1, z1, x1, y + 5, z1, brick, brick, false); // 南墙
        generateBox(level, chunkBox, x0, y + 1, z0, x0, y + 5, z1, brick, brick, false); // 西墙
        generateBox(level, chunkBox, x1, y + 1, z0, x1, y + 5, z1, brick, brick, false); // 东墙
        // 门洞：南墙中段 3 宽 × 3 高挖空
        generateBox(
                level,
                chunkBox,
                origin.getX() - 1,
                y + 1,
                z1,
                origin.getX() + 1,
                y + 3,
                z1,
                Blocks.AIR.defaultBlockState(),
                Blocks.AIR.defaultBlockState(),
                false);

        // 5) 屋檐（外扩一圈 y+6）+ 平顶（y+7）
        generateBox(
                level, chunkBox, x0 - 1, y + 6, z0 - 1, x1 + 1, y + 6, z1 + 1, plank, plank, false);
        generateBox(level, chunkBox, x0, y + 7, z0, x1, y + 7, z1, plank, plank, false);
        // 屋脊（南北向中线用原木）
        generateBox(
                level,
                chunkBox,
                origin.getX(),
                y + 8,
                z0,
                origin.getX(),
                y + 8,
                z1,
                log,
                log,
                false);

        // 6) 内部：中央灵石台 + 四角灯
        generateBox(
                level,
                chunkBox,
                origin.getX(),
                y + 1,
                origin.getZ(),
                origin.getX(),
                y + 1,
                origin.getZ(),
                plaque,
                plaque,
                false);
        generateBox(
                level, chunkBox, x0 + 2, y + 1, z0 + 2, x0 + 2, y + 1, z0 + 2, lamp, lamp, false);
        generateBox(
                level, chunkBox, x1 - 2, y + 1, z0 + 2, x1 - 2, y + 1, z0 + 2, lamp, lamp, false);
        generateBox(
                level, chunkBox, x0 + 2, y + 1, z1 - 2, x0 + 2, y + 1, z1 - 2, lamp, lamp, false);
        generateBox(
                level, chunkBox, x1 - 2, y + 1, z1 - 2, x1 - 2, y + 1, z1 - 2, lamp, lamp, false);

        // 7) 门匾：门洞上方一块灵气结晶
        placeBlock(level, plaque, origin.getX(), y + 4, z1, chunkBox);
    }

    private void fillSupport(
            WorldGenLevel level,
            BoundingBox chunkBox,
            int x,
            int z,
            int surfaceY,
            BlockState stone) {
        generateBox(
                level,
                chunkBox,
                x,
                surfaceY - SUPPORT_DEPTH,
                z,
                x,
                surfaceY - 1,
                z,
                stone,
                stone,
                false);
    }

    @Override
    public BlockPos getLocatorPosition() {
        return new BlockPos(origin.getX(), origin.getY(), origin.getZ());
    }

    /** 结构件类型由 {@link SectStructures} 注册，本类只实现反序列化入口。 */
    static StructurePieceType type() {
        return SectStructures.SECT_HALL_PIECE.get();
    }

    /** 仅测试可见：建筑件原点（几何的世界坐标落点）。 */
    BlockPos origin() {
        return origin;
    }

    /** 仅测试可见：厅堂设计尺寸（半宽、层高、支撑深度）。 */
    static int[] dimensions() {
        return new int[] {HALF, HEIGHT, SUPPORT_DEPTH};
    }
}

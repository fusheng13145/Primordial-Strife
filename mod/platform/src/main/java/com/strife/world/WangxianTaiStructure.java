package com.strife.world;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

/**
 * 上界望仙台（P1-15，G-7 上界维度的"可探索点"）：与 {@link SectHallStructure} 同机制（ADR-022 code 路线）， 几何由代码生成，无 {@code
 * .nbt}。
 *
 * <p>群系来自产物 {@code worldgen/structure/wangxian_tai.json}：{@code minecraft:the_end}——上界维度 {@code
 * strife:upper_realm} 借用的正是该群系（ADR-021），因此结构在原版末地同样可生成；这是"不自研上界群系" 阶段的已知取舍（见 structures.csv
 * 行注），自研群系落地后收紧。
 */
public final class WangxianTaiStructure extends Structure {

    /** 结构 codec：与宗门同款 {@code simpleCodec}，无额外字段（布局参数在 structure_set 产物里）。 */
    public static final MapCodec<WangxianTaiStructure> CODEC =
            simpleCodec(WangxianTaiStructure::new);

    /** 默认 settings（供注册表条目；运行时真值由 datapack 产物经 codec 覆盖，群系列表留空）。 */
    static StructureSettings settings() {
        return new StructureSettings(HolderSet.direct());
    }

    public WangxianTaiStructure(StructureSettings settings) {
        super(settings);
    }

    /** 生成点：浮岛地表（WORLD_SURFACE_WG），与宗门同口径——贴地表而非固定高度。 */
    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        return onTopOfChunkCenter(
                context,
                Heightmap.Types.WORLD_SURFACE_WG,
                (StructurePiecesBuilder builder) -> generate(builder, context));
    }

    private void generate(StructurePiecesBuilder builder, GenerationContext context) {
        ChunkPos chunkPos = context.chunkPos();
        int x = chunkPos.getMiddleBlockX();
        int z = chunkPos.getMiddleBlockZ();
        int surfaceY =
                context.chunkGenerator()
                        .getFirstOccupiedHeight(
                                x,
                                z,
                                Heightmap.Types.WORLD_SURFACE_WG,
                                context.heightAccessor(),
                                context.randomState());
        builder.addPiece(new WangxianTaiPiece(new BlockPos(x, surfaceY, z)));
    }

    @Override
    public StructureType<?> type() {
        return SectStructures.WANGXIAN_TAI_TYPE.get();
    }
}

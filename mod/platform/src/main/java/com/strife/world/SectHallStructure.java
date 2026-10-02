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
 * 宗门厅堂结构（ADR-022 code 路线，原版 {@code ruined_portal} 同机制）。
 *
 * <p><b>为什么 code 式而不是 jigsaw</b>：jigsaw 要 {@code .nbt} 模板，而导出链（WorldEdit schematic →
 * StructureBlock）在本机<b>未验证</b>；code 式零资产依赖，且宗门布局是<b>规则化</b>的（轴对称的厅堂骨架）， 代码表达比模板更可控。代价是外观朴素——形态属真相源
 * {@code LORE.md} §区域卡，待 C 补齐后 可整体替换为 jigsaw + 资产（结构 key 不变，存档兼容）。
 *
 * <p><b>配置为什么空</b>：群系列表 / step / terrain_adaptation 全由 {@code structure} 产物 JSON 决定 （{@link
 * Structure#simpleCodec} 负责解析），本类<b>不再写死任何群系</b>——写成代码常量就会出现 "改了表但结构还长在老地方"的双真相源漂移。这一条是 {@code
 * OreGenerator} 以来的同一纪律。
 */
public final class SectHallStructure extends Structure {

    /**
     * 结构 codec：{@code simpleCodec} 返回 {@code MapCodec}（1.21.1 实证），它把产物 JSON 里的 {@code biomes} /
     * {@code step} / {@code spawn_overrides} 解析进 {@link StructureSettings}。
     * 本期<b>没有额外字段</b>——布局参数（spacing/separation/salt）在 structure_set 产物里。
     */
    public static final MapCodec<SectHallStructure> CODEC = simpleCodec(SectHallStructure::new);

    /** 默认 settings（供注册表条目；运行时真值由 datapack 产物经 codec 覆盖，故群系列表留空）。 */
    static StructureSettings settings() {
        return new StructureSettings(HolderSet.direct());
    }

    public SectHallStructure(StructureSettings settings) {
        super(settings);
    }

    /**
     * 生成点：取区块中心、贴地形表面（WORLD_SURFACE_WG）。
     *
     * <p>为什么贴地表而不是固定高度：宗门是地表建筑，固定高度会埋进山里或浮在半空； {@code terrain_adaptation:
     * beard_thin}（表里配的）再替我们削平地形边缘。
     */
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
        // 出生高度用结构自己的 heightmap 查询，避免与 terrain_adaptation 的取值口径不一致。
        int surfaceY =
                context.chunkGenerator()
                        .getFirstOccupiedHeight(
                                x,
                                z,
                                Heightmap.Types.WORLD_SURFACE_WG,
                                context.heightAccessor(),
                                context.randomState());
        BlockPos origin = new BlockPos(x, surfaceY, z);
        builder.addPiece(new SectHallPiece(origin));
    }

    @Override
    public StructureType<?> type() {
        return SectStructures.SECT_HALL_TYPE.get();
    }
}

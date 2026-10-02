package com.strife.world;

import com.strife.core.StrifeMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * M4 宗门结构的注册（JSON_SCHEMA §4.13，ADR-022 code 路线）。
 *
 * <p><b>为什么三个注册表</b>：原版结构在三张注册表上各有一个条目——
 *
 * <ul>
 *   <li>{@code STRUCTURES}：结构本体（{@code data/strife/worldgen/structure/*.json} 的运行时对应物）， {@code
 *       type} 字段按 key 找到这里；
 *   <li>{@code STRUCTURE_TYPES}：<b>codec</b>。原版用它把 JSON 字段（biomes / step / spawn_overrides） 解析进
 *       {@code StructureSettings}——也就是说群系列表是<b>数据说了算</b>，代码里不写死"宗门长在草原" （否则改表不改代码就漂移）；
 *   <li>{@code PIECES}：建筑件的存档类型（结构块要能序列化到存档里）。
 * </ul>
 *
 * <p>三者缺一，游戏内表现都是"结构列表里有名字但永远找不到"——静默断链，与矿石/结晶同构。
 */
public final class SectStructures {

    public static final DeferredRegister<Structure> STRUCTURES =
            DeferredRegister.create(Registries.STRUCTURE, StrifeMod.MOD_ID);

    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, StrifeMod.MOD_ID);

    public static final DeferredRegister<StructurePieceType> PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, StrifeMod.MOD_ID);

    /** 宗门厅堂：内容 ID 与产物 {@code worldgen/structure/sect_hall.json} 同 ID（ADR-017）。 */
    public static final String SECT_HALL_ID = "sect_hall";

    public static final DeferredHolder<Structure, SectHallStructure> SECT_HALL =
            STRUCTURES.register(
                    SECT_HALL_ID, () -> new SectHallStructure(SectHallStructure.settings()));

    public static final DeferredHolder<StructureType<?>, StructureType<SectHallStructure>>
            SECT_HALL_TYPE =
                    STRUCTURE_TYPES.register(SECT_HALL_ID, () -> () -> SectHallStructure.CODEC);

    public static final DeferredHolder<StructurePieceType, StructurePieceType> SECT_HALL_PIECE =
            PIECES.register(SECT_HALL_ID, () -> SectHallPiece::new);

    private SectStructures() {}
}

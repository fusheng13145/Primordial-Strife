package com.strife.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;

/**
 * 玩家侧全部 strife 数据的聚合对象（03 分册 §3）。
 *
 * <p>存档口径：
 *
 * <ul>
 *   <li>{@link #CURRENT_DATA_VERSION} 是当前写入版本；<b>新增字段必须在 {@link #CODEC} 里带默认值</b>，
 *       删除字段保留读取兼容一个主版本；破档 = 主版本号 +1。
 *   <li>枚举一律以 ordinal 存储（配合数据版本迁移）；可由 NUMBERS.md 查表还原的派生值（如当前修炼速率） 一律不入档。
 *   <li>布尔标记用 {@code long} bitset，不用集合；字符串字段只存内容 ID。
 * </ul>
 *
 * <p>core 只存原始整数与位域，<b>不认识境界枚举</b>——境界/灵根品阶的语义在 realm 包， 依赖方向 realm → core 由 {@code checkImports}
 * 强制（03 分册 §2）。
 *
 * @param dataVersion 写入时的数据版本
 * @param realmOrdinal realm 包境界枚举的 ordinal，0 = 凡人
 * @param stage 小境界序号（练气 1–9 层 / 筑基 初–圆满），1 起
 * @param qi 当前修为，上限由 realm 侧查表（不在此表内）
 * @param lifespanTicks 剩余寿元，以游戏刻计；换算率由 realm 侧查表（1 修行年 = 24000 刻）
 * @param flags 玩家级标记位域（H3 因果/flag 的玩家侧入口）
 * @param spiritrootQuality 灵根品阶序号，对应 NUMBERS.md §6 的 quality_tier_1..4
 * @param spiritrootElements 五行位掩码：bit0 金 bit1 木 bit2 水 bit3 火 bit4 土
 * @param breakthroughAttempts 当前境界已失败次数，用于成功率累计修正（05 分册 §3）
 * @param affiliation 所属势力内容 ID，空串 = 散修（H2）
 * @param reputation 势力声望向量 factionId → [-100,100]（H2）
 */
public record StrifeData(
        int dataVersion,
        int realmOrdinal,
        int stage,
        int qi,
        long lifespanTicks,
        long flags,
        int spiritrootQuality,
        int spiritrootElements,
        int breakthroughAttempts,
        String affiliation,
        Map<String, Integer> reputation) {

    public static final int CURRENT_DATA_VERSION = 1;

    /** 一年 = 24000 游戏刻（20 分钟现实时间，与 NUMBERS.md §4 的流速一致）。 */
    public static final long TICKS_PER_YEAR = 24000L;

    public static final Codec<StrifeData> CODEC =
            RecordCodecBuilder.create(
                    instance ->
                            instance.group(
                                            Codec.INT
                                                    .fieldOf("data_version")
                                                    .orElse(CURRENT_DATA_VERSION)
                                                    .forGetter(StrifeData::dataVersion),
                                            Codec.INT
                                                    .fieldOf("realm")
                                                    .orElse(0)
                                                    .forGetter(StrifeData::realmOrdinal),
                                            Codec.INT
                                                    .fieldOf("stage")
                                                    .orElse(1)
                                                    .forGetter(StrifeData::stage),
                                            Codec.INT
                                                    .fieldOf("qi")
                                                    .orElse(0)
                                                    .forGetter(StrifeData::qi),
                                            Codec.LONG
                                                    .fieldOf("lifespan_ticks")
                                                    .orElse(0L)
                                                    .forGetter(StrifeData::lifespanTicks),
                                            Codec.LONG
                                                    .fieldOf("flags")
                                                    .orElse(0L)
                                                    .forGetter(StrifeData::flags),
                                            Codec.INT
                                                    .fieldOf("spiritroot_quality")
                                                    .orElse(0)
                                                    .forGetter(StrifeData::spiritrootQuality),
                                            Codec.INT
                                                    .fieldOf("spiritroot_elements")
                                                    .orElse(0)
                                                    .forGetter(StrifeData::spiritrootElements),
                                            Codec.INT
                                                    .fieldOf("breakthrough_attempts")
                                                    .orElse(0)
                                                    .forGetter(StrifeData::breakthroughAttempts),
                                            Codec.STRING
                                                    .fieldOf("affiliation")
                                                    .orElse("")
                                                    .forGetter(StrifeData::affiliation),
                                            Codec.unboundedMap(Codec.STRING, Codec.INT)
                                                    .fieldOf("reputation")
                                                    .orElse(Map.of())
                                                    .forGetter(StrifeData::reputation))
                                    .apply(instance, StrifeData::new));

    /** 新玩家默认值：凡人、寿元未初始化（0 表示 realm 侧尚未结算，面板需显示"未知"而非 0 岁）。 */
    public static StrifeData newPlayer() {
        return new StrifeData(CURRENT_DATA_VERSION, 0, 1, 0, 0L, 0L, 0, 0, 0, "", Map.of());
    }

    /** 该数据的可变性由调用方负责：附件值对象一旦写入即视为不可变，改字段须整体 setData 回写。 */
    public StrifeData withQi(int qiValue) {
        return new StrifeData(
                dataVersion,
                realmOrdinal,
                stage,
                qiValue,
                lifespanTicks,
                flags,
                spiritrootQuality,
                spiritrootElements,
                breakthroughAttempts,
                affiliation,
                reputation);
    }
}

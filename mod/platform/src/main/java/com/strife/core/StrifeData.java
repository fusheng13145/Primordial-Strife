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
 * @param meditation 打坐会话状态（03 §3 时间片结算的时间戳载体）
 * @param pills 丹药增益状态（NUMBERS §7：在效增益与本境界丹药延寿记账）
 * @param techniques 功法状态（03 §1 combat 职责：已学清单 + 当前装备）
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
        Map<String, Integer> reputation,
        MeditationState meditation,
        PillState pills,
        TechniqueState techniques) {

    /**
     * 兼容构造：不关心打坐/丹药状态的调用点（任务奖励、灵根生成等）默认取两条空状态。
     *
     * <p>刻意保留这个重载而不是让所有调用点补两个参数：这两块状态各有单一归属（realm 与 production），让它们渗透到每个 构造点只会增加"某处顺手重置了状态"的机会。
     */
    public StrifeData(
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
        this(
                dataVersion,
                realmOrdinal,
                stage,
                qi,
                lifespanTicks,
                flags,
                spiritrootQuality,
                spiritrootElements,
                breakthroughAttempts,
                affiliation,
                reputation,
                MeditationState.IDLE,
                PillState.EMPTY,
                TechniqueState.EMPTY);
    }

    public static final int CURRENT_DATA_VERSION = 1;

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
                                                    .forGetter(StrifeData::reputation),
                                            MeditationState.CODEC
                                                    .fieldOf("meditation")
                                                    .orElse(MeditationState.IDLE)
                                                    .forGetter(StrifeData::meditation),
                                            PillState.CODEC
                                                    .fieldOf("pills")
                                                    .orElse(PillState.EMPTY)
                                                    .forGetter(StrifeData::pills),
                                            TechniqueState.CODEC
                                                    .fieldOf("techniques")
                                                    .orElse(TechniqueState.EMPTY)
                                                    .forGetter(StrifeData::techniques))
                                    .apply(instance, StrifeData::new));

    /** 新玩家默认值：凡人、寿元未初始化（0 表示 realm 侧尚未结算，面板需显示"未知"而非 0 岁）。 */
    public static StrifeData newPlayer() {
        return new StrifeData(
                CURRENT_DATA_VERSION,
                0,
                1,
                0,
                0L,
                0L,
                0,
                0,
                0,
                "",
                Map.of(),
                MeditationState.IDLE,
                PillState.EMPTY,
                TechniqueState.EMPTY);
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
                reputation,
                meditation,
                pills,
                techniques);
    }

    /** 整体替换打坐状态（时间片结算与打断的唯一入口，避免调用点各自拼字段）。 */
    public StrifeData withMeditation(MeditationState updated) {
        return new StrifeData(
                dataVersion,
                realmOrdinal,
                stage,
                qi,
                lifespanTicks,
                flags,
                spiritrootQuality,
                spiritrootElements,
                breakthroughAttempts,
                affiliation,
                reputation,
                updated,
                pills,
                techniques);
    }

    /** 累计失败次数（突破失败 +1、成功清零，05 §3 的 fail_step 依据）。 */
    public StrifeData withAttempts(int attempts) {
        return new StrifeData(
                dataVersion,
                realmOrdinal,
                stage,
                qi,
                lifespanTicks,
                flags,
                spiritrootQuality,
                spiritrootElements,
                Math.max(0, attempts),
                affiliation,
                reputation,
                meditation,
                pills,
                techniques);
    }

    /** 整体替换丹药增益状态（production 的丹药路径唯一入口）。 */
    public StrifeData withPills(PillState updated) {
        return new StrifeData(
                dataVersion,
                realmOrdinal,
                stage,
                qi,
                lifespanTicks,
                flags,
                spiritrootQuality,
                spiritrootElements,
                breakthroughAttempts,
                affiliation,
                reputation,
                meditation,
                updated,
                techniques);
    }

    /** 改寿元（丹药延长与境界续命共用；调用方负责范围校验）。 */
    public StrifeData withLifespanTicks(long updated) {
        return new StrifeData(
                dataVersion,
                realmOrdinal,
                stage,
                qi,
                updated,
                flags,
                spiritrootQuality,
                spiritrootElements,
                breakthroughAttempts,
                affiliation,
                reputation,
                meditation,
                pills,
                techniques);
    }

    /**
     * 整块境界变更（突破成功 / 大限跌落共用）：境界、小境界、修为、寿元、失败计数一并替换。
     *
     * <p>这五项必须一起改才自洽——只改境界不改寿元会留下"化神境界配凡人寿元"这类状态；它们分散在几个 wither 里时， 调用点很容易漏掉一个。
     */
    public StrifeData withRealm(
            int newRealmOrdinal, int newStage, int newQi, long newLifespanTicks, int newAttempts) {
        return new StrifeData(
                dataVersion,
                newRealmOrdinal,
                newStage,
                newQi,
                newLifespanTicks,
                flags,
                spiritrootQuality,
                spiritrootElements,
                Math.max(0, newAttempts),
                affiliation,
                reputation,
                meditation,
                pills,
                techniques);
    }

    /** 整体替换功法状态（combat 的学法/装备路径唯一入口）。 */
    public StrifeData withTechniques(TechniqueState updated) {
        return new StrifeData(
                dataVersion,
                realmOrdinal,
                stage,
                qi,
                lifespanTicks,
                flags,
                spiritrootQuality,
                spiritrootElements,
                breakthroughAttempts,
                affiliation,
                reputation,
                meditation,
                pills,
                updated);
    }
}

package com.strife.core.net;

import com.strife.core.StrifeData;

/**
 * 需要同步到客户端的 {@link StrifeData} 字段清单（docs/03 §5 delta 更新）。
 *
 * <p>顺序即 {@link StrifeDelta} 的位序与槽位下标：<b>只能追加，不得插入或重排</b>——位序是网络 契约，改动 =
 * 协议破档，客户端与服务端版本不一致时会解出错值。新增字段时同时改 {@link #read(StrifeData)} 并在这里追加。
 *
 * <p>{@code dataVersion} 刻意不在清单内：它在一次会话内是常量，随全量快照走一次即可，进 delta 是浪费位。
 *
 * <p>两类字段的承载方式不同：{@link #scalar()} 为真的字段走 {@code long} 槽位（含 int 字段，统一升位）， {@link #AFFILIATION} 与
 * {@link #REPUTATION} 是变长的字符串/映射，走 delta 的独立槽位。
 */
public enum StrifeSyncField {
    REALM_ORDINAL,
    STAGE,
    QI,
    LIFESPAN_TICKS,
    FLAGS,
    SPIRITROOT_QUALITY,
    SPIRITROOT_ELEMENTS,
    BREAKTHROUGH_ATTEMPTS,
    AFFILIATION,
    REPUTATION,
    MEDITATION_START_TICK,
    MEDITATION_CREDITED_QI,
    MEDITATION_COOLDOWN_UNTIL_TICK;

    /** 字段总数；掩码用 long，故上限 64（03 §3 flags 用 bitset 的同一取舍）。 */
    public static final int COUNT = 13;

    public static final long ALL_BITS = (1L << COUNT) - 1;

    /** 该字段在掩码中的位。 */
    public long bit() {
        return 1L << ordinal();
    }

    /** 标量字段（可在 {@code long[]} 槽位里承载）；变长字段为 false。 */
    public boolean scalar() {
        return this != AFFILIATION && this != REPUTATION;
    }

    /**
     * 读字段为 {@code long}。变长字段（{@link #AFFILIATION} / {@link #REPUTATION}）没有标量视图，返回 0——它们的值走 delta
     * 的独立槽位，调用方必须先查 {@link #scalar()}。
     */
    public long read(StrifeData data) {
        return switch (this) {
            case REALM_ORDINAL -> data.realmOrdinal();
            case STAGE -> data.stage();
            case QI -> data.qi();
            case LIFESPAN_TICKS -> data.lifespanTicks();
            case FLAGS -> data.flags();
            case SPIRITROOT_QUALITY -> data.spiritrootQuality();
            case SPIRITROOT_ELEMENTS -> data.spiritrootElements();
            case BREAKTHROUGH_ATTEMPTS -> data.breakthroughAttempts();
            case MEDITATION_START_TICK -> data.meditation().startTick();
            case MEDITATION_CREDITED_QI -> data.meditation().creditedQi();
            case MEDITATION_COOLDOWN_UNTIL_TICK -> data.meditation().cooldownUntilTick();
            case AFFILIATION, REPUTATION -> 0L;
        };
    }
}

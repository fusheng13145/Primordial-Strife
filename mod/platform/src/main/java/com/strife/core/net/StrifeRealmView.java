package com.strife.core.net;

import net.minecraft.nbt.CompoundTag;

/**
 * 面板视图（S2C 协议 DTO）：服务端算好的"玩家当前修炼处境"，供 client_fx 直接显示。
 *
 * <p>为什么放在 core 而不是 realm：它是 <b>S2C 协议的一部分</b>，而 03 §2 规定 client_fx 只允许依赖 core。core 不解释 这些字段的含义（和
 * {@code StrifeData} 一样，core 只承载、realm 生产、client_fx 消费），因此这里只有数据形状与编解码， 没有任何 realm 语义的计算。
 *
 * <p>为什么用"拉取式"而不是把派生值塞进同步包：05 §2 与 05 §4 要求面板能解释速率构成、显示本次成功率与失败后果——这些都是 <b>派生值</b>，03 §3
 * 明令派生值不入档。让服务端按需算一份视图发过来，客户端就不必复制 realm 的表与公式（复制出来的那份 一定会与真相源漂移），并且天然满足"服务端权威"。
 *
 * @param realmId 当前境界内容 ID（客户端拼 lang key）
 * @param realmOrdinal 当前境界序号
 * @param stage 小境界序号（1 起）
 * @param stageCount 当前境界的小境界总数
 * @param qi 当前修为
 * @param qiMax 当前境界修为上限
 * @param finalRealm 是否已是最末境界（决定"能否突破"的解释文案）
 * @param successRate 本次突破成功率（已含失败累计修正，05 §3）
 * @param attempts 当前境界累计失败次数
 * @param qiResetRatioMin 失败回退比例下限（05 §4 要求面板预告失败后果）
 * @param qiResetRatioMax 失败回退比例上限
 * @param meditating 是否正在打坐
 * @param cooldownSeconds 打断后剩余冷却秒数（0 = 无冷却）
 * @param qiPerSecond 当前有效打坐速率（修为/秒，四因子乘积的结果；面板据此解释"为何这么慢/这么快"）
 * @param lifespanYears 剩余寿元年数；负数 = 换算率不可用
 */
public record StrifeRealmView(
        String realmId,
        int realmOrdinal,
        int stage,
        int stageCount,
        int qi,
        int qiMax,
        boolean finalRealm,
        double successRate,
        int attempts,
        double qiResetRatioMin,
        double qiResetRatioMax,
        boolean meditating,
        int cooldownSeconds,
        double qiPerSecond,
        long lifespanYears) {

    private static final String KEY_REALM_ID = "realm_id";
    private static final String KEY_REALM_ORDINAL = "realm_ordinal";
    private static final String KEY_STAGE = "stage";
    private static final String KEY_STAGE_COUNT = "stage_count";
    private static final String KEY_QI = "qi";
    private static final String KEY_QI_MAX = "qi_max";
    private static final String KEY_FINAL_REALM = "final_realm";
    private static final String KEY_SUCCESS_RATE = "success_rate";
    private static final String KEY_ATTEMPTS = "attempts";
    private static final String KEY_RESET_MIN = "qi_reset_min";
    private static final String KEY_RESET_MAX = "qi_reset_max";
    private static final String KEY_MEDITATING = "meditating";
    private static final String KEY_COOLDOWN_SECONDS = "cooldown_seconds";
    private static final String KEY_QI_PER_SECOND = "qi_per_second";
    private static final String KEY_LIFESPAN_YEARS = "lifespan_years";

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString(KEY_REALM_ID, realmId);
        tag.putInt(KEY_REALM_ORDINAL, realmOrdinal);
        tag.putInt(KEY_STAGE, stage);
        tag.putInt(KEY_STAGE_COUNT, stageCount);
        tag.putInt(KEY_QI, qi);
        tag.putInt(KEY_QI_MAX, qiMax);
        tag.putBoolean(KEY_FINAL_REALM, finalRealm);
        tag.putDouble(KEY_SUCCESS_RATE, successRate);
        tag.putInt(KEY_ATTEMPTS, attempts);
        tag.putDouble(KEY_RESET_MIN, qiResetRatioMin);
        tag.putDouble(KEY_RESET_MAX, qiResetRatioMax);
        tag.putBoolean(KEY_MEDITATING, meditating);
        tag.putInt(KEY_COOLDOWN_SECONDS, cooldownSeconds);
        tag.putDouble(KEY_QI_PER_SECOND, qiPerSecond);
        tag.putLong(KEY_LIFESPAN_YEARS, lifespanYears);
        return tag;
    }

    public static StrifeRealmView fromTag(CompoundTag tag) {
        return new StrifeRealmView(
                tag.getString(KEY_REALM_ID),
                tag.getInt(KEY_REALM_ORDINAL),
                tag.getInt(KEY_STAGE),
                tag.getInt(KEY_STAGE_COUNT),
                tag.getInt(KEY_QI),
                tag.getInt(KEY_QI_MAX),
                tag.getBoolean(KEY_FINAL_REALM),
                tag.getDouble(KEY_SUCCESS_RATE),
                tag.getInt(KEY_ATTEMPTS),
                tag.getDouble(KEY_RESET_MIN),
                tag.getDouble(KEY_RESET_MAX),
                tag.getBoolean(KEY_MEDITATING),
                tag.getInt(KEY_COOLDOWN_SECONDS),
                tag.getDouble(KEY_QI_PER_SECOND),
                tag.getLong(KEY_LIFESPAN_YEARS));
    }

    /** 修为是否已满（可押注的前置条件之一）。 */
    public boolean qiFull() {
        return qi >= qiMax;
    }

    /** 现在能不能尝试突破（面板据此置灰按钮/提示原因）。 */
    public boolean canBreakthrough() {
        return qiFull() && !finalRealm;
    }
}

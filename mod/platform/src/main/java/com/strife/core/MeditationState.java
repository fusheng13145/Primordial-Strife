package com.strife.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * 打坐会话状态（docs/03 §3"打坐累计采用时间片结算：记录起止时间戳 + 环境快照"）。
 *
 * <p>为什么三个字段一起存成一条记录：它们只在"开始/结算/打断"三处整体变化，拆成独立分量会让每个调用点都要重复摆弄三个 参数，任何一处漏改都会留下"有起点没结算"这类自相矛盾的状态。
 *
 * <p>为什么入档而不是放内存：结算是按时间戳算的，玩家在打坐期间断开又回来时服务端必须能判断这段会话是否还成立 （NUMBERS §5 {@code offline_gain_allowed:
 * false} → 离线时段不计修为，登录时按 {@link #stop()} 收尾）。内存态一重启就丢， 反而会把断线时段算成修为。
 *
 * <p>{@code creditedQi} 记的是"本次会话已发给玩家的修为总量"，不是"已结算的片数"：按片数记账再乘每片收益，会在每片做一次 向下取整——40 刻 × 0.04 修为/刻 =
 * 1.6，每次只记 1，长期下来凭空蒸发三成修为。按累计量记账则只在最后取整一次。
 *
 * @param startTick 本次会话开始的游戏刻；负数 = 未在打坐（用 -1 而不是 0：游戏刻 0 是合法起点）
 * @param creditedQi 本次会话已发放的修为总量（防重复结算；断线重连后靠它续算在线部分）
 * @param cooldownUntilTick 打断后的再入定冷却截止刻；0 = 无冷却
 */
public record MeditationState(long startTick, int creditedQi, long cooldownUntilTick) {

    /** 未打坐、无冷却。 */
    public static final MeditationState IDLE = new MeditationState(-1L, 0, 0L);

    public static final Codec<MeditationState> CODEC =
            RecordCodecBuilder.create(
                    instance ->
                            instance.group(
                                            Codec.LONG
                                                    .fieldOf("start_tick")
                                                    .orElse(-1L)
                                                    .forGetter(MeditationState::startTick),
                                            Codec.INT
                                                    .fieldOf("credited_qi")
                                                    .orElse(0)
                                                    .forGetter(MeditationState::creditedQi),
                                            Codec.LONG
                                                    .fieldOf("cooldown_until_tick")
                                                    .orElse(0L)
                                                    .forGetter(MeditationState::cooldownUntilTick))
                                    .apply(instance, MeditationState::new));

    /** 正在打坐。 */
    public boolean active() {
        return startTick >= 0L;
    }

    /** 是否处于打断后的再入定冷却中。 */
    public boolean onCooldown(long nowTick) {
        return cooldownUntilTick > nowTick;
    }

    /** 冷却剩余刻数（0 = 无冷却）。 */
    public long cooldownRemaining(long nowTick) {
        return Math.max(0L, cooldownUntilTick - nowTick);
    }

    /** 开始一次会话：起点取当前刻、已发放量归零（冷却不因入定而清除——否则打断惩罚形同虚设）。 */
    public MeditationState start(long nowTick) {
        return new MeditationState(nowTick, 0, cooldownUntilTick);
    }

    /** 结束会话（正常出定/修为满/登录收尾）：保留冷却信息。 */
    public MeditationState stop() {
        return new MeditationState(-1L, 0, cooldownUntilTick);
    }

    /** 打断：结束会话并写入冷却截止刻。 */
    public MeditationState interrupt(long nowTick, long cooldownTicks) {
        return new MeditationState(-1L, 0, nowTick + cooldownTicks);
    }

    /** 会话进行中的记账更新（起点与冷却不变）。 */
    public MeditationState withCreditedQi(int credited) {
        return new MeditationState(startTick, Math.max(0, credited), cooldownUntilTick);
    }

    /** 自洽性：未打坐时不该有已发放修为。 */
    public boolean valid() {
        return active() || creditedQi == 0;
    }
}

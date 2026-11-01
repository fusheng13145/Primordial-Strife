package com.strife.combat;

/**
 * 妖兽技能的纯时序机（P1-10）：蓄力 → 释放 → 冷却，全部按<b>绝对 tick</b> 判定。
 *
 * <p>为什么抽纯类：{@code Goal.tick()} 只在目标运行期被调——冷却若靠 tick 递减，释放后 {@code canContinueToUse} 为
 * false、目标停转，冷却就永远走不完（"一生一次"缺陷，2026-11-01 读码发现）。改为记录 {@code cooldownUntilTick} 后与实体是否被 tick
 * 无关；同时纯状态机不需要 {@code Level}，时序契约可以直接单测（07 §8.3 红先）。
 *
 * <p>释放与冷却解耦：{@link #tick} 返回 {@link TickResult#RELEASE} 时<b>不</b>自动进冷却——结算方（goal）在真正 造成 AOE 后调
 * {@link #commitCooldown}。这样"蓄力中目标死亡 → 释放拍无目标 → 不伤害也不进冷却"的原语义保持不变。
 */
final class BeastSkillTimeline {

    /** 每拍推进结果：蓄力中 / 本拍释放 / 空闲。 */
    enum TickResult {
        CHARGING,
        RELEASE,
        IDLE
    }

    private final int windupTicks;
    private final int cooldownTicks;
    private long cooldownUntilTick;
    private long releaseAtTick = Long.MIN_VALUE;

    BeastSkillTimeline(int windupTicks, int cooldownTicks) {
        this.windupTicks = windupTicks;
        this.cooldownTicks = cooldownTicks;
    }

    /** 冷却是否已结束（与是否在蓄力无关）。 */
    boolean offCooldown(long now) {
        return now >= cooldownUntilTick;
    }

    /** 正在蓄力。 */
    boolean winding() {
        return releaseAtTick != Long.MIN_VALUE;
    }

    /**
     * 尝试开始蓄力：冷却中或已在蓄力时返回 false（调用方据此放弃本次 canUse）。
     *
     * @param now 当前实体 tick
     */
    boolean tryStart(long now) {
        if (!offCooldown(now) || winding()) {
            return false;
        }
        releaseAtTick = now + windupTicks;
        return true;
    }

    /** 每拍推进：{@code RELEASE} 在蓄力到点的那拍恰好返回一次，之后回 {@code IDLE}。 */
    TickResult tick(long now) {
        if (!winding()) {
            return TickResult.IDLE;
        }
        if (now < releaseAtTick) {
            return TickResult.CHARGING;
        }
        releaseAtTick = Long.MIN_VALUE;
        return TickResult.RELEASE;
    }

    /** 蓄力被打断（目标丢失 / Goal 被让位）：清蓄力，不释放、不进冷却，可立即重试。 */
    void abort() {
        releaseAtTick = Long.MIN_VALUE;
    }

    /** AOE 真正结算后由调用方提交冷却（绝对 tick 记账，见类注释）。 */
    void commitCooldown(long now) {
        cooldownUntilTick = now + cooldownTicks;
    }
}

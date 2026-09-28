package com.strife.quest.engine;

/**
 * 奖励缝（QuestEngine → 平台/领域，docs/07 §7 M3"进度入档"的出料口）：引擎算出"该给什么"， 缝的实现负责"怎么给"。item
 * 走原版背包由装配层实现；qi/realm_step/spell/technique 属 realm/combat 领域，等其 API/事件落地（M1/M2）后补实现——本期未实现的缝直接抛
 * {@link UnsupportedOperationException}，让缺口可见而不是静默吞奖励。
 *
 * <p>幂等契约：引擎只保证"同一任务完成判定恰好一次"（重报事件是 no-op）；缝被重放时 <b>不得</b>重复发放——装配层实现时以任务完成态为幂等键。
 */
public interface RewardSink {

    void giveItem(String itemId, long count);

    /** realm 领域（A1-3 修为公式）落地前抛 UnsupportedOperationException。 */
    void grantQi(long amount);

    /** realm 领域（大限回退一档的逆操作）落地前抛 UnsupportedOperationException。 */
    void advanceRealmStep();

    /** combat 领域落地前抛 UnsupportedOperationException。 */
    void grantSpell(String spellId);

    /** 功法授予：装备门槛仍由 realm 查表（STORY §4 节点 4 的口径）。 */
    void grantTechnique(String techniqueId);

    /** H3 运行时键 {@code <命名空间>:<章>:<语义>}。 */
    void setFlag(String key);

    /** §1.4 unlock_key 清单键（meditation 等），落 StrifeData.flags 位域。 */
    void unlock(String unlockKey);

    /** H2 声望向量，delta ∈ [-100,100]（越界是 Validator V-RANGE 的事，缝照传）。 */
    void addReputation(String factionId, int delta);
}

package com.strife.realm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

/**
 * 负面状态与真相源的契约用例（NUMBERS {@code @@lifespan.dasheng_debuff_key} /
 * {@code @@breakthrough_cost.tribulation_debuff_key}）。
 *
 * <p>效果 id 在代码里必须是编译期常量（注册表要字符串），而"该叫什么名字"写在真相源里——两者一旦分叉，表现是"大限给了个
 * 空效果"：不报错、不崩溃，只是玩家什么也没中。所以这里拿真实产物把名字对齐钉住。
 */
class RealmEffectsTest {

    @Test
    void registeredWeakIdMatchesTheTruthSourceKey() {
        assertEquals(
                RealmEffects.WEAK_ID,
                RealmTables.parseRules(ShippedProducts.realmRules()).dashengDebuffKey());
    }

    @Test
    void registeredHeavyWoundIdMatchesTheTruthSourceKey() {
        JsonObject cost = ShippedProducts.realmRules().getAsJsonObject("breakthrough_cost");

        assertEquals(RealmEffects.HEAVY_WOUND_ID, cost.get("tribulation_debuff_key").getAsString());
    }

    @Test
    void debuffMagnitudeIsAPenaltyAndDurationIsPositive() {
        RealmTables.Rules rules = RealmTables.parseRules(ShippedProducts.realmRules());

        assertTrue(rules.debuffAllStatDelta() < 0.0, "负面状态必须是下调：实际 " + rules.debuffAllStatDelta());
        assertTrue(rules.debuffDurationTicks() > 0L, "限时必须为正，否则效果加了个寂寞");
        assertEquals(36_000L, rules.debuffDurationTicks(), "1800 秒 × 20 刻");
    }

    @Test
    void effectNameKeysFollowTheSchemaPrefix() {
        assertEquals("effect.strife.debuff_weak", RealmEffects.langKey(RealmEffects.WEAK_ID));
        assertEquals(
                "effect.strife.debuff_heavy_wound",
                RealmEffects.langKey(RealmEffects.HEAVY_WOUND_ID));
    }

    /** 渡劫境来自产物推导（unlocks 含 tribulation）：突破进元婴那次失败才吃重伤，链路依赖这个标记。 */
    @Test
    void tribulationFlagComesFromTheShippedRealmEntry() {
        assertTrue(
                ShippedProducts.realm("yuanying").get("tribulation").getAsBoolean(),
                "元婴境应标记为天劫境（NUMBERS @@realms yuanying.unlocks 含 tribulation）");
    }
}

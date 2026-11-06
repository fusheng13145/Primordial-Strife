package com.strife.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.strife.testing.ShippedProducts;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** 灵晶引灵（异火野外路径）的规则核：权重解析、归一化抽取（表权重不必和为 100）、坏值加载期红。 */
class FireCrystalTest {

    @Test
    void shippedRulesCarryTheFireCrystalBlock() {
        JsonObject world = ShippedProducts.worldRules().getAsJsonObject("world");
        assertTrue(
                world.has("fires_crystal_cooldown_sec"), "shipped world rules 缺 fires_crystal 键");
        WorldTables.FireCrystal rules = WorldTables.parseFireCrystal(world);
        assertEquals(60L, rules.cooldownSec(), "@@world.fires_crystal_cooldown_sec [拟]");
        assertEquals(7, rules.weights().size(), "none + 六火");
        assertTrue(rules.weights().containsKey("fire_taiyang"), "传说火在池里（0.5 最低权重）");
        assertTrue(rules.weights().containsKey("none"), "灵气涣散是池的一部分");
    }

    @Test
    void rollNormalizesWeightsSoTheyNeedNotSumTo100() {
        var rules = new WorldTables.FireCrystal(60L, Map.of("a", 1.0, "b", 3.0));
        var rng = new Random(20261104);
        int b = 0;
        int trials = 20_000;
        for (int i = 0; i < trials; i++) {
            if ("b".equals(rules.roll(rng::nextDouble))) {
                b++;
            }
        }
        assertEquals(0.75, b / (double) trials, 0.02, "3/4 权重 ≈ 75% 命中 b（归一化语义）");
    }

    @Test
    void zeroTotalWeightsDegradeToNone() {
        var rules = new WorldTables.FireCrystal(60L, Map.of());
        assertEquals("none", rules.roll(() -> new Random(1).nextDouble()), "空池退化为无事发生（表配错不崩引导）");
    }

    @Test
    void negativeOrZeroWeightFailsAtLoad() {
        JsonObject world = new JsonObject();
        world.addProperty("fires_crystal_cooldown_sec", 60);
        var weights = new JsonObject();
        weights.addProperty("fire_mu", 0.0);
        world.add("fires_crystal_weights", weights);
        assertThrows(IllegalStateException.class, () -> WorldTables.parseFireCrystal(world));
    }
}

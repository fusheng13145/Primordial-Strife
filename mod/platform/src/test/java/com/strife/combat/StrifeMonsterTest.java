package com.strife.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.strife.testing.ShippedProducts;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.junit.jupiter.api.Test;

/**
 * 妖兽实体的属性与判别口径用例。
 *
 * <p>实体实例的构造需要 {@code Level}（测试环境无法提供），所以这里只验证无需 Level 的契约： ① {@code createAttributes()} 的默认值等于
 * {@link CombatTables.BeastStats#fallback()}（即"资源缺失时安全网"与 AttributeSupplier 默认一致，避免两套默认值漂移）；② 实体注册
 * id 路径为 {@code monster}，与掉落表路径 {@code loot_tables/entities/monster} 对齐（原版按注册名推导）；③ 随 jar 分发的掉落表产物掉
 * {@code item_yaocai}，且 pool rolls 与 world_rules 产物的 {@code beast_loot_rolls}
 * 对齐（产物×产物对齐，不写死数值）。实体在世界上真实出生并应用 combat_rules 属性、 触发技能 AI 这类需真机证据的链路，按项目纪律不在单元测试里自证（docs/06 §4）。
 */
class StrifeMonsterTest {

    @Test
    void createAttributesUsesFallbackDefaults() {
        AttributeSupplier supplier = StrifeMonster.createAttributes().build();
        assertEquals(20.0, supplier.getBaseValue(Attributes.MAX_HEALTH), 1e-9);
        assertEquals(3.0, supplier.getBaseValue(Attributes.ATTACK_DAMAGE), 1e-9);
        assertEquals(0.30, supplier.getBaseValue(Attributes.MOVEMENT_SPEED), 1e-9);
        assertEquals(16.0, supplier.getBaseValue(Attributes.FOLLOW_RANGE), 1e-9);
    }

    @Test
    void entityRegistrationIdMatchesLootTablePath() {
        // 原版按注册名推导掉落表：strife:monster → loot_tables/entities/monster。id 路径必须为 "monster"。
        assertEquals("monster", StrifeCombatEntities.MONSTER.getId().getPath());
    }

    @Test
    void shippedBeastLootDropsYaoCaiWithRollsFromWorldRules() {
        assertEquals(
                "minecraft:entity",
                ShippedProducts.beastLoot().get("type").getAsString(),
                "掉落表必须是实体掉落表（type=minecraft:entity）");
        int rolls =
                ShippedProducts.worldRules()
                        .getAsJsonObject("world")
                        .get("beast_loot_rolls")
                        .getAsInt();
        var pool = ShippedProducts.beastLoot().getAsJsonArray("pools").get(0).getAsJsonObject();
        assertEquals(
                rolls, pool.get("rolls").getAsInt(), "rolls 必须来自 NUMBERS world.beast_loot_rolls");
        var entry = pool.getAsJsonArray("entries").get(0).getAsJsonObject();
        assertEquals("minecraft:item", entry.get("type").getAsString());
        assertEquals("strife:item_yaocai", entry.get("name").getAsString());
    }

    /**
     * 妖兽的自然生成修饰符必须用 NeoForge 的 {@code spawners} 键。
     *
     * <p>这是实机进世界时崩溃的根因：写成 {@code spawns} 时注册表加载失败，单人模式点进去即崩。
     */
    @Test
    void shippedSpawnModifierUsesNeoForgeSpawnersKey() {
        var modifier = ShippedProducts.biomeModifier("beast_overworld");
        assertEquals("neoforge:add_spawns", modifier.get("type").getAsString());
        assertFalse(
                modifier.has("spawns"), "neoforge:add_spawns 的字段名是 spawners；写成 spawns 会让注册表加载失败");
        var spawners = modifier.getAsJsonArray("spawners");
        assertNotNull(spawners, "缺 spawners 数组");
        assertFalse(spawners.isEmpty(), "spawners 为空时妖兽不会自然生成");
        assertEquals(
                "strife:monster",
                spawners.get(0).getAsJsonObject().get("type").getAsString(),
                "生成条目必须指向已注册的妖兽实体");
    }
}

package com.strife.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.strife.combat.SpellTables.SpellSpec;
import com.strife.testing.ShippedProducts;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 法宝契约、使用判定链与冷却的纯数据用例（器胚由 production 写入，combat 只读）。 */
class ArtifactsTest {

    private static final SpellSpec XUANYU_REN =
            new SpellSpec(
                    "spell_xuanyu_ren",
                    "jin",
                    "tech_xuanyu_jue",
                    18,
                    4.0,
                    "formula_pierce",
                    4,
                    true,
                    1.2,
                    0.0,
                    24.0,
                    1,
                    0.0);

    private static final SpellSpec NO_EFFECT =
            new SpellSpec(
                    "spell_prologue_tubi",
                    "tu",
                    null,
                    45,
                    12.0,
                    null,
                    0,
                    false,
                    null,
                    null,
                    null,
                    0,
                    0.0);

    private static final Artifacts.Contract CONTRACT =
            new Artifacts.Contract("art_xuanyu", "di", "treasure", "spell_xuanyu_ren", 100, 100);

    @Test
    @DisplayName("器胚标签读出完整契约；缺 artifact_id 视为非法宝")
    void readsContractFromItemTags() {
        CompoundTag tag = new CompoundTag();
        tag.putString("artifact_id", "art_xuanyu");
        tag.putString("quality", "di");
        tag.putString("slot_type", "treasure");
        tag.putString("active_spell", "spell_xuanyu_ren");
        tag.putInt("durability", 100);
        tag.putInt("max_durability", 100);

        assertEquals(CONTRACT, Artifacts.readContract(tag));
        assertNull(Artifacts.readContract(new CompoundTag()));
        assertNull(Artifacts.readContract(null));
    }

    @Test
    @DisplayName("耐久标签缺失按 0 处理；成功激发一次扣 1 点且不就地改原标签")
    void durabilityTagsAreReadAndConsumed() {
        CompoundTag tag = new CompoundTag();
        tag.putString("artifact_id", "art_xuanyu");
        tag.putInt("durability", 2);
        tag.putInt("max_durability", 100);

        assertEquals(2, Artifacts.readDurability(tag));
        assertEquals(100, Artifacts.readMaxDurability(tag));
        CompoundTag once = Artifacts.consumeDurability(tag);
        assertEquals(1, once.getInt("durability"));
        assertEquals(2, tag.getInt("durability"), "原标签不应被就地修改");
        CompoundTag empty = Artifacts.consumeDurability(once);
        assertEquals(0, empty.getInt("durability"));
        assertEquals(0, Artifacts.consumeDurability(empty).getInt("durability"), "耐久不为负");
        assertEquals(0, Artifacts.readDurability(new CompoundTag()));
        assertEquals(0, Artifacts.readMaxDurability(null));
    }

    @Test
    @DisplayName("耐久为 0 时拒绝激发；旧器胚无耐久标签时保持可激发")
    void brokenArtifactRejectedButLegacyStillWorks() {
        Artifacts.Contract broken =
                new Artifacts.Contract("art_xuanyu", "di", "treasure", "spell_xuanyu_ren", 0, 100);
        assertEquals(
                Artifacts.Rejection.BROKEN,
                Artifacts.check(broken, true, XUANYU_REN, 999, false, false));
        Artifacts.Contract lastPoint =
                new Artifacts.Contract("art_xuanyu", "di", "treasure", "spell_xuanyu_ren", 1, 100);
        assertNull(Artifacts.check(lastPoint, true, XUANYU_REN, 999, false, false));
        Artifacts.Contract legacy =
                new Artifacts.Contract("art_xuanyu", "di", "treasure", "spell_xuanyu_ren", 0, 0);
        assertNull(Artifacts.check(legacy, true, XUANYU_REN, 999, false, false));
    }

    @Test
    @DisplayName("使用判定链逐分支：契约 → 槽位 → 主动技能 → 解锁 → 法术 → 效果 → 修为 → 双冷却")
    void rejectionChainCoversEveryBranch() {
        assertEquals(
                Artifacts.Rejection.NO_ARTIFACT,
                Artifacts.check(null, true, XUANYU_REN, 999, false, false));
        assertEquals(
                Artifacts.Rejection.WRONG_SLOT,
                Artifacts.check(
                        new Artifacts.Contract(
                                "art_xuanyu", "di", "weapon", "spell_xuanyu_ren", 100, 100),
                        true,
                        XUANYU_REN,
                        999,
                        false,
                        false));
        assertEquals(
                Artifacts.Rejection.NO_ACTIVE_SKILL,
                Artifacts.check(
                        new Artifacts.Contract("art_xuanyu", "di", "treasure", "", 100, 100),
                        true,
                        null,
                        999,
                        false,
                        false));
        assertEquals(
                Artifacts.Rejection.NOT_UNLOCKED,
                Artifacts.check(CONTRACT, false, XUANYU_REN, 999, false, false));
        assertEquals(
                Artifacts.Rejection.UNKNOWN_SPELL,
                Artifacts.check(CONTRACT, true, null, 999, false, false));
        assertEquals(
                Artifacts.Rejection.NO_EFFECT,
                Artifacts.check(CONTRACT, true, NO_EFFECT, 999, false, false));
        assertEquals(
                Artifacts.Rejection.NOT_ENOUGH_QI,
                Artifacts.check(CONTRACT, true, XUANYU_REN, 17, false, false));
        assertEquals(
                Artifacts.Rejection.ARTIFACT_ON_COOLDOWN,
                Artifacts.check(CONTRACT, true, XUANYU_REN, 999, true, false));
        assertEquals(
                Artifacts.Rejection.SPELL_ON_COOLDOWN,
                Artifacts.check(CONTRACT, true, XUANYU_REN, 999, false, true));
        assertNull(Artifacts.check(CONTRACT, true, XUANYU_REN, 18, false, false));
    }

    @Test
    @DisplayName("法宝冷却键与法术冷却键不互相覆盖")
    void artifactCooldownUsesNamespacedKey() {
        assertEquals("artifact:art_xuanyu", Artifacts.cooldownKey("art_xuanyu"));
    }

    @Test
    @DisplayName("法宝冷却 = 战斗规则 artifact_cooldown_sec × 每秒刻数")
    void cooldownConvertsSecondsToTicks() {
        assertEquals(400L, Artifacts.cooldownUntil(0, 20, 20.0));
        assertEquals(500L, Artifacts.cooldownUntil(100, 20, 20.0));
    }

    @Test
    @DisplayName("每份器图的主动技能都能在法术表里找到")
    void everyShippedActiveSkillResolves() {
        SpellTables tables = SpellTables.load();
        for (JsonObject artifact : ShippedProducts.allArtifacts()) {
            JsonElement active = artifact.get("active_skill");
            if (active == null || active.isJsonNull()) {
                continue;
            }
            String spellId = active.getAsJsonObject().get("spell_id").getAsString();
            assertNotNull(
                    tables.spell(spellId),
                    artifact.get("id").getAsString() + " 的主动技能 " + spellId + " 不在法术表");
        }
    }

    @Test
    @DisplayName("法宝冷却与器图产物里的 cooldown_sec 同源")
    void artifactCooldownMatchesProduct() {
        double productCooldown =
                ShippedProducts.artifact("art_xuanyu")
                        .getAsJsonObject("active_skill")
                        .get("cooldown_sec")
                        .getAsDouble();

        assertEquals(productCooldown, SpellTables.load().rules().artifactCooldownSec(), 1e-9);
    }

    @Test
    @DisplayName("info 主动行保留翻译键，不用服务端语言把技能名解析成英文")
    void activeLineKeepsTranslationKey() {
        Component line = Artifacts.activeLine(XUANYU_REN, 0);

        assertTrue(
                line.getSiblings().stream()
                        .anyMatch(
                                sibling ->
                                        sibling.getContents()
                                                        instanceof TranslatableContents translatable
                                                && translatable
                                                        .getKey()
                                                        .equals("spell.strife.spell_xuanyu_ren")),
                "主动技能名必须是 translatable 组件，交给客户端按玩家语言解析");
    }

    @Test
    @DisplayName("槽位类型以中文展示，不把 treasure 等契约值直接丢给玩家")
    void slotLabelsAreChinese() {
        assertEquals("法宝", Artifacts.slotLabel("treasure"));
        assertEquals("兵器", Artifacts.slotLabel("weapon"));
        assertEquals("护具", Artifacts.slotLabel("armor"));
    }
}

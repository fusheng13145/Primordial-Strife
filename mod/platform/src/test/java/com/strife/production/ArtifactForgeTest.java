package com.strife.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.strife.combat.Artifacts;
import com.strife.realm.RealmTables;
import com.strife.testing.ShippedProducts;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 炼器门禁与文案键：境界判定是纯函数，文案键按随 jar 分发的产物品阶枚举核对。 */
class ArtifactForgeTest {

    private static final RealmTables.RealmEntry QILI = realm("qili", 1);

    private static RealmTables.RealmEntry realm(String id, int ordinal) {
        return new RealmTables.RealmEntry(id, ordinal, 100, 9, 100, 0.1, "k", false);
    }

    @Test
    @DisplayName("境界等于或高于要求时可炼")
    void realmGateAllowsAtOrAboveRequired() {
        assertTrue(ArtifactForge.realmAllows(1, QILI));
        assertTrue(ArtifactForge.realmAllows(5, QILI));
    }

    @Test
    @DisplayName("境界低于要求时拒绝")
    void realmGateRejectsBelowRequired() {
        assertFalse(ArtifactForge.realmAllows(0, QILI));
    }

    @Test
    @DisplayName("器图引用了不存在的境界时 fail-closed")
    void realmGateFailsClosedWhenRealmUnknown() {
        assertFalse(ArtifactForge.realmAllows(9, null));
    }

    @Test
    @DisplayName("器胚标签携带战斗侧读取的完整法宝契约")
    void artifactTagsCarryCombatContract() {
        ArtifactRecipe recipe = ArtifactRecipe.parse(ShippedProducts.artifact("art_xuanyu"));

        CompoundTag tag = ArtifactForge.artifactTags(recipe, "di");

        assertEquals("art_xuanyu", tag.getString("artifact_id"));
        assertEquals("di", tag.getString("quality"));
        assertEquals("treasure", tag.getString("slot_type"));
        assertEquals("spell_xuanyu_ren", tag.getString("active_spell"));
        assertEquals(100, tag.getInt("durability"), "器胚出厂耐久应为满");
        assertEquals(100, tag.getInt("max_durability"), "耐久上限来自器图 durability_points");
        Artifacts.Contract contract = Artifacts.readContract(tag);
        assertEquals(
                new Artifacts.Contract(
                        "art_xuanyu", "di", "treasure", "spell_xuanyu_ren", 100, 100),
                contract);
    }

    @Test
    @DisplayName("每份器图的名称、所需境界与全部品阶在中英文里都有词条")
    void shippedArtifactsHaveLangKeys() {
        for (String locale : List.of("zh_cn", "en_us")) {
            JsonObject lang = ShippedProducts.lang(locale);
            assertTrue(lang.has("tooltip.strife.artifact"), locale + " 缺器胚 tooltip");
            assertTrue(lang.has("tooltip.strife.artifact.active"), locale + " 缺法宝主动技能 tooltip");
            for (JsonObject artifact : ShippedProducts.allArtifacts()) {
                String id = artifact.get("id").getAsString();
                assertTrue(lang.has("artifact.strife." + id), locale + " 缺 artifact.strife." + id);
                if (artifact.has("active_skill") && !artifact.get("active_skill").isJsonNull()) {
                    String spellId =
                            artifact.getAsJsonObject("active_skill").get("spell_id").getAsString();
                    assertTrue(
                            lang.has("spell.strife." + spellId),
                            locale + " 缺主动技能词条 spell.strife." + spellId);
                }
                assertTrue(
                        lang.has("realm.strife." + artifact.get("required_realm").getAsString()),
                        locale + " 缺境界词条 " + artifact.get("required_realm").getAsString());
                artifact.getAsJsonArray("quality_probs")
                        .forEach(
                                element -> {
                                    String tier =
                                            element.getAsJsonObject()
                                                    .get("quality_tier")
                                                    .getAsString();
                                    assertTrue(
                                            lang.has("quality.strife." + tier),
                                            locale + " 缺 quality.strife." + tier);
                                });
            }
        }
    }
}

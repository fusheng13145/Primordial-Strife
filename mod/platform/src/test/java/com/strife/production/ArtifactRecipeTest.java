package com.strife.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.strife.testing.ShippedProducts;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 器图产物解析、材料并集与品质抽签的纯数据用例。 */
class ArtifactRecipeTest {

    @Test
    @DisplayName("随 jar 分发的玄玉器图能完整解析")
    void parsesShippedArtifact() {
        ArtifactRecipe recipe = ArtifactRecipe.parse(ShippedProducts.artifact("art_xuanyu"));

        assertEquals("art_xuanyu", recipe.id());
        assertEquals("item_xuanyu", recipe.blank());
        assertEquals("item_lingshi", recipe.coreSlot());
        assertEquals("qili", recipe.requiredRealm());
        assertEquals("treasure", recipe.slotType());
        assertEquals(
                new ArtifactRecipe.ActiveSkill("spell_xuanyu_ren", "medium"), recipe.activeSkill());
        assertEquals(100, recipe.durabilityPoints());
        assertEquals(
                List.of(
                        new ArtifactRecipe.Quality("di", 0.50),
                        new ArtifactRecipe.Quality("fan", 0.40),
                        new ArtifactRecipe.Quality("tian", 0.10)),
                recipe.qualityProbs());
    }

    @Test
    @DisplayName("器胚与核心材料即使重复出现在 materials 里也只按最大值扣一次")
    void consumePlanDeduplicatesBlankAndCore() {
        ArtifactRecipe recipe = ArtifactRecipe.parse(ShippedProducts.artifact("art_xuanyu"));

        assertEquals(
                List.of(
                        new AlchemyRecipe.Material("item_xuanyu", 1),
                        new AlchemyRecipe.Material("item_lingshi", 3),
                        new AlchemyRecipe.Material("item_ningxu", 2)),
                recipe.consumePlan());
    }

    @Test
    @DisplayName("未知字段 fail-fast，不静默忽略")
    void rejectsUnknownField() {
        JsonObject product = ShippedProducts.artifact("art_xuanyu").deepCopy();
        product.addProperty("wallet", 1);

        assertThrows(IllegalStateException.class, () -> ArtifactRecipe.parse(product));
    }

    @Test
    @DisplayName("品质概率和必须为 1")
    void rejectsQualitySumNotOne() {
        JsonObject product = ShippedProducts.artifact("art_xuanyu").deepCopy();
        product.getAsJsonArray("quality_probs").get(0).getAsJsonObject().addProperty("prob", 0.90);

        assertThrows(IllegalStateException.class, () -> ArtifactRecipe.parse(product));
    }

    @Test
    @DisplayName("active_skill 为 null 的器图可解析为无主动技能")
    void acceptsNullActiveSkill() {
        JsonObject product = ShippedProducts.artifact("art_xuanyu").deepCopy();
        product.add("active_skill", JsonNull.INSTANCE);

        assertNull(ArtifactRecipe.parse(product).activeSkill());
    }

    @Test
    @DisplayName("active_skill.spell_id 为空时 fail-fast")
    void rejectsBlankActiveSkillSpell() {
        JsonObject product = ShippedProducts.artifact("art_xuanyu").deepCopy();
        product.getAsJsonObject("active_skill").addProperty("spell_id", " ");

        assertThrows(IllegalStateException.class, () -> ArtifactRecipe.parse(product));
    }

    @Test
    @DisplayName("active_skill 里的未知字段 fail-fast")
    void rejectsUnknownActiveSkillField() {
        JsonObject product = ShippedProducts.artifact("art_xuanyu").deepCopy();
        product.getAsJsonObject("active_skill").addProperty("on_hit", "burn");

        assertThrows(IllegalStateException.class, () -> ArtifactRecipe.parse(product));
    }

    @Test
    @DisplayName("品质抽签按产物顺序的累积区间命中")
    void rollsQualityByCumulativeIntervals() {
        ArtifactRecipe recipe = ArtifactRecipe.parse(ShippedProducts.artifact("art_xuanyu"));

        assertEquals("di", recipe.rollQuality(bound -> 0));
        assertEquals("di", recipe.rollQuality(bound -> 499));
        assertEquals("fan", recipe.rollQuality(bound -> 500));
        assertEquals("fan", recipe.rollQuality(bound -> 899));
        assertEquals("tian", recipe.rollQuality(bound -> 900));
        assertEquals("tian", recipe.rollQuality(bound -> 999));
    }
}

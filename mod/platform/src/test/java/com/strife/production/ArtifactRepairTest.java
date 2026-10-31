package com.strife.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 法宝修复的纯数据判定：满耐久不修、旧器胚不修、修复回满且不改原标签。 */
class ArtifactRepairTest {

    private static CompoundTag artifact(int durability, int maxDurability) {
        CompoundTag tag = new CompoundTag();
        tag.putString("artifact_id", "art_xuanyu");
        tag.putInt("durability", durability);
        tag.putInt("max_durability", maxDurability);
        return tag;
    }

    @Test
    @DisplayName("只有低于上限且仍有耐久记录的器胚需要修复")
    void needsRepairOnlyWhenBelowMax() {
        assertTrue(ArtifactRepair.needsRepair(99, 100));
        assertTrue(ArtifactRepair.needsRepair(0, 100));
        assertFalse(ArtifactRepair.needsRepair(100, 100));
        assertFalse(ArtifactRepair.needsRepair(0, 0), "旧器胚无耐久记录，不进入修复流程");
    }

    @Test
    @DisplayName("修复把耐久回满，返回副本且不改原标签")
    void restoredFillsDurabilityToMaxOnACopy() {
        CompoundTag tag = artifact(3, 100);
        CompoundTag fixed = ArtifactRepair.restored(tag);

        assertEquals(100, fixed.getInt("durability"));
        assertEquals(100, fixed.getInt("max_durability"));
        assertEquals(3, tag.getInt("durability"), "原标签不应被就地修改");
    }

    @Test
    @DisplayName("修复材料取器图的 core_slot")
    void repairCoreComesFromShippedRecipe() {
        ArtifactTables.register();

        assertEquals("item_lingshi", ArtifactRepair.repairCore(artifact(1, 100)));
    }

    @Test
    @DisplayName("非器胚或未知器图没有修复材料")
    void nonArtifactOrUnknownRecipeHasNoCore() {
        assertNull(ArtifactRepair.repairCore(new CompoundTag()));
        CompoundTag unknown = new CompoundTag();
        unknown.putString("artifact_id", "art_missing");
        assertNull(ArtifactRepair.repairCore(unknown));
    }
}

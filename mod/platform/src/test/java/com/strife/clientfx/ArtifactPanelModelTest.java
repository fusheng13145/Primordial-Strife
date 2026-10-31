package com.strife.clientfx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 法宝面板只读模型：缺标签即空面板，耐久比例钳制在 0–1。 */
class ArtifactPanelModelTest {

    @Test
    @DisplayName("空标签/非法宝返回空面板")
    void emptyTagIsNotPresent() {
        ArtifactPanelModel model = ArtifactPanelModel.fromTags(new CompoundTag());

        assertFalse(model.present());
        assertFalse(model.broken());
        assertFalse(model.legacy());
        assertEquals(0.0, model.durabilityRatio());
    }

    @Test
    @DisplayName("完整契约映射到面板字段，耐久比例 0–1")
    void mapsContractFieldsAndRatio() {
        CompoundTag tag = new CompoundTag();
        tag.putString("artifact_id", "art_xuanyu");
        tag.putString("quality", "di");
        tag.putString("active_spell", "spell_xuanyu_ren");
        tag.putInt("durability", 50);
        tag.putInt("max_durability", 100);

        ArtifactPanelModel model = ArtifactPanelModel.fromTags(tag);

        assertTrue(model.present());
        assertEquals("art_xuanyu", model.artifactId());
        assertEquals("di", model.quality());
        assertEquals("spell_xuanyu_ren", model.activeSpell());
        assertEquals(50, model.durability());
        assertEquals(100, model.maxDurability());
        assertEquals(0.5, model.durabilityRatio(), 1e-9);

        tag.putInt("durability", 120);
        assertEquals(1.0, ArtifactPanelModel.fromTags(tag).durabilityRatio(), 1e-9);
    }

    @Test
    @DisplayName("耐久 0 为损毁；无耐久记录的旧器胚不算损毁")
    void brokenAndLegacyAreDistinct() {
        CompoundTag broken = new CompoundTag();
        broken.putString("artifact_id", "art_xuanyu");
        broken.putInt("durability", 0);
        broken.putInt("max_durability", 100);
        assertTrue(ArtifactPanelModel.fromTags(broken).broken());

        CompoundTag legacy = new CompoundTag();
        legacy.putString("artifact_id", "art_xuanyu");
        ArtifactPanelModel model = ArtifactPanelModel.fromTags(legacy);
        assertTrue(model.legacy());
        assertFalse(model.broken());
    }
}

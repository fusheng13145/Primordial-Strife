package com.strife.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

/** 器胚未鉴定标记（ADR-037 ③）的读写往返：明市货默认已鉴定、黑市货打标、鉴定阁去除。 */
class ArtifactTagsTest {

    @Test
    void plainStackIsIdentifiedByDefault() {
        ItemStack stack = new ItemStack(net.minecraft.world.item.Items.IRON_INGOT);
        assertFalse(com.strife.production.ArtifactForge.hasArtifactTag(stack));
        assertFalse(ArtifactTags.isUnidentified(stack), "明市货默认已鉴定");
    }

    @Test
    void markAndAppraiseRoundTrip() {
        ItemStack stack = new ItemStack(net.minecraft.world.item.Items.IRON_INGOT);
        ArtifactTags.markUnidentified(stack);
        assertTrue(ArtifactTags.isUnidentified(stack), "黑市货打标后为未鉴定");
        ArtifactTags.markIdentified(stack);
        assertFalse(ArtifactTags.isUnidentified(stack), "鉴定阁去除标记");
        // 幂等：再去除不炸。
        ArtifactTags.markIdentified(stack);
        assertFalse(ArtifactTags.isUnidentified(stack));
    }

    @Test
    void markPreservesExistingCustomData() {
        ItemStack stack = new ItemStack(net.minecraft.world.item.Items.IRON_INGOT);
        // 器胚已有 artifact_id 等键——打标不得清掉它们（CustomData 合并语义）。
        CompoundTag seed = new CompoundTag();
        seed.putString("artifact_id", "art_xuanyu");
        seed.putString("quality", "di");
        stack.set(
                net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(seed));
        ArtifactTags.markUnidentified(stack);
        assertTrue(ArtifactTags.isUnidentified(stack));
        var tag = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA).copyTag();
        assertTrue(tag.contains("artifact_id") && tag.contains("quality"), "打标必须保留既有契约键（合并而非覆盖）");
    }
}

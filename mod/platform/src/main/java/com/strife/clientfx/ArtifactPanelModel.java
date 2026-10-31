package com.strife.clientfx;

import net.minecraft.nbt.CompoundTag;

/** 法宝面板的只读视图（从副手物品标签取契约，不做任何服务端判定）。 */
public record ArtifactPanelModel(
        boolean present,
        String artifactId,
        String quality,
        String activeSpell,
        int durability,
        int maxDurability) {

    private static final ArtifactPanelModel EMPTY = new ArtifactPanelModel(false, "", "", "", 0, 0);

    public static ArtifactPanelModel fromTags(CompoundTag tag) {
        if (tag == null || !tag.contains("artifact_id")) {
            return EMPTY;
        }
        return new ArtifactPanelModel(
                true,
                tag.getString("artifact_id"),
                tag.getString("quality"),
                tag.getString("active_spell"),
                tag.getInt("durability"),
                tag.getInt("max_durability"));
    }

    public boolean broken() {
        return present && maxDurability > 0 && durability <= 0;
    }

    public boolean legacy() {
        return present && maxDurability <= 0;
    }

    public double durabilityRatio() {
        if (maxDurability <= 0) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, durability / (double) maxDurability));
    }
}

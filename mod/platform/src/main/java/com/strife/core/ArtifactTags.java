package com.strife.core;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * 器胚 CustomData 标签的共享读写（H3 口径：标签是 production 写、combat 读的跨模块契约—— 常量与判定下沉 core，防两处字符串漂移；ADR-037 ③
 * 未鉴定标记随此入库）。
 */
public final class ArtifactTags {

    /** 黑市货未鉴定标记（ADR-037 ③）：鉴定阁去除前，品阶与加成对玩家隐藏。 */
    public static final String UNIDENTIFIED_TAG = "unidentified";

    private ArtifactTags() {}

    /** 是否未鉴定（无标记=已鉴定；明市货默认已鉴定）。 */
    public static boolean isUnidentified(ItemStack stack) {
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        return custom != null
                && custom.copyTag().contains(UNIDENTIFIED_TAG)
                && custom.copyTag().getBoolean(UNIDENTIFIED_TAG);
    }

    /** 打上未鉴定标记（无 CustomData 时新建；已有则合并，不清其余键）。 */
    public static void markUnidentified(ItemStack stack) {
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = custom == null ? new CompoundTag() : custom.copyTag();
        tag.putBoolean(UNIDENTIFIED_TAG, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /** 去除未鉴定标记（鉴定阁的落点）。 */
    public static void markIdentified(ItemStack stack) {
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null) {
            return;
        }
        CompoundTag tag = custom.copyTag();
        if (tag.contains(UNIDENTIFIED_TAG)) {
            tag.remove(UNIDENTIFIED_TAG);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }
}

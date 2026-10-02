package com.strife.core;

import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 容器计数与扣除的共享工具（03 §1 core 职责"数据层框架"）：任务 COLLECT 对账（quest）与炼丹材料
 * 扣除（production）走同一套口径——先主背包后末影箱、物品未注册按 0（热更内容不炸运行时）。
 *
 * <p>为什么在 core：两个消费方分属不同模块且互不依赖（quest/production 都只向下依赖 core），
 * 各写一份迟早漂移（计数口径分叉的表现是"对账说有、扣除说没有"这类最难解释的失联）。
 */
public final class InventoryOps {

    private InventoryOps() {}

    /** 物品内容 ID → 注册物品；未注册返回 empty。 */
    public static Optional<Item> resolve(String itemId) {
        return BuiltInRegistries.ITEM.getOptional(
                ResourceLocation.fromNamespaceAndPath("strife", itemId));
    }

    /** 主背包 + 末影箱的持有量（任务物品交付口径，docs/03 §8）。 */
    public static long countOwned(PlayerContainers containers, String itemId) {
        Item item = resolve(itemId).orElse(null);
        if (item == null) {
            return 0;
        }
        return countIn(containers.main(), item) + countIn(containers.enderChest(), item);
    }

    /** 单容器内某物品总数。 */
    public static long countIn(Container container, Item item) {
        long count = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.getItem() == item) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** 从（主背包 → 末影箱）扣除至多 amount 个指定物品；返回实际扣除数。持有不足时调用方必须先 {@link #countOwned} 校验——扣除路径不静默补足。 */
    public static long remove(PlayerContainers containers, String itemId, long amount) {
        Item item = resolve(itemId).orElse(null);
        if (item == null) {
            return 0;
        }
        long removed = removeFrom(containers.main(), item, amount);
        if (removed < amount) {
            removed += removeFrom(containers.enderChest(), item, amount - removed);
        }
        return removed;
    }

    private static long removeFrom(Container container, Item item, long amount) {
        long removed = 0;
        for (int i = 0; i < container.getContainerSize() && removed < amount; i++) {
            ItemStack stack = container.getItem(i);
            if (stack.getItem() != item) {
                continue;
            }
            int take = (int) Math.min(stack.getCount(), amount - removed);
            stack.shrink(take);
            removed += take;
        }
        return removed;
    }

    /** 玩家的两个容器的只读视图（调用方传实容器，core 不认识 ServerPlayer）。 */
    public record PlayerContainers(Container main, Container enderChest) {}
}

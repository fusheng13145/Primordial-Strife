package com.strife.production;

import com.strife.core.StrifeMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * MVP 物品注册（docs/03 §1 production 职责的物品侧，MVP 快速通道——<b>待 A 评审</b>）：序章任务链与 丹方引用的材料/货币五件。lang
 * 词条（zh_cn/en_us）已由 C 在 content-base 种好。
 *
 * <p>注册名 = 内容 ID 全名（{@code item_ningxu} → {@code strife:item_ningxu}）：任务表 target、奖励 id、lang key、丹方
 * materials 引用的都是全名，运行时按 {@code strife:<内容ID>} 直查注册表，不做前缀 拆装（04 §4 ID 即运行时 ID 的口径）。
 */
public final class StrifeItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, StrifeMod.MOD_ID);

    public static final DeferredHolder<Item, Item> LINGSHI = register("item_lingshi");
    public static final DeferredHolder<Item, Item> NINGXU = register("item_ningxu");
    public static final DeferredHolder<Item, Item> YUEJIAN = register("item_yuejian");
    public static final DeferredHolder<Item, Item> DUANXUE = register("item_duanxue");
    public static final DeferredHolder<Item, Item> YAOCAI = register("item_yaocai");

    private static DeferredHolder<Item, Item> register(String name) {
        return ITEMS.register(name, () -> new Item(new Item.Properties()));
    }

    private StrifeItems() {}
}

package com.strife.production;

import com.strife.core.StrifeMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 物品注册：注册名即内容 ID 全名（{@code item_ningxu} → {@code strife:item_ningxu}）。
 *
 * <p>任务 target、奖励 id、lang key、丹方 materials 与器图产物都按全名直查注册表，不做前缀拆装。
 */
public final class StrifeItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, StrifeMod.MOD_ID);

    public static final DeferredHolder<Item, Item> LINGSHI = register("item_lingshi");

    /** 灵石三档（EP1，ADR-032）：中品 = 下品 ×10，上品 = 下品 ×100（NUMBERS @@market）。 */
    public static final DeferredHolder<Item, Item> LINGSHI_ZHONG = register("item_lingshi_zhong");

    public static final DeferredHolder<Item, Item> LINGSHI_SHANG = register("item_lingshi_shang");
    public static final DeferredHolder<Item, Item> NINGXU = register("item_ningxu");
    public static final DeferredHolder<Item, Item> YUEJIAN = register("item_yuejian");
    public static final DeferredHolder<Item, Item> DUANXUE = register("item_duanxue");
    public static final DeferredHolder<Item, Item> YAOCAI = register("item_yaocai");
    public static final DeferredHolder<Item, Item> XUANYU = register("item_xuanyu");

    /** 丹方产物 pill_* 对应的可服用品（右键服用，行为在 {@link StrifePills}）。 */
    public static final DeferredHolder<Item, Item> PILL_JUQI = registerPill("pill_juqi");

    public static final DeferredHolder<Item, Item> PILL_PEIYUAN = registerPill("pill_peiyuan");
    public static final DeferredHolder<Item, Item> PILL_YANSHOU = registerPill("pill_yanshou");
    public static final DeferredHolder<Item, Item> PILL_XUANYU = registerPill("pill_xuanyu");

    private static DeferredHolder<Item, Item> register(String name) {
        return ITEMS.register(name, () -> new Item(new Item.Properties()));
    }

    private static DeferredHolder<Item, Item> registerPill(String pillId) {
        return ITEMS.register(pillId, () -> StrifePills.item(pillId));
    }

    private StrifeItems() {}
}

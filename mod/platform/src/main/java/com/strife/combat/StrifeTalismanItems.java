package com.strife.combat;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 符箓物品注册（EP1，ADR-035）：combat 自有的物品注册表——效果与数据同模块（production 不掺和， 战斗语义不下沉）。遁符带 {@code use()}
 * 行为；替身草人是普通物品（效果走 {@link TalismanEffects#register()} 的死亡事件监听）。
 */
public final class StrifeTalismanItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(
                    net.minecraft.core.registries.Registries.ITEM,
                    com.strife.core.StrifeMod.MOD_ID);

    /** 遁符：use → 传送到 NUMBERS 指定地点中心（一次性，冷却服务端权威）。 */
    public static final DeferredHolder<Item, Item> ESCAPE =
            ITEMS.register("item_dunfu", EscapeTalismanItem::new);

    /** 替身草人：致死一击代死（事件侧生效，本类无行为）。 */
    public static final DeferredHolder<Item, Item> SUBSTITUTE =
            ITEMS.register("item_tishen_caoren", () -> new Item(new Item.Properties()));

    /** 捕兽符（P1-22 御兽 v1）：对重伤野生妖兽使用 → 概率驯服为灵宠（interactLivingEntity 行为）。 */
    public static final DeferredHolder<Item, Item> BEAST_TALISMAN =
            ITEMS.register("item_bushou_fu", () -> new BeastTalismanItem(new Item.Properties()));

    private static class EscapeTalismanItem extends Item {
        EscapeTalismanItem() {
            super(new Item.Properties());
        }

        @Override
        public InteractionResultHolder<ItemStack> use(
                Level level, Player player, InteractionHand hand) {
            return TalismanEffects.useEscape(level, player, hand);
        }
    }

    private StrifeTalismanItems() {}
}

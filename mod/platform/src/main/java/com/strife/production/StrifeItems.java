package com.strife.production;

import com.strife.core.StrifeMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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

    // ── EP1 炼丹材料（ADR-038 丹药体系）──
    public static final DeferredHolder<Item, Item> HANSHI_CAO = register("item_hanshi_cao");
    public static final DeferredHolder<Item, Item> ZIJIN_HUA = register("item_zijin_hua");
    public static final DeferredHolder<Item, Item> BAIYU_GUO = register("item_baiyu_guo");
    public static final DeferredHolder<Item, Item> QINGXIN_LAN = register("item_qingxin_lan");
    public static final DeferredHolder<Item, Item> XUELIAN = register("item_xuelian");
    public static final DeferredHolder<Item, Item> YAODAN = register("item_yaodan");
    public static final DeferredHolder<Item, Item> XUANTIE = register("item_xuantie");
    public static final DeferredHolder<Item, Item> WUSE_SHI = register("item_wuse_shi");
    public static final DeferredHolder<Item, Item> TIANGUO = register("item_tianguo");
    public static final DeferredHolder<Item, Item> LONGXUE = register("item_longxue");
    public static final DeferredHolder<Item, Item> FENGXUE = register("item_fengxue");

    /** 丹方产物 pill_* 对应的可服用品（右键服用，行为在 {@link StrifePills}）。 */
    public static final DeferredHolder<Item, Item> PILL_JUQI = registerPill("pill_juqi");

    public static final DeferredHolder<Item, Item> PILL_PEIYUAN = registerPill("pill_peiyuan");
    public static final DeferredHolder<Item, Item> PILL_YANSHOU = registerPill("pill_yanshou");
    public static final DeferredHolder<Item, Item> PILL_XUANYU = registerPill("pill_xuanyu");

    /** 《修真入门》（P1-21，D12）：右键开客户端面板（经 GuideUiBridge；无 clientfx 时静默，命令通道兜底）。 */
    public static final DeferredHolder<Item, Item> GUIDE_INTRO =
            ITEMS.register("item_guide_intro", GuideItem::new);

    /** 异火（EP1，ADR-038 §17.4）：炼丹火种，消耗品。7 种火各有品质偏置与五行亲和。 */
    public static final DeferredHolder<Item, Item> FIRE_MU = register("item_fire_mu");

    public static final DeferredHolder<Item, Item> FIRE_DIXIN = register("item_fire_dixin");
    public static final DeferredHolder<Item, Item> FIRE_XUANBING = register("item_fire_xuanbing");
    public static final DeferredHolder<Item, Item> FIRE_CHIYAN = register("item_fire_chiyan");
    public static final DeferredHolder<Item, Item> FIRE_JIUYOU = register("item_fire_jiuyou");
    public static final DeferredHolder<Item, Item> FIRE_TAIYANG = register("item_fire_taiyang");

    private static DeferredHolder<Item, Item> register(String name) {
        return ITEMS.register(name, () -> new Item(new Item.Properties()));
    }

    private static DeferredHolder<Item, Item> registerPill(String pillId) {
        return ITEMS.register(pillId, () -> StrifePills.item(pillId));
    }

    /** 指引物品：use 全权委托 core 桥（面板在 clientfx；无桥=无操作，D-P0-2 命令兜底）。 */
    public static final class GuideItem extends Item {
        public GuideItem() {
            super(new Item.Properties().stacksTo(1));
        }

        @Override
        public InteractionResultHolder<ItemStack> use(
                Level level,
                net.minecraft.world.entity.player.Player player,
                InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!level.isClientSide()) {
                return InteractionResultHolder.success(stack);
            }
            return com.strife.core.GuideUiBridge.openClient(stack)
                    ? InteractionResultHolder.consume(stack)
                    : InteractionResultHolder.pass(stack);
        }
    }

    private StrifeItems() {}
}

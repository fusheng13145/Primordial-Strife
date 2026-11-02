package com.strife.production;

import com.strife.core.InventoryOps;
import com.strife.core.net.StrifeNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;

/**
 * 炉交互屏的数据构建（ADR-038 交互层）：服务端把"这份配方你能不能炼"一次性算好打包下发， 客户端只渲染——材料够不够、概率多少都由服务端判，客户端不做任何业务（03 §4）。
 *
 * <p>tag schema（production 自钉，{@code AlchemyUiTest} 锚定）：
 *
 * <pre>
 * root: {
 *   furnace: string        # 附近炉名（无炉缺省，客户端显示"无炉加成"）
 *   fire: string           # 已装备异火名（无异火缺省 → 客户端显示"凡火"）
 *   fire_effects: string   # 异火效果摘要（服务端按表拼好，客户端零业务）
 *   recipes: [ { pill: string, craftable: bool,
 *                materials: [ {item, need, have} ], outputs: [ {item, count, prob} ] } ]
 * }
 * </pre>
 *
 * <p>屏上不显示"火候区间"——heat 玩法属 UI 立项，契约承载但不上屏（不假装它参与判定）。
 */
public final class AlchemyUi {

    private AlchemyUi() {}

    /** 右键炼丹炉 / 屏内刷新：组数据并下发。 */
    public static void openFor(ServerPlayer player) {
        StrifeNetwork.sendTo(
                player,
                new com.strife.core.net.StrifeUiPayload(
                        com.strife.core.StrifeUiBridge.Keys.ALCHEMY_SCREEN, build(player)));
    }

    /** 组数据（包内可见供用例直测）：不做任何 I/O，纯读运行表 + 玩家背包。 */
    static CompoundTag build(ServerPlayer player) {
        CompoundTag root = new CompoundTag();
        var furnace = StrifeAlchemyBlocks.findNearbyStats(player);
        root.putString("furnace", furnace == null ? "" : furnace.name());
        appendFire(player, root);
        ListTag recipes = new ListTag();
        var containers =
                new InventoryOps.PlayerContainers(
                        player.getInventory(), player.getEnderChestInventory());
        for (AlchemyRecipe recipe : Alchemy.recipes().values()) {
            recipes.add(row(recipe, containers));
        }
        root.put("recipes", recipes);
        return root;
    }

    /** 异火段：只探测不消耗（与炼制路径的"消耗一枚"区分——看屏不烧火）。 */
    private static void appendFire(ServerPlayer player, CompoundTag root) {
        var containers =
                new InventoryOps.PlayerContainers(
                        player.getInventory(), player.getEnderChestInventory());
        for (int i = 0; i < containers.main().getContainerSize(); i++) {
            var stack = containers.main().getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (key == null || !"strife".equals(key.getNamespace())) {
                continue;
            }
            AlchemyFires.Fire fire = AlchemyFires.byItemId(key.getPath());
            if (fire == null) {
                continue;
            }
            root.putString("fire", fire.name());
            root.putString("fire_effects", describe(fire));
            return;
        }
        root.putString("fire", "");
        root.putString("fire_effects", "");
    }

    /** 异火效果摘要（fires.csv 三效的行内人话；tolerance/affinity 未参与判定不上屏）。 */
    static String describe(AlchemyFires.Fire fire) {
        StringBuilder text = new StringBuilder();
        if (fire.successBonus() > 0) {
            text.append("成功率+").append(percent(fire.successBonus())).append("% ");
        }
        if (fire.qualityShift() > 0) {
            text.append("品质偏置+").append(percent(fire.qualityShift())).append("% ");
        }
        if (fire.wasteReduce() > 0) {
            text.append("废丹回收").append(percent(fire.wasteReduce())).append("%");
        }
        return text.toString().trim();
    }

    private static String percent(double value) {
        return String.format("%.0f", value * 100);
    }

    /** 组配方行（包内可见供用例直测形状）。 */
    static CompoundTag row(AlchemyRecipe recipe, InventoryOps.PlayerContainers containers) {
        CompoundTag row = new CompoundTag();
        row.putString("pill", recipe.id());
        ListTag materials = new ListTag();
        boolean craftable = true;
        for (AlchemyRecipe.Material material : recipe.materials()) {
            long owned = InventoryOps.countOwned(containers, material.itemId());
            CompoundTag entry = new CompoundTag();
            entry.putString("item", material.itemId());
            entry.putInt("need", material.count());
            entry.putLong("have", owned);
            materials.add(entry);
            craftable &= owned >= material.count();
        }
        row.putBoolean("craftable", craftable);
        row.put("materials", materials);
        ListTag outputs = new ListTag();
        for (AlchemyRecipe.Output output : recipe.outputs()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("item", output.itemId());
            entry.putInt("count", output.count());
            entry.putDouble("prob", output.prob());
            outputs.add(entry);
        }
        row.put("outputs", outputs);
        return row;
    }

    /** 客户端读取辅助：ListTag 取出为可迭代（clientfx 不碰 production 类型，只碰 tag）。 */
    public static java.util.List<Tag> tags(CompoundTag root, String key) {
        ListTag list = root.getList(key, Tag.TAG_COMPOUND);
        java.util.List<Tag> out = new java.util.ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            out.add(list.get(i));
        }
        return out;
    }
}

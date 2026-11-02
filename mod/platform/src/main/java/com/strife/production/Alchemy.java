package com.strife.production;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.strife.core.InventoryOps;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 炼制服务（production 统一配方机第一类：炼丹，docs/03 §1 + docs/07 §7 M2"统一配方机（丹）"）。
 *
 * <p>一次炼制：配方存在 → 材料持有校验（一句可读的缺料原因）→ 整体扣除 → 概率抽签 → 发放或失败。 失败损失材料（LORE"丹道九转十不成"），成功按 outputs
 * 发放；随机源生产用 {@link Random}（种子化 仅测试需要），蒙特卡洛测试与真实运行走同一 {@link AlchemyRecipe#roll} 路径。
 *
 * <p>入口是 {@code /strife craft pill <id>} 兜底命令（03 §9 随模块进表）——炼丹炉方块与火候交互 属 UI/内容立项（heat_range
 * 契约已由框架承载），不在本服务内假装实现。
 */
public final class Alchemy {

    private static final Logger LOGGER = LoggerFactory.getLogger("strife/production");

    private static final Map<String, AlchemyRecipe> RECIPES = new LinkedHashMap<>();

    private static final String CONTENT_INDEX_PATH = "data/strife/strife_index.json";

    private static final String PILL_PREFIX = "data/strife/strife_pills/";

    private Alchemy() {}

    /** production 入口构造期调用：路径来自内容索引，索引即真相（读不到即 fail-fast）。 */
    public static synchronized void register() {
        if (!RECIPES.isEmpty()) {
            return;
        }
        RECIPES.putAll(loadRecipes(indexPaths()));
        LOGGER.info("strife production alchemy recipes loaded: {}", RECIPES.keySet());
    }

    /** 索引中属于丹方域的全部产物路径。 */
    static java.util.List<String> indexPaths() {
        try (var stream = Alchemy.class.getResourceAsStream("/" + CONTENT_INDEX_PATH)) {
            if (stream == null) {
                throw new IllegalStateException(CONTENT_INDEX_PATH + " not in jar（DataGen 产物缺失）");
            }
            JsonObject index =
                    JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                            .getAsJsonObject();
            java.util.List<String> paths = new java.util.ArrayList<>();
            for (var element : index.getAsJsonArray("products")) {
                String path = element.getAsString();
                if (path.startsWith(PILL_PREFIX)) {
                    paths.add(path);
                }
            }
            if (paths.isEmpty()) {
                throw new IllegalStateException(
                        "strife_index lists no pills under " + PILL_PREFIX + "（DataGen 产物缺失）");
            }
            return paths;
        } catch (Exception e) {
            throw new IllegalStateException("cannot load " + CONTENT_INDEX_PATH, e);
        }
    }

    /** 只加载给定路径；索引漏掉的丹方不会进入运行表（由 V-INDEX 负责发现漂移）。 */
    static Map<String, AlchemyRecipe> loadRecipes(java.util.List<String> paths) {
        Map<String, AlchemyRecipe> recipes = new LinkedHashMap<>();
        for (String path : paths) {
            try (var stream = Alchemy.class.getResourceAsStream("/" + path)) {
                if (stream == null) {
                    throw new IllegalStateException(path + " not in jar（DataGen 产物缺失）");
                }
                JsonObject product =
                        JsonParser.parseReader(
                                        new InputStreamReader(stream, StandardCharsets.UTF_8))
                                .getAsJsonObject();
                AlchemyRecipe recipe = AlchemyRecipe.parse(product);
                recipes.put(recipe.id(), recipe);
            } catch (Exception e) {
                throw new IllegalStateException("cannot load alchemy recipe " + path, e);
            }
        }
        if (recipes.isEmpty()) {
            throw new IllegalStateException("strife_pills: no pill products in jar");
        }
        return recipes;
    }

    public static Map<String, AlchemyRecipe> recipes() {
        return Map.copyOf(RECIPES);
    }

    /**
     * {@code /strife recipe list|show}：配方机数据的游戏内可查询面。docs/07 §7 M2 的"JEI 配方页"依赖 第三方 mod
     * 依赖治理（blamejared 仓库接入 + 可选依赖策略 + CI 验证）——该治理是独立工单；在 JEI 到位之前，配方在这里全量可查（材料/概率/火候/产出），不是缺口静默。
     */
    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<
                    net.minecraft.commands.CommandSourceStack>
            recipeCommand() {
        return net.minecraft.commands.Commands.literal("recipe")
                .then(ArtifactForge.recipeBranch())
                .then(
                        net.minecraft.commands.Commands.literal("list")
                                .executes(
                                        context -> {
                                            for (AlchemyRecipe recipe : recipes().values()) {
                                                context.getSource()
                                                        .sendSuccess(
                                                                () ->
                                                                        net.minecraft.network.chat
                                                                                .Component.literal(
                                                                                recipe.id()
                                                                                        + "（概率合计 "
                                                                                        + String
                                                                                                .format(
                                                                                                        "%.2f",
                                                                                                        recipe
                                                                                                                .outputs()
                                                                                                                .stream()
                                                                                                                .mapToDouble(
                                                                                                                        AlchemyRecipe
                                                                                                                                        .Output
                                                                                                                                ::prob)
                                                                                                                .sum())
                                                                                        + "）"),
                                                                false);
                                            }
                                            return recipes().size();
                                        }))
                .then(
                        net.minecraft.commands.Commands.literal("show")
                                .then(
                                        net.minecraft.commands.Commands.argument(
                                                        "pillId", StringArgumentType.word())
                                                .executes(
                                                        context -> {
                                                            var source = context.getSource();
                                                            String pillId =
                                                                    StringArgumentType.getString(
                                                                            context, "pillId");
                                                            AlchemyRecipe recipe =
                                                                    recipes().get(pillId);
                                                            if (recipe == null) {
                                                                source.sendFailure(
                                                                        Component.literal(
                                                                                "（未知丹方：" + pillId
                                                                                        + "）"));
                                                                return 0;
                                                            }
                                                            source.sendSuccess(
                                                                    () ->
                                                                            Component.literal(
                                                                                    "丹方 " + pillId),
                                                                    false);
                                                            for (AlchemyRecipe.Material material :
                                                                    recipe.materials()) {
                                                                source.sendSuccess(
                                                                        () ->
                                                                                Component.literal(
                                                                                        "  材料 "
                                                                                                + material
                                                                                                        .itemId()
                                                                                                + " ×"
                                                                                                + material
                                                                                                        .count()),
                                                                        false);
                                                            }
                                                            double failure =
                                                                    1.0
                                                                            - recipe
                                                                                    .outputs()
                                                                                    .stream()
                                                                                    .mapToDouble(
                                                                                            AlchemyRecipe
                                                                                                            .Output
                                                                                                    ::prob)
                                                                                    .sum();
                                                            for (AlchemyRecipe.Output out :
                                                                    recipe.outputs()) {
                                                                source.sendSuccess(
                                                                        () ->
                                                                                Component.literal(
                                                                                        "  产出 "
                                                                                                + out
                                                                                                        .itemId()
                                                                                                + " ×"
                                                                                                + out
                                                                                                        .count()
                                                                                                + "（概率 "
                                                                                                + String
                                                                                                        .format(
                                                                                                                "%.2f",
                                                                                                                out
                                                                                                                        .prob())
                                                                                                + "）"),
                                                                        false);
                                                            }
                                                            double finalFailure = failure;
                                                            source.sendSuccess(
                                                                    () ->
                                                                            Component.literal(
                                                                                    "  火候区间 "
                                                                                            + recipe
                                                                                                    .heatMin()
                                                                                            + "–"
                                                                                            + recipe
                                                                                                    .heatMax()
                                                                                            + "，失手率 "
                                                                                            + String
                                                                                                    .format(
                                                                                                            "%.2f",
                                                                                                            finalFailure)),
                                                                    false);
                                                            return 1;
                                                        })));
    }

    /** 在背包中扫火种物品（strife:item_fire_*）；找到返回该堆叠，否则 null。火种→表条目映射在 AlchemyFires。 */
    private static ItemStack findFireStack(InventoryOps.PlayerContainers containers) {
        for (int i = 0; i < containers.main().getContainerSize(); i++) {
            var stack = containers.main().getItem(i);
            if (stack.isEmpty()) continue;
            var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (key != null
                    && "strife".equals(key.getNamespace())
                    && key.getPath().startsWith("item_fire_")
                    && AlchemyFires.byItemId(key.getPath()) != null) {
                return stack;
            }
        }
        return null;
    }

    public static void craft(ServerPlayer player, String pillId) {
        AlchemyRecipe recipe = RECIPES.get(pillId);
        if (recipe == null) {
            player.displayClientMessage(Component.literal("（未知丹方：" + pillId + "）"), true);
            return;
        }
        InventoryOps.PlayerContainers containers =
                new InventoryOps.PlayerContainers(
                        player.getInventory(), player.getEnderChestInventory());

        // 材料校验：逐项给出缺口（"缺什么差多少"可解释，不笼统说材料不足）
        for (AlchemyRecipe.Material material : recipe.materials()) {
            long owned = InventoryOps.countOwned(containers, material.itemId());
            if (owned < material.count()) {
                player.displayClientMessage(
                        Component.literal(
                                "（药材不足："
                                        + material.itemId()
                                        + " 现有 "
                                        + owned
                                        + "，需 "
                                        + material.count()
                                        + "）"),
                        true);
                return;
            }
        }
        // 整体扣除（校验已过，逐项扣足）
        for (AlchemyRecipe.Material material : recipe.materials()) {
            long removed = InventoryOps.remove(containers, material.itemId(), material.count());
            if (removed != material.count()) {
                throw new IllegalStateException(
                        "alchemy material removal mismatch: "
                                + material.itemId()
                                + " want "
                                + material.count()
                                + " got "
                                + removed);
            }
        }

        // ── ADR-038：炉加成（furnaces.json 产物）+ 异火四效（fires.json 产物，AlchemyFires 表驱动）──
        var furnace = StrifeAlchemyBlocks.findNearbyStats(player);
        double secondChance = furnace != null ? furnace.successBonus() : 0.0;
        String furnaceName = furnace != null ? furnace.name() : null;

        // 异火：扫背包找 item_fire_* 火种，命中表条目则消耗一枚；无火种按凡火兜底
        AlchemyFires.Fire fire = AlchemyFires.NORMAL;
        ItemStack fireStack = findFireStack(containers);
        if (fireStack != null) {
            var key =
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
                            fireStack.getItem());
            AlchemyFires.Fire found = key == null ? null : AlchemyFires.byItemId(key.getPath());
            if (found != null) {
                fire = found;
                fireStack.shrink(1);
            }
        }
        double totalBonus = secondChance + fire.successBonus();

        // 品质偏置：异火 quality_shift 重排产出分布（成功/失败总质量不动），原分布与偏置分布共用一条抽签路径
        var pool = AlchemyRecipe.qualityShifted(recipe.outputs(), fire.qualityShift());

        // 概率抽签 → 发放或失败（失败损失材料）
        var output = AlchemyRecipe.rollList(pool, new Random()::nextInt);
        if (output.isEmpty() && totalBonus > 0) {
            // 炉/异火二次判定：加成率作为"第二把火"的成功率
            var retry = new Random();
            if (retry.nextInt(1000) < (int) (totalBonus * 1000)) {
                output = AlchemyRecipe.rollList(pool, retry::nextInt);
            }
        }
        if (output.isEmpty()) {
            // 废丹回收（紫金鼎/龙纹鼎无条件回收；异火 waste_reduce 概率性追回 1 主材）
            int recycled = 0;
            var mat = recipe.materials().get(0);
            if (furnace != null && furnace.wasteRecycle() > 0) {
                recycled += Math.min(furnace.wasteRecycle(), mat.count());
            }
            if (fire.wasteReduce() > 0
                    && new Random().nextInt(1000) < (int) (fire.wasteReduce() * 1000)) {
                recycled += 1;
            }
            int finalRecycled = Math.min(recycled, mat.count());
            if (finalRecycled > 0) {
                int count = finalRecycled;
                InventoryOps.resolve(mat.itemId())
                        .ifPresent(item -> player.getInventory().add(new ItemStack(item, count)));
            }
            String wasteMsg = "丹火失控——药材已毁，这一炉没有成丹。";
            if (furnaceName != null && furnace.wasteRecycle() > 0) {
                wasteMsg += "（" + furnaceName + "回收了部分材料）";
            }
            player.displayClientMessage(
                    Component.literal(wasteMsg).withStyle(net.minecraft.ChatFormatting.DARK_RED),
                    false);
            return;
        }
        String bonusMsg = "";
        if (furnaceName != null) bonusMsg += " · " + furnaceName;
        if (fire != AlchemyFires.NORMAL) bonusMsg += " · " + fire.name();
        AlchemyRecipe.Output out = output.get();
        InventoryOps.resolve(out.itemId())
                .ifPresentOrElse(
                        item -> player.getInventory().add(new ItemStack(item, (int) out.count())),
                        () ->
                                player.displayClientMessage(
                                        Component.literal("（产出 " + out.itemId() + " 尚未注册）"), true));
        player.displayClientMessage(
                Component.literal("✦ 丹成：" + out.itemId() + " ×" + out.count())
                        .withStyle(net.minecraft.ChatFormatting.GOLD),
                false);
    }
}

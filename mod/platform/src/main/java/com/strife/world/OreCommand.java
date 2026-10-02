package com.strife.world;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * {@code /strife world ore status}：矿石表与方块注册的运行时自检（M4 兜底命令，docs/03 §9）。
 *
 * <p><b>为什么需要这条命令</b>：M4 矿石链的每个断点都是<b>静默</b>的——原版找不到配置特征就不生成矿、找不到掉落表就 掉空气、群系 ID
 * 拼错就不在那个群系出现。全都不报错，玩家只看到"这地方没矿"。而开服冒烟、单测、Validator 三条通道都<b>不验证"世界里到底有没有"</b>（它们分别只看日志、只看
 * JSON、只看表）。这条命令把已加载的矿石表 与已注册的方块常量、并排打出来，让"表里有行"与"代码里有方块"这两个事实能被人在游戏里直接对齐。
 *
 * <p><b>它验什么、不验什么</b>：验表↔代码↔资产三方一致性（表有行但没注册常量 = 真实缺口，显式报红而不是跳过； 掉落表/lang
 * 词条缺失同样点名）。<b>不验</b>"矿是否已在世界里生成"——那要造区块采样，超出兜底命令职责， 验它得靠游戏内实测或 {@code /locate}。
 *
 * <p>为什么 lang 检查要真读文件：缺词条时游戏里显示的是 {@code block.strife.block_ore_lingyu} 这样的原始 key，
 * 玩家看到的是一串标识符。只检查"lang 文件存在"会把这种破损当成通过，所以这里解析 JSON 并按 key 精确查找。
 */
final class OreCommand {

    /** 词条前缀：契约 §4.10 登记的方块 lang 键形状。 */
    private static final String BLOCK_LANG_PREFIX = "block.strife.";

    private static final ResourceLocation ZH_CN =
            ResourceLocation.fromNamespaceAndPath("strife", "lang/zh_cn.json");

    private OreCommand() {}

    /** 挂到 {@code /strife world} 下的子树（由 {@link StrifeWorld} 构造期注册进 core 的挂载点）。 */
    static LiteralArgumentBuilder<CommandSourceStack> subtree() {
        return Commands.literal("world")
                .then(
                        Commands.literal("ore")
                                .then(Commands.literal("status").executes(OreCommand::status)));
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        source.sendSuccess(() -> title("矿石表状态（M4 数据驱动链）"), false);

        OreTables tables = OreTables.getOrNull(server);
        if (tables == null) {
            // getOrNull 已记 ERROR；这里再补一条玩家可见的失败，因为"日志里有一行 ERROR"不是排查入口。
            source.sendFailure(Component.literal("矿石表加载失败：见服务端日志 strife/world 的 ERROR 行"));
            return 0;
        }

        List<OreTables.OreSpec> ores = tables.all();
        List<String> missingBlock = new ArrayList<>();
        List<String> missingLoot = new ArrayList<>();
        List<String> missingLang = new ArrayList<>();
        List<String> unknownBiome = new ArrayList<>();
        JsonObject lang = readLang(server);
        Registry<Biome> biomes = server.registryAccess().registryOrThrow(Registries.BIOME);

        for (OreTables.OreSpec spec : ores) {
            if (StrifeOreBlocks.holder(spec.id()) == null) {
                missingBlock.add(spec.id());
            }
            if (!lootTableExists(server, spec.dropTable())) {
                missingLoot.add(spec.id() + " → " + spec.dropTable());
            }
            if (lang == null || !lang.has(BLOCK_LANG_PREFIX + spec.id())) {
                missingLang.add(spec.id());
            }
            for (String biomeId : spec.placement().biomes()) {
                if (biomes.getOptional(ResourceLocation.parse(biomeId)).isEmpty()) {
                    unknownBiome.add(spec.id() + " → " + biomeId);
                }
            }
            source.sendSuccess(() -> detail(spec), false);
        }

        source.sendSuccess(
                () ->
                        title(
                                String.format(
                                        "合计 %d 种：方块已注册 %d，掉落表齐备 %d，lang 齐备 %d，群系 ID 全部存在 %s",
                                        ores.size(),
                                        ores.size() - missingBlock.size(),
                                        ores.size() - missingLoot.size(),
                                        ores.size() - missingLang.size(),
                                        unknownBiome.isEmpty() ? "是" : "否")),
                false);
        source.sendSuccess(
                () ->
                        note(
                                "实际矿脉数 = veins_per_chunk × density_ratio 向上取整（保底 1）："
                                        + "表里写 0.15 的语义是「稀」不是「没有」"),
                false);
        source.sendSuccess(() -> note("高度列是绝对 y，已在生成期换算为原版相对高度（基线 −64）；掉落表路径由方块注册名推导"), false);
        source.sendSuccess(() -> note("本命令不验「矿是否已在世界里生成」——那要造区块采样，请用镐实测或 /locate"), false);

        // 四类缺口逐条点名而不是只报计数：策划要能直接看出「哪一栏没填」。
        reportGaps(source, "表里有行但代码未注册方块", missingBlock);
        reportGaps(source, "掉落表产物缺失（该矿会掉空气）", missingLoot);
        reportGaps(source, "lang 词条缺失（游戏内显示为原始 key）", missingLang);
        reportGaps(source, "群系 ID 不存在（该矿在这些群系不生成）", unknownBiome);

        return missingBlock.isEmpty()
                        && missingLoot.isEmpty()
                        && missingLang.isEmpty()
                        && unknownBiome.isEmpty()
                ? 1
                : 0;
    }

    private static void reportGaps(CommandSourceStack source, String title, List<String> gaps) {
        if (gaps.isEmpty()) {
            source.sendSuccess(() -> ok(title), false);
            return;
        }
        source.sendSuccess(() -> bad(title + "：" + String.join("、", gaps)), false);
    }

    /**
     * 单矿详情一行。
     *
     * <p>密度与折算后的实际矿脉数并列，让「表里写 0.45」与「世界里实际放 2 条」在一行输出里就能对上——这是密度 倍率语义唯一能被人眼验证的地方。群系只报数量不逐个列：原版群系 ID
     * 很长，逐个列会把命令输出冲成十几行。
     */
    private static Component detail(OreTables.OreSpec spec) {
        OreTables.Placement placement = spec.placement();
        int effectiveVeins =
                Math.max(1, (int) Math.ceil(placement.veinsPerChunk() * spec.densityRatio()));
        return Component.literal(
                        String.format(
                                "%s [%s] 群系 %d 种 高度 y[%d..%d] 每区块 %d×%.2f → %d 条 矿脉 %d 格 掉落 %s",
                                spec.id(),
                                spec.element(),
                                placement.biomes().size(),
                                placement.yMin(),
                                placement.yMax(),
                                placement.veinsPerChunk(),
                                spec.densityRatio(),
                                effectiveVeins,
                                placement.veinSize(),
                                spec.dropTable()))
                .withStyle(ChatFormatting.GRAY);
    }

    /**
     * 掉落表是否真的被原版加载。
     *
     * <p>用原版自己的 {@code getLootTable} 而不是查资源目录：这张表存在但解析失败时，原版会给一张空表，此时资源 明明在、掉落却是空气——只查目录会把最坏情况判成通过。
     */
    private static boolean lootTableExists(MinecraftServer server, String dropTable) {
        ResourceKey<LootTable> key =
                ResourceKey.create(
                        Registries.LOOT_TABLE,
                        ResourceLocation.fromNamespaceAndPath("strife", dropTable));
        return server.reloadableRegistries().getLootTable(key) != LootTable.EMPTY;
    }

    /** 读 {@code assets/strife/lang/zh_cn.json}；读不到返回 null（此时所有词条判为缺失，不静默当通过）。 */
    private static JsonObject readLang(MinecraftServer server) {
        return server.getResourceManager()
                .getResource(ZH_CN)
                .map(
                        resource -> {
                            try (InputStreamReader reader =
                                    new InputStreamReader(
                                            resource.open(), StandardCharsets.UTF_8)) {
                                return JsonParser.parseReader(reader).getAsJsonObject();
                            } catch (Exception e) {
                                return null;
                            }
                        })
                .orElse(null);
    }

    private static Component title(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GOLD);
    }

    private static Component note(String text) {
        return Component.literal("  " + text).withStyle(ChatFormatting.DARK_GRAY);
    }

    private static Component ok(String text) {
        return Component.literal("  [OK] " + text).withStyle(ChatFormatting.GREEN);
    }

    private static Component bad(String text) {
        return Component.literal("  [缺] " + text).withStyle(ChatFormatting.RED);
    }
}

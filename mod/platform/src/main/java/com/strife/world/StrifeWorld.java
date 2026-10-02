package com.strife.world;

import com.strife.core.CultivationFactors;
import com.strife.core.StrifeCommands;
import com.strife.core.StrifeMod;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * world 分侧入口（docs/03 §7 模块入口模式）。
 *
 * <p>本入口编排四件事：
 *
 * <ol>
 *   <li>草类方块破坏的草药掉落（{@link HerbDrops}）；
 *   <li><b>灵气浓度场</b>——通过 {@link CultivationFactors#registerEnvironment} 把环境系数接进 05 §2 的四因子公式， realm
 *       侧一行不用改；
 *   <li><b>矿石方块注册与自检命令</b>——注册 {@link StrifeOreBlocks} 的方块/物品，并挂 {@code /strife world ore
 *       status}（{@link OreCommand}）把已加载的矿石表与已注册方块并排打出来；
 *   <li><b>矿石进创造物品栏</b>——挂原版「自然方块」标签页，见 {@link #addCreativeTabEntries}。
 * </ol>
 *
 * <p>场按"世界种子 + 维度"缓存：同一个存档的同一维度共用一个场，换存档（单机切世界）会得到新场；噪声由世界种子决定，因此 同一存档里同一个地方的灵气永远一样。
 *
 * <p>矿石的<b>生成与掉落是数据驱动的</b>（{@code tables/ores.csv} → 五份原版 JSON），本入口只注册方块本身—— 方块属性（硬度、材质色、声音）无法用
 * datapack 表达，而"哪种矿长在哪"可以，两者的分工见 {@link StrifeOreBlocks} 类注释。
 */
@Mod(value = StrifeMod.MOD_ID)
public final class StrifeWorld {

    private static final Logger LOGGER = LoggerFactory.getLogger("strife/world");

    /**
     * 上界维度（M4，ADR-021）：本体是数据包维度（{@code data/strife/dimension/upper_realm.json}，由 {@code
     * tables/dimensions.csv} DataGen 产出），此处只引用其 ID 做灵气参数分支——world 侧对维度的全部职责 就是「提供参数」，注册归 core
     * 治理体系（ADR-021 归属线）。
     */
    static final ResourceKey<Level> UPPER_REALM =
            ResourceKey.create(
                    Registries.DIMENSION,
                    ResourceLocation.fromNamespaceAndPath(StrifeMod.MOD_ID, "upper_realm"));

    /** 场缓存：键 = 世界种子 + 维度 ID（换存档必须换场，否则新世界会沿用旧世界的灵气分布）。 */
    private static final Map<String, AmbientQiField> FIELDS = new ConcurrentHashMap<>();

    private static volatile String loggedFailure;

    public StrifeWorld(IEventBus modEventBus, ModContainer container) {
        LOGGER.info(
                "strife world entry constructed (version {})", container.getModInfo().getVersion());
        StrifeOreBlocks.BLOCKS.register(modEventBus);
        StrifeOreBlocks.ITEMS.register(modEventBus);
        StrifeRealmBlocks.BLOCKS.register(modEventBus);
        StrifeRealmBlocks.ITEMS.register(modEventBus);
        // 宗门结构：结构本体 + codec 类型 + 建筑件类型，三者缺一结构都不会生成（JSON_SCHEMA §4.13）。
        SectStructures.STRUCTURES.register(modEventBus);
        SectStructures.STRUCTURE_TYPES.register(modEventBus);
        SectStructures.PIECES.register(modEventBus);
        modEventBus.addListener(StrifeWorld::addCreativeTabEntries);
        NeoForge.EVENT_BUS.addListener(HerbDrops::onBreakBlock);
        NeoForge.EVENT_BUS.addListener(AmbientQiWarmup::onChunkLoad);
        CultivationFactors.registerEnvironment(StrifeWorld::environmentCoefficient);
        StrifeCommands.MODULE_SUBTREES.add(OreCommand.subtree());
        StrifeCommands.MODULE_SUBTREES.add(RealmCommand.subtree());
        LOGGER.info("strife world wired cultivation factor: environment=ambient qi field");
        LOGGER.info(
                "strife world registered ore blocks: {}",
                String.join(", ", StrifeOreBlocks.registeredIds()));
        LOGGER.info(
                "strife world commands: /strife world ore status | /strife world realm [go upper|overworld]");
    }

    /**
     * 把矿石物品挂进原版「自然方块」创造标签页（与煤矿/铁矿同栏）。
     *
     * <p>NeoForge 1.13+ 起，注册 {@link net.minecraft.world.item.BlockItem} 不会自动出现在创造物品栏——不挂 tab，
     * 玩家在创造模式搜「灵玉」「block_ore」都搜不到（2026-10-02 真机实证的真实缺口）。搜索走物品的翻译键， {@code block.strife.block_ore_*}
     * 已配中文（矿石表状态命令 lang 齐备 3 实证），挂进 tab 后按中文名即可搜到。
     */
    private static void addCreativeTabEntries(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != CreativeModeTabs.NATURAL_BLOCKS) {
            return;
        }
        event.accept(StrifeOreBlocks.ORE_LINGYU.get());
        event.accept(StrifeOreBlocks.ORE_CHIYAN.get());
        event.accept(StrifeOreBlocks.ORE_HANYU.get());
        event.accept(StrifeRealmBlocks.QI_CRYSTAL.get());
    }

    /**
     * 环境系数（05 §2 公式的第二项）：玩家所在区块的灵气浓度。
     *
     * <p>表不可用时返回中性 1.0 并记一次日志——1.0 落在 {@code ambient_qi_min..max} 区间内，因此玩家感知是"此地灵气平平"，
     * 而不是"修炼坏了"；真正的故障信号是那条 ERROR。
     */
    static double environmentCoefficient(ServerPlayer player) {
        AmbientQiField field = fieldFor(player.serverLevel());
        if (field == null) {
            return 1.0;
        }
        BlockPos pos = player.blockPosition();
        return field.ratioAtBlock(pos.getX(), pos.getZ());
    }

    /** 该维度当前的灵气场；表不可用时返回 null（并只报一次错）。 */
    static AmbientQiField fieldFor(ServerLevel level) {
        String key = level.getSeed() + ":" + level.dimension().location();
        AmbientQiField cached = FIELDS.get(key);
        if (cached != null) {
            return cached;
        }
        WorldTables tables = WorldTables.getOrNull(level.getServer());
        if (tables == null) {
            String message = "world tables unavailable — 灵气场退化为中性系数 1.0";
            if (!message.equals(loggedFailure)) {
                loggedFailure = message;
                LOGGER.error(message);
            }
            return null;
        }
        // 维度参数分支（ADR-021）：上界灵气浓度整体高于人界（NUMBERS @@world upper_qi_*），其余维度用人界区间。
        WorldTables.Ambient params =
                level.dimension().equals(UPPER_REALM) ? tables.upperAmbient() : tables.ambient();
        AmbientQiField built = build(level.dimension(), level.getSeed(), params);
        FIELDS.put(key, built);
        LOGGER.info(
                "ambient qi field ready for {}: [{}, {}], region={} chunks, refine={}",
                level.dimension().location(),
                built.min(),
                built.max(),
                tables.ambient().regionChunks(),
                tables.ambient().refineWeight());
        return built;
    }

    /** 用世界种子构造两个独立噪声（粗粒度层与细化层各有各的排列，否则两层会同相叠加成一条直线）。 */
    static AmbientQiField build(
            ResourceKey<Level> dimension, long worldSeed, WorldTables.Ambient ambient) {
        long seed = worldSeed ^ dimension.location().toString().hashCode();
        RandomSource random = RandomSource.create(seed);
        return new AmbientQiField(
                new ImprovedNoise(random)::noise,
                new ImprovedNoise(random)::noise,
                ambient.min(),
                ambient.max(),
                ambient.regionChunks(),
                ambient.refineWeight());
    }

    /** 仅测试用：清空场缓存。 */
    static void clearFieldsForTest() {
        FIELDS.clear();
        loggedFailure = null;
    }
}

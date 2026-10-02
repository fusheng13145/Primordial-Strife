package com.strife.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 矿石表（M4：{@code tables/ores.csv} → {@code data/strife/strife_ores/<id>.json}，content/JSON_SCHEMA
 * §4.8）。
 *
 * <p>与 {@link WorldTables} 同一通道与懒加载口径，但<b>不共用一个类</b>：{@code WorldTables} 是单块 YAML 派生的 运行时参数表（一个
 * json、一个 record），矿石表是"一章多行、每行一个产物 id"的资源表，索引方式与失败语义都不同 （前者缺键即抛，后者是遍历全部产物）。
 *
 * <p><b>严格未知字段</b>：每行只认契约 §4.8 的六个键，出现别的键立即抛错并报出文件路径与键名。这与 {@code SpellTables}/{@code
 * AlchemyRecipe} 同一纪律——产物是 DataGen 写的，出现未知字段只有两种可能：契约改了而 解析器没跟上（该重新生成/该改解析器），或有人手改了 @generated
 * 产物（该红掉）。两种都不该静默接受。
 *
 * <p><b>为什么解析器不碰注册表</b>（群系只存 ID 字符串）：1.21.1 里 {@code BuiltInRegistries} <b>没有</b> {@code BIOME}
 * 字段——群系是动态注册表，只能经 {@code server.registryAccess().registryOrThrow(Registries.BIOME)} 访问，
 * 而那需要已启动的服务端。让纯解析器依赖它会带来两个坏处：用例必须起服才能跑（与本仓"真实产物当夹具"的写法冲突）， 且加载期与运行期的失败时机混在一起。因此分工是：本类只保证 ID
 * <b>形状</b>合法（{@code ns:path}）， "ID 是否真实存在"由 {@link OreCommand} 在有注册表的运行时核对并逐条点名——那才是能给出"哪个群系 ID
 * 拼错了"的 地方，也才是玩家真正会踩到的坑。
 */
public final class OreTables {

    private static final Logger LOGGER = LoggerFactory.getLogger("strife/world");

    /** 产物目录（DataGen 的输出路径，改动须与 {@code OreGenerator} 同 PR）。 */
    private static final String PRODUCT_DIR = "strife_ores";

    /** 契约 §4.8 允许的键，顺序即产物字段顺序。 */
    private static final List<String> KNOWN_KEYS =
            List.of(
                    "id",
                    "element",
                    "placement",
                    "density_ratio",
                    "drop_table",
                    "regen_period_key");

    /**
     * 矿脉放置参数（契约 §4.8 {@code placement}）。
     *
     * @param biomes 允许生成的生物群系（资源 ID 字符串，<b>不</b>在此处解析成注册表对象——见类注释"为什么解析器不碰注册表"）
     * @param yMin 最低生成高度（含）
     * @param yMax 最高生成高度（含）
     * @param veinsPerChunk 每区块矿脉数
     * @param veinSize 单条矿脉方块数
     */
    public record Placement(
            List<String> biomes, int yMin, int yMax, int veinsPerChunk, int veinSize) {}

    /**
     * 一行矿石的完整定义。
     *
     * @param id 内容 ID（= 运行时方块/物品注册名，ADR-017）
     * @param element 五行属性（{@code jin/mu/shui/huo/tu}）——矿石亲和，是"什么属性的矿"的唯一口径
     * @param placement 放置参数
     * @param densityRatio 密度倍率 ∈(0,1]
     * @param dropTable 原版 loot 2 表路径（{@code blocks/<方块ID>}，见 {@link
     *     #requireDropTableMatchesBlockId} 的 1.21.1 源码依据）
     * @param regenPeriodKey H5 再生周期键；本期恒 null
     */
    public record OreSpec(
            String id,
            String element,
            Placement placement,
            double densityRatio,
            String dropTable,
            String regenPeriodKey) {}

    private final Map<String, OreSpec> ores = new LinkedHashMap<>();

    private static volatile OreTables instance;

    private OreTables(ResourceManager resources) {
        for (String path : listProductFiles(resources)) {
            OreSpec spec = parseSpec(resources, path);
            ores.put(spec.id(), spec);
        }
        if (ores.isEmpty()) {
            throw new IllegalStateException(
                    "no ore products under data/strife/"
                            + PRODUCT_DIR
                            + " — OreGenerator 未跑？矿石表是 M4 交付物，空表意味着世界生成会静默不生成任何矿");
        }
    }

    /**
     * 列出矿石产物路径。目录是 jar 内资源，不能用文件系统列举，只能问 {@link ResourceManager}——它按命名空间索引， 返回 {@code
     * strife:strife_ores/} 下的全部条目。
     */
    private static List<String> listProductFiles(ResourceManager resources) {
        List<String> paths = new ArrayList<>();
        // 1.21.1 的 listResources 收的是 data/<命名空间>/ 之下的**相对路径**（纯 "strife_ores"），不是
        // "命名空间:路径"——带冒号会被每个资源包判 Invalid path（真机 2026-10-02 18:46 日志实证：5 条
        // "Invalid path strife:strife_ores"），返回空表。命名空间限定交给下面的过滤器做。
        resources
                .listResources(
                        PRODUCT_DIR,
                        location ->
                                location.getNamespace().equals("strife")
                                        && location.getPath().startsWith(PRODUCT_DIR + "/")
                                        && location.getPath().endsWith(".json"))
                .forEach((location, resource) -> paths.add(location.getPath()));
        paths.sort(String::compareTo);
        return paths;
    }

    private static OreSpec parseSpec(ResourceManager resources, String path) {
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath("strife", path);
        Resource resource =
                resources
                        .getResource(location)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "data/strife/"
                                                        + path
                                                        + " vanished between listing and reading"));
        JsonObject json = readObject(resource, path);
        return parseSpec(json, path);
    }

    /**
     * 解析单份矿石产物，包可见以便用例直接喂<b>真实产物</b>（与 {@code RealmTables.parseRules} 同一手法）。
     *
     * <p>为什么值得单独拆出来：这一层的缺陷形态是"解析器与产物对不上"，而它<b>只在矿被挖的那一刻</b>发作——开服冒烟
     * 不挖矿，照不出来。手写夹具又永远与解析器同步演进，所以输入必须是 DataGen 出的那份 JSON。
     */
    static OreSpec parseSpec(JsonObject json, String path) {
        rejectUnknown(json, path);
        String id = requireString(json, "id", path);
        String element = requireString(json, "element", path);
        Placement placement = parsePlacement(json.getAsJsonObject("placement"), path);
        double density = requireDouble(json, "density_ratio", path);
        if (!(density > 0.0 && density <= 1.0)) {
            throw new IllegalStateException(
                    "data/strife/"
                            + path
                            + ": density_ratio="
                            + density
                            + " outside (0,1] (JSON_SCHEMA §4.8)");
        }
        String dropTable = requireString(json, "drop_table", path);
        requireDropTableMatchesBlockId(dropTable, id, path);
        // 本期 regen_period_key 恒 null（H5 未实现）；写非 null 时不静默忽略——它意味着"表作者以为再生周期已生效"。
        String regen =
                json.has("regen_period_key") && !json.get("regen_period_key").isJsonNull()
                        ? json.get("regen_period_key").getAsString()
                        : null;
        if (regen != null) {
            throw new IllegalStateException(
                    "data/strife/"
                            + path
                            + ": regen_period_key='"
                            + regen
                            + "' but H5 周期事件未实现——本期该键必须为 null（矿石不重生），"
                            + "否则表作者会以为矿会再生");
        }
        return new OreSpec(id, element, placement, density, dropTable, null);
    }

    /** 解析 {@code placement}。 */
    static Placement parsePlacement(JsonObject placement, String path) {
        if (placement == null) {
            throw new IllegalStateException(
                    "data/strife/" + path + ": missing 'placement' (JSON_SCHEMA §4.8)");
        }
        JsonArray biomes = placement.getAsJsonArray("biomes");
        if (biomes == null || biomes.isEmpty()) {
            throw new IllegalStateException(
                    "data/strife/" + path + ": placement.biomes is empty — 该矿在任何群系都不生成");
        }
        List<String> ids = new ArrayList<>(biomes.size());
        for (JsonElement element : biomes) {
            String raw = element.getAsString();
            if (raw.isBlank() || ResourceLocation.tryParse(raw) == null) {
                throw new IllegalStateException(
                        "data/strife/"
                                + path
                                + ": placement.biomes has an invalid id '"
                                + raw
                                + "'（必须是 命名空间:路径 形式；ID 拼错会让该矿在整个群系静默不生成）");
            }
            ids.add(raw);
        }
        int yMin = requireInt(placement, "y_min", path, "placement");
        int yMax = requireInt(placement, "y_max", path, "placement");
        if (yMax < yMin) {
            throw new IllegalStateException(
                    "data/strife/" + path + ": placement y_max=" + yMax + " < y_min=" + yMin);
        }
        int veins = requireInt(placement, "veins_per_chunk", path, "placement");
        int size = requireInt(placement, "vein_size", path, "placement");
        if (veins < 1 || size < 1) {
            throw new IllegalStateException(
                    "data/strife/"
                            + path
                            + ": placement veins_per_chunk="
                            + veins
                            + " / vein_size="
                            + size
                            + " must both be >= 1（0 会让原版放矿逻辑不生成或空转）");
        }
        return new Placement(List.copyOf(ids), yMin, yMax, veins, size);
    }

    private static void rejectUnknown(JsonObject json, String path) {
        for (String key : json.keySet()) {
            if (key.equals("@generated") || KNOWN_KEYS.contains(key)) {
                continue;
            }
            throw new IllegalStateException(
                    "data/strife/"
                            + path
                            + ": unknown field '"
                            + key
                            + "' in ore（JSON_SCHEMA §4.8 只认 "
                            + KNOWN_KEYS
                            + "；出现别的键要么契约改了、要么产物被手改过）");
        }
    }

    private static String requireString(JsonObject json, String key, String path) {
        JsonElement value = json.get(key);
        if (value == null || value.isJsonNull() || value.getAsString().isBlank()) {
            throw new IllegalStateException(
                    "data/strife/" + path + ": missing '" + key + "' (JSON_SCHEMA §4.8 必填)");
        }
        return value.getAsString();
    }

    private static double requireDouble(JsonObject json, String key, String path) {
        JsonElement value = json.get(key);
        if (value == null || !value.isJsonPrimitive()) {
            throw new IllegalStateException(
                    "data/strife/"
                            + path
                            + ": missing numeric '"
                            + key
                            + "' (JSON_SCHEMA §4.8 必填)");
        }
        return value.getAsDouble();
    }

    private static int requireInt(JsonObject json, String key, String path, String where) {
        JsonElement value = json.get(key);
        if (value == null || !value.isJsonPrimitive()) {
            throw new IllegalStateException(
                    "data/strife/"
                            + path
                            + ": missing numeric "
                            + where
                            + "."
                            + key
                            + " (JSON_SCHEMA §4.8 必填)");
        }
        return value.getAsInt();
    }

    private static JsonObject readObject(Resource resource, String path) {
        try (InputStream stream = resource.open();
                InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonObject object = JsonParser.parseReader(reader).getAsJsonObject();
            if (object.get("@generated") == null) {
                throw new IllegalStateException(
                        "data/strife/" + path + " missing @generated header（产物必须由 DataGen 写出）");
            }
            return object;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("cannot read data/strife/" + path, e);
        }
    }

    /**
     * 校验 {@code drop_table} 与方块注册名一致（1.21.1 源码实证，见类注释）。
     *
     * <p>1.21.1 的 {@code BlockBehaviour} 构造器里，{@code Properties} 未显式指定掉落表时走 {@code
     * BuiltInRegistries.BLOCK.getKey(block).withPrefix("blocks/")}；而 {@code Properties} 没有公开的
     * lootTable setter（只有 {@code noLootTable()} / {@code dropsLike(Block)} / {@code
     * lootFrom(Supplier)}），{@code Block.getLootTable()} 又是 {@code
     * final}。三者合起来意味着<b>掉落表路径由注册名唯一确定</b>：产物里写别的路径，
     * 原版找不到表就<b>静默不掉落</b>——矿挖了掉空气，而且不报错。因此这条校验是运行时最后一道防线（DataGen 侧
     * 已在生成时比对过一次，这里防的是产物被手改或表与代码注册名漂移）。
     */
    private static void requireDropTableMatchesBlockId(String dropTable, String id, String path) {
        String expected = "blocks/" + id;
        if (!expected.equals(dropTable)) {
            throw new IllegalStateException(
                    "data/strife/"
                            + path
                            + ": drop_table='"
                            + dropTable
                            + "' but the block id is '"
                            + id
                            + "'（1.21.1 方块掉落表路径由注册名推导为 blocks/<方块ID>，"
                            + "Properties 无公开 lootTable setter 且 Block.getLootTable() 为 final）："
                            + "写成别的路径原版找不到表，会静默不掉落（掉空气），此处应为 '"
                            + expected
                            + "'");
        }
    }

    /** 掉落路径的容错形态：表加载失败时返回 null（不生成矿），并记一次 ERROR。 */
    public static OreTables getOrNull(MinecraftServer server) {
        try {
            return get(server);
        } catch (RuntimeException e) {
            LOGGER.error("ore tables unavailable: {}", e.getMessage());
            return null;
        }
    }

    public static OreTables get(MinecraftServer server) {
        OreTables tables = instance;
        if (tables == null) {
            synchronized (OreTables.class) {
                tables = instance;
                if (tables == null) {
                    instance = tables = new OreTables(server.getResourceManager());
                }
            }
        }
        return tables;
    }

    static OreTables loadForTest(ResourceManager resources) {
        OreTables tables = new OreTables(resources);
        instance = tables;
        return tables;
    }

    public static void invalidate() {
        instance = null;
    }

    /** 全部矿石（按产物路径排序，顺序确定）。 */
    public List<OreSpec> all() {
        return List.copyOf(ores.values());
    }

    public OreSpec ore(String id) {
        return ores.get(id);
    }
}

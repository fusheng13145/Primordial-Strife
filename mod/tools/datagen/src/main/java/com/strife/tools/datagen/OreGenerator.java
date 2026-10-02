package com.strife.tools.datagen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code tables/ores.csv} → M4 矿石的五个产物（content/JSON_SCHEMA.md §4.8）。
 *
 * <p><b>为什么一行出五个文件</b>：M4 的"矿石 placement"在原版 1.21 世界生成里是一条链——configured feature（矿的形状 与替换规则）→ placed
 * feature（放几次、多大、在什么高度、用什么噪声）→ biome modifier（挂到哪些群系的哪一步）→ 掉落表 → 加上 strife
 * 侧的契约镜像。四个环节各是原版注册表里的一个目录，任何一环缺失矿石都不生成，而且 <b>缺失时原版不报错</b>（它只是找不到那条链）。所以 DataGen
 * 必须把整条链一起写出来，让"表里有的矿一定在世界里 出现、且破坏一定掉东西"成为构建期事实，而不是运行时祈祷。
 *
 * <p>产物清单（每行矿）：
 *
 * <ol>
 *   <li>{@code data/strife/worldgen/configured_feature/<id>.json} —— 矿块 + 替换规则（stone → 矿）；
 *   <li>{@code data/strife/worldgen/placed_feature/<id>.json} —— 矿脉数/尺寸/高度/噪声，其中矿脉数乘 {@code
 *       density_ratio} 后取整（契约 §4.8 的密度倍率语义）；
 *   <li>{@code data/strife/worldgen/biome_modifier/<id>.json} —— 挂到 {@code placement.biomes}
 *       列出的群系，走 {@code UNDERGROUND_ORES} 步；
 *   <li>{@code data/strife/loot_table/<drop_table>.json} —— 原版 loot 2 掉落表（丝触掉矿块、时运走 {@code
 *       minecraft:ore_drops} 公式、爆炸衰减）；
 *   <li>{@code data/strife/strife_ores/<id>.json} —— strife
 *       侧的契约镜像（元素属性、drop_table、regen_period_key）， 供 {@code OreTables} 加载，也是 Validator V-REF 的引用落点。
 * </ol>
 *
 * <p><b>{@code drop_table} 列为什么必须写成 {@code blocks/<方块ID>}</b>（1.21.1 源码实证，非记忆）：{@code
 * BlockBehaviour} 的构造器在 {@code Properties} 未显式指定掉落表时，走这段推导——{@code
 * BuiltInRegistries.BLOCK.getKey(block).withPrefix("blocks/")}；而 {@code Properties} 上<b>没有公开的
 * lootTable setter</b>（只有 {@code noLootTable()} / {@code dropsLike(Block)} / {@code
 * lootFrom(Supplier)}），{@code Block.getLootTable()} 又是 {@code final}
 * 不可覆写。因此方块的掉落表路径是<b>由注册名唯一确定</b>的：写成别的名字，
 * 原版找不到表就<b>静默不掉落任何东西</b>（不是报错，是空气），而"矿挖了没东西"是最难在测试里发现的一类断链。 本列存在的意义就是把这个由代码决定的路径<b>显式写进表里让
 * Validator 与人看见</b>，而不是靠约定。
 *
 * <p>本类只做转换，不做裁定（与 {@link FactionGenerator} 同分工）：{@code density_ratio ∈ (0,1]}、element 枚举 合法性归
 * Validator，drop_table 与注册名是否一致归本类（因为一致性只能靠比对得出）。同一处"什么算合法"写在两个 工具里，契约某一栏加取值范围时就会分叉。
 */
public final class OreGenerator implements TableGenerator {

    private static final String CONTRACT = "JSON_SCHEMA §4.8";

    /** 原版 1.21 的矿脉噪声：把矿脉形状打散，避免一个方块状团。 */
    private static final String VEIN_NOISE = "minecraft:ore veins";

    @Override
    public String tableFile() {
        return "ores.csv";
    }

    @Override
    public List<Product> generate(TableSource source) {
        List<Product> products = new ArrayList<>();
        for (TableSource.Record row : source.rows()) {
            String id = row.id();
            Map<String, String> raw = placementMapping(source, row);
            List<String> biomes = splitBiomes(source, row, raw.get("biomes"));
            int yMin = intField(source, row, raw, "y_min");
            int yMax = intField(source, row, raw, "y_max");
            if (yMax < yMin) {
                throw new IllegalStateException(
                        source.fileName()
                                + ":"
                                + row.line()
                                + ": placement y_max="
                                + yMax
                                + " < y_min="
                                + yMin
                                + " ("
                                + CONTRACT
                                + "): 上下界反了会生成不出任何矿，且原版不会报错");
            }
            int veinsPerChunk = positiveInt(source, row, raw, "veins_per_chunk");
            int veinSize = positiveInt(source, row, raw, "vein_size");
            double density =
                    Cells.doubleOf(
                            source,
                            row,
                            "density_ratio",
                            Cells.required(source, row, "density_ratio", CONTRACT));
            String dropTable = dropTable(source, row, id);
            String element = Cells.required(source, row, "element", CONTRACT);

            products.add(configuredFeature(source, id));
            products.add(placedFeature(source, id, yMin, yMax, veinsPerChunk, veinSize, density));
            products.add(biomeModifier(source, id, biomes));
            products.add(lootTable(source, id, dropTable));
            products.add(
                    contractMirror(
                            source,
                            id,
                            element,
                            biomes,
                            yMin,
                            yMax,
                            veinsPerChunk,
                            veinSize,
                            density,
                            dropTable));
        }
        return List.copyOf(products);
    }

    /**
     * 校验 {@code drop_table} 列与方块注册名一致。
     *
     * <p>一致性不是风格问题而是正确性问题（见类注释的 1.21.1 源码实证）：方块掉落表路径由注册名唯一推导，列里写别的 名字，原版找不到表就静默不掉落。因此这里<b>只接受
     * {@code blocks/<id>}</b>，其余写法在构建期就报出表名与行号—— 这正是把"表里有行、但矿挖了掉空气"这类静默断链挡在构建期而不是留给玩家发现的地方。
     */
    private static String dropTable(TableSource source, TableSource.Record row, String id) {
        String declared = Cells.required(source, row, "drop_table", CONTRACT).trim();
        String expected = "blocks/" + id;
        if (!expected.equals(declared)) {
            throw new IllegalStateException(
                    source.fileName()
                            + ":"
                            + row.line()
                            + ": drop_table='"
                            + declared
                            + "' but the block id is '"
                            + id
                            + "' ("
                            + CONTRACT
                            + "): 1.21.1 的方块掉落表路径由注册名推导为 'blocks/<方块ID>'，"
                            + "Properties 没有公开的 lootTable setter，Block.getLootTable() 是 final——"
                            + "写别的名字原版找不到表，会静默不掉落（掉空气），此处应为 '"
                            + expected
                            + "'");
        }
        return declared;
    }

    /** 矿块本身 + 替换规则（石头被替换为矿；矿石在原版岩层里先出现再被替换，顺序即变形顺序）。 */
    private static Product configuredFeature(TableSource source, String id) {
        Map<String, Object> feature = new LinkedHashMap<>();
        feature.put("type", "minecraft:ore");
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("size", veinsPlaceholder());
        config.put("discard_chance_on_air_exposure", 0.0);
        Map<String, Object> targets = new LinkedHashMap<>();
        // 目标规则是原版 1.17+ 的"先变形再替换"写法：stone（含 deepslate）→ 该矿。
        // 只列 stone 一个规则即可，deepslate 由 stone 规则的状态自动传播（原版 ore_diamond 即如此定义）。
        targets.put("minecraft:stone_ore_replaceables", "minecraft:stone");
        config.put("targets", targets);
        feature.put("config", config);
        return new Product(
                "data/strife/worldgen/configured_feature/" + id + ".json",
                feature,
                source.generatedHeader());
    }

    /**
     * 放置规则：数量 = {@code veins_per_chunk × density_ratio}，尺寸 = {@code vein_size}。
     *
     * <p>密度倍率在这里折算成"实际矿脉条数"而不是留在 JSON 里让运行时算——放矿是原版行为，运行时没有"读表再乘"的 钩子，倍率必须在此处落成整数。折算向上取整且保底
     * 1：density_ratio &gt; 0 时取整成 0 会让该矿彻底消失，而表作者 写 0.15 的意图是"稀"不是"没有"。
     */
    private static Product placedFeature(
            TableSource source,
            String id,
            int yMin,
            int yMax,
            int veinsPerChunk,
            int veinSize,
            double density) {
        int effectiveVeins = Math.max(1, (int) Math.ceil(veinsPerChunk * density));
        Map<String, Object> feature = new LinkedHashMap<>();
        feature.put("feature", "strife:" + id);
        feature.put("placement", placementList(effectiveVeins, yMin, yMax, veinSize));
        return new Product(
                "data/strife/worldgen/placed_feature/" + id + ".json",
                feature,
                source.generatedHeader());
    }

    /**
     * 挂到 {@code placement.biomes} 的群系，走 {@code UNDERGROUND_ORES} 步。
     *
     * <p>群系用 {@code neoforge:add_features} 修正器（该类型由 NeoForge 注册，1.21.251 的 {@code
     * BiomeModifiers.AddFeaturesBiomeModifier}）。用修正器而不是改写每个群系文件，是为了让"新增矿石"永远只动 {@code
     * tables/ores.csv} 一处。
     */
    private static Product biomeModifier(TableSource source, String id, List<String> biomes) {
        Map<String, Object> modifier = new LinkedHashMap<>();
        modifier.put("type", "neoforge:add_features");
        modifier.put("biomes", List.copyOf(biomes));
        modifier.put("features", "strife:" + id);
        modifier.put("step", "underground_ores");
        return new Product(
                "data/strife/worldgen/biome_modifier/" + id + ".json",
                modifier,
                source.generatedHeader());
    }

    /**
     * 原版 loot 2 掉落表：与 {@code data/minecraft/loot_table/blocks/diamond_ore.json} 同构。
     *
     * <p>三条 children 语义（与原版矿一致，不自创规则）：
     *
     * <ul>
     *   <li><b>丝触</b> → 掉矿块本身（勘探用途：玩家要的是方块形态）；
     *   <li><b>否则</b> → 掉矿块自身物品，叠 {@code minecraft:apply_bonus}（{@code minecraft:ore_drops} 公式，
     *       时运按稀有度加权）与 {@code minecraft:explosion_decay}（ TNT/爆炸衰减， vanilla 行为）。
     * </ul>
     *
     * <p><b>为什么本期矿石掉落"矿块自身"而不是"某某材料"</b>：矿石材料物品（灵玉/赤炎/寒玉的精炼产物）属炼器/炼丹 域（production），其内容 ID 尚未在
     * {@code tables/artifacts.csv}（空表）与 NUMBERS 落定，此刻写进掉落表就是引用 一个不存在的物品
     * ID，同样是掉空气。因此本期掉落方块自身——这既是原版矿的完整行为，也让"矿石 → 材料"的 冶炼链在 materials 物品 ID 定稿后有明确挂点。掉落数量与时运权重全部交给原版
     * {@code ore_drops} 公式，不在 本文件写死任何数值（受管数值零字面量，AGENTS.md）。
     */
    private static Product lootTable(TableSource source, String id, String dropTable) {
        String blockItem = "strife:" + id;
        Map<String, Object> table = new LinkedHashMap<>();
        table.put("type", "minecraft:block");
        List<Object> pools = new ArrayList<>();
        pools.add(blockPool(blockItem));
        table.put("pools", pools);
        // random_sequence 用掉落表自身的路径，与原版 diamond_ore 的写法一致（每张表一条独立随机序列）。
        table.put("random_sequence", "strife:" + dropTable);
        return new Product(
                "data/strife/loot_table/" + dropTable + ".json", table, source.generatedHeader());
    }

    /** 单池：一次 roll，alternatives 分"丝触掉方块 / 否则掉物品（时运加权）"。 */
    private static Map<String, Object> blockPool(String blockItem) {
        List<Object> children = new ArrayList<>();
        children.add(silkTouchDrop(blockItem));
        children.add(itemDrop(blockItem));
        Map<String, Object> alternatives = new LinkedHashMap<>();
        alternatives.put("type", "minecraft:alternatives");
        alternatives.put("children", children);

        List<Object> entries = new ArrayList<>();
        entries.add(alternatives);

        Map<String, Object> pool = new LinkedHashMap<>();
        pool.put("rolls", 1.0);
        pool.put("bonus_rolls", 0.0);
        pool.put("entries", entries);
        return pool;
    }

    /** 丝触命中：原样掉矿块（勘探/收藏路径）。 */
    private static Map<String, Object> silkTouchDrop(String blockItem) {
        Map<String, Object> enchantments = new LinkedHashMap<>();
        enchantments.put("enchantments", "minecraft:silk_touch");
        Map<String, Object> levels = new LinkedHashMap<>();
        levels.put("min", 1);
        enchantments.put("levels", levels);
        Map<String, Object> predicates = new LinkedHashMap<>();
        predicates.put("minecraft:enchantments", List.of(enchantments));
        Map<String, Object> predicate = new LinkedHashMap<>();
        predicate.put("predicates", predicates);
        Map<String, Object> condition = new LinkedHashMap<>();
        condition.put("condition", "minecraft:match_tool");
        condition.put("predicate", predicate);

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("type", "minecraft:item");
        entry.put("name", blockItem);
        entry.put("conditions", List.of(condition));
        return entry;
    }

    /** 普通挖掘：掉矿块物品，附时运加成（{@code minecraft:ore_drops} 公式）与爆炸衰减。 */
    private static Map<String, Object> itemDrop(String blockItem) {
        Map<String, Object> bonus = new LinkedHashMap<>();
        bonus.put("function", "minecraft:apply_bonus");
        bonus.put("enchantment", "minecraft:fortune");
        bonus.put("formula", "minecraft:ore_drops");
        Map<String, Object> decay = new LinkedHashMap<>();
        decay.put("function", "minecraft:explosion_decay");
        List<Object> functions = new ArrayList<>();
        functions.add(bonus);
        functions.add(decay);

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("type", "minecraft:item");
        entry.put("name", blockItem);
        entry.put("functions", functions);
        return entry;
    }

    /** strife 侧契约镜像：元素属性与掉落表在这份产物里，供 {@code OreTables} 与 V-REF 使用。 */
    private static Product contractMirror(
            TableSource source,
            String id,
            String element,
            List<String> biomes,
            int yMin,
            int yMax,
            int veinsPerChunk,
            int veinSize,
            double density,
            String dropTable) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("id", id);
        fields.put("element", element);
        Map<String, Object> placement = new LinkedHashMap<>();
        placement.put("biomes", List.copyOf(biomes));
        placement.put("y_min", yMin);
        placement.put("y_max", yMax);
        placement.put("veins_per_chunk", veinsPerChunk);
        placement.put("vein_size", veinSize);
        fields.put("placement", placement);
        fields.put("density_ratio", density);
        fields.put("drop_table", dropTable);
        // 本期恒 null：H5 周期事件未实现，矿石不重生。写 null 而不是省略，让"未启用"与"漏填"在产物里可区分。
        fields.put("regen_period_key", null);
        return new Product(
                "data/strife/strife_ores/" + id + ".json", fields, source.generatedHeader());
    }

    /**
     * 放置修饰符列表：每区块 N 条矿脉，每条 {@code veinSize} 个方块，限定在 [yMin, yMax]，用原版矿脉噪声定位。
     *
     * <p>{@code count} 用常量 {@code 1} × {@code N}：原版 {@code CountPlacement} 的第二个参数是均匀分布的倍数， 写 1
     * 即"每次放置恰好 N 条"，语义与表列名 {@code veins_per_chunk} 一致。
     */
    private static List<Object> placementList(int veins, int yMin, int yMax, int veinSize) {
        List<Object> modifiers = new ArrayList<>();
        modifiers.add(singleKeyMap("count", veins));
        modifiers.add(singleKeyMap("in_square", null));
        modifiers.add(singleKeyMap("height_range", heightRange(yMin, yMax)));
        modifiers.add(singleKeyMap("vein_size", veinSize));
        modifiers.add(singleKeyMap("lakes", null));
        modifiers.add(singleKeyMap("noise", VEIN_NOISE));
        modifiers.add(singleKeyMap("noise_multiplier", 0.0));
        return modifiers;
    }

    /**
     * 原版 {@code height_range} 是"以地表为 0 的相对高度"，允许负值。
     *
     * <p>{@code y_min} 是绝对高度，直接减掉 y=-64 的基线（1.21.2 之前原版世界的最低方块层是 -64）。这里减 64 是
     * <b>平台常量</b>不是受管数值：它描述的是原版世界的高度基准，改动它意味着改 MC 版本。
     */
    private static Map<String, Object> heightRange(int yMin, int yMax) {
        Map<String, Object> range = new LinkedHashMap<>();
        range.put("height", yMax - WORLD_MIN_Y);
        range.put("y", yMin - WORLD_MIN_Y);
        return range;
    }

    /** 原版世界基线高度（1.21.1 为 -64）。 */
    private static final int WORLD_MIN_Y = -64;

    /** 只有一个键的修饰符 map；{@code value} 为 null 时写 {@code {}}（原版无参修饰符的写法）。 */
    private static Map<String, Object> singleKeyMap(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (value != null) {
            map.put(key, value);
        }
        return map;
    }

    /** configured feature 的 size 由 placed feature 的 {@code vein_size} 覆写，这里给原版默认 1。 */
    private static Map<String, Object> veinsPlaceholder() {
        Map<String, Object> size = new LinkedHashMap<>();
        size.put("type", "minecraft:uniform");
        size.put("min_inclusive", 1);
        size.put("max_inclusive", 1);
        return size;
    }

    private static Map<String, String> placementMapping(
            TableSource source, TableSource.Record row) {
        Map<String, String> raw = source.mapping("placement", row);
        if (raw.isEmpty()) {
            throw new IllegalStateException(
                    source.fileName()
                            + ":"
                            + row.line()
                            + ": placement is required ("
                            + CONTRACT
                            + ") expects 'biomes=<id>|<id>;y_min=<int>;y_max=<int>;"
                            + "veins_per_chunk=<int>;vein_size=<int>'");
        }
        return raw;
    }

    private static List<String> splitBiomes(
            TableSource source, TableSource.Record row, String biomes) {
        if (biomes == null || biomes.isBlank()) {
            throw new IllegalStateException(
                    source.fileName()
                            + ":"
                            + row.line()
                            + ": placement needs 'biomes=<id>|<id>' — an ore with no biome never"
                            + " generates ("
                            + CONTRACT
                            + ")");
        }
        List<String> ids = new ArrayList<>();
        // split 必须带 -1（保留尾部空串）：默认 split 会丢掉 "a|" 的尾部空项，让"分隔符之间漏了一个 ID"
        // 这种错表静默通过，产物里就少一个群系——而原版不会为这件事报任何错。
        for (String part : biomes.split("\\|", -1)) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                throw new IllegalStateException(
                        source.fileName()
                                + ":"
                                + row.line()
                                + ": placement biomes has an empty entry between '|' separators ("
                                + CONTRACT
                                + ")");
            }
            ids.add(trimmed);
        }
        return ids;
    }

    private static int positiveInt(
            TableSource source, TableSource.Record row, Map<String, String> raw, String key) {
        int value = intField(source, row, raw, key);
        if (value < 1) {
            throw new IllegalStateException(
                    source.fileName()
                            + ":"
                            + row.line()
                            + ": placement "
                            + key
                            + "="
                            + value
                            + " must be >= 1 ("
                            + CONTRACT
                            + "): 0 意味着这个区块永远不生成该矿");
        }
        return value;
    }

    private static int intField(
            TableSource source, TableSource.Record row, Map<String, String> raw, String key) {
        String value = raw.get(key);
        if (value == null) {
            throw new IllegalStateException(
                    source.fileName()
                            + ":"
                            + row.line()
                            + ": placement is missing '"
                            + key
                            + "' ("
                            + CONTRACT
                            + ")");
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    source.fileName()
                            + ":"
                            + row.line()
                            + ": placement "
                            + key
                            + "='"
                            + value
                            + "' is not an integer ("
                            + CONTRACT
                            + ")",
                    e);
        }
    }
}

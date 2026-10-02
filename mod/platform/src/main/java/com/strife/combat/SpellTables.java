package com.strife.combat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 法术产物与战斗规则表（combat 域，docs/03 §1 职责表；content/JSON_SCHEMA.md §4.3）。
 *
 * <p>两个来源都读<b>随 jar 分发的 DataGen 产物</b>：{@code data/strife/strife_spells/<id>.json}（法术）与 {@code
 * data/strife/strife_combat/rules.json}（NUMBERS @@combat 的 realm_coeff 等战斗规则）。注册期早于 datapack
 * 加载，ResourceManager 拿不到数据包，读 jar 内产物与读数据包是同一份真相（{@code RealmEffects} 同款口径）； 读不到即 fail-fast——MOD
 * 起不来，好过"没有数值的空法术"。
 *
 * <p>严格未知字段（§1.2 规则 2）：SpellSpec 与 CombatRules 的键集合精确匹配契约，多一个键就炸。
 */
public final class SpellTables {

    /** 一条法术的运行时规格（§4.3 字段契约；弹道为 null = 非弹道形态，效果契约待内容定义）。 */
    public record SpellSpec(
            String id,
            String element,
            String requiredTechnique,
            int costQi,
            double cooldownSec,
            String damageFormulaKey,
            double damageBase,
            boolean projectileScaleRealmCoeff,
            Double projectileSpeed,
            Double projectileGravity,
            Double projectileRange,
            int projectilePierceCount,
            double aoeRadiusBlocks) {

        public boolean isProjectile() {
            return projectileSpeed != null;
        }
    }

    /** NUMBERS @@combat 的运行时规则（realm_coeff 按 `<域>_<章>_<语义>` 外的境界 ID 查）。 */
    public record CombatRules(Map<String, Double> realmCoeff) {}

    private static final java.util.Set<String> SPELL_KEYS =
            java.util.Set.of(
                    "@generated",
                    "content_format",
                    "id",
                    "element",
                    "required_technique",
                    "cost_key",
                    "cost_qi",
                    "cooldown_key",
                    "cooldown_sec",
                    "damage_formula_key",
                    "damage_formula",
                    "projectile",
                    "aoe_radius_blocks",
                    "effects",
                    "price");
    private static final java.util.Set<String> FORMULA_KEYS =
            java.util.Set.of("base", "scale", "pen", "self_cost_qi");
    private static final java.util.Set<String> PROJECTILE_KEYS =
            java.util.Set.of("speed", "gravity", "range", "pierce_count");

    private final Map<String, SpellSpec> spellsById = new LinkedHashMap<>();
    private final CombatRules rules;

    private SpellTables(CombatRules rules) {
        this.rules = rules;
    }

    public SpellSpec spell(String spellId) {
        return spellsById.get(spellId);
    }

    public Map<String, SpellSpec> all() {
        return java.util.Collections.unmodifiableMap(spellsById);
    }

    public CombatRules rules() {
        return rules;
    }

    /** 注册期加载：从本 jar 的 resources 读全部法术产物与战斗规则（一次成型，之后只读）。 */
    public static SpellTables load() {
        CombatRules rules = loadRules();
        SpellTables tables = new SpellTables(rules);
        for (String path : SPELL_INDEX) {
            JsonObject product = readJson(path);
            tables.spellsById.putAll(parseSpell(product));
        }
        if (tables.spellsById.isEmpty()) {
            throw new IllegalStateException("strife_spells: no spell products in jar");
        }
        return tables;
    }

    private static final java.util.List<String> SPELL_INDEX =
            java.util.List.of(
                    "data/strife/strife_spells/spell_prologue_huodan.json",
                    "data/strife/strife_spells/spell_prologue_jinren.json",
                    "data/strife/strife_spells/spell_prologue_tubi.json");

    /** 法术产物是"一法术一文件"（§4.3 产物形态），这里把单文件包装成兼容解析入口。 */
    private static Map<String, SpellSpec> parseSpell(JsonObject object) {
        Map<String, SpellSpec> parsed = new LinkedHashMap<>();
        parsed.putAll(parseSpellsWrapper(wrap(object)));
        return parsed;
    }

    private static JsonObject wrap(JsonObject spellObject) {
        JsonObject wrapper = new JsonObject();
        JsonArray array = new JsonArray();
        array.add(spellObject);
        wrapper.add("spells", array);
        return wrapper;
    }

    /** 与 DataGen 的"一章一文件"形态对齐的解析入口（测试直接喂 wrapper JSON）。 */
    public static Map<String, SpellSpec> parseSpellsWrapper(JsonObject product) {
        Map<String, SpellSpec> parsed = new LinkedHashMap<>();
        for (JsonElement element : array(product, "spells")) {
            JsonObject json = element.getAsJsonObject();
            SpellSpec spec = parseSpec(json);
            if (parsed.containsKey(spec.id())) {
                throw new IllegalStateException("spell '" + spec.id() + "' declared twice");
            }
            parsed.put(spec.id(), spec);
        }
        return parsed;
    }

    private static SpellSpec parseSpec(JsonObject json) {
        rejectUnknown(json, SPELL_KEYS, "spell");
        String id = string(json, "id", true);
        String element = string(json, "element", true);
        String requiredTechnique = string(json, "required_technique", false);
        int costQi = json.get("cost_qi").getAsInt();
        double cooldownSec = json.get("cooldown_sec").getAsDouble();
        String formulaKey = string(json, "damage_formula_key", false);
        double damageBase = 0;
        boolean scaleRealm = false;
        if (formulaKey != null) {
            JsonObject formula = object(json.get("damage_formula"));
            rejectUnknown(formula, FORMULA_KEYS, "spell '" + id + "' damage_formula");
            damageBase = formula.get("base").getAsDouble();
            scaleRealm = "realm_coeff".equals(string(formula, "scale", false));
        }
        Double speed = null;
        Double gravity = null;
        Double range = null;
        int pierce = 0;
        if (json.has("projectile") && json.get("projectile").isJsonObject()) {
            JsonObject projectile = json.getAsJsonObject("projectile");
            rejectUnknown(projectile, PROJECTILE_KEYS, "spell '" + id + "' projectile");
            speed = projectile.get("speed").getAsDouble();
            gravity = projectile.get("gravity").getAsDouble();
            range = projectile.get("range").getAsDouble();
            pierce = projectile.has("pierce_count") ? projectile.get("pierce_count").getAsInt() : 0;
        }
        double aoe =
                json.has("aoe_radius_blocks") && !json.get("aoe_radius_blocks").isJsonNull()
                        ? json.get("aoe_radius_blocks").getAsDouble()
                        : 0;
        return new SpellSpec(
                id,
                element,
                requiredTechnique,
                costQi,
                cooldownSec,
                formulaKey,
                damageBase,
                scaleRealm,
                speed,
                gravity,
                range,
                pierce,
                aoe);
    }

    private static CombatRules loadRules() {
        JsonObject product = readJson("data/strife/strife_combat/rules.json");
        JsonObject combat = object(product.get("combat"));
        Map<String, Double> coeff = new LinkedHashMap<>();
        JsonObject realmCoeff = object(combat.get("realm_coeff"));
        for (Map.Entry<String, JsonElement> entry : realmCoeff.entrySet()) {
            coeff.put(entry.getKey(), entry.getValue().getAsDouble());
        }
        return new CombatRules(Map.copyOf(coeff));
    }

    private static JsonObject readJson(String path) {
        try (var stream = SpellTables.class.getResourceAsStream("/" + path)) {
            if (stream == null) {
                throw new IllegalStateException(path + " not in jar（DataGen 产物缺失）");
            }
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (Exception e) {
            throw new IllegalStateException("cannot load " + path, e);
        }
    }

    private static void rejectUnknown(
            JsonObject json, java.util.Set<String> allowed, String where) {
        for (String key : json.keySet()) {
            if (!allowed.contains(key)) {
                throw new IllegalStateException(
                        "unknown field '" + key + "' in " + where + "（§1.2 规则 2：严格未知字段 fail-fast）");
            }
        }
    }

    private static JsonArray array(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonArray()) {
            throw new IllegalStateException("field '" + key + "' must be an array");
        }
        return element.getAsJsonArray();
    }

    private static JsonObject object(JsonElement element) {
        if (!element.isJsonObject()) {
            throw new IllegalStateException("expected object but got " + element);
        }
        return element.getAsJsonObject();
    }

    private static String string(JsonObject json, String key, boolean required) {
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull()) {
            if (required) {
                throw new IllegalStateException("field '" + key + "' is 必填");
            }
            return null;
        }
        return element.getAsString();
    }
}

package com.strife.combat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * combat 域运行时数值表：从 classpath 读 DataGen 产物 {@code strife_combat/rules.json}（NUMBERS @@combat
 * 直出），受管数值零字面量（AGENTS.md）。
 *
 * <p>与 {@link com.strife.world.WorldTables} 同一理念，但本类只读 combat 自己的产物——妖兽属性属战斗域， 放在 {@code @@combat}
 * 而非 {@code @@world}，正是为了避免 combat → world 的横向依赖（docs/03 §2 禁止； 模块依赖门禁 {@code
 * ModuleDependencyRules} 也会拦）。
 *
 * <p>资源在 jar 的 {@code data/} 下，classpath 直读，客户端服务端都拿得到，无需 {@code MinecraftServer}。
 */
public final class CombatTables {

    private static final Logger LOGGER = LoggerFactory.getLogger("strife/combat");

    private static volatile BeastStats cachedBeast;

    private CombatTables() {}

    /**
     * 妖兽基础属性（NUMBERS @@combat 的 beast_* 四项）。{@code fallback()} 是工程初值—— 资源缺失时实体仍能用默认属性出生，不会因漏跑
     * DataGen 而崩；但默认值只作安全网， <b>真实调参一律在 NUMBERS.md</b>（AGENTS.md：代码里不得出现受管数值字面量）。
     */
    public record BeastStats(double health, double attack, double speed, double followRange) {

        /** 安全网默认值，等于历史硬编码值（StrifeMonster.createAttributes 旧值）。 */
        public static BeastStats fallback() {
            return new BeastStats(20.0, 3.0, 0.30, 16.0);
        }

        /** 从 combat_rules 的 {@code combat} 块解析四项，缺键即报具体路径（02 §5：加载失败必须具名）。 */
        public static BeastStats fromCombatRules(JsonObject combat) {
            return new BeastStats(
                    require(combat, "beast_health").getAsDouble(),
                    require(combat, "beast_attack").getAsDouble(),
                    require(combat, "beast_speed").getAsDouble(),
                    require(combat, "beast_follow_range").getAsDouble());
        }
    }

    /**
     * 法宝副手属性加成（P1-01，NUMBERS @@combat 的 {@code artifact_bonus_*} 三键）。
     *
     * <p>{@code attackPct} 作用于基础攻击（{@code ADD_MULTIPLIED_BASE}），{@code healthFlat} 为生命上限平加 （{@code
     * ADD_VALUE}）。未知品阶回零加成（不崩、记一次日志）——品阶来自器胚 CustomData，坏数据不该打崩属性栏。
     */
    public record ArtifactBonus(double attackPct, double healthFlat) {

        public static ArtifactBonus zero() {
            return new ArtifactBonus(0.0, 0.0);
        }

        /** 按 quality（fan/di/tian）从 combat 块取加成；缺键报具体路径（02 §5 具名失败）。 */
        public static ArtifactBonus fromCombatRules(JsonObject combat, String quality) {
            String key = "artifact_bonus_" + quality;
            if (!combat.has(key)) {
                throw new IllegalStateException(
                        "strife_combat/rules.json 的 combat 块缺键 '"
                                + key
                                + "'（NUMBERS @@combat 改了键名？）");
            }
            JsonObject bonus = combat.getAsJsonObject(key);
            return new ArtifactBonus(
                    bonus.get("attack_pct").getAsDouble(), bonus.get("health_flat").getAsDouble());
        }
    }

    /**
     * 符箓规则（P3 ADR-035，NUMBERS @@talisman → rules.json 的 talisman 块）。
     *
     * <p>fail-soft 口径与 BeastStats 不同：{@code absent()} 表示"符箓系统整体不可用"（遁符拒绝使用、
     * 替身不触发）——保命道具缺数据时**宁可不生效也不猜默认值**（受管数值零字面量，猜错就是假死/秒回）。
     */
    public record TalismanRules(
            double substituteHealth, int escapeCooldownSec, String escapePlace) {

        public static TalismanRules absent() {
            return new TalismanRules(-1.0, -1, "");
        }

        public boolean present() {
            return substituteHealth >= 0;
        }
    }

    /** 取符箓规则；产物缺失/解析失败回 {@link TalismanRules#absent} 并记日志。 */
    public static TalismanRules talismanRules() {
        try {
            JsonObject talisman = loadProduct().getAsJsonObject("talisman");
            if (talisman == null) {
                throw new IllegalStateException(
                        "strife_combat/rules.json 的 combat 块缺 talisman 子对象（NUMBERS @@talisman 改了键名？）");
            }
            return new TalismanRules(
                    talisman.get("substitute_health").getAsDouble(),
                    talisman.get("escape_cooldown_sec").getAsInt(),
                    talisman.get("escape_place").getAsString());
        } catch (RuntimeException e) {
            LOGGER.error("talisman rules unavailable, talismans disabled: {}", e.getMessage());
            return TalismanRules.absent();
        }
    }

    /**
     * 功法熟练度规则（NUMBERS @@combat 三键 [拟]）：每档施法次数/每档加成/加成封顶。
     * {@code fallback()} 是零加成——熟练度规则缺失时施法照常，只是没有熟练度收益。
     */
    public record TechniqueProficiency(int perTier, double bonusPerTier, double cap) {
        public static TechniqueProficiency fallback() {
            return new TechniqueProficiency(Integer.MAX_VALUE, 0.0, 0.0);
        }

        /** 档位乘数（纯函数）：min(cap, 档数 × 每档加成)。 */
        public double multiplierFor(int proficiencyCount) {
            int tiers = proficiencyCount / Math.max(1, perTier);
            return 1.0 + Math.min(cap, tiers * bonusPerTier);
        }
    }

    /** 取熟练度规则；产物缺失/解析失败回零收益并记日志（施法不是崩点）。 */
    public static TechniqueProficiency techniqueProficiency() {
        try {
            JsonObject combat = loadCombatBlock();
            return new TechniqueProficiency(
                    require(combat, "technique_proficiency_per_tier").getAsInt(),
                    require(combat, "technique_proficiency_bonus_per_tier").getAsDouble(),
                    require(combat, "technique_proficiency_cap").getAsDouble());
        } catch (RuntimeException e) {
            LOGGER.error(
                    "technique proficiency unavailable, using zero bonus: {}", e.getMessage());
            return TechniqueProficiency.fallback();
        }
    }

    /** 取品阶加成；产物缺失/解析失败/未知品阶一律回零加成并记日志（属性栏不是崩点）。 */
    public static ArtifactBonus artifactBonus(String quality) {
        try {
            return ArtifactBonus.fromCombatRules(loadCombatBlock(), quality);
        } catch (RuntimeException e) {
            LOGGER.error(
                    "artifact bonus unavailable for '{}', using zero: {}", quality, e.getMessage());
            return ArtifactBonus.zero();
        }
    }

    /**
     * 法器分级（EP1，ADR-036）：品阶 → 四阶（法器/法宝/灵宝/仙器——仙器 EP4 前不可达） 与阶位属性倍率。映射与倍率都来自
     * NUMBERS @@combat（tier_of_* / tier_multiplier）， 未知品阶回 {@code absent}（同 ArtifactBonus
     * fail-soft）。
     */
    public record ArtifactTier(String tier, String displayName, double multiplier) {

        public static ArtifactTier absent() {
            return new ArtifactTier("", "", 0.0);
        }

        public boolean present() {
            return multiplier > 0;
        }
    }

    /** 品阶 → 阶位；解析失败回 absent。 */
    public static ArtifactTier artifactTier(String quality) {
        try {
            JsonObject combat = loadCombatBlock();
            String tierKey = "tier_of_" + quality;
            if (!combat.has(tierKey)) {
                throw new IllegalStateException(
                        "strife_combat/rules.json 缺 '" + tierKey + "'（NUMBERS @@combat 改了键名？）");
            }
            String tier = combat.get(tierKey).getAsString();
            JsonObject multipliers = combat.getAsJsonObject("tier_multiplier");
            if (multipliers == null || !multipliers.has(tier)) {
                throw new IllegalStateException(
                        "strife_combat/rules.json 缺 tier_multiplier."
                                + tier
                                + "（NUMBERS @@combat）");
            }
            String displayName =
                    switch (tier) {
                        case "fa" -> "法器";
                        case "fa_bao" -> "法宝";
                        case "ling_bao" -> "灵宝";
                        case "xian_qi" -> "仙器";
                        default ->
                                throw new IllegalStateException(
                                        "未知阶位 '" + tier + "'（tier_of_" + quality + "）");
                    };
            return new ArtifactTier(tier, displayName, multipliers.get(tier).getAsDouble());
        } catch (RuntimeException e) {
            LOGGER.error("artifact tier unavailable for '{}': {}", quality, e.getMessage());
            return ArtifactTier.absent();
        }
    }

    /** 生效加成 = 品阶加成 × 阶位倍率（ADR-036）。阶位/品阶任一不可用回零加成—— 光环宁可不生效也不猜数。 */
    public static ArtifactBonus effectiveBonus(String quality) {
        ArtifactBonus base = artifactBonus(quality);
        ArtifactTier tier = artifactTier(quality);
        if (base.equals(ArtifactBonus.zero()) || !tier.present()) {
            return ArtifactBonus.zero();
        }
        return new ArtifactBonus(
                base.attackPct() * tier.multiplier(), base.healthFlat() * tier.multiplier());
    }

    /** 取妖兽属性；产物缺失或解析失败回退到 {@link BeastStats#fallback()} 并记日志。 */
    public static BeastStats beastStats() {
        BeastStats cached = cachedBeast;
        if (cached != null) {
            return cached;
        }
        synchronized (CombatTables.class) {
            if (cachedBeast != null) {
                return cachedBeast;
            }
            try {
                cachedBeast = BeastStats.fromCombatRules(loadCombatBlock());
            } catch (RuntimeException e) {
                LOGGER.error("combat rules unavailable, using beast fallback: {}", e.getMessage());
                cachedBeast = BeastStats.fallback();
            }
            return cachedBeast;
        }
    }

    /** 测试与 /reload 后强制重读。 */
    static void invalidate() {
        cachedBeast = null;
    }

    private static JsonObject loadCombatBlock() {
        return requireBlock(loadProduct(), "combat");
    }

    private static JsonObject loadProduct() {
        try (InputStream stream =
                Objects.requireNonNull(
                        CombatTables.class.getResourceAsStream(
                                "/data/strife/strife_combat/rules.json"),
                        "strife_combat/rules.json not in jar — CombatRulesGenerator 未跑？")) {
            JsonObject product =
                    JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                            .getAsJsonObject();
            if (product.get("@generated") == null) {
                throw new IllegalStateException(
                        "strife_combat/rules.json missing @generated header");
            }
            return product;
        } catch (Exception e) {
            throw new IllegalStateException("cannot read strife_combat/rules.json", e);
        }
    }

    private static JsonObject requireBlock(JsonObject product, String name) {
        JsonObject value = product.getAsJsonObject(name);
        if (value == null) {
            throw new IllegalStateException(
                    "strife_combat/rules.json missing '" + name + "' block");
        }
        return value;
    }

    private static com.google.gson.JsonElement require(JsonObject block, String key) {
        if (!block.has(key)) {
            throw new IllegalStateException(
                    "strife_combat/rules.json 的 combat 块缺键 '" + key + "'（NUMBERS @@combat 改了键名？）");
        }
        return block.get(key);
    }
}

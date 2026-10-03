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
            JsonObject combat = product.getAsJsonObject("combat");
            if (combat == null) {
                throw new IllegalStateException("strife_combat/rules.json missing 'combat' block");
            }
            return combat;
        } catch (Exception e) {
            throw new IllegalStateException("cannot read strife_combat/rules.json", e);
        }
    }

    private static com.google.gson.JsonElement require(JsonObject block, String key) {
        if (!block.has(key)) {
            throw new IllegalStateException(
                    "strife_combat/rules.json 的 combat 块缺键 '" + key + "'（NUMBERS @@combat 改了键名？）");
        }
        return block.get(key);
    }
}

package com.strife.core;

import java.util.Map;
import net.minecraft.server.level.ServerPlayer;

/**
 * 修炼速率四因子的提供者入口（docs/05 §2 锁定公式、03 §2"跨模块只走事件或 core 只读接口"）。
 *
 * <pre>
 * 实际修炼速率 = 基础速率(查表) × 灵根系数 × 环境系数(AmbientQi) × 功法倍率 × (1 + 丹药加成)
 * </pre>
 *
 * <p>为什么要有这层接口，而不是在 realm 里写三个 {@code 1.0}：三个系数的来源模块（world 的灵气场、combat 的功法、production
 * 的丹药）各自独立落地，而公式本身已是拍板口径。把入口定在 core，公式一次写全；每个模块落地时只注册自己那一项， <b>realm 一行不用改</b>，也不存在"某个系数被漏掉"的形态。
 *
 * <p><b>三项分开注册</b>（而不是一个三合一 Provider）：一个模块只拥有一个系数，三合一接口会强迫 world 去实现功法与丹药的空壳，
 * "谁该填哪一项"就糊掉了——那正是"看起来接了其实没接"的来源。
 *
 * <p>契约：实现必须返回 <b>&gt; 0</b> 的有限值。返回 0 会让玩家"打坐不涨修为"且无从解释，05 §2 明确要求任一系数为 0 必须有 可展示的理由——所以 {@link
 * #coefficients} 把 0/NaN/负数回落成中性 1.0，而不是把一次数值缺陷升级成"打坐没收益"。
 */
public final class CultivationFactors {

    /** 环境系数来源（world 的灵气浓度场）。 */
    @FunctionalInterface
    public interface EnvironmentSource {
        double coefficient(ServerPlayer player);
    }

    /** 功法倍率来源（combat 的已装备功法）。 */
    @FunctionalInterface
    public interface TechniqueSource {
        double coefficient(ServerPlayer player);
    }

    /** 丹药加成来源（production），返回 {@code (1 + bonus)}。 */
    @FunctionalInterface
    public interface PillSource {
        double coefficient(ServerPlayer player);
    }

    private static final double NEUTRAL = 1.0;

    private static volatile EnvironmentSource environment = player -> NEUTRAL;
    private static volatile TechniqueSource technique = player -> NEUTRAL;
    private static volatile PillSource pill = player -> NEUTRAL;
    private static volatile boolean environmentWired;
    private static volatile boolean techniqueWired;
    private static volatile boolean pillWired;

    private CultivationFactors() {}

    /** 注册环境系数来源；重复注册是设计错误（两个模块抢同一项），直接失败而不是静默覆盖。 */
    public static synchronized void registerEnvironment(EnvironmentSource source) {
        if (source == null) {
            throw new IllegalArgumentException("environment source must not be null");
        }
        if (environmentWired) {
            throw new IllegalStateException("environment source already registered");
        }
        environment = source;
        environmentWired = true;
    }

    /** 注册功法倍率来源。 */
    public static synchronized void registerTechnique(TechniqueSource source) {
        if (source == null) {
            throw new IllegalArgumentException("technique source must not be null");
        }
        if (techniqueWired) {
            throw new IllegalStateException("technique source already registered");
        }
        technique = source;
        techniqueWired = true;
    }

    /** 注册丹药加成来源。 */
    public static synchronized void registerPill(PillSource source) {
        if (source == null) {
            throw new IllegalArgumentException("pill source must not be null");
        }
        if (pillWired) {
            throw new IllegalStateException("pill source already registered");
        }
        pill = source;
        pillWired = true;
    }

    /** 三项已校验的系数（{@code product()} 即公式里三项之积）。 */
    public record Coefficients(double environment, double technique, double pill) {
        public double product() {
            return environment * technique * pill;
        }
    }

    /** 接线状态：哪几项已经有真实来源（启动日志与面板解释用）。 */
    public record Wiring(boolean environment, boolean technique, boolean pill) {
        public boolean complete() {
            return environment && technique && pill;
        }

        public String describe() {
            return "environment="
                    + (environment ? "wired" : "neutral")
                    + ", technique="
                    + (technique ? "wired" : "neutral")
                    + ", pill="
                    + (pill ? "wired" : "neutral");
        }
    }

    public static Wiring wiring() {
        return new Wiring(environmentWired, techniqueWired, pillWired);
    }

    /** 一次性读三项系数并逐项校验坏值。 */
    public static Coefficients coefficients(ServerPlayer player) {
        return new Coefficients(
                sanitize(environment.coefficient(player)),
                sanitize(technique.coefficient(player)),
                sanitize(pill.coefficient(player)));
    }

    /** 校验单个系数；包内可见供用例直接钉边界。 */
    static double sanitize(double value) {
        return Double.isFinite(value) && value > 0.0 ? value : NEUTRAL;
    }

    /** 仅测试用：清空接线（注册是全局状态，用例之间必须能互相隔离）。 */
    static synchronized void resetForTest() {
        environment = player -> NEUTRAL;
        technique = player -> NEUTRAL;
        pill = player -> NEUTRAL;
        environmentWired = false;
        techniqueWired = false;
        pillWired = false;
    }

    /** 诊断用：三项来源的类名（启动自检打印，便于一眼看出谁没接上）。 */
    public static Map<String, String> sources() {
        return Map.of(
                "environment", environment.getClass().getSimpleName(),
                "technique", technique.getClass().getSimpleName(),
                "pill", pill.getClass().getSimpleName());
    }
}

package com.strife.core;

import net.minecraft.server.level.ServerPlayer;

/**
 * 修炼速率四因子的提供者入口（docs/05 §2 锁定公式、03 §2"跨模块只走事件或 core 只读接口"）。
 *
 * <pre>
 * 实际修炼速率 = 基础速率(查表) × 灵根系数 × 环境系数(AmbientQi) × 功法倍率 × (1 + 丹药加成)
 * </pre>
 *
 * <p>为什么要有这层接口，而不是在 realm 里写三个 {@code 1.0}：三个系数的来源模块（world 的灵气场、combat 的功法、production
 * 的丹药）现在都还没落地，但"公式是四因子"这件事本身已经是拍板口径。把入口定在 core，公式一次写全；各模块落地时只是 {@link #register} 一个实现，<b>realm
 * 一行不用改</b>，也不存在"某个系数被漏掉"的形态——没有提供者时取值是 {@link #NEUTRAL}，并且面板可以据此解释"为何速率等于基础值"。
 *
 * <p>契约：三个方法都必须返回 <b>&gt; 0</b> 的有限值。返回 0 会让玩家"打坐不涨修为"且无法解释，05 §2 明确要求任一系数为 0
 * 必须有可展示的理由——所以这里直接把它当成提供者的实现缺陷拒掉。
 */
public final class CultivationFactors {

    /** 四因子中除基础速率与灵根系数之外的三项。 */
    public interface Provider {
        /** 环境系数（world 的灵气浓度场；NUMBERS §9 {@code ambient_qi_min}..{@code max}）。 */
        double environment(ServerPlayer player);

        /** 功法倍率（combat 的已装备功法；techniques 表 {@code qi_rate}）。 */
        double technique(ServerPlayer player);

        /** 丹药加成 {@code (1 + bonus)}（production 的丹药效果）。 */
        double pill(ServerPlayer player);
    }

    /** 无提供者时的中性系数：三项皆 1.0，即"尚未接入"而不是"恒等于某个编造值"。 */
    public static final Provider NEUTRAL =
            new Provider() {
                @Override
                public double environment(ServerPlayer player) {
                    return 1.0;
                }

                @Override
                public double technique(ServerPlayer player) {
                    return 1.0;
                }

                @Override
                public double pill(ServerPlayer player) {
                    return 1.0;
                }
            };

    private static volatile Provider provider = NEUTRAL;

    private CultivationFactors() {}

    /** 注册提供者（各模块入口构造期调用一次）；重复注册是设计错误，直接失败而不是静默覆盖。 */
    public static synchronized void register(Provider candidate) {
        if (candidate == null) {
            throw new IllegalArgumentException("provider must not be null");
        }
        if (provider != NEUTRAL) {
            throw new IllegalStateException(
                    "cultivation factor provider already registered: " + provider.getClass());
        }
        provider = candidate;
    }

    public static Provider get() {
        return provider;
    }

    /** 是否仍处于"无提供者"状态（诊断/面板解释用）。 */
    public static boolean neutral() {
        return provider == NEUTRAL;
    }

    /** 三项已校验的系数（{@code product()} 即公式里三项之积）。 */
    public record Coefficients(double environment, double technique, double pill) {
        public double product() {
            return environment * technique * pill;
        }
    }

    /**
     * 读三项系数并逐项校验：非有限值或 ≤0 一律回落到中性 1.0。
     *
     * <p>为什么是回落而不是抛出：提供者是别的模块的实现，抛异常会把一次数值缺陷升级成"玩家打坐时崩服"。回落 + 中性值让
     * 症状表现为"速率等于基础值"（可解释、可排查），而不是服务端崩溃。
     */
    public static Coefficients coefficients(ServerPlayer player) {
        Provider current = provider;
        return new Coefficients(
                sanitize(current.environment(player)),
                sanitize(current.technique(player)),
                sanitize(current.pill(player)));
    }

    /** 校验单个系数；包内可见供用例直接钉边界。 */
    static double sanitize(double value) {
        return Double.isFinite(value) && value > 0.0 ? value : 1.0;
    }
}

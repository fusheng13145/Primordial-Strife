package com.strife.realm;

/**
 * 五行生克与亲和判定（docs/05 §2 公式的"灵根系数"一侧；系数值取 NUMBERS §6 的 {@code affinity_matched/neutral/conflict}，关系取自
 * LORE §2.1）。
 *
 * <p>位序与 {@code StrifeData.spiritrootElements} 的契约一致：bit0 金 bit1 木 bit2 水 bit3 火 bit4 土。
 *
 * <p>为什么亲和是<b>算出来的</b>而不是照抄内容表里的 {@code affinity_rule}：亲和是"功法与具体这个玩家灵根"的配对属性，
 * 同一部功法对不同灵根的玩家必须给出不同结果——照抄表里的固定值会让相克惩罚永远不生效，而 LORE 明确要求"相克不禁止修炼， 只是慢，且面板必须给出解释"。表里的 {@code
 * affinity_rule} 只作为"没有灵根可依据时"的兜底。
 */
public final class FiveElements {

    public static final int NONE = 0;
    public static final int JIN = 1;
    public static final int MU = 1 << 1;
    public static final int SHUI = 1 << 2;
    public static final int HUO = 1 << 3;
    public static final int TU = 1 << 4;

    private static final String[] IDS = {"jin", "mu", "shui", "huo", "tu"};

    /** 功法与玩家灵根的亲和三档。 */
    public enum Affinity {
        MATCHED,
        NEUTRAL,
        CONFLICT
    }

    private FiveElements() {}

    /** 位掩码 → 元素 ID（lang key 用）；未知位返回 null。 */
    public static String idOf(int elementBit) {
        for (int i = 0; i < IDS.length; i++) {
            if (elementBit == (1 << i)) {
                return IDS[i];
            }
        }
        return null;
    }

    /** 元素 ID → 位掩码；未知返回 {@link #NONE}。 */
    public static int bitOf(String elementId) {
        for (int i = 0; i < IDS.length; i++) {
            if (IDS[i].equals(elementId)) {
                return 1 << i;
            }
        }
        return NONE;
    }

    /** 五行相生：{@code element} 所生者（金生水、水生木、木生火、火生土、土生金）。 */
    public static int generatedBy(int element) {
        if (element == JIN) {
            return SHUI;
        }
        if (element == SHUI) {
            return MU;
        }
        if (element == MU) {
            return HUO;
        }
        if (element == HUO) {
            return TU;
        }
        if (element == TU) {
            return JIN;
        }
        return NONE;
    }

    /** 五行相克：{@code element} 所克者（金克木、木克土、土克水、水克火、火克金）。 */
    public static int overcomeBy(int element) {
        if (element == JIN) {
            return MU;
        }
        if (element == MU) {
            return TU;
        }
        if (element == TU) {
            return SHUI;
        }
        if (element == SHUI) {
            return HUO;
        }
        if (element == HUO) {
            return JIN;
        }
        return NONE;
    }

    /**
     * 判定功法属性与玩家灵根集合的亲和。
     *
     * <ul>
     *   <li>功法属性 ∈ 灵根集合 → {@link Affinity#MATCHED}（同属性，最顺）
     *   <li>功法与灵根相克（任一方向：功法克灵根，或灵根克功法）→ {@link Affinity#CONFLICT}
     *   <li>其余（含相生与无关）→ {@link Affinity#NEUTRAL}——相生只是"不拖后腿"，不给额外加成
     * </ul>
     *
     * @param techniqueElement 功法主属性位；{@link #NONE} = 无属性要求
     * @param rootMask 玩家五行位掩码；{@link #NONE} = 灵根未生成
     * @param declaredFallback 内容表声明的亲和：仅当没有灵根可依据时使用
     */
    public static Affinity affinity(int techniqueElement, int rootMask, Affinity declaredFallback) {
        if (techniqueElement == NONE) {
            return Affinity.NEUTRAL;
        }
        if (rootMask == NONE) {
            return declaredFallback == null ? Affinity.NEUTRAL : declaredFallback;
        }
        if ((rootMask & techniqueElement) != 0) {
            return Affinity.MATCHED;
        }
        if ((overcomeBy(techniqueElement) & rootMask) != 0) {
            return Affinity.CONFLICT;
        }
        for (int i = 0; i < IDS.length; i++) {
            int root = 1 << i;
            if ((rootMask & root) != 0 && overcomeBy(root) == techniqueElement) {
                return Affinity.CONFLICT;
            }
        }
        return Affinity.NEUTRAL;
    }

    /** 亲和 → NUMBERS §6 的系数值。 */
    public static double coefficient(
            Affinity affinity, double matched, double neutral, double conflict) {
        return switch (affinity) {
            case MATCHED -> matched;
            case CONFLICT -> conflict;
            case NEUTRAL -> neutral;
        };
    }

    /** 内容表的 {@code affinity_rule} 字符串 → 枚举；未知或空返回 null（由调用方回落中性）。 */
    public static Affinity parseDeclared(String declared) {
        if (declared == null || declared.isBlank()) {
            return null;
        }
        return switch (declared) {
            case "matched" -> Affinity.MATCHED;
            case "conflict" -> Affinity.CONFLICT;
            case "neutral" -> Affinity.NEUTRAL;
            default -> null;
        };
    }
}

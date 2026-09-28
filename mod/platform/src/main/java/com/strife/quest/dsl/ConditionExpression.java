package com.strife.quest.dsl;

import java.util.Set;

/**
 * 条件 DSL 的 AST 与求值语义（content/JSON_SCHEMA.md §4.7.1）。
 *
 * <p>谓词语义约定（填表指南 §3.7 的人话口径的代码化）：
 *
 * <ul>
 *   <li>{@code item(id:N)} — 持有该物品<b>至少</b> N 个（"持有某物品数量"按 at-least 读）；
 *   <li>{@code affinity(elem)} — 玩家灵根对该五行的亲和成立与否，判定细节由 {@link Context#affinity} 提供（引擎不认识 NUMBERS §6
 *       系数）；
 *   <li>{@code luck} — H6 预留，本期恒 0，引擎照常比较。
 * </ul>
 *
 * <p>求值是短路布尔树；每个谓词消费一个步数（{@link StepCounter}），超过 {@link ConditionDsl#EVALUATION_STEP_LIMIT} 抛
 * {@link ConditionDsl.EvaluationException}——运行时用 {@link ConditionDsl#satisfies} 降级为 false，校验器（V-DSL
 * 二期）则让它红。
 */
public sealed interface ConditionExpression {

    boolean evaluate(Context context, StepCounter counter);

    /** 求值环境的注入面：QuestEngine / 对话解释器 / V-DSL 各自实现，引擎不认识 Minecraft。 */
    interface Context {

        /** 当前境界 ordinal（0 = 凡人）。 */
        int realm();

        /** H3 命名空间键是否已置位。 */
        boolean flag(String key);

        /** 玩家持有某物品的数量。 */
        int itemCount(String itemId);

        /** H2 声望向量的当前值。 */
        int reputation(String factionId);

        /** 指定任务是否已完成（按任务内容 ID）。 */
        boolean questDone(String questId);

        /** 玩家灵根对某五行的亲和是否成立。 */
        boolean affinity(String element);

        /** 境界 ID → ordinal；未知 ID 抛 {@link ConditionDsl.EvaluationException}（V-DSL 二期在构建期拦）。 */
        int realmOrdinal(String realmId);

        /** 当前小境界序号（1 起）。 */
        int subStage();

        /** H6 福缘，本期恒 0。 */
        int luck();
    }

    /** 求值步数计（§4.7.1 [拟] 上限机制）。 */
    final class StepCounter {

        private int steps;

        public void tick() {
            if (++steps > ConditionDsl.EVALUATION_STEP_LIMIT) {
                throw new ConditionDsl.EvaluationException(
                        "condition evaluation exceeded "
                                + ConditionDsl.EVALUATION_STEP_LIMIT
                                + " steps");
            }
        }

        public int value() {
            return steps;
        }
    }

    Set<String> ELEMENTS = Set.of("jin", "mu", "shui", "huo", "tu");

    record And(ConditionExpression left, ConditionExpression right) implements ConditionExpression {

        @Override
        public boolean evaluate(Context context, StepCounter counter) {
            return left.evaluate(context, counter) && right.evaluate(context, counter);
        }
    }

    record Or(ConditionExpression left, ConditionExpression right) implements ConditionExpression {

        @Override
        public boolean evaluate(Context context, StepCounter counter) {
            return left.evaluate(context, counter) || right.evaluate(context, counter);
        }
    }

    record Not(ConditionExpression inner) implements ConditionExpression {

        @Override
        public boolean evaluate(Context context, StepCounter counter) {
            return !inner.evaluate(context, counter);
        }
    }

    enum Comparison {
        GREATER_OR_EQUAL(">="),
        LESS_OR_EQUAL("<="),
        EQUAL("=="),
        NOT_EQUAL("!=");

        private final String symbol;

        Comparison(String symbol) {
            this.symbol = symbol;
        }

        public String symbol() {
            return symbol;
        }

        public boolean test(long left, long right) {
            return switch (this) {
                case GREATER_OR_EQUAL -> left >= right;
                case LESS_OR_EQUAL -> left <= right;
                case EQUAL -> left == right;
                case NOT_EQUAL -> left != right;
            };
        }

        /** 未知符号返回 null（解析器转 SyntaxException）。 */
        public static Comparison bySymbol(String symbol) {
            for (Comparison comparison : values()) {
                if (comparison.symbol.equals(symbol)) {
                    return comparison;
                }
            }
            return null;
        }
    }

    /** realm 的右值：境界 ID（求值期经 Context 解析 ordinal）或 ordinal 字面量，二者恰一。 */
    record RealmOperand(String realmId, Long ordinal) {

        int resolve(Context context) {
            if (realmId != null) {
                return context.realmOrdinal(realmId);
            }
            return ordinal.intValue();
        }
    }

    record RealmComparison(Comparison comparison, RealmOperand right)
            implements ConditionExpression {

        @Override
        public boolean evaluate(Context context, StepCounter counter) {
            counter.tick();
            return comparison.test(context.realm(), right.resolve(context));
        }
    }

    record SubStageComparison(Comparison comparison, long right) implements ConditionExpression {

        @Override
        public boolean evaluate(Context context, StepCounter counter) {
            counter.tick();
            return comparison.test(context.subStage(), right);
        }
    }

    record LuckComparison(Comparison comparison, long right) implements ConditionExpression {

        @Override
        public boolean evaluate(Context context, StepCounter counter) {
            counter.tick();
            return comparison.test(context.luck(), right);
        }
    }

    record Flag(String key) implements ConditionExpression {

        @Override
        public boolean evaluate(Context context, StepCounter counter) {
            counter.tick();
            return context.flag(key);
        }
    }

    record ItemCount(String itemId, long minimum) implements ConditionExpression {

        @Override
        public boolean evaluate(Context context, StepCounter counter) {
            counter.tick();
            return context.itemCount(itemId) >= minimum;
        }
    }

    record ReputationComparison(String factionId, Comparison comparison, long right)
            implements ConditionExpression {

        @Override
        public boolean evaluate(Context context, StepCounter counter) {
            counter.tick();
            return comparison.test(context.reputation(factionId), right);
        }
    }

    record QuestDone(String questId) implements ConditionExpression {

        @Override
        public boolean evaluate(Context context, StepCounter counter) {
            counter.tick();
            return context.questDone(questId);
        }
    }

    record Affinity(String element) implements ConditionExpression {

        @Override
        public boolean evaluate(Context context, StepCounter counter) {
            counter.tick();
            return context.affinity(element);
        }
    }
}

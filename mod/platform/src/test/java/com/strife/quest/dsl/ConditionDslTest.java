package com.strife.quest.dsl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.strife.quest.dsl.ConditionDsl.EvaluationException;
import com.strife.quest.dsl.ConditionDsl.SyntaxException;
import com.strife.quest.dsl.ConditionExpression.Context;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 条件 DSL 的用例矩阵（docs/07 §7 M3 验收：Agent 生成用例矩阵）。语义基准：JSON_SCHEMA §4.7.1 + 填表指南 §3.7。 */
class ConditionDslTest {

    /** 可编程上下文：默认 凡人(realm 0)/小境界 1/luck 0，其余按需塞。 */
    private static final class ScriptedContext implements Context {

        private int realm;
        private int subStage = 1;
        private int luck;
        private final Map<String, Boolean> flags = new HashMap<>();
        private final Map<String, Integer> items = new HashMap<>();
        private final Map<String, Integer> reputation = new HashMap<>();
        private final Map<String, Boolean> quests = new HashMap<>();
        private final Map<String, Boolean> affinities = new HashMap<>();
        private final Map<String, Integer> realmOrdinals =
                new HashMap<>(Map.of("fanren", 0, "qili", 1, "zhuji", 2));

        private ScriptedContext realm(int realm) {
            this.realm = realm;
            return this;
        }

        ScriptedContext flag(String key, boolean value) {
            flags.put(key, value);
            return this;
        }

        ScriptedContext item(String id, int count) {
            items.put(id, count);
            return this;
        }

        ScriptedContext reputation(String faction, int value) {
            reputation.put(faction, value);
            return this;
        }

        ScriptedContext done(String questId) {
            quests.put(questId, true);
            return this;
        }

        ScriptedContext affinity(String element, boolean value) {
            affinities.put(element, value);
            return this;
        }

        @Override
        public int realm() {
            return realm;
        }

        @Override
        public boolean flag(String key) {
            return flags.getOrDefault(key, false);
        }

        @Override
        public int itemCount(String itemId) {
            return items.getOrDefault(itemId, 0);
        }

        @Override
        public int reputation(String factionId) {
            return reputation.getOrDefault(factionId, 0);
        }

        @Override
        public boolean questDone(String questId) {
            return quests.getOrDefault(questId, false);
        }

        @Override
        public boolean affinity(String element) {
            return affinities.getOrDefault(element, false);
        }

        @Override
        public int realmOrdinal(String realmId) {
            Integer ordinal = realmOrdinals.get(realmId);
            if (ordinal == null) {
                throw new EvaluationException("unknown realm id '" + realmId + "'");
            }
            return ordinal;
        }

        @Override
        public int subStage() {
            return subStage;
        }

        @Override
        public int luck() {
            return luck;
        }
    }

    private static ScriptedContext base() {
        return new ScriptedContext();
    }

    private static boolean eval(String source, Context context) throws SyntaxException {
        return ConditionDsl.parse(source).evaluate(context, new ConditionExpression.StepCounter());
    }

    // ===== 每个谓词 =====

    @Test
    void realmComparesAgainstOrdinalLiteral() throws SyntaxException {
        assertTrue(eval("realm>=1", base().realm(1)));
        assertFalse(eval("realm>=1", base().realm(0)));
        assertTrue(eval("realm==0", base().realm(0)));
        assertTrue(eval("realm!=2", base().realm(0)));
        assertTrue(eval("realm<=1", base().realm(1)));
    }

    /** §4.7.1 写 NUM、指南示例写 realm>=qili——两种右值都收，ID 由 Context 解析。 */
    @Test
    void realmComparesAgainstARealmIdResolvedByContext() throws SyntaxException {
        assertTrue(eval("realm>=qili", base().realm(1)));
        assertFalse(eval("realm>=zhuji", base().realm(1)));
    }

    @Test
    void subStageAndLuckCompare() throws SyntaxException {
        assertTrue(eval("sub_stage<=3", base()));
        assertFalse(eval("sub_stage==2", base()));
        assertTrue(eval("luck>=0", base()));
        assertTrue(eval("luck==0", base()), "H6 本期恒 0");
    }

    @Test
    void flagReadsH3NamespaceKeys() throws SyntaxException {
        assertTrue(
                eval(
                        "flag(fac_qingshi:prologue:met_elder)",
                        base().flag("fac_qingshi:prologue:met_elder", true)));
        assertFalse(eval("flag(fac_qingshi:prologue:met_elder)", base()));
    }

    /** 指南 §3.7 口径：item(...) 是"持有至少 N 个"。 */
    @Test
    void itemIsAtLeastSemantics() throws SyntaxException {
        assertTrue(eval("item(item_lingshi:10)", base().item("item_lingshi", 10)));
        assertTrue(eval("item(item_lingshi:10)", base().item("item_lingshi", 12)));
        assertFalse(eval("item(item_lingshi:10)", base().item("item_lingshi", 9)));
        assertFalse(eval("item(item_lingshi:10)", base()));
    }

    @Test
    void reputationQuestDoneAndAffinity() throws SyntaxException {
        assertTrue(eval("reputation(fac_qingshi:>=50)", base().reputation("fac_qingshi", 50)));
        assertFalse(eval("reputation(fac_qingshi:>=50)", base().reputation("fac_qingshi", -10)));
        assertTrue(eval("reputation(fac_yuelai:==-5)", base().reputation("fac_yuelai", -5)));
        assertTrue(
                eval(
                        "quest_done(quest_prologue_meditation_01)",
                        base().done("quest_prologue_meditation_01")));
        assertTrue(eval("affinity(shui)", base().affinity("shui", true)));
        assertFalse(eval("affinity(shui)", base()));
    }

    // ===== 组合与优先级 =====

    @Test
    void andOrNotCompose() throws SyntaxException {
        ScriptedContext met =
                base().flag("fac_qingshi:prologue:met_elder", true).item("item_lingshi", 3);
        assertTrue(
                eval(
                        "realm>=qili && flag(fac_qingshi:prologue:met_elder) && item(item_lingshi:3)",
                        met.realm(1)),
                "契约原话示例（§4.7.1）");
        assertTrue(eval("!flag(fac_x:a:b)", base()));
        assertTrue(eval("!(realm>=1)", base()), "凡人 0 不满足 realm>=1，取反为真");
    }

    @Test
    void precedenceIsNotThenAndThenOr() throws SyntaxException {
        // a || b && c = a || (b && c)——a 为真时整体为真，即便 b&&c 为假
        assertTrue(eval("flag(x:y:z) || flag(a:b:c) && flag(p:q:r)", base().flag("x:y:z", true)));
        // !b && c = (!b) && c
        assertTrue(eval("!flag(a:b:c) && flag(p:q:r)", base().flag("p:q:r", true)));
        // a && b || c = (a && b) || c
        assertFalse(eval("flag(a:b:c) && flag(p:q:r) || flag(x:y:z)", base()));
    }

    @Test
    void parenthesesOverridePrecedence() throws SyntaxException {
        assertTrue(
                eval(
                        "(flag(a:b:c) || flag(p:q:r)) && realm>=1",
                        base().flag("a:b:c", true).realm(1)));
        // a=true 时 a || (p && realm) 短路为真——与优先级一测同源
        assertTrue(eval("flag(a:b:c) || flag(p:q:r) && realm>=1", base().flag("a:b:c", true)));
        assertFalse(
                eval("flag(a:b:c) || flag(p:q:r) && realm>=1", base().realm(1)),
                "无括号时 && 先结合：p 为假则整体假");
        assertTrue(
                eval(
                        "flag(a:b:c) || (flag(p:q:r) && realm>=1)",
                        base().realm(1).flag("p:q:r", true)));
    }

    @Test
    void whitespaceIsFree() throws SyntaxException {
        assertTrue(
                eval(
                        "  realm  >=  1   &&  flag( a : b : c )  ",
                        base().realm(1).flag("a:b:c", true)));
    }

    // ===== 解析错误 =====

    private static SyntaxException parseError(String source) {
        return assertThrows(SyntaxException.class, () -> ConditionDsl.parse(source));
    }

    @Test
    void unknownPredicatesAreRejected() {
        SyntaxException error = parseError("mana>=5");
        assertTrue(error.getMessage().contains("unknown predicate 'mana'"), error.getMessage());
    }

    @Test
    void malformedComparisonsAndParensAreRejected() {
        assertTrue(parseError("realm>1").getMessage().contains(">="), "单独 > 不允许");
        assertTrue(parseError("realm>=1 =").getMessage().contains("unexpected '='"), "悬空赋值号");
        assertTrue(parseError("(realm>=1").getMessage().contains("')'"), "括号不闭合");
        assertTrue(parseError("realm>=1)").getMessage().contains("trailing"), "多余右括号");
        assertTrue(
                parseError("flag(fac_qingshi:met_elder)").getMessage().contains("three segments"),
                "H3 键两段");
        assertTrue(parseError("affinity(gold)").getMessage().contains("affinity"), "元素不在词表");
        assertTrue(parseError("item(item_lingshi:)").getMessage().contains("integer"), "缺数量");
        assertTrue(parseError("").getMessage().contains("empty"), "空白条件是填表漏项");
        assertTrue(
                parseError("realm>=QILI").getMessage().contains("unexpected character"), "大写不在词法里");
        assertTrue(parseError("realm>=1 &&").getMessage().contains("predicate"), "&& 后悬空");
    }

    @Test
    void deeplyNestedExpressionsHitTheParseGuard() {
        StringBuilder deep = new StringBuilder();
        for (int i = 0; i < ConditionDsl.PARSE_DEPTH_LIMIT + 10; i++) {
            deep.append('(');
        }
        deep.append("realm>=1");
        for (int i = 0; i < ConditionDsl.PARSE_DEPTH_LIMIT + 10; i++) {
            deep.append(')');
        }
        assertTrue(parseError(deep.toString()).getMessage().contains("nests deeper"));
    }

    // ===== 求值护栏 =====

    @Test
    void evaluationStepLimitKicksInAndSatisfiesDegradesToFalse() throws SyntaxException {
        StringBuilder chain = new StringBuilder();
        for (int i = 0; i < ConditionDsl.EVALUATION_STEP_LIMIT + 1; i++) {
            if (i > 0) {
                chain.append("&&");
            }
            chain.append("item(item_x:0)");
        }
        ConditionExpression expression = ConditionDsl.parse(chain.toString());

        assertThrows(
                EvaluationException.class,
                () -> expression.evaluate(base(), new ConditionExpression.StepCounter()));
        assertFalse(ConditionDsl.satisfies(expression, base()), "§4.7.1：超限按 false 处理（调用方记日志）");
    }

    /** 未知境界 ID 在求值期炸出——V-DSL 二期要在构建期拦住同一件事。 */
    @Test
    void unknownRealmIdDegradesToFalseUnderSatisfies() throws SyntaxException {
        ConditionExpression expression = ConditionDsl.parse("realm>=yuan_shen_realm");
        assertThrows(
                EvaluationException.class,
                () -> expression.evaluate(base(), new ConditionExpression.StepCounter()));
        assertFalse(ConditionDsl.satisfies(expression, base()));
    }

    @Test
    void shortCircuitSkipsUnneededPredicates() throws SyntaxException {
        ConditionExpression expression = ConditionDsl.parse("flag(x:y:z) || flag(p:q:r)");
        ConditionExpression.StepCounter counter = new ConditionExpression.StepCounter();
        assertTrue(expression.evaluate(base().flag("x:y:z", true), counter));
        assertEquals(1, counter.value(), "|| 短路：右侧谓词不消费步数");
    }

    @Test
    void comparisonSymbolsMapRoundTrip() {
        for (ConditionExpression.Comparison comparison : ConditionExpression.Comparison.values()) {
            assertEquals(comparison, ConditionExpression.Comparison.bySymbol(comparison.symbol()));
        }
    }
}

package com.strife.quest.dsl;

import java.util.ArrayList;
import java.util.List;

/**
 * 条件 DSL 的解析入口（content/JSON_SCHEMA.md §4.7.1，语法冻结；语义来自 docs/03 §8）。
 *
 * <p>语法（按标准布尔优先级消歧：{@code !} &gt; {@code &&} &gt; {@code ||}，括号改变结合）：
 *
 * <pre>
 * expr      := or
 * or        := and ( '||' and )*
 * and       := unary ( '&&' unary )*
 * unary     := '!' unary | primary
 * primary   := '(' expr ')' | predicate
 * predicate := realm CMP (NUM | realmId) | sub_stage CMP NUM | luck CMP NUM
 *            | flag '(' H3KEY ')' | item '(' itemId ':' NUM ')'
 *            | reputation '(' factionId ':' CMP NUM ')' | quest_done '(' questId ')'
 *            | affinity '(' element ')'
 * CMP       := '>=' | '<=' | '==' | '!='
 * </pre>
 *
 * <p>契约没有给出 {@code realm} 右值的定论（§4.7.1 写 NUM，填表指南示例写 {@code realm>=qili}）， 两种都收：字面量直接比较 ordinal，境界
 * ID 由 {@link ConditionExpression.Context#realmOrdinal} 解析——未知 ID 在求值期抛 {@link
 * EvaluationException}，由 V-DSL（二期）在构建期拦住。
 *
 * <p>机制参数：求值步数上限 1000（§4.7.1 {@code [拟]}：超限按 false 处理并记日志）与解析深度护栏是 契约/机制参数，不是受管游戏数值。
 */
public final class ConditionDsl {

    /** §4.7.1 [拟]：求值步数上限，超限按 false 处理并记日志。 */
    public static final int EVALUATION_STEP_LIMIT = 1000;

    /** 解析深度护栏：防"表写错死循环"式的深嵌套把解析线程栈打爆（docs/03 §8 同源动机）。 */
    public static final int PARSE_DEPTH_LIMIT = 128;

    private ConditionDsl() {}

    /** 解析失败：位置与可读原因齐备（docs/04 §6 报错口径：调用方补上表文件与行号即可自助修复）。 */
    public static final class SyntaxException extends Exception {

        private final int position;

        SyntaxException(int position, String message) {
            super(message);
            this.position = position;
        }

        public int position() {
            return position;
        }
    }

    /** 求值失败（未知境界 ID、步数超限等）：运行时按 false 降级，见 {@link #satisfies}。 */
    public static final class EvaluationException extends RuntimeException {

        public EvaluationException(String message) {
            super(message);
        }
    }

    /** 解析一个条件表达式；空串与全空白是非法条件（没写条件就不该调用解析器）。 */
    public static ConditionExpression parse(String source) throws SyntaxException {
        if (source == null || source.isBlank()) {
            throw new SyntaxException(0, "condition is empty — 空白条件是填表漏项，不是 false");
        }
        Parser parser = new Parser(tokenize(source), source);
        ConditionExpression expression = parser.parseExpression(0);
        parser.expectEnd();
        return expression;
    }

    /**
     * 求值的容错形态：任何 {@link EvaluationException}（含步数超限、未知境界 ID）一律 false—— §4.7.1 的"超限按 false
     * 处理并记日志"；调用方负责记日志。
     */
    public static boolean satisfies(
            ConditionExpression expression, ConditionExpression.Context context) {
        try {
            return expression.evaluate(context, new ConditionExpression.StepCounter());
        } catch (EvaluationException e) {
            return false;
        }
    }

    private static List<Token> tokenize(String source) throws SyntaxException {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (c == '(') {
                tokens.add(new Token(TokenType.LEFT_PAREN, "(", i++));
                continue;
            }
            if (c == ')') {
                tokens.add(new Token(TokenType.RIGHT_PAREN, ")", i++));
                continue;
            }
            if (c == ':') {
                tokens.add(new Token(TokenType.COLON, ":", i++));
                continue;
            }
            if (c == '!') {
                if (i + 1 < source.length() && source.charAt(i + 1) == '=') {
                    tokens.add(new Token(TokenType.NOT_EQUAL, "!=", i));
                    i += 2;
                } else {
                    tokens.add(new Token(TokenType.NOT, "!", i++));
                }
                continue;
            }
            if (c == '>') {
                if (i + 1 < source.length() && source.charAt(i + 1) == '=') {
                    tokens.add(new Token(TokenType.GREATER_OR_EQUAL, ">=", i));
                    i += 2;
                } else {
                    throw new SyntaxException(i, "unexpected '>' — 比较-only 支持 >= <= == !=");
                }
                continue;
            }
            if (c == '<') {
                if (i + 1 < source.length() && source.charAt(i + 1) == '=') {
                    tokens.add(new Token(TokenType.LESS_OR_EQUAL, "<=", i));
                    i += 2;
                } else {
                    throw new SyntaxException(i, "unexpected '<' — 比较-only 支持 >= <= == !=");
                }
                continue;
            }
            if (c == '=') {
                if (i + 1 < source.length() && source.charAt(i + 1) == '=') {
                    tokens.add(new Token(TokenType.EQUAL, "==", i));
                    i += 2;
                } else {
                    throw new SyntaxException(i, "unexpected '=' — 相等写作 ==");
                }
                continue;
            }
            if (c == '&') {
                if (i + 1 < source.length() && source.charAt(i + 1) == '&') {
                    tokens.add(new Token(TokenType.AND, "&&", i));
                    i += 2;
                    continue;
                }
                throw new SyntaxException(i, "unexpected '&' — 逻辑与写作 &&");
            }
            if (c == '|') {
                if (i + 1 < source.length() && source.charAt(i + 1) == '|') {
                    tokens.add(new Token(TokenType.OR, "||", i));
                    i += 2;
                    continue;
                }
                throw new SyntaxException(i, "unexpected '|' — 逻辑或写作 ||");
            }
            // 数字带可选负号：声望区间是 [-100,100]，reputation 的右值必须能写负数
            if (Character.isDigit(c)
                    || (c == '-'
                            && i + 1 < source.length()
                            && Character.isDigit(source.charAt(i + 1)))) {
                int start = i;
                if (c == '-') {
                    i++;
                }
                while (i < source.length() && Character.isDigit(source.charAt(i))) {
                    i++;
                }
                tokens.add(new Token(TokenType.NUMBER, source.substring(start, i), start));
                continue;
            }
            if (isIdentifierStart(c)) {
                int start = i;
                while (i < source.length() && isIdentifierPart(source.charAt(i))) {
                    i++;
                }
                tokens.add(new Token(TokenType.IDENTIFIER, source.substring(start, i), start));
                continue;
            }
            throw new SyntaxException(i, "unexpected character '" + c + "' — 条件只允许小写标识符、数字与运算符");
        }
        tokens.add(new Token(TokenType.END, "", source.length()));
        return tokens;
    }

    private static boolean isIdentifierStart(char c) {
        return (c >= 'a' && c <= 'z') || c == '_';
    }

    private static boolean isIdentifierPart(char c) {
        return isIdentifierStart(c) || Character.isDigit(c);
    }

    private enum TokenType {
        IDENTIFIER,
        NUMBER,
        COLON,
        LEFT_PAREN,
        RIGHT_PAREN,
        NOT,
        AND,
        OR,
        EQUAL,
        NOT_EQUAL,
        GREATER_OR_EQUAL,
        LESS_OR_EQUAL,
        END
    }

    private record Token(TokenType type, String text, int position) {}

    private static final class Parser {

        private final List<Token> tokens;
        private final String source;
        private int position;
        private int depth;

        Parser(List<Token> tokens, String source) {
            this.tokens = tokens;
            this.source = source;
        }

        ConditionExpression parseExpression(int depth) throws SyntaxException {
            if (depth > PARSE_DEPTH_LIMIT) {
                throw new SyntaxException(
                        peek().position(),
                        "expression nests deeper than " + PARSE_DEPTH_LIMIT + " levels");
            }
            ConditionExpression left = parseAnd(depth + 1);
            while (peek().type() == TokenType.OR) {
                advance();
                left = new ConditionExpression.Or(left, parseAnd(depth + 1));
            }
            return left;
        }

        private ConditionExpression parseAnd(int depth) throws SyntaxException {
            ConditionExpression left = parseUnary(depth + 1);
            while (peek().type() == TokenType.AND) {
                advance();
                left = new ConditionExpression.And(left, parseUnary(depth + 1));
            }
            return left;
        }

        private ConditionExpression parseUnary(int depth) throws SyntaxException {
            if (peek().type() == TokenType.NOT) {
                advance();
                return new ConditionExpression.Not(parseUnary(depth + 1));
            }
            return parsePrimary(depth + 1);
        }

        private ConditionExpression parsePrimary(int depth) throws SyntaxException {
            if (depth > PARSE_DEPTH_LIMIT) {
                throw new SyntaxException(
                        peek().position(),
                        "expression nests deeper than " + PARSE_DEPTH_LIMIT + " levels");
            }
            if (peek().type() == TokenType.LEFT_PAREN) {
                advance();
                ConditionExpression inner = parseExpression(depth + 1);
                expect(TokenType.RIGHT_PAREN, "')'");
                return inner;
            }
            return parsePredicate();
        }

        private ConditionExpression parsePredicate() throws SyntaxException {
            Token token = peek();
            if (token.type() != TokenType.IDENTIFIER) {
                throw new SyntaxException(
                        token.position(), "expected a predicate, found '" + token.text() + "'");
            }
            switch (token.text()) {
                case "realm" -> {
                    advance();
                    return new ConditionExpression.RealmComparison(comparison(), realmOperand());
                }
                case "sub_stage" -> {
                    advance();
                    return new ConditionExpression.SubStageComparison(
                            comparison(), number("sub_stage"));
                }
                case "luck" -> {
                    advance();
                    return new ConditionExpression.LuckComparison(comparison(), number("luck"));
                }
                case "flag" -> {
                    advance();
                    return new ConditionExpression.Flag(h3Key("flag"));
                }
                case "item" -> {
                    advance();
                    expect(TokenType.LEFT_PAREN, "'('");
                    String itemId = identifier("item id");
                    expect(TokenType.COLON, "':'");
                    long count = number("item");
                    expect(TokenType.RIGHT_PAREN, "')'");
                    return new ConditionExpression.ItemCount(itemId, count);
                }
                case "reputation" -> {
                    advance();
                    expect(TokenType.LEFT_PAREN, "'('");
                    String faction = identifier("faction id");
                    expect(TokenType.COLON, "':'");
                    ConditionExpression.Comparison comparison = comparison();
                    long value = number("reputation");
                    expect(TokenType.RIGHT_PAREN, "')'");
                    return new ConditionExpression.ReputationComparison(faction, comparison, value);
                }
                case "quest_done" -> {
                    advance();
                    expect(TokenType.LEFT_PAREN, "'('");
                    String questId = identifier("quest id");
                    expect(TokenType.RIGHT_PAREN, "')'");
                    return new ConditionExpression.QuestDone(questId);
                }
                case "affinity" -> {
                    advance();
                    expect(TokenType.LEFT_PAREN, "'('");
                    String element = identifier("element");
                    if (!ConditionExpression.ELEMENTS.contains(element)) {
                        throw new SyntaxException(
                                token.position(),
                                "affinity '"
                                        + element
                                        + "' is not one of "
                                        + ConditionExpression.ELEMENTS);
                    }
                    expect(TokenType.RIGHT_PAREN, "')'");
                    return new ConditionExpression.Affinity(element);
                }
                default ->
                        throw new SyntaxException(
                                token.position(),
                                "unknown predicate '"
                                        + token.text()
                                        + "' — allowed: realm/sub_stage/luck/flag/item/reputation/"
                                        + "quest_done/affinity (JSON_SCHEMA §4.7.1)");
            }
        }

        private ConditionExpression.Comparison comparison() throws SyntaxException {
            Token token = peek();
            ConditionExpression.Comparison comparison =
                    ConditionExpression.Comparison.bySymbol(token.text());
            if (comparison == null) {
                throw new SyntaxException(
                        token.position(),
                        "expected a comparison (>= <= == !=), found '" + token.text() + "'");
            }
            advance();
            return comparison;
        }

        /** realm 的右值：境界 ID 或 ordinal 字面量都收（解析期无法查链序，求值期解析）。 */
        private ConditionExpression.RealmOperand realmOperand() throws SyntaxException {
            Token token = peek();
            if (token.type() == TokenType.NUMBER) {
                advance();
                return new ConditionExpression.RealmOperand(null, Long.parseLong(token.text()));
            }
            if (token.type() == TokenType.IDENTIFIER) {
                advance();
                return new ConditionExpression.RealmOperand(token.text(), null);
            }
            throw new SyntaxException(
                    token.position(),
                    "realm comparison expects a realm id or an ordinal, found '"
                            + token.text()
                            + "'");
        }

        private long number(String where) throws SyntaxException {
            Token token = peek();
            if (token.type() != TokenType.NUMBER) {
                throw new SyntaxException(
                        token.position(),
                        where + " expects a non-negative integer, found '" + token.text() + "'");
            }
            advance();
            return Long.parseLong(token.text());
        }

        /** H3 运行时键：&lt;命名空间&gt;:&lt;章&gt;:&lt;语义&gt; 三段，刻意与内容 ID 形态不同（JSON_SCHEMA §5）。 */
        private String h3Key(String where) throws SyntaxException {
            expect(TokenType.LEFT_PAREN, "'('");
            StringBuilder key = new StringBuilder(identifier(where));
            int segments = 1;
            while (peek().type() == TokenType.COLON) {
                advance();
                key.append(':').append(identifier(where));
                segments++;
            }
            expect(TokenType.RIGHT_PAREN, "')'");
            if (segments != 3) {
                throw new SyntaxException(
                        peek().position(),
                        where
                                + " key '"
                                + key
                                + "' must be <命名空间>:<章>:<语义> (three segments, JSON_SCHEMA §5 H3)");
            }
            return key.toString();
        }

        private String identifier(String what) throws SyntaxException {
            Token token = peek();
            if (token.type() != TokenType.IDENTIFIER) {
                throw new SyntaxException(
                        token.position(),
                        what + " expects an identifier, found '" + token.text() + "'");
            }
            advance();
            return token.text();
        }

        private void expectEnd() throws SyntaxException {
            Token token = peek();
            if (token.type() != TokenType.END) {
                throw new SyntaxException(
                        token.position(), "unexpected trailing input '" + token.text() + "'");
            }
        }

        private void expect(TokenType type, String readable) throws SyntaxException {
            Token token = peek();
            if (token.type() != type) {
                throw new SyntaxException(
                        token.position(),
                        "expected " + readable + ", found '" + token.text() + "'");
            }
            advance();
        }

        private Token peek() {
            return tokens.get(position);
        }

        private void advance() {
            if (position < tokens.size() - 1) {
                position++;
            }
        }
    }
}

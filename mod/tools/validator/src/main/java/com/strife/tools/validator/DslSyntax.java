package com.strife.tools.validator;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 条件 DSL 与对话 effects 的独立语法校验器（docs/04 §6 V-DSL）。
 *
 * <p>刻意<b>不依赖</b> platform 的 {@code ConditionDsl}：validator 是构建期独立 JVM 工具（03 §7），
 * 不引游戏代码。这里做的是词法级交叉验证——运行时解释器与构建期校验器对同一语法的实现若漂移，红的是构建。 语义正确性（未知内容 ID、取值范围）不在本类职责内（V-REF/V-RANGE
 * 的事）。
 *
 * <p>语法契约（content/JSON_SCHEMA.md §4.7.1 / docs/03 §10）：
 *
 * <pre>
 * expr    := term (('&&' | '||') term)* | '!' expr
 * term    := predicate | '(' expr ')'
 * predicate := name '(' args ')' | name 比较符 数字
 * </pre>
 */
final class DslSyntax {

    /** 运行时 Context 支持的谓词名（与 ConditionExpression.Context 方法一一对应；新增谓词须双侧同步）。 */
    private static final Set<String> PREDICATES =
            Set.of(
                    "realm",
                    "stage",
                    "subStage",
                    "flag",
                    "item",
                    "reputation",
                    "questDone",
                    "affinity",
                    "realmOrdinal",
                    "luck");

    private static final Set<String> COMPARISONS = Set.of(">=", "<=", "==", "!=", ">", "<");

    private DslSyntax() {}

    /** 校验一条条件表达式；问题列表非空即语法错误（每条带定位前缀 {@code where}）。 */
    static List<String> validateCondition(String source, String where) {
        List<String> problems = new ArrayList<>();
        if (source == null || source.isBlank()) {
            return problems;
        }
        String text = source.trim();
        int depth = 0;
        int i = 0;
        int maxStep = 1000;
        while (i < text.length()) {
            if (--maxStep < 0) {
                problems.add(where + ": 条件表达式超过 1000 步求值上限（docs/03 §10）");
                return problems;
            }
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (c == '(') {
                depth++;
                i++;
                continue;
            }
            if (c == ')') {
                depth--;
                if (depth < 0) {
                    problems.add(where + ": 位置 " + i + " 出现多余的 ')'");
                    return problems;
                }
                i++;
                continue;
            }
            if (c == '!') {
                // '!' 只允许作为否定前缀（后跟谓词/括号）或 '!=' 比较符（在谓词内处理）
                if (i + 1 < text.length() && text.charAt(i + 1) == '=') {
                    problems.add(where + ": 位置 " + i + " 出现孤立的 '!='（比较符只能在谓词内）");
                    return problems;
                }
                i++;
                continue;
            }
            if (c == '&' || c == '|') {
                if (i + 1 >= text.length() || text.charAt(i + 1) != c) {
                    problems.add(where + ": 位置 " + i + " 的 '" + c + "' 必须成对（&& / ||）");
                    return problems;
                }
                i += 2;
                continue;
            }
            if (Character.isLetter(c) || c == '_') {
                int start = i;
                while (i < text.length()
                        && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '_')) {
                    i++;
                }
                String name = text.substring(start, i);
                if (PREDICATES.contains(name)) {
                    i = validatePredicate(text, i, where, problems);
                    if (!problems.isEmpty()
                            && !problems.get(problems.size() - 1).startsWith(where)) {
                        return problems;
                    }
                } else {
                    problems.add(
                            where
                                    + ": 未知谓词 '"
                                    + name
                                    + "'（合法谓词："
                                    + String.join("/", PREDICATES.stream().sorted().toList())
                                    + "）");
                    return problems;
                }
                continue;
            }
            problems.add(where + ": 位置 " + i + " 出现非法字符 '" + c + "'");
            return problems;
        }
        if (depth != 0) {
            problems.add(where + ": 括号不平衡（缺 " + depth + " 个 ')'）");
        }
        return problems;
    }

    /** 谓词调用：name(...) 或 name 比较符 数字。返回扫描后的位置。 */
    private static int validatePredicate(String text, int i, String where, List<String> problems) {
        // 跳过空白后必须是 '('（带参谓词）——无参谓词当前不存在，全部需要括号
        int j = i;
        while (j < text.length() && Character.isWhitespace(text.charAt(j))) {
            j++;
        }
        if (j >= text.length() || text.charAt(j) != '(') {
            problems.add(where + ": 谓词 '" + peekName(text, i) + "' 缺少 '(' 参数表");
            return j;
        }
        int close = findClose(text, j);
        if (close < 0) {
            problems.add(where + ": 谓词 '" + peekName(text, i) + "' 的参数表缺 ')'");
            return text.length();
        }
        String args = text.substring(j + 1, close).trim();
        String name = peekName(text, i);
        // flag/reputation/questDone/realmOrdinal 取内容 ID（允许冒号/连字符/下划线/点）；
        // item 取 <id>:<最少数量>；realm/stage/subStage/luck/affinity/realmOrdinal 取比较表达式或 ID
        if (name.equals("item")) {
            String[] parts = args.split(":");
            if (parts.length != 2 || !parts[1].trim().matches("\\d+")) {
                problems.add(where + ": item 谓词参数必须为 <item_id>:<最少数量>，实际 '" + args + "'");
            }
        } else if (args.isEmpty()) {
            problems.add(where + ": 谓词 '" + name + "' 的参数为空");
        } else if (!args.matches("[\\w:.\\-]+(\\s*(>=|<=|==|!=|>|<)\\s*-?\\d+)?")
                && !args.matches("[\\w:\\-]+")) {
            problems.add(where + ": 谓词 '" + name + "' 的参数 '" + args + "' 含非法字符");
        }
        return close + 1;
    }

    private static int findClose(String text, int open) {
        int depth = 0;
        for (int i = open; i < text.length(); i++) {
            if (text.charAt(i) == '(') {
                depth++;
            } else if (text.charAt(i) == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String peekName(String text, int nameStart) {
        int end = nameStart;
        while (end < text.length()
                && (Character.isLetterOrDigit(text.charAt(end)) || text.charAt(end) == '_')) {
            end++;
        }
        return text.substring(nameStart, end);
    }

    /** 校验一条对话 effect 的 type/args 契约（JSON_SCHEMA §4.7 effects 语义补注 `[拟]`）。 返回 null = 合法。 */
    static String validateEffect(String type, String args, String where) {
        boolean hasArgs = args != null && !args.isBlank();
        switch (type == null ? "" : type) {
            case "set_flag" -> {
                if (!hasArgs || !args.trim().matches("[\\w:\\-]+")) {
                    return where + ": set_flag 需要 <flag键>，实际 '" + args + "'";
                }
            }
            case "reputation" -> {
                if (!hasArgs || !args.trim().matches("[\\w\\-]+\\s*:\\s*-?\\d+")) {
                    return where + ": reputation 需要 <fac_id>:<delta>，实际 '" + args + "'";
                }
            }
            case "give_item", "take_item" -> {
                if (!hasArgs || !args.trim().matches("[\\w\\-]+\\s*:\\s*\\d+")) {
                    return where + ": " + type + " 需要 <item_id>:<count>，实际 '" + args + "'";
                }
            }
            case "start_quest" -> {
                if (!hasArgs || !args.trim().matches("[\\w\\-]+")) {
                    return where + ": start_quest 需要 <quest_id>，实际 '" + args + "'";
                }
            }
            case "complete_node" -> {
                // args 可空（默认记当前节点）；非空时须是合法 flag 键
                if (hasArgs && !args.trim().matches("[\\w:\\-]+")) {
                    return where + ": complete_node 的自定义键非法，实际 '" + args + "'";
                }
            }
            case "play_sound" -> {
                if (!hasArgs
                        || !args.trim().matches("[\\w:.\\-]+(\\s*:\\s*[\\d.]+\\s*:\\s*[\\d.]+)?")) {
                    return where
                            + ": play_sound 需要 <sound_id>[:<volume>:<pitch>]，实际 '"
                            + args
                            + "'";
                }
            }
            case "teleport" -> {
                if (!hasArgs
                        || !args.trim()
                                .matches(
                                        "-?[\\d.]+\\s*,\\s*-?[\\d.]+\\s*,\\s*-?[\\d.]+(\\s*,\\s*[\\w:.-]+)?")) {
                    return where + ": teleport 需要 <x>,<y>,<z>[,<dimension>]，实际 '" + args + "'";
                }
            }
            default -> {
                return where + ": 未知 effect type '" + type + "'（§4.7 八型之外）";
            }
        }
        return null;
    }
}

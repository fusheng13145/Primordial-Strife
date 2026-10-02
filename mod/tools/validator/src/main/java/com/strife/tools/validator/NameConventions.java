package com.strife.tools.validator;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * V-NAME — ID 命名规范门禁（docs/11 §2）。
 *
 * <p>docs/11 划了一条红线：**机器标识符用 ASCII 拼音，人类可读文本用中文**。靠人记会漏，所以本门禁把它变成机器判定：
 *
 * <ol>
 *   <li><b>ID 形状</b>：全小写、下划线分词、无连字符、无驼峰（{@code block_ore_lingyu} ✓ / {@code blockOreLingyu} ✗ / {@code ore-lingyu} ✗）。
 *   <li><b>ID 语言</b>：不得含汉字或其他非 ASCII 字母——原版 {@code ResourceLocation} 只允许 {@code [a-z0-9_.]}，写成中文会**加载期直接崩**，
 *       这不是风格问题而是物理约束（docs/11 §1）。
 *   <li><b>显示文本语言</b>：{@code zh_cn.json} 的每个非空值必须含汉字。缺了就是"该中文的地方没中文"，比 ID 形状错更该红。
 *   <li><b>lang key 形状</b>：{@code <类别>.strife.<id>} 三段齐全。
 * </ol>
 *
 * <p>检查<b>源表与产物两侧</b>：只查产物的话，手写坏 ID 要等 DataGen 生成后才暴露；只查源表的话，产物被手改就漏。两侧都查，与 V-DUP 同理。
 *
 * <p>台账表（{@code id_migration} / {@code known-placeholders}）的第一列是记录号而非内容 ID，只查"不得含非 ASCII 字母"，
 * 不套用形状规则——与 {@link ValidatorMain} 的 {@code NON_CONTENT_TABLES} 豁免口径一致。
 */
final class NameConventions {

    /**
     * 合法的内容 ID：{@code [a-z][a-z0-9]*(_[a-z0-9]+)*}。
     *
     * <p>刻意不允许：连字符（lang key 与 ID 惯例用下划线；Java 标识符也不能含 {@code -}）、驼峰（docs/11 §2 全小写下划线）、
     * 前后下划线与双下划线（{@code _id} / {@code id_} / {@code a__b} 都会被读成拼写事故）。
     */
    private static final Pattern ID_SHAPE = Pattern.compile("^[a-z][a-z0-9]*(_[a-z0-9]+)*$");

    /**
     * lang key 三段式：{@code <类别>.strife.<id>}，命名空间固定 {@code strife}（ADR-017）。
     *
     * <p>id 段允许再点分（{@code gui.strife.hud.realm}、{@code gui.strife.panel.affiliation.none}）——这与原版 lang key 惯例一致，
     * {@code gui}/{@code msg} 两大类天然带二级分组。单段内容 ID（{@code realm.strife.qili}）是它的特例。
     */
    private static final Pattern LANG_KEY =
            Pattern.compile("^[a-z][a-z0-9_]*\\.strife\\.[a-z][a-z0-9_]*(\\.[a-z0-9_]+)*$");

    /**
     * 原版强制的键位分类 key：{@code key.categories.<命名空间>}。它由 Minecraft 自己按命名空间查找，形态不归本项目管，
     * 写成 {@code key.strife.categories.strife} 会让原版找不到分类名。
     */
    private static final Pattern VANILLA_CATEGORY_KEY = Pattern.compile("^[a-z][a-z0-9_]*\\.categories\\.strife$");

    /** 至少一个汉字（统一表意文字基本区）。 */
    private static final Pattern HAS_HAN = Pattern.compile("[\\u4e00-\\u9fff]");

    private NameConventions() {}

    static List<String> check(ValidatorMain.Options options) {
        List<String> problems = new ArrayList<>();
        checkProductIds(options.dataRoot(), problems);
        checkTableIds(options.tablesRoot(), problems);
        checkLangFiles(options.assetsRoot(), problems);
        return problems;
    }

    /** 产物侧：每个内容 JSON 的 {@code id} 字段。 */
    private static void checkProductIds(Path dataRoot, List<String> problems) {
        if (dataRoot == null) {
            return;
        }
        for (Path file : ValidatorMain.jsonFiles(dataRoot)) {
            JsonElement root = ValidatorMain.parse(file);
            if (!root.isJsonObject()) {
                continue;
            }
            JsonObject object = root.getAsJsonObject();
            if (!object.has("id") || !object.get("id").isJsonPrimitive()) {
                continue;
            }
            // 产物 id 是<b>内容 ID</b>，不是 lang key：它等于 lang key 去掉 <类别>.strife. 前缀后的那一段，
            // 所以不点分。lang key 的点分多级形态只由 checkLangFiles 认。
            describeShape(object.get("id").getAsString(), file + " (id)", problems);
        }
    }

    /** 源表侧：逐行第一列。 */
    private static void checkTableIds(Path tablesRoot, List<String> problems) {
        if (tablesRoot == null || !Files.isDirectory(tablesRoot)) {
            return;
        }
        for (Path table : ValidatorMain.csvFiles(tablesRoot)) {
            String fileName = table.getFileName().toString();
            boolean ledger = ValidatorMain.isLedgerTable(fileName);
            List<String> lines = ValidatorMain.readLines(table);
            for (int row = 1; row < lines.size(); row++) {
                String line = lines.get(row);
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                List<String> cells = ValidatorMain.splitRow(line);
                if (cells.isEmpty() || cells.get(0).isBlank()) {
                    continue; // 空行与缺 id 由 V-DUP 报，这里不重复
                }
                String where = table + ":" + (row + 1);
                if (ledger) {
                    if (hasNonAsciiLetter(cells.get(0))) {
                        problems.add(
                                where + ": ledger key '" + cells.get(0) + "' must stay ASCII — 它是记录号，"
                                        + "不是内容 ID；中文显示名写进 lang (docs/11 §2)");
                    }
                } else {
                    describeShape(cells.get(0), where, problems);
                }
            }
        }
    }

    /**
     * lang 侧：key 形状两侧都查（两侧一起写错 key 会一起通过，所以不能只查一边）；只有 {@code zh_cn} 查"含汉字"，
     * 因为 {@code en_us} 是占位语言，ADR-009 明确允许占位过 CI。
     */
    private static void checkLangFiles(Path assetsRoot, List<String> problems) {
        if (assetsRoot == null || !Files.isDirectory(assetsRoot)) {
            return;
        }
        for (String locale : List.of("zh_cn", "en_us")) {
            Path lang = assetsRoot.resolve("strife/lang/" + locale + ".json");
            if (!Files.isRegularFile(lang)) {
                continue;
            }
            for (Map.Entry<String, Object> entry : ValidatorMain.langEntries(lang).entrySet()) {
                String key = entry.getKey();
                if (!LANG_KEY.matcher(key).matches() && !VANILLA_CATEGORY_KEY.matcher(key).matches()) {
                    problems.add(
                            lang + ": lang key '" + key + "' 必须形如 <类别>.strife.<id>"
                                    + "（类别前缀需先在 JSON_SCHEMA.md 登记，docs/11 §2.1）");
                }
                String value = String.valueOf(entry.getValue());
                if ("zh_cn".equals(locale) && !value.isBlank() && !HAS_HAN.matcher(value).find()) {
                    problems.add(
                            lang + ": '" + key + "' = \"" + value + "\" 不含任何汉字 — 玩家可见文本必须是中文"
                                    + "（docs/11 §1：ID 用拼音，文本用中文）");
                }
            }
        }
    }

    private static void describeShape(String id, String where, List<String> problems) {
        if (id.isEmpty()) {
            return;
        }
        if (hasNonAsciiLetter(id)) {
            problems.add(
                    where + ": id '" + id + "' 含非 ASCII 字母 — Minecraft ResourceLocation 只允许"
                            + " [a-z0-9_.]，中文 ID 会在加载期直接崩。ID 用拼音、中文写进 lang (docs/11 §1)");
            return;
        }
        if (!ID_SHAPE.matcher(id).matches()) {
            problems.add(
                    where + ": id '" + id + "' 不符 [a-z][a-z0-9]*(_[a-z0-9]+)* — 全小写、下划线分词，"
                            + "禁连字符与驼峰 (docs/11 §2)");
        }
    }

    /** 任一非 ASCII 字母（汉字、全角字母、含重音拉丁字母等）。 */
    private static boolean hasNonAsciiLetter(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isLetter(value.charAt(i)) && value.charAt(i) > 0x7F) {
                return true;
            }
        }
        return false;
    }
}

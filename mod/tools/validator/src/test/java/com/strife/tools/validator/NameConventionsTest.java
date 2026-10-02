package com.strife.tools.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.strife.tools.validator.ValidatorMain.Options;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * V-NAME（docs/11 §2）用例。
 *
 * <p>本类的重点不是"合规产物能过"，而是<b>每条规则都有一张红脸</b>——一个永远返回空的门禁比没有门禁更危险，因为它看起来在工作。 所以每条 {@code
 * assertTrue(...contains(...))} 都在钉住"这个坏形态必须被抓到，且报错文本要说清怎么改"。
 */
class NameConventionsTest {

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    /** 写一份只含一个产物、id 为 {@code id} 的最小产物集。 */
    private static Path product(Path root, String id) throws IOException {
        write(root.resolve("data/strife/thing/x.json"), "{\"id\": \"" + id + "\"}");
        return root.resolve("data");
    }

    private static Path table(Path root, String name, String... ids) throws IOException {
        StringBuilder csv = new StringBuilder("id,note\n");
        for (String id : ids) {
            csv.append(id).append(",x\n");
        }
        write(root.resolve("tables").resolve(name), csv.toString());
        return root.resolve("tables");
    }

    private static Path langs(Path root, String zhBody, String enBody) throws IOException {
        write(root.resolve("assets/strife/lang/zh_cn.json"), zhBody);
        write(root.resolve("assets/strife/lang/en_us.json"), enBody);
        return root.resolve("assets");
    }

    // ── ID 形状：合法形态必须放过 ────────────────────────────────────────

    @Test
    void lowercaseUnderscoreIdPasses(@TempDir Path root) throws IOException {
        assertEquals(
                List.of(),
                NameConventions.check(
                        new Options(product(root, "block_ore_lingyu"), null, null, null)));
    }

    /**
     * 产物 {@code id} 字段是<b>内容 ID</b>，不点分——点分只属于 lang key（{@code dialog.strife.nd_...}）。 内容 ID 是 lang
     * key 去掉 {@code <类别>.strife.} 前缀后的那一段，所以它自己必然是单段下划线形态。
     */
    @Test
    void contentIdWithDotsIsRejected(@TempDir Path root) throws IOException {
        List<String> problems =
                NameConventions.check(
                        new Options(
                                product(root, "dialog.strife.nd_elder_gate_met"),
                                null,
                                null,
                                null));
        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.get(0).contains("全小写"), problems.toString());
    }

    // ── ID 形状：三种坏形态各一张红脸 ───────────────────────────────────

    @Test
    void camelCaseIdIsRejected(@TempDir Path root) throws IOException {
        Options options = new Options(product(root, "blockOreLingyu"), null, null, null);
        List<String> problems = NameConventions.check(options);
        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.get(0).contains("驼峰"), problems.toString());
    }

    @Test
    void hyphenatedIdIsRejected(@TempDir Path root) throws IOException {
        Options options = new Options(product(root, "ore-lingyu"), null, null, null);
        List<String> problems = NameConventions.check(options);
        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.get(0).contains("连字符"), problems.toString());
    }

    @Test
    void leadingUnderscoreIsRejected(@TempDir Path root) throws IOException {
        Options options = new Options(product(root, "_ore"), null, null, null);
        assertEquals(1, NameConventions.check(options).size());
    }

    @Test
    void upperCaseIdIsRejected(@TempDir Path root) throws IOException {
        Options options = new Options(product(root, "Ore_Lingyu"), null, null, null);
        assertEquals(1, NameConventions.check(options).size());
    }

    // ── ID 语言：中文 ID 必须被抓，并说清"会崩" ──────────────────────────

    @Test
    void chineseIdIsRejectedWithCrashReason(@TempDir Path root) throws IOException {
        Options options = new Options(product(root, "灵玉矿"), null, null, null);
        List<String> problems = NameConventions.check(options);
        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.get(0).contains("加载期"), problems.toString());
        assertTrue(problems.get(0).contains("lang"), problems.toString());
    }

    @Test
    void fullWidthIdIsRejected(@TempDir Path root) throws IOException {
        Options options = new Options(product(root, "ｏｒｅ_lingyu"), null, null, null);
        assertEquals(1, NameConventions.check(options).size());
    }

    // ── 源表侧：同样规则，且报错带表名与行号 ─────────────────────────────

    @Test
    void tableRowIdIsCheckedAndPointedAt(@TempDir Path root) throws IOException {
        Path tables = table(root, "spells.csv", "spell_Prologue_Huodan");
        List<String> problems = NameConventions.check(new Options(null, tables, null, null));
        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.get(0).contains("spells.csv:2"), problems.toString());
    }

    @Test
    void chineseTableIdIsRejected(@TempDir Path root) throws IOException {
        Path tables = table(root, "spells.csv", "火弹术");
        List<String> problems = NameConventions.check(new Options(null, tables, null, null));
        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.get(0).contains("加载期"), problems.toString());
    }

    @Test
    void blankRowIsLeftToDuplicateGate(@TempDir Path root) throws IOException {
        // 空行由 V-DUP 报"row has no id"；V-NAME 不重复报，避免同一问题两条红
        Path tables = table(root, "spells.csv", "");
        assertEquals(List.of(), NameConventions.check(new Options(null, tables, null, null)));
    }

    /** 台账表第一列是记录号：只查非 ASCII 字母，不套内容 ID 形状。 */
    @Test
    void ledgerTableReusesExistingNames(@TempDir Path root) throws IOException {
        Path tables = table(root, "known-placeholders.csv", "existing_flag_name");
        assertEquals(List.of(), NameConventions.check(new Options(null, tables, null, null)));
    }

    @Test
    void ledgerTableStillRejectsChinese(@TempDir Path root) throws IOException {
        Path tables = table(root, "known-placeholders.csv", "占位键");
        List<String> problems = NameConventions.check(new Options(null, tables, null, null));
        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.get(0).contains("记录号"), problems.toString());
    }

    // ── lang：key 形状与中文值 ──────────────────────────────────────────

    @Test
    void langKeyMustBeThreePart(@TempDir Path root) throws IOException {
        Path assets = langs(root, "{\"realm.qili\": \"练气\"}", "{\"realm.qili\": \"Qi\"}");
        List<String> problems = NameConventions.check(new Options(null, null, null, assets));
        assertTrue(problems.stream().anyMatch(p -> p.contains("lang key")), problems.toString());
    }

    @Test
    void langKeyAllowsDottedId(@TempDir Path root) throws IOException {
        Path assets =
                langs(
                        root,
                        "{\"gui.strife.panel.affiliation.none\": \"散修\"}",
                        "{\"gui.strife.panel.affiliation.none\": \"None\"}");
        assertEquals(List.of(), NameConventions.check(new Options(null, null, null, assets)));
    }

    /** 原版键位分类 key 形态特殊，必须放过。 */
    @Test
    void vanillaCategoryKeyPasses(@TempDir Path root) throws IOException {
        Path assets =
                langs(
                        root,
                        "{\"key.categories.strife\": \"玄黄劫争\"}",
                        "{\"key.categories.strife\": \"Primordial Strife\"}");
        assertEquals(List.of(), NameConventions.check(new Options(null, null, null, assets)));
    }

    @Test
    void zhValueWithoutChineseIsRejected(@TempDir Path root) throws IOException {
        Path assets =
                langs(
                        root,
                        "{\"realm.strife.qili\": \"Qi Condensation\"}",
                        "{\"realm.strife.qili\": \"Qi\"}");
        List<String> problems = NameConventions.check(new Options(null, null, null, assets));
        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.get(0).contains("不含任何汉字"), problems.toString());
    }

    /** en_us 是占位语言（ADR-009 允许占位过 CI），不能因为没有汉字就红。 */
    @Test
    void enValueWithoutChineseIsAccepted(@TempDir Path root) throws IOException {
        Path assets =
                langs(
                        root,
                        "{\"realm.strife.qili\": \"练气\"}",
                        "{\"realm.strife.qili\": \"Qi Condensation\"}");
        assertEquals(List.of(), NameConventions.check(new Options(null, null, null, assets)));
    }

    @Test
    void blankZhValueIsLeftToTextGate(@TempDir Path root) throws IOException {
        // 空串由 V-TEXT 报；V-NAME 的职责是"非空却没中文"
        Path assets =
                langs(root, "{\"realm.strife.qili\": \"\"}", "{\"realm.strife.qili\": \"Qi\"}");
        assertEquals(List.of(), NameConventions.check(new Options(null, null, null, assets)));
    }

    // ── 缺输入时的行为：不崩、不误报 ────────────────────────────────────

    @Test
    void missingRootsAreNoOps() {
        assertEquals(List.of(), NameConventions.check(new Options(null, null, null, null)));
    }
}

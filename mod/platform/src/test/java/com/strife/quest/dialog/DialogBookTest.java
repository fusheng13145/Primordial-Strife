package com.strife.quest.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.strife.quest.dialog.DialogBook.EffectType;
import com.strife.quest.dialog.DialogBook.OptionSpec;
import com.strife.quest.dialog.DialogBook.TreeSpec;
import com.strife.quest.dialog.DialogRunner.ChooseOutcome;
import com.strife.quest.dialog.DialogRunner.EffectSink;
import com.strife.quest.dsl.ConditionExpression;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 对话树引擎核的用例矩阵（content/JSON_SCHEMA.md §4.7 + docs/03 §10"纯解释无副作用、有求值上限"）。
 *
 * <p>夹具刻意手写（与 {@code ProloguePlaythroughTest} 的真实产物路线互补）：解释器契约的边界（条件旁路、 深度爆炸、未知字段）需要构造产物里不出现的病态形状。
 */
class DialogBookTest {

    /** 录制型 sink：effects 出料全部记账。 */
    private static final class RecordingSink implements EffectSink {
        final List<String> flags = new ArrayList<>();
        final List<String> reputation = new ArrayList<>();
        final List<String> items = new ArrayList<>();
        final List<String> quests = new ArrayList<>();
        final List<String> completedNodes = new ArrayList<>();
        final List<String> sounds = new ArrayList<>();
        final List<String> teleports = new ArrayList<>();

        @Override
        public void setFlag(String key) {
            flags.add(key);
        }

        @Override
        public void addReputation(String factionId, int delta) {
            reputation.add(factionId + ":" + delta);
        }

        @Override
        public void giveItem(String itemId, long count) {
            items.add(itemId + "x" + count);
        }

        @Override
        public void startQuest(String questId) {
            quests.add(questId);
        }

        @Override
        public void completeNode(String key) {
            completedNodes.add(key);
        }

        @Override
        public void playSound(String soundId, float volume, float pitch) {
            sounds.add(soundId + ":" + volume + ":" + pitch);
        }

        @Override
        public void teleport(double x, double y, double z, String dimension) {
            teleports.add(x + "," + y + "," + z + (dimension == null ? "" : "@" + dimension));
        }
    }

    private static DialogBook book(String json) {
        JsonObject product = JsonParser.parseString(json).getAsJsonObject();
        return DialogBook.parse(product);
    }

    private static final String TWO_TREES =
            """
            {
              "id": "node_elder_open",
              "chapter": "prologue",
              "trees": [
                {"id": "dlg_prologue_elder", "npc": "npc_qingshi_zhizhi", "root": "node_elder_open",
                 "max_depth_levels": 8,
                 "effects": [{"type": "start_quest", "args": "quest_prologue_herb_pick_01"}],
                 "nodes": [
                   {"id": "node_elder_open", "speaker": "zhizhi", "text_key": "dlg.elder.open",
                    "options": [
                      {"text_key": "dlg.elder.ask_herb", "next": "node_elder_herb"},
                      {"text_key": "dlg.elder.leave", "conditions": "questDone(quest_prologue_herb_pick_01)", "next": "node_elder_bye"}
                    ]},
                   {"id": "node_elder_herb", "speaker": "zhizhi", "text_key": "dlg.elder.herb",
                    "options": [
                      {"text_key": "dlg.elder.accept",
                       "effects": [{"type": "set_flag", "args": "fac_qingshi:prologue:accepted_herb"},
                                   {"type": "complete_node"}]}
                    ]},
                   {"id": "node_elder_bye", "speaker": "zhizhi", "text_key": "dlg.elder.bye"}
                 ]},
                {"id": "dlg_prologue_herbalist", "npc": "npc_yaonong_su", "root": "node_herb_open",
                 "max_depth_levels": 4,
                 "nodes": [
                   {"id": "node_herb_open", "speaker": "su", "text_key": "dlg.herb.open",
                    "conditions": "flag(fac_qingshi:prologue:accepted_herb)",
                    "next": "node_herb_trade"},
                   {"id": "node_herb_trade", "speaker": "su", "text_key": "dlg.herb.trade",
                    "options": [
                      {"text_key": "dlg.herb.give",
                       "effects": [{"type": "reputation", "args": "fac_qingshi:5"},
                                   {"type": "give_item", "args": "item_lingshi:2"},
                                   {"type": "play_sound", "args": "strife:ui.coin:1.0:1.2"},
                                   {"type": "teleport", "args": "100, 64, -200, minecraft:overworld"}]}
                    ]}
                 ]}
              ]
            }
            """;

    private static ConditionExpression.Context emptyContext() {
        return new ConditionExpression.Context() {
            @Override
            public int realm() {
                return 0;
            }

            @Override
            public boolean flag(String key) {
                return false;
            }

            @Override
            public int itemCount(String itemId) {
                return 0;
            }

            @Override
            public int reputation(String factionId) {
                return 0;
            }

            @Override
            public boolean questDone(String questId) {
                return false;
            }

            @Override
            public boolean affinity(String element) {
                return false;
            }

            @Override
            public int realmOrdinal(String realmId) {
                return 0;
            }

            @Override
            public int subStage() {
                return 1;
            }

            @Override
            public int luck() {
                return 0;
            }
        };
    }

    @Test
    @DisplayName("解析：按 npc 索引、入口树 id 必须存在")
    void parseIndexesByNpcAndChecksEntry() {
        DialogBook book = book(TWO_TREES);

        assertEquals("prologue", book.chapter());
        assertEquals("dlg_prologue_elder", book.entryTreeId());
        assertEquals("dlg_prologue_elder", book.treeByNpc("npc_qingshi_zhizhi").id());
        assertEquals("dlg_prologue_herbalist", book.treeByNpc("npc_yaonong_su").id());
        assertNull(book.treeByNpc("npc_unknown"));
    }

    @Test
    @DisplayName("严格未知字段：多一个键就炸（§1.2 规则 2）")
    void unknownFieldFailsFast() {
        String bad =
                """
                {"id": "r", "chapter": "prologue", "trees": [
                  {"id": "t", "npc": "npc_x", "root": "r1", "max_depth_levels": 4, "nodes": [
                    {"id": "r1", "text_key": "k", "typo_field": 1}]}]}
                """;
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> book(bad));
        assertTrue(e.getMessage().contains("typo_field"), e.getMessage());
    }

    @Test
    @DisplayName("悬空 next 拒绝（V-REF 的运行时兜底）")
    void danglingNextRejected() {
        String bad =
                """
                {"id": "r", "chapter": "prologue", "trees": [
                  {"id": "t", "npc": "npc_x", "root": "r1", "max_depth_levels": 4, "nodes": [
                    {"id": "r1", "text_key": "k", "next": "nowhere"}]}]}
                """;
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> book(bad));
        assertTrue(e.getMessage().contains("dangling next 'nowhere'"), e.getMessage());
    }

    @Test
    @DisplayName("条件链旁路：不满足 conditions 的入口节点沿 next 前进到可停点")
    void conditionChainBypassesUnmetNodes() {
        DialogBook book = book(TWO_TREES);
        TreeSpec tree = book.treeByNpc("npc_yaonong_su");

        // flag 未设置：入口节点（带 conditions）旁路 → 自动前进到 node_herb_trade 决策节点
        DialogRunner.Session unmet = DialogRunner.open(book, "npc_yaonong_su", emptyContext());
        assertEquals("node_herb_trade", unmet.nodeId());

        // 条件满足：入口节点直接停下——但它是流转节点（无 options），自动前进到 node_herb_trade
        ConditionExpression.Context met =
                new ConditionExpression.Context() {
                    @Override
                    public int realm() {
                        return 0;
                    }

                    @Override
                    public boolean flag(String key) {
                        return "fac_qingshi:prologue:accepted_herb".equals(key);
                    }

                    @Override
                    public int itemCount(String itemId) {
                        return 0;
                    }

                    @Override
                    public int reputation(String factionId) {
                        return 0;
                    }

                    @Override
                    public boolean questDone(String questId) {
                        return false;
                    }

                    @Override
                    public boolean affinity(String element) {
                        return false;
                    }

                    @Override
                    public int realmOrdinal(String realmId) {
                        return 0;
                    }

                    @Override
                    public int subStage() {
                        return 1;
                    }

                    @Override
                    public int luck() {
                        return 0;
                    }
                };
        DialogRunner.Session session = DialogRunner.open(book, "npc_yaonong_su", met);
        assertEquals(tree.root(), "node_herb_open");
        assertEquals("node_herb_trade", session.nodeId());
    }

    @Test
    @DisplayName("决策节点：选项按 conditions 过滤，选择执行 effects 并跳转")
    void optionsFilterAndEffectsLand() {
        DialogBook book = book(TWO_TREES);
        RecordingSink sink = new RecordingSink();

        DialogRunner.Session session =
                DialogRunner.open(book, "npc_qingshi_zhizhi", emptyContext());
        List<OptionSpec> visible = DialogRunner.visibleOptions(session, emptyContext());
        // 第二个选项 questDone(...) 未通过——不可见（fail-closed 过滤）
        assertEquals(1, visible.size());
        assertEquals("dlg.elder.ask_herb", visible.getFirst().textKey());

        ChooseOutcome outcome = DialogRunner.choose(session, 0, emptyContext(), sink);
        assertFalse(outcome.ended());
        assertEquals("node_elder_herb", outcome.nextNodeId());
        assertTrue(sink.flags.isEmpty(), "跳转本身不执行 effects");

        // 接受采药：set_flag + complete_node（默认键 dlg:<tree>:<node>）
        ChooseOutcome end = DialogRunner.choose(session, 0, emptyContext(), sink);
        assertTrue(end.ended(), "next 为空 = 对话结束");
        assertEquals(List.of("fac_qingshi:prologue:accepted_herb"), sink.flags);
        assertEquals(
                List.of("dlg:dlg_prologue_elder:node_elder_herb"),
                sink.completedNodes,
                "complete_node 无 args 时默认记当前节点");
        // 对话结束 → 树级收尾 effects（start_quest）
        assertEquals(List.of("quest_prologue_herb_pick_01"), sink.quests);
    }

    @Test
    @DisplayName("选项越界拒绝：客户端回显与服务端求值不一致时 fail-fast")
    void optionIndexOutOfRangeRejected() {
        DialogBook book = book(TWO_TREES);
        DialogRunner.Session session =
                DialogRunner.open(book, "npc_qingshi_zhizhi", emptyContext());

        assertThrows(
                IllegalStateException.class,
                () -> DialogRunner.choose(session, 5, emptyContext(), new RecordingSink()));
        assertThrows(
                IllegalStateException.class,
                () -> DialogRunner.choose(session, -1, emptyContext(), new RecordingSink()));
    }

    @Test
    @DisplayName("全部七种 effects 的 args 语法逐条落地")
    void allSevenEffectTypesParseAndLand() {
        String all =
                """
                {"id": "r", "chapter": "prologue", "trees": [
                  {"id": "t", "npc": "npc_x", "root": "r1", "max_depth_levels": 4,
                   "nodes": [
                     {"id": "r1", "text_key": "k",
                      "options": [{"text_key": "o",
                        "effects": [
                          {"type": "set_flag", "args": "fac_qingshi:prologue:met"},
                          {"type": "reputation", "args": "fac_qingshi:-5"},
                          {"type": "give_item", "args": "item_lingshi:3"},
                          {"type": "start_quest", "args": "quest_ch1_pingcang_survey_01"},
                          {"type": "complete_node"},
                          {"type": "play_sound", "args": "strife:ui.coin"},
                          {"type": "teleport", "args": "1.5, 64, -3.5"}
                        ]}]}]}]}
                """;
        DialogBook book = book(all);
        RecordingSink sink = new RecordingSink();
        DialogRunner.Session session = DialogRunner.open(book, "npc_x", emptyContext());

        ChooseOutcome outcome = DialogRunner.choose(session, 0, emptyContext(), sink);

        assertTrue(outcome.ended());
        assertEquals(List.of("fac_qingshi:prologue:met"), sink.flags);
        assertEquals(List.of("fac_qingshi:-5"), sink.reputation);
        assertEquals(List.of("item_lingshix3"), sink.items);
        assertEquals(List.of("quest_ch1_pingcang_survey_01"), sink.quests);
        assertEquals(List.of("dlg:t:r1"), sink.completedNodes);
        assertEquals(List.of("strife:ui.coin:1.0:1.0"), sink.sounds, "缺省 volume/pitch = 1.0");
        assertEquals(List.of("1.5,64.0,-3.5"), sink.teleports, "无维度段 = 当前维度");
    }

    @Test
    @DisplayName("坏 args 语法当场炸（内容 bug 必须可自助定位）")
    void badEffectArgsRejected() {
        String bad =
                """
                {"id": "r", "chapter": "prologue", "trees": [
                  {"id": "t", "npc": "npc_x", "root": "r1", "max_depth_levels": 4,
                   "nodes": [
                     {"id": "r1", "text_key": "k",
                      "options": [{"text_key": "o",
                        "effects": [{"type": "reputation", "args": "fac_qingshi"}]}]}]}]}
                """;
        DialogBook book = book(bad);
        DialogRunner.Session session = DialogRunner.open(book, "npc_x", emptyContext());

        IllegalArgumentException e =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> DialogRunner.choose(session, 0, emptyContext(), new RecordingSink()));
        assertTrue(e.getMessage().contains("expected <fac_id>:<delta>"), e.getMessage());
    }

    @Test
    @DisplayName("深度防护：写成环的树在 max_depth_levels 处炸掉，不死循环")
    void cyclicTreeExplodesAtDepthLimit() {
        String cyclic =
                """
                {"id": "r", "chapter": "prologue", "trees": [
                  {"id": "t", "npc": "npc_x", "root": "r1", "max_depth_levels": 4,
                   "nodes": [
                     {"id": "r1", "text_key": "k", "next": "r2"},
                     {"id": "r2", "text_key": "k", "next": "r1"}]}]}
                """;
        DialogBook book = book(cyclic);

        IllegalStateException e =
                assertThrows(
                        IllegalStateException.class,
                        () -> DialogRunner.open(book, "npc_x", emptyContext()));
        assertTrue(e.getMessage().contains("exceeded max_depth_levels=4"), e.getMessage());
    }

    @Test
    @DisplayName("同一 NPC 两棵树 = 内容错误，解析期拒绝")
    void duplicateNpcRejected() {
        String dup =
                """
                {"id": "t1", "chapter": "prologue", "trees": [
                  {"id": "t1", "npc": "npc_x", "root": "r", "max_depth_levels": 4, "nodes": [
                    {"id": "r", "text_key": "k"}]},
                  {"id": "t2", "npc": "npc_x", "root": "r", "max_depth_levels": 4, "nodes": [
                    {"id": "r", "text_key": "k"}]}]}
                """;
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> book(dup));
        assertTrue(e.getMessage().contains("two dialog trees"), e.getMessage());
    }

    @Test
    @DisplayName("EffectType 枚举覆盖 §4.7 全集")
    void effectTypeCoversSchema() {
        assertEquals(7, EffectType.values().length);
        assertEquals(EffectType.SET_FLAG, DialogBook.EffectType.fromId("set_flag"));
        assertEquals(EffectType.COMPLETE_NODE, DialogBook.EffectType.fromId("complete_node"));
        assertEquals(EffectType.TELEPORT, DialogBook.EffectType.fromId("teleport"));
        assertFalse(EffectType.fromId("explode") != null);
    }
}

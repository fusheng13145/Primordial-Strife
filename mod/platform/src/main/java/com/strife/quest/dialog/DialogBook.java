package com.strife.quest.dialog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.strife.quest.dsl.ConditionDsl;
import com.strife.quest.dsl.ConditionExpression;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一章对话树的引擎核（docs/03 §1：对话树 DSL 解释器属 quest；content/JSON_SCHEMA.md §4.7）。
 *
 * <p>输入是 DataGen 产物 JSON（{@code data/strife/dialog_trees/<章>.json}，一章一文件）。与 {@code QuestBook}
 * 同一防御口径：信任 DataGen + Validator 的构建期校验，但当场拒绝坏结构——热更 zip 会绕过门禁直接进游戏。
 *
 * <p>严格未知字段（§1.2 规则 2）：tree/node/option/effect 四层对象的键集合必须精确匹配契约，多一个键就炸—— 拼错字段名的表现是"内容凭空消失"，只有
 * fail-fast 可自助修复。
 *
 * <p>职责边界：结构持有 + 按条件求值的遍历查询；<b>不含</b> effects 的落地（{@code DialogRunner} 经 {@code EffectSink}
 * 出料）、持久化、网络与 UI。
 */
public final class DialogBook {

    /** §4.7 effects.type 枚举（`[拟]` 语义补注已登记真相源：args 语法见 {@code DialogRunner#parseEffect}）。 */
    public enum EffectType {
        SET_FLAG,
        REPUTATION,
        GIVE_ITEM,
        START_QUEST,
        COMPLETE_NODE,
        PLAY_SOUND,
        TELEPORT;

        static EffectType fromId(String id) {
            for (EffectType type : values()) {
                if (type.name().toLowerCase(java.util.Locale.ROOT).equals(id)) {
                    return type;
                }
            }
            return null;
        }
    }

    /** 单条效果：{@code {type, args}}，args 保持原文（逐类型语法在 {@code DialogRunner} 解析）。 */
    public record EffectSpec(EffectType type, String args) {}

    /** 选项：{@code {text_key, conditions, next, effects}}；next 为空 = 对话结束。 */
    public record OptionSpec(
            String textKey,
            ConditionExpression conditions,
            String next,
            List<EffectSpec> effects) {}

    /**
     * 节点：{@code {id, speaker, text_key, conditions, next, options}}。
     *
     * <p>节点两态：带 {@code options} 的决策节点（玩家选择，停下）；带 {@code next} 的流转节点（条件链与顺序流， 运行器自动前进）。两者都缺 =
     * 终端节点（对话自然结束）。
     */
    public record NodeSpec(
            String id,
            String speaker,
            String textKey,
            ConditionExpression conditions,
            String next,
            List<OptionSpec> options) {}

    /** 一棵树：属于一个 NPC；{@code entryEffects} 在对话<b>结束</b>时执行一次（树级收尾效果）。 */
    public record TreeSpec(
            String id,
            String npc,
            String root,
            Map<String, NodeSpec> nodesById,
            List<EffectSpec> entryEffects,
            int maxDepthLevels) {}

    private final String chapter;
    private final String entryTreeId;
    private final Map<String, TreeSpec> treesById = new LinkedHashMap<>();
    private final Map<String, TreeSpec> treesByNpc = new LinkedHashMap<>();

    private DialogBook(String chapter, String entryTreeId) {
        this.chapter = chapter;
        this.entryTreeId = entryTreeId;
    }

    public String chapter() {
        return chapter;
    }

    public String entryTreeId() {
        return entryTreeId;
    }

    public TreeSpec treeById(String treeId) {
        return treesById.get(treeId);
    }

    /** 某 NPC 的对话树（右键交互入口）；一个 NPC 一章一棵树，重复即内容错误。 */
    public TreeSpec treeByNpc(String npcId) {
        return treesByNpc.get(npcId);
    }

    /** 从 DataGen 产物解析；坏结构当场炸。 */
    public static DialogBook parse(JsonObject product) {
        String chapter = string(product, "chapter", true);
        String entryTreeId = string(product, "id", true);
        DialogBook book = new DialogBook(chapter, entryTreeId);
        JsonArray treeArray = array(product, "trees");
        boolean sawEntry = false;
        for (JsonElement element : treeArray) {
            TreeSpec tree = parseTree(object(element), chapter);
            if (book.treesById.containsKey(tree.id())) {
                throw new IllegalStateException(
                        "dialog tree '" + tree.id() + "' declared twice in chapter " + chapter);
            }
            if (book.treesByNpc.containsKey(tree.npc())) {
                throw new IllegalStateException(
                        "npc '"
                                + tree.npc()
                                + "' has two dialog trees in chapter "
                                + chapter
                                + " ('"
                                + book.treesByNpc.get(tree.npc()).id()
                                + "' and '"
                                + tree.id()
                                + "')");
            }
            if (tree.id().equals(entryTreeId)) {
                sawEntry = true;
            }
            book.treesById.put(tree.id(), tree);
            book.treesByNpc.put(tree.npc(), tree);
        }
        if (!sawEntry) {
            throw new IllegalStateException(
                    "chapter " + chapter + " has no dialog tree with id '" + entryTreeId + "'");
        }
        // 悬空引用检查（V-REF 也查，热更 zip 场景下这里再拦一道）：root / next / option.next 必须指向本树节点
        for (TreeSpec tree : book.treesById.values()) {
            if (!tree.nodesById().containsKey(tree.root())) {
                throw new IllegalStateException(
                        "dialog tree '" + tree.id() + "' root '" + tree.root() + "' not in nodes");
            }
            for (NodeSpec node : tree.nodesById().values()) {
                if (node.next() != null && !tree.nodesById().containsKey(node.next())) {
                    throw new IllegalStateException(
                            "node '"
                                    + node.id()
                                    + "' of tree '"
                                    + tree.id()
                                    + "' has dangling next '"
                                    + node.next()
                                    + "'");
                }
                for (OptionSpec option :
                        node.options() == null ? List.<OptionSpec>of() : node.options()) {
                    if (option.next() != null && !tree.nodesById().containsKey(option.next())) {
                        throw new IllegalStateException(
                                "option '"
                                        + option.textKey()
                                        + "' of node '"
                                        + node.id()
                                        + "' in tree '"
                                        + tree.id()
                                        + "' has dangling next '"
                                        + option.next()
                                        + "'");
                    }
                }
            }
        }
        return book;
    }

    private static final java.util.Set<String> TREE_KEYS =
            java.util.Set.of("id", "npc", "root", "nodes", "effects", "max_depth_levels");
    private static final java.util.Set<String> NODE_KEYS =
            java.util.Set.of("id", "speaker", "text_key", "conditions", "next", "options");
    private static final java.util.Set<String> OPTION_KEYS =
            java.util.Set.of("text_key", "conditions", "next", "effects");
    private static final java.util.Set<String> EFFECT_KEYS = java.util.Set.of("type", "args");

    private static TreeSpec parseTree(JsonObject json, String chapter) {
        rejectUnknownKeys(json, TREE_KEYS, "tree");
        String id = string(json, "id", true);
        String npc = string(json, "npc", true);
        String root = string(json, "root", true);
        Map<String, NodeSpec> nodesById = new LinkedHashMap<>();
        for (JsonElement element : array(json, "nodes")) {
            NodeSpec node = parseNode(object(element), id);
            if (nodesById.containsKey(node.id())) {
                throw new IllegalStateException(
                        "node '" + node.id() + "' declared twice in dialog tree '" + id + "'");
            }
            nodesById.put(node.id(), node);
        }
        if (nodesById.isEmpty()) {
            throw new IllegalStateException("dialog tree '" + id + "' has no nodes");
        }
        List<EffectSpec> entryEffects = new ArrayList<>();
        if (json.has("effects") && json.get("effects").isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray("effects")) {
                entryEffects.add(parseEffect(object(element), id));
            }
        }
        int maxDepth = json.get("max_depth_levels").getAsInt();
        if (maxDepth < 1) {
            throw new IllegalStateException(
                    "dialog tree '"
                            + id
                            + "' has max_depth_levels="
                            + maxDepth
                            + "（§4.7：解释器求值深度上限，防表写错死循环）");
        }
        return new TreeSpec(id, npc, root, nodesById, List.copyOf(entryEffects), maxDepth);
    }

    private static NodeSpec parseNode(JsonObject json, String treeId) {
        rejectUnknownKeys(json, NODE_KEYS, "node of tree " + treeId);
        String id = string(json, "id", true);
        String speaker = string(json, "speaker", false);
        String textKey = string(json, "text_key", true);
        ConditionExpression conditions = parseConditions(json, id, treeId);
        String next = string(json, "next", false);
        List<OptionSpec> options = null;
        if (json.has("options") && json.get("options").isJsonArray()) {
            options = new ArrayList<>();
            for (JsonElement element : json.getAsJsonArray("options")) {
                options.add(parseOption(object(element), id, treeId));
            }
            if (options.isEmpty()) {
                throw new IllegalStateException(
                        "node '" + id + "' of tree '" + treeId + "' has an empty options array");
            }
        }
        if (options != null && next != null) {
            throw new IllegalStateException(
                    "node '"
                            + id
                            + "' of tree '"
                            + treeId
                            + "' has both next and options（§4.7：决策节点不流转，流转节点不决策）");
        }
        return new NodeSpec(
                id,
                speaker,
                textKey,
                conditions,
                next,
                options == null ? null : List.copyOf(options));
    }

    private static OptionSpec parseOption(JsonObject json, String nodeId, String treeId) {
        rejectUnknownKeys(json, OPTION_KEYS, "option of node " + nodeId + " in tree " + treeId);
        ConditionExpression conditions = parseConditions(json, nodeId, treeId);
        List<EffectSpec> effects = new ArrayList<>();
        if (json.has("effects") && json.get("effects").isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray("effects")) {
                effects.add(parseEffect(object(element), treeId));
            }
        }
        return new OptionSpec(
                string(json, "text_key", true),
                conditions,
                string(json, "next", false),
                List.copyOf(effects));
    }

    private static EffectSpec parseEffect(JsonObject json, String treeId) {
        rejectUnknownKeys(json, EFFECT_KEYS, "effect of tree " + treeId);
        String typeId = string(json, "type", true);
        EffectType type = EffectType.fromId(typeId);
        if (type == null) {
            throw new IllegalStateException(
                    "effect type '" + typeId + "' is not in JSON_SCHEMA §4.7");
        }
        return new EffectSpec(type, string(json, "args", false));
    }

    /** 节点/选项的 conditions 在解析期就 parse（坏 DSL 进不了运行时，与 QuestBook 同口径）。 */
    static ConditionExpression parseConditions(JsonObject json, String ownerId, String treeId) {
        String raw = string(json, "conditions", false);
        if (raw == null) {
            return null;
        }
        try {
            return ConditionDsl.parse(raw);
        } catch (ConditionDsl.SyntaxException e) {
            throw new IllegalStateException(
                    "conditions of '"
                            + ownerId
                            + "' in dialog tree '"
                            + treeId
                            + "' is illegal: "
                            + e.getMessage(),
                    e);
        }
    }

    private static void rejectUnknownKeys(
            JsonObject json, java.util.Set<String> allowed, String where) {
        for (String key : json.keySet()) {
            if (!allowed.contains(key)) {
                throw new IllegalStateException(
                        "unknown field '" + key + "' in " + where + "（§1.2 规则 2：严格未知字段 fail-fast）");
            }
        }
    }

    private static JsonArray array(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonArray()) {
            throw new IllegalStateException(
                    "field '" + key + "' must be an array (JSON_SCHEMA §4.7)");
        }
        return element.getAsJsonArray();
    }

    private static JsonObject object(JsonElement element) {
        if (!element.isJsonObject()) {
            throw new IllegalStateException("expected object but got " + element);
        }
        return element.getAsJsonObject();
    }

    private static String string(JsonObject json, String key, boolean required) {
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull()) {
            if (required) {
                throw new IllegalStateException("field '" + key + "' is 必填 (JSON_SCHEMA §4.7)");
            }
            return null;
        }
        return element.getAsString();
    }
}

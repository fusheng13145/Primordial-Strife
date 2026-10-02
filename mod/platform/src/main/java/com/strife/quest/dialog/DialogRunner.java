package com.strife.quest.dialog;

import com.strife.quest.dialog.DialogBook.EffectSpec;
import com.strife.quest.dialog.DialogBook.NodeSpec;
import com.strife.quest.dialog.DialogBook.OptionSpec;
import com.strife.quest.dialog.DialogBook.TreeSpec;
import com.strife.quest.dsl.ConditionDsl;
import com.strife.quest.dsl.ConditionExpression;
import java.util.ArrayList;
import java.util.List;

/**
 * 对话树解释器（docs/03 §10"对话 DSL：纯解释无副作用、有条件求值上限"）。
 *
 * <p>无副作用：本类不落任何状态——条件求值走 {@link ConditionExpression.Context}（与任务条件同一解释器）， effects 经 {@link
 * EffectSink} 出料给装配层落地。会话状态（当前树/当前节点/已跳深度）全部在 {@link Session}
 * 里，装配层把它挂在自己的会话表上（服务端权威：客户端只报"我选了第几个选项"）。
 *
 * <p>遍历模型（`[拟]` 语义，已登记 JSON_SCHEMA §4.7）：
 *
 * <ol>
 *   <li>进入树从 {@code root} 开始；节点带 {@code conditions} 且不满足 → 沿 {@code next} 旁路（线性条件链）；
 *   <li>决策节点（有 options）停下等玩家选择；无选项但有 next 的节点在进入时自动前进；两者皆无 = 终端；
 *   <li>选项执行自身 effects 后跳 {@code next}；next 为空 = 对话结束，随后执行树级 {@code effects} 一次；
 *   <li>跳转累计深度不得超过 {@code max_depth_levels}（防表写错成环）；回环同样被深度拦住——表写错是内容 bug， 不是运行时特征，必须炸出来而不是转圈。
 * </ol>
 */
public final class DialogRunner {

    /** effects 的落地缝（装配层实现；纯逻辑核不碰 Minecraft）。 */
    public interface EffectSink {

        void setFlag(String key);

        void addReputation(String factionId, int delta);

        void giveItem(String itemId, long count);

        void startQuest(String questId);

        /** {@code complete_node} 的落地：节点完成记忆（装配层写 H3 flag 命名空间）。 */
        void completeNode(String key);

        void playSound(String soundId, float volume, float pitch);

        /** {@code dimension} 为空 = 当前维度；坐标语义由装配层解释（绝对坐标）。 */
        void teleport(double x, double y, double z, String dimension);
    }

    /** 一次对话会话的状态（服务端权威，客户端只回显）。 */
    public static final class Session {
        private final DialogBook book;
        private TreeSpec tree;
        private String nodeId;
        private int depth;

        Session(DialogBook book, TreeSpec tree, String nodeId) {
            this.book = book;
            this.tree = tree;
            this.nodeId = nodeId;
        }

        public TreeSpec tree() {
            return tree;
        }

        public String nodeId() {
            return nodeId;
        }

        NodeSpec node() {
            NodeSpec node = tree.nodesById().get(nodeId);
            if (node == null) {
                throw new IllegalStateException(
                        "session points at unknown node '"
                                + nodeId
                                + "' in tree '"
                                + tree.id()
                                + "'");
            }
            return node;
        }
    }

    private DialogRunner() {}

    /** 打开某 NPC 的对话树：从 root 沿条件链走到第一个停下点。NPC 没有对话树返回 {@code null}（调用方 决定是提示"他没什么要说的"还是回落到任务交互）。 */
    public static Session open(DialogBook book, String npcId, ConditionExpression.Context context) {
        TreeSpec tree = book.treeByNpc(npcId);
        if (tree == null) {
            return null;
        }
        Session session = new Session(book, tree, tree.root());
        advanceToStop(session, context);
        return session;
    }

    /** 当前节点的可见选项（conditions 通过的；无 conditions = 恒可见）。终端节点返回空列表。 */
    public static List<OptionSpec> visibleOptions(
            Session session, ConditionExpression.Context context) {
        NodeSpec node = session.node();
        List<OptionSpec> visible = new ArrayList<>();
        if (node.options() == null) {
            return visible;
        }
        for (OptionSpec option : node.options()) {
            if (option.conditions() == null
                    || ConditionDsl.satisfies(option.conditions(), context)) {
                visible.add(option);
            }
        }
        return visible;
    }

    /** 选择结果：{@code ended=true} 表示对话已结束（树级收尾效果已执行）。 */
    public record ChooseOutcome(boolean ended, String nextNodeId) {}

    /**
     * 玩家选择：{@code optionIndex} 是 {@link #visibleOptions} 的下标（客户端只回显可见列表的序号，
     * 条件过滤始终在服务端重放——客户端不知道也不需要知道被过滤的选项）。
     */
    public static ChooseOutcome choose(
            Session session,
            int optionIndex,
            ConditionExpression.Context context,
            EffectSink sink) {
        List<OptionSpec> visible = visibleOptions(session, context);
        if (optionIndex < 0 || optionIndex >= visible.size()) {
            throw new IllegalStateException(
                    "option index "
                            + optionIndex
                            + " out of range at node '"
                            + session.nodeId
                            + "' of tree '"
                            + session.tree.id()
                            + "（客户端回显与服务端求值不一致）");
        }
        OptionSpec option = visible.get(optionIndex);
        runEffects(option.effects(), session, context, sink);
        if (option.next() == null) {
            return end(session, context, sink);
        }
        session.nodeId = option.next();
        session.depth++;
        advanceToStop(session, context);
        return new ChooseOutcome(false, session.nodeId);
    }

    /** 一步执行完所有 effects（顺序语义；单条解析失败炸出具体 effect，不留半执行状态——见 parseEffect）。 */
    private static void runEffects(
            List<EffectSpec> effects,
            Session session,
            ConditionExpression.Context context,
            EffectSink sink) {
        for (EffectSpec effect : effects) {
            applyEffect(effect, session, context, sink);
        }
    }

    private static void applyEffect(
            EffectSpec effect,
            Session session,
            ConditionExpression.Context context,
            EffectSink sink) {
        String args = effect.args();
        switch (effect.type()) {
            case SET_FLAG -> sink.setFlag(requireArgs(effect, args));
            case REPUTATION -> {
                // args=<fac_id>:<delta>
                String[] parts = requireArgs(effect, args).split(":", 2);
                if (parts.length != 2) {
                    throw badArgs(effect, "expected <fac_id>:<delta>");
                }
                sink.addReputation(parts[0], Integer.parseInt(parts[1].trim()));
            }
            case GIVE_ITEM -> {
                // args=<item_id>:<count>
                String[] parts = requireArgs(effect, args).split(":", 2);
                if (parts.length != 2) {
                    throw badArgs(effect, "expected <item_id>:<count>");
                }
                sink.giveItem(parts[0], Long.parseLong(parts[1].trim()));
            }
            case START_QUEST -> sink.startQuest(requireArgs(effect, args));
            case COMPLETE_NODE -> {
                // args 可空：默认记当前节点（dlg:<tree>:<node> 命名空间，conditions 可用 flag() 引用）
                String key =
                        args == null || args.isBlank()
                                ? "dlg:" + session.tree.id() + ":" + session.nodeId
                                : args.trim();
                sink.completeNode(key);
            }
            case PLAY_SOUND -> {
                // args=<sound_id>[:<volume>:<pitch>]
                String[] parts = requireArgs(effect, args).split(":");
                float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0f;
                float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0f;
                sink.playSound(parts[0], volume, pitch);
            }
            case TELEPORT -> {
                // args=<x>,<y>,<z>[,<dimension>]
                String[] parts = requireArgs(effect, args).split(",");
                if (parts.length < 3) {
                    throw badArgs(effect, "expected <x>,<y>,<z>[,<dimension>]");
                }
                String dimension = parts.length > 3 ? parts[3].trim() : null;
                sink.teleport(
                        Double.parseDouble(parts[0].trim()),
                        Double.parseDouble(parts[1].trim()),
                        Double.parseDouble(parts[2].trim()),
                        dimension);
            }
        }
    }

    /** 对话结束：树级收尾效果执行一次。 */
    private static ChooseOutcome end(
            Session session, ConditionExpression.Context context, EffectSink sink) {
        runEffects(session.tree.entryEffects(), session, context, sink);
        return new ChooseOutcome(true, null);
    }

    /** 条件链前进：conditions 不满足的节点沿 next 旁路，直到停下点（决策节点 / 满足条件的节点 / 终端）。 */
    private static void advanceToStop(Session session, ConditionExpression.Context context) {
        while (true) {
            session.depth++;
            if (session.depth > session.tree.maxDepthLevels()) {
                throw new IllegalStateException(
                        "dialog tree '"
                                + session.tree.id()
                                + "' exceeded max_depth_levels="
                                + session.tree.maxDepthLevels()
                                + " at node '"
                                + session.nodeId
                                + "'（§4.7 求值上限——表大概率写成了环）");
            }
            NodeSpec node = session.node();
            boolean conditionsMet =
                    node.conditions() == null || ConditionDsl.satisfies(node.conditions(), context);
            if (!conditionsMet) {
                // 条件旁路：条件不满足的节点直接跳过；无 next 可跳 = 没话可说（终端）
                if (node.next() == null) {
                    return;
                }
                session.nodeId = node.next();
                continue;
            }
            // 条件满足：决策节点（有选项）停下；顺序流转节点（无选项有 next）自动前进；终端停下
            if (node.options() != null) {
                return;
            }
            if (node.next() == null) {
                return;
            }
            session.nodeId = node.next();
        }
    }

    private static String requireArgs(EffectSpec effect, String args) {
        if (args == null || args.isBlank()) {
            throw badArgs(effect, "args is required");
        }
        return args.trim();
    }

    private static IllegalArgumentException badArgs(EffectSpec effect, String why) {
        return new IllegalArgumentException(
                "effect " + effect.type() + " with args '" + effect.args() + "': " + why);
    }
}

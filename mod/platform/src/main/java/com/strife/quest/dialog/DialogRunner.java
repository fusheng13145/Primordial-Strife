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
 *   <li>进入树从 {@code root} 开始；带 conditions 的节点是<b>门</b>——条件不满足沿 {@code next} 旁路（看下一个门
 *       或兜底节点），满足则停（决策节点等玩家选）；
 *   <li>无条件的顺序节点（只有 next）进入时自动前进；选项执行自身 effects 后跳 {@code next}；next 为空 = 对话结束，随后执行树级 {@code
 *       effects} 一次；
 *   <li>选项跳转重置深度计数（玩家驱动的循环叙事合法）；门链推进累计深度，超过 {@code max_depth_levels} 即炸（门写成环是内容 bug，不是运行时特征）。
 * </ol>
 */
public final class DialogRunner {

    /** effects 的落地缝（装配层实现；纯逻辑核不碰 Minecraft）。 */
    public interface EffectSink {

        void setFlag(String key);

        void addReputation(String factionId, int delta);

        void giveItem(String itemId, long count);

        /** {@code take_item}：从玩家库存扣物品；持有不足返回 false（交付选项必须配 item() 条件兜底）。 */
        boolean takeItem(String itemId, long count);

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

        public DialogBook book() {
            return book;
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
        // 深度计数重置：选项跳转是玩家驱动的（"回上一级""再打听一件事"这类循环叙事是合法设计），
        // max_depth 防的是条件链 next 成环（无输入的自动流转，见 advanceToStop）。
        session.depth = 0;
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
            case TAKE_ITEM -> {
                // args=<item_id>:<count>；持有不足 = 内容错误（选项条件应已兜底），fail-fast 不静默
                String[] parts = requireArgs(effect, args).split(":", 2);
                if (parts.length != 2) {
                    throw badArgs(effect, "expected <item_id>:<count>");
                }
                if (!sink.takeItem(parts[0], Long.parseLong(parts[1].trim()))) {
                    throw new IllegalStateException(
                            "take_item "
                                    + args
                                    + " but player does not hold enough（交付选项必须配 item() 条件兜底）");
                }
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
                // args=<sound_id>[:<volume>:<pitch>]；sound_id 自带命名空间冒号（strife:ui.coin），
                // 所以从右往左收集最多两个纯数字段，剩下的整体作为 id。
                String[] parts = requireArgs(effect, args).trim().split(":");
                float volume = 1.0f;
                float pitch = 1.0f;
                int end = parts.length;
                if (end >= 3
                        && parts[end - 1].matches("\\d+(\\.\\d+)?")
                        && parts[end - 2].matches("\\d+(\\.\\d+)?")) {
                    pitch = Float.parseFloat(parts[--end]);
                    volume = Float.parseFloat(parts[--end]);
                }
                String soundId = String.join(":", java.util.Arrays.copyOfRange(parts, 0, end));
                if (soundId.isBlank()) {
                    throw badArgs(effect, "sound id is empty");
                }
                sink.playSound(soundId, volume, pitch);
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

    /**
     * 条件门链推进（docs/03 §10 语义）：带 conditions 的节点是<b>门</b>——条件不满足沿 {@code next} 旁路
     * （"没话对你说"，链条走到下一个门或兜底节点）；满足则停下（决策节点等玩家选，纯终端就是没话说）。 无条件的顺序节点（只有 next）自动前进。深度只在门链上累计，防表把门写成了环。
     */
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
                                + "'（§4.7 求值上限——门链大概率写成了环）");
            }
            NodeSpec node = session.node();
            boolean conditionsMet =
                    node.conditions() == null || ConditionDsl.satisfies(node.conditions(), context);
            if (!conditionsMet) {
                // 门未开：沿 next 旁路本节点；无 next = 没话可说（终端）
                if (node.next() == null) {
                    return;
                }
                session.nodeId = node.next();
                continue;
            }
            // 门已开（或本就无条件）：决策节点停下；顺序流转节点自动前进；纯终端停下
            if (node.options() != null || node.next() == null) {
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

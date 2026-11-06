package com.strife.quest.engine;

import com.strife.quest.dsl.ConditionExpression;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 多章任务簿（2026-11-06 章节接线）：prologue / ch1 / ep1 / ep2 各自是一份 {@link QuestBook}，运行时同引擎并行装载。
 *
 * <p>为什么需要这一层：{@code QuestAdapter} 此前只读 {@code prologue.json}，其余章节的产物只出现在单测里——第一章与 EP1/EP2
 * 任务在游戏里既不推进也不上追踪条。本类把 "多份章簿"收成一个纯对象：推进/对账/追踪条/状态一览都遍历全部章簿，追踪条按章序取第一个可接取任务（序章优先，其后按 chapter id
 * 字典序）。
 *
 * <p>章节入口仍是表里的 {@code entry}+{@code prerequisites} 语义：各章 entry
 * 无前置即天然可接，后续节点由前置链串住。章节之间的先后只影响追踪条展示顺序，不额外发明门槛—— 跨章门槛属于真相源决策，不在这里猜。
 */
public final class QuestBooks {

    private static final String PROLOGUE = "prologue";

    private final List<QuestBook> books;

    private QuestBooks(List<QuestBook> books) {
        this.books = List.copyOf(books);
    }

    /** 按章序整理：序章固定第一，其余按 chapter id 字典序（ch1 → ep2）。 */
    public static QuestBooks of(List<QuestBook> books) {
        if (books.isEmpty()) {
            throw new IllegalStateException("任务章簿为空：strife_quests 目录没有任何产物");
        }
        List<QuestBook> ordered = new ArrayList<>(books);
        ordered.sort(
                Comparator.<QuestBook>comparingInt(book -> PROLOGUE.equals(book.chapter()) ? 0 : 1)
                        .thenComparing(QuestBook::chapter));
        return new QuestBooks(ordered);
    }

    public static QuestBooks of(QuestBook... books) {
        return of(List.of(books));
    }

    public List<QuestBook> books() {
        return books;
    }

    /** 追踪条选中任务：按章序取第一份有可接取任务的章簿。 */
    public Optional<QuestBook.QuestView> trackable(
            QuestState state, ConditionExpression.Context dslContext) {
        for (QuestBook book : books) {
            Optional<QuestBook.QuestView> view = book.trackable(state, dslContext);
            if (view.isPresent()) {
                return view;
            }
        }
        return Optional.empty();
    }

    /** 目标事件推进全部章簿（同一事件可命中多章；可用性判定仍由各章自己负责）。 */
    public List<QuestBook.AppliedReward> report(
            QuestBook.ObjectiveType type,
            String target,
            long amount,
            QuestState state,
            RewardSink sink,
            ConditionExpression.Context dslContext) {
        List<QuestBook.AppliedReward> applied = new ArrayList<>();
        for (QuestBook book : books) {
            applied.addAll(book.report(type, target, amount, state, sink, dslContext));
        }
        return applied;
    }

    /** 库存对账跨章汇总：同一物品可能同时是序章与第一章的 COLLECT 目标。 */
    public List<QuestBook.CollectDelta> collectDeltas(
            QuestState state,
            ConditionExpression.Context dslContext,
            java.util.function.ToIntFunction<String> itemCount) {
        List<QuestBook.CollectDelta> deltas = new ArrayList<>();
        for (QuestBook book : books) {
            deltas.addAll(book.collectDeltas(state, dslContext, itemCount));
        }
        return deltas;
    }

    /** 全部章簿的任务状态一览（{@code /strife quest status}）。 */
    public String describe(QuestState state, ConditionExpression.Context dslContext) {
        StringBuilder text = new StringBuilder();
        for (QuestBook book : books) {
            text.append(book.describe(state, dslContext));
        }
        return text.toString();
    }

    /** 任务在当前进度下是否可接（前置 + 激活 + conditions）；世界钩子（试炼台）用它代替激活集判定。 */
    public boolean isAvailable(
            QuestState state, String questId, ConditionExpression.Context dslContext) {
        for (QuestBook book : books) {
            if (book.isAvailable(state, questId, dslContext)) {
                return true;
            }
        }
        return false;
    }
}

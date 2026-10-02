package com.strife.quest.dialog;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 对话 UI 的客户端镜像（common 侧纯数据持有，docs/03 §5 同步镜像惯例）。
 *
 * <p>为什么是镜像而不是直接开 Screen：{@code client_fx} 包在专用服务端被整体剔除（03 §7），common 收包回调里 不能 import 任何 client
 * 类。收包写这里（纯数据），{@code StrifeClientFx} 的 client tick 轮询 {@link #revision()} 变化后开/推进 {@code
 * DialogScreen}——与面板读 {@code StrifeClientMirror} 是同一套隔离。
 *
 * <p>版本计数保证"同内容重复推送"也能触发一次 UI 刷新（同一节点被服务端重发时 revision 仍 +1）。
 */
public final class DialogClientMirror {

    private static final AtomicLong REVISION = new AtomicLong();

    /** 当前对话节点；无活动对话为 null。 */
    private static volatile DialogPayloads.Open current;

    private DialogClientMirror() {}

    /** 服务端 S2C 回调（主线程）：整体替换镜像。 */
    public static void accept(DialogPayloads.Open payload) {
        current = payload;
        REVISION.incrementAndGet();
    }

    /** 当前对话节点；无活动对话返回 null。 */
    public static DialogPayloads.Open current() {
        return current;
    }

    /** 镜像版本号（client tick 轮询用）。 */
    public static long revision() {
        return REVISION.get();
    }

    /** 对话结束（本地关 UI 或收到 TERMINATED）时清理。 */
    public static void clear() {
        current = null;
        REVISION.incrementAndGet();
    }

    /** 便捷值：空镜像的选项列表。 */
    public static List<String> emptyOptions() {
        return List.of();
    }
}

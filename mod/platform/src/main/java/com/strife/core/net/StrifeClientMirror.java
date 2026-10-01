package com.strife.core.net;

import com.strife.core.StrifeData;

/**
 * 客户端侧的服务端权威数据镜像（docs/03 §5）。
 *
 * <p>这是"客户端只是显示器"（03 §4）的落地点：HUD 与面板读的都是这份由服务端推送的镜像，不读任何客户端本地状态， 更不读 integrated server
 * 的附件——那样写出来的面板在专用服上会直接空掉（这正是本类要取代的旧做法）。
 *
 * <p>可读性约定：{@link #getOrNull()} 在"还没收到快照"时返回 null，调用方必须降级为"暂无数据"而不是编一个默认值显示 ——面板上出现一个凭空的"凡人 0
 * 修为"比空着更容易误导玩家。
 *
 * <p>volatile：写入发生在主线程（包处理经 {@code enqueueWork} 回主线程），读取发生在渲染线程。
 */
public final class StrifeClientMirror {

    private static volatile StrifeData snapshot;
    private static volatile StrifeRealmView realmView;
    private static volatile long revision;

    private StrifeClientMirror() {}

    /** 收到全量快照：整体替换镜像。 */
    public static void accept(StrifeData data) {
        if (data == null) {
            return;
        }
        snapshot = data;
        revision++;
    }

    /** 收到差量：施加到当前镜像上；镜像还没建立时丢弃（等下一次快照，绝不半途造一份）。 */
    public static void accept(StrifeDelta delta) {
        StrifeData current = snapshot;
        if (current == null || delta == null) {
            return;
        }
        snapshot = delta.applyTo(current);
        revision++;
    }

    /** 收到面板视图（拉取式响应）：整体替换。 */
    public static void accept(StrifeRealmView updatedView) {
        if (updatedView == null) {
            return;
        }
        realmView = updatedView;
        revision++;
    }

    /** 当前面板视图；没拉取过时为 null。 */
    public static StrifeRealmView realmView() {
        return realmView;
    }

    /** 当前镜像；未同步时 null。 */
    public static StrifeData getOrNull() {
        return snapshot;
    }

    /** 是否已经拿到过服务端权威数据。 */
    public static boolean synced() {
        return snapshot != null;
    }

    /** 镜像版本号（每次接受增量 +1，调试与用例判据用）。 */
    public static long revision() {
        return revision;
    }

    /** 退出世界/断开连接时清空：留着旧镜像会让下一个世界的面板先显示上一个角色的数据。 */
    public static void clear() {
        snapshot = null;
        realmView = null;
        revision++;
    }
}

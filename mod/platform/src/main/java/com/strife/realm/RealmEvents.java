package com.strife.realm;

import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;

/**
 * realm 侧事件（docs/03 §2"跨模块只走事件"的 realm 内 MVP 版，A0-4 收编前的临时家）： quest/combat 等下游模块按事件订阅 realm
 * 的推进信号，realm 不反向 import 任何下游—— 模块链 {@code quest → realm → core} 由 checkImports 强制，本类就是那道边界的出料口。
 *
 * <p>正式版本按 A0-4 事件契约目录 v1 收编进 core（{@code PlayerBreakthroughEvent} 等）， 本类的语义与之对齐，迁移是搬文件不是改设计。
 * 契约草案（{@code docs/appendix/事件契约目录v1草案.md}）给 {@code PlayerBreakthroughEvent} 定的载荷含 {@code success}
 * 与 {@code attempts}，本类按同一口径携带，避免下游"按事件名猜结果"。
 */
public final class RealmEvents {

    private RealmEvents() {}

    /** 打坐结算一段（count 为本次结算刻数；SIT 目标按累计刻推进）。 */
    public static final class SitMeditated extends Event {

        private final net.minecraft.server.level.ServerPlayer player;
        private final long ticks;

        public SitMeditated(net.minecraft.server.level.ServerPlayer player, long ticks) {
            this.player = player;
            this.ticks = ticks;
        }

        public net.minecraft.server.level.ServerPlayer player() {
            return player;
        }

        public long ticks() {
            return ticks;
        }
    }

    /**
     * 一次突破尝试的结算结果（key 为本次使用的 NUMBERS §3 成功率键）。
     *
     * <p>{@code success} 必须显式携带：旧的"事件名即成功"口径会让订阅方把失败也当成功记账（任务链的 breakthrough 目标 就是这么错的）。{@code
     * attempts} 是本次失败后的累计次数，供订阅方展示"下次更难/更易"。
     */
    public static final class Breakthrough extends Event {

        private final net.minecraft.server.level.ServerPlayer player;
        private final String breakthroughKey;
        private final boolean success;
        private final int attempts;

        public Breakthrough(
                net.minecraft.server.level.ServerPlayer player,
                String breakthroughKey,
                boolean success,
                int attempts) {
            this.player = player;
            this.breakthroughKey = breakthroughKey;
            this.success = success;
            this.attempts = attempts;
        }

        public net.minecraft.server.level.ServerPlayer player() {
            return player;
        }

        public String breakthroughKey() {
            return breakthroughKey;
        }

        public boolean success() {
            return success;
        }

        /** 本次结算后的累计失败次数（成功时为 0）。 */
        public int attempts() {
            return attempts;
        }
    }

    public static void postSit(net.minecraft.server.level.ServerPlayer player, long ticks) {
        NeoForge.EVENT_BUS.post(new SitMeditated(player, ticks));
    }

    public static void postBreakthrough(
            net.minecraft.server.level.ServerPlayer player,
            String key,
            boolean success,
            int attempts) {
        NeoForge.EVENT_BUS.post(new Breakthrough(player, key, success, attempts));
    }

    /**
     * 大限结算（寿元耗尽，05 §4 / ADR-008 非破坏性）。载荷对齐事件契约草案的 {@code DashengSettlementEvent}：
     * 结算<b>已经完成</b>才发，订阅方不得再"追加惩罚"；{@code demoted} 区分"真退了档"与"已在凡人退无可退"， 面板与任务文案据此分支。
     */
    public static final class DashengSettlement extends Event {

        private final net.minecraft.server.level.ServerPlayer player;
        private final int realmBefore;
        private final int realmAfter;
        private final boolean demoted;
        private final long lifespanResetTicks;
        private final String debuffKey;

        public DashengSettlement(
                net.minecraft.server.level.ServerPlayer player,
                int realmBefore,
                int realmAfter,
                boolean demoted,
                long lifespanResetTicks,
                String debuffKey) {
            this.player = player;
            this.realmBefore = realmBefore;
            this.realmAfter = realmAfter;
            this.demoted = demoted;
            this.lifespanResetTicks = lifespanResetTicks;
            this.debuffKey = debuffKey;
        }

        public net.minecraft.server.level.ServerPlayer player() {
            return player;
        }

        public int realmBefore() {
            return realmBefore;
        }

        public int realmAfter() {
            return realmAfter;
        }

        public boolean demoted() {
            return demoted;
        }

        public long lifespanResetTicks() {
            return lifespanResetTicks;
        }

        public String debuffKey() {
            return debuffKey;
        }
    }

    public static void postDashengSettlement(
            net.minecraft.server.level.ServerPlayer player,
            int realmBefore,
            int realmAfter,
            boolean demoted,
            long lifespanResetTicks,
            String debuffKey) {
        NeoForge.EVENT_BUS.post(
                new DashengSettlement(
                        player, realmBefore, realmAfter, demoted, lifespanResetTicks, debuffKey));
    }
}

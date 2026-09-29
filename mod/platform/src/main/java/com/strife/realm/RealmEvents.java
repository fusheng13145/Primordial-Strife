package com.strife.realm;

import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;

/**
 * realm 侧事件（docs/03 §2"跨模块只走事件"的 realm 内 MVP 版，A0-4 收编前的临时家）： quest/combat 等下游模块按事件订阅 realm
 * 的推进信号，realm 不反向 import 任何下游—— 模块链 {@code quest → realm → core} 由 checkImports 强制，本类就是那道边界的出料口。
 *
 * <p>正式版本按 A0-4 事件契约目录 v1 收编进 core（{@code PlayerBreakthroughEvent} 等）， 本类的语义与之对齐，迁移是搬文件不是改设计。
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

    /** 一次突破尝试成功（key 为本次使用的 NUMBERS §3 成功率键）。 */
    public static final class Breakthrough extends Event {

        private final net.minecraft.server.level.ServerPlayer player;
        private final String breakthroughKey;

        public Breakthrough(
                net.minecraft.server.level.ServerPlayer player, String breakthroughKey) {
            this.player = player;
            this.breakthroughKey = breakthroughKey;
        }

        public net.minecraft.server.level.ServerPlayer player() {
            return player;
        }

        public String breakthroughKey() {
            return breakthroughKey;
        }
    }

    public static void postSit(net.minecraft.server.level.ServerPlayer player, long ticks) {
        NeoForge.EVENT_BUS.post(new SitMeditated(player, ticks));
    }

    public static void postBreakthrough(
            net.minecraft.server.level.ServerPlayer player, String key) {
        NeoForge.EVENT_BUS.post(new Breakthrough(player, key));
    }
}

package com.strife.core;

import net.minecraft.server.level.ServerPlayer;

/**
 * 奖励缝的跨模块桥（docs/03 §2：模块之间不得横向依赖，quest 不能 import combat）。
 *
 * <p>任务奖励里有"给功法""给法术"这类归属别的模块的奖励。quest 只能依赖 realm/core，够不到 combat；靠事件订阅反过来 又要求 combat
 * 知道"哪次奖励该给谁"。所以照 {@link CultivationFactors} 的做法，在 core 定一个注册口：拥有方注册实现， 需求方调用，谁也不认识谁。
 *
 * <p><b>没注册就等于没有这条路</b>：调用返回 false 并保持沉默，由调用方决定怎么向玩家解释（例如"M2 未接入"）。刻意不提供
 * "默认实现"，因为默认实现只能是个空操作——那正是本项目要清掉的那种"看起来接了其实没接"。
 */
public final class RewardBridges {

    /** 授功法（combat 注册）。 */
    @FunctionalInterface
    public interface TechniqueGranter {
        boolean grant(ServerPlayer player, String techniqueId);
    }

    /** 授法术（combat 的 M2 法术系统注册）。 */
    @FunctionalInterface
    public interface SpellGranter {
        boolean grant(ServerPlayer player, String spellId);
    }

    private static volatile TechniqueGranter techniqueGranter;
    private static volatile SpellGranter spellGranter;

    private RewardBridges() {}

    /** 注册授功法实现；重复注册是设计错误（两个模块抢同一项）。 */
    public static synchronized void registerTechniqueGranter(TechniqueGranter granter) {
        if (granter == null) {
            throw new IllegalArgumentException("technique granter must not be null");
        }
        if (techniqueGranter != null) {
            throw new IllegalStateException("technique granter already registered");
        }
        techniqueGranter = granter;
    }

    /** 注册授法术实现。 */
    public static synchronized void registerSpellGranter(SpellGranter granter) {
        if (granter == null) {
            throw new IllegalArgumentException("spell granter must not be null");
        }
        if (spellGranter != null) {
            throw new IllegalStateException("spell granter already registered");
        }
        spellGranter = granter;
    }

    /** 是否已有授功法实现（面板/命令/自检据此说明"未接入"）。 */
    public static boolean techniqueWired() {
        return techniqueGranter != null;
    }

    public static boolean spellWired() {
        return spellGranter != null;
    }

    /** 授功法；未接入时返回 false（调用方负责给出可读解释）。 */
    public static boolean grantTechnique(ServerPlayer player, String techniqueId) {
        TechniqueGranter granter = techniqueGranter;
        return granter != null && granter.grant(player, techniqueId);
    }

    /** 授法术；未接入时返回 false。 */
    public static boolean grantSpell(ServerPlayer player, String spellId) {
        SpellGranter granter = spellGranter;
        return granter != null && granter.grant(player, spellId);
    }

    /** 仅测试用：清空接线（注册是全局状态）。 */
    static synchronized void resetForTest() {
        techniqueGranter = null;
        spellGranter = null;
    }
}

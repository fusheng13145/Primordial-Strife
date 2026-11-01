package com.strife.combat;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * 命中反馈的元素映射（P1-09）：五行元素 → 原版粒子/音效（D5 占位资产策略，per-元素资产属美术管线迭代）。
 *
 * <p>"无音频/缺库不崩"由构造保证：映射表只取 vanilla 注册表常量（编译期即存在，无资源加载路径，不会因资产
 * 缺失在运行期炸）；未知元素回落中性默认，不抛错——弹道是热路径，反馈缺一拍好过打崩一拍。
 *
 * <p>服务端权威：{@code ServerLevel.playSound(null, …)} 广播给附近客户端，clientfx 零改动。
 */
final class SpellImpactFeedback {

    /** 一次命中的反馈三件套：粒子 + 音效 + 音量/音调。 */
    record Impact(ParticleOptions particle, SoundEvent sound, float volume, float pitch) {}

    private static final Impact DEFAULT =
            new Impact(ParticleTypes.CRIT, SoundEvents.GENERIC_HURT, 0.8f, 0.8f);

    private SpellImpactFeedback() {}

    /** 按五行元素取反馈；未知（含空串、大小写不符）回落 {@link #DEFAULT}。 */
    static Impact forElement(String element) {
        return switch (element) {
            case "huo" -> new Impact(ParticleTypes.FLAME, SoundEvents.FIRE_EXTINGUISH, 1.0f, 1.1f);
            case "shui" -> new Impact(ParticleTypes.SPLASH, SoundEvents.GENERIC_SPLASH, 1.0f, 1.0f);
            case "jin" ->
                    new Impact(ParticleTypes.SWEEP_ATTACK, SoundEvents.TRIDENT_HIT, 0.9f, 1.2f);
            case "mu" ->
                    new Impact(ParticleTypes.HAPPY_VILLAGER, SoundEvents.GRASS_BREAK, 0.9f, 0.9f);
            case "tu" -> new Impact(ParticleTypes.POOF, SoundEvents.GRAVEL_BREAK, 0.9f, 0.8f);
            default -> DEFAULT;
        };
    }
}

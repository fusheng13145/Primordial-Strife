package com.strife.realm;

import com.strife.core.StrifeAttachmentTypes;
import com.strife.core.StrifeData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

/**
 * 灵根生成（docs/07 §7 A1-1 的 MVP 快速通道）：UUID+seed 种子化、一次成型入档、永不重算。 品阶走 NUMBERS §6
 * roll_weights，元素数按品阶（1→单系 … 4→四/五杂灵根）。
 */
public final class SpiritRootGenerator {

    private SpiritRootGenerator() {}

    static StrifeData ensureGenerated(ServerPlayer player, StrifeData data, RealmTables tables) {
        if (data.spiritrootQuality() != 0) {
            return data; // 一次成型，永不重算（docs/03 §8）
        }
        long seed =
                BreakthroughMath.spiritRootSeed(
                        player.getUUID(), player.server.overworld().getSeed());
        RandomSource random = RandomSource.create(seed);
        RealmTables.Rules rules = tables.rules();
        BreakthroughMath.SpiritRootResult result =
                BreakthroughMath.rollSpiritRoot(
                        random::nextInt,
                        rules.rollWeightTier1(),
                        rules.rollWeightTier2(),
                        rules.rollWeightTier3(),
                        rules.rollWeightTier4());
        StrifeData generated =
                new StrifeData(
                        data.dataVersion(),
                        data.realmOrdinal(),
                        data.stage(),
                        data.qi(),
                        data.lifespanTicks(),
                        data.flags(),
                        result.quality(),
                        result.elementsMask(),
                        data.breakthroughAttempts(),
                        data.affiliation(),
                        data.reputation());
        player.setData(StrifeAttachmentTypes.PLAYER_DATA, generated);
        player.displayClientMessage(
                Component.literal("灵根显现：")
                        .append(spiritRootText(result))
                        .withStyle(net.minecraft.ChatFormatting.AQUA),
                false);
        return generated;
    }

    private static Component spiritRootText(BreakthroughMath.SpiritRootResult result) {
        String[] elements = {"金", "木", "水", "火", "土"};
        StringBuilder names = new StringBuilder();
        for (int bit = 0; bit < 5; bit++) {
            if ((result.elementsMask() & (1 << bit)) != 0) {
                if (names.length() > 0) {
                    names.append('/');
                }
                names.append(elements[bit]);
            }
        }
        return Component.literal(names + "灵根 · 品阶 " + result.quality());
    }
}

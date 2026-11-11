package com.strife.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 功法状态（03 §1 combat 职责：已学清单 + 当前装备；03 §8 洗髓要求"学会了但用不了"可表达）。 */
class TechniqueStateTest {

    @Test
    void emptyStateKnowsNothingAndEquipsNothing() {
        assertEquals(Set.of(), TechniqueState.EMPTY.learned());
        assertEquals("", TechniqueState.EMPTY.equipped());
        assertFalse(TechniqueState.EMPTY.knows("tech_qingxin_jue"));
    }

    @Test
    void learningIsIdempotent() {
        TechniqueState once = TechniqueState.EMPTY.learning("tech_qingxin_jue");
        TechniqueState twice = once.learning("tech_qingxin_jue");

        assertEquals(once, twice, "任务奖励可能被重放，学法必须幂等");
        assertTrue(twice.knows("tech_qingxin_jue"));
    }

    @Test
    void blankIdIsIgnoredInsteadOfCreatingAJackedEntry() {
        assertEquals(TechniqueState.EMPTY, TechniqueState.EMPTY.learning(""));
        assertEquals(TechniqueState.EMPTY, TechniqueState.EMPTY.learning(null));
    }

    @Test
    void equippingRequiresHavingLearnedIt() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TechniqueState.EMPTY.equipping("tech_qingxin_jue"),
                "没学过就装备 = 凭空生效，必须拒绝");

        TechniqueState state =
                TechniqueState.EMPTY.learning("tech_qingxin_jue").equipping("tech_qingxin_jue");
        assertEquals("tech_qingxin_jue", state.equipped());
    }

    @Test
    void equippingEmptyStringUnequips() {
        TechniqueState state =
                TechniqueState.EMPTY.learning("tech_qingxin_jue").equipping("tech_qingxin_jue");

        assertEquals("", state.equipping("").equipped());
        assertEquals("", state.equipping(null).equipped(), "null 与空串同义（卸下）");
    }

    @Test
    void unequipIfOnlyTouchesTheInvalidOne() {
        TechniqueState state =
                TechniqueState.EMPTY
                        .learning("tech_qingxin_jue")
                        .learning("tech_jinsha_jue")
                        .equipping("tech_qingxin_jue");

        assertEquals(state, state.unequipIf(id -> false), "条件不成立时原样返回（幂等，不产生新对象）");
        assertEquals("", state.unequipIf(id -> true).equipped());
        assertEquals(2, state.unequipIf(id -> true).learned().size(), "卸下不等于抹掉已学");
    }

    /** 入档字段带默认值（03 §3 存档兼容红线）：老档缺 techniques 时读成空状态而不是构建失败。 */
    @Test
    void codecDefaultsMissingFieldsToEmpty() {
        TechniqueState decoded =
                TechniqueState.CODEC
                        .parse(JsonOps.INSTANCE, new JsonObject())
                        .result()
                        .orElseThrow();

        assertEquals(TechniqueState.EMPTY, decoded);
    }

    @Test
    void codecRoundTripsEveryField() {
        TechniqueState state =
                TechniqueState.EMPTY
                        .learning("tech_qingxin_jue")
                        .learning("tech_houtu_gong")
                        .equipping("tech_houtu_gong");

        var encoded =
                TechniqueState.CODEC.encodeStart(JsonOps.INSTANCE, state).result().orElseThrow();
        TechniqueState decoded =
                TechniqueState.CODEC.parse(JsonOps.INSTANCE, encoded).result().orElseThrow();

        assertEquals(state, decoded);
    }

    @Test
    void proficiencyGainsOnlyWhenEquippedAndSurvivesReequip() {
        TechniqueState learned =
                new TechniqueState(java.util.Set.of("tech_qingxin_jue"), "tech_qingxin_jue", 0);

        // 施法累积（装备中才涨）
        TechniqueState trained = learned.withProficiencyGain();
        assertEquals(1, trained.proficiency(), "装备中施法 +1");

        // 卸下 → 熟练度保留；回装 → 延续（切换不清零）
        TechniqueState unequipped = trained.equipping("");
        assertEquals(1, unequipped.proficiency(), "卸下不清零（回装延续）");
        TechniqueState reequipped = unequipped.equipping("tech_qingxin_jue");
        assertEquals(1, reequipped.proficiency(), "回装延续熟练");

        // 未装备施法 = 空操作（不给暗收益）
        assertEquals(unequipped, unequipped.withProficiencyGain());
    }

    @Test
    void proficiencyCodecIsAppendOnlyBackwardCompatible() {
        // 旧档（无 proficiency 字段）解码 = 0，不破档
        TechniqueState legacy = new TechniqueState(java.util.Set.of("t1"), "t1", 0);
        var legacyTag = com.mojang.serialization.DataResult.<net.minecraft.nbt.Tag>success(null);
        // 手工构造无 proficiency 的 NBT：仅 learned+equipped
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("equipped", "t1");
        var list = new net.minecraft.nbt.ListTag();
        list.add(net.minecraft.nbt.StringTag.valueOf("t1"));
        tag.put("learned", list);
        var decoded =
                TechniqueState.CODEC.decode(net.minecraft.nbt.NbtOps.INSTANCE, tag)
                        .result()
                        .map(com.mojang.datafixers.util.Pair::getFirst)
                        .orElseThrow();
        assertEquals(0, decoded.proficiency(), "旧档缺字段 → 0（append-only 兼容）");
        assertEquals("t1", decoded.equipped());
    }
}

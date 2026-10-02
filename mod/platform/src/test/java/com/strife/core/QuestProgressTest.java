package com.strife.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.DataResult;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 任务进度附件组件的契约（03 §3：serialize/deserialize 往返一致、新增字段带默认值）。
 *
 * <p>这条组件承载"任务进度随角色生死"的全部数据，任何一次 codec 漂移的表现都是"做完的任务登录后回来又要重做"—— 所以往返一致性在这里单独钉住，不依赖 StrifeDataTest
 * 的聚合用例间接覆盖。
 */
class QuestProgressTest {

    private static Tag encode(QuestProgress progress) {
        DataResult<Tag> result = QuestProgress.CODEC.encodeStart(NbtOps.INSTANCE, progress);
        return result.result().orElseThrow(() -> new AssertionError("encode failed: " + result));
    }

    private static QuestProgress decode(Tag tag) {
        DataResult<QuestProgress> result =
                QuestProgress.CODEC.decode(NbtOps.INSTANCE, tag).map(pair -> pair.getFirst());
        return result.result().orElseThrow(() -> new AssertionError("decode failed: " + result));
    }

    @Test
    @DisplayName("非空进度往返一致：已完成集合 + 嵌套目标进度 + H3 标记")
    void roundTripPreservesAllThreeParts() {
        QuestProgress original =
                new QuestProgress(
                        Set.of("quest_prologue_herb_pick_01", "quest_prologue_deliver_01"),
                        Map.of(
                                "quest_prologue_herb_pick_01", Map.of("collect", 3L),
                                "quest_prologue_breakthrough_qi_01", Map.of("breakthrough", 1L)),
                        Set.of("quest_scaffold_talk_done", "h3_luoxia_met"));

        QuestProgress decoded = decode(encode(original));

        assertEquals(original, decoded);
        assertEquals(original.completed(), decoded.completed());
        assertEquals(original.objectiveProgress(), decoded.objectiveProgress());
        assertEquals(original.flags(), decoded.flags());
    }

    @Test
    @DisplayName("缺字段解码走空默认（旧档兼容：quests 字段引入前的存档不破档）")
    void missingFieldsFallBackToEmpty() {
        QuestProgress decoded = decode(encode(QuestProgress.EMPTY));

        assertEquals(QuestProgress.EMPTY, decoded);
        assertTrue(decoded.completed().isEmpty());
        assertTrue(decoded.objectiveProgress().isEmpty());
        assertTrue(decoded.flags().isEmpty());
    }

    @Test
    @DisplayName("progress 对未知任务/未知目标一律返回 0，不抛异常")
    void progressDefaultsToZeroForUnknownKeys() {
        QuestProgress progress =
                new QuestProgress(Set.of(), Map.of("quest_a", Map.of("obj_1", 2L)), Set.of());

        assertEquals(2L, progress.progress("quest_a", "obj_1"));
        assertEquals(0L, progress.progress("quest_a", "obj_unknown"));
        assertEquals(0L, progress.progress("quest_unknown", "obj_1"));
    }

    @Test
    @DisplayName("withFlag 幂等且不可变：重复添加不产生新对象外的副作用")
    void withFlagIsIdempotent() {
        QuestProgress base = new QuestProgress(Set.of("quest_a"), Map.of(), Set.of());

        QuestProgress once = base.withFlag("h3_x");
        QuestProgress twice = once.withFlag("h3_x");

        assertTrue(once.hasFlag("h3_x"));
        assertEquals(once, twice, "重复添加同一标记必须得到相等状态");
        assertFalse(base.hasFlag("h3_x"), "withFlag 不得改动原对象");
        assertTrue(once.isCompleted("quest_a"), "withFlag 只动 flags，进度保持");
    }

    @Test
    @DisplayName("withFlag 对空白标记是空操作（内容层脏数据不进档）")
    void withFlagIgnoresBlank() {
        QuestProgress base = QuestProgress.EMPTY;

        assertEquals(base, base.withFlag(null));
        assertEquals(base, base.withFlag(""));
        assertEquals(base, base.withFlag("   "));
    }

    @Test
    @DisplayName("构造器防御性拷贝：外部持有的可变集合后续改动不影响已构造状态")
    void constructorCopiesInputs() {
        Set<String> completed = new java.util.HashSet<>(Set.of("quest_a"));
        Map<String, Map<String, Long>> progress = new java.util.HashMap<>();
        progress.put("quest_a", new java.util.HashMap<>(Map.of("obj", 1L)));

        QuestProgress snapshot = new QuestProgress(completed, progress, Set.of());
        completed.add("quest_b");
        progress.get("quest_a").put("obj", 99L);

        assertEquals(Set.of("quest_a"), snapshot.completed(), "已完成集合被外部污染");
        assertEquals(1L, snapshot.progress("quest_a", "obj"), "目标进度被外部污染");
    }
}

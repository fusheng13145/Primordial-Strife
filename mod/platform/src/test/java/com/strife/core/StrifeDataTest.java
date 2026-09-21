package com.strife.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.DataResult;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * M0 准出两项的可执行验证（07 分册 §3）：
 *
 * <ul>
 *   <li>"存档重载三次数据一致" → {@link #roundTripIsIdenticalAcrossThreeLoads()}
 *   <li>"附件内存快照测试 ≤2KB" → {@link #serializedSizeStaysWithinAttachmentBudget()}
 * </ul>
 *
 * <p>NeoForge 21.1.251 的 AttachmentType 没有 serializedSizeLimit，2KB 预算只能自建门禁： 这里以 <b>NBT
 * 未压缩编码长度</b>计量。它是持久化负载的准确值，但只是 Java 堆占用的下界估算， 堆侧真值待 M1 运行时快照测试补（未验证项）。
 */
class StrifeDataTest {

    private static final int ATTACHMENT_BUDGET_BYTES = 2048;

    private static Tag encode(StrifeData data) {
        DataResult<Tag> result = StrifeData.CODEC.encodeStart(NbtOps.INSTANCE, data);
        return result.result().orElseThrow(() -> new AssertionError("encode failed: " + result));
    }

    private static StrifeData decode(Tag tag) {
        DataResult<StrifeData> result =
                StrifeData.CODEC.decode(NbtOps.INSTANCE, tag).map(pair -> pair.getFirst());
        return result.result().orElseThrow(() -> new AssertionError("decode failed: " + result));
    }

    private static StrifeData saturated() {
        return new StrifeData(
                StrifeData.CURRENT_DATA_VERSION,
                8,
                9,
                Integer.MAX_VALUE,
                12000L * StrifeData.TICKS_PER_YEAR,
                -1L,
                4,
                0b11111,
                7,
                "faction_sect_qingxin",
                Map.of(
                        "faction_sect_qingxin", 100,
                        "faction_race_yao", -100,
                        "faction_dynasty_xuantian", 42,
                        "faction_clan_lei", -7));
    }

    @Test
    @DisplayName("往返序列化三次结果一致（03 §3 断线重连/重载一致性）")
    void roundTripIsIdenticalAcrossThreeLoads() {
        StrifeData original = saturated();
        Tag firstPayload = encode(original);
        StrifeData current = original;
        for (int load = 0; load < 3; load++) {
            current = decode(encode(current));
            assertEquals(original, current, "load " + load + " diverged");
            assertEquals(
                    firstPayload, encode(current), "NBT payload not deterministic on load " + load);
        }
    }

    @Test
    @DisplayName("新玩家默认值可往返，且字段全为初始态")
    void newPlayerDefaultsRoundTrip() {
        StrifeData fresh = StrifeData.newPlayer();
        assertEquals(fresh, decode(encode(fresh)));
        assertEquals(0, fresh.realmOrdinal());
        assertEquals(0L, fresh.lifespanTicks(), "寿元必须为 0=未初始化，不能预置假值");
        assertTrue(fresh.reputation().isEmpty());
    }

    @Test
    @DisplayName("缺字段的旧存档解码走默认值，不破档（03 §3 新增字段必须带默认值）")
    void missingFieldsFallBackToDefaults() {
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("realm", 2);
        legacy.putInt("qi", 500);

        StrifeData migrated = decode(legacy);

        assertEquals(2, migrated.realmOrdinal());
        assertEquals(500, migrated.qi());
        assertEquals(StrifeData.CURRENT_DATA_VERSION, migrated.dataVersion(), "缺版本字段应视为当前版本写入");
        assertEquals(1, migrated.stage(), "缺 stage 必须回落到 1 段，不能是 0");
        assertEquals("", migrated.affiliation());
        assertEquals(Map.of(), migrated.reputation());
        assertEquals(0L, migrated.lifespanTicks());
    }

    @Test
    @DisplayName("单玩家附件 NBT 编码 ≤2KB（03 §3 内存预算）")
    void serializedSizeStaysWithinAttachmentBudget() throws IOException {
        Tag tag = encode(saturated());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            NbtIo.write((CompoundTag) tag, out);
        }
        int size = bytes.size();
        assertTrue(
                size <= ATTACHMENT_BUDGET_BYTES,
                "attachment payload is "
                        + size
                        + "B, budget is "
                        + ATTACHMENT_BUDGET_BYTES
                        + "B per player");
    }

    @Test
    @DisplayName("withQi 只改修为，其余字段不动（不可变值对象约定）")
    void withQiOnlyChangesQi() {
        StrifeData base = saturated();
        StrifeData updated = base.withQi(base.qi() + 10);
        assertEquals(base.qi() + 10, updated.qi());
        assertEquals(base.dataVersion(), updated.dataVersion());
        assertEquals(base.flags(), updated.flags());
        assertEquals(base.reputation(), updated.reputation());
    }
}

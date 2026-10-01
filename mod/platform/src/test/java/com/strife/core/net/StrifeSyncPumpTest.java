package com.strife.core.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.strife.core.StrifeData;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * docs/03 §5 的三条硬约束逐条验：无变化 0 包、静止 ≤5 包/s、单包不超预算（超限跨 tick 分片后仍收敛）。
 *
 * <p>判据是"客户端镜像最终等于服务端权威"，不是"发了几个包"——包数只是手段，镜像一致才是目的。
 */
class StrifeSyncPumpTest {

    private static final long SECOND = 1_000_000_000L;
    private static final long TICK = SECOND / 20;

    private static StrifeData data(int qi, Map<String, Integer> reputation) {
        return new StrifeData(
                StrifeData.CURRENT_DATA_VERSION,
                1,
                3,
                qi,
                2_000_000L,
                0b101L,
                2,
                0b00101,
                0,
                "fac_qingshi",
                reputation);
    }

    private static StrifeData base() {
        return data(120, Map.of("fac_qingshi", 10));
    }

    private static StrifeSyncPump pump(long resyncSeconds) {
        return new StrifeSyncPump(base(), 5.0, 32_768, resyncSeconds * SECOND, 0L);
    }

    @Test
    void idlePlayerCostsZeroPackets() {
        StrifeSyncPump pump = pump(30);

        for (int tick = 0; tick < 100; tick++) {
            assertTrue(
                    pump.poll(base(), tick * TICK).isEmpty(),
                    "没有变化就不该发包（比 ≤5 包/s 更省，03 §5 的下限不是配额）");
        }
        assertEquals(0, pump.packetsSent());
        assertEquals(base(), pump.clientView());
    }

    @Test
    void aChangeIsPushedOnceAndTheMirrorMatchesTheAuthority() {
        StrifeSyncPump pump = pump(30);
        StrifeData changed = data(180, Map.of("fac_qingshi", 10));

        List<StrifeDelta> outgoing = pump.poll(changed, 0L);

        assertEquals(1, outgoing.size());
        assertEquals(changed, pump.clientView());
        assertFalse(outgoing.get(0).isEmpty());
    }

    /**
     * 静默上限：把变化密度开到每 tick 都有（远高于限速），逐秒统计包数。令牌桶容量取 1，因此任一半开窗口 (t, t+1] 最多 5 个包——03 §6
     * 的红线按字面成立，不靠"平均下来差不多"。
     */
    @Test
    void neverExceedsFivePacketsPerSecondUnderMaximumChurn() {
        StrifeSyncPump pump = pump(600);
        int seconds = 20;
        int ticks = seconds * 20;
        int[] packetsInWindow = new int[seconds];

        for (int tick = 0; tick <= ticks; tick++) {
            List<StrifeDelta> outgoing =
                    pump.poll(data(120 + tick, Map.of("fac_qingshi", 10)), tick * TICK);
            if (tick > 0) {
                packetsInWindow[(tick - 1) / 20] += outgoing.size();
            }
        }

        for (int window = 0; window < seconds; window++) {
            assertTrue(
                    packetsInWindow[window] <= 5,
                    "第 " + (window + 1) + " 秒发了 " + packetsInWindow[window] + " 个包（上限 5）");
        }
        assertTrue(pump.packetsSent() >= 90, "限速不是不发：20 秒至少该推 ~100 个包");
    }

    /** 限速期间不许丢内容：变化停下来之后，镜像必须收敛到权威值。 */
    @Test
    void mirrorConvergesAfterTheChurnStops() {
        StrifeSyncPump pump = pump(600);
        StrifeData finalState = data(999, Map.of("fac_yuelai", 7));

        for (int tick = 0; tick <= 100; tick++) {
            pump.poll(data(120 + tick, Map.of("fac_qingshi", 10)), tick * TICK);
        }
        assertFalse(finalState.equals(pump.clientView()), "限速下还没追平（这是预期的中间态）");

        pump.poll(finalState, 200 * TICK);
        long settleAt = 200 * TICK;
        for (int i = 0; i < 40 && !finalState.equals(pump.clientView()); i++) {
            settleAt += SECOND;
            pump.poll(finalState, settleAt);
        }

        assertEquals(finalState, pump.clientView());
    }

    /** 单包超预算时跨 tick 分片：每一片都不超限，且全部发完后镜像仍等于权威值。 */
    @Test
    void oversizedUpdateIsSplitAcrossPacketsAndStillConverges() {
        Map<String, Integer> big = new LinkedHashMap<>();
        for (int i = 0; i < 400; i++) {
            big.put("fac_generated_" + i, i);
        }
        StrifeData target = data(500, big);
        StrifeSyncPump pump = new StrifeSyncPump(base(), 5.0, 1_024, 600 * SECOND, 0L);

        long now = 0L;
        int guard = 0;
        while (!target.equals(pump.clientView()) && guard++ < 200) {
            for (StrifeDelta part : pump.poll(target, now)) {
                assertTrue(
                        part.encodedSizeUpperBound() <= 1_024,
                        "分片超预算：" + part.encodedSizeUpperBound());
            }
            now += SECOND;
        }

        assertEquals(target, pump.clientView(), "分片之后仍必须收敛到权威值");
        assertEquals(0, pump.queuedParts(), "队列必须发空");
    }

    /** 周期性全量重同步：delta 链漏一拍时，这是唯一的收敛手段（丢包/重连/别的模块直接写了附件）。 */
    @Test
    void periodicFullResyncRepairsADriftedMirror() {
        StrifeSyncPump pump = pump(30);
        StrifeData target = data(777, Map.of("fac_yuelai", 3));
        pump.poll(target, 0L);
        assertEquals(target, pump.clientView());

        assertEquals(0, pump.fullSyncsSent());
        // 30 秒之前不重同步；到点后发一份全量。
        pump.poll(target, 29 * SECOND);
        assertEquals(0, pump.fullSyncsSent());
        List<StrifeDelta> resync = pump.poll(target, 30 * SECOND);
        assertEquals(1, pump.fullSyncsSent());
        assertEquals(1, resync.size());
        assertEquals(StrifeSyncField.COUNT, resync.get(0).changedFieldCount());
        assertEquals(target, pump.clientView());
    }

    @Test
    void rejectsNonsensePayloadBudget() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new StrifeSyncPump(base(), 5.0, 0, SECOND, 0L));
    }
}

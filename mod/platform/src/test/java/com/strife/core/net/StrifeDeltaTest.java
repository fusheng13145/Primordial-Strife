package com.strife.core.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.strife.core.StrifeData;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** delta 同步的核不变式：客户端把差量应用到本地镜像后，必须与服务端权威逐字段相等。整个同步框架的正确性都压在这一条上， 所以这里不测"包发了几个"，只测"镜像算对没有"。 */
class StrifeDeltaTest {

    private static StrifeData data(
            int realm,
            int stage,
            int qi,
            long lifespan,
            long flags,
            int quality,
            int elements,
            int attempts,
            String affiliation,
            Map<String, Integer> reputation) {
        return new StrifeData(
                StrifeData.CURRENT_DATA_VERSION,
                realm,
                stage,
                qi,
                lifespan,
                flags,
                quality,
                elements,
                attempts,
                affiliation,
                reputation);
    }

    private static StrifeData base() {
        return data(
                1,
                3,
                120,
                2_000_000L,
                0b101L,
                2,
                0b00101,
                0,
                "fac_qingshi",
                Map.of("fac_qingshi", 10));
    }

    @Test
    void identicalSnapshotsProduceNoDelta() {
        StrifeDelta delta = StrifeDelta.between(base(), base());
        assertTrue(delta.isEmpty());
        assertEquals(0, delta.changedFieldCount());
    }

    @Test
    void deltaCarriesOnlyChangedFieldsAndRebuildsTheTarget() {
        StrifeData before = base();
        StrifeData after =
                data(
                        1,
                        3,
                        180,
                        1_999_000L,
                        0b101L,
                        2,
                        0b00101,
                        0,
                        "fac_qingshi",
                        Map.of("fac_qingshi", 10));

        StrifeDelta delta = StrifeDelta.between(before, after);

        assertTrue(delta.has(StrifeSyncField.QI));
        assertTrue(delta.has(StrifeSyncField.LIFESPAN_TICKS));
        assertFalse(delta.has(StrifeSyncField.REALM_ORDINAL));
        assertFalse(delta.has(StrifeSyncField.REPUTATION));
        assertEquals(2, delta.changedFieldCount());
        assertEquals(after, delta.applyTo(before));
    }

    @Test
    void affiliationAndReputationReplaceWholeValues() {
        StrifeData before = base();
        StrifeData after =
                data(1, 3, 120, 2_000_000L, 0b101L, 2, 0b00101, 0, "", Map.of("fac_yuelai", -5));

        StrifeDelta delta = StrifeDelta.between(before, after);

        assertTrue(delta.has(StrifeSyncField.AFFILIATION));
        assertTrue(delta.has(StrifeSyncField.REPUTATION));
        assertEquals(after, delta.applyTo(before));
        // 整值替换：镜像不该留下旧势力的残影（否则玩家的声望面板会凭空多一行）。
        assertEquals(Map.of("fac_yuelai", -5), delta.applyTo(before).reputation());
    }

    @Test
    void fullSnapshotCarriesEveryFieldAndAppliesToAnyBase() {
        StrifeData target = base();
        StrifeData stale = data(7, 1, 0, 0L, 0L, 0, 0, 9, "", Map.of());

        StrifeDelta full = StrifeDelta.full(target);

        assertEquals(StrifeSyncField.COUNT, full.changedFieldCount());
        assertEquals(target, full.applyTo(stale));
    }

    @Test
    void mergeKeepsTheNewestValueOfEachField() {
        StrifeData start = base();
        StrifeData mid =
                data(
                        1,
                        3,
                        150,
                        1_999_900L,
                        0b101L,
                        2,
                        0b00101,
                        0,
                        "fac_qingshi",
                        Map.of("fac_qingshi", 10));
        StrifeData end =
                data(
                        1,
                        3,
                        175,
                        1_999_900L,
                        0b111L,
                        2,
                        0b00101,
                        0,
                        "fac_yuelai",
                        Map.of("fac_yuelai", 3));

        StrifeDelta merged = StrifeDelta.between(start, mid).merge(StrifeDelta.between(mid, end));

        // 合并结果必须与"直接算一次从 start 到 end 的差"等价——这是 tick 末合并的正确性判据。
        assertEquals(StrifeDelta.between(start, end).applyTo(start), merged.applyTo(start));
        assertEquals(end, merged.applyTo(start));
    }

    @Test
    void mergeIsIdentityWithEmpty() {
        StrifeDelta delta =
                StrifeDelta.between(
                        base(),
                        data(
                                1,
                                4,
                                120,
                                2_000_000L,
                                0b101L,
                                2,
                                0b00101,
                                0,
                                "fac_qingshi",
                                Map.of("fac_qingshi", 10)));
        assertEquals(delta.applyTo(base()), delta.merge(StrifeDelta.EMPTY).applyTo(base()));
        assertEquals(delta.applyTo(base()), StrifeDelta.EMPTY.merge(delta).applyTo(base()));
    }

    @Test
    void constructorCopiesMutableInputs() {
        long[] values = new long[StrifeSyncField.COUNT];
        values[StrifeSyncField.QI.ordinal()] = 42L;
        Map<String, Integer> reputation = new LinkedHashMap<>();
        reputation.put("fac_qingshi", 1);

        StrifeDelta delta =
                new StrifeDelta(StrifeSyncField.QI.bit(), values, "fac_qingshi", reputation);
        values[StrifeSyncField.QI.ordinal()] = 999L;
        reputation.put("fac_yuelai", 2);

        assertEquals(42L, delta.applyTo(base()).qi());
        assertEquals(Map.of("fac_qingshi", 1), delta.reputation());
        assertNotSame(values, delta.scalarValues());
    }

    @Test
    void smallDeltaFitsThePacketBudgetInOnePiece() {
        StrifeDelta delta =
                StrifeDelta.between(
                        base(),
                        data(
                                1,
                                3,
                                180,
                                2_000_000L,
                                0b101L,
                                2,
                                0b00101,
                                0,
                                "fac_qingshi",
                                Map.of("fac_qingshi", 10)));
        assertEquals(List.of(delta), delta.splitForBudget(32_768));
        assertTrue(delta.encodedSizeUpperBound() < 1_000);
    }

    @Test
    void oversizedDeltaIsSplitIntoPacketsThatEachFitTheBudget() {
        Map<String, Integer> big = new LinkedHashMap<>();
        for (int i = 0; i < 400; i++) {
            big.put("fac_generated_" + i, i);
        }
        StrifeData before = base();
        StrifeData after = data(2, 1, 500, 1_000_000L, 0b111L, 3, 0b11111, 4, "fac_yuelai", big);
        StrifeDelta delta = StrifeDelta.between(before, after);
        int budget = 1_024;

        List<StrifeDelta> parts = delta.splitForBudget(budget);

        assertTrue(parts.size() > 1, "超限的差量必须被切开，实际 " + parts.size() + " 片");
        for (StrifeDelta part : parts) {
            assertTrue(
                    part.encodedSizeUpperBound() <= budget,
                    "分片仍超预算：" + part.encodedSizeUpperBound() + " > " + budget);
        }
        // 逐片施加与一次性施加等价：分片不许丢字段，也不许把字段拆成两半。
        StrifeData stepwise = before;
        for (StrifeDelta part : parts) {
            stepwise = part.applyTo(stepwise);
        }
        assertEquals(after, stepwise);
    }

    @Test
    void emptyDeltaSplitsToNothing() {
        assertTrue(StrifeDelta.EMPTY.splitForBudget(1_024).isEmpty());
    }
}

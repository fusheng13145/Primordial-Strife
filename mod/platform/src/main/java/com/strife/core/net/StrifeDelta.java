package com.strife.core.net;

import com.strife.core.MeditationState;
import com.strife.core.StrifeData;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 一次同步推送的字段差量（docs/03 §5"变更合并 + delta 更新"）。
 *
 * <p>纯逻辑，不依赖 Minecraft 类型：服务端算差、tick 末合并、按预算分片，客户端把它应用到本地镜像——两侧跑的是同一份代码， 所以"客户端镜像 ==
 * 服务端权威"这条不变式可以用单测钉住（{@code applyTo(before)} 必须等于 {@code after}）。
 *
 * <p>位序契约见 {@link StrifeSyncField}：{@code mask} 的 bit i 表示第 i 个字段在包内有效，{@code scalarValues[i]}
 * 是它的值。 变长字段（{@link StrifeSyncField#AFFILIATION} / {@link StrifeSyncField#REPUTATION}）不走槽位。
 *
 * <p>声望向量的两种语义（{@code reputationPartial}）：默认是<b>整值替换</b>——{@link #between} 给出的永远是完整映射，
 * 客户端直接换掉本地那份，被删掉的势力不会在面板里留残影。分片时只有首片用替换、后续片用<b>逐条 upsert</b>：否则逐片施加会 互相覆盖，客户端最终只剩最后一片的势力。（这个错误正是
 * {@code oversizedDeltaIsSplitIntoPacketsThatEachFitTheBudget} 当初抓出来的，不是推演出来的。）
 *
 * <p>不变式：{@code scalarValues.length == StrifeSyncField.COUNT}；构造器对入参做防御性拷贝，delta 一经产生就不可变。
 */
public record StrifeDelta(
        long mask,
        long[] scalarValues,
        String affiliation,
        Map<String, Integer> reputation,
        boolean reputationPartial) {

    public static final StrifeDelta EMPTY =
            new StrifeDelta(0L, new long[StrifeSyncField.COUNT], null, Map.of(), false);

    public StrifeDelta {
        scalarValues = scalarValues.clone();
        // 名声向量允许 null 入口（存档反序列化可能给出 null），但 delta 内部一律用空映射表示"没有名声"：
        // 否则一次 merge 就会把 null 带到客户端镜像，玩家的声望面板直接 NPE。
        reputation = reputation == null ? Map.of() : Map.copyOf(reputation);
    }

    /** 整值替换语义的便捷构造（{@link #between} / {@link #full} 走这条）。 */
    public static StrifeDelta of(
            long mask, long[] scalarValues, String affiliation, Map<String, Integer> reputation) {
        return new StrifeDelta(mask, scalarValues, affiliation, reputation, false);
    }

    /** 服务端算差：{@code from} 与 {@code to} 之间真正变化的字段。无变化时返回 {@link #EMPTY}。 */
    public static StrifeDelta between(StrifeData from, StrifeData to) {
        long mask = 0L;
        long[] values = new long[StrifeSyncField.COUNT];
        for (StrifeSyncField field : StrifeSyncField.values()) {
            if (!field.scalar()) {
                continue;
            }
            long before = field.read(from);
            long after = field.read(to);
            if (before != after) {
                mask |= field.bit();
                values[field.ordinal()] = after;
            }
        }
        String affiliation = null;
        if (!Objects.equals(from.affiliation(), to.affiliation())) {
            mask |= StrifeSyncField.AFFILIATION.bit();
            affiliation = to.affiliation();
        }
        Map<String, Integer> reputation = Map.of();
        if (!Objects.equals(from.reputation(), to.reputation())) {
            mask |= StrifeSyncField.REPUTATION.bit();
            reputation = to.reputation();
        }
        return mask == 0L ? EMPTY : StrifeDelta.of(mask, values, affiliation, reputation);
    }

    /** 全量差（登录/重连的快照）：所有字段都算变化。 */
    public static StrifeDelta full(StrifeData data) {
        long[] values = new long[StrifeSyncField.COUNT];
        for (StrifeSyncField field : StrifeSyncField.values()) {
            if (field.scalar()) {
                values[field.ordinal()] = field.read(data);
            }
        }
        return StrifeDelta.of(
                StrifeSyncField.ALL_BITS, values, data.affiliation(), data.reputation());
    }

    public boolean has(StrifeSyncField field) {
        return (mask & field.bit()) != 0L;
    }

    public boolean isEmpty() {
        return mask == 0L;
    }

    public int changedFieldCount() {
        return Long.bitCount(mask);
    }

    /**
     * tick 末合并：同一 tick 内多次变更只发一个包（03 §5）。{@code newer} 覆盖同名字段。
     *
     * <p>变长字段的语义是"整值替换"：只要任一侧带着它，结果就带 {@code newer} 的值（{@code newer} 没带则保留本侧）， 因为 {@link #between}
     * 每次给的都是完整映射而非增量条目——否则合并会得到一个谁也没声明过的中间态。
     *
     * <p>注意：{@link #splitForBudget} 产出的分片必须按序单独发出，不要再走 merge——分片的 upsert 语义只在按序施加时成立。
     */
    public StrifeDelta merge(StrifeDelta newer) {
        if (newer.isEmpty()) {
            return this;
        }
        if (this.isEmpty()) {
            return newer;
        }
        long mergedMask = mask | newer.mask;
        long[] values = new long[StrifeSyncField.COUNT];
        for (StrifeSyncField field : StrifeSyncField.values()) {
            if (!field.scalar()) {
                continue;
            }
            values[field.ordinal()] =
                    newer.has(field)
                            ? newer.scalarValues[field.ordinal()]
                            : scalarValues[field.ordinal()];
        }
        String mergedAffiliation =
                newer.has(StrifeSyncField.AFFILIATION)
                        ? newer.affiliation
                        : (has(StrifeSyncField.AFFILIATION) ? affiliation : null);
        boolean newerOwnsReputation = newer.has(StrifeSyncField.REPUTATION);
        Map<String, Integer> mergedReputation =
                newerOwnsReputation
                        ? newer.reputation
                        : (has(StrifeSyncField.REPUTATION) ? reputation : Map.of());
        boolean mergedPartial =
                newerOwnsReputation
                        ? newer.reputationPartial
                        : has(StrifeSyncField.REPUTATION) && reputationPartial;
        return new StrifeDelta(
                mergedMask, values, mergedAffiliation, mergedReputation, mergedPartial);
    }

    /** 客户端施加差量：得到与服务端一致的镜像。未覆盖的字段一律保留 {@code base} 的值。 */
    public StrifeData applyTo(StrifeData base) {
        long[] values = new long[StrifeSyncField.COUNT];
        for (StrifeSyncField field : StrifeSyncField.values()) {
            if (field.scalar()) {
                values[field.ordinal()] =
                        has(field) ? scalarValues[field.ordinal()] : field.read(base);
            }
        }
        MeditationState meditation =
                new MeditationState(
                        values[StrifeSyncField.MEDITATION_START_TICK.ordinal()],
                        (int) values[StrifeSyncField.MEDITATION_CREDITED_QI.ordinal()],
                        values[StrifeSyncField.MEDITATION_COOLDOWN_UNTIL_TICK.ordinal()]);
        return new StrifeData(
                base.dataVersion(),
                (int) values[StrifeSyncField.REALM_ORDINAL.ordinal()],
                (int) values[StrifeSyncField.STAGE.ordinal()],
                (int) values[StrifeSyncField.QI.ordinal()],
                values[StrifeSyncField.LIFESPAN_TICKS.ordinal()],
                values[StrifeSyncField.FLAGS.ordinal()],
                (int) values[StrifeSyncField.SPIRITROOT_QUALITY.ordinal()],
                (int) values[StrifeSyncField.SPIRITROOT_ELEMENTS.ordinal()],
                (int) values[StrifeSyncField.BREAKTHROUGH_ATTEMPTS.ordinal()],
                has(StrifeSyncField.AFFILIATION) ? affiliation : base.affiliation(),
                mergedReputation(base),
                meditation,
                // 丹药/功法/任务/法术状态不在同步清单里（客户端不读原始状态，面板读服务端算好的四因子分解与视图）：镜像保留原值。
                base.pills(),
                base.techniques(),
                base.quests(),
                base.spells());
    }

    private Map<String, Integer> mergedReputation(StrifeData base) {
        if (!has(StrifeSyncField.REPUTATION)) {
            return base.reputation();
        }
        if (!reputationPartial) {
            return reputation;
        }
        Map<String, Integer> merged =
                new HashMap<>(base.reputation() == null ? Map.of() : base.reputation());
        merged.putAll(reputation);
        return merged;
    }

    /**
     * 编码后字节数的上界（docs/03 §5"单包 &lt;32KB"）。按 varint/UTF-8 的最坏宽度算，因此上界成立即真实大小成立： 掩码 2 字节，每个标量字段最坏 10
     * 字节（VAR_LONG），字符串/映射带 5 字节长度前缀。
     */
    public int encodedSizeUpperBound() {
        int size = 2;
        for (StrifeSyncField field : StrifeSyncField.values()) {
            if (field.scalar() && has(field)) {
                size += 10;
            }
        }
        if (has(StrifeSyncField.AFFILIATION)) {
            size += 5 + utf8Length(affiliation);
        }
        if (has(StrifeSyncField.REPUTATION)) {
            size += 5;
            for (Map.Entry<String, Integer> entry : reputation.entrySet()) {
                size += 5 + utf8Length(entry.getKey()) + 5;
            }
        }
        return size;
    }

    /**
     * 按单包预算分片（03 §5：超限必须分片，不许发一个超包）。
     *
     * <p>分片顺序固定：标量 → 势力身份 → 声望向量；声望向量仍然超限时按条目切块，<b>首片整值替换、后续片 upsert</b>，
     * 因此按序施加的结果与一次性施加等价。标量本身不可能超限（10 字段最坏 102 字节），所以不需要再切。返回空列表表示"没有东西要发"。
     */
    public List<StrifeDelta> splitForBudget(int maxBytes) {
        if (isEmpty()) {
            return List.of();
        }
        if (encodedSizeUpperBound() <= maxBytes) {
            return List.of(this);
        }
        List<StrifeDelta> parts = new ArrayList<>();
        StrifeDelta scalars = scalarsOnly();
        if (!scalars.isEmpty()) {
            parts.add(scalars);
        }
        if (has(StrifeSyncField.AFFILIATION)) {
            parts.add(
                    StrifeDelta.of(
                            StrifeSyncField.AFFILIATION.bit(),
                            new long[StrifeSyncField.COUNT],
                            affiliation,
                            Map.of()));
        }
        if (has(StrifeSyncField.REPUTATION)) {
            parts.addAll(splitReputation(maxBytes));
        }
        return List.copyOf(parts);
    }

    /** 只带标量字段的分片。 */
    public StrifeDelta scalarsOnly() {
        long scalarMask = 0L;
        long[] values = new long[StrifeSyncField.COUNT];
        for (StrifeSyncField field : StrifeSyncField.values()) {
            if (field.scalar() && has(field)) {
                scalarMask |= field.bit();
                values[field.ordinal()] = scalarValues[field.ordinal()];
            }
        }
        return scalarMask == 0L ? EMPTY : StrifeDelta.of(scalarMask, values, null, Map.of());
    }

    /** 声望向量的分片：单包装不下就按条目切块；首片替换、其余 upsert，合起来恰好等于整值替换。 */
    private List<StrifeDelta> splitReputation(int maxBytes) {
        List<StrifeDelta> parts = new ArrayList<>();
        Map<String, Integer> chunk = new LinkedHashMap<>();
        int size = 7;
        boolean first = true;
        for (Map.Entry<String, Integer> entry : reputation.entrySet()) {
            int entrySize = 5 + utf8Length(entry.getKey()) + 5;
            if (!chunk.isEmpty() && size + entrySize > maxBytes) {
                parts.add(reputationPart(chunk, first));
                first = false;
                chunk = new LinkedHashMap<>();
                size = 7;
            }
            chunk.put(entry.getKey(), entry.getValue());
            size += entrySize;
        }
        if (!chunk.isEmpty()) {
            parts.add(reputationPart(chunk, first));
        }
        return parts;
    }

    private StrifeDelta reputationPart(Map<String, Integer> entries, boolean replaceFirst) {
        return new StrifeDelta(
                StrifeSyncField.REPUTATION.bit(),
                new long[StrifeSyncField.COUNT],
                null,
                entries,
                !replaceFirst);
    }

    private static int utf8Length(String text) {
        return text == null ? 0 : text.getBytes(StandardCharsets.UTF_8).length;
    }
}

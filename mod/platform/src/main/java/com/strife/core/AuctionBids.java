package com.strife.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

/**
 * 拍卖行密封投标状态（ADR-041 第三批）：全服态，挂 overworld Level 附件（StrifeAttachmentTypes.AUCTION_BIDS） 随 level.dat
 * 持久化——出价不属于任何玩家的 PLAYER_DATA，也不该散进各玩家档。
 *
 * <p>两条平表而非嵌套映射（codec 与结算逻辑都因此简单）：
 *
 * <ul>
 *   <li>{@code bids}：出价流水，ACTIVE → 结算后转 WON/LOST（结算只判 slotIndex &lt; 当前槽的 ACTIVE 行）
 *   <li>{@code payouts}：待领取池行—— lingShi &gt; 0 = 退款行；itemId 非空 = 拍品行。领取即从池中移除
 * </ul>
 *
 * <p>一价密封语义（最高价胜、同价先出价胜、同玩家只最高一笔参与）在 production {@code AuctionBidding.settleSlots}，
 * 本类只管"状态长什么样、怎么存取"。
 */
public record AuctionBids(List<BidEntry> bids, List<PayoutRow> payouts) {

    public static final AuctionBids EMPTY = new AuctionBids(List.of(), List.of());

    public static final Codec<AuctionBids> CODEC =
            RecordCodecBuilder.create(
                    inst ->
                            inst.group(
                                            BidEntry.CODEC
                                                    .listOf()
                                                    .fieldOf("bids")
                                                    .forGetter(AuctionBids::bids),
                                            PayoutRow.CODEC
                                                    .listOf()
                                                    .fieldOf("payouts")
                                                    .forGetter(AuctionBids::payouts))
                                    .apply(inst, AuctionBids::new));

    /** 一笔密封出价。status 只取 ACTIVE / WON / LOST（结算终态，不再流转）。 */
    public record BidEntry(
            long slotIndex,
            String bidderId,
            String bidderName,
            String lotId,
            int amount,
            long bidGameTime,
            String status) {

        public static final String ACTIVE = "active";
        public static final String WON = "won";
        public static final String LOST = "lost";

        public static final Codec<BidEntry> CODEC =
                RecordCodecBuilder.create(
                        inst ->
                                inst.group(
                                                Codec.LONG
                                                        .fieldOf("slot")
                                                        .forGetter(BidEntry::slotIndex),
                                                Codec.STRING
                                                        .fieldOf("bidder")
                                                        .forGetter(BidEntry::bidderId),
                                                Codec.STRING
                                                        .fieldOf("name")
                                                        .forGetter(BidEntry::bidderName),
                                                Codec.STRING
                                                        .fieldOf("lot")
                                                        .forGetter(BidEntry::lotId),
                                                Codec.INT
                                                        .fieldOf("amount")
                                                        .forGetter(BidEntry::amount),
                                                Codec.LONG
                                                        .fieldOf("bid_time")
                                                        .forGetter(BidEntry::bidGameTime),
                                                Codec.STRING
                                                        .fieldOf("status")
                                                        .forGetter(BidEntry::status))
                                        .apply(inst, BidEntry::new));
    }

    /** 一行待领取：灵石退款（itemId 空）或拍品（itemId 非空，count 为数量）。 */
    public record PayoutRow(String playerId, int lingshi, String itemId, int itemCount) {

        public static final Codec<PayoutRow> CODEC =
                RecordCodecBuilder.create(
                        inst ->
                                inst.group(
                                                Codec.STRING
                                                        .fieldOf("player")
                                                        .forGetter(PayoutRow::playerId),
                                                Codec.INT
                                                        .fieldOf("lingshi")
                                                        .forGetter(PayoutRow::lingshi),
                                                Codec.STRING
                                                        .fieldOf("item")
                                                        .forGetter(PayoutRow::itemId),
                                                Codec.INT
                                                        .fieldOf("count")
                                                        .forGetter(PayoutRow::itemCount))
                                        .apply(inst, PayoutRow::new));
    }

    public AuctionBids {
        bids = bids == null ? List.of() : List.copyOf(bids);
        payouts = payouts == null ? List.of() : List.copyOf(payouts);
    }
}

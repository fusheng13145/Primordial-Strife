package com.strife.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.strife.core.AuctionBids;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 密封投标结算核（ADR-041）×纯函数：一价密封、同价先出价胜、同玩家只最高一笔参与、败者全额退款。 结算碰的是玩家灵石——这里的每条规则都是钱，错一条就是经济事故。 */
class AuctionBiddingTest {

    private static AuctionBids.BidEntry bid(
            long slot, String player, String name, int amount, long time) {
        return new AuctionBids.BidEntry(
                slot, player, name, "auction_xuanyu", amount, time, AuctionBids.BidEntry.ACTIVE);
    }

    @Test
    void highestBidWinsAndPaysOwnBid() {
        var bids =
                List.of(
                        bid(0, "a", "甲", 12, 100L),
                        bid(0, "b", "乙", 15, 200L),
                        bid(0, "c", "丙", 13, 300L));
        var result = AuctionBidding.settleSlots(bids, 1);

        var won =
                result.bids().stream()
                        .filter(b -> b.bidderId().equals("b"))
                        .findFirst()
                        .orElseThrow();
        assertEquals(AuctionBids.BidEntry.WON, won.status());
        assertEquals(15, won.amount(), "一价密封：胜者按自己的出价成交");
        // 胜者入池拍品行；败者入池退款行
        assertEquals(
                1,
                result.payouts().stream().filter(r -> r.itemId().equals("auction_xuanyu")).count());
        assertEquals(2, result.payouts().stream().filter(r -> r.lingshi() > 0).count(), "两个败者退款");
        assertTrue(
                result.payouts().stream()
                        .anyMatch(r -> r.playerId().equals("a") && r.lingshi() == 12));
        assertTrue(
                result.payouts().stream()
                        .anyMatch(r -> r.playerId().equals("c") && r.lingshi() == 13));
    }

    @Test
    void tieGoesToTheEarliestBid() {
        var bids = List.of(bid(0, "a", "甲", 15, 500L), bid(0, "b", "乙", 15, 300L));
        var result = AuctionBidding.settleSlots(bids, 1);

        var won =
                result.bids().stream()
                        .filter(b -> b.bidderId().equals("b"))
                        .findFirst()
                        .orElseThrow();
        assertEquals(AuctionBids.BidEntry.WON, won.status(), "同价先出价者得（乙 300L 早于甲 500L）");
        assertTrue(
                result.bids().stream()
                        .filter(b -> b.bidderId().equals("a"))
                        .allMatch(b -> AuctionBids.BidEntry.LOST.equals(b.status())));
        assertTrue(
                result.payouts().stream()
                        .anyMatch(r -> r.playerId().equals("a") && r.lingshi() == 15));
    }

    @Test
    void samePlayerBidsCollapseToHighestWithRefund() {
        var bids = List.of(bid(0, "a", "甲", 10, 100L), bid(0, "a", "甲", 16, 200L));
        var result = AuctionBidding.settleSlots(bids, 1);

        // 两笔都终态化：16 的 WON、10 的 LOST+退款
        var won = result.bids().stream().filter(b -> b.amount() == 16).findFirst().orElseThrow();
        assertEquals(AuctionBids.BidEntry.WON, won.status());
        var lost = result.bids().stream().filter(b -> b.amount() == 10).findFirst().orElseThrow();
        assertEquals(AuctionBids.BidEntry.LOST, lost.status());
        assertTrue(
                result.payouts().stream()
                        .anyMatch(r -> r.playerId().equals("a") && r.lingshi() == 10),
                "被覆盖的出价原路退款");
    }

    @Test
    void unelapsedSlotsAreUntouched() {
        var active = bid(5, "a", "甲", 15, 100L);
        var settled =
                new AuctionBids.BidEntry(
                        1, "b", "乙", "auction_xuanyu", 15, 100L, AuctionBids.BidEntry.WON);
        var result = AuctionBidding.settleSlots(List.of(active, settled), 5);

        assertEquals(2, result.bids().size());
        assertTrue(
                result.bids().stream()
                        .allMatch(
                                b ->
                                        AuctionBids.BidEntry.ACTIVE.equals(b.status())
                                                || AuctionBids.BidEntry.WON.equals(b.status())),
                "当前槽 ACTIVE 与历史 WON 都原样保留");
        assertTrue(result.payouts().isEmpty(), "无过槽出价 = 无结算产物");
    }

    @Test
    void validateBidEnforcesCurrentLotAndFloor() {
        var lot = new AuctionHouse.Lot("auction_xuanyu", "item_xuanyu", 20, 10, 2);
        assertNull(AuctionBidding.validateBid(lot, true, 10), "底价即准入");
        assertNull(AuctionBidding.validateBid(lot, true, 20));
        assertEquals("（出价低于底价：底 10 灵石）", AuctionBidding.validateBid(lot, true, 9));
        assertTrue(AuctionBidding.validateBid(lot, false, 15).contains("不在本槽"));
        assertTrue(AuctionBidding.validateBid(null, false, 15).contains("拍品不存在"), "未知拍品给出可读拒绝");
    }
}

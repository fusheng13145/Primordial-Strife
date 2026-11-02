package com.strife.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mojang.serialization.DataResult;
import java.util.List;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

/** 拍卖投标状态（ADR-041）的存取契约：往返一致是底线——丢一笔出价 = 玩家灵石蒸发。 */
class AuctionBidsTest {

    private static Tag encode(AuctionBids state) {
        DataResult<Tag> result = AuctionBids.CODEC.encodeStart(NbtOps.INSTANCE, state);
        return result.result().orElseThrow(() -> new AssertionError("encode failed: " + result));
    }

    private static AuctionBids decode(Tag tag) {
        DataResult<AuctionBids> result =
                AuctionBids.CODEC.decode(NbtOps.INSTANCE, tag).map(pair -> pair.getFirst());
        return result.result().orElseThrow(() -> new AssertionError("decode failed: " + result));
    }

    @Test
    void roundTripPreservesBidsAndPayouts() {
        AuctionBids original =
                new AuctionBids(
                        List.of(
                                new AuctionBids.BidEntry(
                                        3L,
                                        "uuid-a",
                                        "散人甲",
                                        "auction_xuanyu",
                                        15,
                                        120500L,
                                        AuctionBids.BidEntry.ACTIVE),
                                new AuctionBids.BidEntry(
                                        2L,
                                        "uuid-b",
                                        "散人乙",
                                        "auction_xuanyu",
                                        18,
                                        100200L,
                                        AuctionBids.BidEntry.WON)),
                        List.of(
                                new AuctionBids.PayoutRow("uuid-b", 0, "auction_xuanyu", 1),
                                new AuctionBids.PayoutRow("uuid-a", 15, "", 0)));

        AuctionBids decoded = decode(encode(original));

        assertEquals(original, decoded);
        assertEquals(2, decoded.bids().size());
        assertEquals(2, decoded.payouts().size());
    }

    @Test
    void emptyStateRoundTrips() {
        assertEquals(AuctionBids.EMPTY, decode(encode(AuctionBids.EMPTY)));
    }

    @Test
    void statusConstantsAreTheCodecValues() {
        // 状态字符串是持久化契约的一部分（表里存的就是这三个词）——改名 = 旧档出价全变 LOST
        assertEquals("active", AuctionBids.BidEntry.ACTIVE);
        assertEquals("won", AuctionBids.BidEntry.WON);
        assertEquals("lost", AuctionBids.BidEntry.LOST);
    }
}

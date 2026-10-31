package com.strife.clientfx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 轮盘几何：四张卡必须落在上/右/下/左，点击命中只认卡片本身，不认卡片之间的空隙。 */
class SpellWheelLayoutTest {

    @Test
    void fourCardsSitAtTopRightBottomLeft() {
        List<SpellWheelLayout.Card> cards = SpellWheelLayout.cards(4, 200, 150, 100, 34, 72);

        assertEquals(4, cards.size());
        assertTrue(cards.get(0).centerY() < 150, "第 1 张应在中心上方");
        assertTrue(cards.get(1).centerX() > 200, "第 2 张应在中心右侧");
        assertTrue(cards.get(2).centerY() > 150, "第 3 张应在中心下方");
        assertTrue(cards.get(3).centerX() < 200, "第 4 张应在中心左侧");
        assertEquals(
                4, cards.stream().map(card -> card.left() + ":" + card.top()).distinct().count());
    }

    @Test
    void hitTestFindsTheCardUnderTheCursor() {
        List<SpellWheelLayout.Card> cards = SpellWheelLayout.cards(4, 200, 150, 100, 34, 72);

        assertEquals(0, SpellWheelLayout.hitTest(cards, 200, 61));
        assertEquals(1, SpellWheelLayout.hitTest(cards, 289, 150));
        assertEquals(-1, SpellWheelLayout.hitTest(cards, 200, 150), "中心空档不应命中任何卡");
    }

    @Test
    void zeroCardsProduceAnEmptyWheel() {
        assertTrue(SpellWheelLayout.cards(0, 200, 150, 100, 34, 72).isEmpty());
    }
}

package com.strife.clientfx;

import java.util.ArrayList;
import java.util.List;

/** 施法轮盘的卡片位置与命中判定（与渲染分开，便于单测几何）。 */
public final class SpellWheelLayout {

    public record Card(int index, int left, int top, int width, int height) {
        public int centerX() {
            return left + width / 2;
        }

        public int centerY() {
            return top + height / 2;
        }

        public boolean contains(double x, double y) {
            return x >= left && x < left + width && y >= top && y < top + height;
        }
    }

    private SpellWheelLayout() {}

    static List<Card> cards(
            int count, int centerX, int centerY, int width, int height, int radius) {
        List<Card> cards = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double angle = Math.toRadians(-90.0 + 360.0 * i / count);
            int cardCenterX = (int) Math.round(centerX + Math.cos(angle) * radius);
            int cardCenterY = (int) Math.round(centerY + Math.sin(angle) * radius);
            cards.add(
                    new Card(i, cardCenterX - width / 2, cardCenterY - height / 2, width, height));
        }
        return List.copyOf(cards);
    }

    static int hitTest(List<Card> cards, double x, double y) {
        for (Card card : cards) {
            if (card.contains(x, y)) {
                return card.index();
            }
        }
        return -1;
    }
}

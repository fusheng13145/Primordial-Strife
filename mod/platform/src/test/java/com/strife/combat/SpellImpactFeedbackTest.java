package com.strife.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 命中反馈的元素映射（P1-09）用例：五行各映射到非空的原版粒子/音效，未知元素回落默认不抛错。
 *
 * <p>"无音频/缺库不崩"的契约：映射表只取 vanilla 注册表常量（编译期存在，无资源加载路径）； 用例把"每个已知元素都有反馈、每个未知元素都有安全默认"钉住。
 */
class SpellImpactFeedbackTest {

    @Test
    void everyElementMapsToACompleteImpact() {
        for (String element : new String[] {"jin", "mu", "shui", "huo", "tu"}) {
            SpellImpactFeedback.Impact impact = SpellImpactFeedback.forElement(element);
            assertNotNull(impact, element + " 必须有反馈");
            assertNotNull(impact.particle(), element + " 必须有粒子");
            assertNotNull(impact.sound(), element + " 必须有音效");
            assertTrue(impact.volume() > 0 && impact.volume() <= 2.0f, element + " 音量越界");
            assertTrue(impact.pitch() >= 0.5f && impact.pitch() <= 2.0f, element + " 音调越界");
        }
    }

    @Test
    void elementsAreVisuallyDistinct() {
        long distinct =
                java.util.Arrays.stream(new String[] {"jin", "mu", "shui", "huo", "tu"})
                        .map(SpellImpactFeedback::forElement)
                        .map(SpellImpactFeedback.Impact::particle)
                        .distinct()
                        .count();
        assertEquals(5, distinct, "五行粒子必须两两可辨——否则元素反馈名存实亡");
    }

    @Test
    void unknownElementFallsBackToNeutralDefaultWithoutThrowing() {
        for (String bad : new String[] {"", "unknown_el", "HUO"}) {
            SpellImpactFeedback.Impact impact = SpellImpactFeedback.forElement(bad);
            assertNotNull(impact, "未知元素 '" + bad + "' 不得抛错");
            assertNotNull(impact.particle());
            assertNotNull(impact.sound());
        }
        // 大小写敏感：element 是内容 ID 词表（JSON_SCHEMA §1.4 全小写），"HUO" 不算已知元素。
        assertEquals(
                SpellImpactFeedback.forElement(""),
                SpellImpactFeedback.forElement("HUO"),
                "未知元素应统一走中性默认");
    }

    @Test
    void knownElementDoesNotFallBackToDefault() {
        assertFalse(
                SpellImpactFeedback.forElement("huo").equals(SpellImpactFeedback.forElement("")),
                "已知元素不得回落默认——那说明映射表没接上");
    }
}

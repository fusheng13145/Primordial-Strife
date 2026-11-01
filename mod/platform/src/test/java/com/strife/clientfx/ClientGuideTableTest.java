package com.strife.clientfx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 客户端指引表（P1-21）的契约用例。
 *
 * <p>与命令通道同读一份产物（D12 ④）——这里钉住客户端视角的五域顺序、每域下限与 顺序稳定（面板翻页按此渲染）。
 */
class ClientGuideTableTest {

    @Test
    void sectionTitlesFollowD12Order() {
        var keys = List.copyOf(ClientGuideTable.SECTION_TITLES.keySet());
        assertEquals(
                java.util.List.of("realm", "cultivation", "combat", "production", "quest"), keys);
    }

    @Test
    void bySectionGroupsEveryEntryAndKeepsOrder() {
        // 产物已由 GuideLibraryTest 钉内容；这里只钉分组器行为（合成输入，客户端无产物夹具需求）。
        var all =
                java.util.List.of(
                        new ClientGuideTable.Entry("combat", "战斗", "guide_combat_a", "a", "t"),
                        new ClientGuideTable.Entry("realm", "境界", "guide_realm_a", "r", "t"),
                        new ClientGuideTable.Entry("combat", "战斗", "guide_combat_b", "b", "t"));
        var grouped = ClientGuideTable.bySection(all);
        assertEquals(2, grouped.get("combat").size(), "同域条目聚合");
        assertEquals(1, grouped.get("realm").size());
        assertEquals("guide_combat_a", grouped.get("combat").get(0).id(), "组内保持产物顺序");
        assertTrue(grouped.get("quest").isEmpty(), "缺域给空组（面板渲染跳过）");
    }

    @Test
    void entriesCarryChineseSectionTitles() {
        assertEquals("境界", ClientGuideTable.SECTION_TITLES.get("realm"));
        assertEquals("任务", ClientGuideTable.SECTION_TITLES.get("quest"));
    }
}

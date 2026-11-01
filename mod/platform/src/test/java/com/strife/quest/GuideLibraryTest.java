package com.strife.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 入门指引 ×【真实产物】的契约用例（P1-21，D12）。
 *
 * <p>验收口径：无依赖环境全指引可达——五域齐备、每域 ≥3 条、正文 ≤40 汉字（LORE §3 语域）。 命令通道与客户端面板同读这份产物，这里钉住"产物即真相"。
 */
class GuideLibraryTest {

    @Test
    void fiveSectionsInD12Order() {
        var keys = List.copyOf(GuideLibrary.sections().keySet());
        assertEquals(
                List.of("realm", "cultivation", "combat", "production", "quest"),
                keys,
                "五域顺序即 D12 口径，缺域或改名都是门禁级问题");
    }

    @Test
    void everySectionCarriesAtLeastThreeEntries() {
        for (var e : GuideLibrary.sections().entrySet()) {
            assertTrue(e.getValue().size() >= 3, e.getKey() + " 域至少 3 条（P1-21 验收）");
        }
    }

    @Test
    void entriesStayWithinTheRegisterLimit() {
        for (var e : GuideLibrary.sections().entrySet()) {
            for (GuideLibrary.Entry entry : e.getValue()) {
                int hanzi = entry.text().replaceAll("[^\u4e00-\u9fff]", "").length();
                assertTrue(hanzi <= 40, e.getKey() + "/" + entry.id() + " 超 40 汉字：" + hanzi);
                assertTrue(!entry.title().isBlank() && !entry.text().isBlank());
            }
        }
    }

    @Test
    void marketGuideMentionsTheCommandChannel() {
        // D-P0-2：指引必须指向命令通道（无库/无面板环境全可达的关键一环）。
        var production = GuideLibrary.sections().get("production");
        assertTrue(
                production.stream().anyMatch(e -> e.text().contains("/strife market")),
                "生产域必须有一条指向 /strife market（D12 ④ 命令永久保留口径）");
    }
}

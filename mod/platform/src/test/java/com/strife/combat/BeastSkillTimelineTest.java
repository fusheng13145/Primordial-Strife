package com.strife.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 妖兽技能纯时序机（P1-10）的用例：蓄力 12 拍 → 释放恰好一次 → 冷却 80 拍。
 *
 * <p>为什么抽纯类：{@code Goal.tick()} 只在目标运行期被调——冷却若靠 tick 递减，释放后目标停转就永远不再转 （"一生一次"缺陷，2026-11-01 读码发现）。绝对
 * tick 判定与实体解耦，单测不需要 {@code Level}。 参数用真实默认（12/80）钉口径；数值本身仍是 NUMBERS [拟] 口径的硬编码占位，改动随 G-5。
 */
class BeastSkillTimelineTest {

    private final BeastSkillTimeline timeline = new BeastSkillTimeline(12, 80);

    @Test
    void windupReleasesExactlyOnceOnTheTwelfthTick() {
        assertTrue(timeline.tryStart(0), "冷却未起时应可起手");
        assertTrue(timeline.winding());

        for (long now = 1; now <= 11; now++) {
            assertEquals(
                    BeastSkillTimeline.TickResult.CHARGING,
                    timeline.tick(now),
                    "第 " + now + " 拍应仍在蓄力");
        }
        assertEquals(BeastSkillTimeline.TickResult.RELEASE, timeline.tick(12), "第 12 拍释放，且只此一拍");
        assertEquals(BeastSkillTimeline.TickResult.IDLE, timeline.tick(13), "释放不得重复结算");
        assertFalse(timeline.winding());
    }

    @Test
    void releaseWithoutCommitDoesNotEnterCooldown() {
        // 目标在蓄力中死亡：release 时 target==null，goal 不 commit 冷却——原语义（可立即重试）必须保留。
        timeline.tryStart(0);
        assertEquals(BeastSkillTimeline.TickResult.RELEASE, timeline.tick(12));

        assertTrue(timeline.tryStart(13), "未 commit 冷却时允许立刻再次起手（目标已换）");
    }

    @Test
    void committedCooldownBlocksUntilEightyTicksElapse() {
        timeline.tryStart(0);
        assertEquals(BeastSkillTimeline.TickResult.RELEASE, timeline.tick(12));
        timeline.commitCooldown(12);

        assertFalse(timeline.tryStart(13), "冷却第 1 拍不得起手");
        assertFalse(timeline.offCooldown(12 + 79), "冷却第 79 拍仍未结束");
        assertTrue(timeline.offCooldown(12 + 80), "冷却第 80 拍整点恢复");
        assertTrue(timeline.tryStart(12 + 80), "冷却结束后可再次蓄力（修复一生一次缺陷）");
    }

    @Test
    void abortCancelsWindupWithoutCooldown() {
        // 蓄力被打断（目标丢失/Goal 被让位）：不释放、不进冷却，可立即重试——与改前行为等价。
        timeline.tryStart(0);
        timeline.abort();

        assertFalse(timeline.winding());
        assertEquals(BeastSkillTimeline.TickResult.IDLE, timeline.tick(12), "中断后原释放点不得再结算");
        assertTrue(timeline.tryStart(1), "中断后立即可重试");
    }

    @Test
    void cannotStartWhileAlreadyWinding() {
        assertTrue(timeline.tryStart(0));
        assertFalse(timeline.tryStart(1), "蓄力中重复起手是状态污染");
    }

    @Test
    void offCooldownStartsTrueAtSpawn() {
        assertTrue(timeline.offCooldown(0), "刚出生的妖兽技能应可用");
    }
}

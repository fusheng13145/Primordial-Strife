# STORY.md — 世界观总纲与章节大纲

> **状态：Agent 初稿（序章可审、第一章骨架可审、第二章起仅登记主题），待 C 名义确认。** 结构按 05 §6：世界观总纲 → 各章主题/场景/关键 NPC/情感曲线 → 任务节点骨架。
>
> 标注口径同 NUMBERS.md：`[锚]` = 手册已定；`[拟]` = 初稿提案；`[占位]` = 主题登记、内容后补。
>
> **冻结时点 `[锚]`（05 §6 O2）**：序章大纲 M2 末产出、M3 前定稿冻结；第一章允许 M3 滚动定稿，但**节点骨架必须先冻结再填对话文本**。本文件就是"骨架"的载体：`## 3` 之后的 quest ID 一旦进 `tables/quests_prologue.csv` 并合入，改名即破档（04 §4）。
>
> 与设定卡的关系：所有专名（区域/势力/NPC）以 `content/LORE.md` 为准，本文件只引用卡 ID，不重新定义。

## 1. 世界观总纲（写作依据，不直接进游戏文本）

**玄黄之世，万族争劫。** 天道不设裁判，只给两样东西：寿元，和境界之劫。修仙者与之相争的方式也不是打赢天道，而是**在有限的寿元里换一个位置**——从被灵脉分配的人，变成参与分配的人。

三条世界公理（所有剧情判断回到这里）：

1. **寿元是货币，也是倒计时**（`[锚]` 01 §7 核心循环、03 §8 大限）。青石观不养闲人，不是刻薄，是观里只剩一个筑基圆满的长老在撑着两条人命的份额。
2. **灵脉不可再生，所以一切冲突都是地理冲突**（`[拟]`，LORE §2）。改道、衰败、争夺——第一章的全部戏剧性来自"此地灵气不再够分"。
3. **世界不为玩家而转，但为玩家留门**（`[锚]` 05 §6 主题基调）。事变按 H5 时钟与 H7 声明表自己推进；玩家能做的是在其中择路（入观／散修／借力）。

**玩家幻想**（`[拟]`，一句话，验收用）：一个灵根随机的凡人，用有限寿元换到"我有资格选择站在哪一边"的位置。
**反幻想**（明确不卖的东西）：天选之子、系统面板告诉我该杀谁、世界因我而生。

## 2. 情感曲线（序章 45 分钟，`[锚]` 01 §6 成功标准）

| 段 | 目标情绪 | 承载手段（不写代码，只写体验） |
|---|---|---|
| 0–3 分钟 | 无措 | 进服即见寿元与灵根：凡人、80 年、五行杂灵根（服务端签发一次，03 §8）。没有"欢迎导师"长篇，只有一块山碑刻着历年观中弟子的寿数 |
| 3–10 分钟 | 第一次「有进展」 | 打坐出修为（`quest_prologue_meditation_01`），面板三项解锁（`meditation`/`spiritroot_panel`/`cultivation_panel`） |
| 10–22 分钟 | 「我需要一个身份」 | 采集—交付—入门引气（`[拟]` 与苏药农的世俗牵系：他给材料、给灵石、也给"你可以不入观"的选项） |
| 22–32 分钟 | 尝到代价 | 石执事卡关百年的对话（第一次让玩家算清寿元账），突破失败的可预期后果（面板显示本次成功率与回退，05 §4 设计原则） |
| 32–42 分钟 | 押注 | 筑基突破：材料自备 + 成功率 0.90 + 失败不清零（`[锚]` D6 非破坏性代价） |
| 42–45 分钟 | 择路 | 吴长老"山门不养闲人"→ 立 `affiliation`（青石观弟子／散修两条文本，H2），第一章入口开放 |

关键纪律（`[锚]` 05 §7）：段 3、段 5、段 6 的对话文本属于"情感关键节点"，**首版骨架文本必须人工重写**后才能进 M3 冻结。

## 3. 章节总表

| 章 | 主题 | 场景 | 境界跨度 | 交付 | 状态 |
|---|---|---|---|---|---|
| `prologue` 序章 | 寿元与入门 | `qi_luoxia` 落霞山麓 | 凡人 → 筑基 | M3 | 本文件 §4 骨架可审 |
| `ch1` 第一章 | 灵脉改道 | `qi_wenjiang` 文津渡 / `qi_shiguo` 石过滩 | 筑基 → 金丹 | M3–M4 滚动定稿 | §5 骨架 `[拟]` |
| `ch2` 第二章 | **五行灵珠**（`[锚]` 04 §3 主题已定） | `qi_yunmeng` 云梦泽外围 | 金丹 → 元婴 | M5 起 | §6 仅登记 |
| `ch3+` | `[占位]` | — | 化神及以后 | — | 随 M4 末评审；**H7 战争类条目（人妖大战/外敌）必须在 ch4+ 以声明表承载**（09 D、ADR-016） |

## 4. 序章节点骨架（`quest_prologue_*`，与 `tables/quests_prologue.csv` 一一对应）

字段名以 `content/JSON_SCHEMA.md` §4.6 为准；`解锁` 列指 `rewards.unlock`，值取 NUMBERS §1 的 `unlock_key`。

| # | quest id | 目标（`objective_type` / target） | 前置 | 奖励 | 时长 | 备注 |
|---|---|---|---|---|---|---|
| 1 | `quest_prologue_root_read_01` | `talk` / `npc_qingshi_zhizhi` | —（`entry: true`） | flag `fac_qingshi:prologue:met_elder` | 2′ | 服务端已生成灵根，本节点只教面板读法 |
| 2 | `quest_prologue_meditation_01` | `sit` / 累计 60 刻（`[拟]`，NUMBERS §5 结算粒度 40 刻） | 1 | `qi` 40；unlock `meditation`,`cultivation_panel` | 4′ | 手册 04 §4 的示例 ID 原样采用 `[锚]` |
| 3 | `quest_prologue_herb_pick_01` | `collect` / `item_ningxu` ×6（`[拟]` 宁须，LORE 未定名→待 C 定） | 2 | `item` ×灵石 3 | 5′ | 苏药农线，第一次"世界有别人在活" |
| 4 | `quest_prologue_deliver_01` | `deliver` / `npc_yaonong_su`（支持末影箱内交付，`[锚]` 03 §8） | 3 | `qi` 60；`technique` 入门引气诀 `tech_qingxin_jue`（`[锚]` 04 §4 示例 ID） | 4′ | 功法只给，**装备在筑基后才可用**（`technique_equip` 属 `zhuji` 解锁） |
| 5 | `quest_prologue_breakthrough_qi_01` | `breakthrough` / `fanren→qili`（`bs_fanren_qili` 0.95） | 2 且 `qi>=100` | unlock `spiritroot_panel`；flag `prologue:first_qi` | 3′ | 近乎必成：入门不卡人（NUMBERS §3 设计口径） |
| 6 | `quest_prologue_elder_story_01` | `talk` / `npc_qingshi_zhizhi` | 5 | flag `fac_qingshi:prologue:saw_debt` | 3′ | 石执事卡关百年的寿元账；情感关键节点，人工重写 |
| 7 | `quest_prologue_nine_layers_01` | `sit` + `kill`（山麓妖兽，一阶） | 6 | `qi` 累加至 230 上限的 90%；`item` 妖材 ×2 | 10′ | 打坐 + 战斗首次并置；`survive` 类目标留 ch1 |
| 8 | `quest_prologue_prepare_zhuji_01` | `craft` 类前置：集齐 `pill_yanshou` 材料（**只采不炼**，`[拟]`）+ 突破台就位 | 7 | flag `prologue:ready_to_zhuji` | 6′ | `pill_crafting` 属筑基解锁，所以序章只能备料——用规则本身教学 |
| 9 | `quest_prologue_breakthrough_zhuji_01` | `breakthrough` / `qili→zhuji`（`bs_qili_zhuji` 0.90，失败回退 60–80%） | 8 | unlock `technique_equip`,`spell_cast`,`pill_crafting` | 6′ | 押注段；面板必须预告失败后果 |
| 10 | `quest_prologue_sect_choice_01` | `talk` / `npc_taishang_wu` → 二选一 `option` | 9 | `affiliation` = `fac_qingshi` \| 空（散修）；`ch1` 入口 | 3′ | H2 落地的第一个选择，两条文本；择路而非旁观（05 §6） |

骨架不变式（提交前自检，Validator 会硬查 `V-DAG`）：单入口（#1 `entry:true`）、无环、全部必做节点从 #1 可达、`timer` 全为 null（序章不限时）、每节点奖励引用的 ID 存在于其他表或 NUMBERS 键。

## 5. 第一章骨架（`quest_ch1_*`，`[拟]`，允许 M3 滚动定稿）

**主题**：灵脉改道让"石过滩"从有人要变成没人要。玩家第一次看见**势力如何对待资源**：青石观要保住份额，月来渡靠消息与雇佣活下来，而两边都不肯先付账。

- 场景：`qi_wenjiang` → `qi_shiguo`；NPC：`npc_yuelai_deng`（挑灯人）为主线，`npc_taishang_wu` 在观内继续施压。
- 情感曲线三段（`[拟]`）：**借势**（散修身份可卖消息给两家）→ **担责**（改道后果落到药农聚落，苏药农线回收）→ **越界**（第一次介入他人因果，写 H3 债务/仇杀记录，为心魔与通缉留素材）。
- 节点骨架（10 个左右，粒度与序章一致）：`ch1_pingcang_survey_01`（勘脉，`reach`+`collect`）→ `ch1_pingcang_offer_01`（对话树 `dlg_ch1_offer`，条件 DSL 含 `affinity(tu)` 与 `reputation(fac_qingshi>=-10)`）→ `ch1_pingcang_debt_01`（`causality` 写入债务）→ `ch1_pingcang_raid_01`（`kill` 妖兽群，掉落走 `loot_quest_ch1_*`）→ `ch1_pingcang_reckon_01`（`survive` 限时守台，第一次真用 `timer`/`fail_goto`）→ `ch1_jindan_gate_01`（筑基圆满条件 + 材料 → 金丹突破）。
- 与主线钩子：第一章**不**展开战争，只在 `flag`（`fac_qingshi:ch1:war_rumor`）里为 ch3+ 的 H7 剧本埋引用。
- 待审：第一章是否要求玩家必须与青石观保持正声望（若是，`affiliation` 散修路线的 ch1 文本量翻倍，需 C 与 A 评估工期）。

## 6. 第二章登记（主题 `[锚]`，其余 `[占位]`）

主题：**五行灵珠**（04 §3）。方向 `[拟]`：五行各一珠，落在 `qi_yunmeng` 外围的五处灵气极端区；每珠附带一次灵根/亲和玩法化验证（`element` 五档），把 NUMBERS §6 的亲和规则变成剧情语言。关键节点：圆满后化神前的"道心一问"（对话树承载，不新增机制）。

## 7. 与实现层的对应关系（供 B/A 核对，不是需求单）

| 剧情需要 | 平台层已承诺的承载 |
|---|---|
| 灵根随机、不可重算 | 03 §8 服务端 seeded by UUID + 世界 seed |
| 择路身份 | H2 `affiliation` / `reputation`（core 附件，已在 `StrifeData`） |
| 因果与债务 | H3 flag 命名空间 + `quests.causality` |
| 突破不卡死 | 03 §9 兜底命令（`/strife realm set`、`/strife lifespan set`，M1 落地） |
| 限时事件 | H5 `strife_periods`（表结构已定，本期 ch1 的 `timer` 用任务内计时，不依赖全局时钟） |
| 大战 | H7 声明式冲突表（`strife_wars`，`form` 两形态同表） |

## 8. 待审清单（C 确认后本节删除）

1. §4 #3 的药材 `item_ningxu` 名称与 §4 全部 `[拟]` 数值前置（如 `qi>=100`）——数值只能进 NUMBERS，剧情文本里我用了引用式写法，需确认落成表键。
2. §2 45 分钟预算：段 7 的 10 分钟打坐 + 战斗是否偏紧（与 NUMBERS §1 的 `sit_rate` 曲线强耦合，若 A 判偏慢则先改 §4 而非改数值表）。
3. §4 #10 的两条择路文本量（散修路线是否复用青石观文本）→ 工期影响，需 C 估。
4. 后三段境界名（`lianxu` / `heti` / `dujie`）是否沿用（05 §3 标为暂定，NUMBERS §1 已按此登记；改名 = 破档）。
5. §5 声望门槛是否强制；`alignment` 枚举四值（`orthodox/demonic/neutral/yao`）够用否——与 JSON_SCHEMA §5.2 同源。
6. ch2 起是否引入"多章并行可选"（DAG 跨章并联会挑战 `V-DAG` 的单入口不变式，需要 A 先定策略）。

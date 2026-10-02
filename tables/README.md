# tables/ — 策划填表区（DataGen 输入）

CSV 是唯一入口：UTF-8、首行表头、以 `_` 开头的列视为注释列（docs/04 §5）。

模板与填写说明 `FILLING_GUIDE.md`（人话版，含逐列说明、格式示例、报错读法、待确认清单）
是内容作者的第一份必读。**填表前先读它的 §0**——那里讲清了"现在填表能走多远"。

**已接生成器的表**（2026-10-02 实测：`datagen: tables=17 rows=79 generators=11 numbers=5 products=30 problems=0`）：
11 个生成器实例覆盖 9 张 CSV（quests 与 dialog_trees / dialog_text 各按章注册为独立实例），
另有 5 个 NUMBERS 直出域（realms / realm_rules / world_rules / core_rules / combat_rules）。
往**没有**生成器的表里填行，`./gradlew :tools:datagen:run` 会直接报错并指名这张表
（刻意如此，不让内容静默消失）——所以下表"本期填不填"必须看"生成器"那一列。

表结构（列名、字段语义、破档项）由 `content/JSON_SCHEMA.md` 约定（成员 C 起草、A 终审）。
**改表头 = 改契约 = 破档风险**，必须先改 SCHEMA 并在同一提交里对齐 DataGen 与 Validator；
不要自行加列、删列、改列名。

## 模板清单

| 文件 | 对应契约 | 生成器 | 行数 | 本期填不填 |
|---|---|---|---|---|
| `FILLING_GUIDE.md` | — | — | — | 先读这个（§0 讲清了"现在填表会怎样"） |
| `factions.csv` | §5.2 | **已接**（→ `strife_factions/`） | 2 | 已填（取值依据 LORE §5 初稿） |
| `techniques.csv` | §4.2 | **已接**（→ `strife_techniques/`） | 5 | 已填（C1-1 基础功法 5 部） |
| `spells.csv` | §4.3 | **已接**（→ `strife_spells/`） | 3 | 已填（C1-1 入门法术 3 部） |
| `pills.csv` | §4.4 | **已接**（→ `strife_pills/`） | 3 | 已填（C1-1 入门丹方 3 张） |
| `artifacts.csv` | §4.5 | **已接**（→ `strife_artifacts/`） | 0 | 炼器玩法未做（G-3），生成器在库等表 |
| `quests_prologue.csv` | §4.6 | **已接**（→ `strife_quests/prologue.json`） | 10 | 已填（STORY §4 十节点转译） |
| `quests_ch1.csv` | §4.6 | **已接**（→ `strife_quests/ch1.json`） | 6 | 已填（STORY §5 骨架，W4 演练产出） |
| `dialog_trees_prologue.csv` | §4.7 | **已接**（→ `dialog_trees/prologue.json`） | 4 | 已填（4 棵树，门链/交付/风味/择宗） |
| `dialog_trees_ch1.csv` | §4.7 | **已接**（→ `dialog_trees/ch1.json`） | 0 | W4 演练刻意留空（不制造未审定内容） |
| `dialog_prologue_text.csv` | §4.10 | **已接**（→ `dialog_text/prologue.json`） | 41 | 已填（正文 + 双变体 + 设定引用） |
| `dialog_ch1_text.csv` | §4.10 | **已接**（→ `dialog_text/ch1.json`） | 0 | 文本由 C 按 05 §7 流程产出 |
| `spirit_field.csv` | §4.8 | ⬜ **无** | 0 | M4 待接（区域灵气卡，灵气场目前走噪声 + NUMBERS） |
| `ores.csv` | §4.8 | ⬜ **无** | 0 | M4 待接（矿石 placement，G-7） |
| `periods.csv` | §5.1 | ⬜ **无** | 0 | **不填**（H5 全局时钟，`[占位]` 结构先定） |
| `wars.csv` | §5.3 | ⬜ **无** | 0 | **不填**（H7 声明式冲突表，ADR-016 后置） |
| `id_migration.csv` | §6 | 不适用（台账表） | 0 | 不填（本期无破档 ID） |
| `known-placeholders.csv` | §2 末条 | 不适用（台账表） | 5 | **已带 5 行初值**（Validator 占位键白名单，每行必须挂工单号） |
| ~~`realms.csv`~~ | §4.1 | 境界无 CSV，**产物由 NUMBERS `@@realms` 生成**（生成器已接） | — | 不建表：数值与解锁改在 `content/NUMBERS.md` §1 `@@realms` 块（指南 §2） |

## 门禁现状（填表时会撞上的墙）

已接生成器的表，`*_key` 列的值由 DataGen 从 `content/NUMBERS.md` 的 `@@块` 内联展开进产物
（键与值同时写入，JSON_SCHEMA §3）；必填列留空 = 构建失败并指出表:行。

**填错会被拦住**（Validator 11 项门禁全绿，`checks=11 problems=0`）：
重复 ID、产物过期（改了表没重新生成）、DAG 有环或不可达、跨表引用悬空（含 npc/item 落点）、
条件 DSL 非法、概率和 ≠ 1、zh_cn 缺词条、H1 price 为负、境界成长比越界。

完整清单与判据见 [docs/04 §6](../docs/04-内容管线.md)。**不要再按"六项未实现"的旧说法估工作量**——
那批门禁已在 2026-10-02 前全部落地并有实战记录（`docs/appendix/W4-演练记录.md`）。

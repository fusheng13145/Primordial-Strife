# tables/ — 策划填表区（DataGen 输入）

CSV 是唯一入口：UTF-8、首行表头、以 `_` 开头的列视为注释列（docs/04 §5）。

M0 已交付全部 CSV 模板（**只有表头，数据行为空**）与填写说明
`FILLING_GUIDE.md`（人话版，含逐列说明、格式示例、报错读法、待确认清单）。
**DataGen 的读表器与产物写入器已就位，但"生成器"只接了 `factions.csv` 一张表**：
往没有生成器的表里填行，`./gradlew :tools:datagen:run` 会直接报错并指名这张表（刻意如此，
不让内容静默消失）。所以下表"本期填不填"要看"生成器"那一列：没有生成器的表，先立工单接生成器，再填。
表结构（列名、字段语义、破档项）由
`content/JSON_SCHEMA.md` 约定，该文件由成员 C 起草、A 终审。
改表头 = 改契约 = 破档风险，必须先改 SCHEMA 并在同一提交里对齐 DataGen 与 Validator；
不要自行加列、删列、改列名。

模板清单：

| 文件 | 对应契约 | 生成器 | 本期填不填 |
|---|---|---|---|
| `FILLING_GUIDE.md` | — | — | 先读这个（§0 讲清了"现在填表会怎样"） |
| `factions.csv` | §5.2 | **已接**（→ `data/strife/strife_factions/`） | 填，M0 只需 2–3 行占位 |
| `techniques.csv` | §4.2 | 无 | 待接生成器后填 |
| `spells.csv` | §4.3 | 无 | 待接生成器后填 |
| `pills.csv` | §4.4 | 无 | 待接生成器后填 |
| `artifacts.csv` | §4.5 | 无 | 待接生成器后填 |
| `quests_prologue.csv` / `quests_ch1.csv` | §4.6 | 无 | 待接生成器后填（一章一表，列结构相同） |
| `dialog_trees_prologue.csv` / `dialog_trees_ch1.csv` | §4.7 | 无 | 待接生成器后填 |
| `dialog_prologue_text.csv` / `dialog_ch1_text.csv` | §4.10 | 无 | 待接生成器后填（节点文本 + 2 个变体） |
| `spirit_field.csv` / `ores.csv` | §4.8 | 无 | 待接生成器后填 |
| `periods.csv` / `wars.csv` | §5.1 / §5.3 | 无 | **不填**（`[占位]` 结构先定） |
| `id_migration.csv` | §6 | 不适用（台账表） | 不填（本期无破档 ID） |
| `known-placeholders.csv` | §2 末条 | 不适用（台账表） | **已带 5 行初值**（Validator 占位键白名单，每行必须挂工单号） |
| ~~`realms.csv`~~ | §4.1 | 境界无 CSV | **故意不建**：数值与解锁改在 `content/NUMBERS.md` §1 `@@realms` 块（详见指南 §2） |

MVP 需要填充的表（04 §2）：realms / techniques / spells / pills / artifacts / quests /
dialog_trees。符箓、阵法、灵植目录在 EP0 只占位不填。

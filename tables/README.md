# tables/ — 策划填表区（DataGen 输入）

CSV 是唯一入口：UTF-8、首行表头、以 `_` 开头的列视为注释列（docs/04 §5）。

M0 已交付全部 CSV 模板（**只有表头，数据行为空**）与填写说明
`FILLING_GUIDE.md`（人话版，含逐列说明、格式示例、报错读法、待确认清单）。
表结构（列名、字段语义、破档项）由
`content/JSON_SCHEMA.md` 约定，该文件由成员 C 起草、A 终审。
改表头 = 改契约 = 破档风险，必须先改 SCHEMA 并在同一提交里对齐 DataGen 与 Validator；
不要自行加列、删列、改列名。

模板清单：

| 文件 | 对应契约 | 本期填不填 |
|---|---|---|
| `FILLING_GUIDE.md` | — | 先读这个（§0 讲清了"现在填表还不会进游戏"） |
| `techniques.csv` | SCHEMA §4.2 | 填 |
| `spells.csv` | §4.3 | 填 |
| `pills.csv` | §4.4 | 填 |
| `artifacts.csv` | §4.5 | 填 |
| `quests_prologue.csv` / `quests_ch1.csv` | §4.6 | 填（一章一表，列结构相同） |
| `dialog_trees_prologue.csv` / `dialog_trees_ch1.csv` | §4.7 | 填 |
| `dialog_prologue_text.csv` / `dialog_ch1_text.csv` | §4.10 | 填（节点文本 + 2 个变体） |
| `spirit_field.csv` / `ores.csv` | §4.8 | 填 |
| `factions.csv` | §5.2 | M0 只需 2–3 行占位 |
| `periods.csv` / `wars.csv` | §5.1 / §5.3 | **不填**（`[占位]` 结构先定） |
| `id_migration.csv` | §6 | 不填（本期无破档 ID） |
| `known-placeholders.csv` | §2 末条 | **已带 5 行初值**（Validator 占位键白名单，每行必须挂工单号） |
| ~~`realms.csv`~~ | §4.1 | **故意不建**：境界无 CSV，数值与解锁改在 `content/NUMBERS.md` §1 `@@realms` 块（详见指南 §2） |

MVP 需要填充的表（04 §2）：realms / techniques / spells / pills / artifacts / quests /
dialog_trees。符箓、阵法、灵植目录在 EP0 只占位不填。

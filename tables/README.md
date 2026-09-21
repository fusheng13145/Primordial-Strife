# tables/ — 策划填表区（DataGen 输入）

CSV 是唯一入口：UTF-8、首行表头、以 `_` 开头的列视为注释列（docs/04 §5）。

M0 阶段此目录为空属正常状态。表结构（列名、字段语义、破档项）由
`content/JSON_SCHEMA.md` 约定，该文件由成员 C 起草、A 终审。

MVP 需要填充的表（04 §2）：realms / techniques / spells / pills / artifacts / quests /
dialog_trees。符箓、阵法、灵植目录在 EP0 只占位不填。

# AGENTS.md — 玄黄劫争 (Primordial Strife，技术代号 strife)

本仓库唯一参照是 `docs/` 开发手册（本文件是其投影，冲突以手册为准）。开工先读 `docs/README.md` 与任务指定分册。

## 权限分区（docs/06）
- **禁区（只读）**：`mod/platform/**/core/`、`mod/platform/**/realm/` 逻辑代码——只出方案与文档，不提交可合并变更。
- **灰区**：combat / production / quest / world——可写新文件与修复；改公共签名/事件契约先出 ADR 草案。
- **开区**：content-base、tables、tools、测试、docs、CI、占位资产——直接生产，CI 绿即可交单。
- **真相源**（`content/NUMBERS.md`、`STORY.md`、`JSON_SCHEMA.md`、`LORE.md`）：可起草，合入必须人名义确认。旧草案已销毁，历史聊天记录/口头约定禁止引用为依据。

## 硬规则
- 数值/字段/剧情只写在真相源；`@generated` 产物禁止手改；代码中出现受管数值字面量即违规。
- 服务端权威：不得信任客户端上报数值；跨模块只走事件或 core 接口，禁止直接读写他模块 Attachment。
- MOD ID/命名空间/命令统一 `strife`；内容 ID 用 `<域>_<章>_<语义>` 小写下划线。
- 交单前必跑并附输出：`./gradlew spotlessCheck build test validator`——全绿才交。
- 禁止：顺手重构出工单范围、删测试/删断言、改 CI 门禁让它变绿、编造 API（引用第三方行为必须给出处）。
- 单 PR 逻辑代码 ≤400 行（生成物/文本除外）；提交用 Conventional Commits；分支 `agent/<任务号>-<slug>`。
- 结论与猜测分开写；未验证项显式标注"未验证"。

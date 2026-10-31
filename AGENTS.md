# AGENTS.md — 玄黄劫争 (Primordial Strife，技术代号 strife)

本仓库唯一参照是 `docs/` 开发手册（本文件是其投影，冲突以手册为准）。开工先读 `docs/README.md` 与任务指定分册。

**开发模式（ADR-024）**：本人独立开发 + AI Agent 执行。本人是唯一决策者、实现者、审核者、真相源确认人与发布负责人；Agent 产出默认"未验收"，不得自批、不得代确认真相源、不得改门禁。

## 权限分区（docs/06；分区约束 Agent，本人不受限）
- **禁区（Agent 只读）**：`mod/platform/**/core/`、`mod/platform/**/realm/` 逻辑代码——Agent 只出方案与文档，不提交可合并变更；本人可直接写。
- **灰区**：combat / production / quest / world——Agent 可写新文件与修复；改公共签名/事件契约先出 ADR 草案。
- **开区**：content-base、tables、tools、测试、docs、CI、占位资产——Agent 直接生产，CI 绿即可交单。
- **真相源**（`content/NUMBERS.md`、`STORY.md`、`JSON_SCHEMA.md`、`LORE.md`）：Agent 可起草，合入必须**本人**确认。旧草案已销毁，历史聊天记录/口头约定禁止引用为依据。

## 硬规则
- 数值/字段/剧情只写在真相源；`@generated` 产物禁止手改；代码中出现受管数值字面量即违规。
- 服务端权威：不得信任客户端上报数值；跨模块只走事件或 core 接口，禁止直接读写他模块 Attachment。
- MOD ID/命名空间/命令统一 `strife`；内容 ID 用 `<域>_<章>_<语义>` 小写下划线；Java 包根统一 `com.strife.`，包名不用下划线（客户端包为 `com.strife.clientfx`；tools/buildSrc 也不例外，docs/02 §3）。
- 交单前必跑并附输出：`./gradlew spotlessCheck build test validator`——全绿才交。
- 禁止：顺手重构出工单范围、删测试/删断言、改 CI 门禁让它变绿、编造 API（引用第三方行为必须给出处）。
- 单 PR 逻辑代码 ≤400 行（生成物/文本除外）；同一交付链的多文件例外须在 PR 描述列文件清单与耦合理由（D-P0-4，2026-10-25）。提交用 Conventional Commits；分支 `agent/<任务号>-<slug>`。
- 结论与猜测分开写；未验证项显式标注"未验证"。
- **未经本人明确同意，Agent 不得启动游戏**；待验证项累积进 `deliverables/待验证台账.md`，本人发话时一次性汇总执行。

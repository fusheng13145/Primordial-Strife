# 玄黄劫争 · Primordial Strife（技术代号 `strife`）

Minecraft 1.21.1 / NeoForge 修仙 MOD。**唯一参照是 [`docs/`](docs/README.md) 开发手册**——本文件只是仓库地图与上手命令，与手册冲突时以手册为准；权限分区与硬规则见 [`AGENTS.md`](AGENTS.md)。

## 仓库地图（结构定义见 docs/02 §3）

| 路径 | 是什么 | 谁能改 |
|---|---|---|
| `docs/` | 开发手册 9 分册 + 附录（唯一参照） | 开区 |
| `content/` | 真相源：`NUMBERS.md` / `STORY.md` / `JSON_SCHEMA.md` / `LORE.md`（**均为 Agent 初稿，未经人名义确认不得当既成事实引用**） | 可起草，合入须人确认 |
| `tables/` | 策划 CSV（DataGen 输入）+ `FILLING_GUIDE.md` 填表说明书 | 开区 |
| `mod/` | Gradle 根工程（命令都在此目录执行） | — |
| `mod/platform/` | 平台层单一 MOD 制品，包级模块化 | core/realm = 禁区 |
| `mod/content-base/` | 内容层纯资源（禁止 `src/main/java`，CI 检查） | 开区 |
| `mod/tools/datagen` | 表 → 游戏 JSON 生成器 | 开区 |
| `mod/tools/validator` | 构建期内容校验（纯 JVM，不开 MC） | 开区 |
| `mod/buildSrc/` | 规范插件 `strife.conventions` + 包依赖断言 | docs/06 未单列，按 CI 门禁代码对待（改动须说明理由） |
| `.github/workflows/` | PR 门禁（清单见 docs/04 §8） | 开区，**禁止改门禁让它变绿** |

包内模块链：`combat/production/quest/world → realm → core`，`client_fx` 只依赖 `core`——由 `:checkImports` 强制（docs/03 §2）。

## 命名口径（ADR-017，禁止再改）

| 项 | 值 |
|---|---|
| MOD ID / 命名空间 / 命令前缀 | `strife` / `strife` / `/strife` |
| Java 包根 | `com.strife.*`（platform 用 `com.strife.<模块>`，工具用 `com.strife.tools.*`，buildSrc 用 `com.strife.conventions`） |
| 内容 ID | `<域>_<章>_<语义>` 全小写下划线（docs/04 §4）；**ID 用拼音、显示文本用中文**——完整规则见 [docs/11 命名与目录规范](docs/11-命名与目录规范.md) |
| 发布物 | `strife-X.Y.Z.jar`；展示名「玄黄劫争 / Primordial Strife」 |
| 分支 / 提交 | Conventional Commits。日常集成推 `leyon`；`leyon` → `main` 仅在负责人批准后合入（docs/02 §5） |

## 上手

前置：JDK 21（Temurin）、Git。Gradle 用仓库内 wrapper，不要自行安装发行版。

```bash
cd mod
./gradlew spotlessCheck        # 格式
./gradlew build                # 编译 + 单测 + 包依赖断言（单测能否本地运行见下）
./gradlew validator            # 内容校验（= :tools:validator:run）
./gradlew :tools:datagen:run   # 表 → content-base JSON
./gradlew :platform:runServer  # 需自备 run/server/eula.txt，见 docs/02 §4
./gradlew :platform:runClient  # 载入判据三行见 docs/02 §4
```

**只想进游戏看看（不改代码）**：不必用 Gradle。`./gradlew :platform:build` 产出 `mod/platform/build/libs/strife-0.0.1.jar`，把它丢进一个 **NeoForge 1.21.1** 实例的 `mods/`，用官方启动器 / PCL2 / HMCL 启动即可（注意选 NeoForge，不是 Forge）。开发与正式启动两条路径的差别见 docs/02 §4.1。DataGen 已接 **20 个表生成器实例**（覆盖 `tables/` 下 22 张表中的 20 张；`id_migration.csv`/`known-placeholders.csv` 是台账不产内容）与 **6 个 NUMBERS 直出域**（realms / realm_rules / world_rules / core_rules / combat_rules / beast_loot），**69 个内容产物**打进 jar（九境 + 三势力 + 六功法 + 四法术 + 四丹方 + 一器图 + 序章与第一章任务 DAG ×2 + 对话树与对话文本 ×4 + **矿石五份产物 ×3** + 五张运行时数值表 + 上界域（维度/群系/宗门结构/上界装饰/地点）+ W4 域（灵气场/周期/战事/妖兽掉落）），lang 种子 zh_cn/en_us 在库。**进游戏（单机）即可通关 MVP 主链**：登录随机显现灵根（UUID+种子化，永不重算）→ **按住潜行打坐**积修为（打断惩罚、小境界随修为推进）→ 修为滴满自动尝试突破（成功率按 NUMBERS 表 + 失败累计衰减，失败回退修为）→ 突破成功境界+1、寿元续至新境年限 → **序章任务链十节点全可推进**：破坏草丛掉落凝须草/断血草（概率走 NUMBERS @@world）、击杀任意生物计数、`/strife quest talk|deliver <npc_id>` 完成对话与交付节点、持有量（背包+末影箱）自动对账 `collect` 目标，奖励真实发放（灵石/药材是注册物品，`qi`/`flag`/`unlock` 直写附件）。屏幕左下角 HUD 与 **K 键面板**实时显示修为侧数据；`/strife quest status` 随时查任务进度。**游戏内对话**已可用：`/strife npc spawn <npc_id>` 放出一位 NPC，右键开对话树（选择项 + 条件门 + 八型效果），Esc 关闭。**M4 矿石已落地**：`/strife world ore status` 打印三种矿（灵玉/赤炎/寒玉）的群系、高度、实际矿脉数与掉落表，并逐条点名缺口；镐可挖掉落（丝触掉矿块、时运走原版 `ore_drops` 公式）。数值侧：修炼与战斗受管数值全部经 DataGen 产物读取（零受管字面量）；代码中仅存**技术常量**（同屏弹道上限 64、区块缓存条目上限 4096、每区块 16 格等 MC 固有量），已在 `docs/10-当前进展与交接.md` §4.6 列出并说明为何不受表管。MVP 简化清单见 `CultivationHandler`/`QuestAdapter` 类注（突破无主动押注动作等，待 A 排期）。上手细节见 [SETUP.md](SETUP.md)。服务端专用制品走 `./gradlew :platform:serverJar`（见 docs/02 §4）。

**Windows 本机 `test` 在中文路径下不可运行**（GBK + 非 ASCII 路径的 Gradle 已知缺陷，机制与禁令见 docs/02 §4）。**结论是换路径，不是换代码**：把仓库挂在纯 ASCII 路径的 worktree 上（例：`C:/strife-test`，`git worktree add` 后 `git checkout --detach <commit>`）即可跑全量单测；本仓库主工作树在中文路径下用 `build -x test` + `validator` + 客户端实测作为本地验证。**不得用 `jvmArgs`/`systemProperty`/跳过测试掩盖**。

交单前必跑（docs/06）：`./gradlew spotlessCheck build test validator`——全绿才交，附输出。

## 当前状态

> 快照日期 2026-10-08。**逐项进展、遗留与下一步动作见 [`docs/10-当前进展与交接.md`](docs/10-当前进展与交接.md)**（交接成员先读那份）。本节只给结论级概览。

M0–M3 主链已闭环，M2 战斗域与 M4 world 域部分交付。

**已落地（可玩可验）**
- **数据层**：`StrifeData` 16 字段聚合 record 挂单附件 `PLAYER_DATA`（copyOnDeath），Codec 全字段带默认值，旧档不破档。
- **修炼主链**：灵根生成（UUID+种子化，永不重算）→ 潜行打坐（打断惩罚、降频结算）→ 修为累积 → 突破（成功率查表 + 失败累计衰减，失败非破坏性）→ 寿元续至新境年限。修仙 HUD（客户端）+ K 键面板（境界/修为/寿元/灵根/声望/所属势力）。
- **任务域**：QuestEngine DAG 状态机 + 幂等推进 + 奖励缝 + 库存对账（背包 + 末影箱）；八谓词条件 DSL（短路布尔树）；对话树引擎（`DialogBook` 严格解析 / `DialogRunner` 门语义遍历 + 八型 effects）；NPC 实体（一实体类型承载全部 NPC，`npcId` 为同步数据字段）+ 对话 UI。序章十节点由真实 `prologue.json` 驱动的全链路用例守着。
- **战斗域**：法术弹道（同屏 ≤64、超距回收、穿透/AOE 半伤）、妖兽实体 AI、限速令牌桶与统一意图信封（fail-closed 校验）、`/strife spell cast`。
- **生产域**：统一配方机（炼丹）——材料校验 → 扣除 → 区间抽签（Σprob<1 差额判废丹）、`/strife recipe list|show`。
- **世界域**：灵气浓度场（`ImprovedNoise` 双层，粗粒度 16×16 区块一值 + 细化层，按世界种子+维度缓存）、草类方块草药掉落。
- **内容管线**：69 个 DataGen 产物（见上手节），`*_key` 列从 NUMBERS `@@块` 内联展开，zh_cn/en_us lang 在库；14 项 Validator 门禁全绿（`checks=14 problems=0`），含 V-DSL、V-REF 二期与 V-NAME（ID 用拼音、文本用中文，见 [docs/11](docs/11-命名与目录规范.md)）。
- **质量基线**：单测 454 例全绿（platform 279 / tools 175），`spotlessCheck build validator` 全绿，纯净服务端 jar 无头冒烟通过；`runClient` 客户端实测进世界、ERROR=0。
- **资产**：8 张物品贴图 + 通用 NPC/妖兽贴图 + 全部模型 JSON 入库，生成方式与替换流程见 [CREDITS.md](CREDITS.md)。

**已知遗留**（详见交接文档 §3）
- `tables/` 下内容表均已接入生成器并出产物（含 `spirit_field` / `ores` / `periods` / `wars`；逐表状态见 `tables/README.md`）；仅 `id_migration.csv` / `known-placeholders.csv` 为不产内容的台账表。
- JEI/Patchouli/Accessories 三个软依赖仍是"无库降级"状态，未接真实 API。
- 联机客户端镜像已通（`StrifeClientMirror` 轮询 + revision），但专用服下的面板消费端未做。
- `content/` 四份真相源仍为 Agent 初稿，`[拟]` 值待 C 名义确认（AGENTS.md 硬规则）。
- 本机中文路径下 Gradle 测试 worker 受 GBK 缺陷拦截，单测走 `C:/strife-test` ASCII worktree 或 CI。

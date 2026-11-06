# 玄黄劫争 · Primordial Strife（技术代号 `strife`）

Minecraft 1.21.1 / NeoForge 修仙 MOD。**唯一参照是 [`docs/`](docs/README.md) 开发手册**——本文件只是仓库地图与上手命令，与手册冲突时以手册为准；权限分区与硬规则见 [`AGENTS.md`](AGENTS.md)。

> **开发模式（ADR-024）**：本人独立开发 + AI Agent 执行。本人是唯一决策者、实现者、审核者、真相源确认人与发布负责人；Agent 产出默认"未验收"。路线与阶段见 [docs/07](docs/07-开发路线与迭代计划.md)，实况见 [docs/10](docs/10-当前进展与交接.md)。

## 仓库地图（结构定义见 docs/02 §3）

| 路径 | 是什么 | 谁能改 |
|---|---|---|
| `docs/` | 开发手册 11 分册 + 附录（唯一参照） | 开区 |
| `content/` | 真相源：`NUMBERS.md` / `STORY.md` / `JSON_SCHEMA.md` / `LORE.md`（**2026-11-01 已定稿；11-03/04 新增域仍标 `[拟]`，未经本人追认不得当既成事实引用**） | 可起草，合入须本人确认 |
| `tables/` | 策划 CSV（DataGen 输入）+ `FILLING_GUIDE.md` 填表说明书 | 开区 |
| `mod/` | Gradle 根工程（命令都在此目录执行） | — |
| `mod/platform/` | 平台层单一 MOD 制品，包级模块化 | core/realm = Agent 禁区（本人可写） |
| `mod/content-base/` | 内容层纯资源（禁止 `src/main/java`，CI 检查） | 开区 |
| `mod/tools/datagen` | 表 → 游戏 JSON 生成器 | 开区 |
| `mod/tools/validator` | 构建期内容校验（纯 JVM，不开 MC） | 开区 |
| `mod/buildSrc/` | 规范插件 `strife.conventions` + 包依赖断言 | docs/06 未单列，按 CI 门禁代码对待（改动须说明理由） |
| `.github/workflows/` | PR 门禁（清单见 docs/04 §8） | 开区，**禁止改门禁让它变绿** |

包内模块链：`combat/production/quest/world → realm → core`，`clientfx` 只依赖 `core`——由 `:checkImports` 强制（docs/03 §2）。

## 命名口径（ADR-017，禁止再改）

| 项 | 值 |
|---|---|
| MOD ID / 命名空间 / 命令前缀 | `strife` / `strife` / `/strife` |
| Java 包根 | `com.strife.*`（platform 用 `com.strife.<模块>`，工具用 `com.strife.tools.*`，buildSrc 用 `com.strife.conventions`） |
| 内容 ID | `<域>_<章>_<语义>` 全小写下划线（docs/04 §4）；**ID 用拼音、显示文本用中文**——完整规则见 [docs/11 命名与目录规范](docs/11-命名与目录规范.md) |
| 发布物 | `strife-X.Y.Z.jar`；展示名「玄黄劫争 / Primordial Strife」 |
| 分支 / 提交 | Conventional Commits。日常集成推 `leyon`；`leyon` → `main` 仅在本人批准后合入（docs/02 §5） |

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

**只想进游戏看看（不改代码）**：不必用 Gradle。`./gradlew :platform:build` 产出 `mod/platform/build/libs/strife-0.0.1.jar`，把它丢进一个 **NeoForge 1.21.1** 实例的 `mods/`，用官方启动器 / PCL2 / HMCL 启动即可（注意选 NeoForge，不是 Forge）。开发与正式启动两条路径的差别见 docs/02 §4.1。DataGen 已接 **35 个表生成器实例**（覆盖 `tables/` 下 37 张表中的 35 张；`id_migration.csv`/`known-placeholders.csv` 是台账不产内容）与 **6 个 NUMBERS 直出域**（realms / realm_rules / world_rules / core_rules / combat_rules / beast_loot），**135 个内容产物**打进 jar（九境 + 三势力 + 六功法 + 四法术 + 四丹方 + 一器图 + 序章与第一章任务 DAG ×2 + 对话树与对话文本 ×4 + **矿石五份产物 ×3** + 四张运行时数值表 + 上界域（维度/群系/宗门结构/上界装饰/地点）+ NPC 摆放 ×3 + W4 域（灵气场/周期/战事/妖兽掉落）），lang 种子 zh_cn/en_us 在库。**进游戏（单机）即可通关 MVP 主链**：登录随机显现灵根（UUID+种子化，永不重算）→ **按住潜行打坐**积修为（打断惩罚、小境界随修为推进）→ 修为滴满自动尝试突破（成功率按 NUMBERS 表 + 失败累计衰减，失败回退修为）→ 突破成功境界+1、寿元续至新境年限 → **序章任务链十节点全可推进**：破坏草丛掉落凝须草/断血草（概率走 NUMBERS @@world）、击杀任意生物计数、与落霞山麓自动摆出的石执事/苏药农/吴长老对话完成对话与交付节点、`/strife quest talk|deliver <npc_id>` 保留为兜底、持有量（背包+末影箱）自动对账 `collect` 目标，奖励真实发放（灵石/药材是注册物品，`qi`/`flag`/`unlock` 直写附件）。屏幕左下角 HUD 与 **K 键面板**实时显示修为侧数据；屏幕顶部 **BossBar 任务追踪条**显示当前任务必做目标、NPC 目标的距离与方位（`/strife quest track [on|off]` 可收起）；`/strife quest status` 随时查任务进度。**R 键施法轮盘**读取 jar 内四份法术产物（`strife_spells`）环形排布，点击即发既有 `cast` 意图；服务端仍做解锁/功法/灵气/冷却/限速全量校验，客户端读数仅作提示。**炼器与法宝**已可用：`/strife recipe artifact list|show` 查器图与实扣清单，`/strife craft artifact art_xuanyu`（需练气 + 玄玉×1/灵石×3/凝须草×2）产出带凡/地/天品阶的玄玉佩器胚；把器胚放**副手**（F 交换）后 `/strife artifact info` 查看状态、`/strife artifact cast` 激发主动技能玄玉刃（灵气 18，法宝冷却 20 秒与法术冷却 4 秒双计；法宝槽需金丹解锁）；每次成功激发耐久 -1（0 拒绝激发），`/strife craft repair` 消耗核心回满，**G 键法宝面板**显示副手槽位/品阶/耐久条/激发按钮。属性加成与 Accessories 饰品槽仍待 G-1 余项。**游戏内对话**已可用：走进落霞山麓（`/strife world place goto qi_luoxia`）后 NPC 会自动出现，右键开对话树（选择项 + 条件门 + 八型效果），Esc 关闭；也可用 `/strife npc spawn <npc_id>` 手动补位。序章 #1 必须靠真实对话完成，登录不再代推。**M4 矿石已落地**：`/strife world ore status` 打印三种矿（灵玉/赤炎/寒玉）的群系、高度、实际矿脉数与掉落表，并逐条点名缺口；镐可挖掉落（丝触掉矿块、时运走原版 `ore_drops` 公式）。数值侧：修炼与战斗受管数值全部经 DataGen 产物读取（零受管字面量）；代码中仅存**技术常量**（同屏弹道上限 64、区块缓存条目上限 4096、每区块 16 格等 MC 固有量），已在 `docs/10-当前进展与交接.md` §4.6 列出并说明为何不受表管。MVP 简化清单见 `CultivationHandler`/`QuestAdapter` 类注（突破无主动押注动作等，排期见 docs/07 §4）。上手细节见 [SETUP.md](SETUP.md)。服务端专用制品走 `./gradlew :platform:serverJar`（见 docs/02 §4）。

**Windows 本机 `test` 在中文路径下不可运行**（GBK + 非 ASCII 路径的 Gradle 已知缺陷，机制与禁令见 docs/02 §4）。**结论是换路径，不是换代码**：把仓库挂在纯 ASCII 路径的 worktree 上（例：`C:/strife-test`，`git worktree add` 后 `git checkout --detach <commit>`）即可跑全量单测；本仓库主工作树在中文路径下用 `build -x test` + `validator` + 客户端实测作为本地验证。**不得用 `jvmArgs`/`systemProperty`/跳过测试掩盖**。

交单前必跑（docs/06）：`./gradlew spotlessCheck build test validator`——全绿才交，附输出。

## 当前状态

> 快照日期 2026-11-04（代码基线 `6b621bb`）。**当前阶段：P3 EP1 已交 v1、P4 EP2 骨架推进中；P1 准出仍待实机**（[docs/07](docs/07-开发路线与迭代计划.md)）。**逐项进展、遗留与下一步动作见 [`docs/10-当前进展与交接.md`](docs/10-当前进展与交接.md)**（每批开工先读那份）。本节只给结论级概览。

M0–M3 主链已闭环；M4 四项本体（灵气场 / 矿石 / 宗门结构 / 上界维度）已落地，待实机验收与真相源数值确认。

**已落地（可玩可验）**
- **数据层**：`StrifeData` 16 字段聚合 record 挂单附件 `PLAYER_DATA`（copyOnDeath），Codec 全字段带默认值，旧档不破档。
- **修炼主链**：灵根生成（UUID+种子化，永不重算）→ 潜行打坐（打断惩罚、降频结算）→ 修为累积 → 突破（成功率查表 + 失败累计衰减，失败非破坏性）→ 寿元续至新境年限。修仙 HUD（客户端）+ K 键面板（境界/修为/寿元/灵根/声望/所属势力）。
- **任务域**：QuestEngine DAG 状态机 + 幂等推进 + 奖励缝 + 库存对账（背包 + 末影箱）；八谓词条件 DSL（短路布尔树）；对话树引擎（`DialogBook` 严格解析 / `DialogRunner` 门语义遍历 + 八型 effects）；NPC 实体（一实体类型承载全部 NPC，`npcId` 为同步数据字段）+ 对话 UI；服务端 BossBar 任务追踪条（必做目标进度 + NPC 目标距离/方位/跨维度提示，`/strife quest track [on|off]`）。序章十节点由真实 `prologue.json` 驱动的全链路用例守着。
- **战斗域**：法术弹道（同屏 ≤64、超距回收、穿透/AOE 半伤）、妖兽实体 AI、限速令牌桶与统一意图信封（fail-closed 校验）、`/strife spell cast`、客户端 **R 键施法轮盘**（只发意图，不做判定）；法宝主动技能（副手即槽，`/strife artifact info|cast`，法宝/法术双冷却）。
- **生产域**：统一配方机（炼丹 + 炼器）——材料校验 → 扣除 → 抽签（丹：Σprob<1 差额判废丹；器：境界门禁 + `quality_probs` 抽品阶）；`/strife recipe list|show`、`/strife recipe artifact list|show`、`/strife craft pill|artifact <id>`。
- **世界域**：灵气浓度场（`ImprovedNoise` 双层，粗粒度 16×16 区块一值 + 细化层，按世界种子+维度缓存）、草类方块草药掉落、三种矿石 placement（各 5 产物 + `/strife world ore status` 自检）、宗门结构 `sect_hall`（ADR-022）、上界维度（ADR-021）。
- **内容管线**：135 个 DataGen 产物（见上手节），`*_key` 列从 NUMBERS `@@块` 内联展开，zh_cn/en_us lang 在库；16 项 Validator 门禁全绿（`checks=16 problems=0`），含 V-DSL、V-REF 二期、V-NAME（ID 用拼音、文本用中文，见 [docs/11](docs/11-命名与目录规范.md)）、V-BIOME 与 V-INDEX（运行时加载器只读生成索引）。
- **质量基线**：单测 **708 例**全绿（platform 500 / tools 201 + buildSrc 7，2026-11-04 ASCII 复跑），`spotlessCheck build test validator` 全绿（Validator 16 项 `problems=0`），纯净服务端 jar 无头冒烟通过；`runClient` 客户端实测进世界、ERROR=0。
- **资产**：43 张物品贴图 + 52 份物品模型 + 9 份方块模型/blockstate（2026-11-03 批量生成 35 件物品资产；`item_xuanyu` / `pill_xuanyu` 复用存量贴图）+ 通用 NPC/妖兽贴图，生成方式与替换流程见 [CREDITS.md](CREDITS.md)。

**已知遗留**（详见交接文档 §3）
- `tables/` 下内容表均已接入生成器并出产物（含 `spirit_field` / `ores` / `periods` / `wars`；逐表状态见 `tables/README.md`）；仅 `id_migration.csv` / `known-placeholders.csv` 为不产内容的台账表。
- JEI/Patchouli/Accessories 三个软依赖仍是"无库降级"状态，未接真实 API；其中 Patchouli/JEI 的 P0 边界（optional + CLIENT、版本集中、硬引用守卫）已落，P1 再做全量接入（docs/02 §2.1）。
- 联机客户端镜像已通（`StrifeClientMirror` 轮询 + revision），但专用服下的面板消费端未做。
- `content/` 四份真相源已定稿（2026-11-01，ADR-031）；11-03/04 新增域（心魔/秘境灵气/试炼台/灵草掉率等）仍标 `[拟]`，待本人追认（AGENTS.md 硬规则）。
- 本机中文路径下 Gradle 测试 worker 受 GBK 缺陷拦截，单测走 `C:/strife-test` ASCII worktree 或 CI。

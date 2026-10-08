# SETUP.md — 环境搭建与进游戏看效果

> 本文是 docs/02 §4 的上手投影（冲突以手册为准）。目标：15 分钟内把工程跑起来、进游戏看到 strife 的第一个可见物。

## 1. 前置

- **JDK 21**（Temurin）+ Git。Gradle 用仓库内 wrapper（`mod/gradlew`），不要自装发行版。
- **Windows 硬约束：clone 路径必须纯 ASCII**（如 `D:\work\strife`）。非 ASCII 路径 + GBK 代码页会让测试 worker 启动即 `ClassNotFoundException`（docs/02 §4，上游缺陷 gradle#30304）——这是"换路径"问题，不是"换代码"问题。
- Windows 成员统一用 Git Bash。

## 2. 命令速查（都在 `mod/` 目录执行）

```bash
./gradlew spotlessCheck        # 格式门禁
./gradlew build                # 编译 + 依赖断言（本机中文路径下用 build -x test，见 §4）
./gradlew validator            # 内容校验（14 项门禁：V-DUP/V-FRESH/V-DAG/V-REF×2/V-GROWTH/V-RANGE×3/V-TEXT/V-PROB/V-DSL/V-NAME/price）
./gradlew :tools:datagen:run   # 表 + NUMBERS → content-base 产物
./gradlew :platform:runClient  # 开发客户端（进游戏看效果）
./gradlew :platform:runServer  # 开发服务端（需自备 run/server/eula.txt）
./gradlew :platform:build      # 出主 jar（含 content-base 产物），丢进启动器实例的 mods/
./gradlew :platform:serverJar  # 纯净服务端制品（CI 用它做 60s 开服冒烟）
```

三条验证通道的完整判据见 [docs/02 §4.2](docs/02-技术基线.md)。简版：

| 通道 | 命令 | 用途 |
|---|---|---|
| A 开发客户端 | `./gradlew :platform:runClient` | 日常端到端（渲染/UI/实体/进世界） |
| B 纯 ASCII worktree | `git worktree add C:/strife-test <branch>` → `cd C:/strife-test/mod && ./gradlew test` | **Windows 中文路径下唯一能跑单测的通道** |
| C 正式启动器 | `./gradlew :platform:build` → jar 放进 NeoForge 1.21.1 实例 `mods/`，用 PCL2 / HMCL / 官方启动器启动 | 交付验收、非开发成员试玩 |

**通道 C 的 M4 判据（2026-10-03 待跑，尚未验证）**：进主世界后依次做
1. `/strife world ore status` → 三种矿各一行明细，末尾四类缺口（方块未注册 / 掉落表缺失 / lang 缺词条 / 群系 ID 不存在）**必须全 `[OK]`**；
2. 用时运镐挖任意一种矿 → 掉落物名称应为 `灵玉矿/赤炎矿/寒玉矿`（掉空气 = 掉落表链断了）；
3. 丝触镐挖同一种 → 应掉**方块本体**；
4. 存档截图存 `docs/appendix/`。

## 3. 进游戏看什么（当前可见物）

**载入成功判据**（docs/02 §4）：mod 列表出现 `Primordial Strife x.y.z (strife)`；日志出现 `strife platform entry constructed` 与 `strife client_fx entry constructed`；`ResourceManager: ... mod/strife ...`。首屏可能出现 `authlib ... Read timed out`（session/realms 域名网络受限），不影响载入。

进世界后：

| 可见物 | 在哪 | 说明 |
|---|---|---|
| **修仙 HUD** | 屏幕左下角，常驻 | `境界 凡人 · 修为 0` + `寿元 X 年 · 灵根 未生成`。单机读 integrated server 的**权威附件真实数据**；专用服/联机上暂不显示（客户端镜像等 M1 A1-5 同步），绝不画假数据 |
| **修炼面板** | 游戏内按 **K** | 详情页：境界/小境界/修为/寿元（年+刻）/灵根五行品阶/突破失败累计/所属势力/H2 声望向量 |
| **对话树** | `/strife npc spawn <npc_qingshi_zhizhi>` 放出一位 NPC → **右键** | 说话人 + 正文 + 选项按钮；选项按条件门显隐；Esc 关闭（不暂停世界）。序章四棵树：石执事三段门链 / 苏药农交付 / 挑灯人风味 / 吴长老择宗 |
| **妖兽** | 原版环境下自然生成 | `StrifeMonster`：仇恨/追击/近战 AI；可被 `/strife spell cast` 命中 |
| **矿石** | 原版主世界地形中自然生成 | 三种：`block_ore_lingyu` 灵玉矿（金属性 常见）/ `block_ore_chiyan` 赤炎矿（火属性 稀有）/ `block_ore_hanyu` 寒玉矿（水属性 灵品）。丝触掉方块本体，时运走物品。**进世界先跑 `/strife world ore status` 看四类缺口是否全 `[OK]`**，再挖一铲验掉落 |
| **灵气场** | 任意已加载区块 | 区域灵气浓度场（噪声 + NUMBERS `world` 域），客户端面板与环境系数联动。数据表 `spirit_field.csv` 已接生成器（W4 P3），运行时灵气场仍走噪声路径 |
| **宗门结构** | 主世界生成 `sect_hall` | **已实现**（ADR-022）：`sect_hall` 结构数据链 + `SectHallStructure`/`SectHallPiece`/`SectStructures` 运行时 |

命令速查：

```
/strife info                     查看玩家数据
/strife quest status             任务进度
/strife quest talk|deliver <npc> 兜底推进对话/交付节点（NPC 实体不可用时的备用通道）
/strife npc spawn <npc_id>       放置 NPC
/strife npc dialog open <npc_id> 直接打开某 NPC 的对话
/strife spell cast <spell_id>    施法（走完整限速与冷却校验）
/strife recipe list|show         丹方查阅（JEI 接入前的可信兜底）
/strife world ore status         矿石链自检：逐矿打印 + 四类缺口（方块未注册/掉落表缺失/lang 缺词条/群系不存在）
```

## 4. 本机限制（重要）

- **本机主工作树在中文路径下不跑 `test`**：GBK + 非 ASCII 路径的 Gradle 已知缺陷（docs/02 §4，上游缺陷 gradle#30304/#30391）。**结论是换路径，不是换代码**——用 §2 的通道 B。**不得用改 jvmArgs/跳测试来掩盖。**
- 通道 B 的 worktree 是独立检出：**必须在目标 commit 上跑**，否则测的是旧代码；测完 `git worktree remove C:/strife-test` 清理。

## 5. 改代码前

先读 [AGENTS.md](AGENTS.md) 的权限分区：`core/realm` = 禁区（只读），`combat/production/quest/world` = 灰区（新文件可以、改契约先出 ADR），其余（content-base/tables/tools/docs/CI）= 开区。交单前必跑 `./gradlew spotlessCheck build test validator`（test 由 CI 代跑）并附输出。

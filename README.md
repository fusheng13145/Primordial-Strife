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
| 内容 ID | `<域>_<章>_<语义>` 全小写下划线（docs/04 §4） |
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

**只想进游戏看看（不改代码）**：不必用 Gradle。`./gradlew :platform:build` 产出 `mod/platform/build/libs/strife-0.0.1.jar`，把它丢进一个 **NeoForge 1.21.1** 实例的 `mods/`，用官方启动器 / PCL2 / HMCL 启动即可（注意选 NeoForge，不是 Forge）。开发与正式启动两条路径的差别见 docs/02 §4.1。目前 content-base 还没有生成物、`:platform:serverJar` 未实现，进去只有 `/strife info` 一条命令。

**Windows 本机 `test` 不可运行**（GBK + 非 ASCII 路径的 Gradle 已知缺陷，机制与禁令见 docs/02 §4）：单测由 CI（ubuntu）执行，本地验证以 `build -x test` + headless 冒烟为准，**不得用 `jvmArgs`/`systemProperty`/跳过测试掩盖**。

交单前必跑（docs/06）：`./gradlew spotlessCheck build test validator`——全绿才交，附输出。

## 当前状态

M0 工程骨架已就位：MOD 可被服务端与客户端载入，`:platform` 有 `strife` 附件与 `/strife info` 兜底命令根。后续任务与准出条件见 docs/07 与 docs/04 §8 的"未点亮门禁"清单。

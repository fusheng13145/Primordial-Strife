# SETUP.md — 环境搭建与进游戏看效果

> 本文是 docs/02 §4 的上手投影（冲突以手册为准）。目标：15 分钟内把工程跑起来、进游戏看到 strife 的第一个可见物。

## 1. 前置

- **JDK 21**（Temurin）+ Git。Gradle 用仓库内 wrapper（`mod/gradlew`），不要自装发行版。
- **Windows 硬约束：clone 路径必须纯 ASCII**（如 `D:\work\strife`）。非 ASCII 路径 + GBK 代码页会让测试 worker 启动即 `ClassNotFoundException`（docs/02 §4，上游缺陷 gradle#30304）——这是"换路径"问题，不是"换代码"问题。
- Windows 成员统一用 Git Bash。

## 2. 命令速查（都在 `mod/` 目录执行）

```bash
./gradlew build                # 编译 + 依赖断言（单测只在 CI 跑，见 §4）
./gradlew spotlessCheck        # 格式门禁
./gradlew validator            # 内容校验（V-DUP/V-FRESH/V-GROWTH/V-TEXT/V-PROB/数值域）
./gradlew :tools:datagen:run   # 表 + NUMBERS → content-base 产物
./gradlew :platform:runClient  # 开发客户端（进游戏看效果）
./gradlew :platform:runServer  # 开发服务端（需自备 run/server/eula.txt）
./gradlew :platform:serverJar  # 纯净服务端制品（CI 用它做 60s 开服冒烟）
```

## 3. 进游戏看什么（当前可见物）

`./gradlew :platform:runClient` 启动开发客户端（离线账号，首屏可能出现 `authlib ... Read timed out`，不影响载入）。**载入成功判据三行**（docs/02 §4）：

1. mod 列表出现 `Primordial Strife x.y.z (strife)`
2. 日志出现 `strife platform entry constructed`
3. 日志出现 `strife client_fx entry constructed`

进世界后：

| 可见物 | 在哪 | 说明 |
|---|---|---|
| **修仙 HUD** | 屏幕左下角，常驻 | `境界 凡人 · 修为 0` + `寿元 X 年 · 灵根 未生成`。单机读 integrated server 的**权威附件真实数据**；专用服/联机上暂不显示（客户端镜像等 M1 A1-5 同步），绝不画假数据 |
| `/strife info` | 聊天栏 | 玩家数据骨架 |

境界/灵根的生成逻辑属 M1（A1-1/A1-5，禁区代码），当前新档显示默认值：凡人、寿元按存档默认、灵根未生成——HUD 骨架已把消费端打通，M1 数值落地即自动变活。

## 4. 本机限制（重要）

- **本机不跑 `test`**：GBK + 非 ASCII 路径的 Gradle 已知缺陷（docs/02 §4）。单测由 CI（ubuntu）唯一执行；本地验证用 `build -x test` + `:platform:serverJar`。**不得用改 jvmArgs/跳测试来掩盖。**
- 诊断用途例外：把仓库复制到纯 ASCII 路径（如 `C:\Temp\strife-ci`）后可跑全量 `build`（含单测），仅用于开发期自测，交单门禁仍以 CI 为准。

## 5. 改代码前

先读 [AGENTS.md](AGENTS.md) 的权限分区：`core/realm` = 禁区（只读），`combat/production/quest/world` = 灰区（新文件可以、改契约先出 ADR），其余（content-base/tables/tools/docs/CI）= 开区。交单前必跑 `./gradlew spotlessCheck build test validator`（test 由 CI 代跑）并附输出。

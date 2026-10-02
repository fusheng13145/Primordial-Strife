# CREDITS.md — 资产来源与致谢

> 本文件是 `docs/04-内容管线.md` §7 的落地物：所有随 jar 分发的美术资产，凡非原创正式资产者，必须在此登记来源与生成方式。占位资产清点清零或挂账是 M5 准出条件（docs/07 §3）。

## 状态声明

**当前全部美术资产均为占位资产**（程序化生成或借用原版资源），无一来自人工绘制。它们存在的目的是让"内容 → 游戏内可见"的管线在美术到位前就真实可用；替换它们不需要改任何代码——按同路径投放同名文件即可。

## 资产清单

| 资产 | 路径 | 生成方式 | 生成日期 |
|---|---|---|---|
| 通用 NPC 皮肤 | `assets/strife/textures/entity/npc/npc.png` | Python 程序化绘制（64×64 标准皮肤布局，青灰长袍配色） | 2026-10-02 |
| 妖兽皮肤 | `assets/strife/textures/entity/npc/monster.png` | Python 程序化绘制（暗红妖气配色、红眼獠牙） | 2026-10-02 |
| 石执事/苏药农/挑灯人/吴长老 专属皮肤 | `assets/strife/textures/entity/npc/npc_*.png` | Python 程序化绘制（按 LORE 设定卡配色） | 2026-10-02 |
| 灵石/凝墟草/越鉴/断血/药材 物品贴图 | `assets/strife/textures/item/item_*.png` | Python 程序化绘制（16×16，菱形晶体/药草/书卷/袋装） | 2026-10-02 |
| 聚气/培元/延寿丹 物品贴图 | `assets/strife/textures/item/pill_*.png` | Python 程序化绘制（16×16 圆丹三色） | 2026-10-02 |
| 法术弹道视觉 | （借用）`minecraft:fire_charge` | 原版火焰弹贴图，经 `SpellProjectile#getItem` 借用 | 2026-10-02 |
| 灵玉/赤炎/寒玉矿 方块贴图 | `assets/strife/textures/block/block_ore_*.png` | `mod/tools/gen_ore_textures.py` 程序化绘制（16×16，石头底纹 + 斜向矿脉条带 + 矿斑，固定随机种子保证可复现） | 2026-10-02 |
| 矿石方块模型与 blockstate | `assets/strife/models/block/block_ore_*.json`、`assets/strife/blockstates/block_ore_*.json` | 手写（继承 `minecraft:block/cube_all`，全表贴图） | 2026-10-02 |
| HUD/面板/字体 | （借用）原版 GUI 元素 | `StrifeHudOverlay`/`StrifePanelScreen`/`DialogScreen` 使用原版组件与文本渲染 | 2026-10-02 |

## 程序化生成方式说明

物品与生物贴图由一次性 Python 脚本绘制（RGBA PNG，标准 MC UV 布局），脚本本体未入库——生成参数记录在各资产的配色注释里（见生成脚本执行日志与 git 提交 `eebde98`、`0a28435` 的资产新增记录）。

矿石贴图是唯一**脚本入库**的一类：`mod/tools/gen_ore_textures.py`（无第三方依赖，`zlib`+`struct` 手写 PNG）。入库理由是它带**固定随机种子**（`seed = sum(矿名.encode())`）——贴图因此是"可复现的源"而不是"一次性的产物"，重跑得到逐字节相同的结果，便于评审比对与回归。运行方式：

```bash
python mod/tools/gen_ore_textures.py
```

**替换流程**：美术人员按同路径投放同名文件（PNG，同尺寸）即可，无需改代码、无需重新构建内容管线。

## 借用资源致谢

- **Minecraft 原版资源**（Mojang Studios）：火焰弹贴图（法术弹道占位视觉）、原版 GUI 组件、原版生物模型层（人形模型 `ModelLayers.PLAYER`/`ZOMBIE`）。
- **NeoForge**（NeoForged 项目）：mod 加载与注册框架。

除上述外，本项目不含、不引用、不修改任何第三方 mod 的资产。

# Patchouli 手册与音效挂点说明（M5 准备，开区交付）

> 本文档记录 P1 阶段在 `content-base` 内落地的 **纯数据/资源结构**（不生成二进制资源、不碰渲染管线），并明确**剩余待美术/音频资产**与**平台闭区配套任务**。
> 与本手册冲突时以 `docs/04-内容管线.md` 与 `docs/02-技术基线.md` 为准。

## 1. Patchouli 手册（book JSON / 章节）

**位置**：`mod/content-base/.../assets/strife/patchouli_books/strife_compendium/`

**结构**：
- `book.json`：手册根（`name` / `landing_text` / `version` / `creative_tab` / `i18n:false` / `dictionary:[strife]`）。
- `categories/`：6 个分类 —— `realms`(境界) / `techniques`(功法) / `factions`(势力) / `artifacts`(法宝) / `wars`(征伐) / `items`(物什)。
- `entries/<分类>/<id>.json`：共 **28 条**条目，显示名复用 `lang/zh_cn.json`（与运行时 lang 同源，避免漂移）。
  - 境界 9 条、功法 6 条、势力 3 条、法宝 1 条（art_xuanyu）、征伐 1 条（war_luoxia_dispute）、物什 8 条。

**约束与已知缺口**：
- `i18n:false`：手册正文为字面中文，en_us 本地化（D4 翻译复核范畴）**未做**，列为待办。
- 分类 `background` 羊皮纸底纹 PNG **已剥离引用**（避免悬空资源）；待美术补 `textures/gui/entries/<cat>.png` 后回填。
- 条目 `icon` 均引用已注册物品（`strife:item_*`），可正常解析。
- Patchouli 为**软依赖**（`modRuntimeOnly` + optional，见 `docs/02 §2`）；缺 Patchouli 时本手册不加载，游戏功能不受影响。平台侧接入软依赖守卫为闭区任务（见 §3）。

## 2. 音效挂点结构（sounds.json）

**位置**：`mod/content-base/.../assets/strife/sounds.json`

声明 **9 个音效事件**（资源侧命名空间，事件名 → `sounds/<name>.ogg`）。当前**无二进制 ogg**，事件静默，不崩溃；补音频后自动生效。

| 事件名 | 触发点（对应代码 / 文案 key） | 待补文件 |
|---|---|---|
| `strife.meditate_start` | 打坐开始 `msg.strife.sit.start` | `sounds/meditate_start.ogg` |
| `strife.meditate_stop` | 出定 `msg.strife.sit.stop` | `sounds/meditate_stop.ogg` |
| `strife.breakthrough_success` | 突破成功 `msg.strife.breakthrough.success` | `sounds/breakthrough_success.ogg` |
| `strife.breakthrough_fail` | 突破失败 `msg.strife.breakthrough.failure` | `sounds/breakthrough_fail.ogg` |
| `strife.tribulation` | 天劫反噬 `msg.strife.tribulation.wounded` | `sounds/tribulation.ogg` |
| `strife.pill_use` | 服丹 `msg.strife.pill.*` | `sounds/pill_use.ogg` |
| `strife.spell_cast` | 施法（spell_xuanyu_ren 等） | `sounds/spell_cast.ogg` |
| `strife.war_horn` | 征伐开战（wars 阶段 entry） | `sounds/war_horn.ogg` |
| `strife.ui_panel_open` | 打开修仙面板 `key.strife.panel` | `sounds/ui_panel_open.ogg` |

## 3. 剩余待美术 / 音频资产（明确挂账，非假绿）

**音频**（M5，D-3 跟踪）：上表 9 个 `.ogg` 文件。
**UI 图**（M5）：修仙面板（K 键）、Patchouli 6 个分类底纹 `textures/gui/entries/<cat>.png`。
**封面插画**（M5）： manuals / 整合包封面。
**物品贴图**（待平台接入，非缺失）：`item_xuanyu`、`pill_xuanyu` 当前仅为内容表引用，未在 `StrifeItems`（platform 闭区）注册为可渲染物品，故**不生成贴图**；待平台接入注册后由美术补占位（见 `docs/04 §7` 台账"待平台接入"行）。

**平台闭区配套任务（不在本分支，需 A 区 owner 认领）**：
1. `StrifeItems` 注册 `item_xuanyu` / `pill_xuanyu`（含 `registerPill`）。
2. 注册上表 9 个 `SoundEvent`（`DeferredRegister<SoundEvent>`）并在对应逻辑处 `playSound`。
3. Patchouli 软依赖 `isLoaded()` 守卫与手册入口物品（如「修真通鉴」书物品）注册。

## 4. 校验

- `sounds.json` / Patchouli `book.json` 均为合法 JSON，经 `spotlessCheck` + 资源打包校验（无二进制、无渲染改动）。
- 未引入任何 Java 改动，未触碰渲染管线；纯 `content-base` 资源与 `docs` 说明。

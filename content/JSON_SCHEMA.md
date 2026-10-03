# JSON_SCHEMA.md — 内容层字段级契约

> **状态：Agent 初稿，待 C 审字段语义、A 终审破档项。** 本文件是 `tables/*.csv → content-base JSON` 的唯一字段契约（04 分册 §2 末）；三者（本文件、DataGen、Validator）的变更必须在同一提交内对齐。
>
> 标注口径与 NUMBERS.md 一致：`[锚]` = 手册已定，照抄不议；`[拟]` = 初稿提案，需人确认；`[占位]` = 结构现在定、内容本期不填。

## 1. 总则

### 1.1 分层与所有权

| 层 | 位置 | 谁能编辑 | 能否手改 |
|---|---|---|---|
| 源表 | `tables/*.csv`、`content/NUMBERS.md`、`content/STORY.md` | C（+Agent 起草） | 是（唯一入口） |
| 产物 | `mod/content-base/src/main/resources/data\|assets/strife/**` | DataGen | **否**（`@generated` 头，手改即 CI 漂移失败） |
| 契约 | 本文件 | C 起草 / A 终审 | 是 |

### 1.2 五条硬规则

1. **产物不手写、源码不重复**：同一字段只在一张源表里出现一次；跨表引用用 ID，不用复制值。
2. **严格未知字段**（`[拟]`）：`data/strife/**` 下自定义域（realms/techniques/spells/pills/artifacts/quests/dialog_trees/factions/wars/spirit_field）的加载器对未知字段 **fail-fast 报错**，不静默忽略——拼错字段名的后果是"内容凭空消失"，只有报错可自助修复（04 §6 末：报错须给文件路径 + JSON pointer + 人话说明）。原版格式（`loot_table`/`recipe`/`advancement`/`lang`）例外，走原版宽松语义。
3. **单位写在字段名后缀**（与 NUMBERS §0 同源）：`_sec` 秒、`_ticks` 刻、`_pct` 0–1 小数、`_years` 修行年、`_ratio` 无量纲倍率、`_prob` 0–1 概率、`_blocks` 格（方块）、`_nodes` 节点数、`_points` 点数（耐久/生命值等无量纲计数）、`_count` 个数。**禁止无后缀的裸数值单位字段**（`[锚]` 04 §6 数值越界项依赖此约定才能定位区间）。
4. **数值型字段引用而非内联**：凡 NUMBERS.md 管辖的数值（速率、成功率、冷却、消耗、加成、权重、区间），表里写 `*_key`（指向 NUMBERS 块键），不写数字。反例：`cooldown_sec: 4.0`；正例：`cooldown_key: "medium"`。
5. **破档分级**：新增可选字段 = 不破档（必须带默认值）；新增必填字段 = 破档（须给迁移映射）；删除/重命名字段 = 破档；字段语义变更 = 破档。破档项在 §6 表内登记，合入须 A 名义确认（AGENTS 真相源条款）。

### 1.3 文件与 ID 约定

- 路径：`data/strife/<域目录>/<id>.json`；`域目录` 取 04 §2 枚举（`strife_realms` / `strife_techniques` / `strife_spells` / `strife_pills` / `strife_artifacts` / `strife_quests` / `dialog_trees` / `strife_factions` / `strife_wars` / `strife_periods` / `worldgen` / `loot_table` / `recipe` / `advancement`）。
- **文件名 = 内容 ID**（不带命名空间前缀），运行时 ID 为 `strife:<id>`。
- ID 格式（04 §4 `[锚]`）：`<域前缀>_<章>_<语义>`，全小写下划线。域前缀固定枚举：`tech_ / spell_ / pill_ / qi_ / quest_ / dlg_ / npc_ / block_ / item_ / ch<N>_`；另登记 `[拟]` 扩展前缀：`realm_`（境界内部 ID 例外，见 §4.1）、`art_`（器图）、`fac_`（势力）、`war_`（冲突）、`per_`（周期事件）、`flag_` 不用于内容 ID（flag 是运行时键，见 §5 H3）。
- 一文件一对象；`strife_quests` 与 `dialog_trees` 为**一章一文件**（04 §2 `[锚]`），文件内 `id` 是该章入口节点 ID。
- 每个产物文件头：

```json
{
  "@generated": "from tables/techniques.csv @ sha256:1f3a…",
  "content_format": 1,
  "id": "tech_qingxin_jue"
}
```

`@generated` 与 `content_format` 为 DataGen 注入的保留键，Validator 按 04 §6"产物新鲜"比对哈希；`content_format` 是平台层与内容包的版本契约字段（04 §9）。

`@generated` 的源头也可以是 `content/NUMBERS.md`（由 `@@块` 生成、无 CSV 的域，如 `strife_realms`，§4.1）：`"from content/NUMBERS.md @ sha256:…"`。Validator 对该源头的新鲜度校验在真相源合入（A0-7）前打印声明并跳过，合入后自动生效——与 V-GROWTH 同一"合入即武装"口径。

### 1.4 枚举词表（`[拟]`，Validator 白名单即源于此）

| 枚举 | 取值 | 用途 |
|---|---|---|
| `element` | `jin / mu / shui / huo / tu`（金木水火土） | 灵根/功法/法术/矿石亲和；位序 bit0..bit4 与 `StrifeData.spiritroot_elements` 一致 |
| `quality_tier` | `fan / di / tian / xian`（凡/地/天/仙） | 功法、法宝、丹药品阶（03 §8 灵根品阶同源） |
| `technique_grade` | `huang / xuan / di / tian`（黄/玄/地/天阶） | 功法传承分级（09 A） |
| `chapter` | `prologue / ch1 / ch2 / …`（内容 ID 里写作 `prologue`、`ch1`） | 章节命名段；第二章主题"五行灵珠"`[锚]`（04 §3） |
| `unlock_key` | `meditation / spiritroot_panel / cultivation_panel / technique_equip / spell_cast / pill_crafting / artifact_slot / item_refine / quest_line_ch1 / tribulation / sect_join / ambient_qi_affinity / soul_scan / upper_realm_gate / ascension` | `strife_realms.unlocks` 清单键，NUMBERS §1 已登记 |
| `affinity` | `matched / neutral / conflict` | 灵根×功法适配，系数查 NUMBERS §6 |
| `rate_key` | `light / medium / heavy` | 法术消耗/冷却档位，值查 NUMBERS §8 |
| `formula_key` | `formula_basic / formula_pierce / formula_burst` | 伤害公式，查 NUMBERS §8 |
| `debuff_key` | `debuff_heavy_wound / debuff_weak` | 查 NUMBERS §3/§4 |
| `objective_type` | `kill / collect / deliver / talk / reach / sit / breakthrough / craft / pill_craft / artifact_craft / escort / survive` | 任务目标类型（ quest 引擎按此订阅事件） |

词表新增取值 = 破档风险项（会影响 Validator 白名单与 lang key 生成），登记在 §6。

## 2. CSV 表通用约定

- 编码 UTF-8（无 BOM）、首行表头、`,` 分隔、字段内换行禁止；`\n` 表示软换行。
- 以 `_` 开头的列 = 注释列，DataGen 忽略（04 §5 `[锚]`）。
- 空单元格 = 该字段缺省（`null`），**不等于** 0 或空串；必填列留空即构建失败并指出行号。需要"显式空数组"的列必须写 `()`（空单元格一律按缺省处理，二者不可混用）。
- **内层对象数组的整体包裹**（`[拟]`，DataGen 已实现）：对象作为更深一层结构的字段值时（如对话节点的 `options`），值必须整体再用 `( )` 包一层——`options=((text_key=a;next=b)|(text_key=c))`。双包裹是让内层 `|` 不与单元格顶层对象分隔符歧义的唯一写法；单选项 `options=(text_key=a;next=b)` 合法。
- 列表值用 `;` 分隔（`jin;mu`），映射值用 `k=v` 并以 `;` 分隔（`fac_qingshi=10;fac_yuelai=-5`），对象之间用 `|` 分隔（`item_lingshi=20|item_herb=1`），内层嵌套用 `( )` 包裹。`[拟]` 该字面量语法即 DataGen CSV 解析器的实现契约，改动 = 破档。
- 引用其它表的主键值必须存在（04 §6"引用存在性"）；`[占位]` 键走 Validator 白名单 `tables/known-placeholders.csv`（与源表同目录、同一编辑入口；Validator 已接 `--tables-root` 并用它扫源表 `id`，读白名单本身要等 `V-REF` 实现时一并接上）。白名单必须挂 issue 号，禁止长期驻留。
- 每表强制列：`id`（主键，全域唯一）、`_note`（注释列，人话备注）。

## 3. 数值源契约（NUMBERS.md → DataGen）

DataGen 只从 NUMBERS.md 读以下块，其余键视为未定义：

| `@@块 id` | 提供的引用命名空间 | 消费者 |
|---|---|---|
| `realms` | `realm:<id>:qi_max/stage_count/lifespan_years/sit_rate` | `strife_realms`、realm 运行时 |
| `limits` | `limit:*` | Validator（不进产物） |
| `breakthrough` | `bs:*` | `strife_realms.breakthrough_success_key` |
| `breakthrough_cost` | `bt_cost:*` | realm 结算 |
| `lifespan` | `life:*` | realm 结算、`pill_yanshou` |
| `meditation` | `sit:*` | realm 打坐 |
| `spiritroot` | `sr:*` | realm 灵根、`strife_techniques.required_spiritroot` |
| `pills` | `pill:*` | `strife_pills`、production |
| `combat` | `combat:*` / `formula_*` | `strife_spells` |
| `world` | `world:*` | `worldgen`、loot |
| `rate_limits` | `rl:*` | core 网络层（不进产物） |

- 字段名带 `_key` 的列，其值必须在对应命名空间存在；DataGen 生成产物时**把值内联展开并写下来源键**（便于运行时零查表成本，同时保持单一真相源：源表只有键，产物有展开值 + `@generated` 哈希）。
- 展开规则：产物里同时写 `"cooldown_key": "medium"` 与 `"cooldown_sec": 1.0`；加载器读 `_key` 校验、读实际值运行。二者不一致 = Validator 红。

## 4. 各内容域字段契约

以下每域给出：JSON 字段表 + 最小示例。`必` = 必填。

### 4.1 `strife_realms` — 境界（源：NUMBERS `@@realms`，无 CSV）

| 字段 | 类型 | 必 | 语义 |
|---|---|---|---|
| `id` | string | 必 | 境界 ID。`[拟]` 破例不带域前缀（`fanren`/`qili`/`zhuji`…），因 NUMBERS 块键与 `StrifeData.realmOrdinal` 序号需一字对应，加前缀会制造两套拼写 |
| `ordinal` | int | 必（推导） | 序号，0 起，与 `StrifeData.realmOrdinal` 同义（core 只存整数，03 §8）。**DataGen 按 NUMBERS §1 `@@realms` 块的书写顺序推导**，不写进表——链序即真相，避免两处序号打架 |
| `qi_max` | int | 必 | 修为上限，查 `realm:<id>:qi_max` |
| `stage_count` | int | 必 | 小境界数；阈值等分不入表（NUMBERS §1 口径） |
| `lifespan_years` | int | 必 | 该境界寿元上限（年） |
| `sit_rate` | number | 必 | 打坐基础速率（修为/刻），查表 |
| `unlocks` | string[] | 必（可空） | `unlock_key` 枚举值 |
| `breakthrough_success_key` | string | 必 | 直接写 NUMBERS §3 的块内键名原值（`bs_fanren_qili`…），**不加 `bs:` 前缀**——`bs_` 已在键名里，再叠命名空间会造出 `bs:bs_x` 双前缀。`bs:` 只是 §3 表内的"引用命名空间"记法，不是字段值格式 |
| `tribulation` | bool | 必（推导） | 是否附带天劫事件（03 §8 渡劫）。**由 `unlocks` 是否含 `tribulation` 推导**，不单独设列，否则两处口径可各自漂移 |
| `display_name_key` | string | 必（推导） | lang key，由 §4.10 的 realm 映射规则 `realm.strife.<id>` 生成，**不需在 NUMBERS 里写** |
| `disabled_by_placeholder` | bool | 可 | `[占位]` 后三段标记，Validator 据此放宽单调递增检查 |

### 4.2 `strife_techniques` — 功法（`tables/techniques.csv`）

| 字段 | 类型 | 必 | 语义 |
|---|---|---|---|
| `id` | `tech_<章>_<语义>` | 必 | |
| `grade` | `technique_grade` | 必 | 品阶 |
| `element` | `element` | 必 | 主属性；`[拟]` 单一主属性，多属性走后续 ADR |
| `required_realm` | realm id | 必 | 最低境界（04 §2 `required_realm`） |
| `required_stage` | int | 可 | 小境界下限，默认 1 |
| `required_spiritroot` | `element`[] | 可 | 需要的灵根集合。**留空 = 无灵根要求**（走 `affinity_neutral` 1.00）；确需显式空数组时写 `()` |
| `affinity_rule` | `affinity` | 可 | 缺省由服务端按 §6 灵根系数计算 |
| `qi_rate_ratio` | number | 必 | 功法倍率（05 §2 公式第四项），∈(0,4] |
| `passives` | effect id[] | 可 | 被动效果清单 |
| `grants_spells` | spell id[] | 可 | 学会即得法术 |
| `faction` | faction id | 可 | 归属势力（H2）；空 = 无门无派 |
| `price` | `{item_id, count}` \| null | 必（可 null） | **H1 钩子**，非负校验 |
| `source` | enum `inherit/scroll/reward/shop` | 可 | 传承来源，供 LORE 与文本引用 |
| `disabled_reason_key` | string | 可 | 洗髓致灰时的提示 key（03 §8 禁止静默失效） |

### 4.3 `strife_spells` — 法术（`tables/spells.csv`）

| 字段 | 类型 | 必 | 语义 |
|---|---|---|---|
| `id` | `spell_<章>_<语义>` | 必 | |
| `element` | `element` | 必 | |
| `required_technique` | tech id | 必 | 前置功法 |
| `cost_key` | `rate_key` | 必 | 消耗档位，值查 `combat:spell_cost_qi` |
| `cooldown_key` | `rate_key` | 必 | 冷却档位，值查 `combat:spell_cooldown_sec` |
| `damage_formula_key` | `formula_key` | 必 | 禁止旁路（05 §2） |
| `projectile` | `{speed, gravity, range, pierce_count}` \| null | 必（可 null） | null = 瞬时/自身；MVP 用原版弹道（D5） |
| `aoe_radius_blocks` | number | 可 | 0 = 单体（无后缀即违规，§1.2 规则 3） |
| `effects` | `{effect_id, duration_sec, amp}`[] | 可 | 走原版状态效果或 strife 自定义 |
| `price` | 同 H1 | 必（可 null） | 符箓/卷轴可售 |

### 4.4 `strife_pills` — 丹方（`tables/pills.csv`）

| 字段 | 类型 | 必 | 语义 |
|---|---|---|---|
| `id` | `pill_<语义>` | 必 | 与 NUMBERS §7 键一致（`pill_juqi` 等） |
| `quality_tier` | `quality_tier` | 必 | |
| `pattern` | string（网格图样 DSL） | 必 | 投料顺序/摆放图样，04 §2 `pattern`；语法见 §4.4.1 |
| `core_slot` | item id | 必 | 主药（决定丹名与效果） |
| `materials` | `{item_id, count}`[] | 必 | 引用存在性校验 |
| `heat_range` | `[min,max]` | 必 | 火候窗口，0≤min≤max≤1 |
| `outputs` | `{item_id, count, prob, quality}`[] | 必 | 概率和 = 1（容差 1e-6，04 §6） |
| `effect_key` | pill id | 可 | 加成值查 `pill:<id>` |
| `failure_output` | `{item_id, count}` | 可 | 废丹（09 F），缺省 = 销毁材料 |
| `price` | H1 | 必（可 null） | |

#### 4.4.1 pattern DSL（`[拟]`，M2 炼丹实现，语法先冻结）

`slot(位置)=材料键` 以 `;` 分隔，位置为 3×3 网格坐标 `r<c`（如 `1x1=item_ningxu`）；`H` 段声明火候曲线点 `H=[0:0.2,1:0.7]`。Validator 校验：坐标不重复、在网格内、材料键存在于 `materials` 或原版物品。材料一律用 `item_` 前缀内容 ID（04 §4 枚举内）；`plant_` 前缀归 EP1 灵植玩法，不用于投料药材。

### 4.5 `strife_artifacts` — 器图（`tables/artifacts.csv`）

| 字段 | 类型 | 必 | 语义 |
|---|---|---|---|
| `id` | `art_<语义>` | 必 | |
| `blank` | item id | 必 | 器胚 |
| `restriction_count` | int | 必 | 禁制数（09 F 炼器），≥1 |
| `core_slot` | item id | 必 | 核心材料 |
| `materials` | `{item_id,count}`[] | 必 | |
| `quality_probs` | `{quality_tier, prob}`[] | 必 | 和 = 1 |
| `slot_type` | enum `weapon/armor/treasure` | 必 | 装备槽 |
| `required_realm` | realm id | 必 | |
| `active_skill` | `{spell_id, cooldown_key}` \| null | 必（可 null） | 主动技能，限速 `rl:artifact_per_sec` |
| `passives` | effect id[] | 可 | |
| `durability_points` | int | 必 | >0（点，无量纲计数） |
| `price` | H1 | 必（可 null） | |

### 4.6 `strife_quests` — 任务 DAG（`tables/quests_<章>.csv`，一章一表一产物文件）

| 字段 | 类型 | 必 | 语义 |
|---|---|---|---|
| `id` | `quest_<章>_<语义>_<NN>` | 必 | 04 §4 示例 `quest_prologue_meditation_01` |
| `chapter` | `chapter` | 必 | 所属章 |
| `entry` | bool | 必 | 是否章节入口（每章恰一个 `true`，Validator 校验） |
| `prerequisites` | quest id[] | 必（可空） | DAG 前置，无环校验 |
| `objectives` | `{id, type, target, count, optional}`[] | 必 | `objective_type` 枚举；`target` 为内容 ID 或坐标区域 |
| `conditions` | DSL string | 可 | §4.7.1 条件语言；进入前置（境界/flag/物品） |
| `rewards` | `{type, id, count, unlock_key}`[] | 必 | type ∈ `item/qi/realm_step/flag/unlock/reputation/spell/technique` |
| `reputation_delta` | `{faction_id, delta}`[] | 可 | **H2**，delta ∈ [-100,100] |
| `causality` | `{kind, subject_id, note_key}`[] | 可 | **H3**，kind ∈ `debt/grudge/killing` |
| `timer` | `{sec, fail_goto}` \| null | 必（可 null） | 超时跳转节点 ID（04 §2 `timer`/`fail_goto`） |
| `fail_goto` | quest id \| null | 必（可 null） | 与 timer 独立：任务可失败时的去向 |
| `flags_set` | flag 键[] | 可 | **H3** 命名空间键，见 §5 |
| `repeatable` | enum `no/per_day/per_period` | 必 | `per_period` 依赖 H5 周期 ID |
| `hidden` | bool | 必 | 是否在面板隐藏（剧情反转） |
| `price_reward` | H1 | 必（可 null） | 灵石奖励统一走 H1 价格类型，避免第二货币字段 |

DAG 完备性（04 §6 `[锚]`）：无环、章节入口可达全部必做节点、每个 `rewards` 引用存在、`fail_goto`/`timer.fail_goto` 不指向已完成节点之外。

`rewards.type` 语义补注（`[拟]`，待 C 审定）：`realm_step` = 小境界推进一档——修为直接置为下一档阈值（与 §4.1 `stage_count` 的等分口径一致，`qi_max × stage / stage_count`）；已在满段时给到 `qi_max`（境界圆满，获得主动押注突破资格，不自动突破）。境界序号、寿元、突破失败计数均不变。

对话树 effects 语义补注（`[拟]`，待 C 审定；§4.7 `effects` 八型，args 语法逐型冻结）：

| type | args 语法 | 语义 |
|---|---|---|
| `set_flag` | `<flag键>` | 落玩家 H3 flag |
| `reputation` | `<fac_id>:<delta>` | 声望向量增减，delta ∈ [-100,100] |
| `give_item` | `<item_id>:<count>` | 发放物品（注册名 = 内容 ID 全名） |
| `take_item` | `<item_id>:<count>` | **交付闭合面**：从玩家库存扣物品（先主背包后末影箱）；持有不足 = 内容错误（选项必须配 `item()` 条件兜底），fail-fast |
| `start_quest` | `<quest_id>` | 激活任务（跳过 prerequisites，conditions 仍生效）；激活集随任务进度持久化 |
| `complete_node` | 可空（默认 `dlg:<tree>:<node>`） | 节点完成记忆（落 H3 flag 命名空间，conditions 可用 `flag()` 引用做一次性节点） |
| `play_sound` | `<sound_id>[:<volume>:<pitch>]` | 客户端音效，缺省 volume/pitch = 1.0 |
| `teleport` | `<x>,<y>,<z>[,<dimension>]` | 绝对坐标传送；缺省维度 = 当前维度 |

遍历模型（`[拟]`，与 `DialogRunner` 实现对齐）：带 `conditions` 的节点是**门**——条件不满足沿 `next` 旁路（看下一个门或兜底节点），满足则停（决策节点等玩家选）；无条件的顺序节点（只有 `next`）进入时自动前进；选项 `next` 为空 = 对话结束，随后执行树级 `effects` 一次。选项跳转重置深度计数（玩家驱动的循环叙事合法），门链推进累计深度受 `max_depth_levels` 约束（门写成环 = 内容 bug，构建期炸）。带 conditions 的门节点允许 `options` 与 `next` 共存；无条件节点不得共存。

任务联动（`[拟]`）：对话自然结束（玩家走完分支到终端）时装配层自动报一次 `talk` 与 `deliver`（target = 树的 npc；report 对无匹配目标是 no-op）。交付物由对话树的 `take_item` 声明流转，引擎不反查内容——"带齐东西来对话"即交付完成，Esc 关闭不报（任务对话必须走完才算交互）。

产物形态（`[拟]`，DataGen 已实现）：一章一文件 `data/strife/strife_quests/<章>.json`，文件 `id` = 该章 `entry=true` 的任务 ID，全部行按表序进 `quests` 数组；每行字段同上表。生成器硬校验：恰一个 `entry=true`、行 `chapter` 与文件名章段一致——空表合法（章内容未写）。

### 4.7 `dialog_trees` — 对话树（`tables/dialog_trees_<章>.csv`，一章一文件）

| 字段 | 类型 | 必 | 语义 |
|---|---|---|---|
| `id` | `dlg_<章>_<语义>` | 必 | |
| `npc` | `npc_` id | 必 | 说话人（09 D 世系） |
| `root` | node id | 必 | 入口节点 |
| `nodes` | `{id, speaker, text_key, conditions, next, options}`[] | 必 | `options` = `{text_key, conditions, next, effects}`[] |
| `effects` | `{type, args}`[] | 可 | type ∈ `set_flag / reputation / give_item / start_quest / complete_node / play_sound / teleport` |
| `max_depth_levels` | int | 必 | `[拟]` 默认 16；解释器求值深度上限（03 §8 防表写错死循环） |

产物形态（`[拟]`，DataGen 已实现）：一章一文件 `data/strife/dialog_trees/<章>.json`，文件 `id` = 首棵树的 `root` 节点 ID，全部行按表序进 `trees` 数组；节点 `options` 由整体包裹语法展开为 `{text_key, conditions, next, effects}[]`（§2 末条）。

#### 4.7.1 条件 DSL（`[锚]` 语义来自 03 §8，语法在此冻结）

```
expr    := term (('&&' | '||') term)* | '!' expr
term    := predicate | '(' expr ')'
predicate := realm CMP NUM | sub_stage CMP NUM | flag '(' KEY ')' | item '(' ID ':' NUM ')'
           | reputation '(' FAC ':' CMP NUM ')' | quest_done '(' ID ')' | affinity '(' ELEM ')' | luck CMP NUM
CMP     := '>=' | '<=' | '==' | '!='
```

- `KEY` 必须是 §5 H3 的合法命名空间键；`ID`/`FAC` 必须存在于内容域；`luck` 为 H6 预留（本期恒 0）。
- 未知谓词、未知 flag、未知 ID → Validator 直接失败（04 §6"DSL 合法"）。求值步数上限 `[拟]` 1000，超限按 false 处理并记日志。
- 实现注记（DataGen 同提交口径的对面：解释器已落地 `com.strife.quest.dsl`）：`item(...)` 为 at-least 语义；`realm` 右值收境界 ID 或 ordinal 字面量两种（§4.7.1 与填表指南示例的口径分歧按此收口）；负数字面量仅限贴数字（声望区间 [-100,100]）。
- 示例：`realm>=qili && flag(fac_qingshi:ch1:met_elder) && item(item_lingshi:10)`。

### 4.8 `worldgen` — 灵气场与矿石（`tables/spirit_field.csv`、`tables/ores.csv`）

| 字段 | 类型 | 必 | 语义 |
|---|---|---|---|
| `id` | `qi_<区域>` / `block_<矿石>` | 必 | |
| `region_id` | string | 必 | 区域粗粒度缓存键（16×16 区块一值，03 §6） |
| `ambient_qi_ratio` | number | 必 | 落 `world:ambient_qi_min..max`，**不得为 0**（05 §2 tooltip 可解释性） |
| `placement` | `{biomes[], y_min, y_max, veins_per_chunk, vein_size}` | 必 | 矿石生成 |
| `element` | `element` | 必 | 矿石属性 |
| `density_ratio` | number | 必 | ∈(0,1] |
| `drop_table` | loot id | 必 | 引用 `loot_table` |
| `regen_period_key` | `per_` id \| null | 必（可 null） | **H5** 再生周期，本期恒 null |

### 4.9 `loot_table` — 掉落（原版格式 + H6）

沿用原版 loot 2 格式；额外约束：

- 每个 `conditions` 里允许出现自定义谓词 `strife:luck`，字段 `luck_modifier_source`（`[占位]` 本期无来源，默认 1.0）。
- 与任务奖励联动的表命名 `loot_quest_<章>_<语义>`，被 `quests.rewards` 引用时必须存在。

### 4.10 `lang` — 文本（`assets/strife/lang/zh_cn.json` 为源，`en_us.json` 占位）

- key 与内容 ID 一一映射（04 §4 `[锚]`）：`item.strife.<id>` / `block.strife.<id>` / `entity.strife.<id>`（妖兽与 NPC 实体，含 `npc_` 前缀 ID）/ `effect.strife.<id>` / `quest.strife.<id>.title` / `quest.strife.<id>.desc` / `dialog.strife.<node_id>` / `realm.strife.<id>` / `[拟]` `faction.strife.<id>` / `[拟]` `region.strife.<id>`（灵气场与 LORE 区域卡共用）/ `[拟]` `technique.strife.<id>` / `[拟]` `spell.strife.<id>` / `[拟]` `artifact.strife.<id>`。丹药按物品处理：`pill_` 前缀 ID 用 `item.strife.<id>`。Validator 的 V-TEXT 按"产物域 → 上述前缀"反查 zh_cn/en_us 覆盖（quest/dialog 的键规则随 M3 生成器接入）。
- 上述前缀是 lang key 的**唯一**生成来源：`display_name_key` 一类字段只写已登记前缀拼出的 key，Validator 按 §7 `V-TEXT` 反查；缺前缀 = 契约漏项，走 ADR 补登记，不得在表里自造新前缀。
- zh_cn 缺失 = 构建失败；en_us 允许占位但**不得为空串**（04 §6）。
- 对话文本源在 `tables/dialog_<章>_text.csv`（05 §7 流程产物），每节点要求 `text` + `variant_a` + `variant_b`（05 §7"文本 + 变体 2 个"），只有 `text` 进 lang，变体进台账供人工润色挑选。
- 对话文本的**运行时通道**（`[拟]`，本轮起草）：DataGen 由 `dialog_<章>_text.csv` 生成独立产物 `data/strife/dialog_text/<章>.json`（`texts` 映射 = `dialog.strife.<条目id>` → 正文），服务端在对话打开/推进时把成品文本随 S2C 包下发，客户端零 lang 依赖。理由：lang 文件是人工润色资产（无 `@generated` 头、不参与 V-FRESH），生成器合并写它会把"生成"与"手种"两种来源混进一个不可审计的文件。条目 id 约定：节点行 = 节点 id；选项行 = `<node_id>_opt<N>`（N 按 options 序从 1 起）。`V-TEXT` 对该产物反查与 `dialog_trees` 的 text_key 对应。
- 版权红线（05 §7）：任何进 lang 的字符串不得含网文专名（调研用语清单见 LORE §7 禁用词表）。

### 4.11 表目录占位不填（`[占位]`，04 §2 末 / 09 F）

`strife_talismans`（符箓）、`strife_formations`（阵法）、`strife_spirit_plants`（灵植）：本文件定义目录名与主键前缀（`tal_` / `form_` / `plant_`），字段留 `TODO(EP1)`，DataGen 不生成、Validator 不检查。**目录名与 ID 前缀现在就定**是为了避免 EP1 破档改名。

### 4.12 运行时数值表（config 域，非内容本体，`[拟]` 补登记）

三张由 `NUMBERS.md` `@@块` 直出的运行时参数表，**不是内容**：没有 lang 键、不进 `V-TEXT` 覆盖、玩家不可见，只被代码查表读取——这是 05 §1"代码里出现受管数值字面量即违规"的落地通道。

| 产物 | 来源块 | 消费者 | 承载 |
|---|---|---|---|
| `data/strife/strife_realms/rules.json` | `@@breakthrough` `@@breakthrough_cost` `@@meditation` `@@spiritroot` | realm | 突破成功率与失败代价、打坐结算参数、灵根系数 |
| `data/strife/strife_worldgen/rules.json` | `@@world` | world | 灵气浓度上下限、矿石权重、妖兽掉落、草丛采集概率 |
| `data/strife/strife_core/rules.json` | `@@limits` `@@rate_limits` | core（S2C 同步框架、C2S 意图限速） | 预算红线、发包上限与重同步间隔、意图限速 |

约定：产物 `id` = `<域>_rules`（V-DUP 台账外的配置单例，非内容 ID）；域目录名 `strife_realms` / `strife_worldgen` / `strife_core` 由生成器固定、不来自任何表；新增 config 域 = 同一 PR 改本表 + DataGen + 消费者。`@@limits` 中的预算与间隔**同为受管数值**，代码不得复制字面量。

`strife_core/rules.json` 还带一个 `derived` 块：由其他块在**生成期算一次**的换算值，目前只有 `ticks_per_year`（= `@@lifespan.seconds_per_year` × 20 刻/秒）。放这里的理由：它不是独立事实，写进 NUMBERS 就有了两个可互相矛盾的真相，写进代码就是受管字面量；生成期换算是唯一不会漂移的位置。派生值只允许出现在 `derived` 内，且必须注明它由哪个块算出。

### 4.13 宗门结构（**已生效**——ADR-022，A 批准 2026-10-02：`kind=code` 路线实现）

M4 宗门结构的字段契约。**本节已生效**：A 于 2026-10-02 按 ADR-022 裁决走 `code` 路线并实现（前提：表 + DataGen 产出 + Java `Structure` 子类 + codec 注册）。
**内容形态仍待 C**：宗门建筑的具体样式/规模属真相源 `LORE.md` §区域卡，本节只定机制不定内容——本期建筑是「规则化厅堂骨架」，美术形态随 C 的真相源与 04 §7 资产管线迭代。

**为什么单列一节而不并入 §4.8**：§4.8 是灵气场与矿石（世界生成噪声层），宗门结构是**离散建筑放置**，两者在原版侧走不同机制（`biome_modifier` vs `structure_set`），字段没有交集。

#### 4.13.1 原版机制事实（1.21.1 产物实证，勿凭记忆改）

原版结构由**两份产物**组成，缺一则结构不生成（且不报错）——与矿石五份产物同构的静默断链风险：

| 产物 | 作用 | 关键字段（实证取自 `minecraft_1.21.1` jar 内 30 个原版结构） |
|---|---|---|
| `data/<ns>/worldgen/structure/<id>.json` | 结构定义 | `type` / `biomes` / `step` / `terrain_adaptation` / `spawn_overrides`；jigsaw 式另有 `start_pool` + `size` |
| `data/<ns>/worldgen/structure_set/<id>.json` | 放置规则 | `placement`（`spacing` / `separation` / `salt`）+ `structures[]`（`structure` + `weight`） |

**`.nbt` 模板不是硬依赖**（这条纠正了 `docs/10` §6.1 阻塞原因①的表述）：原版 30 个结构分两类——

- **拼图式**（`type: minecraft:jigsaw`，如 village / ancient_city / pillager_outpost）：需要 `start_pool` 指向的 template pool，**`.nbt` 在那里**；
- **代码生成式**（如 `ruined_portal` / `nether_fossil` / `ocean_ruin` / `fortress` / `mineshaft`）：由原版 Java 代码现场生成，**完全不需要 `.nbt`**，产物里也没有 `start_pool` 字段（已逐个核对 30 份原版产物）。

因此宗门结构有**两条路**，成本差一个数量级：

| 路线 | 需要什么 | 特点 |
|---|---|---|
| **A. 代码生成式** | 写一个 `Structure` 子类 + codec 注册（Java 侧） | 宗门建筑是规则化布局（门/院/塔），代码生成更可控，**且无资产依赖** |
| **B. jigsaw 式拼图** | `.nbt` 模板 + template pool 目录 | 需外部工具（WorldEdit schematic → StructureBlock 导出）产资产，是 §6.1 阻塞点①的真正所指 |

**建议走 A**：不必等 C 提供 `.nbt` 资产就能推进。代价是 `world` 包要引入一个 `Structure` 实现类——**不与 03 §2 的分层冲突**（`Structure` 属 world，core 不 import world）。

#### 4.13.2 表结构（草案，`tables/structures.csv`）

| 列 | 必/可 | 填什么 |
|---|---|---|
| `id` | 必 | `sect_<语义>`，小写下划线（V-NAME 守形状，见 docs/11 §2） |
| `kind` | 必 | 实现路线：`code`（代码生成式，本期唯一可实现值）/ `jigsaw`（需 `.nbt`，本期留空不实现） |
| `biomes` | 必 | 原版生物群系 ID，`\|` 分隔多值，**禁空项**（与 `ores.csv` 同规则） |
| `step` | 必 | 原版生成阶段：`surface_structures` / `underground_structures` / `underground_decoration` |
| `spacing` | 必 | `structure_set` 区域间距（区块）。**必须 > `separation`**，否则原版视作无效而不生成 |
| `separation` | 必 | 区域内最小间距 |
| `salt` | 必 | 原版随机盐（整数）。同 `spacing`/`separation` 下不同 salt 决定不同分布 |
| `terrain_adaptation` | 可 | `none` / `beard_thin` / `beard_box` / `bury` / `encapsulate`，留空 = `none` |
| `_note` | 可 | 备注 |

#### 4.13.3 产物（草案）

每行两份 JSON，字段名逐字对齐 §4.13.1 的原版实证：

- `worldgen/structure/<id>.json` —— `kind=code` 时 `type` 填本命名空间注册名（需 Java 侧先注册 codec）
- `worldgen/structure_set/<id>.json` —— `placement` 三字段 + `structures[]` 单元素带 `weight`

#### 4.13.4 未决 → ADR-022 裁决（2026-10-02，A）

1. **`kind` 路线**：**裁决 `code`**。jigsaw 式需 `.nbt` 模板，而导出链未验证（第 4 项），不能让 M4 收尾等一个未验证的外部工具链；code 式（原版 ruined_portal / nether_fossil 同机制）零资产依赖。`.nbt` 保留为后续美术管线的可选升级路径。
2. `spacing`/`separation`/`salt` 归属：**裁决为内容参数进 `tables/structures.csv`**（与本节草案倾向的"受管进 NUMBERS"不同）。理由：三者与**单个结构强绑定**（每结构一套值，是结构定义的一部分；进 NUMBERS 会让"加一个新结构"必须同时动两张真相源，且 NUMBERS 的 `@@world` 是全局块，放逐结构参数会稀释其语义）。若后续需要按难度/境界调密度，再升为受管数值并另开 ADR。
3. 宗门建筑的具体形态属真相源（`content/LORE.md` §区域卡），**本节只定机制不定内容**——本期生成的是规则化厅堂骨架，形态待 C。
4. `.nbt` 路线（jigsaw 式）的 WorldEdit schematic → StructureBlock 导出链**仍未验证**，本期不走，不阻塞。

### 4.14 维度（ADR-021，2026-10-02 已实现）

- 表：`tables/dimensions.csv`（列：`id,generator_type,noise_settings,biome,coordinate_scale,ambient_light,_note`）
- 产物（每行维度两份，均带 `@generated` 头）：
- `worldgen/dimension_type/<id>.json` —— 维度物理属性；格式基准 = 原版 jar 内 `the_end.json`（1.21.1 实证）
- `worldgen/dimension/<id>.json` —— `type` 引用同 id 的 dimension_type；`generator`（noise + fixed 群系源）
- 校验：`generator_type` 仅 `minecraft:noise`；`coordinate_scale>0`；`ambient_light∈[0,1]`；`noise_settings` 仅许 `minecraft:`（借原版 preset）；`biome` 许 `minecraft:` 或 `strife:`；id 小写 snake_case
- 灵气参数不在本表（成长数值进 NUMBERS `@@world` `upper_qi_min/max`，随 §4.12 rules 直出）

### 4.15 群系（ADR-021，2026-10-02 已实现）

- 表：`tables/biomes.csv`（列：`id,sky_color,fog_color,water_color,water_fog_color,grass_color,temperature,downfall,features,_note`）
- 产物：`worldgen/biome/<id>.json`（格式基准 = 原版 jar 内 `the_end.json`：`features` 11 步数组、颜色十进制 int）
- `features` 列形状 `ores=a;b;`（k=v;，值必须 `strife:` 产物）落 index 6（UNDERGROUND_ORES）
- 颜色/降水/刷怪均为材质属性（04 §7 管线调），非受管数值；`[拟]` 待 C 审定配色

### 4.16 上界装饰（ADR-021，2026-10-02 已实现）

- 表：`tables/realm_decor.csv`（列：`id,block,replace_target,veins_per_chunk,vein_size,y_min,y_max,_note`）
- 产物（每行两份）：`worldgen/configured_feature/<id>.json`（`block_match` 精确匹配——end_stone 不在 `stone_ore_replaceables` tag，tag_match 会静默零替换）+ `worldgen/placed_feature/<id>.json`（count + trapezoid 高度）
- 无 `biome_modifier`：装饰只长自研群系（§4.15 `features` 列直引）；校验：`block` 必须 `strife:`、`replace_target` 必须 `minecraft:`、veins/size>0、y_max≥y_min

### 4.17 地点（世界域，G-4 任务导航 + 灵气地点差异化）

- 表：`tables/places.csv`（列：`id,dimension,name,x,y,z,radius,qi_scale,chapter,_note`）
- 产物（每行一份）：`strife_places/<id>.json`
- 双消费真相源（属「设定/材质」属性，不进 NUMBERS——与 §4.8 矿石 density 同口径，与单结构强绑定，进 NUMBERS 会让「加地点」动两张真相源）：
  - **G-4 任务导航**：任务目标 `type=reach;target=<id>` 的 target 即地点 ID，导航命令按坐标 + 维度算方向距离；
  - **灵气地点差异化**：`qi_scale` 是地点环境系数（05 §2 公式第二项），玩家落在半径内覆盖全局噪声场——闭合 docs/10 §3.3 的 `spirit_field.csv` 欠账。
- 校验（构建期红）：`id` 小写蛇形；`dimension` 须带已知命名空间（`minecraft:`/`strife:`，自定义命名空间无法随注册表校验）；`x/y/z` 整数；`radius>0`；`qi_scale>0 且 ≤10`（>0 是 05 §2「系数恒正」硬约束，越界判误填）；`chapter` 空则省略、非空须 `^[a-z0-9_]+$`。
- 归属判定（运行时）：仅水平面比较（高度不参与），同维度内取最近（更小）圈；`name` 为玩家可见中文，不入 lang（少数固定地点，直接写表）。

## 5. 七钩子在 schema 中的落点（03 §10，H1–H7 必须从 M0 就存在）

| 钩子 | schema 落点 | 读写入口 | 本期实现 |
|---|---|---|---|
| H1 价格 | 所有可交易域（techniques/spells/pills/artifacts/quests）统一 `price: {item_id, count} \| null`；`item_id` 允许 `item_lingshi`（灵石，`[占位]` 物品现在不注册） | 只读字段 + Validator 非负校验（03 §10 `[锚]`） | 校验实现，玩法不实现 |
| H2 声望向量 | `StrifeData.affiliation/reputation`（core 附件）+ `quests.reputation_delta` + `dialog effects.reputation` + `strife_factions` 表 | core 读写入口 | 结构与读写入口，职级/贡献点不实现 |
| H3 因果/flag | 运行时键 `<命名空间>:<章>:<语义>`（冒号分段，与内容 ID 刻意不同，03 §10 末）；`quests.flags_set`、`quests.causality`、DSL `flag(...)` | core flag 表 + 存档 | 键格式与校验实现；恩仇结算不实现 |
| H4 位面 | `strife_periods`/`strife_realms.unlocks.upper_realm_gate` + 维度注册清单（Java 侧，非内容表） | core/world | 注册表在，上界维度不做 |
| H5 全局时钟 | `strife_periods` 表（新增，字段见 §5.1） | SavedData in core | 灵脉潮汐试跑（M4） |
| H6 福缘 | loot `luck_modifier_source`（恒 1.0）+ DSL `luck` 谓词（恒 0） | core 抽取层 | 入口在，无来源 |
| H7 势力关系与战争 | `strife_factions` + `strife_wars`（字段见 §5.2/§5.3） | core + tables | 状态机原语与表结构，剧本不做 |

### 5.1 `strife_periods` — 周期事件声明（`[占位]`，结构先定）

| 字段 | 类型 | 必 | 语义 |
|---|---|---|---|
| `id` | `per_<语义>` | 必 | |
| `kind` | enum `tide/open_window/harvest/war_phase` | 必 | 灵脉潮汐 / 秘境开启窗口 / 灵气丰歉年 / 战争阶段 |
| `period_ticks` | int | 必 | 周期长度（刻），引用 NUMBERS 键或字面量（本域不受 §1.2 规则 4 约束，`[拟]` 待 A 定） |
| `window_sec` | int | 必 | 开启窗口时长 |
| `condition` | DSL string | 可 | 触发谓词（境界/flag） |
| `effect_on_world` | `{type, args}`[] | 必 | 世界后果（改 `ambient_qi_ratio`、生成结构、广播） |

### 5.2 `strife_factions` — 势力（`tables/factions.csv`，M0 只需 2–3 行占位）

`id`(`fac_<语义>`) / `display_name_key` / `alignment` enum `orthodox/demonic/neutral/yao`（`[占位]` 枚举待 STORY 定稿） / `rep_range` = `[-100,100]`（固定，不入表） / `home_region`（`qi_` 区域 ID，可 null） / `relations` `{fac_id: attitude_value}`[]（H7 态度矩阵，-100..100） / `price` 无（势力本身不可交易）。

### 5.3 `strife_wars` — 声明式冲突（`[占位]` 表目录 + 字段，剧本不写）

`id`(`war_<语义>`) / `belligerents` `[fac_id, fac_id]` / `cause_predicate`（DSL） / `phases` `[{id, entry_conditions, world_effects, duration_ticks}]` / `peace_terms`（DSL） / `consequences` `[{type, args}]` / `form` enum `once_mainline/recurring`（**同一场战争两形态同表承载**，ADR-016 变形测试 `[锚]`）。

## 6. 破档登记表

| 项 | 触发 | 迁移策略 |
|---|---|---|
| 新增必填字段 | 旧内容包缺字段 | 该字段必须同时带默认值 + `content_format` 次版本 +1；一个主版本内保持向后读 |
| 字段重命名 | 产物与旧 ID 引用失效 | 保留旧名只读别名一个主版本，DataGen 同时写两键并标注 `@deprecated_key` |
| 枚举新增取值 | Validator 白名单变化 | 不破档，但须同步 lang 与面板文案 |
| ID 重命名 | 04 §4"视同破档" | 旧 ID 迁移映射表 `tables/id_migration.csv` 保留一个主版本 |
| 语义变更（同名字段含义变） | 静默破档，最难查 | **禁止**，一律走重命名 |

## 7. Validator 校验项映射（04 §6 八项 → 规则 ID）

| 规则 ID | 内容 | 来源 |
|---|---|---|
| `V-REF` | 引用存在性：所有 `*_key` / `*_id` / 内容 ID 跨表引用不悬空（占位白名单除外）——**未实现** | 04 §6 |
| `V-DAG` | 任务图无环 + 入口可达 + 奖励闭环 | 04 §6 |
| `V-PROB` | `outputs` / `quality_probs` / `roll_weights` 概率和 = 1（容差 1e-6） | 04 §6 |
| `V-RANGE` | 数值越界：成长比 1.8–2.5、成功率 0–1、寿元单调递增、`price` 非负、`ambient_qi_ratio` 不为 0 | 04 §6 + H1 |
| `V-TEXT` | zh_cn 全覆盖、en_us 非空串、lang key 与内容 ID 同源 | 04 §4/§6 |
| `V-DUP` | 全域 ID 唯一——**已实现**：产物 JSON 的 `id` 与 `tables/*.csv` 第一列合并比对（跨域撞也报，源表行号进报错）；**按来源归并**：一行数据与它生成的产物算同一次声明，两张表共用一个 ID 才算撞，另有「产物数 > 源行数」的拷贝检测，`known-placeholders.csv`/`id_migration.csv` 作为台账不进气泡 ID 空间 | 04 §6 |
| `V-DSL` | 条件表达式可解析 + 谓词合法 + 步数上限 | 04 §6 + 03 §8 |
| `V-FRESH` | 产物 `@generated` 哈希与源表一致——**已实现全**：`@generated` 指名的源表必须仍存在，且头里的 `sha256:` 必须等于该表当前字节哈希；头里没写哈希也算错 | 04 §6 |
| `V-FMT` `[拟]` | `content_format` 落在平台 jar 支持区间；未知字段（严格域）报错 | 04 §9 + §1.2 规则 2 |
| `V-DRIFT` `[拟]` | `_key` 与展开值一致（§3 末） | 本文件 §3 |

`[拟]` 两条需 A 确认后进 04 §6 清单（那条是 `[锚]` 的门禁枚举，扩项要同一提交里改）。

## 8. 未决项（需人拍板）

1. §1.2 规则 2 的"严格未知字段"是否采纳（影响所有自定义域的加载器写法与调试体验）。
2. `strife_realms.id` 破例不加 `realm_` 前缀（§4.1）——涉及 04 §4 域前缀枚举的例外条款。
3. §3 的"`_key` 展开进产物"是否与 04 §5"确定性产物"口径完全一致（我按可审计方向拟的）。
4. H5 `period_ticks` 是否受 05 §1 数值管辖（若受管，需进 NUMBERS 新增 `@@periods` 块）。
5. `alignment` / `chapter` / `objective_type` 枚举取值需与 STORY 定稿对齐——见 `STORY.md` §8 待审清单。
6. `pattern` DSL（§4.4.1）语法由炼丹玩法实现方（B）确认，M2 前冻结。
7. **§4.13 宗门结构契约草案是否采纳**（M4 剩余项的前置）。草案已把原版机制查实（两份产物；`.nbt` 仅 jigsaw 式需要，代码生成式不需要），待拍板：① `kind` 只做 `code` 还是含 `jigsaw`；② `spacing`/`separation`/`salt` 归 NUMBERS 受管还是归内容表；③ 建筑形态内容需 C 提供。**在拍板前 DataGen 不生成、Validator 不检查该表。**

### 8.1 与 `tables/FILLING_GUIDE.md` §6 待确认清单的对账

模板交付方（表侧）提出 14 条疑点，本轮在契约侧的处理：

| 表侧条目 | 状态 | 契约侧处理 |
|---|---|---|
| 1 境界 `ordinal`/`display_name_key`/`tribulation` 无源 | **已定** | §4.1 改为 DataGen 推导项（块顺序 / lang 映射规则 / `unlocks` 含 `tribulation`），不再要求 NUMBERS 增设列 |
| 2 占位白名单路径二选一 | **已定** | 白名单落 `tables/known-placeholders.csv`（§2）。`--tables-root` 参数已接（V-DUP 用它扫源表），读白名单本身随 `V-REF` 一并接 |
| 3 §4.8 灵气场/矿石列归属 | **待定** | 表侧按语义拆表的口径可接受，但 §4.8 是一张合并字段表，需 C 明确"一表一段"还是"两段两表"；`regen_period_key` 只属矿石已在本表确认 |
| 4 裸数值列无单位后缀 | **已定** | §1.2 规则 3 补 `_blocks`/`_nodes`/`_levels`/`_points`/`_count`，字段随之更名：`aoe_radius_blocks`、`durability_points`、`max_depth_levels`（表头同步改） |
| 5 空格 vs null | **已定** | §2：空单元格恒等于缺省（null）；显式空数组写 `()`。DataGen 已按此实现（空格子 → 产物里显式 `null`，`()` → 空容器） |
| 6 `required_spiritroot` 悖论 | **已定** | §4.2 降为可选列，留空 = 无灵根要求（走 `affinity_neutral`） |
| 7 势力/NPC/区域 lang 前缀 | **已定（`[拟]`）** | §4.10 登记 `faction.strife.<id>`、`region.strife.<id>`，并规定前缀只能由契约登记、禁止表内自造 |
| 8 `bs:` 命名空间写法 | **已定** | §4.1：字段值直接写 NUMBERS 键名原值（`bs_fanren_qili`），`bs:` 仅为 §3 的内部记法 |
| 9 嵌套值字面量语法 | **已定（`[拟]`）** | §2 把 `\|`、`( )`、`()` 写入契约并标破档。实现侧现状：`;` 列表与 `k=v` 映射已按此实现，`\|` 与 `( … )`（`()` 除外）DataGen **明确拒绝并报错**，不猜语义 |
| 10 基表 `quests.csv` | **已定** | §4.6 取消基表：跨章共用任务归入其所属章，避免"两表同一 DAG" |
| 11 `chapter` 列与文件名重复 | **待定** | 保留必填列（DAG 与产物路径都靠它，推导会增加隐式耦合），C 若判定冗余再删 |
| 12 `id_migration.csv` 列结构 | **待定** | 表侧 7 列属起草，破档真发生时才需定稿 |
| 13 列顺序是否有语义 | **已定（已实测）** | DataGen 按列名读取，顺序无语义（§2 首行表头即契约）；重排列不算破档。唯一有位置含义的是**首列必须是 `id`**，DataGen 与 Validator 都会当场拒绝 |
| 14 严格未知字段（同 §8 项 1） | **待定** | 影响"填错会怎样"的答案，需 A 拍板；未拍板前 §1.2 规则 2 保持 `[拟]`。实现侧现状：**已接生成器的表**列名拼错 = DataGen 报错（多余列静默忽略）；**未接生成器的表**只有首列被读，且填了行会被"无生成器"门禁拦住 |

另：`known-placeholders.csv` 里 5 行的 `issue` 写的是 `A0-7`（取自 NUMBERS §11 待审标题），**是否为可跳转的真实 issue 号未验证**，合入前须补真实号。

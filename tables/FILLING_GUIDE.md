# tables/ 填表指南（人话版）

> 读者：没有开发经验的内容策划。
> 本指南只做一件事：把 `content/JSON_SCHEMA.md`（字段级契约）翻译成人话。
> **口径冲突时一律以 `content/JSON_SCHEMA.md` 为准**，本指南不是契约，是说明书。
> 数值真相源在 `content/NUMBERS.md`，流程与门禁在 `docs/04-内容管线.md`。

---

## 0. 先说清楚：现在填表还不会进游戏（诚实状态）

这一点必须先看，不要跳过。

| 环节 | 契约里应该有 | 现在实际有 | 证据 |
|---|---|---|---|
| DataGen | 把 CSV 读成 JSON 产物 | **读表器与产物写入器已建好，生成器只接了 1 张表**：`factions.csv` → `data/strife/strife_factions/<id>.json`。其余 16 张表仍无生成器 —— 但**只要往没有生成器的表里填了行，DataGen 会直接报错并指名这张表**，不会静默丢弃 | `mod/tools/datagen/src/main/java/com/strife/tools/datagen/`（`DataGenMain` 注册表 + `FactionGenerator`） |
| Validator | 八项校验（引用存在性、DAG、概率归一、越界、文本、重复 ID、DSL、产物新鲜） | **实现了两项**：`V-DUP`（ID 全域唯一，源表与产物一起扫，**按来源归并**：一行数据与它生成的产物算同一次声明）与 `V-FRESH`（`@generated` 指名的源表必须还在，且哈希必须等于这张表当前的哈希）。其余六项（引用/DAG/概率/越界/文本/DSL）仍未实现 | `mod/tools/validator/src/main/java/com/strife/tools/validator/ValidatorMain.java` |
| 产物 | `data/strife/**.json` + `lang` | 目录还没建立：`factions.csv` 现在没有数据行，所以生成 0 个文件。填一行进去、跑一次 datagen，产物就落在 `mod/content-base/src/main/resources/data/` | 仓库现状 + 本机实测 |

所以本期（M0）这些模板的用途是**定契约**：

- 你现在填的每一行，**只有 `factions.csv` 会真的生成产物**；别的表填了行，DataGen 会红着告诉你"这张表还没有生成器"，需要开工单接上（`docs/04` §5）。
- 现在**会**被拦住的：
  1. 同一个 `id` 出现两次（跨表也算，源表与产物之间按来源归并后再判重）；
  2. 表头第一列不是 `id`、某一行少了格子（多打/少打逗号）、格子带英文双引号；
  3. 有数据行但那张表没有生成器；
  4. 改了源表没重新生成（`V-FRESH` 哈希对不上）；
  5. `--tables-root` 指错目录；
  6. 格子里用了**尚未批准的嵌套语法**（`|` 或以 `(` 开头）—— DataGen 明确拒绝猜。
- 填错别的（引用不存在的 ID、概率不为 1、必填列留空）**还不会**报错 —— 那六项检查器还没建起来，契约里那些"留空即构建失败并指出行号"目前仍是**已定未实现**。
- 表头（列名、列的多少）就是契约本身。改表头 = 改契约 = 破档风险（`JSON_SCHEMA.md` §1.2 规则 5），**必须先改 `content/JSON_SCHEMA.md` 并在同一个提交里对齐 DataGen 与 Validator**（`docs/04` §2 末）。
- 因此：**不要自己加列、删列、改列名、调整列的语义**。有想法写进本指南末尾的"待确认清单"，由 C 拍板。

能做的：把列的含义吃透、把手上的内容按格式排好、先填在本地或草稿里。等生成器接上，一次性提交。

---

## 1. 所有表共同遵守的格式

### 1.1 文件层

- 编码：**UTF-8 无 BOM**；换行：**LF**；逗号分隔；**首行必须是表头**。
- **单元格内不允许出现真实换行**。需要软换行写 `\n`（`JSON_SCHEMA.md` §2）。
- 本指南所有示例行都不含逗号 —— 一旦某格必须含逗号，就得用双引号整格包起来，而 DataGen 是否支持引号转义**尚未验证**，所以约定：**格内一律不用 ASCII 逗号**，改用 `;`、`|`、顿号。
- 列数由表头定死，行是记录。空表（只有表头）是**合法状态**。

### 1.2 四条硬规则（最重要的四句话）

> 出处：`JSON_SCHEMA.md` §1.2 与 §2。前三条来自 §1.2「五条硬规则」，第四条来自 §2 的单元格约定。

**规则一：单位写在列名后缀里，不看列名猜不出单位。**

| 后缀 | 含义 | 例 |
|---|---|---|
| `_sec` | 秒 | `duration_sec` |
| `_ticks` | 游戏刻 | `period_ticks` |
| `_pct` | 百分比，写成 0–1 的小数（20% 写 `0.20`，不写 `20`） | — |
| `_years` | 修行年 | `lifespan_years` |
| `_ratio` | 无量纲倍率/比例 | `qi_rate_ratio`、`ambient_qi_ratio`、`density_ratio` |
| `_prob` | 概率，0–1 | `quality_probs` 里的 `prob` |

契约明文**禁止**出现没有后缀的裸数值单位字段。本目录原有的三处漏项已在契约侧改名收口：`aoe_radius` → `aoe_radius_blocks`（格）、`durability_base` → `durability_points`（点）、`max_depth` → `max_depth_levels`（层）。发现新的裸数值列，写进待确认清单，**别自己改名**。

**规则二：数值不写在表里，写 `*_key` 指向 NUMBERS.md。**

速率、成功率、冷却、消耗、加成、权重、区间这几类数值，全部归 `content/NUMBERS.md` 管。表里只写键名。

- 反例（禁止）：`cooldown_sec` 里填 `4.0`
- 正例：`cooldown_key` 里填 `medium`（值在 NUMBERS §8 `spell_cooldown_sec: { light: 1.0, medium: 4.0, heavy: 12.0 }`）

生成产物时平台会把键展开成实际值并同时保留 `*_key`（`JSON_SCHEMA.md` §3），两边不一致就是校验红。**你只维护键，永远不要在 CSV 里抄一份数字。**

**规则三：生成产物禁止手改。**

`mod/content-base/src/main/resources/**` 下的东西是 DataGen 吐出来的，文件头带 `@generated`。手改会在 CI 的"生成器漂移"检查里直接失败（`docs/04` §5）。要改内容就改这张表，改完重新生成。

**规则四：空格子 = "这项不填，用默认值"，不等于 0、也不等于空字符串。**

- 可留空的列（契约里标"可"）：留空即缺省。例：`required_stage` 留空 = 默认 1。
- 必填的列：留空 = 按契约应"构建失败并指出行号"（该报错**尚未实现**，见 §0）。
- 契约里标"必（可 null）"的列（如 `price`、`projectile`、`timer`）：本指南暂定**留空 = null**；需要非 null 就必须写出值。此暂定见待确认清单第 5 条。
- 0 是一个**有意义的值**（例：`aoe_radius_blocks` = 0 表示单体），想要缺省就留空，不要填 0。

### 1.3 格子里的复杂值怎么写

`JSON_SCHEMA.md` §2 只定死了两条：**列表用 `;` 分隔**、**映射/对象用 `k=v`，多对再用 `;` 分隔**。对象数组、二层嵌套的写法契约没给，本指南采用下面这套（**属于拟稿，需与 DataGen 实现对齐**）：

| 结构 | 写法 | 例 |
|---|---|---|
| 列表 | `值1;值2;值3` | `jin;mu` |
| 对象 / 映射 | `键=值;键=值` | `item_id=herb_ningxu;count=3` |
| 对象数组（多个对象） | 对象之间用 `\|` | `quality=fan;prob=0.7\|quality=di;prob=0.3` |
| 嵌套一层（对象里套对象/对象数组） | 用括号 `( )` 包住内层 | `options=(text_key=dialog.strife.d2;next=end)` |
| 二元区间 `[min,max]` | `min;max` | `0.30;0.70` |

契约里的原话例子，照抄为格式基准：`fac_qingshi=10;fac_yuelai=-5`（映射，`JSON_SCHEMA.md` §2）。

### 1.4 注释列 `_note`

- **以 `_` 开头的列是注释列，DataGen 直接忽略**（`docs/04` §5），只给人看。
- 每张表都有 `_note`，放在最后一列。写什么：这行是谁填的、给谁看、有什么坑、挂在哪个工单。
- `_note` 不能替代工单：真正的占位/待办要在 issue 上登记。

### 1.5 ID 列怎么写（第一列）

- 格式：`<域前缀>_<章>_<语义>`，**全小写、只用下划线**，不许中文、不许大写、不许空格（`docs/04` §4）。
- 允许的域前缀（固定枚举）：`tech_ / spell_ / pill_ / qi_ / quest_ / dlg_ / npc_ / block_ / item_ / ch<N>_`；契约另外登记了拟稿扩展：`art_`（器图）、`fac_`（势力）、`war_`（冲突）、`per_`（周期事件）。
- 章节段取值只能是 `prologue / ch1 / ch2 / …`。第二章主题"五行灵珠"是已定的（`docs/04` §3），示例 ID `ch2_pearl_water` 出自 `docs/04` §4。
- `flag_` **不许**用作内容 ID。flag 是运行时键，格式是 `<命名空间>:<章>:<语义>`（冒号分段，故意和内容 ID 长得不一样，`JSON_SCHEMA.md` §5 H3）。
- `id` 全域唯一（校验规则 `V-DUP`）。
- **改已经提交过的 ID = 破档**：要写 ADR，并在 `tables/id_migration.csv` 登记旧→新映射，保留一个主版本（`docs/04` §4 末、`JSON_SCHEMA.md` §6）。
- 唯一的例外：境界 ID（`fanren`/`qili`/…）不带 `realm_` 前缀，见 §2 与 `JSON_SCHEMA.md` §8 未决项 2。

---

## 2. 境界（realms）为什么没有表 —— 要改境界去哪儿改

**本目录故意不建 `tables/realms.csv`。**

`JSON_SCHEMA.md` §4.1 的标题写得很明确：境界"源：NUMBERS `@@realms`，**无 CSV**"。境界的每一项数值（修为上限、小境界数、寿元、打坐速率、解锁清单、突破成功率键）本身**就是**数值真相源的一部分，再开一张表就会造出两份真相 —— 那是硬规则二和 `docs/05` §1 直接禁止的事。所以"展示与解锁"这一侧也没剩下多少可拆出来的列：

| §4.1 的字段 | 现在写在哪 | 你要改就去哪 |
|---|---|---|
| `id`、`ordinal` | NUMBERS §1 `@@realms` 的键与书写顺序 | `content/NUMBERS.md` |
| `qi_max` / `stage_count` / `lifespan_years` / `sit_rate` | 同上（`@@realms` 块每行一个大括号） | `content/NUMBERS.md` §1 |
| `unlocks`（解锁清单） | 同上，值只能是 `JSON_SCHEMA.md` §1.4 的 `unlock_key` 枚举：`meditation / spiritroot_panel / cultivation_panel / technique_equip / spell_cast / pill_crafting / artifact_slot / item_refine / quest_line_ch1 / tribulation / sect_join / ambient_qi_affinity / soul_scan / upper_realm_gate / ascension` | `content/NUMBERS.md` §1 |
| `breakthrough_success_key` | `@@realms` 里指向 NUMBERS §3 `@@breakthrough` 的键（如 `bs_qili_zhuji`）。**值就写键名原值，不加 `bs:` 前缀**（§4.1 已定；`bs:` 只是 §3 的记法） | 数值改 §3，引用关系改 §1 |
| `ordinal`、`tribulation`、`display_name_key` | **不需要你填** —— 契约 §4.1 已把它们定为 DataGen 推导项：`ordinal` 取 `@@realms` 块的书写顺序，`tribulation` 由 `unlocks` 是否含 `tribulation` 推出，`display_name_key` 由 §4.10 的 `realm.strife.<id>` 规则拼出 | 要改顺序就去改 §1 块的行序（会同时改 `ordinal` 与成长比校验），不要在 NUMBERS 里加列 |

已登记的口径（改的时候会被 Validator 卡，`JSON_SCHEMA.md` §7 `V-RANGE`）：

- 链序固定：`fanren → qili → zhuji → jindan → yuanying → huashen → lianxu → heti → dujie`（凡人→…→渡劫）。后三段是**占位命名**，随 STORY.md 定稿（`docs/05` §3）。
- 相邻境界 `qi_max` 成长比必须落在 **1.8–2.5**（NUMBERS §2 `growth_ratio_min/max`）。改一个境界的上限，等于同时改两段成长比，很容易红。
- 寿元必须**单调递增**。
- 小境界阈值是 `qi_max × i / stage_count` 等分算出来的，**不入表**，不要为它开列。
- 后三段用到的 `bs_placeholder_high_1/2`、`unlock_placeholder_1/2` 以及化神的 `bs_huashen_placeholder` 是占位键，靠 `tables/known-placeholders.csv` 白名单放行（见 §3.15）。

调参流程（`docs/05` §1）：改 `content/NUMBERS.md` → 跑 DataGen + Validator → 蒙特卡洛抽样 → 提交。**数值变更走表格流程，不走聊天。**

---

## 3. 逐表说明

每张表给：用途 → 列清单（`必` = 必填，留空会失败；`可` = 可留空走默认值）→ 一行格式示例 → 这张表最容易错在哪。

> 示例行里的取值只为演示格式。其中**真实存在的名字**只有这些来源：`JSON_SCHEMA.md` §1.4 枚举词表（`jin/mu/shui/huo/tu`、`fan/di/tian/xian`、`huang/xuan/di/tian`、`light/medium/heavy`、`formula_basic/formula_pierce/formula_burst`、`matched/neutral/conflict`、`debuff_heavy_wound/debuff_weak`、`meditation` 等解锁键、任务目标类型）、`NUMBERS.md` 里已有的键（`fanren/qili/zhuji/…`、`bs_*`、`pill_juqi/pill_peiyuan/pill_yanshou`）、以及契约与 `docs/04` §4 出现过的示例 ID（`tech_qingxin_jue`、`quest_prologue_meditation_01`、`ch2_pearl_water`、`fac_qingshi`、`fac_yuelai`、`item_lingshi`、`herb_ningxu`）。其余带 `<…>` 的尖括号表示"这里必须填真实值，但还没定名，等 C 定"，**不许照抄进表**。名字带 `demo` 的是明显的假 ID，同样不许当真填进去。

### 3.1 `techniques.csv` —— 功法（15 列）

产物：`data/strife/strife_techniques/<id>.json`（契约 §4.2）

| 列 | 必/可 | 填什么 |
|---|---|---|
| `id` | 必 | `tech_<章>_<语义>` |
| `grade` | 必 | 功法品阶，只能填 `huang`/`xuan`/`di`/`tian`（黄玄地天阶，§1.4） |
| `element` | 必 | 主属性，五行之一。当前契约只允许**单一**主属性，多属性要走后续 ADR，别填 `jin;mu` |
| `required_realm` | 必 | 最低境界 ID，见 §2 链序 |
| `required_stage` | 可 | 小境界下限整数，留空 = 默认 1 |
| `required_spiritroot` | 可 | 需要的灵根集合，五行列表。**留空 = 无灵根要求**（按 `affinity_neutral` 1.00 结算）；确要显式空数组写 `()`（§2 已定：空格子恒等于缺省） |
| `affinity_rule` | 可 | `matched`/`neutral`/`conflict`；留空则由服务端按 NUMBERS §6 灵根系数算，一般留空 |
| `qi_rate_ratio` | 必 | 功法倍率，无量纲，必须 `0 < 值 ≤ 4`。它是"实际修炼速率"公式里的第四项（`docs/05` §2），不属于 NUMBERS 管辖，所以这里是少见的"直接写数字"的列 |
| `passives` | 可 | 被动效果 ID 列表 |
| `grants_spells` | 可 | 学会即得的法术 ID 列表（必须存在于 `spells.csv`） |
| `faction` | 可 | 归属势力 ID（H2 钩子）。留空 = 无门无派的散修功法 |
| `price` | 必（可 null） | H1 价格钩子，`item_id=…;count=…`，非负。本期一般留空 = null（不可交易） |
| `source` | 可 | 传承来源，四选一：`inherit`/`scroll`/`reward`/`shop`。给 LORE 和文案引用用 |
| `disabled_reason_key` | 可 | 洗髓导致功法变灰时的提示 key。契约明令**禁止静默失效**（`docs/03` §8），有变灰可能的功法请填上 |
| `_note` | 可 | 备注 |

格式示例：

```csv
tech_qingxin_jue,xuan,mu,zhuji,1,mu,matched,1.15,,,fac_qingshi,,inherit,,示例行 取值仅演示 qi_rate_ratio 必须落在 0<值≤4
```

容易错：`grade` 与 `quality_tier` 是两套枚举（功法用 `huang/xuan/di/tian`，丹药法宝用 `fan/di/tian/xian`），别串。`grants_spells` 里的法术 ID 写了就要在 `spells.csv` 存在（`V-REF`）。

### 3.2 `spells.csv` —— 法术（11 列）

产物：`strife_spells`（契约 §4.3）

| 列 | 必/可 | 填什么 |
|---|---|---|
| `id` | 必 | `spell_<章>_<语义>` |
| `element` | 必 | 五行之一 |
| `required_technique` | 必 | 前置功法 ID，必须存在于 `techniques.csv` |
| `cost_key` | 必 | 消耗档位：`light`/`medium`/`heavy`。**不填数字**，值在 NUMBERS §8 `spell_cost_qi` |
| `cooldown_key` | 必 | 冷却档位，同上三档，值在 NUMBERS §8 `spell_cooldown_sec` |
| `damage_formula_key` | 必 | `formula_basic`/`formula_pierce`/`formula_burst`。伤害公式**禁止旁路**（`docs/05` §2），所以没有"自定义伤害"这个选项 |
| `projectile` | 必（可 null） | 弹道 `{speed,gravity,range,pierce_count}`；留空 = null = 瞬时/自身施放。MVP 用原版弹道 |
| `aoe_radius_blocks` | 可 | 范围半径（格），**0 = 单体** |
| `effects` | 可 | `{effect_id,duration_sec,amp}` 列表，走原版状态效果或 strife 自定义 |
| `price` | 必（可 null） | H1。符箓/卷轴类可售 |
| `_note` | 可 | 备注 |

格式示例：

```csv
spell_prologue_demo,mu,tech_qingxin_jue,medium,light,formula_basic,,0,,,示例行 消耗与冷却写档位不写秒数 projectile 留空即瞬时施放
```

容易错：把冷却/消耗写成秒数（违反规则二）；`projectile` 与 `aoe_radius_blocks` 混为一谈（一个是弹道物体，一个是作用范围）。

### 3.3 `pills.csv` —— 丹方（11 列）

产物：`strife_pills`（契约 §4.4）

| 列 | 必/可 | 填什么 |
|---|---|---|
| `id` | 必 | `pill_<语义>`，且**必须与 NUMBERS §7 的键一致**（本期合法值只有 `pill_juqi`、`pill_peiyuan`、`pill_yanshou`；新丹药要先进 NUMBERS §7 加键，再进这张表） |
| `quality_tier` | 必 | `fan`/`di`/`tian`/`xian` |
| `pattern` | 必 | 投料图样 DSL。3×3 网格坐标写作 `r<c`（中心是 `1x1`），`格=材料键` 用 `;` 分隔；火候曲线点写作 `H=[0:0.2,1:0.7]` 这样一段（契约 §4.4.1）。语法由炼丹玩法实现方在 M2 前冻结，**现在填的内容都可能返工** |
| `core_slot` | 必 | 主药物品 ID（决定丹名与效果） |
| `materials` | 必 | `{item_id,count}` 列表，引用的物品必须存在（`V-REF`） |
| `heat_range` | 必 | 火候窗口，写作 `min;max`，要求 `0 ≤ min ≤ max ≤ 1` |
| `outputs` | 必 | `{item_id,count,prob,quality}` 列表，**所有 prob 加起来必须 = 1**（容差 1e-6，`V-PROB`） |
| `effect_key` | 可 | 加成值键，值查 NUMBERS §7 `pill:<id>`（一般就填本丹的 ID） |
| `failure_output` | 可 | 废丹产物 `{item_id,count}`；留空 = 失败即销毁材料（`docs/09` F） |
| `price` | 必（可 null） | H1 |
| `_note` | 可 | 备注 |

格式示例：

```csv
pill_juqi,fan,1x1=herb_ningxu;2x2=<副药ID>,<主药ID 待C定名>,item_id=herb_ningxu;count=3,0.30;0.70,item_id=pill_juqi;count=1;prob=1.0;quality=fan,pill_juqi,,,示例行 数值与物品名仅演示 outputs 概率和须为 1
```

容易错：`outputs` 概率和 ≠ 1（`V-PROB` 会红）；给 NUMBERS §7 里没有的丹名直接建表（那样 `effect_key` 无处可查，`V-REF` 会红）。

### 3.4 `artifacts.csv` —— 器图/炼器（13 列）

产物：`strife_artifacts`（契约 §4.5）

| 列 | 必/可 | 填什么 |
|---|---|---|
| `id` | 必 | `art_<语义>`（`art_` 是契约拟稿的扩展前缀） |
| `blank` | 必 | 器胚物品 ID |
| `restriction_count` | 必 | 禁制数，整数 ≥1（`docs/09` F） |
| `core_slot` | 必 | 核心材料物品 ID |
| `materials` | 必 | `{item_id,count}` 列表 |
| `quality_probs` | 必 | `{quality_tier,prob}` 列表，**和 = 1**（`V-PROB`） |
| `slot_type` | 必 | 装备槽：`weapon`/`armor`/`treasure` |
| `required_realm` | 必 | 境界 ID |
| `active_skill` | 必（可 null） | `{spell_id,cooldown_key}`；留空 = 无主动技能。运行时限速走 `rl:artifact_per_sec`（NUMBERS §10） |
| `passives` | 可 | 被动效果 ID 列表 |
| `durability_points` | 必 | 耐久基值（点），整数 >0 |
| `price` | 必（可 null） | H1 |
| `_note` | 可 | 备注 |

格式示例：

```csv
art_demo,<器胚ID 待C定名>,3,<核心材料ID 待C定名>,item_id=<材料ID>;count=2,fan=0.70;di=0.25;tian=0.05,weapon,qili,spell_id=spell_prologue_demo;cooldown_key=heavy,,120,,示例行 品质概率和须=1 耐久数值仅演示
```

容易错：`quality_probs` 用 `di`（品质 tiers 里的"地"）却和 `slot_type` 的 `treasure` 混填；`cooldown_key` 只能填 `light/medium/heavy` 三档。

### 3.5 `quests_prologue.csv` / `quests_ch1.csv` —— 任务 DAG（各 16 列）

产物：`data/strife/strife_quests/<章>.json`，**一章一个文件**，文件内 `id` 是该章入口节点 ID（`docs/04` §2、契约 §1.3）。两张表**列完全相同**，只是按章分文件；以后每加一章就复制一份 `quests_<章>.csv`（复制文件属于加表，请先和 C 打招呼）。

| 列 | 必/可 | 填什么 |
|---|---|---|
| `id` | 必 | `quest_<章>_<语义>_<NN>`，例：`quest_prologue_meditation_01`（真实示例，出自 `docs/04` §4） |
| `chapter` | 必 | 章节枚举，`prologue` 表就填 `prologue`，`ch1` 表就填 `ch1`。**必须和文件名一致** |
| `entry` | 必 | `true`/`false`。每章**恰好一个** `true`（Validator 校验） |
| `prerequisites` | 必（可空） | 前置任务 ID 列表；入口任务留空 |
| `objectives` | 必 | `{id,type,target,count,optional}` 列表。`type` 只能取 §1.4 `objective_type`：`kill / collect / deliver / talk / reach / sit / breakthrough / craft / pill_craft / artifact_craft / escort / survive`。`target` 填内容 ID 或坐标区域 |
| `conditions` | 可 | 进入前置（境界/flag/物品）的条件 DSL，语法见本指南 §3.7 |
| `rewards` | 必 | `{type,id,count,unlock_key}` 列表，`type` ∈ `item/qi/realm_step/flag/unlock/reputation/spell/technique`。`unlock` 类的 `unlock_key` 必须是 §1.4 枚举成员 |
| `reputation_delta` | 可 | H2 声望：`{faction_id,delta}`，delta 必须在 `[-100,100]`。写法：`fac_qingshi=10;fac_yuelai=-5`（契约原话示例） |
| `causality` | 可 | H3 因果：`{kind,subject_id,note_key}`，`kind` ∈ `debt`/`grudge`/`killing`。**本期只登记结构，结算不实现** |
| `timer` | 必（可 null） | `{sec,fail_goto}`；不限时留空 |
| `fail_goto` | 必（可 null） | 任务可失败时的去向；与 `timer` 独立 |
| `flags_set` | 可 | H3 运行时 flag 键，格式 `<命名空间>:<章>:<语义>`，例 `fac_qingshi:ch1:met_elder` |
| `repeatable` | 必 | `no`/`per_day`/`per_period`。`per_period` 要依赖 H5 的周期 ID，**本期别用** |
| `hidden` | 必 | `true` = 面板隐藏（剧情反转用） |
| `price_reward` | 必（可 null） | 灵石奖励统一走 H1 价格类型，**不要再开第二个货币列** |
| `_note` | 可 | 备注 |

格式示例：

```csv
quest_prologue_meditation_01,prologue,true,,id=1;type=sit;count=1;optional=false,,type=unlock;unlock_key=meditation;count=1,,,,,,no,false,,示例行 序章入口任务 解锁打坐
```

容易错（都是 `V-DAG` 会抓的）：前置关系成环；入口不可达某个必做节点；`fail_goto` 指到一个不该到的节点；`rewards` 引用的物品/法术/功法 ID 不存在；一章出现两个 `entry=true` 或一个都没有。

### 3.6 `dialog_trees_prologue.csv` / `dialog_trees_ch1.csv` —— 对话树（各 7 列）

产物：`data/strife/dialog_trees/<章>.json`，一章一个文件（契约 §4.7）。

| 列 | 必/可 | 填什么 |
|---|---|---|
| `id` | 必 | `dlg_<章>_<语义>` |
| `npc` | 必 | 说话人 `npc_` 开头的 ID（`docs/09` D 世系）。目前还没有任何合法 NPC ID 登记，等 C/剧情定名 |
| `root` | 必 | 入口节点的 id |
| `nodes` | 必 | `{id,speaker,text_key,conditions,next,options}` 列表；`options` 是 `{text_key,conditions,next,effects}` 列表。`text_key` 按 §4.10 规则由节点 id 派生：`dialog.strife.<节点id>` |
| `effects` | 可 | 树级效果 `{type,args}`，`type` ∈ `set_flag / reputation / give_item / start_quest / complete_node / play_sound / teleport` |
| `max_depth_levels` | 必 | 解释器求值深度上限（层），整数。拟稿默认 **16**（防表写错死循环，`docs/03` §8），不确定就填 16 |
| `_note` | 可 | 备注 |

格式示例：

```csv
dlg_prologue_demo,<npc ID 待C定名>,d1,id=d1;speaker=<npc ID>;text_key=dialog.strife.d1;next=d2|id=d2;speaker=<npc ID>;text_key=dialog.strife.d2;options=(text_key=dialog.strife.d2_a;conditions=realm>=qili;next=end),type=start_quest;args=quest_prologue_meditation_01,16,示例行 节点文本另填 dialog_序章_text.csv
```

容易错：`nodes` 里的 `text_key` 和 `dialog_<章>_text.csv` 里的 id 对不上（`V-TEXT`/`V-REF`）；分支绕回根节点形成无限循环；`effects` 里 `start_quest` 的任务 ID 不存在。

### 3.7 条件的写法（对话与任务共用一套 DSL，契约 §4.7.1）

语义来自 `docs/03` §8，语法在契约里冻结。可以直接用的谓词：

- `realm>=qili`、`sub_stage<=3`（境界与小境界比较，`CMP` 支持 `>= <= == !=`）
- `flag(fac_qingshi:ch1:met_elder)`（flag 必须是有合法命名空间的键）
- `item(item_lingshi:10)`（持有某物品数量）
- `reputation(fac_qingshi:>=50)`
- `quest_done(quest_prologue_meditation_01)`
- `affinity(shui)`（灵根亲和）
- `luck>=…` —— H6 预留，**本期恒 0，别用它做门槛**
- 组合用 `&&`、`||`、`!`、括号

契约原话示例：`realm>=qili && flag(fac_qingshi:ch1:met_elder) && item(item_lingshi:10)`

未知谓词、未知 flag、未知 ID 会让校验直接失败（`V-DSL`）。求值步数上限拟稿 1000，超限按 false 处理并记日志 —— 也就是说**条件写太长会被静默判假**，请把复杂逻辑拆成节点。

### 3.8 `dialog_prologue_text.csv` / `dialog_ch1_text.csv` —— 对话文本源（各 5 列）

这是 AI 文本流程（`docs/05` §7）的落地台账，不是 lang 文件。规则在契约 §4.10：

| 列 | 必/可 | 填什么 |
|---|---|---|
| `id` | 必 | **对话节点 id**（就是 `nodes` 里那个 `id`），lang key 由它派生为 `dialog.strife.<id>`，所以**不要**再开一列写 key |
| `text` | 必 | 唯一进 `zh_cn.json` 的正式文本 |
| `variant_a` | 必 | 变体 A。不进游戏，供人工润色时挑选 |
| `variant_b` | 必 | 变体 B，同上 |
| `_note` | 可 | 写明这段用到的设定引用清单（`docs/05` §7 要求 Agent 产文必须附引用清单，防吃书） |

格式示例：

```csv
d1,<节点正式文本>,<变体A>,<变体B>,本段引用设定：<LORE 卡名 尚未建立>
```

三条红线：

1. **每节点必须 `text` + 2 个变体**，三格都不许空（契约要求"文本 + 变体 2 个"）。
2. **版权红线**：任何进 lang 的字符串不得含网文专名与情节（"韩立式""荒古圣体"这类只允许出现在内部调研语里，`docs/05` §7）。禁用词清单在 `content/LORE.md` §7（初稿已起草，**尚未合入**，仍在等内容负责人名义确认）—— 定稿前这条仍要你人工自检，不要假定别人替你看过。
3. zh_cn 是源语言，key 缺失 = 构建失败；en_us 允许占位但**不得为空串**（`docs/04` §6）。这两张表只管 zh_cn。

### 3.9 `spirit_field.csv` —— 灵气场（4 列）

产物：`data/strife/worldgen/…`（契约 §4.8）

| 列 | 必/可 | 填什么 |
|---|---|---|
| `id` | 必 | `qi_<区域>` |
| `region_id` | 必 | 区域粗粒度缓存键（16×16 区块一值，`docs/03` §6）。具体格式由世界生成实现方定，**未验证** |
| `ambient_qi_ratio` | 必 | 环境系数，无量纲倍率。必须落在 NUMBERS §9 的 `world:ambient_qi_min..max`，即 **0.50–2.00**，并且**绝不允许为 0** —— 0 会让玩家"零成长且无法解释"，契约明令 tooltip 要能解释任一 0 系数 |
| `_note` | 可 | 备注 |

格式示例：

```csv
qi_demo,<区域缓存键 待M4定>,1.00,示例行 取值必须落在 0.50–2.00 之间且不得为 0
```

说明：契约里 `regen_period_key` 没有放进这张表 —— 灵气场受周期事件影响是走 H5 的 `periods.effect_on_world`（契约 §5.1 明写"改 `ambient_qi_ratio`"），不在灵气场自己行里挂周期键。此归属判断见待确认第 3 条。

### 3.10 `ores.csv` —— 矿石（7 列）

| 列 | 必/可 | 填什么 |
|---|---|---|
| `id` | 必 | `block_<矿石>` |
| `element` | 必 | 五行属性之一 |
| `placement` | 必 | `{biomes[],y_min,y_max,veins_per_chunk,vein_size}`，写法：`biomes=<原版生物群系ID>;y_min=<整数>;y_max=<整数>;veins_per_chunk=<整数>;vein_size=<整数>` |
| `density_ratio` | 必 | 密度倍率，`0 < 值 ≤ 1` |
| `drop_table` | 必 | 掉落表 ID，引用 `loot_table`（原版 loot 2 格式）。与任务联动的表必须命名成 `loot_quest_<章>_<语义>`（契约 §4.9） |
| `regen_period_key` | 必（可 null） | H5 再生周期键，`per_` 开头。**本期恒留空（null）** |
| `_note` | 可 | 备注 |

格式示例：

```csv
block_demo_ore,shui,biomes=<原版生物群系ID>;y_min=<整数>;y_max=<整数>;veins_per_chunk=<整数>;vein_size=<整数>,0.50,loot_demo,,示例行 生成参数待与 M4 世界生成对齐
```

容易错：`drop_table` 指向不存在的掉落表（`V-REF`）；矿石品质权重（NUMBERS §9 `ore_drop_weights`）**不写在这**，它是掉落表里的事。

### 3.11 `factions.csv` —— 势力（6 列，**本表已接生成器**）

契约 §5.2。M0 只要求 2–3 行占位。**这是 17 张表里目前唯一接了生成器的一张**：填一行、跑一次
`./gradlew :tools:datagen:run`，就会在
`mod/content-base/src/main/resources/data/strife/strife_factions/<id>.json` 落一个产物，
**产物必须与源表同一个提交**（否则 CI 的 datagen 漂移检查红）。

| 列 | 必/可 | 填什么 |
|---|---|---|
| `id` | 必 | `fac_<语义>`。目前契约示例里出现过的合法 ID 只有 `fac_qingshi`、`fac_yuelai` |
| `display_name_key` | 必 | lang key，写 `faction.strife.<id>`（§4.10 已登记该前缀，`[拟]` 待确认）。**前缀只能来自契约登记**，不要自造。**留空不会替你派生**：DataGen 拒绝猜未批准的 `[拟]` 规则，会报错到行号让你自己写 |
| `alignment` | 必 | 阵营：`orthodox`/`demonic`/`neutral`/`yao`。该枚举是 `[占位]`，要等 STORY 定稿（契约 §8 未决项 5） |
| `home_region` | 可（可 null） | 本土灵气区域 ID（`qi_` 开头），留空 = 无固定山门 |
| `relations` | 可 | H7 态度矩阵，写法 `fac_yuelai=-5;fac_x=40`（`;` 分隔，`=` 给值）。**值必须是整数**，写"敌对"这类汉字 DataGen 当场报错；无关系写 `()` |
| `_note` | 可 | 备注 |

**没有** `rep_range` 列（声望区间固定 `[-100,100]`，契约明写不入表），**也没有** `price` 列（势力本身不可交易）。

格式示例：

```csv
fac_qingshi,faction.strife.fac_qingshi,orthodox,qi_demo,fac_yuelai=-5,示例行 势力关系矩阵属 H7 本期只登记结构
```

生成出来的产物长这样（本机实测，删掉 `_note` 列、带上源表哈希）：

```json
{
  "@generated": "from tables/factions.csv @ sha256:938259b3…",
  "id": "fac_probe",
  "display_name_key": "faction.strife.fac_probe",
  "alignment": "neutral",
  "home_region": "qi_zhongyuan",
  "relations": {
    "yuelai": -80
  }
}
```

注意 `rep_range` 不在产物里：契约 §5.2 明写它固定 `[-100,100]` 且不入表，而把这个数写进 DataGen 代码就成了"代码里的受管数值字面量"（`AGENTS.md` 硬规则），所以由平台侧提供。

### 3.12 `periods.csv` —— 周期事件（H5，7 列，**本期不填**）

契约 §5.1，整节标 `[占位]`：结构现在定，内容本期不填。**留下表头，不要提交数据行**；要填得先改契约。

列：`id`(`per_<语义>`) / `kind`(`tide` 灵脉潮汐 / `open_window` 秘境开启窗口 / `harvest` 灵气丰歉年 / `war_phase` 战争阶段) / `period_ticks`(周期长度，刻) / `window_sec`(开启窗口时长，秒) / `condition`(条件 DSL，可空) / `effect_on_world`(`{type,args}` 列表：改 `ambient_qi_ratio`、生成结构、广播) / `_note`。

唯一已定的用例：灵脉潮汐试跑排在 M4。`period_ticks` 是否受 NUMBERS 管辖属契约 §8 未决项 4，所以**这张表现在连数值能不能内联都没定**。

格式（**不要提交**，只为说明列的写法）：

```csv
per_demo,tide,<周期刻数>,<窗口秒数>,,type=<效果类型 待H5定>;args=<参数>,示例行 本期禁止提交数据行
```

### 3.13 `wars.csv` —— 势力冲突（H7，8 列，**本期不填**）

契约 §5.3，同样 `[占位]`。列：`id`(`war_<语义>`) / `belligerents`（两个势力 ID，`;` 分隔，例 `fac_qingshi;fac_yuelai`）/ `cause_predicate`（条件 DSL）/ `phases`（`{id,entry_conditions,world_effects,duration_ticks}` 列表）/ `peace_terms`（DSL）/ `consequences`（`{type,args}` 列表）/ `form`（`once_mainline` 一次性主线 / `recurring` 可复现，**同一场战争两种形态写在同一张表里**，这是 ADR-016 的变形测试要求）/ `_note`。

本期只交付"状态机原语与表结构"，剧本不做（契约 §5 H7 行）。

格式（**不要提交**，只为说明列的写法）：

```csv
war_demo,fac_qingshi;fac_yuelai,reputation(fac_qingshi:<=-50),id=p1;entry_conditions=<条件>;world_effects=<效果>;duration_ticks=<刻数>,<和平条款 DSL>,type=<后果类型>;args=<参数>,once_mainline,示例行 本期禁止提交数据行
```

### 3.14 `id_migration.csv` —— 破档 ID 迁移映射（7 列）

契约 §6 末行、`docs/04` §4 末：ID 重命名视同破档，旧 ID 的迁移映射要在**一个主版本**内保留。

列：`id`（本条迁移记录的 ID，`mig_NNNN` 之类，见待确认第 8 条）/ `old_id` / `new_id` / `domain`（产物域目录名，如 `strife_techniques`）/ `issue`（ADR 或工单号）/ `keep_until_content_format`（保留到哪个 `content_format` 主版本）/ `_note`。

**本期没有任何迁移项**，表头留着即可。格式（不要提交）：

```csv
mig_0001,<旧 ID>,<新 ID>,<产物域目录名>,<ADR 编号>,<主版本号>,ID 重命名必须先有 ADR 与 C/A 签字
```

DataGen 在保留期内会同时写新旧两个键并标 `@deprecated_key`（契约 §6）。

### 3.15 `known-placeholders.csv` —— Validator 占位键白名单（6 列，**本表已带数据行**）

契约 §2 末条 + §5 H1：引用存在性校验（`V-REF`）允许放行"结构已定、值未定"的占位键，但**白名单必须挂 issue 号，禁止长期驻留**。

列：`id`（**被放行的占位键本身**，不遵守内容 ID 命名规范）/ `kind`（占位类型）/ `used_by_field`（谁引用了它，写成 `产物域:字段`）/ `issue` / `remove_when`（什么条件下从白名单删掉）/ `_note`。

初值取自 `content/NUMBERS.md` §1 与 §11 已登记的占位键，共 5 行：`bs_placeholder_high_1`、`bs_placeholder_high_2`、`unlock_placeholder_1`、`unlock_placeholder_2`、`bs_huashen_placeholder`，都挂在 A0-7（NUMBERS §11 交审清单）与"后三段境界命名随 STORY.md 定稿"这条线。

用这张表的规矩：

- 加一行前，先确认这个键在 `content/NUMBERS.md` 里**确实存在并标了 `[占位]`**。白名单是放行，不是造键。
- `issue` 必须是**真实工单/issue 号**。本批初值写的 `A0-7` 取自 NUMBERS §11 的待审标题，它是否等于可跳转的 issue 号**未验证**，请 C 补成真实号。
- 占位键一旦定稿（例如后三段境界改名为正式名），要**同时**删掉 NUMBERS 里的 `[占位]` 标注和本表白名单行，否则等于留了根永远不放行的拐杖。
- 位置已定：契约 §2 认这张 `tables/known-placeholders.csv`（与源表同一编辑入口）。Validator 已经接 `--tables-root`（现在用它扫源表 ID），但**还没读这张白名单** —— 因为放行逻辑属于 `V-REF`，那条检查本身尚未实现。等 `V-REF` 上线时必须一并读它，否则后三段境界的占位键会让构建红。

---

## 4. 该去哪儿查已有的 ID（速查）

| 要查的东西 | 去哪儿看 | 说明 |
|---|---|---|
| 境界 ID 与其数值 | `content/NUMBERS.md` §1（`@@realms`） | 链序、上限、寿元、打坐速率、解锁键 |
| 突破成功率键 | `content/NUMBERS.md` §3（`@@breakthrough`） | `bs_*` 系列 |
| 丹药加成键 | `content/NUMBERS.md` §7（`@@pills`） | 本期只有 `pill_juqi`/`pill_peiyuan`/`pill_yanshou` |
| 伤害公式键 | `content/NUMBERS.md` §8（`@@combat`） | `formula_basic`/`formula_pierce`/`formula_burst` |
| 消耗/冷却档位 | `content/NUMBERS.md` §8 | `light`/`medium`/`heavy` |
| 灵根系数、亲和系数、抽品阶权重 | `content/NUMBERS.md` §6 | 不要抄进表，引用即可 |
| 环境系数合法区间 | `content/NUMBERS.md` §9 | `ambient_qi_min/max` = 0.50/2.00 |
| 限速参数 | `content/NUMBERS.md` §10 | `rl:*`，运行时真值以块为准 |
| 校验区间（成长比等） | `content/NUMBERS.md` §2 | `@@limits` |
| 枚举词表（五行、品阶、阶、章节、解锁键、目标类型…） | `content/JSON_SCHEMA.md` §1.4 | Validator 白名单就源于此 |
| 字段契约与必/可 | `content/JSON_SCHEMA.md` §4、§5 | 本指南 §3 是它的翻译 |
| 产物路径与 ID 规则 | `docs/04-内容管线.md` §2、§4 | 域目录枚举、命名规范 |
| 内容 ID 命名示例 | `docs/04` §4：`tech_qingxin_jue`、`quest_prologue_meditation_01`、`ch2_pearl_water` | 只有这三个是手册给的真示例 |
| 功法/法术/丹药/器图/势力/矿石已有条目 | 本目录对应的 CSV 本身 | 现在除了白名单全是空表 |
| NPC 名单、世界观、章节大纲 | `content/STORY.md`（章节与序章十节点骨架）、`content/LORE.md`（设定卡、区域卡、势力卡、NPC 卡、禁用词表） | 两份**初稿已起草但尚未合入**（真相源须内容负责人名义确认）。在它们定稿前，涉及定名的格子仍留 `<待定名>`，不要自造名字当既成事实 |

---

## 5. 改了一张表要跑什么命令，CI 会怎么告诉你错在哪

### 5.1 本地三连（在仓库根目录）

```bash
cd mod
./gradlew :tools:datagen:run   # 由 tables/*.csv + NUMBERS.md 生成产物
./gradlew validator            # 校验产物（是 :tools:validator:run 的别名）
./gradlew build                # 编译与整体构建
```

交单前的完整门禁（`AGENTS.md` 硬规则，四条都要绿并附输出）：

```bash
./gradlew spotlessCheck build test validator
```

预期输出（本机实测，**这就是 §0 说的现状**）：

- `datagen: tables-root=… resources-root=… tables=17 rows=5 generators=1 products=0 problems=0`
  - `tables=17` = 17 张表**全部被读进来了**（表头不合规当场报错）；`rows=5` 全来自 `known-placeholders.csv`（台账表，本来就不生成产物）；`generators=1` = 只接了 `factions.csv`；`products=0` = 势力表还没行。
- `validator: data-root=… tables-root=… json-files=0 csv-files=17 checks=2 problems=0`
  - `json-files=0` = 还没有产物可查；`checks=2` = 八项里装了 `V-DUP` 与 `V-FRESH`。

往 `factions.csv` 填一行后，这两行会变成 `products=1 json-files=1`，产物落在
`mod/content-base/src/main/resources/data/strife/strife_factions/<id>.json`，**必须连产物一起提交**（CI 的"生成器漂移"检查就是比对它）。

### 5.2 CI 会跑什么（`docs/04` §8）

```
spotlessCheck → build → unitTest（realm/quest 单测 100% DAG 用例）
→ datagen 新鲜度 → validator 全项 → 模块依赖断言 → server jar headless 冒烟 → 附件内存快照测试
```

其中和你填表直接相关的两条：

- **datagen 新鲜度**：你改了源表没重新生成、或者手改了产物，这里就红（"生成器漂移"）。
- **validator 全项**：契约里的八项 + 两条拟稿项。每晚还会追加蒙特卡洛 10 万次丹方期望抽样。

### 5.3 报错长什么样（怎么读）

契约要求（`docs/04` §6 末）：Validator 报错必须给出**文件路径 + JSON pointer + 人话说明**，因为这是能否自助修复的关键。

现在**已实现**的两项（`V-DUP`、`V-FRESH`），输出形如：

ID 撞车（本机实测过的输出，位置要么是 `文件:行号`，要么是产物路径）：

```
validator: duplicate id 'fac_probe' declared by 2 independent sources: tables/factions.csv […/strife_factions/fac_probe.json, …/tables/factions.csv:2] vs tables/periods.csv […/tables/periods.csv:2]
```

三种形态，读法不同：

| 报错片段 | 意思 |
|---|---|
| `declared by N independent sources: A […] vs B […]` | 同一个 ID 被**两张不同的表**声明（或一张表 + 一份没有 `@generated` 头的手改产物） |
| `declared N times within tables/x.csv at [x.csv:2, x.csv:7]` | 同一张表里两行用了同一个 `id` |
| `N generated files for M source row(s) in tables/x.csv` | 产物被手抄过，或源表行删了没重新生成 |

> **一行数据和它生成的产物算同一次声明**，不会自己跟自己撞 —— 这是刻意的：否则 DataGen 一跑，每个产物都会和自己的源行撞红，这道门禁永远不可能绿。

**同一个 ID 跨域撞也算**（`docs/04 §6` 写的是“全域唯一”，lang key 与内容 ID 一一映射，04 §4）。解决办法是改其中一行的 `id`，别删测试、别改 CI 门禁让它变绿。

改表没重新生成（`V-FRESH`，本机实测）：

```
validator: …/fac_probe.json: @generated claims sha256:938259… but tables/factions.csv is now sha256:fcf809… — the table changed without regenerating (docs/04 §1: 产物永远不被手工编辑)
```

往没有生成器的表里填了行（DataGen 自己拦，本机实测）：

```
datagen: tables/pills.csv has 1 rows but no generator is registered in DataGenMain.generators() — this content would silently not exist in game
```

表头写错：`validator: <文件>: first header column is 'name', must be 'id' (content/JSON_SCHEMA.md §2)`。
格子数对不上（多半是多打/少打了一个逗号）：`datagen: <文件>:3: has 5 cells but the header has 6 columns — commas inside a cell are forbidden (tables/FILLING_GUIDE.md §1.1), use ';' or '、' instead`。
用了没批准的嵌套写法：`datagen: <文件>:2: column 'relations' uses nested grammar ('|' / '(' ) that is still [拟] in content/JSON_SCHEMA.md §2 — DataGen does not guess it.`
参数指错目录：`--tables-root <路径> is not a directory (typo? CI must not skip the source tables)` —— 这是刻意的：目录不存在**不算通过**，否则这道门禁会假绿。

规则 ID 与含义对照（契约 §7），报错时按这个自查：

| 规则 | 意思 | 你多半犯了什么 |
|---|---|---|
| `V-REF` | 引用不悬空 | 写了不存在的物品/法术/任务/掉落表 ID，或 `*_key` 在 NUMBERS 里没有 |
| `V-DAG` | 任务图无环、入口可达、奖励闭环 | 前置成环、`fail_goto` 指错、一章没有入口 |
| `V-PROB` | 概率和 = 1（容差 1e-6） | `outputs` / `quality_probs` 加起来不是 1 |
| `V-RANGE` | 数值越界 | 成长比不在 1.8–2.5、成功率不在 0–1、寿元不单调、`price` 为负、`ambient_qi_ratio` 为 0 |
| `V-TEXT` | zh_cn 全覆盖、en_us 非空串 | 有内容 ID 没有对应 lang key |
| `V-DUP` | 全域 ID 唯一（**已实现**：源表与产物一起扫，跨域也算，一行数据与其产物按同一来源归并） | 同一个 ID 出现两次 |
| `V-DSL` | 条件表达式可解析、谓词合法 | 条件里写了未知 flag / 未知 ID / 未知谓词 |
| `V-FRESH` | 产物哈希与源表一致（**已实现全**：`@generated` 指名的源表必须还在，且其当前哈希必须等于头里写的那一个） | 改了源表没重新跑 datagen，或手改了产物 |
| `V-FMT`（拟稿） | `content_format` 落在支持区间、未知字段报错 | 列名拼错、多加了契约没有的列 |
| `V-DRIFT`（拟稿） | `*_key` 与展开值一致 | 正常填表不会碰到，DataGen 内部一致性 |

还有一条演练用例（W4）：故意在表里留一个悬空引用，构建**必须**失败。目前它还做不到 —— 这也说明 §0 的判断：现在处在"把契约钉死"的阶段，不是"填表出内容"的阶段。

---

## 6. 待确认清单（未验证 / 需 C 拍板，A 终审破档项）

本表 15 条疑点已与契约对过一轮账。**逐条的处理结果与依据都在 `content/JSON_SCHEMA.md` §8.1（对账表）**，这里只留结论：

### 6.1 已在契约侧收口（照新口径填即可）

- 境界的 `ordinal` / `tribulation` / `display_name_key` 改为 DataGen 推导项，**表里不要有它们**（§4.1）。
- 占位白名单落在 `tables/known-placeholders.csv`；`V-REF` 上线前 Validator 需补 `--tables-root` 参数（§2）。
- 三个裸数值列补了单位后缀并同步改了表头：`aoe_radius_blocks`、`durability_points`、`max_depth_levels`；规则 3 的后缀枚举也补齐（§1.2）。
- 空格子恒等于缺省（null），显式空数组写 `()`（§2）。
- `required_spiritroot` 降为可选，留空 = 无要求（§4.2）。
- 势力/区域 lang 前缀登记为 `faction.strife.<id>`、`region.strife.<id>`，前缀只能由契约登记（§4.10）。
- 嵌套值字面量语法（`;`、`|`、`( )`）写进契约并标 `[拟]`（§2）。
- 取消基表 `tables/quests.csv`，任务只有一章一表（§4.6）。
- `breakthrough_success_key` 写键名原值，不加 `bs:` 前缀（§4.1）。
- DataGen 按列名读取，**列顺序无语义**，重排不破档（§2）。已实测确认，不再是"未验证"。
- **空格子 = null、`()` = 空数组**已落进实现：DataGen 就是这么读的（§2）。
- **嵌套语法 `|` 与 `( … )` 现在会被 DataGen 直接拒绝**并指到行号，不是"静默生成错产物"。要真用这语法，先让 C 拍板 §2 的 `[拟]`，再实现读取器。
- **多余的列不报错、也不进产物**（DataGen 只按列名取自己要的）。**已接生成器的表**（现在只有 `factions.csv`）改错列名 = 当场红；**没接生成器的表**改错列名暂时不会被 DataGen 发现（V-DUP 只认第一列）。"多加一列是否要报错"见 §6.2 第 5 条。

### 6.2 仍悬空，别按暂定口径大批量填

1. **§4.8 灵气场与矿石的列归属**：契约是一张合并字段表，本批按语义拆成 `spirit_field.csv` / `ores.csv`（`regen_period_key` 只归矿石）。拆分是否被认可需 C 确认。
2. **`id_migration.csv` 的 7 列结构**属模板起草，契约 §6 只说"要有这张表、保留一个主版本"。真发生破档前不定稿。
3. **`quests.chapter` 与文件名重复**：本轮**保留**必填列（产物路径与 DAG 校验都靠它，隐式推导不利于排错），C 若判冗余再删。
4. **`alignment` / `chapter` / `objective_type` 三个枚举**待 `STORY.md` 定稿（契约 §8 未决项 5）；符箓/阵法/灵植三表（`tal_`/`form_`/`plant_`）按 §4.11 只占位不填，本批未建空表头。
5. **§1.2 规则 2「严格未知字段」是否采纳**（契约 §8 未决项 1）。实现后的口径是这样，别再按"未定"猜：
   - 已接生成器的表（现在只有 `factions.csv`）：**列名拼错 = DataGen 报错到文件名**（`header has no 'x' column, contract drift?`），多余的列被读进来但没人消费，**既不报错也不进产物**。
   - 还没接生成器的表：DataGen 只看首列（V-DUP 用它），其余列**根本不读**。
   也就是说"改表头 = 改契约"这件事，现在只有 factions 一张表有代码兜底。规则 2 若判"多余列也算错"，需要给每张表加声明式列白名单，那是另一个工单。
6. **`known-placeholders.csv` 的 `issue` 列写的是 `A0-7`**（取自 NUMBERS §11 待审标题），**是否为可跳转的真实 issue 号未验证**，合入前补真号。
7. **剩下 16 张表各自需要一张"接生成器"的工单**（`docs/04` §5 说是 A1/C1 的活）。在那之前往这些表填行，DataGen 会红着拒绝 —— 这是**特性不是故障**：它宁可不让你填，也不让内容静默消失。谁要开填，先把这张表的生成器工单立起来。

---

## 7. 本目录文件一览

| 文件 | 列数 | 数据行 | 状态 |
|---|---|---|---|
| `techniques.csv` | 15 | 无 | 表头 = 契约 §4.2 |
| `spells.csv` | 11 | 无 | §4.3 |
| `pills.csv` | 11 | 无 | §4.4 |
| `artifacts.csv` | 13 | 无 | §4.5 |
| `quests_prologue.csv` | 16 | 无 | §4.6 |
| `quests_ch1.csv` | 16 | 无 | §4.6 |
| `dialog_trees_prologue.csv` | 7 | 无 | §4.7 |
| `dialog_trees_ch1.csv` | 7 | 无 | §4.7 |
| `dialog_prologue_text.csv` | 5 | 无 | §4.10 |
| `dialog_ch1_text.csv` | 5 | 无 | §4.10 |
| `spirit_field.csv` | 4 | 无 | §4.8（拆分见待确认 3） |
| `ores.csv` | 7 | 无 | §4.8 |
| `factions.csv` | 6 | 无 | §5.2（M0 允许 2–3 行占位）**已接生成器，填了就会出产物** |
| `periods.csv` | 7 | 无 | §5.1，`[占位]` 本期不填 |
| `wars.csv` | 8 | 无 | §5.3，`[占位]` 本期不填 |
| `id_migration.csv` | 7 | 无 | §6，本期无迁移项 |
| `known-placeholders.csv` | 6 | **5 行** | §2 末条白名单，初值取自 NUMBERS §1/§11 |
| `realms.csv` | **不建** | — | 境界无 CSV，见 §2 |

每张表都以 `id` 开头、以 `_note` 结尾（契约 §2 强制两列）。所有示例行都在本指南里，**没有写进 CSV**：模板是空表，等你按格式填第一行。

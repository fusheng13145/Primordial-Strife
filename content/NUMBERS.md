# NUMBERS.md — 全部数值的唯一真相源（ADR-005 / 05 分册 §1）

> **状态：A0-7 骨架表初稿（Agent 起草，未经 A/C 审定，不构成定稿）。**
> 本文件是数值与字段的唯一真相源：任何受管数值只允许写在这里，代码与 config 不得出现第二份（05 分册 §1）。
> 表内标 `[锚]` 的值来自 05 分册已拍板口径；标 `[拟]` 的值是本轮初稿提案，需 A+C 审定后方可作为基线。

## 0. 解析约定（草案，最终契约以 `content/JSON_SCHEMA.md` 为准）

- 每个数据块 = 一个二级标题 + 紧随其后的**唯一** ```yaml 围栏块；DataGen 以 `@@<id>` 行定位块（`#` 注释行内的 `@@` 除外）。
- 块内只允许 YAML 标量/映射/序列；键用 `snake_case`；单位写在键名后缀（`_sec` 秒、`_ticks` 刻、`_pct` 百分比 0–1 小数、`_years` 修行年）。
- ID 引用一律用 04 分册 §4 规范的全小写下划线内容 ID，不得出现中文键。
- 值后允许行内注释（`# [锚]` / `# [拟]`），DataGen 解析前剥离。

## 1. 境界链（05 分册 §3）

链序：`凡人 → 练气 → 筑基 → 金丹 → 元婴 → 化神 → 炼虚 → 合体 → 渡劫`。
后三段为占位命名，随 STORY.md 定稿（05 §3）；`unlocks` 为功能/内容清单键，M0 只登记不实现。

@@realms
```yaml
# qi_max = 该境界修为上限（突破到新境界后 qi 归 0，重新计）
# stage_count = 小境界（层/段）数；小境界阈值按 qi_max × i / stage_count 等分，不入表
# unlocks = 该境界解锁的功能/内容清单键（04 §2 strife_realms 必填字段）
# growth_ratio = qi_max(n) / qi_max(n-1)，Validator 硬校验区间见 §2
fanren:   { qi_max: 100,   stage_count: 1, lifespan_years: 80,    sit_rate: 0.50,  unlocks: [], breakthrough_success_key: bs_fanren_qili } # [拟]
qili:     { qi_max: 230,   stage_count: 9, lifespan_years: 120,   sit_rate: 0.80,  unlocks: [meditation, spiritroot_panel, cultivation_panel], breakthrough_success_key: bs_qili_zhuji } # [拟]
zhuji:    { qi_max: 520,   stage_count: 4, lifespan_years: 200,   sit_rate: 1.25,  unlocks: [technique_equip, spell_cast, pill_crafting], breakthrough_success_key: bs_zhuji_jindan } # [拟]
jindan:   { qi_max: 1180,  stage_count: 4, lifespan_years: 400,   sit_rate: 2.00,  unlocks: [artifact_slot, item_refine, quest_line_ch1], breakthrough_success_key: bs_jindan_yuanying } # [拟]
yuanying: { qi_max: 2650,  stage_count: 4, lifespan_years: 800,   sit_rate: 3.20,  unlocks: [tribulation, sect_join, ambient_qi_affinity], breakthrough_success_key: bs_yuanying_huashen } # [拟]
huashen:  { qi_max: 6000,  stage_count: 4, lifespan_years: 1500,  sit_rate: 5.10,  unlocks: [soul_scan, upper_realm_gate], breakthrough_success_key: bs_huashen_placeholder } # [拟]
lianxu:   { qi_max: 13500, stage_count: 4, lifespan_years: 3000,  sit_rate: 8.20,  unlocks: [unlock_placeholder_1], breakthrough_success_key: bs_placeholder_high_1 } # [拟][占位]
heti:     { qi_max: 30000, stage_count: 4, lifespan_years: 6000,  sit_rate: 13.00, unlocks: [unlock_placeholder_2], breakthrough_success_key: bs_placeholder_high_2 } # [拟][占位]
dujie:    { qi_max: 67000, stage_count: 1, lifespan_years: 12000, sit_rate: 20.50, unlocks: [ascension], breakthrough_success_key: bs_dujie_ascend } # [拟][占位]
```

设计口径 `[拟]`：`sit_rate` 增速固定约 1.6×，低于 `qi_max` 增速约 2.25×，因此单境界纯打坐时长（`qi_max / sit_rate`）随境界递增：3.3 / 4.8 / 6.9 / 9.8 / 13.8 / 19.6 / 27.4 / 38.5 / 54.5 分钟（未计灵根·环境·功法·丹药四项系数，实际更快；突破的材料与任务门槛承担剩余节奏控制，不靠堆 `qi_max`）。相邻 `qi_max` 成长比实测落在 2.22–2.30，全部在 §2 的 1.8–2.5 区间内。

## 2. 校验区间（Validator 硬门禁，05 分册 §2 / 04 分册 §6）

@@limits
```yaml
growth_ratio_min: 1.8        # [锚] 相邻境界总成长比下限
growth_ratio_max: 2.5        # [锚] 上限
success_rate_min: 0.0        # [锚] 成功率取值域
success_rate_max: 1.0        # [锚]
lifespan_monotonic: true     # [锚] 寿元随境界单调递增
attachment_budget_bytes: 2048 # [锚] 单玩家全部附件 ≤2KB（03 §3）
chunk_gen_budget_ms: 2        # [锚] 灵气场 chunk 生成预算（07 M4 准出）
spell_latency_budget_ms: 100  # [锚] 法术响应延迟预算（07 M2 准出）
# --- S2C 同步预算（03 §5/§6，core 同步框架查表用；产物见 JSON_SCHEMA §4.12）---
sync_packets_per_sec_idle: 5   # [锚] 静止时单玩家发包上限（03 §6 性能红线）；有变更才发，无变更 0 包
sync_payload_max_bytes: 32768  # [锚] 单网络包上限（03 §5）；超限必须分片或改拉取式
sync_full_resync_sec: 30       # [拟] 周期性全量重同步间隔，防 delta 漂移（丢包/重连的兜底）
```

## 3. 突破与渡劫成功率（05 分册 §3/§4，ADR-008）

@@breakthrough
```yaml
bs_fanren_qili:    { base: 0.95, fail_step: 0.00, floor: 0.95 }  # [拟] 入门突破不卡人
bs_qili_zhuji:     { base: 0.90, fail_step: -0.05, floor: 0.40 } # [锚] 练气→筑基 90%
bs_zhuji_jindan:   { base: 0.85, fail_step: -0.05, floor: 0.40 } # [锚] 筑基→金丹 85%
bs_jindan_yuanying:{ base: 0.75, fail_step: -0.05, floor: 0.40 } # [锚] 金丹→元婴 75%→70%，失败每次 -5%，下限 40%
bs_yuanying_huashen:{ base: 0.65, fail_step: -0.05, floor: 0.40 } # [拟]
bs_huashen_placeholder: { base: 0.55, fail_step: -0.05, floor: 0.40 } # [拟][占位]
bs_placeholder_high_1: { base: 0.45, fail_step: -0.05, floor: 0.35 } # [拟][占位]
bs_placeholder_high_2: { base: 0.35, fail_step: -0.05, floor: 0.30 } # [拟][占位]
bs_dujie_ascend:   { base: 0.25, fail_step: -0.05, floor: 0.20 } # [拟][占位] 天劫，失败进 §4 渡劫结算
```

`fail_step` 语义 `[拟]`：同一境界每次失败后的累计修正（服务端记录 `bt_attempts`），到 `floor` 封底；面板必须显示"本次成功率 = base + fail_step × attempts"。

@@breakthrough_cost
```yaml
qi_reset_ratio_min: 0.60     # [锚] 突破失败：修为回退至上限的 60%（下限）
qi_reset_ratio_max: 0.80     # [锚] 上限
tribulation_debuff_key: debuff_heavy_wound # [锚] 渡劫失败附加"重伤"
debuff_all_stat_delta: -0.30 # [锚] 重伤：全属性 -30%
debuff_duration_sec: 1800    # [拟] 重伤限时（服务端权威）
tribulation_drop_radius: 16  # [拟] 天劫材料散落劫台半径（格），可拾回
```

## 4. 寿元与大限（05 分册 §4，ADR-008）

@@lifespan
```yaml
years_per_realtime_sec: 0.000833 # [拟] = 1 修行年 / 20 分钟现实时间（打坐期间同样流逝）
years_per_sit_realtime_sec: 0.000833 # [拟] 与挂机一致，避免"打坐即长生"
limit_recovery_pill_key: pill_yanshou  # [拟] 续命玩法路径入口（丹药/任务，绝不删角色）
dasheng_realm_drop_stages: 1     # [拟] 大限：回退一档境界下限（段数按 stage_count 计）
dasheng_reset_years_ratio: 0.05  # [拟] 大限后寿元重置为"残余寿元" = 该境界 lifespan_years × 0.05
dasheng_debuff_key: debuff_weak  # [拟] 大限附加虚弱状态
```

## 5. 打坐（M1，realm 包，服务端权威）

@@meditation
```yaml
interrupt_progress_keep: 0.90  # [拟] 打断后已积累修为保留比例（惩罚 10%）
interrupt_cooldown_sec: 30     # [拟] 打断后重新入定冷却
tick_interval_ticks: 40        # [拟] 打坐结算降频（07 M1"降频+打断惩罚"），每 2 秒结算一次
offline_gain_allowed: false    # [拟] 离线不产出修为（服务端权威 + 反滥用）
```

实际修炼速率（05 分册 §2 锁定公式，四项系数全部来自表）：
`速率 = sit_rate(查表) × 灵根系数(§6) × 环境系数(AmbientQi) × 功法倍率(techniques 表) × (1 + 丹药加成(§7))`

## 6. 灵根系数（05 分册 §2；C1-1"灵根五行×4 品阶"）

@@spiritroot
```yaml
# 品阶系数：作用于打坐基础速率
quality_tier_1: 1.40   # [拟] 天灵根（单系）
quality_tier_2: 1.20   # [拟] 双灵根
quality_tier_3: 0.95   # [拟] 三灵根
quality_tier_4: 0.70   # [拟] 四/五灵根（杂灵根）
# 五行亲和：功法 required_spiritroot 与玩家灵根集合的关系
affinity_matched: 1.25  # [拟] 功法属性在玩家灵根内
affinity_neutral: 1.00  # [拟] 无关
affinity_conflict: 0.60 # [拟] 相克（面板必须给出 tooltip 解释，05 §2）
roll_weights: { tier_1: 2, tier_2: 8, tier_3: 25, tier_4: 65 } # [拟] 角色创建时服务端抽签权重（和 = 100）
```

## 7. 丹药加成（M2 炼丹；C1-1"入门丹方 3 张"）

@@pills
```yaml
pill_juqi:    { qi_rate_bonus: 0.25, duration_sec: 300,  repeat_step: 0.05, repeat_floor: 0.10 } # [拟] 聚气丹
pill_peiyuan: { qi_rate_bonus: 0.60, duration_sec: 120, repeat_step: 0.10, repeat_floor: 0.20 } # [拟] 培元丹（短效高倍）
pill_yanshou: { lifespan_years_gain: 10, max_gain_per_realm: 30 }                               # [拟] 延寿丹，续命路径，受单次境界封顶
# repeat_* 语义 [拟]：同种丹药在冷却窗内重复服用，加成按 repeat_step 递减至 repeat_floor（防堆叠挂机化）
```

## 8. 伤害与战斗（M2；公式禁止旁路，05 分册 §2）

@@combat
```yaml
# damage = formula_key 查表(base, realm_coeff × technique_bonus × (1 - target_resist)) - 穿透
realm_coeff: { fanren: 1.0, qili: 1.5, zhuji: 2.4, jindan: 4.0, yuanying: 7.0, huashen: 12.0, lianxu: 20.0, heti: 33.0, dujie: 55.0 } # [拟]
formula_basic:    { base: 6,  scale: realm_coeff }               # [拟] 基础法术
formula_pierce:   { base: 4,  scale: realm_coeff, pen: 0.5 }     # [拟] 穿透系
formula_burst:    { base: 12, scale: realm_coeff, self_cost_qi: 0.15 } # [拟] 爆发系，消耗当前修为比例
spell_cost_qi:    { light: 6,  medium: 18, heavy: 45 }           # [拟] 灵气/修为消耗三档
spell_cooldown_sec: { light: 1.0, medium: 4.0, heavy: 12.0 }     # [拟]
artifact_cooldown_sec: 20                                        # [拟] 法宝主动技能
```

## 9. 世界与掉落（M4 / 04 分册 §2）

@@world
```yaml
ambient_qi_min: 0.50   # [拟] 环境系数下限（绝不为 0，避免"零成长无可解释"）
ambient_qi_max: 2.00   # [拟] 上限（灵脉/宗门核心区）
ore_drop_weights: { common: 70, rare: 25, spirit: 5 }  # [拟] 矿石品质权重（和 = 100）
beast_loot_rolls: 3    # [拟] 妖兽掉落表默认 roll 次数
herb_grass_drop_prob: { item_ningxu: 0.12, item_duanxue: 0.05 }  # [拟] 草丛采集 MVP：破坏草类方块时各草药独立掷此概率（序章 #3/#8 的采集来源；键 = 物品内容 ID 全名），待 C 审定
```

## 10. 限速参数（03 分册 §4 表内基线；运行时真值以本块为准）

@@rate_limits
```yaml
cast_per_sec: 5      # [锚] 施法意图
sit_per_sec: 2       # [锚] 打坐起止
quest_per_sec: 2     # [锚] 任务交互/交付
artifact_per_sec: 5  # [锚] 法宝主动使用
breakthrough_per_min: 6  # [拟] 突破请求（防连点刷 fail_step）
```

## 11. 待审项（A0-7 交审清单）

- `[拟]` 值共 6 处成块：§1 境界量级、§3 小境界成功率梯度、§4 寿元流速、§6 灵根系数、§7 丹药耐药、§8 战斗量级。请 A（节奏/可行性）+ C（内容口径）审定后把 `[拟]` 标注去除或改值。
- §4 `years_per_realtime_sec` 与 §1 的时长曲线强耦合，先定 §4 再回校 §1。
- 后三段境界（炼虚/合体/渡劫）四个必填字段齐但命名与 `unlocks` 待 STORY.md 定稿（05 §3）；M0 仅占位，Validator 需对 `bs_placeholder_high_*` / `unlock_placeholder_*` 这类占位键开白名单，否则 §2 引用存在性检查会红。
- 本文件与 `content/JSON_SCHEMA.md`（C 出）的解析契约需在同一 PR 内对齐（04 §2 末：表结构变更 = SCHEMA + DataGen + Validator 三处同 PR）。

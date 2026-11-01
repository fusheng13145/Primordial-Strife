# NUMBERS.md — 全部数值的唯一真相源（ADR-005 / 05 分册 §1）

> **状态：已定稿（2026-11-01，ADR-031 授权批量拍板；`[锚]` 与数值原样转正，`[占位]` 为 EP 后置内容的结构性占位。保留项：大限键名病灶随禁区实现捆绑批处理、`@@artifact`/`@@pvp`/`@@companion` 值随实机批校准）。**
> 本文件是数值与字段的唯一真相源：任何受管数值只允许写在这里，代码与 config 不得出现第二份（05 分册 §1）。
> 表内标 `[锚]` 的值来自 05 分册已拍板口径；标 `` 的值是本轮初稿提案，需本人审定后方可作为基线。

## 0. 解析约定（草案，最终契约以 `content/JSON_SCHEMA.md` 为准）

- 每个数据块 = 一个二级标题 + 紧随其后的**唯一** ```yaml 围栏块；DataGen 以 `@@<id>` 行定位块（`#` 注释行内的 `@@` 除外）。
- 块内只允许 YAML 标量/映射/序列；键用 `snake_case`；单位写在键名后缀（`_sec` 秒、`_ticks` 刻、`_pct` 百分比 0–1 小数、`_years` 修行年）。
- ID 引用一律用 04 分册 §4 规范的全小写下划线内容 ID，不得出现中文键。
- 值后允许行内注释（`# [锚]` / `# `），DataGen 解析前剥离。

## 1. 境界链（05 分册 §3）

链序：`凡人 → 练气 → 筑基 → 金丹 → 元婴 → 化神 → 炼虚 → 合体 → 渡劫`。
后三段为占位命名，随 STORY.md 定稿（05 §3）；`unlocks` 为功能/内容清单键，M0 只登记不实现。

@@realms
```yaml
# qi_max = 该境界修为上限（突破到新境界后 qi 归 0，重新计）
# stage_count = 小境界（层/段）数；小境界阈值按 qi_max × i / stage_count 等分，不入表
# unlocks = 该境界解锁的功能/内容清单键（04 §2 strife_realms 必填字段）
# growth_ratio = qi_max(n) / qi_max(n-1)，Validator 硬校验区间见 §2
# sit_rate 单位 = 修为/秒（与 §5 的 tick_interval_ticks 配合；结算按"秒 × 刻/秒"折算成每刻速率）
fanren:   { qi_max: 100,   stage_count: 1, lifespan_years: 80,    sit_rate: 0.50,  unlocks: [], breakthrough_success_key: bs_fanren_qili }
qili:     { qi_max: 230,   stage_count: 9, lifespan_years: 120,   sit_rate: 0.80,  unlocks: [meditation, spiritroot_panel, cultivation_panel], breakthrough_success_key: bs_qili_zhuji }
zhuji:    { qi_max: 520,   stage_count: 4, lifespan_years: 200,   sit_rate: 1.25,  unlocks: [technique_equip, spell_cast, pill_crafting], breakthrough_success_key: bs_zhuji_jindan }
jindan:   { qi_max: 1180,  stage_count: 4, lifespan_years: 400,   sit_rate: 2.00,  unlocks: [artifact_slot, item_refine, quest_line_ch1], breakthrough_success_key: bs_jindan_yuanying }
yuanying: { qi_max: 2650,  stage_count: 4, lifespan_years: 800,   sit_rate: 3.20,  unlocks: [tribulation, sect_join, ambient_qi_affinity], breakthrough_success_key: bs_yuanying_huashen }
huashen:  { qi_max: 6000,  stage_count: 4, lifespan_years: 1500,  sit_rate: 5.10,  unlocks: [soul_scan, upper_realm_gate], breakthrough_success_key: bs_huashen_placeholder }
lianxu:   { qi_max: 13500, stage_count: 4, lifespan_years: 3000,  sit_rate: 8.20,  unlocks: [unlock_placeholder_1], breakthrough_success_key: bs_placeholder_high_1 } # [占位]
heti:     { qi_max: 30000, stage_count: 4, lifespan_years: 6000,  sit_rate: 13.00, unlocks: [unlock_placeholder_2], breakthrough_success_key: bs_placeholder_high_2 } # [占位]
dujie:    { qi_max: 67000, stage_count: 1, lifespan_years: 12000, sit_rate: 20.50, unlocks: [ascension], breakthrough_success_key: bs_dujie_ascend } # [占位]
```

设计口径 ``：`sit_rate` 增速固定约 1.6×，低于 `qi_max` 增速约 2.25×，因此单境界纯打坐时长（`qi_max / sit_rate`）随境界递增：3.3 / 4.8 / 6.9 / 9.8 / 13.8 / 19.6 / 27.4 / 38.5 / 54.5 分钟（未计灵根·环境·功法·丹药四项系数；突破的材料与任务门槛承担剩余节奏控制，不靠堆 `qi_max`）。相邻 `qi_max` 成长比实测落在 2.22–2.30，全部在 §2 的 1.8–2.5 区间内。

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
sync_full_resync_sec: 30  # 周期性全量重同步间隔，防 delta 漂移（丢包/重连的兜底）
intent_args_max_bytes: 4096  # C2S 意图参数上限；超限直接丢弃（03 §4"不得信任客户端上报"）
```

## 3. 突破与渡劫成功率（05 分册 §3/§4，ADR-008）

@@breakthrough
```yaml
bs_fanren_qili: { base: 0.95, fail_step: 0.00, floor: 0.95 }  # 入门突破不卡人
bs_qili_zhuji:     { base: 0.90, fail_step: -0.05, floor: 0.40 } # [锚] 练气→筑基 90%
bs_zhuji_jindan:   { base: 0.85, fail_step: -0.05, floor: 0.40 } # [锚] 筑基→金丹 85%
bs_jindan_yuanying:{ base: 0.75, fail_step: -0.05, floor: 0.40 } # [锚] 金丹→元婴 75%→70%，失败每次 -5%，下限 40%
bs_yuanying_huashen:{ base: 0.65, fail_step: -0.05, floor: 0.40 }
bs_huashen_placeholder: { base: 0.55, fail_step: -0.05, floor: 0.40 } # [占位]
bs_placeholder_high_1: { base: 0.45, fail_step: -0.05, floor: 0.35 } # [占位]
bs_placeholder_high_2: { base: 0.35, fail_step: -0.05, floor: 0.30 } # [占位]
bs_dujie_ascend:   { base: 0.25, fail_step: -0.05, floor: 0.20 } # [占位] 天劫，失败进 §4 渡劫结算
```

`fail_step` 语义 ``：同一境界每次失败后的累计修正（服务端记录 `bt_attempts`），到 `floor` 封底；面板必须显示"本次成功率 = base + fail_step × attempts"。

@@breakthrough_cost
```yaml
qi_reset_ratio_min: 0.60     # [锚] 突破失败：修为回退至上限的 60%（下限）
qi_reset_ratio_max: 0.80     # [锚] 上限
tribulation_debuff_key: debuff_heavy_wound # [锚] 渡劫失败附加"重伤"
debuff_all_stat_delta: -0.30 # [锚] 重伤：全属性 -30%
debuff_duration_sec: 1800  # 重伤限时（服务端权威）
tribulation_drop_radius: 16  # 天劫材料散落劫台半径（格），可拾回
```

## 4. 寿元与大限（05 分册 §4，ADR-008）

@@lifespan
```yaml
seconds_per_year: 1200  # 1 修行年 = 1200 现实秒（20 分钟），打坐期间同样流逝；×20 刻/秒即 ticks_per_year
seconds_per_year_sitting: 1200  # 与挂机一致，避免"打坐即长生"
limit_recovery_pill_key: pill_yanshou  # 续命玩法路径入口（丹药/任务，绝不删角色）
dasheng_realm_drop_stages: 1     # [锚] 大限代价：回退一档境界下限（段数按 stage_count 计）。目标口径 = ADR-026 ①（不降境、代价=降小境界）；现行实现仍跌境（DashengMath.java:40，禁区）——键名病灶（名含 stages 实作用 ordinal）与实现修正捆绑到同一禁区批（ADR-031 ②）
dasheng_reset_years_ratio: 0.05  # 大限后寿元重置为"残余寿元" = 该境界 lifespan_years × 0.05
dasheng_debuff_key: debuff_weak  # 大限附加虚弱状态
```

## 5. 打坐（M1，realm 包，服务端权威）

@@meditation
```yaml
interrupt_progress_keep: 0.90  # 打断后本次会话所得保留比例（惩罚 10%）
interrupt_cooldown_sec: 30  # 打断后重新入定冷却
tick_interval_ticks: 40  # 打坐结算降频（07 M1"降频+打断惩罚"），每 2 秒结算一次
offline_gain_allowed: false  # 离线不产出修为（服务端权威 + 反滥用）
interrupt_move_sqr: 0.0004  # 位移打断阈值（格²）：0.02 格 = 2cm，只为滤掉浮点噪声，任何真实移动都算打断
```

实际修炼速率（05 分册 §2 锁定公式，四项系数全部来自表）：
`速率 = sit_rate(查表) × 灵根系数(§6) × 环境系数(AmbientQi) × 功法倍率(techniques 表) × (1 + 丹药加成(§7))`

## 6. 灵根系数（05 分册 §2；C1-1"灵根五行×4 品阶"）

@@spiritroot
```yaml
# 品阶系数：作用于打坐基础速率
quality_tier_1: 1.40  # 天灵根（单系）
quality_tier_2: 1.20  # 双灵根
quality_tier_3: 0.95  # 三灵根
quality_tier_4: 0.70  # 四/五灵根（杂灵根）
# 五行亲和：功法 required_spiritroot 与玩家灵根集合的关系
affinity_matched: 1.25  # 功法属性在玩家灵根内
affinity_neutral: 1.00  # 无关
affinity_conflict: 0.60  # 相克（面板必须给出 tooltip 解释，05 §2）
roll_weights: { tier_1: 2, tier_2: 8, tier_3: 25, tier_4: 65 }  # 角色创建时服务端抽签权重（和 = 100）
```

## 7. 丹药加成（M2 炼丹；C1-1"入门丹方 3 张"）

@@pills
```yaml
pill_juqi: { qi_rate_bonus: 0.25, duration_sec: 300,  repeat_step: 0.05, repeat_floor: 0.10 }  # 聚气丹
pill_peiyuan: { qi_rate_bonus: 0.60, duration_sec: 120, repeat_step: 0.10, repeat_floor: 0.20 }  # 培元丹（短效高倍）
pill_yanshou: { lifespan_years_gain: 10, max_gain_per_realm: 30 }  # 延寿丹，续命路径，受单次境界封顶
pill_xuanyu: { qi_rate_bonus: 0.50, duration_sec: 180, repeat_step: 0.08, repeat_floor: 0.15 }  # 玄玉丹（玄玉诀配套地阶丹，2026-11-01 定稿）
pill_ningshen: { sense_regen_bonus: 2 }                                    # 凝神丹：神识恢复加速
pill_huichun:  { heal_health: 10.0 }                                       # 回春丹：即时回血
pill_liaoshang: { heal_health: 6.0, clear_debuff: true }                   # 疗伤丹：回血+清负面
pill_baoxue:   { attack_flat: 4.0, defense_flat: -2.0, duration_sec: 120 }  # 暴血丹
pill_jingang:  { defense_flat: 4.0, attack_flat: -1.0, duration_sec: 120 }  # 金刚丹
pill_jifeng:   { speed_flat: 0.03, duration_sec: 90 }                       # 疾风丹
pill_jiedu:    { clear_poison: true }                                      # 解毒丹
pill_pojing:   { breakthrough_bonus: 0.10 }                                # 破境丹
pill_wenyuan:  { fail_keep_ratio_bonus: 0.10 }                             # 稳元丹
pill_wugou:    { clear_causality: 1 }                                      # 洗尘丹
pill_xisui:    { spiritroot_upgrade: 1 }                                   # 洗髓丹
# repeat_* 语义 ：同种丹药在冷却窗内重复服用，加成按 repeat_step 递减至 repeat_floor（防堆叠挂机化）
```

## 8. 伤害与战斗（M2；公式禁止旁路，05 分册 §2）

@@combat
```yaml
# damage = formula_key 查表(base, realm_coeff × technique_bonus × (1 - target_resist)) - 穿透
realm_coeff: { fanren: 1.0, qili: 1.5, zhuji: 2.4, jindan: 4.0, yuanying: 7.0, huashen: 12.0, lianxu: 20.0, heti: 33.0, dujie: 55.0 }
formula_basic: { base: 6,  scale: realm_coeff }  # 基础法术
formula_pierce: { base: 4,  scale: realm_coeff, pen: 0.5 }  # 穿透系
formula_burst: { base: 12, scale: realm_coeff, self_cost_qi: 0.15 }  # 爆发系，消耗当前修为比例
spell_cost_qi: { light: 6,  medium: 18, heavy: 45 }  # 灵气/修为消耗三档
spell_cooldown_sec: { light: 1.0, medium: 4.0, heavy: 12.0 }    
artifact_cooldown_sec: 20  # 法宝主动技能
# —— 妖兽基础属性（M2 妖兽 AI / 战斗目标；EP3 前不做平行境界，数值为通用战斗参数，非代码字面量）——
beast_health: 20.0  # 妖兽基础生命（strife:monster），启动时按 combat_rules 应用，覆盖硬编码默认值
beast_attack: 3.0  # 妖兽基础攻击伤害
beast_speed: 0.30  # 妖兽移动速度
beast_follow_range: 16.0  # 妖兽索敌/追击半径（格）

# —— 妖兽种类（EP1，ADR-039；值 2026-11-02 定值，随实机批校准）——
@@beasts
```yaml
wolf:
  name: 野狼
  health: 15.0
  attack: 3.0
  speed: 0.38
  follow_range: 20.0
  xp: 5
rock:
  name: 岩甲兽
  health: 40.0
  attack: 4.0
  speed: 0.18
  follow_range: 12.0
  xp: 10
fire_fox:
  name: 炎狐
  health: 12.0
  attack: 5.0
  speed: 0.42
  follow_range: 24.0
  xp: 8
ice_serpent:
  name: 寒蛟
  health: 25.0
  attack: 4.0
  speed: 0.22
  follow_range: 16.0
  xp: 10
shadow_leopard:
  name: 暗影豹
  health: 18.0
  attack: 6.0
  speed: 0.45
  follow_range: 28.0
  xp: 15
treant:
  name: 古树妖
  health: 50.0
  attack: 3.0
  speed: 0.08
  follow_range: 10.0
  xp: 20
```
# —— 法宝副手属性加成（P1-01；副手装备法宝时按品阶生效，卸下即撤——2026-11-01 定值，随实机批校准 ADR-031 ③）——
artifact_bonus_fan:  { attack_pct: 0.05, health_flat: 0.0 }  # 凡品（法器）
artifact_bonus_di:   { attack_pct: 0.10, health_flat: 1.0 }  # 地品（法宝）
artifact_bonus_tian: { attack_pct: 0.20, health_flat: 2.0 }  # 天品（灵宝）
# —— 法器分级（EP1，ADR-036；品阶 → 四阶映射与阶位倍率，仙器 EP4 前不可达）——
tier_of_fan: fa      # 凡品 → 法器
tier_of_di: fa_bao   # 地品 → 法宝
tier_of_tian: ling_bao  # 天品 → 灵宝
tier_multiplier: { fa: 1.0, fa_bao: 1.5, ling_bao: 2.0, xian_qi: 3.0 }  # 阶位属性倍率（乘 artifact_bonus_*）
```

## 9. 世界与掉落（M4 / 04 分册 §2）

@@world
```yaml
ambient_qi_min: 0.50  # 环境系数下限（绝不为 0，避免"零成长无可解释"）
ambient_qi_max: 2.00  # 上限（灵脉/宗门核心区）
ambient_region_chunks: 16  # 灵气场粗粒度：16×16 区块一值（03 §6 的"区域粗粒度缓存"），细化层在其上加局部差异
ambient_refine_weight: 0.25  # 细化层权重：局部差异占最终环境系数的比例（0 = 完全粗粒度，1 = 只看局部）
ore_drop_weights: { common: 70, rare: 25, spirit: 5 }  # 矿石品质权重（和 = 100）
upper_qi_min: 1.50  # 上界维度（strife:upper_realm）环境系数下限——灵气浓度整体高于人界（人界 0.50–2.00），ADR-021 授权后实现；数值待本人复核
upper_qi_max: 3.00  # 上界维度环境系数上限
beast_loot_rolls: 3  # 妖兽掉落表默认 roll 次数
herb_grass_drop_prob: { item_ningxu: 0.12, item_duanxue: 0.05 }  # 草丛采集 MVP：破坏草类方块时各草药独立掷此概率（序章 #3/#8 的采集来源；键 = 物品内容 ID 全名；2026-11-01 定稿）
```

## 10. 限速参数（03 分册 §4 表内基线；运行时真值以本块为准）

@@rate_limits
```yaml
cast_per_sec: 5      # [锚] 施法意图
sit_per_sec: 2       # [锚] 打坐起止
quest_per_sec: 2     # [锚] 任务交互/交付
artifact_per_sec: 5  # [锚] 法宝主动使用
breakthrough_per_min: 6  # 突破请求（防连点刷 fail_step）
panel_per_sec: 4  # 面板视图拉取（03 §5 拉取式：C2S 请求 → S2C 视图包）
```

## 11. 坊市（EP1，ADR-032；值 2026-11-01 定值，随实机批/Beta 周更校准）

@@market
```yaml
tier_zhong: 10   # 中品灵石 = 下品 ×10（兑换命令用，服务端权威）
tier_shang: 100  # 上品灵石 = 下品 ×100
appraise_cost: 5  # 鉴定阁：每件未鉴定品的手续费（下品灵石）
```

## 12. 定稿记录

- 2026-11-01 按 `deliverables/真相源定稿决策表-2026-11-01.md` 建议值批量定稿（ADR-031 ②授权）：N1–N11 采纳现值；保留项 = 大限键名与实现修正捆绑禁区批（Z2）、`@@artifact/@@pvp/@@companion` 值随实机批校准（ADR-031 ③）。
- `@@limits` 的 S2C 预算 `[锚]` 与 03 §5/§6 一致；`@@downgrade` 段待建（ADR-026 ①），建段时同步本文件与 `DashengMath` 修正批。

## 13. 宗门职级（EP1，ADR-032；值 2026-11-01 定值，随实机批校准）

@@sect
```yaml
rank_neimen: 30   # 内门：对所属势力声望 ≥30
rank_zhenchuan: 60  # 真传：对所属势力声望 ≥60（低于内门线为外门，无所属=散修）
```

## 14. 符箓与保命道具（EP1，ADR-035；值 2026-11-01 定值，随实机批校准）

@@talisman
```yaml
substitute_health: 1.0   # 替身草人触发后保留的生命（半颗心）
escape_cooldown_sec: 30  # 遁符使用冷却（秒）
escape_place: qi_fangshi  # 遁符落点（地点内容 ID——"遁回坊市"；经 core 只读桥解析）
```

## 15. 闭关时间跳变（EP1，ADR-034；值 2026-11-01 定值，随实机批校准）

@@seclusion
```yaml
shichen_ticks: 12000   # 1 时辰 = 现实 10 分钟等值刻数（结算粒度）
max_shichen: 12        # 单次闭关上限（12 时辰 = 6 修行年寿元代价）
safety_radius: 8       # 入定安全半径：格内存在敌对生物即拒入
```

## 16. 拍卖行（EP1，ADR-037 ①；值 2026-11-02 定值，随实机批校准）

@@auction
```yaml
slot_shichen: 2   # 每件拍品的轮换时长（2 时辰 = 4 现实分钟等值；价格确定性推导自游戏时间）
```

## 17. 丹药体系（EP1 百艺·专项设计，ADR-038；值 2026-11-02 定值，随实机批校准）

### 17.1 设计原则

丹药不是"吃蓝瓶"——每种丹有**主效**与**代价**（副作用/限制/依赖），让炼丹师在配方选择时
做有意义的取舍，而不是无脑堆最强丹。三层结构：

- **丹药**（效果）：作用于玩家属性/状态，有时长与衰减；
- **丹方**（配方）：决定材料消耗与产出概率分布（含废丹）；
- **器具与火**（ modifiers）：炉改变成功率与品质上限，火改变品质偏置与容差。

### 17.2 丹药效果（按分类）

@@alchemy_pills
```yaml
# 修炼辅助
pill_juqi:     { qi_rate_bonus: 0.25, duration_sec: 300, repeat_step: 0.05, repeat_floor: 0.10 }
pill_peiyuan:  { qi_rate_bonus: 0.60, duration_sec: 120, repeat_step: 0.10, repeat_floor: 0.20 }
pill_xuanyu:   { qi_rate_bonus: 0.50, duration_sec: 180, repeat_step: 0.08, repeat_floor: 0.15 }
pill_ningshen: { sense_regen_bonus: 2, duration_sec: 180 }            # 凝神丹：神识恢复加速
pill_xisui:    { spiritroot_upgrade: 1 }                              # 洗髓丹：灵根品阶 +1（一次性，上限 tier1）
# 疗伤
pill_huichun:  { heal_health: 10.0 }                                 # 回春丹：即时回血
pill_liaoshang: { heal_health: 6.0, clear_debuff: true }             # 疗伤丹：回血+清负面
# 突破辅助
pill_pojing:   { breakthrough_bonus: 0.10 }                          # 破境丹：下次突破成功率 +10%（一次性）
pill_wenyuan:  { fail_keep_ratio_bonus: 0.10 }                       # 稳元丹：失败回退比率 +10%（一次性）
# 战斗（有时长，互斥：同类同时只生效一个）
pill_baoxue:   { attack_flat: 4.0, defense_flat: -2.0, duration_sec: 120 }  # 暴血丹：攻+4 防-2
pill_jingang:  { defense_flat: 4.0, attack_flat: -1.0, duration_sec: 120 }  # 金刚丹：防+4 攻-1
pill_jifeng:   { speed_flat: 0.03, duration_sec: 90 }                # 疾风丹：移速 +0.03
# 毒/解
pill_jiedu:    { clear_poison: true }                                # 解毒丹
# 特殊
pill_wugou:    { clear_causality: 1 }                                # 洗尘丹：清 1 笔因果标记
```

### 17.3 炼丹炉（furnace）

@@alchemy_furnaces
```yaml
furnace_shi:    { name: 青石炉, success_bonus: 0.00, quality_cap: di,   waste_recycle: 0 }   # 基础
furnace_tie:    { name: 玄铁炉, success_bonus: 0.05, quality_cap: tian, waste_recycle: 0 }   # 中阶
furnace_zijin:  { name: 紫金鼎, success_bonus: 0.10, quality_cap: tian, waste_recycle: 1 }   # 高阶：废丹回收 1 材料
furnace_long:   { name: 龙纹鼎, success_bonus: 0.20, quality_cap: tian, waste_recycle: 2, double_output: 0.10 }  # 传说：10% 双产
```

### 17.4 异火（fires）

@@alchemy_fires
```yaml
fire_normal:    { name: 凡火,     tolerance: 0.00, quality_shift: 0.00, affinity: none }       # 默认
fire_mu:        { name: 木灵火,   tolerance: 0.05, quality_shift: 0.00, affinity: mu }          # 木系亲和
fire_dixin:     { name: 地心火,   tolerance: 0.00, quality_shift: 0.00, affinity: tu,  success_bonus: 0.03 }
fire_xuanbing:  { name: 玄冰焰,   tolerance: 0.00, quality_shift: 0.00, affinity: shui, waste_reduce: 0.05 }
fire_chiyian:   { name: 赤炎,     tolerance: 0.00, quality_shift: 0.05, affinity: huo }          # 品质+5%
fire_jiuyou:    { name: 九幽冥火, tolerance: 0.03, quality_shift: 0.03, affinity: none, success_bonus: 0.08 }
fire_taiyang:   { name: 太阳真火, tolerance: 0.05, quality_shift: 0.10, affinity: none }          # 传说：品质+10%
```

### 17.5 天材地宝（materials）

@@alchemy_materials
```yaml
# 灵药（herb）：采集/种植获取，对应五行
mat_ningxu:      { name: 凝须草,   element: mu,   rarity: common,   source: gather }
mat_yuejian:     { name: 月见草,   element: huo,  rarity: common,   source: gather }
mat_hanshi_cao:  { name: 寒石草,   element: shui, rarity: uncommon, source: gather }
mat_zijin_hua:   { name: 紫金花,   element: huo,  rarity: uncommon, source: gather }
mat_baiyu_guo:   { name: 白玉果,   element: tu,   rarity: uncommon, source: gather }
mat_qingxin_lan: { name: 青心兰,   element: mu,   rarity: uncommon, source: farm }     # 灵田种植
mat_xuelian:     { name: 天山雪莲, element: shui, rarity: rare,     source: secret_realm }
# 矿石/兽核
mat_yaodan:      { name: 妖丹,     element: none, rarity: uncommon, source: drop }     # 妖兽内丹
mat_xuantie:     { name: 玄铁,     element: jin,  rarity: common,   source: mine }
mat_wuse_shi:    { name: 五色石,   element: none, rarity: rare,     source: mine }     # 五行合一
mat_duanxue:     { name: 断血,     element: none, rarity: common,   source: drop }
# 天材地宝（treasure）
mat_tianguo:     { name: 天元果,   element: none, rarity: epic,     source: secret_realm }  # 直接增修为
mat_longxue:     { name: 龙血草,   element: huo,  rarity: epic,     source: secret_realm }
mat_fengxue:     { name: 凤血,     element: huo,  rarity: epic,     source: secret_realm }
```

### 17.6 品质偏置机制

异火的 quality_shift 不是直接改概率——它是把产出概率从低品质段向高品质段<b>平移</b>：
shift 0.05 = 每个低品段的 5% 概率转移到紧邻的高品段。如果丹方只有一个输出，shift 无效。
这一设计使异火改变品质分布而不改变成败——成败由炉与材料稀有度控制。

## 18. 神识（EP1，ADR-036 ⑦；值 2026-11-02 定值，随实机批校准）

@@shenshi
```yaml
max_base: 10           # 凡人基础神识上限
max_per_ordinal: 5     # 每境界 +5（练气=15, 筑基=20, 金丹=25 ...）
regen_per_minute: 1    # 自然恢复：每现实分钟 +1
sense_cost: 3          # /strife sense 消耗
sense_radius: 24       # 探查半径（格）
```

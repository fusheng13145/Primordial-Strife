# P3（EP1 风起坊市）工单池（2026-11-01，ADR-032）

> 启动依据：本人"完成P3所有任务"指示（阶段处置见 ADR-032）。P1/P2 准出未满足项照常挂账。

| # | 工单 | 分区 | 验收证据 | 状态 |
|---|---|---|---|---|
| E1 | 经济原语：prices.csv 定价表 + 生成器 + **价格门禁在生成器**（构建期红，与 separation>=spacing 同构——Validator 主循环零改动）+ `/strife market list\|buy\|sell` + 防复制（sell<buy、服务端扣发） | production 灰区 + 内容开区 | PriceGeneratorTest 5 红脸 + MarketPricesTest 5 例 | ✅ e7ed577 |
| E2 | 灵石三档：下/中/上品物品注册 + 兑换（1:10:100，NUMBERS @@market） | production 灰区 | MarketPricesTest 守恒用例（含 MAX_VALUE 边界） | ✅ e7ed577 |
| E3 | 坊市商人 NPC + `qi_fangshi` 地点（数据） | 内容开区 | 产物对账 + 真机 R2 组 | ✅ 本批 |
| E4 | 境界分级悬赏（quests_ep1.csv 三档；**单次**——引擎对 repeatable≠no fail-fast，扩展留 P3.1 小 ADR；达标即赏语义如实入档） | 内容开区 | 真实产物链用例（随 D5） | ✅ 本批 |
| E5 | 宗门职级派生（H2 声望阈值 → 外门/内门/真传）+ `/strife sect status` | **realm 禁区**（只读派生） | 阈值单测 + 真机 | ⛔ 按禁区纪律留单独披露批（ADR-031 ①） |
| E6 | 资源地理数据：坊市地点（灵脉/洞府留 E6.1，随 EP1 内容产能） | 内容开区 | places 产物 + 校验 | ✅ 本批（qi_fangshi） |
| E7 | EP1 主线骨架（quest_ep1_* 入口 + 3–4 节点，[拟] 随校准） | 内容开区 | DAG 用例 | ⬜ |
| E8 | 实机验收台账 R 组 + 平衡报告 | 验证 | 本人实机批 | ⬜ |

**显式后置（需各自 ADR）**：暗拍/拍卖、黑市销赃、雇佣、鉴定、符箓/阵法/灵植。

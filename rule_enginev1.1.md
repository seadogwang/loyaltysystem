# 积分活动规则引擎设计文档（最终交付版 v5.0）
> **版本**：5.0\
> **关联主文档**：基于AI的下一代全渠道忠诚度管理SaaS平台 v7.3\
> **更新内容**：
>
> * 在触发条件下方增加 **奖励配置 Tab**（默认“奖励配置（简单模式）”，另一 Tab“阶梯奖励配置”）。
>
> * 简单模式支持 **倍数** 或 **固定积分值** 两种奖励类型。
>
> * 支持 **订单头计算**（基于订单头金额）或 **订单明细计算**（基于每个订单明细行，便于退单时逐行回溯）。
>
> * 所有活动（固定倍数、固定积分、阶梯循环、生命周期）共用同一套触发条件，通过生命周期里程碑条件区分。
***
## 一、数据库变更
### 1.1 修改 `rule_definition` 表
sql
```
-- 1. 重命名 agenda_group 为 rule_category
ALTER TABLE rule_definition RENAME COLUMN agenda_group TO rule_category;
-- 2. 修改注释和默认值
COMMENT ON COLUMN rule_definition.rule_category IS '规则分类：base=基础规则，promo=促销活动';
ALTER TABLE rule_definition ALTER COLUMN rule_category SET DEFAULT 'base';
-- 3. 扩展 rule_type 长度
ALTER TABLE rule_definition ALTER COLUMN rule_type TYPE VARCHAR(30);
-- 4. 确保 metadata 为 JSONB
ALTER TABLE rule_definition ALTER COLUMN metadata TYPE JSONB USING metadata::jsonb;
-- 5. 新增 rule_group 字段（用于同一组内优先级排序）
ALTER TABLE rule_definition ADD COLUMN rule_group VARCHAR(50);
COMMENT ON COLUMN rule_definition.rule_group IS '规则组，同组内按 priority 排序执行';
ALTER TABLE rule_definition ADD COLUMN priority INT DEFAULT 0;
COMMENT ON COLUMN rule_definition.priority IS '组内优先级，数字越小越先执行';
-- 6. 新增有效时间字段（冗余便于查询）
ALTER TABLE rule_definition ADD COLUMN effective_start TIMESTAMPTZ;
ALTER TABLE rule_definition ADD COLUMN effective_end TIMESTAMPTZ;
```
### 1.2 新增会员活动累计状态表
sql
```
CREATE TABLE member_activity_state (
    id BIGSERIAL PRIMARY KEY,
    program_code VARCHAR(32) NOT NULL,
    member_id VARCHAR(64) NOT NULL,
    rule_code VARCHAR(64) NOT NULL,
    total_rewarded NUMERIC(18,4) DEFAULT 0,
    last_updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(program_code, member_id, rule_code)
);
CREATE INDEX idx_mas_member ON member_activity_state(program_code, member_id);
CREATE INDEX idx_mas_rule ON member_activity_state(program_code, rule_code);
```
### 1.3 复用现有 `program_schema` 表
* 前端通过现有 Schema API (`/api/admin/schemas/{entityType}/current`) 获取实体属性列表及类型。
* 订单头属性：`TransactionEvent` 的 payload 字段 Schema（`entity_type='TRANSACTION_EVENT'`），包括 `total_amount` 等。
* 会员属性：`Member` 的标准属性 + `ext_attributes` 动态属性（`entity_type='MEMBER'`）。
* 订单明细属性：系统预定义，如 `sku`, `category_id`, `price`, `quantity` 等。
***
## 二、枚举值定义
| 枚举名                | 值                                                                 | 说明               |
| ------------------ | ----------------------------------------------------------------- | ---------------- |
| `rule_type`        | `DRL`                                                             | 基础规则（手写/AI生成）    |
|                    | `ACTIVITY_PROMO`                                                  | 所有促销活动           |
| `rule_category`    | `base`                                                            | 基础规则（Tab 1）      |
|                    | `promo`                                                           | 活动规则（Tab 2）      |
| `status`           | `DRAFT`                                                           | 草稿               |
|                    | `ACTIVE`                                                          | 已发布              |
|                    | `INACTIVE`                                                        | 已停用              |
| `reward_calc_mode` | `HEADER`                                                          | 基于订单头金额计算        |
|                    | `LINE`                                                            | 基于订单明细行计算        |
| `reward_type`      | `MULTIPLIER`                                                      | 按比例倍数奖励          |
|                    | `FIXED_POINTS`                                                    | 固定积分值奖励          |
| `cycleMode`        | `SINGLE_MATCH`                                                    | 单次匹配（不循环）        |
|                    | `THRESHOLD_LOOP`                                                  | 循环扣除             |
| `remainderMode`    | `USE_STEP_MULTIPLIER`                                             | 剩余金额按阶梯倍数        |
|                    | `FIXED_MULTIPLIER`                                                | 固定倍数             |
| `excessStrategy`   | `STOP`                                                            | 停止赠送             |
|                    | `RATIO`                                                           | 按比例缩放            |
|                    | `TRUNCATE_AND_DOWNGRADE`                                          | 截断降级             |
| 运算符                | `EQ`, `NE`, `GT`, `LT`, `GTE`, `LTE`, `BETWEEN`, `IN`, `CONTAINS` | 条件比较符            |
| 属性类型               | `NUMBER`, `STRING`, `DATE`, `ENUM`, `BOOLEAN`, `LIFECYCLE`        | 从 Schema 推断或系统定义 |
***
## 三、`metadata` JSON 结构（统一）
### 3.1 完整示例（阶梯奖励 + 明细计算）
```json
{
  "entity_conditions": [
    {
      "entity": "Order",
      "attribute": "total_amount",
      "operator": "BETWEEN",
      "value": { "min": 100, "max": null }
    },
    {
      "entity": "Member",
      "attribute": "tier_code",
      "operator": "IN",
      "value": ["GOLD", "PLATINUM"]
    }
  ],
  "effective_time_range": {
    "start": "2026-01-01T00:00:00Z",
    "end": null
  },
  "reward": {
    "calc_mode": "LINE",      // "HEADER" 或 "LINE"
    "type": "STEP_CYCLE",     // "SIMPLE" 或 "STEP_CYCLE"（根据前端Tab）
    "steps": [
      { "lower": 0, "upper": 1000, "multiplier": 0.5, "isCycleThreshold": false },
      { "lower": 1000, "upper": 2000, "multiplier": 1.0, "isCycleThreshold": false },
      { "lower": 2000, "upper": 3000, "multiplier": 1.5, "isCycleThreshold": false },
      { "lower": 3000, "upper": null, "multiplier": 2.0, "isCycleThreshold": true }
    ],
    "cycleMode": "THRESHOLD_LOOP",
    "cycleThresholdOrder": [3000],
    "remainderMode": "USE_STEP_MULTIPLIER",
    "remainderFixedMultiplier": 1.0,
    "perOrderLimit": 10000,
    "accumulativeLimit": 50000,
    "excessStrategy": "TRUNCATE_AND_DOWNGRADE",
    "downgradeMultiplier": 1.0,
    "downgradeContinueCycle": false
  }
}
```
**简单模式（固定倍数）示例**：
```json
{
  "entity_conditions": [...],
  "effective_time_range": {...},
  "reward": {
    "calc_mode": "HEADER",
    "type": "SIMPLE",
    "simple_type": "MULTIPLIER",   // 或 "FIXED_POINTS"
    "simple_multiplier": 2.0,      // 当 simple_type = MULTIPLIER 时
    "simple_fixed_points": 100,    // 当 simple_type = FIXED_POINTS 时
    "perOrderLimit": 10000,
    "accumulativeLimit": 50000
  }
}
```
### 3.2 字段详细说明
| 字段路径                         | 类型     | 说明                                                        |
| ---------------------------- | ------ | --------------------------------------------------------- |
| `reward.calc_mode`           | string | `HEADER`（基于订单头金额）或 `LINE`（基于订单明细行，退单时可逐行处理）               |
| `reward.type`                | string | `SIMPLE`（简单模式）或 `STEP_CYCLE`（阶梯奖励模式）                      |
| `reward.simple_type`         | string | 当 `type=SIMPLE` 时，`MULTIPLIER`（倍数）或 `FIXED_POINTS`（固定积分值） |
| `reward.simple_multiplier`   | number | 倍数模式下的倍数                                                  |
| `reward.simple_fixed_points` | number | 固定积分模式下的积分值                                               |
| `reward.steps`               | array  | 阶梯模式下的阶梯规则（见前文）                                           |
| `reward.cycleMode`           | string | 阶梯模式下的循环模式                                                |
| `reward.perOrderLimit`       | number | 单笔订单赠送上限（不限时为 null）                                       |
| `reward.accumulativeLimit`   | number | 活动期间累计上限（不限时为 null）                                       |
| `reward.excessStrategy`      | string | 仅累计上限非空时有效，超限策略                                           |
***
## 四、前端界面设计（完整布局）
### 4.1 活动列表页
（与前几版相同，略）
### 4.2 新建/编辑活动页面（统一界面，含 Tab 奖励配置）
**整体布局**（单页滚动表单，区块顺序如下）：
1. 基础信息
2. 触发条件（所有条件为 AND 关系）
3. 奖励配置（Tab 切换：简单模式 / 阶梯奖励配置）
4. 测试与预览
5. 操作按钮
***
#### 4.2.1 基础信息区
text
```
┌─ 基础信息 ──────────────────────────────────────────────────────────────┐
│ 规则代码 *   [PROMO_618                ]  (唯一，字母数字下划线)         │
│ 规则名称 *   [618大促                  ]                                 │
│ 规则组       [promo_group              ]  (可选，同组内按优先级排序)      │
│ 优先级       [10]                         (数字越小越先执行)              │
│ 生效时间     [2026-06-01 00:00] 至 [2026-06-30 23:59]  □ 永久            │
└─────────────────────────────────────────────────────────────────────────┘
```
#### 4.2.2 触发条件区（统一，所有活动共用）
text
```
┌─ 触发条件（所有条件为 AND 关系） ───────────────────────────────────────┐
│                                                                         │
│  ┌───────────────────────────────────────────────────────────────────┐ │
│  │ 实体：[Order ▼]  属性：[订单金额 ▼]   ≥ [100]   ≤ [      ]  [删除] │ │
│  └───────────────────────────────────────────────────────────────────┘ │
│  ┌───────────────────────────────────────────────────────────────────┐ │
│  │ 实体：[Member ▼] 属性：[会员等级 ▼]   ⊂ [GOLD] [PLATINUM]  [删除] │ │
│  └───────────────────────────────────────────────────────────────────┘ │
│  ┌───────────────────────────────────────────────────────────────────┐ │
│  │ 实体：[OrderItem▼]属性：[SKU ▼]        ⊂ [SKU001] [SKU002] [删除] │ │
│  └───────────────────────────────────────────────────────────────────┘ │
│  ┌───────────────────────────────────────────────────────────────────┐ │
│  │ 实体：[Member ▼] 属性：[生命周期里程碑▼] = [生日月首单 ▼]  [删除] │ │
│  └───────────────────────────────────────────────────────────────────┘ │
│                                                                         │
│                                              [+ 添加条件]               │
└─────────────────────────────────────────────────────────────────────────┘
```
#### 4.2.3 奖励配置区（Tab 切换）
text
```
┌─ 奖励配置 ──────────────────────────────────────────────────────────────┐
│  [奖励配置（简单模式）]  [阶梯奖励配置]                                   │
│ ─────────────────────────────────────────────────────────────────────── │
│                                                                         │
│  【当选中“奖励配置（简单模式）”时】                                       │
│  ┌───────────────────────────────────────────────────────────────────┐ │
│  │ ┌─ 计算范围 ───────────────────────────────────────────────────┐ │ │
│  │ │ ● 订单头计算（基于整笔订单总金额）                             │ │ │
│  │ │ ○ 订单明细计算（基于每个商品行，退单时按行处理）               │ │ │
│  │ └──────────────────────────────────────────────────────────────┘ │ │
│  │                                                                    │ │
│  │ ┌─ 奖励方式 ───────────────────────────────────────────────────┐ │ │
│  │ │ ● 按比例倍数奖励  ○ 固定积分值奖励                           │ │ │
│  │ │                                                              │ │ │
│  │ │ 当选择“按比例倍数奖励”时：                                    │ │ │
│  │ │   奖励倍数： [2.0] 倍                                         │ │ │
│  │ │                                                              │ │ │
│  │ │ 当选择“固定积分值奖励”时：                                    │ │ │
│  │ │   奖励积分： [100] 积分/笔（订单头）或 [10] 积分/件（明细）     │ │ │
│  │ └──────────────────────────────────────────────────────────────┘ │ │
│  │                                                                    │ │
│  │ ┌─ 上限控制 ──────────────────────────────────────────────────┐ │ │
│  │ │ 单笔订单赠送上限： [10000] 积分   □ 不限制                   │ │ │
│  │ │ 活动期间累计上限： [50000] 积分   □ 不限制                   │ │ │
│  │ │ 累计窗口：        [活动期间 ▼]                                │ │ │
│  │ └──────────────────────────────────────────────────────────────┘ │ │
│  └───────────────────────────────────────────────────────────────────┘ │
│                                                                         │
│  【当选中“阶梯奖励配置”时】                                             │
│  ┌───────────────────────────────────────────────────────────────────┐ │
│  │ ┌─ 计算范围 ───────────────────────────────────────────────────┐ │ │
│  │ │ ● 订单头计算  ○ 订单明细计算（暂不支持阶梯明细，可用简单模式） │ │ │
│  │ └──────────────────────────────────────────────────────────────┘ │ │
│  │                                                                    │ │
│  │ 阶梯表格（可增删行，拖拽排序）                        [ + 添加区间 ]│ │
│  │ ┌──────────┬──────────┬──────────┬─────────────────────┬─────────┐│ │
│  │ │ 下限(元) │ 上限(元) │ 倍数     │ 是循环分段点？      │ 操作    ││ │
│  │ ├──────────┼──────────┼──────────┼─────────────────────┼─────────┤│ │
│  │ │ 0        │ 1000     │ 0.5      │ ☐                   │ [删除]  ││ │
│  │ │ 1000     │ 2000     │ 1.0      │ ☐                   │ [删除]  ││ │
│  │ │ 2000     │ 3000     │ 1.5      │ ☐                   │ [删除]  ││ │
│  │ │ 3000     │ (空)     │ 2.0      │ ☑                   │ [删除]  ││ │
│  │ └──────────┴──────────┴──────────┴─────────────────────┴─────────┘│ │
│  │                                                                    │ │
│  │ ┌─ 循环扣除配置（可折叠） ─────────────────────────────────────┐   │ │
│  │ │ ☑ 启用循环扣除                                              │   │ │
│  │ │ 循环分段点顺序（从高到低）： [3000] [↑] [↓]  删除            │   │ │
│  │ │ 剩余金额处理： ● 按阶梯倍数  ○ 固定倍数 [1] 倍              │   │ │
│  │ └────────────────────────────────────────────────────────────┘   │ │
│  │                                                                    │ │
│  │ ┌─ 上限控制（同简单模式）──────────────────────────────────────┐  │ │
│  │ │ 单笔订单赠送上限： [10000] 积分   □ 不限制                   │  │ │
│  │ │ 活动期间累计上限： [50000] 积分   □ 不限制                   │  │ │
│  │ │ 累计窗口：        [活动期间 ▼]                                │  │ │
│  │ └────────────────────────────────────────────────────────────┘  │ │
│  │                                                                    │ │
│  │ ┌─ 超限处理策略（仅当累计上限非空时显示） ─────────────────────┐  │ │
│  │ │ ● 截断并降级（推荐）                                          │  │ │
│  │ │   - 优先级顺序（可拖拽）： [2.0] [1.5] [1.0] [0.5] [↑][↓]    │  │ │
│  │ │   - 降级倍数： [1] 倍                                         │  │ │
│  │ │   - □ 降级后继续循环                                          │  │ │
│  │ │ ○ 停止赠送                                                    │  │ │
│  │ │ ○ 按比例缩放                                                  │  │ │
│  │ └────────────────────────────────────────────────────────────┘  │ │
│  └───────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
```
#### 4.2.4 测试与预览区
（与前几版相同，略）
#### 4.2.5 操作按钮
（与前几版相同，略）
### 4.3 关键交互说明
| 区块           | 行为                                                         |
| ------------ | ---------------------------------------------------------- |
| **基础信息**     | 规则代码唯一性校验；勾选“永久”则结束时间清空禁用。                                 |
| **触发条件**     | 实体和属性从后端 Schema 动态加载；属性类型决定运算符及控件；可增删条件行；生命周期里程碑作为普通条件项存在。 |
| **奖励配置 Tab** | 默认“简单模式”。“阶梯奖励配置”展示完整阶梯表格和循环配置。                            |
| **计算范围**     | 简单模式下支持订单头计算（基于订单总金额）或订单明细计算（基于每个商品行）。阶梯模式下暂只支持订单头计算。      |
| **简单模式奖励方式** | 倍数或固定积分值可选。固定积分值时，按订单头则每单固定积分；按明细则每件商品固定积分。                |
| **上限控制**     | 无论简单还是阶梯模式，单笔/累计上限均有效。累计上限非空时，超限策略才显示。                     |
| **预览**       | 根据当前配置调用后端预览接口，展示计算明细。                                     |
***
## 五、后端核心伪代码
### 5.1 DRL 生成器（统一处理所有配置）
```java
@Component
public class UnifiedPromoDrlGenerator implements DrlGenerator {
    public String generate(RuleDefinition rule) {
        JSONObject meta = rule.getMetadata();
        String ruleCode = rule.getRuleCode();
        String ruleGroup = rule.getRuleGroup();
        int priority = rule.getPriority();
        JSONObject reward = meta.getJSONObject("reward");
        String calcMode = reward.getString("calc_mode"); // "HEADER" or "LINE"
        String rewardType = reward.getString("type");     // "SIMPLE" or "STEP_CYCLE"
        StringBuilder drl = new StringBuilder();
        drl.append("package com.loyalty.rules;\n");
        drl.append("import com.loyalty.platform.domain.points.model.*;\n");
        drl.append("import com.loyalty.platform.domain.activity.ActivityStateService;\n");
        drl.append("import com.loyalty.platform.domain.activity.StepCycleCalculator;\n");
        drl.append("global ActivityStateService stateService;\n\n");
        drl.append("rule \"").append(ruleCode).append("\"\n");
        if (ruleGroup != null && !ruleGroup.isEmpty()) {
            drl.append("    ruleflow-group \"").append(ruleGroup).append("\"\n");
        }
        drl.append("    salience ").append(1000 - priority).append("\n");
        drl.append("when\n");
        drl.append("    $event: EventFact(eventType == \"ORDER\", getPayloadNumber(\"total_amount\") > 0)\n");
        drl.append("    $member: MemberFact(memberId == $event.memberId)\n");
        drl.append("    eval( stateService.isActivityActive(\"").append(ruleCode).append("\", $event.getEventTime()) )\n");
        
        // 动态生成条件检查
        JSONArray conditions = meta.getJSONArray("entity_conditions");
        for (int i = 0; i < conditions.size(); i++) {
            String conditionExpr = ConditionCodeGenerator.generate(conditions.getJSONObject(i));
            drl.append("    ").append(conditionExpr).append("\n");
        }
        
        // 累计上限检查
        if (reward.has("accumulativeLimit") && reward.get("accumulativeLimit") != null) {
            drl.append("    BigDecimal alreadyRewarded = stateService.getTotalRewarded(\"").append(ruleCode).append("\", $member.memberId);\n");
            drl.append("    BigDecimal remainingCap = new BigDecimal(\"").append(reward.getBigDecimal("accumulativeLimit")).append("\").subtract(alreadyRewarded);\n");
            drl.append("    if (remainingCap.compareTo(BigDecimal.ZERO) <= 0) return;\n");
        } else {
            drl.append("    BigDecimal remainingCap = null;\n");
        }
        
        // 根据 calcMode 和 rewardType 生成不同计算逻辑
        if ("HEADER".equals(calcMode)) {
            drl.append("    $orderAmount: BigDecimal($event.getPayloadNumber(\"total_amount\"))\n");
            drl.append("then\n");
            if ("SIMPLE".equals(rewardType)) {
                generateSimpleHeaderReward(drl, ruleCode, reward);
            } else {
                generateStepHeaderReward(drl, ruleCode, reward);
            }
        } else { // LINE mode
            drl.append("    $items: List($event.getPayload().get(\"items\"))\n");
            drl.append("then\n");
            generateLineReward(drl, ruleCode, reward);
        }
        
        drl.append("end\n");
        return drl.toString();
    }
    private void generateSimpleHeaderReward(StringBuilder drl, String ruleCode, JSONObject reward) {
        String simpleType = reward.getString("simple_type"); // "MULTIPLIER" or "FIXED_POINTS"
        if ("MULTIPLIER".equals(simpleType)) {
            BigDecimal multiplier = reward.getBigDecimal("simple_multiplier");
            drl.append("    BigDecimal rawPoints = $orderAmount.multiply(new BigDecimal(\"").append(multiplier).append("\"));\n");
        } else {
            BigDecimal fixedPoints = reward.getBigDecimal("simple_fixed_points");
            drl.append("    BigDecimal rawPoints = new BigDecimal(\"").append(fixedPoints).append("\");\n");
        }
        // 单笔上限
        if (reward.has("perOrderLimit") && reward.get("perOrderLimit") != null) {
            drl.append("    rawPoints = rawPoints.min(new BigDecimal(\"").append(reward.getBigDecimal("perOrderLimit")).append("\"));\n");
        }
        drl.append("    BigDecimal finalPoints = (remainingCap != null) ? rawPoints.min(remainingCap) : rawPoints;\n");
        drl.append("    if (finalPoints.compareTo(BigDecimal.ZERO) > 0) {\n");
        drl.append("        ActionCollector.get().awardPoints($event.getEventId(), finalPoints, \"").append(ruleCode).append("\").execute(drools);\n");
        drl.append("        stateService.addRewarded(\"").append(ruleCode).append("\", $member.memberId, finalPoints);\n");
        drl.append("    }\n");
    }
    private void generateStepHeaderReward(StringBuilder drl, String ruleCode, JSONObject reward) {
        // 生成阶梯循环奖励逻辑，类似之前的版本
        // 调用 StepCycleCalculator 等方法
    }
    private void generateLineReward(StringBuilder drl, String ruleCode, JSONObject reward) {
        // 遍历订单明细行，每行单独计算奖励，便于退单时按行冲抵
        drl.append("    BigDecimal totalPoints = BigDecimal.ZERO;\n");
        drl.append("    for (Map<String, Object> item : $items) {\n");
        // 提取明细金额或数量（根据奖励类型）
        if ("SIMPLE".equals(reward.getString("type"))) {
            if ("MULTIPLIER".equals(reward.getString("simple_type"))) {
                drl.append("        BigDecimal lineAmount = new BigDecimal(item.get(\"price\").toString()).multiply(new BigDecimal(item.get(\"quantity\").toString()));\n");
                drl.append("        BigDecimal linePoints = lineAmount.multiply(new BigDecimal(\"").append(reward.getBigDecimal("simple_multiplier")).append("\"));\n");
            } else {
                drl.append("        BigDecimal linePoints = new BigDecimal(\"").append(reward.getBigDecimal("simple_fixed_points")).append("\").multiply(new BigDecimal(item.get(\"quantity\").toString()));\n");
            }
            // 单笔上限这里按整单限制，因此明细累计后再限制
            drl.append("        totalPoints = totalPoints.add(linePoints);\n");
            drl.append("    }\n");
            // 单笔上限与累计上限
            if (reward.has("perOrderLimit") && reward.get("perOrderLimit") != null) {
                drl.append("    totalPoints = totalPoints.min(new BigDecimal(\"").append(reward.getBigDecimal("perOrderLimit")).append("\"));\n");
            }
            drl.append("    BigDecimal finalPoints = (remainingCap != null) ? totalPoints.min(remainingCap) : totalPoints;\n");
            drl.append("    if (finalPoints.compareTo(BigDecimal.ZERO) > 0) {\n");
            drl.append("        ActionCollector.get().awardPoints($event.getEventId(), finalPoints, \"").append(ruleCode).append("\").execute(drools);\n");
            drl.append("        stateService.addRewarded(\"").append(ruleCode).append("\", $member.memberId, finalPoints);\n");
            drl.append("    }\n");
        } else {
            // 阶梯模式一般不适合明细计算，可暂不支持或留空
            drl.append("    // 阶梯模式暂不支持订单明细计算，请使用订单头计算\n");
        }
    }
}
```
### 5.2 条件代码生成器（同前，略）
### 5.3 阶梯计算器（同前，略）
### 5.4 全局服务 ActivityStateService（同前，略）
***
## 六、API 接口定义
| 方法     | 路径                                       | 说明                   |
| ------ | ---------------------------------------- | -------------------- |
| GET    | `/api/rules?category=promo`              | 获取活动规则列表             |
| GET    | `/api/rules/{ruleCode}`                  | 获取单个规则详情             |
| GET    | `/api/schemas/MEMBER/current`            | 获取会员扩展属性（复用）         |
| GET    | `/api/schemas/TRANSACTION_EVENT/current` | 获取交易事件动态属性（复用）       |
| POST   | `/api/rules`                             | 创建或更新规则（草稿）          |
| POST   | `/api/rules/{ruleCode}/publish`          | 发布活动（生成DRL，热更新）      |
| POST   | `/api/rules/{ruleCode}/deactivate`       | 停用活动                 |
| DELETE | `/api/rules/{ruleCode}`                  | 删除草稿                 |
| POST   | `/api/rules/preview`                     | 预览奖励计算（支持订单头和明细两种模式） |
**统一响应格式**（主文档定义）：
```json
{ "code": "SUCCESS", "message": "", "data": {} }
```
***
## 七、与现有代码的集成指引
1. **数据库**：执行 DDL 变更。
2. **实体类**：修改 `RuleDefinition`，增加 `ruleCategory`、`ruleGroup`、`priority`、`effectiveStart`、`effectiveEnd`。
3. **规则列表**：支持按 `ruleCategory` 筛选。
4. **规则编辑器**：基础规则保持 DRL 编辑器；活动规则使用新统一界面。
5. **规则发布**：活动规则调用 `UnifiedPromoDrlGenerator` 生成 DRL。
6. **规则执行**：无需修改，活动规则与基础规则共用会话。
7. **生命周期里程碑**：在 `ActivityStateService.checkLifecycleMilestone` 中实现具体逻辑。
***
## 八、开发顺序建议
1. 后端数据库变更 + 实体类修改。
2. 后端实现 `UnifiedPromoDrlGenerator`、`ConditionCodeGenerator`、`ActivityStateService` 及预览 API。
3. 前端实现活动列表页（Tab 切换、筛选）。
4. 前端实现统一配置界面（基础信息、触发条件、奖励配置 Tab、计算范围切换、简单/阶梯模式、上限控制、超限策略、预览）。
5. 联调测试。
***
**文档结束** – 本版本完整整合了所有讨论内容，包含 Tab 分离奖励配置、简单模式支持倍数/固定值、订单头/明细计算选择、阶梯奖励独立 Tab，可直接交付 AI 开发。

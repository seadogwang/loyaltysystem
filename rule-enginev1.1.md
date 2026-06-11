# 补充设计：组合商品条件与商品级奖励设置
> 本文档是对主积分活动规则引擎设计文档的补充，解决两类特殊促销需求：
>
> 1. **组合商品条件**：订单中必须同时包含指定的一组商品（如 A 和 B）才能触发奖励。
>
> 2. **商品级奖励设置**：不同商品（SKU）享受不同的奖励倍数（如 A、B 商品 1 倍，C 商品 2 倍）。
这两项功能均集成在统一的活动配置界面中，无需额外页面。
***
## 一、组合商品条件
### 1.1 业务场景
* 用户同时购买商品 A 和商品 B（不论是否购买其他商品），额外奖励 100 积分。
* 用户同时购买商品 A、B、C，打 9 折（按固定倍数奖励 0.9 倍）。
### 1.2 界面设计
在“触发条件”区域，添加条件时增加一个新选项：
```text
实体：[Order ▼]  属性：[组合商品 ▼]
```
选择后，出现一个**多选下拉**（或标签输入），运营人员可以勾选需要**同时包含**的商品列表。
效果：
```text
┌─ 触发条件（所有条件为 AND 关系） ───────────────────────────────────────┐
│  ... 已有条件 ...                                                       │
│  ┌───────────────────────────────────────────────────────────────────┐ │
│  │ 实体：[Order ▼]  属性：[组合商品 ▼]  必须同时包含：               │ │
│  │   [SKU_A] [SKU_B]  [ + 添加商品 ]                                 │ │
│  │ 操作：[删除]                                                       │ │
│  └───────────────────────────────────────────────────────────────────┘ │
│                                              [+ 添加条件]               │
└─────────────────────────────────────────────────────────────────────────┘
```
* “必须同时包含”表示订单明细中必须包含所有勾选的 SKU（每个至少一件）。
* 可添加多组组合商品条件（它们之间是 AND 关系，即订单需同时满足所有组合）。
### 1.3 存储 JSON 结构
在 `metadata.entity_conditions` 数组中增加如下对象：
```json
{
  "entity": "Order",
  "attribute": "combination_sku_set",
  "operator": "CONTAINS_ALL",
  "value": ["SKU_A", "SKU_B"]
}
```
### 1.4 后端实现要点
#### 1.4.1 在 `EventFact` 中添加辅助方法
```java
public boolean containsAllSkus(Set<String> requiredSkus) {
    List<Map<String, Object>> items = (List<Map<String, Object>>) payload.get("items");
    Set<String> orderSkus = items.stream()
        .map(item -> (String) item.get("sku"))
        .collect(Collectors.toSet());
    return orderSkus.containsAll(requiredSkus);
}
```
#### 1.4.2 在 `ConditionCodeGenerator` 中添加生成逻辑
```java
case "combination_sku_set":
    JSONArray skus = (JSONArray) value;
    String skuList = skus.toList().stream()
        .map(s -> "\"" + s + "\"")
        .collect(Collectors.joining(","));
    return "eval( $event.containsAllSkus(new java.util.HashSet<>(java.util.Arrays.asList(" + skuList + "))) )";
```
#### 1.4.3 DRL 生成示例
在规则的条件部分会生成类似：

```drools
$event: EventFact(...)
eval( $event.containsAllSkus(new HashSet<>(Arrays.asList("SKU_A", "SKU_B"))) )
```
### 1.5 注意事项
* 组合商品条件仅与“订单明细计算”模式兼容（因为需要检查明细行）。如果用户同时选择了订单头计算，应给出提示或自动切换。
* 组合商品条件通常配合固定积分奖励使用（简单模式 + 固定积分值）。
***
## 二、商品级奖励设置
### 2.1 业务场景
* A、B 商品按金额 1 倍积分，C 商品按金额 2 倍积分。
* 不同商品可设置不同的倍数或固定积分值。
### 2.2 界面设计
在“简单模式”奖励配置中，增加一个可折叠区域“商品级奖励设置”（默认收起）。折叠展开后的效果如下：
```text
┌─ 奖励配置（简单模式） ────────────────────────────────────────────────┐
│ ┌─ 计算范围 ──────────────────────────────────────────────────────┐  │
│ │ ● 订单明细计算  ○ 订单头计算（商品级设置仅明细有效）            │  │
│ └─────────────────────────────────────────────────────────────────┘  │
│ ┌─ 奖励方式 ──────────────────────────────────────────────────────┐  │
│ │ ● 按比例倍数奖励  ○ 固定积分值奖励                               │  │
│ │ 全局奖励倍数：[1.0] 倍                                           │  │
│ └─────────────────────────────────────────────────────────────────┘  │
│                                                                       │
│ ▼ 商品级奖励设置（为不同商品单独配置）          [启用]                │
│   ┌─────────────────────────────────────────────────────────────┐   │
│   │ 商品/SKU        │ 奖励类型   │ 倍数/固定值   │ 操作         │   │
│   ├─────────────────┼───────────┼──────────────┼──────────────┤   │
│   │ SKU_A           │ 倍数      │ 1.0          │ [删除]       │   │
│   │ SKU_B           │ 倍数      │ 1.0          │ [删除]       │   │
│   │ SKU_C           │ 倍数      │ 2.0          │ [删除]       │   │
│   │ [+ 添加商品]    │           │              │              │   │
│   └─────────────────────────────────────────────────────────────┘   │
│   未匹配的商品： ● 使用全局倍数(1.0)  ○ 不奖励                        │
│   （提示：商品级奖励仅在“订单明细计算”模式下生效）                     │
│                                                                       │
│ ┌─ 上限控制（同前） ──────────────────────────────────────────────┐  │
│ └─────────────────────────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────────────────────────┘
```
* **启用开关**：默认关闭，开启后显示商品列表。
* **添加商品行**：点击“添加商品”弹出商品选择器（可从后端获取 SKU 列表），选择后自动填入。
* **奖励类型**：可选“倍数”或“固定积分值”，与全局类型独立。
* **未匹配的商品**：决定那些没有在表中配置的商品是使用全局奖励还是不奖励。
### 2.3 存储 JSON 结构
在 `metadata.reward` 中增加字段：
```json
{
  "reward": {
    "calc_mode": "LINE",
    "type": "SIMPLE",
    "simple_type": "MULTIPLIER",
    "global_multiplier": 1.0,
    "item_rules_enabled": true,
    "item_rules": [
      { "sku": "SKU_A", "type": "MULTIPLIER", "value": 1.0 },
      { "sku": "SKU_B", "type": "MULTIPLIER", "value": 1.0 },
      { "sku": "SKU_C", "type": "MULTIPLIER", "value": 2.0 }
    ],
    "unmatched_action": "USE_GLOBAL",  // 或 "NO_REWARD"
    "perOrderLimit": null,
    "accumulativeLimit": null
  }
}
```
### 2.4 后端实现要点
#### 2.4.1 DRL 生成关键代码（在 LINE 模式下）
```java
// 构建 sku -> 倍数/固定值 的映射
Map<String, BigDecimal> skuMultipliers = new HashMap<>();
JSONArray itemRules = reward.getJSONArray("item_rules");
for (int i = 0; i < itemRules.size(); i++) {
    JSONObject rule = itemRules.getJSONObject(i);
    String sku = rule.getString("sku");
    BigDecimal value = rule.getBigDecimal("value");
    skuMultipliers.put(sku, value);
}
boolean itemRulesEnabled = reward.optBoolean("item_rules_enabled", false);
String unmatchedAction = reward.optString("unmatched_action", "USE_GLOBAL");
BigDecimal globalMultiplier = reward.getBigDecimal("global_multiplier");
drl.append("    BigDecimal totalPoints = BigDecimal.ZERO;\n");
drl.append("    for (Map<String, Object> item : $items) {\n");
drl.append("        String sku = (String) item.get(\"sku\");\n");
if (itemRulesEnabled) {
    drl.append("        BigDecimal multiplier = skuMultipliers.get(sku);\n");
    drl.append("        if (multiplier == null) {\n");
    if ("USE_GLOBAL".equals(unmatchedAction)) {
        drl.append("            multiplier = globalMultiplier;\n");
    } else {
        drl.append("            continue;\n");
    }
    drl.append("        }\n");
} else {
    drl.append("        BigDecimal multiplier = globalMultiplier;\n");
}
drl.append("        BigDecimal lineAmount = new BigDecimal(item.get(\"price\").toString()).multiply(new BigDecimal(item.get(\"quantity\").toString()));\n");
drl.append("        BigDecimal linePoints = lineAmount.multiply(multiplier);\n");
drl.append("        totalPoints = totalPoints.add(linePoints);\n");
drl.append("    }\n");
// 后续应用单笔上限、累计上限等...
```
#### 2.4.2 退单精确冲抵
* 在发放积分时，如果启用了商品级奖励，建议为每个明细行生成独立的积分发放记录（通过 `ActionCollector.awardPointsForItem` 传递 `lineId`）。
* 在 `account_transaction` 表中增加 `line_id` 字段（可选），或在 `redemption_allocation` 中关联行标识。
* 退单时根据退款订单的明细行 ID 精准扣减对应积分。
***
## 三、完整界面整合（与主文档 v5.0 对齐）
将上述两项功能嵌入主文档的“新建/编辑活动页面”中：
1. **触发条件区**：增加“组合商品”条件类型（实体 Order → 属性 组合商品）。
2. **奖励配置区（简单模式）**：增加“商品级奖励设置”折叠块，包含启用开关、商品列表表格、未匹配商品选项。
整体布局保持不变，仅在原有区块内添加新控件。最终页面（触发条件区域示例）：
```text
┌─ 触发条件（所有条件为 AND 关系） ───────────────────────────────────────┐
│  ┌───────────────────────────────────────────────────────────────────┐ │
│  │ 实体：[Order ▼]  属性：[订单金额 ▼]   ≥ [100]   ≤ [      ]  [删除] │ │
│  └───────────────────────────────────────────────────────────────────┘ │
│  ┌───────────────────────────────────────────────────────────────────┐ │
│  │ 实体：[Order ▼]  属性：[组合商品 ▼]  必须同时包含：               │ │
│  │   [SKU_A] [SKU_B]  [ + 添加商品 ]                                 │ │
│  └───────────────────────────────────────────────────────────────────┘ │
│                                              [+ 添加条件]               │
└─────────────────────────────────────────────────────────────────────────┘
```
奖励配置区域示例（展开商品级奖励设置时）：
```text
┌─ 奖励配置（简单模式） ────────────────────────────────────────────────┐
│ ... 计算范围、奖励方式 ...                                            │
│ ▼ 商品级奖励设置                                [启用] ✔              │
│   ┌─────────────────────────────────────────────────────────────┐   │
│   │ 商品/SKU        │ 奖励类型   │ 倍数/固定值   │ 操作         │   │
│   ├─────────────────┼───────────┼──────────────┼──────────────┤   │
│   │ SKU_A           │ 倍数      │ 1.0          │ [删除]       │   │
│   │ SKU_B           │ 倍数      │ 1.0          │ [删除]       │   │
│   │ SKU_C           │ 倍数      │ 2.0          │ [删除]       │   │
│   └─────────────────────────────────────────────────────────────┘   │
│   未匹配的商品： ● 使用全局倍数  ○ 不奖励                            │
└─────────────────────────────────────────────────────────────────────┘
```
***
## 四、开发指引
1. **前端**：
   * 在触发条件组件中增加“组合商品”类型的条件行，使用多选组件（如 `Select` mode="tags"）让用户选择 SKU。
   * 在简单模式奖励配置中增加“商品级奖励设置”折叠面板，使用动态表格组件管理商品规则列表。
   * 确保计算范围选择“订单明细计算”时，商品级奖励设置才可启用。
2. **后端**：
   * 扩展 `ConditionCodeGenerator` 以处理 `combination_sku_set` 条件。
   * 扩展 `EventFact` 添加 `containsAllSkus` 方法。
   * 扩展 `UnifiedPromoDrlGenerator`，在 LINE 模式下处理 `item_rules`，生成逐行奖励代码。
   * 如需退单精准冲抵，考虑在 `account_transaction` 中添加 `line_id` 字段或扩展 `redemption_allocation`。
3. **测试**：
   * 测试组合商品条件：构造订单仅包含 SKU\_A+SKU\_B，验证条件匹配；缺少其中一个则不匹配。
   * 测试商品级奖励：分别配置 A、B 为 1 倍，C 为 2 倍，验证订单总积分计算正确。
   * 测试退单：部分退款时验证扣减积分与原始发放一致。
***
**文档结束** – 本补充设计可直接用于指导 AI 修改代码，实现组合商品条件和商品级奖励设置。

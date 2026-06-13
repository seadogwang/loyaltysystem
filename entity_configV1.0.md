# 统一实体建模与映射配置平台设计文档
> **版本**：1.0\
> **目的**：将实体配置（Schema/API实体）、API↔业务实体映射、业务实体间关系配置三大功能整合到一个可视化页面，基于 ChartDB 提供拖拽连线式配置，支持双击连线定义字段级转换（函数/脚本），大幅降低配置分散度和使用门槛。
***
## 一、设计目标与原则
### 1.1 核心目标
* **一个页面完成所有配置**：实体定义、实体间关系、API与业务实体之间的映射（入站/出站）、字段转换逻辑，均在同一个 ER 图工作区完成。
* **可视化连线驱动**：通过拖拽连线表示关系或映射，双击连线配置转换细节（包括基础函数和自定义脚本）。
* **支持复杂场景**：一个 API 实体映射到多个业务实体（如订单头+订单明细），多个业务实体组合成一个 API 实体（出站），字段级转换灵活。
* **配置即存储**：所有配置最终保存到 `program_schema`（实体、关系）和 `channel_adapter_config`（映射、转换脚本）中。
### 1.2 设计原则
* **以实体为中心**：所有配置都围绕实体节点展开。
* **连线类型区分**：用不同颜色/线型区分“业务关系”（1:1,1:N）和“映射关系”（入站/出站）。
* **双向可逆**：入站映射（API→业务）和出站映射（业务→API）在同一界面通过方向切换或独立连线表示。
* **渐进式复杂度**：简单字段映射用下拉选择+基础函数，复杂转换用 Monaco 编辑器写脚本。
***
## 二、整体功能架构
```text
┌─────────────────────────────────────────────────────────────────────┐
│                    统一实体建模与映射配置平台                         │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │                   ER 图画布 (ChartDB 扩展)                     │  │
│  │  • 节点：业务实体、API实体（请求/响应）、系统实体              │  │
│  │  • 连线：业务关系（实线）、入站映射（虚线，API→业务）         │  │
│  │         出站映射（点线，业务→API）                            │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                                 │                                    │
│  ┌──────────────────────────────┴───────────────────────────────┐  │
│  │                      右侧上下文面板                           │  │
│  │  • 选中节点 → 实体属性编辑（字段 Schema、主键/外键、API 元数据）│  │
│  │  • 选中连线 → 映射配置编辑器（源字段→目标字段 + 转换表达式）   │  │
│  │  • 全局设置 → 方向（入站/出站模式）、渠道选择、保存/发布       │  │
│  └───────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
```
***
## 三、页面布局设计
### 3.1 整体布局（左右结构）
```text
┌─ 统一实体建模与映射 ──────────────────────────────────────────────────┐
│ [模式: 入站 ▼]  渠道: [天猫 ▼]            [保存] [发布] [导入/导出]   │
├────────────────────────────────────────────────────────────────────────┤
│ ┌────────────────────────────────────────────────────────────────────┐ │
│ │                         ER 图画布区域                              │ │
│ │  ┌─────────┐      (入站映射虚线)   ┌─────────┐                    │ │
│ │  │ Tmall   │ ─ ─ ─ ─ ─ ─ ─ ─ ─ → │  Order  │                    │ │
│ │  │ OrderResp│                      │ (业务)  │                    │ │
│ │  │ (API)   │                       └────┬────┘                    │ │
│ │  └─────────┘                            │ (1:N 实线)              │ │
│ │                                          ▼                         │ │
│ │                                   ┌─────────┐                      │ │
│ │                                   │OrderItem│                      │ │
│ │                                   │ (业务)  │                      │ │
│ │                                   └─────────┘                      │ │
│ └────────────────────────────────────────────────────────────────────┘ │
│                                                                        │
│ ┌────────────────────────────────────────────────────────────────────┐ │
│ │ 右侧面板（点击节点或连线动态切换内容）                              │ │
│ │ ┌────────────────────────────────────────────────────────────────┐ │ │
│ │ │ 当前选择: [连线] 源: TmallOrderResp → 目标: Order               │ │ │
│ │ │ ┌──────────┬────────────┬────────────────────────────────────┐ │ │ │
│ │ │ │ 源字段   │ 目标字段   │ 转换表达式                          │ │ │ │
│ │ │ ├──────────┼────────────┼────────────────────────────────────┤ │ │ │
│ │ │ │ tid      │ orderId    │ -                                   │ │ │ │
│ │ │ │ payment  │ totalAmount│ parseFloat                          │ │ │ │
│ │ │ │ pay_time │ paidAt     │ toISOString                         │ │ │ │
│ │ │ │ orders   │ items      │ (数组嵌套，双击编辑子映射)           │ │ │ │
│ │ │ └──────────┴────────────┴────────────────────────────────────┘ │ │ │
│ │ │ [+ 添加映射行]  [高级脚本模式]                                  │ │ │
│ │ └────────────────────────────────────────────────────────────────┘ │ │
│ └────────────────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────────────┘
```
### 3.2 顶部工具栏说明
* **模式切换**：入站（API → 业务）/ 出站（业务 → API），影响连线的方向和可选的实体类型。
* **渠道选择**：当存在多个渠道（天猫、京东等），筛选显示该渠道下的映射配置。
* **保存/发布**：保存草稿或发布生效版本。
* **导入/导出**：支持 JSON 格式导入导出配置，便于迁移。
***
## 四、ChartDB 集成与扩展
### 4.1 选型与集成方式
使用 `@chartdb/react` 作为 ER 图画布组件。由于原生 ChartDB 主要面向数据库表建模，我们需要进行以下扩展。
### 4.2 扩展点列表
| 原生能力          | 扩展需求                                             | 实现方法                                                                                  |
| ------------- | ------------------------------------------------ | ------------------------------------------------------------------------------------- |
| 实体 = 数据库表     | 区分业务实体、API实体（请求/响应）、系统实体                         | 在节点 data 中增加 `entityCategory` 字段，自定义节点渲染样式（颜色、图标）                                     |
| 关系仅支持外键（实线）   | 增加映射关系（虚线/点线）                                    | 扩展 `relationType` 枚举：`FOREIGN_KEY`, `INBOUND_MAPPING`, `OUTBOUND_MAPPING`，并在画布上渲染不同线型 |
| 字段类型为 SQL 类型  | 使用 JSON Schema 类型（string, number, array, object） | 扩展字段编辑器，支持 JSON Schema 并存储到 `fieldSchema`                                             |
| 不支持嵌套对象/数组    | 支持展开定义子字段                                        | 在属性面板中为 `object`/`array` 类型提供“编辑子结构”按钮，递归定义                                           |
| 不支持 API 额外元数据 | 为 API 实体增加 URL、认证方式等                             | 节点选中时，右侧面板显示 API 专用配置表单                                                               |
| 连线只能一对一       | 允许一个节点连向多个节点（一对多映射）                              | 原生连线即支持多对多，我们只需在连线上保存映射配置即可                                                           |
| 双击连线默认无行为     | 双击打开映射配置面板                                       | 监听 `onRelationDoubleClick` 事件，打开模态框或右侧面板直接编辑                                          |
### 4.3 自定义节点渲染（React + TypeScript 示例）
```tsx
// CustomNode.tsx
import { Handle, Position } from 'reactflow'; // ChartDB 基于 ReactFlow
const getNodeStyle = (category: string) => {
  switch (category) {
    case 'BUSINESS': return { background: '#e6f7ff', border: '2px solid #1890ff' };
    case 'API_REQUEST': return { background: '#f6ffed', border: '2px solid #52c41a', borderStyle: 'dashed' };
    case 'API_RESPONSE': return { background: '#fff7e6', border: '2px solid #fa8c16', borderStyle: 'dashed' };
    default: return { background: '#fafafa', border: '1px solid #d9d9d9' };
  }
};
export const CustomNode = ({ data }: { data: { entity: Entity } }) => {
  const { entity } = data;
  return (
    <div style={{ padding: 10, borderRadius: 8, width: 180, ...getNodeStyle(entity.category) }}>
      <div style={{ fontWeight: 'bold' }}>
        {entity.name}
        {entity.category === 'API_REQUEST' && <Tag color="green">请求</Tag>}
        {entity.category === 'API_RESPONSE' && <Tag color="orange">响应</Tag>}
      </div>
      <div style={{ fontSize: 12, color: '#666' }}>{entity.description}</div>
      <Handle type="target" position={Position.Left} />
      <Handle type="source" position={Position.Right} />
    </div>
  );
};
```
### 4.4 连线类型定义
```typescript
enum RelationType {
  FOREIGN_KEY = 'FOREIGN_KEY',       // 业务关系，实线
  INBOUND_MAPPING = 'INBOUND_MAPPING', // API → 业务，虚线
  OUTBOUND_MAPPING = 'OUTBOUND_MAPPING' // 业务 → API，点线
}
interface ExtendedEdge {
  id: string;
  source: string;      // 源实体ID
  target: string;      // 目标实体ID
  type: RelationType;
  mappingConfig?: MappingRule[];   // 字段映射配置
  relationConfig?: {               // 仅当 type=FOREIGN_KEY 时
    sourceField: string;
    targetField: string;
    cardinality: 'ONE_TO_ONE' | 'ONE_TO_MANY';
  };
}
```
### 4.5 自定义连线渲染（根据类型不同线型）
```tsx
const edgeTypes = {
  [RelationType.FOREIGN_KEY]: (props) => <CustomEdge {...props} stroke="blue" strokeDasharray="none" />,
  [RelationType.INBOUND_MAPPING]: (props) => <CustomEdge {...props} stroke="green" strokeDasharray="5,5" />,
  [RelationType.OUTBOUND_MAPPING]: (props) => <CustomEdge {...props} stroke="orange" strokeDasharray="2,4" />,
};
```
***
## 五、数据模型与存储格式
### 5.1 实体存储（复用 `program_schema` 表）
```json
// 业务实体示例
{
  "programCode": "BRAND_A",
  "entityType": "Order",
  "entityCategory": "BUSINESS",
  "version": "v1",
  "fieldSchema": {
    "type": "object",
    "properties": {
      "orderId": { "type": "string", "primaryKey": true },
      "memberId": { "type": "string", "foreignKey": { "entity": "Member", "field": "memberId" } },
      "totalAmount": { "type": "number", "minimum": 0 },
      "items": {
        "type": "array",
        "items": { "$ref": "#/definitions/OrderItem" }
      }
    },
    "definitions": {
      "OrderItem": {
        "type": "object",
        "properties": {
          "sku": { "type": "string" },
          "quantity": { "type": "integer" }
        }
      }
    }
  },
  "primaryKey": "orderId",
  "foreignKeys": [{ "field": "memberId", "references": { "entity": "Member", "field": "memberId" } }]
}
```
### 5.2 关系存储（`program_schema.entity_relations` JSONB）
```json
[
  {
    "sourceEntity": "Order",
    "targetEntity": "OrderItem",
    "relationType": "FOREIGN_KEY",
    "sourceField": "orderId",
    "targetField": "parentOrderId",
    "cardinality": "ONE_TO_MANY"
  },
  {
    "sourceEntity": "TmallOrderResp",
    "targetEntity": "Order",
    "relationType": "INBOUND_MAPPING",
    "mappingRules": [
      { "source": "tid", "target": "orderId", "type": "PATH" },
      { "source": "payment", "target": "totalAmount", "type": "EXPRESSION", "expression": "parseFloat" },
      { "source": "orders", "target": "items", "type": "PATH" },
      { "source": "oid", "target": "items[].orderItemId", "type": "PATH", "parentArray": "items" }
    ]
  }
]
```
### 5.3 映射配置存储（`channel_adapter_config.inbound_mappings` / `outbound_mappings`）
为了支持渠道级别隔离，实际存储时以渠道+操作为主键：
```json
// channel_adapter_config 表的 inbound_mappings 字段
{
  "orderCreate": [
    { "source": "tid", "target": "orderId", "type": "PATH" },
    { "source": "payment", "target": "totalAmount", "type": "EXPRESSION", "expression": "parseFloat" },
    { "source": "pay_time", "target": "paidAt", "type": "EXPRESSION", "expression": "toISOString" },
    { "source": "orders", "target": "items", "type": "PATH" },
    { "source": "oid", "target": "items[].orderItemId", "type": "PATH", "parentArray": "items" }
  ]
}
```
> **注意**：ER 图中的连线本质上就是这些映射规则的图形化表示。双向同步：修改连线配置即更新 `inbound_mappings` 或 `entity_relations`。
***
## 六、核心交互流程
### 6.1 创建新实体
1. 在画布空白处双击，弹出“新建实体”对话框。
2. 选择实体类别（业务实体 / API请求实体 / API响应实体 / 系统实体）。
3. 填写名称、描述、版本。
4. 创建后，节点出现在画布上，右侧面板自动打开属性编辑器。
5. 在属性编辑器中添加字段（支持嵌套结构、主键、外键）。
### 6.2 配置业务关系（1:1, 1:N）
1. 在工具栏选择“业务关系模式”（或默认连线类型为 FOREIGN\_KEY）。
2. 从实体 A 的字段点（Handle）拖拽到实体 B 的字段点。
3. 松开后弹出关系配置框：
   * 源实体、目标实体自动填充。
   * 选择基数（一对一/一对多）。
   * 选择源字段和目标字段（下拉选择）。
4. 确定后，生成实线连线，并更新 `entity_relations`。
### 6.3 配置入站映射（API → 业务实体）
1. 顶部模式切换为“入站”。
2. 从 API 响应实体节点拖拽连线到业务实体节点。
3. 松开后自动创建虚线，并在右侧面板打开映射编辑器。
4. 映射编辑器以表格形式显示：
   * 左侧可选择源字段（树形选择，支持从 API Schema 中递归选择）。
   * 右侧选择目标字段（从业务实体的 Schema 中选择）。
   * 转换表达式列：下拉选择基础函数（parseFloat, toISOString, formatDate, concat等）或自定义脚本。
5. 对于数组嵌套（如 API 返回的订单列表 → 业务订单明细），支持子映射：双击该行，打开嵌套编辑器，定义数组内对象的字段映射。
6. 保存后，映射规则存入 `inbound_mappings` 对应的操作代码下。
### 6.4 配置出站映射（业务 → API）
1. 顶部模式切换为“出站”。
2. 从业务实体拖拽连线到 API 请求实体（注意方向）。
3. 松开后创建点线，右侧面板同样显示映射表格（源为业务实体字段，目标为 API 请求字段）。
4. 支持常量值、表达式、聚合函数（如将订单头和明细组合成一个 JSON 数组）。
5. 存储到 `outbound_mappings`。
### 6.5 配置字段转换（双击连线）
* 直接双击任意映射连线，打开映射编辑器模态框（或右侧面板聚焦），可编辑该连线上所有字段对的转换表达式。
* 支持两种模式：
  * **基础函数模式**：提供常用函数库（数学、日期、字符串、类型转换），可视化选择。
  * **脚本模式**：使用 Monaco 编辑器编写 JavaScript 表达式（如 `value + ' suffix'`），支持访问上下文（`context` 对象提供辅助方法）。
### 6.6 复杂场景：一个 API 实体对应多个业务实体
* 例如天猫订单响应包含订单头和商品列表两个业务实体。
* 配置方式：
  1. 创建 API 实体 `TmallOrderResp`。
  2. 创建业务实体 `Order` 和 `OrderItem`。
  3. 从 `TmallOrderResp` 分别连线到 `Order` 和 `OrderItem`（两条虚线）。
  4. 在 `TmallOrderResp → Order` 映射中，配置 `tid → orderId`, `payment → totalAmount` 等。
  5. 在 `TmallOrderResp → OrderItem` 映射中，配置 `orders[].oid → orderItemId`, `orders[].num → quantity` 等，且需要指定父数组 `orders`。
  6. 运行时，标准化组件会先创建 `Order` 实例，然后遍历 `orders` 数组创建多个 `OrderItem`，并自动关联 `orderId`。
### 6.7 测试映射
* 在右侧面板底部提供“测试”按钮，弹出抽屉：
  * 输入示例源 JSON（根据 API 实体的 Schema 自动生成模板）。
  * 点击运行，后端执行映射脚本，输出标准化事件或业务实体 JSON。
  * 展示结果和错误信息。
***
## 七、转换表达式配置（函数库与脚本）
### 7.1 内置函数库（可供下拉选择）
| 函数名                            | 说明     | 示例                                                        |
| ------------------------------ | ------ | --------------------------------------------------------- |
| `toString`                     | 转字符串   | `toString(value)`                                         |
| `toNumber`                     | 转数字    | `toNumber(value)`                                         |
| `parseFloat`                   | 解析浮点数  | `parseFloat(payment)`                                     |
| `toISOString`                  | 日期转ISO | `toISOString(pay_time)`                                   |
| `formatDate(value, format)`    | 日期格式化  | `formatDate(pay_time, 'yyyy-MM-dd')`                      |
| `concat(sep, ...fields)`       | 字符串拼接  | `concat(' ', firstName, lastName)`                        |
| `default(value, defaultValue)` | 默认值    | `default(phone, '未知')`                                    |
| `arrayMap(array, mappingExpr)` | 数组映射   | `arrayMap(orders, (item) => { return { id: item.oid } })` |
### 7.2 自定义脚本模式
* 使用 GraalVM 支持的 JavaScript 语法。
* 脚本模板：
javascript
```
function transform(value, context) {
    // value: 源字段的值
    // context: 提供辅助方法，如 context.getRoot(), context.setTemp(key, val) 等
    return value * 100;
}
```
* 在映射配置中存储为：`{ "source": "payment", "target": "totalAmount", "type": "SCRIPT", "script": "function transform(value, context) { return parseFloat(value) * 100; }" }`
### 7.3 数组嵌套映射特殊语法
对于数组到数组的映射，使用 `parentArray` 和 `arrayItemMapping` 配置：
```json
{
  "source": "orders",
  "target": "items",
  "type": "ARRAY_MAPPING",
  "itemMapping": [
    { "source": "oid", "target": "orderItemId", "type": "PATH" },
    { "source": "num", "target": "quantity", "type": "EXPRESSION", "expression": "toNumber" }
  ]
}
```
在 UI 上，双击数组映射行会打开子映射编辑器。
***
## 八、前端实现伪代码（React + TypeScript）
### 8.1 主组件结构
```tsx
// UnifiedEntityModeling.tsx
import React, { useState, useEffect } from 'react';
import { ChartDB, useChartDBStore } from '@chartdb/react';
import { CustomNode } from './CustomNode';
import { CustomEdge } from './CustomEdge';
import { RightPanel } from './RightPanel';
import { TopToolbar } from './TopToolbar';
export const UnifiedEntityModeling: React.FC = () => {
  const [mode, setMode] = useState<'inbound' | 'outbound'>('inbound');
  const [selectedChannel, setSelectedChannel] = useState('tmall');
  const [selectedItem, setSelectedItem] = useState<{ type: 'node' | 'edge'; id: string } | null>(null);
  
  const { nodes, edges, addNode, addEdge, updateNode, updateEdge } = useChartDBStore();
  // 加载已有配置
  useEffect(() => {
    loadEntitiesAndMappings(selectedChannel, mode);
  }, [selectedChannel, mode]);
  // 处理连线创建
  const handleConnect = (params: any) => {
    const { source, target } = params;
    const sourceNode = nodes.find(n => n.id === source);
    const targetNode = nodes.find(n => n.id === target);
    if (!sourceNode || !targetNode) return;
    let relationType: RelationType;
    if (mode === 'inbound' && sourceNode.data.entity.category === 'API_RESPONSE' && targetNode.data.entity.category === 'BUSINESS') {
      relationType = RelationType.INBOUND_MAPPING;
    } else if (mode === 'outbound' && sourceNode.data.entity.category === 'BUSINESS' && targetNode.data.entity.category === 'API_REQUEST') {
      relationType = RelationType.OUTBOUND_MAPPING;
    } else if (sourceNode.data.entity.category === 'BUSINESS' && targetNode.data.entity.category === 'BUSINESS') {
      relationType = RelationType.FOREIGN_KEY;
    } else {
      alert('不支持该连线方向');
      return;
    }
    const newEdge = {
      id: `${source}->${target}`,
      source,
      target,
      type: relationType,
      mappingConfig: [],
      relationConfig: relationType === RelationType.FOREIGN_KEY ? { cardinality: 'ONE_TO_MANY' } : undefined
    };
    addEdge(newEdge);
    setSelectedItem({ type: 'edge', id: newEdge.id });
  };
  // 双击连线打开映射编辑器
  const handleEdgeDoubleClick = (edge: Edge) => {
    setSelectedItem({ type: 'edge', id: edge.id });
    // 右侧面板自动切换到映射编辑模式
  };
  return (
    <div style={{ display: 'flex', height: '100vh' }}>
      <div style={{ flex: 3, display: 'flex', flexDirection: 'column' }}>
        <TopToolbar mode={mode} setMode={setMode} channel={selectedChannel} setChannel={setSelectedChannel} />
        <ChartDB
          nodes={nodes}
          edges={edges}
          onConnect={handleConnect}
          onNodeClick={(node) => setSelectedItem({ type: 'node', id: node.id })}
          onEdgeDoubleClick={handleEdgeDoubleClick}
          nodeTypes={{ custom: CustomNode }}
          edgeTypes={edgeTypes}
          defaultNodeType="custom"
        />
      </div>
      <div style={{ flex: 1, borderLeft: '1px solid #ddd', padding: 16, overflowY: 'auto' }}>
        <RightPanel selectedItem={selectedItem} nodes={nodes} edges={edges} onUpdate={updateNode} onUpdateEdge={updateEdge} />
      </div>
    </div>
  );
};
```
### 8.2 右侧面板核心逻辑（映射表格）
```tsx
// MappingTableEditor.tsx
import React, { useState } from 'react';
import { Table, Button, Select, Input, Modal } from 'antd';
import { MonacoEditor } from './MonacoEditor';
interface MappingRule {
  source: string;
  target: string;
  type: 'PATH' | 'EXPRESSION' | 'CONSTANT' | 'SCRIPT';
  expression?: string;
  script?: string;
  constant?: any;
  parentArray?: string;
  itemMapping?: MappingRule[];
}
export const MappingTableEditor: React.FC<{
  sourceEntitySchema: JSONSchema;
  targetEntitySchema: JSONSchema;
  initialRules: MappingRule[];
  onSave: (rules: MappingRule[]) => void;
}> = ({ sourceEntitySchema, targetEntitySchema, initialRules, onSave }) => {
  const [rules, setRules] = useState<MappingRule[]>(initialRules);
  const addRule = () => {
    setRules([...rules, { source: '', target: '', type: 'PATH' }]);
  };
  const updateRule = (index: number, field: keyof MappingRule, value: any) => {
    const newRules = [...rules];
    newRules[index][field] = value;
    setRules(newRules);
  };
  const openScriptEditor = (rule: MappingRule) => {
    Modal.confirm({
      title: '编辑转换脚本',
      content: <MonacoEditor value={rule.script} onChange={(val) => rule.script = val} />,
      onOk: () => onSave(rules)
    });
  };
  const columns = [
    { title: '源字段', dataIndex: 'source', render: (text, record, idx) => <FieldSelector schema={sourceEntitySchema} value={text} onChange={(v) => updateRule(idx, 'source', v)} /> },
    { title: '目标字段', dataIndex: 'target', render: (text, record, idx) => <FieldSelector schema={targetEntitySchema} value={text} onChange={(v) => updateRule(idx, 'target', v)} /> },
    { title: '转换表达式', dataIndex: 'expression', render: (text, record, idx) => (
      <Select value={record.type} onChange={(v) => updateRule(idx, 'type', v)} style={{ width: 120 }}>
        <Select.Option value="PATH">直接映射</Select.Option>
        <Select.Option value="EXPRESSION">基础函数</Select.Option>
        <Select.Option value="SCRIPT">自定义脚本</Select.Option>
      </Select>
      {record.type === 'EXPRESSION' && <Input value={record.expression} onChange={(e) => updateRule(idx, 'expression', e.target.value)} />}
      {record.type === 'SCRIPT' && <Button onClick={() => openScriptEditor(record)}>编辑脚本</Button>}
    ) }
  ];
  return (
    <div>
      <Table dataSource={rules} columns={columns} rowKey="source" />
      <Button onClick={addRule}>+ 添加映射</Button>
      <Button type="primary" onClick={() => onSave(rules)}>保存映射</Button>
    </div>
  );
};
```
### 8.3 后端 API 调用伪代码
```typescript
// 保存实体
const saveEntity = async (entity: Entity) => {
  await axios.post('/api/entity/schemas', entity);
};
// 保存关系/映射
const saveRelation = async (edge: ExtendedEdge) => {
  if (edge.type === RelationType.FOREIGN_KEY) {
    await axios.post('/api/entity/relations', {
      sourceEntity: edge.source,
      targetEntity: edge.target,
      relationType: edge.type,
      ...edge.relationConfig
    });
  } else {
    // 保存映射配置到 channel_adapter_config
    await axios.put(`/api/channels/${selectedChannel}/${mode}-mappings/${operationCode}`, {
      rules: edge.mappingConfig
    });
  }
};
```
***
## 九、与已有功能模块的整合策略
假设已有四个独立功能：
1. 实体配置（列表+表单）
2. API 实体配置
3. 映射配置（表格形式）
4. 业务关系配置（独立页面）
**整合方式**：
* **复用数据层**：直接使用已有的 `program_schema` 和 `channel_adapter_config` 表，不改变存储格式。
* **复用后端 API**：保留原有 CRUD 接口，前端新页面调用相同接口。
* **前端逐步替换**：
  * 先开发统一页面作为高级模式，原有功能作为简单模式保留。
  * 待统一页面稳定后，废弃旧页面，菜单只保留一个入口。
* **代码复用**：
  * 将原来独立的实体属性编辑器组件抽离成通用组件 `EntityPropertyEditor`，在右侧面板中复用。
  * 将映射表格编辑器独立，供新旧页面共用。
  * 将 ChartDB 集成封装成独立模块，便于维护。
***
## 十、开发实施步骤
1. **搭建基础环境**：安装 `@chartdb/react`，配置 React 项目支持。
2. **扩展 ChartDB 节点和边**：实现自定义节点渲染、连线类型区分。
3. **实现右侧面板的基础框架**：动态显示节点属性或映射表格。
4. **实现实体创建与编辑**：支持 JSON Schema 编辑（表单+Monaco）。
5. **实现映射表格编辑器**：支持源/目标字段树形选择、基础函数、脚本编辑。
6. **实现业务关系配置**：连线双击弹出基数设置框。
7. **实现保存与加载**：从后端加载已有实体和映射，渲染到画布；保存时同步到后端。
8. **实现测试映射功能**：调用后端测试接口，展示结果。
9. **整合已有功能模块**：确保旧数据可被新界面正确读取和修改。
10. **用户验收测试**：邀请业务人员配置一个真实场景（天猫订单接入），收集反馈优化。
***
## 十一、总结
本设计文档提供了**一个页面完成所有实体和映射配置**的完整方案，核心要点：
* 基于 ChartDB 扩展，用不同线型和颜色区分业务关系、入站映射、出站映射。
* 通过双击连线进行字段级转换配置，支持基础函数和自定义脚本。
* 右侧面板上下文感知，选中不同元素展示不同编辑器，操作集中。
* 数据模型复用现有表结构，无缝集成已有功能。
* 提供了详细的前后端伪代码和开发步骤，可直接指导 AI 或开发人员实现。
该平台将极大降低配置复杂度，提升 Loyalty 系统的灵活性和可维护性。



好的，根据您的补充要求，我将在原有设计文档基础上新增一个**补充说明章节**，明确以下四点：
1. **连线配置界面**：不使用弹出层（Modal），改为**右侧面板展开**或**底部抽屉**形式。
2. **悬浮工具栏**：画布上增加悬浮工具栏，快速创建业务实体、API实体等。
3. **字段级连线**：实体连线必须是**字段对字段**，而非实体对实体。
4. **字段设计风格**：实体字段配置采用**数据库表设计风格**（字段名、类型、备注、是否主键）。
以下是补充内容，可直接追加到原文档末尾：
***
## 十二、补充说明：交互细节与连线字段级约束
### 12.1 连线配置界面：右侧面板 / 底部抽屉（不用弹出层）
**设计原则**：用户双击连线（或单击选中连线后）时，配置界面应嵌入在主工作区内，不打断画布操作流程。
**实现方式**：
* **默认使用右侧面板**：与选中节点的属性编辑共用同一块右侧区域。当选中连线时，右侧面板自动切换到“映射配置”视图，显示源字段→目标字段的映射表格。
* **可选底部抽屉**：对于复杂映射（大量字段），可提供一个“展开底部抽屉”按钮，抽屉从画布下方滑出，显示更宽敞的映射编辑区。用户可同时看到画布和配置。
**布局示意**：
```text
┌─ 统一实体建模与映射 ──────────────────────────────────────────────────┐
│ 悬浮工具栏                                                           │
├───────────────────────────────────────────────────────────────────────┤
│                                                                       │
│                         ER 图画布区域                                 │
│                         (用户点击连线)                                │
│                                                                       │
├───────────────────────────────────────────────────────────────────────┤
│ 右侧面板（点击连线后显示映射表格）                                    │
│ ┌───────────────────────────────────────────────────────────────────┐│
│ │ 连线: TmallOrderResp → Order   [×]                                ││
│ │ ┌──────────┬────────────┬──────────────────────────────────────┐ ││
│ │ │ 源字段   │ 目标字段   │ 转换表达式                            │ ││
│ │ ├──────────┼────────────┼──────────────────────────────────────┤ ││
│ │ │ tid      │ orderId    │ -                                     │ ││
│ │ │ payment  │ totalAmount│ parseFloat                            │ ││
│ │ │ pay_time │ paidAt     │ toISOString                           │ ││
│ │ └──────────┴────────────┴──────────────────────────────────────┘ ││
│ │ [+ 添加映射]                                                       ││
│ └───────────────────────────────────────────────────────────────────┘│
└───────────────────────────────────────────────────────────────────────┘
```
**底部抽屉展开时**：
```text
┌──────────────────────────────────────────────────────────────────────┐
│                           ER 图画布                                  │
└──────────────────────────────────────────────────────────────────────┘
┌─ 映射配置抽屉（可拖拽高度） ─────────────────────────────────────────┐
│ 连线: TmallOrderResp → Order                          [收起] [保存] │
│ ┌─────────────┬──────────────┬────────────────────────────────────┐ │
│ │ 源字段      │ 目标字段     │ 转换表达式                         │ │
│ ├─────────────┼──────────────┼────────────────────────────────────┤ │
│ │ ...         │ ...          │ ...                                │ │
│ └─────────────┴──────────────┴────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────────┘
```
### 12.2 画布悬浮工具栏
**位置**：固定在画布左上角或右下角，半透明背景，鼠标悬浮时高亮。
**包含按钮**：
* **业务实体**：点击后画布进入添加模式，再点击空白处创建新业务实体节点。
* **API实体（请求）**：创建 API 请求实体。
* **API实体（响应）**：创建 API 响应实体。
* **系统实体**（可选）：如事件、日志等预定义实体。
* **连线模式切换**：可切换当前要创建的连线类型（业务关系 / 入站映射 / 出站映射），或智能识别。
**样式示意**：

```text
   ┌─────────────────────────────────┐
   │  ➕ 业务实体  │  📦 API请求  │  📬 API响应  │  🔗 连线模式  │
   └─────────────────────────────────┘
```
**交互**：
* 点击按钮后，鼠标样式变为十字准星，在画布空白处单击即可放置新实体节点。
* 如果点击“连线模式”，再依次点击两个实体上的字段 Handle，则创建对应类型的连线。
### 12.3 实体连线必须是字段对字段
**原设计问题**：连线仅连接两个实体，未指定具体字段，导致映射时需额外选择字段。
**新约束**：
* **每个实体节点上的字段应暴露可连线的端点（Handle）**。
* 用户从**源实体的某个字段**拖拽连线到**目标实体的某个字段**，松开后即建立一条字段级映射。
* 支持**多字段连线**：用户可重复拖拽不同字段对，形成多条连线，每条连线代表一个字段映射。
* 对于数组嵌套场景，可从数组字段拖拽到目标数组字段，然后展开子映射。
**连线存储**：每条连线的 `mappingConfig` 不再是一个数组，而是直接对应一个字段映射。即：**一个连线 = 一个字段对映射**。
**优势**：
* 可视化更直观：一眼看出哪些字段被映射了。
* 减少配置表格的复杂度：不需要在表格中逐行添加，直接拖拽完成。
* 符合数据库建模习惯。
**实现调整**：
* 每个实体节点渲染时，为每个字段（或至少为顶层字段）生成一个 `Handle` 组件。
* 用户拖拽连线后，后端自动创建一条映射规则，存入 `inbound_mappings` 或 `outbound_mappings` 中的相应位置。
* 多条字段级连线共享同一个 `operationCode` 或关系ID。
**伪代码示例**（节点字段渲染）：
```tsx
const EntityNode = ({ data }) => {
  const { fields, entityName } = data;
  return (
    <div className="entity-node">
      <div className="entity-header">{entityName}</div>
      {fields.map(field => (
        <div className="entity-field" key={field.name}>
          <span>{field.name}</span>
          <Handle type="source" position={Position.Right} id={`${entityName}.${field.name}`} />
          <Handle type="target" position={Position.Left} id={`${entityName}.${field.name}`} />
        </div>
      ))}
    </div>
  );
};
```
### 12.4 字段配置采用数据库表设计风格
**原设计**：使用 JSON Schema 风格，字段包含类型、描述等，但缺少数据库建模中常见的“字段备注”和“主键”显式标记。
**新要求**：字段编辑器应模拟数据库表结构设计器，提供以下列：
| 字段名         | 数据类型    | 长度/精度 | 是否主键 | 允许空 | 默认值  | 字段备注 |
| ----------- | ------- | ----- | ---- | --- | ---- | ---- |
| orderId     | VARCHAR | 32    | ✓    | ✗   | -    | 订单号  |
| memberId    | VARCHAR | 32    | ✗    | ✗   | -    | 会员ID |
| totalAmount | DECIMAL | 10,2  | ✗    | ✓   | 0.00 | 实付金额 |
**对应 JSON Schema 存储**：
```json
{
  "fieldName": "orderId",
  "dataType": "VARCHAR",
  "length": 32,
  "isPrimaryKey": true,
  "nullable": false,
  "defaultValue": null,
  "comment": "订单号",
  "jsonType": "string"   // 映射到 JSON Schema 类型
}
```
**前端实现**：使用类似表格编辑器的组件，支持添加行、删除行、下拉选择数据类型（VARCHAR, INT, DECIMAL, DATETIME, BOOLEAN, TEXT, JSON等）。同时支持将数据库类型自动映射到 JSON Schema 类型（如 VARCHAR → string, INT → integer）。
**与现有 Schema 兼容**：可以在 `field_schema` 的 `properties` 中扩展 `x-db-metadata` 字段存储数据库设计信息，同时保持标准 JSON Schema 结构。
```json
{
  "properties": {
    "orderId": {
      "type": "string",
      "description": "订单号",
      "x-db-metadata": {
        "dataType": "VARCHAR",
        "length": 32,
        "primaryKey": true,
        "nullable": false
      }
    }
  }
}
```
***
## 十三、整合后的前端组件调整建议
基于以上补充，对原设计中的组件进行微调：
1. **统一页面布局**：
   * 画布区域左上角增加悬浮工具栏（使用 `FloatingToolbar` 组件）。
   * 右侧面板默认显示实体属性（选中节点时）或字段映射列表（选中连线时）。
   * 增加一个可折叠的底部抽屉，用于批量编辑复杂映射。
2. **节点组件升级**：
   * 每个字段渲染独立的 `Handle`，支持字段级连线。
   * 节点样式可以折叠/展开字段列表，避免过长。
3. **连线逻辑变更**：
   * 取消原先“实体到实体”的连线方式。
   * 只允许从字段 Handle 拖拽到字段 Handle。
   * 连线创建后，自动弹出右侧映射配置面板，显示当前字段对的转换选项（如果不需要转换，可留空）。
4. **字段编辑器改造**：
   * 将原先的属性面板改为“数据库表风格”的表格编辑器。
   * 支持复制粘贴 Excel 风格的行（快速批量添加字段）。
以上补充内容应整合到最终的设计文档中，确保开发人员理解并实现这些交互细节。

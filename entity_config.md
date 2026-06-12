# 统一实体元数据与 API 映射设计文档
> **关联主文档**：基于AI的下一代全渠道忠诚度管理SaaS平台 v7.3\
> **版本**：2.0\
> **目的**：建立统一的实体元数据管理系统，覆盖所有业务实体（会员、订单、积分流水等）的 JSON Schema 定义、实体关系可视化配置、API 实体定义及双向映射，使规则引擎、数据接入、数据输出均基于同一套元数据，实现真正灵活的配置驱动架构。
***
## 一、设计目标
1. **统一实体元数据**：所有业务实体（包括系统预定义的会员、积分流水、规则等，以及用户自定义的业务实体）都有标准 JSON Schema 定义，包含字段名、类型、描述、主键/外键、校验规则等。
2. **实体关系可视化**：通过 ER 图（使用 ChartDB 或类似组件）直观配置实体间的一对一、一对多、多对多关系，并自动生成外键约束建议。
3. **API 实体定义**：为每个外部接口定义请求/响应实体（API 实体），支持认证方式、路径、方法、分页等元数据。
4. **双向映射配置**：
   * **入站映射**：外部 API 响应 → API 实体 → 业务实体（数据接入）。
   * **出站映射**：业务实体 → API 实体 → 外部 API 请求（数据输出，如主动推送积分变动）。
5. **规则引擎集成**：规则引擎的触发条件直接基于业务实体的 Schema 生成（例如选择 `Order.totalAmount > 100`），无需硬编码。
6. **LiteFlow 集成**：标准化组件根据入站映射配置动态生成 GraalVM 脚本，将原始请求转换为标准化事件；出站场景可由动作执行组件触发反向映射。
***
## 二、整体架构
```text
┌─────────────────────────────────────────────────────────────────────┐
│                        实体元数据管理（统一 JSON Schema）            │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐              │
│  │ 业务实体      │  │  API 实体    │  │  关系定义     │              │
│  │ (Member,      │  │ (OrderReq,   │  │ (1:N, N:N)   │              │
│  │  Order,       │  │  OrderResp)  │  │              │              │
│  │  PointTx...)  │  │              │  │              │              │
│  └──────────────┘  └──────────────┘  └──────────────┘              │
└─────────────────────────────────────────────────────────────────────┘
        │                       │                       │
        ▼                       ▼                       ▼
┌───────────────┐      ┌───────────────┐      ┌───────────────┐
│  入站映射引擎  │      │  出站映射引擎  │      │  规则引擎     │
│ (外部API→业务  │      │ (业务→外部API) │      │ (基于Schema   │
│  实体)        │      │               │      │  生成条件)    │
└───────────────┘      └───────────────┘      └───────────────┘
        │                       │                       │
        ▼                       ▼                       ▼
┌───────────────┐      ┌───────────────┐      ┌───────────────┐
│ LiteFlow      │      │ 外部系统调用  │      │ Drools 规则   │
│ 标准化组件    │      │ (Webhook/API) │      │ 执行          │
└───────────────┘      └───────────────┘      └───────────────┘
```
* **实体元数据**：所有实体（业务实体 + API 实体）的定义存储在 `program_schema` 表中，通过 `entity_category` 区分（`BUSINESS`, `API`）。
* **关系定义**：存储在 `program_schema.entity_relations` JSONB 字段中，或单独的关系表。
* **入站映射**：将外部 API 响应映射到 API 实体，再映射到业务实体；映射配置存储在 `channel_adapter_config` 扩展字段中。
* **出站映射**：将业务实体映射到 API 实体，再构建外部请求；配置存储在同一张表中，通过方向字段区分。
* **规则引擎**：规则条件编辑器动态读取业务实体 Schema，提供字段选择和运算符。
***
## 三、数据库变更（基于 v7.3）
### 3.1 扩展 `program_schema` 表
```sql
-- 增加实体分类字段
ALTER TABLE program_schema 
ADD COLUMN entity_category VARCHAR(20) DEFAULT 'SYSTEM';
COMMENT ON COLUMN program_schema.entity_category IS 'SYSTEM（系统内部实体，如Member/TransactionEvent）、BUSINESS（业务实体）、API（外部API请求/响应实体）';
-- 增加实体描述字段
ALTER TABLE program_schema 
ADD COLUMN description TEXT;
-- 增加主键/外键标记（可选，用于生成ER图）
ALTER TABLE program_schema 
ADD COLUMN primary_key VARCHAR(64);
ALTER TABLE program_schema 
ADD COLUMN foreign_keys JSONB;   -- 格式：[{"field": "memberId", "references": {"entity": "Member", "field": "memberId"}}]
-- 确保 entity_relations 字段可用于存储实体关系
-- (v7.3 已有 entity_relations JSONB，无需新增)
```
### 3.2 扩展 `channel_adapter_config` 表（支持双向映射）
```sql
-- 增加操作脚本映射（JSONB，key 为 operation_code，value 为脚本内容）
ALTER TABLE channel_adapter_config 
ADD COLUMN operation_scripts JSONB;
-- 增加字段映射配置（JSONB，区分入站和出站）
ALTER TABLE channel_adapter_config 
ADD COLUMN inbound_mappings JSONB;   -- 格式：{"orderCreate": [...]}
ALTER TABLE channel_adapter_config 
ADD COLUMN outbound_mappings JSONB;  -- 格式：{"sendPoints": [...]}
-- 增加 API 实体关联
ALTER TABLE channel_adapter_config 
ADD COLUMN api_entity_type VARCHAR(64);  -- 对应的 API 实体类型（如 "OrderCreateReq"）
```
### 3.3 新增 API 操作元数据表（可选，简化查询）
```sql
CREATE TABLE api_operation_metadata (
    id BIGSERIAL PRIMARY KEY,
    program_code VARCHAR(32) NOT NULL,
    channel VARCHAR(50) NOT NULL,
    operation_code VARCHAR(64) NOT NULL,
    operation_name VARCHAR(128) NOT NULL,
    direction VARCHAR(10) NOT NULL,     -- INBOUND / OUTBOUND
    target_business_entity VARCHAR(64), -- 入站时目标业务实体
    source_business_entity VARCHAR(64), -- 出站时源业务实体
    api_entity_type VARCHAR(64),        -- 关联的 API 实体类型
    http_method VARCHAR(10),            -- GET, POST, PUT, DELETE
    http_path VARCHAR(256),
    auth_type VARCHAR(32),              -- BASIC, BEARER, HMAC, NONE
    auth_config JSONB,
    pagination_type VARCHAR(20),        -- NONE, PAGE_NUMBER, CURSOR
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(program_code, channel, operation_code)
);
```
***
## 四、实体元数据管理
### 4.1 实体列表与编辑器（集成 ER 图）
**页面布局**：左侧 ER 图画布（ChartDB），右侧实体/属性面板。
```text
┌─ 实体元数据管理 ────────────────────────────────────────────────────────┐
│ [ER图] [实体列表]  [关系列表]                         [导入] [导出]      │
├──────────────────────────────────────────────────────────────────────────┤
│ ┌────────────────────────────────────────────────────────────────────┐   │
│ │                          ER 图画布 (ChartDB)                        │   │
│ │  ┌──────────┐        ┌──────────┐        ┌──────────┐             │   │
│ │  │  Member  │ 1    N │  Order   │ 1    1 │  PointTx │             │   │
│ │  │ ──────── │ ────── │ ──────── │ ────── │ ──────── │             │   │
│ │  │ memberId │        │ orderId  │        │  txId    │             │   │
│ │  │ name     │        │ memberId │        │ memberId │             │   │
│ │  │ ...      │        │ totalAmt │        │ amount   │             │   │
│ │  └──────────┘        └──────────┘        └──────────┘             │   │
│ │                                                                     │   │
│ │  ┌──────────┐        ┌──────────┐                                  │   │
│ │  │ OrderItem│ N     1 │ Product  │                                  │   │
│ │  │ ──────── │ ────── │ ──────── │                                  │   │
│ │  │ itemId   │        │ skuId    │                                  │   │
│ │  │ orderId  │        │ name     │                                  │   │
│ │  │ skuId    │        │ ...      │                                  │   │
│ │  └──────────┘        └──────────┘                                  │   │
│ └────────────────────────────────────────────────────────────────────┘   │
│                                                                          │
│ 右侧面板（点击实体或关系时显示）                                          │
│ ┌────────────────────────────────────────────────────────────────────┐   │
│ │ 实体: Order                                                         │   │
│ │ ┌──────────┬────────┬──────┬────────┬─────────────┐               │   │
│ │ │ 字段名   │ 类型   │ 主键 │ 外键   │ 描述        │               │   │
│ │ ├──────────┼────────┼──────┼────────┼─────────────┤               │   │
│ │ │ orderId  │ string │ ✓    │        │ 订单号      │               │   │
│ │ │ memberId │ string │      │ Member │ 会员ID      │               │   │
│ │ │ totalAmt │ number │      │        │ 实付金额    │               │   │
│ │ └──────────┴────────┴──────┴────────┴─────────────┘               │   │
│ │ [+ 添加字段]                                                        │   │
│ └────────────────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────────────┘
```
**操作说明**：
* **ER 图**：使用 ChartDB 渲染实体关系，支持拖拽添加实体、连线配置关系（双击连线设置 1:N, N:N 等）。
* **实体列表**：切换表格视图，管理所有实体（系统实体、业务实体、API 实体）。
* **属性编辑**：支持 JSON Schema 中的标准属性（type, format, required, minimum, maximum, enum 等），以及主键/外键标记。
### 4.2 实体定义 JSON Schema 示例（存储于 `program_schema.field_schema`）
```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "properties": {
    "orderId": { "type": "string", "description": "订单号", "primaryKey": true },
    "memberId": { "type": "string", "description": "会员ID", "foreignKey": { "entity": "Member", "field": "memberId" } },
    "totalAmount": { "type": "number", "minimum": 0, "description": "实付金额" },
    "status": { "type": "string", "enum": ["PENDING", "PAID", "SHIPPED", "FINISHED"] },
    "items": {
      "type": "array",
      "items": { "$ref": "#/definitions/OrderItem" }
    }
  },
  "required": ["orderId", "memberId"],
  "definitions": {
    "OrderItem": {
      "type": "object",
      "properties": {
        "sku": { "type": "string" },
        "quantity": { "type": "integer" },
        "price": { "type": "number" }
      }
    }
  }
}
```
### 4.3 规则引擎集成
规则编辑器中的“触发条件”构建器，动态读取业务实体 Schema，提供如下 UI：
```text
选择实体: [Order ▼]
选择字段: [totalAmount ▼]
运算符: [大于 ▼]
值: [100]
```
后端生成 Drools 条件：`$event: EventFact(getPayloadNumber("total_amount") > 100)`，其中 `total_amount` 来自标准化事件的 payload 字段，其结构由 `Order` 实体的 Schema 决定。
***
## 五、API 实体定义
### 5.1 API 实体管理
API 实体与业务实体类似，但主要用于描述外部 API 的请求/响应结构。例如，天猫订单查询接口的响应实体。
```sql
-- 插入 API 实体
INSERT INTO program_schema (program_code, entity_type, entity_category, version, field_schema, description)
VALUES ('BRAND_A', 'TmallOrderResp', 'API', 'v1', '{
    "type": "object",
    "properties": {
        "tid": { "type": "string" },
        "payment": { "type": "string" },
        "pay_time": { "type": "string", "format": "date-time" },
        "orders": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "oid": { "type": "string" },
                    "price": { "type": "string" },
                    "num": { "type": "string" }
                }
            }
        }
    }
}', '天猫订单查询响应结构');
```
### 5.2 API 操作配置（入站）
在渠道配置界面，为每个 API 操作绑定 API 实体，并配置映射到业务实体。
```text
┌─ 入站 API 配置：天猫订单创建 ──────────────────────────────────────────┐
│ 操作代码: orderCreate                                                   │
│ 外部 API: GET /trade/simple/get                                        │
│ 认证方式: [HMAC-SHA256 ▼]  密钥: [****]                                 │
│ 响应实体: [TmallOrderResp ▼]                                            │
│ 目标业务实体: [Order ▼]                                                 │
│                                                                         │
│ 映射配置（右侧为映射编辑器）                                             │
└──────────────────────────────────────────────────────────────────────────┘
```
### 5.3 出站 API 配置（业务实体 → 外部 API）
用于主动推送数据（如积分变动通知）到外部系统。
```text
┌─ 出站 API 配置：积分变动通知 ──────────────────────────────────────────┐
│ 操作代码: sendPointsChange                                              │
│ 外部 API: POST /api/points/change                                      │
│ 认证方式: [Bearer Token ▼]   Token: [****]                             │
│ 请求实体: [PointsChangeReq ▼]                                           │
│ 源业务实体: [PointTx ▼]                                                 │
│                                                                         │
│ 映射配置（业务实体属性 → 请求实体字段）                                  │
└──────────────────────────────────────────────────────────────────────────┘
```
***
## 六、双向映射配置
### 6.1 入站映射配置界面
与之前设计的 API 映射编辑器类似，但源是 API 实体，目标是业务实体。支持路径映射、表达式、常量，以及数组嵌套处理。
**存储格式**（在 `channel_adapter_config.inbound_mappings` 中）：
```json
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
### 6.2 出站映射配置界面
反向操作：选择业务实体属性，映射到 API 请求实体字段，支持固定值、表达式、聚合等。
```text
┌─ 出站映射配置：积分变动通知 ──────────────────────────────────────────┐
│ 源业务实体: PointTx                                                    │
│ 目标 API 实体: PointsChangeReq                                         │
│                                                                         │
│ ┌───────────────┬─────────────────┬─────────────────────────────────┐  │
│ │ 源属性        │ 目标字段        │ 转换表达式                      │  │
│ ├───────────────┼─────────────────┼─────────────────────────────────┤  │
│ │ memberId      │ userId          │ -                               │  │
│ │ amount        │ points          │ toString                        │  │
│ │ createdAt     │ eventTime       │ toISOString                     │  │
│ │ (常量)        │ eventType       │ "POINTS_CHANGE"                 │  │
│ └───────────────┴─────────────────┴─────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────┘
```
**存储格式**（在 `channel_adapter_config.outbound_mappings` 中）：
```json
{
  "sendPointsChange": [
    { "source": "memberId", "target": "userId", "type": "PATH" },
    { "source": "amount", "target": "points", "type": "EXPRESSION", "expression": "toString" },
    { "source": "createdAt", "target": "eventTime", "type": "EXPRESSION", "expression": "toISOString" },
    { "constant": "POINTS_CHANGE", "target": "eventType", "type": "CONSTANT" }
  ]
}
```
### 6.3 运行时执行
* **入站**：`StandardizeComponent` 根据 `inbound_mappings` 生成脚本执行。
* **出站**：可在 `ActionExecuteComponent` 中增加出站动作，或由独立服务（如 `OutboundEventProcessor`）监听内部事件触发。
***
## 七、Java 伪代码
### 7.1 实体元数据服务
```java
@Service
public class EntityMetadataService {
    // 获取实体 Schema（支持缓存）
    public JSONObject getEntitySchema(String programCode, String entityType) {
        ProgramSchema schema = programSchemaRepo.findByProgramCodeAndEntityTypeAndStatus(
            programCode, entityType, "ACTIVE");
        return schema.getFieldSchema();
    }
    // 获取实体关系图
    public JSONObject getEntityGraph(String programCode) {
        List<ProgramSchema> entities = programSchemaRepo.findByProgramCodeAndEntityCategory(
            programCode, "BUSINESS");
        // 构建节点和边，用于前端 ER 图
        return buildGraph(entities);
    }
}
```
### 7.2 入站映射脚本生成器
```java
@Service
public class InboundMappingGenerator {
    public String generateScript(ApiOperationMetadata opMeta, 
                                 List<InboundMapping> mappings,
                                 String apiEntityType, 
                                 String businessEntityType) {
        StringBuilder script = new StringBuilder();
        script.append("function transform(source, context) {\n");
        script.append("    const root = source;\n");
        script.append("    const event = {\n");
        script.append("        event_type: \"").append(opMeta.getEventType()).append("\",\n");
        script.append("        channel: \"").append(opMeta.getChannel()).append("\",\n");
        script.append("        idempotent_key: extractIdempotentKey(root, \"").append(opMeta.getIdempotentKeySource()).append("\"),\n");
        script.append("        event_time: extractEventTime(root),\n");
        script.append("        payload: {}\n");
        script.append("    };\n");
        
        // 生成映射代码
        for (InboundMapping mapping : mappings) {
            generateMappingCode(script, mapping);
        }
        
        // 提取身份标识（可根据需要配置）
        script.append("    // 身份标识提取\n");
        script.append("    if (getValueByPath(root, \"ouid\")) {\n");
        script.append("        context.setIdentity(\"TMALL_OUID\", getValueByPath(root, \"ouid\"));\n");
        script.append("    }\n");
        
        script.append("    return event;\n");
        script.append("}\n");
        return script.toString();
    }
}
```
### 7.3 出站映射执行器
```java
@Service
public class OutboundMappingExecutor {
    public Object executeOutbound(String programCode, String operationCode, 
                                  Map<String, Object> businessEntityData) {
        // 1. 获取出站映射配置
        ChannelAdapterConfig config = configRepo.findByProgramCodeAndChannel(programCode, channel);
        JSONObject mappings = config.getOutboundMappings().getJSONObject(operationCode);
        ApiOperationMetadata opMeta = apiMetaRepo.findByOperationCode(operationCode);
        
        // 2. 构建 API 请求实体
        JSONObject requestEntity = new JSONObject();
        for (OutboundMapping mapping : mappings) {
            String targetPath = mapping.getTarget();
            Object value = evaluateExpression(businessEntityData, mapping);
            setValueByPath(requestEntity, targetPath, value);
        }
        
        // 3. 调用外部 API
        String response = httpClient.send(opMeta.getHttpMethod(), opMeta.getHttpPath(), 
                                          requestEntity, buildAuthHeaders(opMeta));
        return response;
    }
}
```
***
## 八、与规则引擎的衔接
规则编辑器中的条件构建器需要动态读取业务实体的 JSON Schema，生成 DRL 条件。具体做法：
* 后端提供 `/api/rules/entity-fields?entityType=Order` 接口，返回该实体所有字段及其类型。
* 前端根据类型渲染不同的输入控件（数值区间、下拉枚举、日期范围等）。
* 后端将前端提交的条件表达式转换为 DRL 片段。
例如，前端提交：
```json
{
  "entity": "Order",
  "field": "totalAmount",
  "operator": ">",
  "value": 100
}
```
后端生成：`$event: EventFact(getPayloadNumber("total_amount") > 100)`。
***
## 九、总结
本设计文档在 v7.3 基础上，通过扩展 `program_schema` 和 `channel_adapter_config` 表，建立了统一的实体元数据管理系统，支持：
* 所有业务实体和 API 实体的 JSON Schema 定义、ER 图关系配置。
* 入站映射（外部 API → API 实体 → 业务实体）和出站映射（业务实体 → API 实体 → 外部 API）。
* 与规则引擎无缝集成，规则条件基于实体 Schema 动态生成。
* 与 LiteFlow 集成，标准化组件使用入站映射脚本。
此设计实现了真正的配置驱动，使 Loyalty 平台能够灵活接入各种外部系统，同时保持内部规则引擎的通用性。所有配置均可通过可视化界面完成，大幅降低开发维护成本。
# 统一实体元数据与 API 映射设计文档（补充：前端界面与 ChartDB 集成）
> **关联主文档**：《业务实体配置与 API 映射设计文档》v2.0\
> **版本**：2.1\
> **目的**：详细描述前端界面交互、ER 图可视化配置（基于 ChartDB）及其扩展功能，确保开发人员可落地实现。
***
## 一、前端界面整体规划
### 1.1 菜单结构
在管理后台“数据建模”模块下增加两个子菜单：
* **实体建模**：管理业务实体、API 实体及 ER 图。
* **API 映射**：配置渠道的入站/出站映射。
```text
数据建模
  ├── Schema编辑器（原有，用于会员/交易扩展属性）
  ├── 实体建模（新增）← 本设计
  └── API映射配置（新增）← 本设计
```
### 1.2 实体建模页面
**路由**：`/entity-modeling`
**布局**：左右结构，左侧 ER 图画布（ChartDB），右侧实体/属性/关系编辑面板。
***
## 二、ER 图组件选型与扩展
### 2.1 为什么选择 ChartDB？
[ChartDB](https://chartdb.io/) 是一个开源的数据库关系图工具，支持：
* 拖拽生成实体关系图（DDL 导入/导出）。
* 自定义实体属性（字段、类型、主键、外键）。
* 导出为 JSON 或 SQL。
* 提供 React 组件版本（`@chartdb/react`）可嵌入现有项目。
**优势**：界面现代、支持外键连线、可编辑属性、开源可扩展。
### 2.2 集成方式
安装依赖（假设使用 React + TypeScript）：

```bash
npm install @chartdb/react chartdb-core
```
在页面中引入画布组件：
```tsx
import { ChartDB, useChartDBStore } from '@chartdb/react';
const ERDiagram = () => {
  const { tables, relations, addTable, updateTable, addRelation } = useChartDBStore();
  return (
    <ChartDB
      tables={tables}
      relations={relations}
      onTableClick={(table) => openEntityPanel(table)}
      onRelationClick={(rel) => openRelationPanel(rel)}
      onCanvasDoubleClick={() => createNewEntity()}
    />
  );
};
```
### 2.3 需要扩展的功能
原生 ChartDB 主要用于数据库建模，我们需要扩展以下能力以支持我们的业务：
| 原生功能                      | 扩展需求                                                      | 实现方式                                                      |
| ------------------------- | --------------------------------------------------------- | --------------------------------------------------------- |
| 实体 = 数据库表                 | 实体分为 BUSINESS、API、SYSTEM 三种类别，且有不同图标/颜色                   | 自定义 `entityType` 字段，在渲染时根据类型改变节点样式（背景色、图标）                |
| 字段类型仅 SQL 类型              | 需要 JSON Schema 类型（string, number, boolean, array, object） | 添加 `jsonType` 属性，同时兼容 SQL 类型映射                            |
| 不支持数组/嵌套对象                | 支持 `array` 和 `object` 类型，并支持展开定义子字段                       | 扩展属性面板，允许为 `array` 或 `object` 类型定义子结构，存储在 `fieldSchema` 中 |
| 不支持 API 实体的额外元数据（URL、认证等） | 为 API 实体增加“API 配置”选项卡                                     | 在右侧面板中增加 API 专用表单                                         |
| 关系仅支持外键关联                 | 同时支持“虚拟关系”（用于 API 实体间的逻辑关联）                               | 增加关系类型枚举：`FOREIGN_KEY` 和 `LOGICAL`                        |
| 导出仅 SQL DDL               | 导出 JSON Schema 和业务实体定义                                    | 添加“导出 JSON Schema”按钮，调用后端接口保存到 `program_schema` 表         |
### 2.4 自定义节点样式（示例）
```typescript
const getNodeStyle = (entity: Entity) => {
  switch (entity.category) {
    case 'BUSINESS': return { backgroundColor: '#e6f7ff', borderColor: '#1890ff' };
    case 'API': return { backgroundColor: '#f6ffed', borderColor: '#52c41a' };
    case 'SYSTEM': return { backgroundColor: '#fff7e6', borderColor: '#fa8c16' };
    default: return {};
  }
};
```
***
## 三、实体建模界面详细设计
### 3.1 整体布局
```text
┌─ 实体建模 ──────────────────────────────────────────────────────────────┐
│ [ER图] [实体列表] [关系列表]                    [导入] [导出] [保存]      │
├──────────────────────────────────────────────────────────────────────────┤
│ ┌────────────────────────────────────────────────────────────────────┐   │
│ │                         ER 图画布 (ChartDB)                         │   │
│ │  ┌──────────┐        ┌──────────┐        ┌──────────┐             │   │
│ │  │  Member  │ 1    N │  Order   │ 1    1 │  PointTx │             │   │
│ │  │ (业务)   │ ────── │ (业务)   │ ────── │ (系统)   │             │   │
│ │  └──────────┘        └──────────┘        └──────────┘             │   │
│ │                                                                     │   │
│ │  ┌──────────┐        ┌──────────┐                                  │   │
│ │  │ TmallResp│        │ OrderCreateReq (API)                        │   │
│ │  │ (API)    │        └──────────┘                                  │   │
│ │  └──────────┘                                                       │   │
│ └────────────────────────────────────────────────────────────────────┘   │
│                                                                          │
│ 右侧面板（点击实体或关系时显示）                                          │
│ ┌────────────────────────────────────────────────────────────────────┐   │
│ │ 实体: Order  (BUSINESS)               [切换为 API] [删除]           │   │
│ │ ┌────────────────────────────────────────────────────────────────┐ │   │
│ │ │ 属性列表                                         [+ 添加属性]   │ │   │
│ │ │ ┌──────────┬──────────┬──────┬────────┬─────────┐             │ │   │
│ │ │ │ 字段名   │ JSON类型 │ 主键 │ 外键   │ 描述    │             │ │   │
│ │ │ ├──────────┼──────────┼──────┼────────┼─────────┤             │ │   │
│ │ │ │ orderId  │ string   │ ✓    │        │ 订单号  │             │ │   │
│ │ │ │ memberId │ string   │      │ Member │ 会员ID  │             │ │   │
│ │ │ │ items    │ array    │      │        │ 商品列表 │ [编辑子结构]│ │   │
│ │ │ └──────────┴──────────┴──────┴────────┴─────────┘             │ │   │
│ │ └────────────────────────────────────────────────────────────────┘ │   │
│ │                                                                     │   │
│ │ ┌─ API 配置（仅当实体类型为 API 时显示）─────────────────────────┐  │   │
│ │ │ 实体类型: [响应实体 ▼]   (请求实体/响应实体)                    │  │   │
│ │ │ 关联操作: [orderCreate ▼]                                      │  │   │
│ │ └────────────────────────────────────────────────────────────────┘  │   │
│ └────────────────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────────────┘
```
### 3.2 实体列表视图（切换代替 ER 图）
点击“实体列表”选项卡，显示表格视图：
```text
┌─ 实体列表 ──────────────────────────────────────────────────────────────┐
│ [+ 新建实体]                                               [搜索]       │
├────────────┬──────────────┬────────┬──────────────────────────────────┤
│ 实体标识   │ 名称         │ 类别   │ 操作                             │
├────────────┼──────────────┼────────┼──────────────────────────────────┤
│ Member     │ 会员         │ 业务   │ [编辑] [删除]                     │
│ Order      │ 订单         │ 业务   │ [编辑] [删除]                     │
│ TmallResp  │ 天猫订单响应 │ API    │ [编辑] [删除]                     │
└────────────┴──────────────┴────────┴──────────────────────────────────┘
```
### 3.3 实体属性编辑（右侧面板细节）
* **添加属性**：弹出模态框，选择 JSON 类型（string, number, boolean, integer, object, array）。
* **编辑子结构**：当类型为 object 或 array 时，可点击“编辑子结构”打开嵌套编辑器（类似树形表格）。
* **主键**：勾选后，该字段在 Schema 中标记 `primaryKey: true`。
* **外键**：勾选后，选择目标实体及关联字段。
* **校验规则**：对 number/string 类型可添加 min/max/pattern 等，存储在 `validation_rules` 中。
### 3.4 关系配置（连线操作）
* **创建关系**：从实体 A 的字段拖拽到实体 B（或直接在连线工具中点选两个实体），弹出关系配置框：
```text
┌─ 配置关系 ──────────────────────────────────────────────────────────────┐
│ 源实体: Order                                                           │
│ 目标实体: OrderItem                                                     │
│ 关系类型: [一对多 ▼]                                                    │
│ 源字段: orderId                                                         │
│ 目标字段: parentOrderId                                                 │
│ 级联删除: [ ]                                                           │
│                              [确定] [取消]                              │
└─────────────────────────────────────────────────────────────────────────┘
```
* 关系数据存储在 `program_schema.entity_relations` JSONB 中。
***
## 四、API 映射配置界面
### 4.1 页面布局
**路由**：`/api-mapping`
**布局**：左侧渠道树 + 操作列表，右侧映射编辑器。
```text
┌─ API 映射配置 ──────────────────────────────────────────────────────────┐
│ ┌───────────────┬─────────────────────────────────────────────────────┐│
│ │ 渠道列表       │ 操作：天猫订单创建 (orderCreate)                    ││
│ │ ├─ 天猫       │ 方向: [入站 ▼]                                      ││
│ │ │  ├─ 订单创建│ ┌─────────────────────────────────────────────────┐││
│ │ │  ├─ 订单退款│ │ 源实体: TmallOrderResp (API)                     │││
│ │ │  └─ 会员注册│ │ 目标实体: Order (业务)                           │││
│ │ ├─ 京东       │ │                                                  │││
│ │ └─ 抖音       │ │ 映射表格（可拖拽、表达式）                        │││
│ │               │ │ ┌────────────┬────────────┬──────────────────┐  │││
│ │               │ │ │ 源字段     │ 目标字段   │ 转换表达式       │  │││
│ │               │ │ ├────────────┼────────────┼──────────────────┤  │││
│ │               │ │ │ tid        │ orderId    │ -                │  │││
│ │               │ │ │ payment    │ totalAmount│ parseFloat       │  │││
│ │               │ │ │ pay_time   │ paidAt     │ toISOString      │  │││
│ │               │ │ │ orders     │ items      │ -                │  │││
│ │               │ │ │   .oid     │ items[].id │ -                │  │││
│ │               │ │ └────────────┴────────────┴──────────────────┘  │││
│ │               │ │ [智能推荐] [测试映射] [保存] [发布]              │││
│ └───────────────┴─────────────────────────────────────────────────────┘│
└──────────────────────────────────────────────────────────────────────────┘
```
### 4.2 映射表格交互
* **添加映射行**：点击“+ 添加映射”，弹出字段选择器（树形选择源字段和目标字段）。
* **拖拽映射**：从左侧 API 实体树拖拽字段到右侧业务实体字段上，自动生成映射行。
* **表达式编辑**：双击“转换表达式”单元格，弹出表达式编辑器（支持内置函数库：parseFloat, toISOString, formatDate, 自定义 JS 表达式）。
* **智能推荐**：调用后端 API，根据字段名称相似度自动生成映射建议。
### 4.3 测试映射面板
点击“测试映射”展开底部抽屉：
```text
┌─ 测试映射 ──────────────────────────────────────────────────────────────┐
│ 输入 JSON:                                                              │
│ ┌─────────────────────────────────────────────────────────────────────┐ │
│ │ { "trade_simple_get_response": { "trade": {...} } }                 │ │
│ └─────────────────────────────────────────────────────────────────────┘ │
│ [运行测试]                                                              │
│ 输出 TransactionEvent:                                                 │
│ ┌─────────────────────────────────────────────────────────────────────┐ │
│ │ { "event_type": "ORDER", "payload": {...} }                         │ │
│ └─────────────────────────────────────────────────────────────────────┘ │
│ 错误信息: 无                                                            │
└──────────────────────────────────────────────────────────────────────────┘
```
### 4.4 出站映射配置
方向切换为“出站”时，界面反向：
* 源实体变为业务实体（如 `PointTx`）。
* 目标实体变为 API 实体（如 `PointsChangeReq`）。
* 映射表格允许使用常量、表达式。
***
## 五、前端与后端交互 API
| 接口                                                      | 方法      | 说明                            |
| ------------------------------------------------------- | ------- | ----------------------------- |
| `/api/entity/schemas?category=BUSINESS`                 | GET     | 获取所有业务实体列表（含 JSON Schema）     |
| `/api/entity/schemas/{entityType}`                      | GET     | 获取单个实体的完整 Schema              |
| `/api/entity/schemas`                                   | POST    | 创建/更新实体（保存到 `program_schema`） |
| `/api/entity/relations`                                 | GET     | 获取所有实体关系（用于 ER 图）             |
| `/api/entity/relations`                                 | POST    | 保存实体关系                        |
| `/api/channels/{channel}/operations`                    | GET     | 获取渠道下的所有操作                    |
| `/api/channels/{channel}/inbound-mappings/{operation}`  | GET/PUT | 获取/保存入站映射配置                   |
| `/api/channels/{channel}/outbound-mappings/{operation}` | GET/PUT | 获取/保存出站映射配置                   |
| `/api/channels/{channel}/test-mapping`                  | POST    | 测试映射（执行 GraalVM 脚本）           |
***
## 六、ChartDB 扩展开发指南
### 6.1 自定义节点渲染
ChartDB 允许通过 `customNodeRenderer` 自定义节点外观。我们在项目中实现：
```tsx
const CustomNodeRenderer = ({ node }) => {
  const entity = node.data.entity;
  const style = getCategoryStyle(entity.category);
  return (
    <div style={{ ...style, borderRadius: 8, padding: 8, border: '1px solid' }}>
      <strong>{entity.entity_type}</strong>
      {entity.category === 'API' && <Tag color="green">API</Tag>}
      <div style={{ fontSize: 12 }}>{entity.description}</div>
    </div>
  );
};
```
### 6.2 扩展属性面板
在原 ChartDB 右侧属性面板基础上，我们增加“JSON Schema”选项卡，使用 Monaco Editor 直接编辑 Schema JSON，适用于高级用户。同时保持表单式编辑简单字段。
### 6.3 导出/导入与后端同步
* **导出**：将当前 ER 图中的实体和关系转换为后端 API 调用的 JSON 格式（`/api/entity/schemas` 批量保存）。
* **导入**：从后端加载现有实体，渲染到 ChartDB。
### 6.4 与 v7.3 现有 `program_schema` 表同步
保存实体时，将 `field_schema` 字段存储为标准 JSON Schema；`entity_relations` 存储关系 JSON。关系格式示例：
```json
[
  {
    "sourceEntity": "Order",
    "targetEntity": "OrderItem",
    "relationType": "ONE_TO_MANY",
    "sourceField": "orderId",
    "targetField": "parentOrderId",
    "onDelete": "CASCADE"
  }
]
```
***
## 七、开发顺序建议
1. **后端**：完成数据库扩展（`program_schema` 增加字段、创建 `api_operation_metadata` 表、扩展 `channel_adapter_config`）。
2. **后端**：实现实体 CRUD API、关系保存 API、映射配置 CRUD API、脚本生成与测试 API。
3. **前端**：集成 ChartDB，实现实体建模界面（ER 图、属性面板、关系配置）。
4. **前端**：实现 API 映射配置界面（渠道树、映射表格、表达式编辑器、测试面板）。
5. **联调**：验证实体定义保存到 `program_schema`，映射配置保存到 `channel_adapter_config`，标准化组件能正确读取并执行脚本。
***
## 八、总结
本补充设计文档详细描述了：
* **前端界面布局**：实体建模页（ER 图 + 右侧面板）、API 映射配置页。
* **ChartDB 集成方案**：选型理由、扩展功能（实体分类、数组/对象支持、API 元数据、自定义样式）。
* **交互细节**：属性编辑、关系连线、映射表格、测试面板。
* **后端 API**：实体管理、关系管理、映射配置、脚本测试。
开发人员可依据此文档实现一个功能完整的统一实体元数据与映射配置系统，使 Loyalty 平台真正具备配置驱动的数据接入/输出能力，并与规则引擎、LiteFlow 无缝协作。

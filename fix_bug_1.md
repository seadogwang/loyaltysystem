# 交易事件表扩展与标准化结构设计
***
## 一、概述
为支持客服快速查询交易流水和行为轨迹，对核心事件收件箱表 `event_inbox` 进行扩展，增加独立列承载高频查询字段（订单号、金额、状态、时间、行为代码等），同时保留完整 JSONB `payload` 存储标准化事件的详细数据。通过 **独立列 + JSONB** 的混合存储，实现列表展示的高性能与详情数据的完整性。
***
## 二、`event_inbox` 表扩展设计
### 2.1 原有表结构（保留）
```sql
CREATE TABLE event_inbox (
    id BIGSERIAL PRIMARY KEY,
    program_code VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(300) NOT NULL,
    payload JSONB NOT NULL,
    transform_logs JSONB,
    retry_count INT DEFAULT 0,
    max_retry INT DEFAULT 3,
    last_error TEXT,
    reject_reason VARCHAR(50),
    next_retry_at TIMESTAMPTZ,
    status VARCHAR(30) NOT NULL DEFAULT 'RECEIVED',
    created_at TIMESTAMPTZ DEFAULT NOW()
);
```
### 2.2 新增独立列（用于列表展示与过滤）
| 字段名             | 类型            | 说明          | 订单事件取值                   | 行为事件取值             |
| --------------- | ------------- | ----------- | ------------------------ | ------------------ |
| `member_id`     | VARCHAR(64)   | 会员 One-ID   | 匹配后填充                    | 匹配后填充              |
| `event_type`    | VARCHAR(32)   | 事件类型        | `ORDER` / `ORDER_REFUND` | `BEHAVIOR`         |
| `event_time`    | TIMESTAMPTZ   | 事件主时间（用于排序） | 下单时间（`created_at`）       | 行为发生时间             |
| `channel`       | VARCHAR(32)   | 渠道          | `TMALL` / `JD` 等         | `APP` / `WECHAT` 等 |
| `order_id`      | VARCHAR(64)   | 订单号         | 订单号                      | `NULL`             |
| `total_amount`  | DECIMAL(18,2) | 实付金额        | 订单实付                     | `NULL`             |
| `order_status`  | VARCHAR(32)   | 订单状态        | `TRADE_FINISHED` 等       | `NULL`             |
| `paid_at`       | TIMESTAMPTZ   | 付款时间        | 付款时间                     | `NULL`             |
| `shipped_at`    | TIMESTAMPTZ   | 发货时间        | 发货时间                     | `NULL`             |
| `completed_at`  | TIMESTAMPTZ   | 完成时间        | 完成时间                     | `NULL`             |
| `behavior_code` | VARCHAR(64)   | 行为代码        | `NULL`                   | 例如 `SIGN_IN`       |
**说明**：
* 订单事件的 `event_time` 使用下单时间（`created_at`），以保证时间线的自然顺序。
* 行为事件的 `event_time` 使用行为实际发生时间。
* 独立列仅存储列表展示必需的字段，复杂信息（促销、商品明细、渠道扩展、行为扩展属性）保留在 `payload` JSONB 中。
### 2.3 索引
```sql
CREATE INDEX idx_event_inbox_order_id ON event_inbox(order_id);
CREATE INDEX idx_event_inbox_member_id ON event_inbox(member_id);
CREATE INDEX idx_event_inbox_event_time ON event_inbox(event_time);
CREATE INDEX idx_event_inbox_channel ON event_inbox(channel);
CREATE INDEX idx_event_inbox_order_status ON event_inbox(order_status);
CREATE INDEX idx_event_inbox_paid_at ON event_inbox(paid_at);
CREATE INDEX idx_event_inbox_total_amount ON event_inbox(total_amount);
CREATE INDEX idx_event_inbox_member_time ON event_inbox(member_id, event_time DESC);
CREATE INDEX idx_event_inbox_type_time ON event_inbox(event_type, event_time);
CREATE INDEX idx_event_inbox_behavior_code ON event_inbox(behavior_code);
```
### 2.4 数据填充逻辑（伪代码）
```java
// SPI 网关完成映射后生成 TransactionEvent 对象
EventInbox record = new EventInbox();
record.setProgramCode(programCode);
record.setIdempotencyKey(event.getIdempotencyKey());
record.setPayload(JSON.toJSONBytes(event));
record.setMemberId(event.getMemberId());
record.setEventType(event.getEventType());
record.setChannel(event.getChannel());
// 若为订单事件，填充订单相关独立列
if ("ORDER".equals(event.getEventType()) || "ORDER_REFUND".equals(event.getEventType())) {
    OrderPayload p = event.getPayload();
    record.setEventTime(p.getCreatedAt());          // 使用下单时间作为主时间
    record.setOrderId(p.getOrderId());
    record.setTotalAmount(p.getTotalAmount());
    record.setOrderStatus(p.getStatus());
    record.setPaidAt(p.getPaidAt());
    record.setShippedAt(p.getShippedAt());
    record.setCompletedAt(p.getCompletedAt());
}
// 若为行为事件，填充行为代码
if ("BEHAVIOR".equals(event.getEventType())) {
    BehaviorPayload p = event.getPayload();
    record.setEventTime(p.getBehaviorTime());       // 使用行为发生时间
    record.setBehaviorCode(p.getBehaviorCode());
}
eventInboxRepo.save(record);
```
***
## 三、订单事件 payload 标准结构
### 3.1 顶层 TransactionEvent 结构（订单）
```json
{
  "event_id": "string (系统生成)",
  "member_id": "string (One-ID 匹配后填充)",
  "event_type": "ORDER | ORDER_REFUND",
  "channel": "TMALL | JD | DOUYIN | WECHAT | POS",
  "event_time": "ISO 8601 UTC (下单时间)",
  "idempotent_key": "string (订单号)",
  "payload": { ... }
}
```
### 3.2 payload 字段定义
| 字段名               | 类型       | 必填 | 说明                       |
| ----------------- | -------- | -- | ------------------------ |
| `order_id`        | string   | ✅  | 订单号                      |
| `total_amount`    | number   | ✅  | 实付金额（元，两位小数）             |
| `original_amount` | number   | ❌  | 原订单总金额                   |
| `discount_amount` | number   | ❌  | 优惠总金额                    |
| `post_fee`        | number   | ❌  | 运费                       |
| `status`          | string   | ✅  | 订单状态（见枚举）                |
| `created_at`      | ISO 8601 | ✅  | 下单时间                     |
| `paid_at`         | ISO 8601 | ❌  | 付款时间                     |
| `shipped_at`      | ISO 8601 | ❌  | 发货时间                     |
| `completed_at`    | ISO 8601 | ❌  | 完成时间                     |
| `buyer_memo`      | string   | ❌  | 买家备注                     |
| `seller_memo`     | string   | ❌  | 卖家备注                     |
| `buyer_message`   | string   | ❌  | 买家留言                     |
| `shipping_type`   | string   | ❌  | 配送方式                     |
| `promotions`      | array    | ❌  | 促销明细                     |
| `items`           | array    | ✅  | 商品明细                     |
| `channel_ext`     | object   | ❌  | 渠道扩展字段（如 `ouid`, `omid`） |
**订单状态枚举**：\
`WAIT_BUYER_PAY`、`WAIT_SELLER_SEND`、`WAIT_BUYER_CONFIRM`、`TRADE_FINISHED`、`TRADE_CLOSED`、`TRADE_REFUNDED`
**促销明细元素**：\
`id`(string), `name`(string), `discount_fee`(number), `type`(string)
**商品明细元素**：\
`title`(string), `price`(number), `quantity`(int), `total_fee`(number), `payment`(number), `sku_properties`(string), `outer_iid`(string), `outer_sku_id`(string), `sku_id`(string), `category_id`(string)
### 3.3 完整示例（天猫订单）
```json
{
  "event_type": "ORDER",
  "channel": "TMALL",
  "event_time": "2026-06-07T10:23:15Z",
  "idempotent_key": "TM2026060700001",
  "payload": {
    "order_id": "TM2026060700001",
    "total_amount": 699.00,
    "original_amount": 759.00,
    "discount_amount": 50.00,
    "post_fee": 10.00,
    "status": "TRADE_FINISHED",
    "created_at": "2026-06-07T10:23:15Z",
    "paid_at": "2026-06-07T10:25:32Z",
    "shipped_at": "2026-06-07T15:10:00Z",
    "completed_at": "2026-06-10T14:20:11Z",
    "buyer_memo": "麻烦上门前电话联系",
    "seller_memo": "加急发货",
    "promotions": [
      {
        "id": "mjs_500_50",
        "name": "满500减50",
        "discount_fee": 50.00,
        "type": "FULL_REDUCTION"
      }
    ],
    "items": [
      {
        "title": "经典款运动鞋",
        "price": 339.00,
        "quantity": 1,
        "total_fee": 339.00,
        "payment": 339.00,
        "sku_properties": "颜色:白色;尺码:42码",
        "outer_iid": "SPU_2026_001",
        "outer_sku_id": "SPU_2026_001_WH_42"
      },
      {
        "title": "轻薄休闲裤",
        "price": 199.00,
        "quantity": 1,
        "total_fee": 199.00,
        "payment": 199.00,
        "sku_properties": "颜色:深灰色;尺码:32码",
        "outer_iid": "SPU_2026_002",
        "outer_sku_id": "SPU_2026_002_GY_32"
      }
    ],
    "channel_ext": {
      "ouid": "AAHk5d123"
    }
  }
}
```
***
## 四、行为事件 payload 标准结构
### 4.1 顶层 TransactionEvent 结构（行为）
```json
{
  "event_id": "string (系统生成)",
  "member_id": "string (One-ID 匹配后填充)",
  "event_type": "BEHAVIOR",
  "channel": "TMALL | JD | WECHAT | APP | POS",
  "event_time": "ISO 8601 UTC (行为发生时间)",
  "idempotent_key": "string (例如 BEHAVIOR_SIGN_IN_20260608_M123)",
  "payload": { ... }
}
```
### 4.2 payload 字段定义
| 字段名              | 类型       | 必填 | 说明                            |
| ---------------- | -------- | -- | ----------------------------- |
| `behavior_code`  | string   | ✅  | 行为代码，须在 `behavior_code` 表中预定义 |
| `behavior_time`  | ISO 8601 | ✅  | 行为发生时间（与顶层 event\_time 一致）    |
| `source`         | string   | ❌  | 触发来源（小程序/H5/APP）              |
| `page_url`       | string   | ❌  | 页面URL                         |
| `referrer`       | string   | ❌  | 来源页面                          |
| `device_id`      | string   | ❌  | 设备ID                          |
| `ip_address`     | string   | ❌  | 客户端IP（脱敏）                     |
| `user_agent`     | string   | ❌  | UA 信息（截断至200字符）               |
| `ext_attributes` | object   | ❌  | 行为扩展属性                        |
### 4.3 行为代码表（`behavior_code`）
```sql
CREATE TABLE behavior_code (
    id BIGSERIAL PRIMARY KEY,
    program_code VARCHAR(32) NOT NULL,
    behavior_code VARCHAR(64) NOT NULL,
    behavior_name VARCHAR(128) NOT NULL,
    description TEXT,
    default_points INT,
    freq_limit_type VARCHAR(20) DEFAULT 'NONE',   -- NONE/DAILY/WEEKLY/MONTHLY/CUSTOM_SECONDS
    freq_limit_value INT,
    freq_limit_seconds INT,
    applicable_tiers JSONB,
    status VARCHAR(20) DEFAULT 'ENABLED',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    UNIQUE (program_code, behavior_code)
);
```
### 4.4 行为事件示例
```json
{
  "event_type": "BEHAVIOR",
  "channel": "APP",
  "event_time": "2026-06-08T08:30:00Z",
  "idempotent_key": "SIGN_IN_M123_20260608",
  "payload": {
    "behavior_code": "SIGN_IN",
    "behavior_time": "2026-06-08T08:30:00Z",
    "source": "app",
    "ext_attributes": {
      "sign_in_streak": 5
    }
  }
}
```
***
## 五、统一事件时间线展示
为方便客服查看会员完整轨迹，在会员详情页新增「活动时间线」Tab，将订单事件、行为事件按时间倒序混合展示。积分变动由独立的积分流水表管理，不在此处关联。
### 5.1 统一事件 API
text
```
GET /api/members/{memberId}/timeline
```
**请求参数**：\
`startTime`、`endTime`（可选）、`eventTypes`（ORDER,BEHAVIOR，默认全部）、`page`、`size`
**响应示例**：
```json
{
  "code": "SUCCESS",
  "data": {
    "total": 128,
    "items": [
      {
        "id": "evt_001",
        "event_type": "BEHAVIOR",
        "event_time": "2026-06-08T08:30:00Z",
        "summary": {
          "behavior_code": "SIGN_IN",
          "behavior_name": "每日签到",
          "source": "APP",
          "ext_attributes": {
            "sign_in_streak": 5
          }
        }
      },
      {
        "id": "evt_002",
        "event_type": "ORDER",
        "event_time": "2026-06-07T10:23:15Z",
        "summary": {
          "order_id": "TM2026060700001",
          "total_amount": 699.00,
          "status": "TRADE_FINISHED",
          "created_at": "2026-06-07T10:23:15Z",
          "paid_at": "2026-06-07T10:25:32Z"
        }
      }
    ]
  }
}
```
### 5.2 查询 SQL（仅事件本身，不关联积分）
```sql
SELECT 
    id,
    event_type,
    event_time,
    order_id,
    total_amount,
    order_status,
    paid_at,
    behavior_code,
    payload   -- 完整 JSONB，用于详情展示
FROM event_inbox
WHERE member_id = ?
ORDER BY event_time DESC
LIMIT ? OFFSET ?;
```
***
## 六、存储原则与前端展示
| 数据类型                      | 存储位置            | 用途               |
| ------------------------- | --------------- | ---------------- |
| 订单号、实付金额、订单状态、下单时间、付款时间等  | 独立列             | 交易流水列表直接展示、排序、过滤 |
| 行为代码                      | 独立列             | 行为列表展示、过滤        |
| 促销明细、完整商品明细、渠道扩展字段、行为扩展属性 | JSONB `payload` | 详情页点击“查看详情”时读取   |
**前端交易流水表格列**（订单事件）：
| 列名     | 数据来源                |
| ------ | ------------------- |
| 订单号    | `order_id`          |
| 实付金额   | `total_amount`      |
| 订单状态   | `order_status`      |
| 下单时间   | `event_time`        |
| 付款时间   | `paid_at`           |
| 操作（详情） | 读取 `payload` 展示完整信息 |
**行为时间线表格列**：
| 列名     | 数据来源                       |
| ------ | -------------------------- |
| 行为类型   | `behavior_code`（配合字典表显示名称） |
| 发生时间   | `event_time`               |
| 操作（详情） | 读取 `payload` 展示扩展属性        |
***
## 七、更新主设计文档的位置
| 内容                        | 目标章节                |
| ------------------------- | ------------------- |
| `event_inbox` 表扩展 DDL 与索引 | 第十一章 数据库物理模型        |
| 订单事件 payload 结构           | 附录 A.3 交易相关（新增子节）   |
| 行为事件 payload 结构           | 附录 A.4 行为事件（新增）     |
| 行为代码管理                    | 第八章 系统设置 或 第六章 规则引擎 |
| 事件时间线统一展示                 | 第八章 会员详情页设计         |
| API 定义（统一时间线）             | 第十章 API 规范          |
***
**文档结束**

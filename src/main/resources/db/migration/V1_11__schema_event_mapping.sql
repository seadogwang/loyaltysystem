-- V1_11: Schema-Event 映射完善
-- 新增 TRANSACTION 和 OrderItem schema，确保所有事件类型都有对应 schema 定义

-- 1. TRANSACTION: Base schema for all transaction events
INSERT INTO schema_version (program_code, schema_type, schema_code, version, status, schema_json)
SELECT 'PROG001', 'TRANSACTION', 'TRANSACTION', 1, 'PUBLISHED', '{
  "type": "object",
  "title": "Transaction Event (Base)",
  "description": "Common columns of transaction_event table, baseline for all event types",
  "properties": {
    "event_id":       {"type":"string","title":"Event ID"},
    "program_code":   {"type":"string","title":"Program Code"},
    "member_id":      {"type":"string","title":"Member ID"},
    "event_type":     {"type":"string","title":"Event Type",
                       "enum":["ORDER_PAID","ORDER_REFUND_FULL","ORDER_REFUND_PARTIAL",
                               "CHECK_IN","SHARE","REGISTER","SIGN_IN",
                               "REDEMPTION","ADJUSTMENT","MERGE"]},
    "event_time":     {"type":"string","title":"Event Time","format":"date-time"},
    "channel":        {"type":"string","title":"Channel",
                       "enum":["TMALL","JD","DOUYIN","WECHAT_MINI"]},
    "source_event_id":{"type":"string","title":"Source Event ID"},
    "idempotency_key":{"type":"string","title":"Idempotency Key"},
    "trade_time":     {"type":"string","title":"Trade Time","format":"date-time"},
    "pay_time":       {"type":"string","title":"Pay Time","format":"date-time"},
    "order_amount":   {"type":"number","title":"Order Amount"},
    "trade_status":   {"type":"string","title":"Trade Status"},
    "processing_status":{"type":"string","title":"Processing Status"},
    "schema_version": {"type":"string","title":"Schema Version"}
  },
  "required":["event_id","program_code","event_type","event_time"]
}'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM schema_version WHERE schema_code='TRANSACTION');

-- 2. OrderItem: Order line item schema (nested child entity)
INSERT INTO schema_version (program_code, schema_type, schema_code, version, status, schema_json)
SELECT 'PROG001', 'OrderItem', 'OrderItem', 1, 'PUBLISHED', '{
  "type": "object",
  "title": "Order Line Item",
  "description": "Order line item, nested in ORDER events items array, written to custom_entity_data table",
  "properties": {
    "sku":          {"type":"string","title":"SKU Code"},
    "category_id":  {"type":"string","title":"Category ID"},
    "price":        {"type":"number","title":"Unit Price"},
    "quantity":     {"type":"number","title":"Quantity"},
    "product_name": {"type":"string","title":"Product Name"}
  },
  "required":["sku"]
}'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM schema_version WHERE schema_code='OrderItem');

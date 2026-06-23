-- V1_8: Rule Engine Business Entity Schemas — aligned with transaction_event + member table structure
-- Schema describes fields accessible in DRL rules via $event.getPayload*() / $member.getExt*()

-- Clean old data (only delete records with version < 99, keep current ones)
DELETE FROM schema_version WHERE schema_code IN ('TRANSACTION_EVENT','TRANSACTION','ORDER','BEHAVIOR','MEMBER')
AND version < 99 AND version < (SELECT COALESCE(MAX(version),0) FROM schema_version sv2 WHERE sv2.schema_code = schema_version.schema_code);

-- 1. ORDER: Standardized payload for order payment events
--    Corresponds to transaction_event columns + fields standardized by EventInboxProcessor
--    Source: TMALL/JD/DOUYIN/WECHAT channel webhooks
INSERT INTO schema_version (program_code, schema_type, schema_code, version, status, schema_json)
SELECT 'PROG001', 'ORDER', 'ORDER', 1, 'PUBLISHED', '{
  "type": "object",
  "title": "Order Event Payload",
  "properties": {
    "order_id":      {"type":"string","title":"Order ID"},
    "order_amount":  {"type":"number","title":"Order Amount"},
    "total_amount":  {"type":"number","title":"Total Amount"},
    "pay_time":      {"type":"string","title":"Pay Time"},
    "trade_time":    {"type":"string","title":"Trade Time"},
    "trade_status":  {"type":"string","title":"Trade Status",
                      "enum":["WAIT_BUYER_PAY","WAIT_SELLER_SEND_GOODS","WAIT_BUYER_CONFIRM_GOODS","TRADE_FINISHED","TRADE_CLOSED"]},
    "channel":       {"type":"string","title":"Channel",
                      "enum":["TMALL","JD","DOUYIN","WECHAT_MINI"]},
    "member_id":     {"type":"string","title":"Member ID"},
    "eventType":     {"type":"string","title":"Event Type","enum":["ORDER_PAID"]},
    "buyer_nick":    {"type":"string","title":"Buyer Nick"},
    "item_count":    {"type":"number","title":"Item Count"},
    "item_category": {"type":"string","title":"Item Category"},
    "remark":        {"type":"string","title":"Remark"}
  },
  "required":["member_id","eventType"]
}'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM schema_version WHERE schema_code='ORDER' AND status='PUBLISHED');

-- 2. BEHAVIOR: Standardized payload for behavior events
--    Corresponds to transaction_event table + CHECK_IN/SHARE/REGISTER etc.
INSERT INTO schema_version (program_code, schema_type, schema_code, version, status, schema_json)
SELECT 'PROG001', 'BEHAVIOR', 'BEHAVIOR', 1, 'PUBLISHED', '{
  "type": "object",
  "title": "Behavior Event Payload",
  "properties": {
    "behavior_code": {"type":"string","title":"Behavior Code"},
    "behavior_name": {"type":"string","title":"Behavior Name"},
    "channel":       {"type":"string","title":"Channel",
                      "enum":["TMALL","JD","DOUYIN","WECHAT_MINI"]},
    "member_id":     {"type":"string","title":"Member ID"},
    "eventType":     {"type":"string","title":"Event Type",
                      "enum":["CHECK_IN","SHARE","REGISTER","SIGN_IN"]},
    "timestamp":     {"type":"number","title":"Unix Timestamp"},
    "source":        {"type":"string","title":"Source"},
    "remark":        {"type":"string","title":"Remark"}
  },
  "required":["member_id","eventType"]
}'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM schema_version WHERE schema_code='BEHAVIOR' AND status='PUBLISHED');

-- 3. MEMBER: Member extended attributes
--    Corresponds to member.ext_attributes JSONB field
INSERT INTO schema_version (program_code, schema_type, schema_code, version, status, schema_json)
SELECT 'PROG001', 'MEMBER', 'MEMBER', 1, 'PUBLISHED', '{
  "type": "object",
  "title": "Member Ext Attributes",
  "properties": {
    "tier_code":      {"type":"string","title":"Tier",
                       "enum":["BASE","SILVER","GOLD","PLATINUM"]},
    "status":         {"type":"string","title":"Status",
                       "enum":["ENROLLED","SUSPENDED","MERGED","DEACTIVATED"]},
    "phone":          {"type":"string","title":"Phone"},
    "email":          {"type":"string","title":"Email"},
    "birthday":       {"type":"string","title":"Birthday"},
    "gender":         {"type":"string","title":"Gender",
                       "enum":["MALE","FEMALE","UNKNOWN"]},
    "city":           {"type":"string","title":"City"},
    "shoe_size":      {"type":"number","title":"Shoe Size"},
    "pet_name":       {"type":"string","title":"Pet Name"},
    "age":            {"type":"number","title":"Age"},
    "level":          {"type":"number","title":"Level"},
    "total_orders":   {"type":"number","title":"Total Orders"},
    "total_spent":    {"type":"number","title":"Total Spent"}
  }
}'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM schema_version WHERE schema_code='MEMBER' AND status='PUBLISHED');

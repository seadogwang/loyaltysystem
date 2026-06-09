-- V1_8: 规则引擎所需的业务实体 Schema 定义
-- Schema 描述的是 DRL 规则中可通过 $event.getPayload*() / $member.getExt*() 访问的字段
-- 与 transaction_event 表 + EventInboxProcessor 标准化后的 payload 结构一致

-- 1. TRANSACTION_EVENT: 标准化事件 payload (EventFact.payload)
--    对应 transaction_event 表 + EventInboxProcessor.extractMemberId/extractEventType 标准化的字段
INSERT INTO schema_version (program_code, schema_type, schema_code, version, status, schema_json)
VALUES
('PROG001', 'TRANSACTION_EVENT', 'TRANSACTION_EVENT', 2, 'PUBLISHED', '{
  "type": "object",
  "title": "Standardized Event Payload",
  "properties": {
    "member_id":      {"type": "string", "title": "Member ID"},
    "eventType":      {"type": "string", "title": "Event Type",
                       "enum": ["ORDER_PAID","CHECK_IN","SHARE","REFUND","REGISTER",
                                "SIGN_IN","ENROLLMENT","ORDER_REFUND_FULL","ORDER_REFUND_PARTIAL",
                                "REDEMPTION","REDEMPTION_CANCEL","ADJUSTMENT","MERGE","TIER_CHANGE"]},
    "event_type":     {"type": "string", "title": "Event Type (alt)"},
    "channel":        {"type": "string", "title": "Channel",
                       "enum": ["TMALL","JD","DOUYIN","WECHAT_MINI"]},

    "-- ORDER fields": {"type": "string", "title": "--- Order Fields ---"},
    "order_amount":   {"type": "number", "title": "Order Amount (yuan)"},
    "total_amount":   {"type": "number", "title": "Total Amount (yuan)"},
    "order_id":       {"type": "string", "title": "Order ID"},
    "pay_time":       {"type": "string", "title": "Pay Time", "format": "date-time"},
    "trade_time":     {"type": "string", "title": "Trade Time", "format": "date-time"},
    "trade_status":   {"type": "string", "title": "Trade Status",
                       "enum": ["WAIT_BUYER_PAY","WAIT_SELLER_SEND_GOODS",
                                "WAIT_BUYER_CONFIRM_GOODS","TRADE_FINISHED","TRADE_CLOSED"]},
    "buyer_nick":     {"type": "string", "title": "Buyer Nick (Tmall)"},
    "item_count":     {"type": "number", "title": "Item Count"},

    "-- BEHAVIOR fields": {"type": "string", "title": "--- Behavior Fields ---"},
    "behavior_code":  {"type": "string", "title": "Behavior Code"},
    "behavior_name":  {"type": "string", "title": "Behavior Name"},

    "-- REFUND fields": {"type": "string", "title": "--- Refund Fields ---"},
    "refund_amount":  {"type": "number", "title": "Refund Amount (yuan)"},
    "refund_id":      {"type": "string", "title": "Refund ID"},
    "related_order_id":{"type": "string", "title": "Related Order ID"},

    "-- Common fields": {"type": "string", "title": "--- Common ---"},
    "timestamp":      {"type": "number", "title": "Unix Timestamp"},
    "event_time":     {"type": "string", "title": "Event Time", "format": "date-time"},
    "source":         {"type": "string", "title": "Source"},
    "remark":         {"type": "string", "title": "Remark"}
  },
  "required": ["member_id","eventType"]
}')
ON CONFLICT (program_code, schema_code, version) DO UPDATE SET
  schema_json = EXCLUDED.schema_json,
  status = 'PUBLISHED';

-- 2. MEMBER: 会员扩展属性 (MemberFact.extAttributes)
--    对应 member 表的 ext_attributes JSONB + DRL 中 $member.getExt*() 可访问的字段
INSERT INTO schema_version (program_code, schema_type, schema_code, version, status, schema_json)
VALUES
('PROG001', 'MEMBER', 'MEMBER', 2, 'PUBLISHED', '{
  "type": "object",
  "title": "Member Ext Attributes",
  "properties": {
    "tier_code":      {"type": "string", "title": "Tier",
                       "enum": ["BASE","SILVER","GOLD","PLATINUM"]},
    "status":         {"type": "string", "title": "Member Status",
                       "enum": ["ENROLLED","SUSPENDED","MERGED","DEACTIVATED"]},
    "phone":          {"type": "string", "title": "Phone"},
    "email":          {"type": "string", "title": "Email"},
    "birthday":       {"type": "string", "title": "Birthday", "format": "date"},
    "gender":         {"type": "string", "title": "Gender",
                       "enum": ["MALE","FEMALE","UNKNOWN"]},
    "city":           {"type": "string", "title": "City"},
    "shoe_size":      {"type": "number", "title": "Shoe Size"},
    "pet_name":       {"type": "string", "title": "Pet Name"},
    "age":            {"type": "number", "title": "Age"},
    "level":          {"type": "number", "title": "Level"},
    "registration_date": {"type": "string", "title": "Registration Date", "format": "date"},
    "last_login_time":   {"type": "string", "title": "Last Login", "format": "date-time"},
    "total_orders":   {"type": "number", "title": "Total Orders"},
    "total_spent":    {"type": "number", "title": "Total Spent (yuan)"}
  }
}')
ON CONFLICT (program_code, schema_code, version) DO UPDATE SET
  schema_json = EXCLUDED.schema_json,
  status = 'PUBLISHED';
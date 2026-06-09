-- V1_8: 规则引擎所需的业务实体 Schema 定义
-- 为 MEMBER 和 TRANSACTION 实体创建初始 JSON Schema

INSERT INTO schema_version (program_code, schema_type, schema_code, version, status, schema_json)
VALUES
('PROG001', 'TRANSACTION', 'TRANSACTION', 1, 'PUBLISHED', '{
  "type": "object",
  "properties": {
    "event_id": {"type": "string", "title": "Event ID"},
    "event_type": {"type": "string", "title": "Event Type", "x-component": "Select", "enum": ["ORDER_PAID","SIGN_IN","ENROLLMENT","ORDER_REFUND_FULL","ORDER_REFUND_PARTIAL","REDEMPTION","REDEMPTION_CANCEL","ADJUSTMENT","MERGE","TIER_CHANGE"]},
    "channel": {"type": "string", "title": "Channel", "x-component": "Select", "enum": ["TMALL","JD","DOUYIN","WECHAT_MINI"]},
    "order_amount": {"type": "number", "title": "Order Amount", "x-component": "NumberPicker"},
    "pay_time": {"type": "string", "title": "Pay Time", "x-component": "DatePicker"},
    "trade_time": {"type": "string", "title": "Trade Time", "x-component": "DatePicker"},
    "trade_status": {"type": "string", "title": "Trade Status", "enum": ["WAIT_BUYER_PAY","WAIT_SELLER_SEND_GOODS","WAIT_BUYER_CONFIRM_GOODS","TRADE_FINISHED","TRADE_CLOSED"]},
    "behavior_code": {"type": "string", "title": "Behavior Code"},
    "member_id": {"type": "number", "title": "Member ID"}
  },
  "required": ["event_id","event_type"]
}')
ON CONFLICT DO NOTHING;

INSERT INTO schema_version (program_code, schema_type, schema_code, version, status, schema_json)
VALUES
('PROG001', 'MEMBER', 'MEMBER', 1, 'PUBLISHED', '{
  "type": "object",
  "properties": {
    "member_id": {"type": "number", "title": "Member ID"},
    "tier_code": {"type": "string", "title": "Tier", "enum": ["BASE","SILVER","GOLD","PLATINUM"]},
    "status": {"type": "string", "title": "Status", "enum": ["ENROLLED","SUSPENDED","MERGED","DEACTIVATED"]},
    "phone": {"type": "string", "title": "Phone"},
    "email": {"type": "string", "title": "Email"},
    "birthday": {"type": "string", "title": "Birthday", "x-component": "DatePicker"},
    "gender": {"type": "string", "title": "Gender", "enum": ["MALE","FEMALE","UNKNOWN"]},
    "city": {"type": "string", "title": "City"},
    "shoe_size": {"type": "number", "title": "Shoe Size"},
    "pet_name": {"type": "string", "title": "Pet Name"},
    "age": {"type": "number", "title": "Age"}
  }
}')
ON CONFLICT DO NOTHING;
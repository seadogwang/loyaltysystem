-- 为测试会员 318969221033889792 新增 20 条订单积分流水
-- 按照设计文档 v7.1 标准化订单结构（含 omid/ouid）

-- 先清理旧测试数据
DELETE FROM account_transaction WHERE member_id=318969221033889792 AND operation_key LIKE 'test-%';
DELETE FROM redemption_allocation WHERE program_code='PROG001';

-- 20条订单积分流水（天猫订单）
-- 积分规则: 实付金额 * 1 = 积分 (total_amount = 积分)
INSERT INTO account_transaction (account_id, program_code, member_id, account_type, transaction_type, operation_key, amount, remaining_amount, expires_at, status, created_at)
VALUES
  -- 2026-06-07 今天的订单
  (1,'PROG001',318969221033889792,'REWARD','ACCRUAL','test-OM-TM2026060700001',699.00,699.00,'2027-06-07','ACTIVE','2026-06-07 10:23:15'),
  (1,'PROG001',318969221033889792,'REWARD','ACCRUAL','test-OM-TM2026060700002',1280.00,1280.00,'2027-06-07','ACTIVE','2026-06-07 11:05:22'),
  (1,'PROG001',318969221033889792,'REWARD','ACCRUAL','test-OM-TM2026060700003',349.00,349.00,'2027-06-07','ACTIVE','2026-06-07 14:30:11'),

  -- 2026-06-06 昨天的订单
  (1,'PROG001',318969221033889792,'REWARD','ACCRUAL','test-OM-TM2026060600004',2560.00,2560.00,'2027-06-06','ACTIVE','2026-06-06 09:15:33'),
  (1,'PROG001',318969221033889792,'REWARD','ACCRUAL','test-OM-TM2026060600005',168.00,168.00,'2027-06-06','ACTIVE','2026-06-06 13:45:18'),
  (1,'PROG001',318969221033889792,'REWARD','ACCRUAL','test-OM-TM2026060600006',499.00,499.00,'2027-06-06','ACTIVE','2026-06-06 20:12:05'),

  -- 2026-06-05~06-01
  (1,'PROG001',318969221033889792,'REWARD','ACCRUAL','test-OM-TM2026060500007',890.00,890.00,'2027-06-05','ACTIVE','2026-06-05 08:30:00'),
  (1,'PROG001',318969221033889792,'REWARD','ACCRUAL','test-OM-TM2026060500008',2100.00,2100.00,'2027-06-05','ACTIVE','2026-06-05 16:22:41'),
  (1,'PROG001',318969221033889792,'REWARD','ACCRUAL','test-OM-TM2026060400009',365.00,365.00,'2027-06-04','ACTIVE','2026-06-04 12:18:09'),
  (1,'PROG001',318969221033889792,'REWARD','ACCRUAL','test-OM-TM2026060300010',520.00,520.00,'2027-06-03','ACTIVE','2026-06-03 18:55:27'),
  (1,'PROG001',318969221033889792,'REWARD','ACCRUAL','test-OM-TM2026060200011',1420.00,1420.00,'2027-06-02','ACTIVE','2026-06-02 10:40:12'),
  (1,'PROG001',318969221033889792,'REWARD','ACCRUAL','test-OM-TM2026060100012',780.00,780.00,'2027-06-01','ACTIVE','2026-06-01 14:15:33'),

  -- 2026-05 大额订单
  (1,'PROG001',318969221033889792,'REWARD','ACCRUAL','test-OM-TM2026053000013',3999.00,3999.00,'2027-05-30','ACTIVE','2026-05-30 09:22:18'),
  (1,'PROG001',318969221033889792,'REWARD','ACCRUAL','test-OM-TM2026052500014',588.00,588.00,'2027-05-25','ACTIVE','2026-05-25 11:45:09'),
  (1,'PROG001',318969221033889792,'REWARD','ACCRUAL','test-OM-TM2026052000015',1650.00,0,'2027-05-20','EXHAUSTED','2026-05-20 16:30:44'),
  (1,'PROG001',318969221033889792,'REWARD','ACCRUAL','test-OM-TM2026051500016',920.00,0,'2027-05-15','EXHAUSTED','2026-05-15 10:05:21'),

  -- 2026-04
  (1,'PROG001',318969221033889792,'REWARD','ACCRUAL','test-OM-TM2026042800017',2350.00,2350.00,'2027-04-28','ACTIVE','2026-04-28 08:12:33'),
  (1,'PROG001',318969221033889792,'REWARD','ACCRUAL','test-OM-TM2026041500018',430.00,430.00,'2027-04-15','ACTIVE','2026-04-15 20:33:18'),

  -- 兑换记录（消耗积分）
  (1,'PROG001',318969221033889792,'REWARD','REDEMPTION','test-OM-REDEMPTION-TM2026060700001',-500.00,-500.00,null,'ACTIVE','2026-06-07 12:00:00'),
  (1,'PROG001',318969221033889792,'REWARD','REDEMPTION','test-OM-REDEMPTION-TM2026060500001',-300.00,-300.00,null,'ACTIVE','2026-06-05 15:00:00');

-- 更新积分账户总累计
UPDATE member_account
SET total_accrued = total_accrued + 21685.00, total_redeemed = total_redeemed + 800.00
WHERE program_code='PROG001' AND member_id=318969221033889792 AND account_type='REWARD';

-- 补充 TransactionEvent 测试数据（订单详情 payload）
INSERT INTO transaction_event (program_code, event_id, member_id, event_type, channel, event_time, idempotent_key, payload, created_at)
VALUES
  ('PROG001','evt-test-001',318969221033889792,'ORDER','TMALL','2026-06-07 10:23:15','TM2026060700001',
   '{"order_id":"TM2026060700001","total_amount":699.00,"original_amount":759.00,"discount_amount":50.00,"post_fee":10.00,"status":"TRADE_FINISHED","created_at":"2026-06-07T10:23:15Z","paid_at":"2026-06-07T10:25:32Z","channel_ext":{"ouid":"AAHk5d123","omid":"omid_brand_test"}}'::jsonb,
   NOW()),
  ('PROG001','evt-test-002',318969221033889792,'ORDER','TMALL','2026-06-07 11:05:22','TM2026060700002',
   '{"order_id":"TM2026060700002","total_amount":1280.00,"discount_amount":0,"status":"TRADE_FINISHED","created_at":"2026-06-07T11:05:22Z","paid_at":"2026-06-07T11:06:10Z","items":[{"title":"经典款运动鞋","price":640.00,"quantity":2,"total_fee":1280.00}],"channel_ext":{"ouid":"AAHk5d123","omid":"omid_brand_test"}}'::jsonb,
   NOW()),
  ('PROG001','evt-test-003',318969221033889792,'ORDER','TMALL','2026-06-06 09:15:33','TM2026060600004',
   '{"order_id":"TM2026060600004","total_amount":2560.00,"discount_amount":200.00,"status":"TRADE_FINISHED","created_at":"2026-06-06T09:15:33Z","paid_at":"2026-06-06T09:15:58Z","items":[{"title":"4K显示器","price":2560.00,"quantity":1,"total_fee":2560.00}],"promotions":[{"name":"满2000减200","discount_fee":200.00,"type":"FULL_REDUCTION"}],"channel_ext":{"ouid":"AAHk5d123","omid":"omid_brand_test"}}'::jsonb,
   NOW()),
  ('PROG001','evt-test-004',318969221033889792,'ORDER','TMALL','2026-06-05 16:22:41','TM2026060500008',
   '{"order_id":"TM2026060500008","total_amount":2100.00,"status":"TRADE_FINISHED","created_at":"2026-06-05T16:22:41Z","paid_at":"2026-06-05T16:23:15Z","items":[{"title":"智能手表","price":1050.00,"quantity":2,"total_fee":2100.00}],"channel_ext":{"ouid":"AAHk5d123","omid":"omid_brand_test"}}'::jsonb,
   NOW()),
  ('PROG001','evt-test-005',318969221033889792,'ORDER','TMALL','2026-05-30 09:22:18','TM2026053000013',
   '{"order_id":"TM2026053000013","total_amount":3999.00,"status":"TRADE_FINISHED","created_at":"2026-05-30T09:22:18Z","paid_at":"2026-05-30T09:22:55Z","items":[{"title":"笔记本电脑","price":3999.00,"quantity":1,"total_fee":3999.00}],"channel_ext":{"ouid":"AAHk5d123","omid":"omid_brand_test"}}'::jsonb,
   NOW())
ON CONFLICT DO NOTHING;
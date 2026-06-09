-- 修复后的测试数据

-- 1. 会员 (已存在一个，更新更多)
DELETE FROM member WHERE member_id IN (8821,8822,8823,8824,8825);
UPDATE member SET tier_code='GOLD', status='ENROLLED', ext_attributes='{"pet_name":"旺财","shoe_size":42,"mobile":"13812345678"}' WHERE program_code='PROG001' AND member_id=318969221033889792;

-- 2. 积分流水 (account_transaction 需要 account_id)
INSERT INTO account_transaction (account_id, program_code, member_id, account_type, transaction_type, amount, remaining_amount, expires_at, status, operation_key, created_at)
VALUES
  (1,'PROG001',318969221033889792,'REWARD','ACCRUAL',1200,200,'2026-12-01','ACTIVE','tx-init-001','2025-12-01 10:00:00'),
  (1,'PROG001',318969221033889792,'REWARD','ACCRUAL',800,800,'2026-06-01','ACTIVE','tx-init-002','2026-01-15 14:00:00'),
  (1,'PROG001',318969221033889792,'REWARD','ACCRUAL',500,280,'2026-09-01','ACTIVE','tx-init-003','2026-03-10 09:00:00'),
  (1,'PROG001',318969221033889792,'REWARD','REDEMPTION',-500,-500,null,'ACTIVE','tx-init-004','2026-04-01 11:00:00'),
  (1,'PROG001',318969221033889792,'REWARD','REDEMPTION',-300,-300,null,'ACTIVE','tx-init-005','2026-04-15 15:00:00'),
  (1,'PROG001',318969221033889792,'REWARD','REDEMPTION',-220,-220,null,'ACTIVE','tx-init-006','2026-05-20 10:00:00');

-- 3. 等级变更日志 (from_tier, to_tier, event_id)
INSERT INTO tier_change_log (program_code, member_id, from_tier, to_tier, change_reason, event_id, changed_at)
VALUES
  ('PROG001',318969221033889792,'BASE','SILVER','UPGRADE','evt-up-01','2025-08-20 10:00:00'),
  ('PROG001',318969221033889792,'SILVER','GOLD','UPGRADE','evt-up-02','2026-02-15 12:00:00');

-- 4. 渠道绑定
DELETE FROM member_unique_key WHERE program_code='PROG001';
INSERT INTO member_unique_key (program_code, key_combination, key_value, member_id)
VALUES
  ('PROG001','MOBILE','138****1234',318969221033889792),
  ('PROG001','TMALL_OUID','tb_ouid_test',318969221033889792),
  ('PROG001','WECHAT_OPENID','oxc_test_openid',318969221033889792);

-- 5. 积分类型 (3种默认)
DELETE FROM point_type_definition WHERE program_code='PROG001';
INSERT INTO point_type_definition (program_code, type_code, type_name, is_redeemable, is_tier_calc, is_transferable, allow_negative, expiry_days, expiry_mode, expiry_value, is_visible, overdraft_limit, credit_limit, status, created_at)
VALUES
  ('PROG001','REWARD','消费积分',true,false,true,false,365,'CALENDAR_YEARS',1,true,0,0,'ACTIVE',NOW()),
  ('PROG001','TIER','等级成长值',false,true,false,false,0,'FIXED_DAYS',0,true,0,0,'ACTIVE',NOW()),
  ('PROG001','CREDIT','授信积分',true,false,false,true,0,'FIXED_DAYS',0,true,0,5000,'ACTIVE',NOW());

-- 6. 等级定义
DELETE FROM tier_definition WHERE program_code='PROG001';
INSERT INTO tier_definition (program_code, tier_code, tier_name, sequence, upgrade_criteria, downgrade_criteria, created_at)
VALUES
  ('PROG001','BASE','普通会员',1,'{"minPoints":0,"maxPoints":10000}','{}',NOW()),
  ('PROG001','SILVER','银卡会员',2,'{"minPoints":10000,"maxPoints":50000}','{}',NOW()),
  ('PROG001','GOLD','金卡会员',3,'{"minPoints":50000,"maxPoints":100000}','{}',NOW()),
  ('PROG001','PLATINUM','铂金会员',4,'{"minPoints":100000,"maxPoints":99999999}','{}',NOW());
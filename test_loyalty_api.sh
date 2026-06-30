#!/bin/bash
# ============================================================
# Loyalty System Comprehensive API Test Suite v4
# Fixed: Point types (REWARD, TIER), Python reference, freeze/unfreeze
# ============================================================

BASE="http://localhost:8081"
PROG="PROG001"
PASS=0
FAIL=0
TOKEN=""
IDEM="idem-$(date +%s)"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Find python
PYTHON=$(which python 2>/dev/null || which python3 2>/dev/null || echo "")

api() {
  local method=$1 path=$2 data_file=$3
  local headers=(-H "X-Program-Code: $PROG" -H "Content-Type: application/json")
  if [ -n "$TOKEN" ]; then
    headers+=(-H "Authorization: Bearer $TOKEN")
  fi
  if [ "$method" = "POST" ] || [ "$method" = "PUT" ]; then
    headers+=(-H "X-Idempotency-Key: $IDEM-$RANDOM")
  fi
  if [ -n "$data_file" ] && [ -f "$data_file" ]; then
    curl -s -X "$method" "$BASE$path" "${headers[@]}" -d "@$data_file"
  else
    curl -s -X "$method" "$BASE$path" "${headers[@]}"
  fi
}

check() {
  local label=$1 result=$2
  local code=$(echo "$result" | grep -o '"code":"[^"]*"' | head -1 | cut -d'"' -f4)
  if [ "$code" = "SUCCESS" ]; then
    echo -e "${GREEN}[PASS]${NC} $label"
    PASS=$((PASS + 1))
  else
    local msg=$(echo "$result" | grep -o '"message":"[^"]*"' | head -1 | cut -d'"' -f4)
    echo -e "${RED}[FAIL]${NC} $label (code=$code, msg=$msg)"
    FAIL=$((FAIL + 1))
  fi
}

check_contains() {
  local label=$1 result=$2 expected=$3
  if echo "$result" | grep -q "$expected"; then
    echo -e "${GREEN}[PASS]${NC} $label"
    PASS=$((PASS + 1))
  else
    echo -e "${RED}[FAIL]${NC} $label (expected '$expected' not found)"
    FAIL=$((FAIL + 1))
  fi
}

info() {
  echo -e "${YELLOW}[INFO]${NC} $1: $(echo "$2" | head -c 300)"
}

json() {
  local file=$1
  cat > "$file"
}

echo "============================================"
echo "  Loyalty System Comprehensive API Test v4"
echo "  $(date)"
echo "  Python: ${PYTHON:-not found}"
echo "============================================"
echo ""

# ============================================================
# SECTION 1: Authentication
# ============================================================
echo "--- Section 1: Authentication ---"

json /tmp/login.json << 'EOF'
{"username":"superadmin","password":"admin123","programCode":"PROG001"}
EOF
result=$(api POST "/api/auth/login" /tmp/login.json)
TOKEN=$(echo "$result" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
check "L-AUTH-01: Login superadmin" "$result"
echo ""

# ============================================================
# SECTION 2: Member Management
# ============================================================
echo "--- Section 2: Member Management ---"

MID1=$((88001001 + RANDOM % 1000))
PHONE1="139$(printf '%08d' $RANDOM)"
echo "  Test Member ID: $MID1, Phone: $PHONE1"

# L-M-001: Create member
json /tmp/m1.json << EOF
{"member_id":$MID1,"tier_code":"BASE","ext_attributes":{"mobile":"$PHONE1","name":"测试会员001"}}
EOF
result=$(api POST "/api/members" /tmp/m1.json)
check "L-M-001: Create member (ID=$MID1)" "$result"

# L-M-002: Duplicate mobile
json /tmp/m2.json << EOF
{"member_id":$((MID1+1)),"tier_code":"BASE","ext_attributes":{"mobile":"$PHONE1"}}
EOF
result=$(api POST "/api/members" /tmp/m2.json)
check_contains "L-M-002: Duplicate mobile rejected" "$result" "ERR_MEMBER_EXISTS"

# L-M-003: Search by mobile
result=$(api GET "/api/members/search?keyword=$PHONE1")
check "L-M-003: Search by mobile" "$result"

# L-M-004: Get by memberId
result=$(api GET "/api/members/$MID1")
check "L-M-004: Get by memberId" "$result"
check_contains "L-M-004b: Has accounts" "$result" "accounts"

# L-M-005: Create without memberId
PHONE2="139$(printf '%08d' $RANDOM)"
json /tmp/m3.json << EOF
{"ext_attributes":{"mobile":"$PHONE2","name":"自动生成会员"}}
EOF
result=$(api POST "/api/members" /tmp/m3.json)
check "L-M-005: Create auto-generated memberId" "$result"

# L-M-006: Update member
json /tmp/m4.json << EOF
{"ext_attributes":{"mobile":"$PHONE1","name":"测试会员001","pet_name":"旺财","age":25}}
EOF
result=$(api PUT "/api/members/$MID1" /tmp/m4.json)
check "L-M-006: Update member ext attributes" "$result"

# L-M-007: Freeze
result=$(api POST "/api/members/$MID1/freeze")
check "L-M-007: Freeze member" "$result"
result=$(api GET "/api/members/$MID1")
check_contains "L-M-007b: Verify frozen" "$result" "SUSPENDED"

# L-M-008: Unfreeze
result=$(api POST "/api/members/$MID1/unfreeze")
check "L-M-008: Unfreeze member" "$result"
result=$(api GET "/api/members/$MID1")
check_contains "L-M-008b: Verify unfrozen" "$result" "ENROLLED"

echo ""

# ============================================================
# SECTION 3: Points Adjustment (using REWARD/TIER)
# ============================================================
echo "--- Section 3: Points Adjustment ---"

# L-P-001: Grant REWARD points
json /tmp/pa1.json << 'EOF'
{"amount":500,"accountType":"REWARD","increase":true}
EOF
result=$(api POST "/api/members/$MID1/points/adjust" /tmp/pa1.json)
check "L-P-001: Grant 500 REWARD points" "$result"

result=$(api GET "/api/members/$MID1")
check_contains "L-P-001b: Verify 500 balance" "$result" '"balance":500'

# L-P-002: Deduct REWARD points
json /tmp/pa2.json << 'EOF'
{"amount":200,"accountType":"REWARD","increase":false}
EOF
result=$(api POST "/api/members/$MID1/points/adjust" /tmp/pa2.json)
check "L-P-002: Deduct 200 REWARD points" "$result"

result=$(api GET "/api/members/$MID1")
check_contains "L-P-002b: Verify 300 balance" "$result" '"balance":300'

# L-P-003: Query transactions
result=$(api GET "/api/members/$MID1/transactions?page=0&size=20")
check "L-P-003: Query transactions" "$result"

# L-P-004: Filter by type
result=$(api GET "/api/members/$MID1/transactions?typeFilter=MANUAL_ADJUST")
check "L-P-004: Filter by MANUAL_ADJUST" "$result"

# L-P-005: Filter by date
result=$(api GET "/api/members/$MID1/transactions?dateFrom=2026-01-01T00:00:00&dateTo=2026-12-31T23:59:59")
check "L-P-005: Filter by date range" "$result"

echo ""

# ============================================================
# SECTION 4: Tier Adjustment
# ============================================================
echo "--- Section 4: Tier Adjustment ---"

json /tmp/t1.json << 'EOF'
{"newTier":"GOLD"}
EOF
result=$(api POST "/api/members/$MID1/tier/adjust" /tmp/t1.json)
check "L-T-001: Adjust tier to GOLD" "$result"
result=$(api GET "/api/members/$MID1")
check_contains "L-T-001b: Verify GOLD tier" "$result" '"tierCode":"GOLD"'

result=$(api GET "/api/members/$MID1/tier-logs")
check "L-T-002: Query tier logs" "$result"

result=$(api GET "/api/members/$MID1/tier-logs?reasonFilter=MANUAL_ADJUSTMENT")
check "L-T-003: Filter by MANUAL_ADJUSTMENT" "$result"

json /tmp/t2.json << 'EOF'
{"newTier":"SILVER"}
EOF
result=$(api POST "/api/members/$MID1/tier/adjust" /tmp/t2.json)
check "L-T-004: Downgrade to SILVER" "$result"
result=$(api GET "/api/members/$MID1")
check_contains "L-T-004b: Verify SILVER tier" "$result" '"tierCode":"SILVER"'

echo ""

# ============================================================
# SECTION 5: Order Events
# ============================================================
echo "--- Section 5: Order Events ---"

ORDER_ID1="TEST-ORDER-$(date +%s)-001"
json /tmp/o1.json << EOF
{"eventType":"ORDER_PAID","order_id":"$ORDER_ID1","memberId":$MID1,"totalAmount":1000.00,"channel":"TMALL","tradeTime":"2026-06-29T10:00:00","payTime":"2026-06-29T10:05:00","items":[{"sku":"SKU001","name":"商品A","price":500,"quantity":1,"category":"服装"},{"sku":"SKU002","name":"商品B","price":500,"quantity":1,"category":"食品"}]}
EOF
result=$(api POST "/api/events/ORDER_CHAIN/$PROG" /tmp/o1.json)
check "L-O-001: Order paid (1000)" "$result"

result=$(api GET "/api/members/$MID1/orders?page=0&size=20")
check "L-O-001b: Query orders" "$result"

ORDER_ID2="TEST-ORDER-$(date +%s)-002"
json /tmp/o2.json << EOF
{"eventType":"ORDER_PAID","order_id":"$ORDER_ID2","memberId":$MID1,"totalAmount":500.00,"channel":"JD","tradeTime":"2026-06-29T11:00:00","payTime":"2026-06-29T11:02:00","items":[{"sku":"SKU003","name":"商品C","price":500,"quantity":1,"category":"电子"}]}
EOF
result=$(api POST "/api/events/ORDER_CHAIN/$PROG" /tmp/o2.json)
check "L-O-002: Order paid (500)" "$result"

ORDER_ID3="TEST-ORDER-$(date +%s)-003"
json /tmp/o3.json << EOF
{"eventType":"ORDER_PAID","order_id":"$ORDER_ID3","memberId":$MID1,"totalAmount":2000.00,"channel":"DOUYIN","tradeTime":"2026-06-29T12:00:00","payTime":"2026-06-29T12:01:00","items":[{"sku":"SKU004","name":"商品D","price":1000,"quantity":2,"category":"服装","brand":"BrandA"}],"extAttributes":{"campaign_id":"PROMO-001"}}
EOF
result=$(api POST "/api/events/ORDER_CHAIN/$PROG" /tmp/o3.json)
check "L-O-003: Order with ext attrs (2000)" "$result"

result=$(api GET "/api/members/$MID1/orders/detail?orderId=$ORDER_ID3")
check "L-O-003b: Order detail" "$result"

result=$(api GET "/api/members/$MID1/transactions")
check "L-O-004: Transactions after orders" "$result"

info "L-O-005: Member state" "$(api GET "/api/members/$MID1")"

echo ""

# ============================================================
# SECTION 6: Refund
# ============================================================
echo "--- Section 6: Refund ---"

json /tmp/r1.json << EOF
{"eventType":"ORDER_REFUND_FULL","order_id":"$ORDER_ID1","memberId":$MID1,"refundAmount":1000.00,"channel":"TMALL","originalOrderId":"$ORDER_ID1","refundTime":"2026-06-29T14:00:00"}
EOF
result=$(api POST "/api/events/REFUND_CHAIN/$PROG" /tmp/r1.json)
check "L-R-001: Full refund" "$result"

json /tmp/r2.json << EOF
{"eventType":"ORDER_REFUND_PARTIAL","order_id":"$ORDER_ID2","memberId":$MID1,"refundAmount":200.00,"channel":"JD","originalOrderId":"$ORDER_ID2","refundTime":"2026-06-29T15:00:00"}
EOF
result=$(api POST "/api/events/REFUND_CHAIN/$PROG" /tmp/r2.json)
check "L-R-002: Partial refund" "$result"

result=$(api GET "/api/members/$MID1/transactions")
check "L-R-003: Transactions after refunds" "$result"

echo ""

# ============================================================
# SECTION 7: Rules Management
# ============================================================
echo "--- Section 7: Rules Management ---"

# L-RL-001: Create rule (simple DRL, no Python needed)
RULE_CODE="TEST-RULE-$(date +%s)"
json /tmp/rule.json << EOF
{"rule_code":"$RULE_CODE","rule_name":"测试积分规则","rule_type":"DRL","rule_category":"base","rule_group":"test","priority":10,"drl_content":"package com.loyalty.rules;\nrule \"TestRule\"\nwhen\n  eval(true)\nthen\n  System.out.println(\"Test rule fired\");\nend"}
EOF
result=$(api POST "/api/admin/rules" /tmp/rule.json)
check "L-RL-001: Create rule (DRAFT)" "$result"
RULE_ID=$(echo "$result" | grep -o '"id":[0-9]*' | head -1 | cut -d':' -f2)
echo "  Rule ID: $RULE_ID"

if [ -n "$RULE_ID" ]; then
  result=$(api GET "/api/admin/rules/$RULE_ID")
  check "L-RL-002: Get rule detail" "$result"

  json /tmp/rule_upd.json << EOF
{"rule_name":"测试积分规则-已更新","priority":20}
EOF
  result=$(api PUT "/api/admin/rules/$RULE_ID" /tmp/rule_upd.json)
  check "L-RL-003: Update rule" "$result"

  result=$(api POST "/api/admin/rules/$RULE_ID/publish")
  check "L-RL-004: Publish rule" "$result"

  result=$(api POST "/api/admin/rules/$RULE_ID/deactivate")
  check "L-RL-005: Deactivate rule (ARCHIVED)" "$result"

  result=$(api DELETE "/api/admin/rules/$RULE_ID")
  check "L-RL-006: Delete rule" "$result"
fi

# DRL Validation
json /tmp/drl_val.json << 'EOF'
{"drl_content":"package test; rule \"Test\" when eval(true) then end"}
EOF
result=$(api POST "/api/admin/rules/validate-drl" /tmp/drl_val.json)
check "L-RL-007: Validate DRL syntax" "$result"

# Rule test run
json /tmp/run.json << EOF
{"eventType":"ORDER","memberId":$MID1,"payload":{"totalAmount":1000}}
EOF
result=$(api POST "/api/admin/rules/test-run" /tmp/run.json)
check "L-RL-008: Rule test run" "$result"

echo ""

# ============================================================
# SECTION 8: Tier & Point Type Config
# ============================================================
echo "--- Section 8: Tier & Point Type Config ---"

result=$(api GET "/api/admin/tiers")
check "L-PT-001: Get tiers and point types" "$result"

ACT_CODE="TIER-UP-$(date +%s)"
json /tmp/ta.json << EOF
{"activityCode":"$ACT_CODE","activityName":"测试等级直升活动","targetTierCode":"GOLD","triggerType":"MANUAL","triggerConfig":{"type":"manual"},"validStartTime":"2026-06-01T00:00:00","memberScope":"ALL","oncePerMember":true,"description":"测试活动描述"}
EOF
result=$(api POST "/api/admin/tier-activities" /tmp/ta.json)
check "L-TR-001: Create tier activity" "$result"

result=$(api POST "/api/admin/tier-activities/$ACT_CODE/publish")
check "L-TR-002: Publish tier activity" "$result"

result=$(api GET "/api/admin/tier-activities")
check "L-TR-003: List tier activities" "$result"

echo ""

# ============================================================
# SECTION 9: Program Management
# ============================================================
echo "--- Section 9: Program Management ---"

result=$(api GET "/api/admin/programs")
check "L-PG-001: List programs" "$result"

result=$(api GET "/api/admin/programs/PROG001")
check "L-PG-002: Get program detail" "$result"

echo ""

# ============================================================
# SECTION 10: Event Flow
# ============================================================
echo "--- Section 10: Event Flow ---"

json /tmp/ev1.json << EOF
{"chainName":"ORDER_CHAIN","payload":{"eventType":"ORDER_PAID","order_id":"TEST-RUN-$(date +%s)","memberId":$MID1,"totalAmount":300.00,"channel":"TEST"}}
EOF
result=$(api POST "/api/events/test-run" /tmp/ev1.json)
check "L-EV-001: Event test run" "$result"

json /tmp/ev2.json << EOF
{"chainName":"BEHAVIOR_CHAIN","payload":{"eventType":"SIGN_IN","memberId":$MID1,"channel":"APP"}}
EOF
result=$(api POST "/api/events/test-run" /tmp/ev2.json)
check "L-EV-002: Behavior chain test" "$result"

echo ""

# ============================================================
# SECTION 11: Edge Cases
# ============================================================
echo "--- Section 11: Edge Cases ---"

EDGE_MID=$((88009000 + RANDOM % 1000))
PHONE_EDGE="139$(printf '%08d' $RANDOM)"
json /tmp/edge.json << EOF
{"member_id":$EDGE_MID,"tier_code":"SILVER","ext_attributes":{"mobile":"$PHONE_EDGE","name":"边缘测试会员"}}
EOF
result=$(api POST "/api/members" /tmp/edge.json)
check "L-EDGE-01: Create edge case member" "$result"

json /tmp/zp.json << 'EOF'
{"amount":0,"accountType":"REWARD","increase":true}
EOF
result=$(api POST "/api/members/$EDGE_MID/points/adjust" /tmp/zp.json)
info "L-EDGE-02: Grant zero points" "$result"

json /tmp/np.json << 'EOF'
{"amount":-100,"accountType":"REWARD","increase":true}
EOF
result=$(api POST "/api/members/$EDGE_MID/points/adjust" /tmp/np.json)
info "L-EDGE-03: Grant negative points" "$result"

result=$(api GET "/api/members/search?keyword=99999999999")
info "L-EDGE-04: Non-existent search" "$result"

result=$(api GET "/api/members/999999999")
info "L-EDGE-05: Non-existent get" "$result"

json /tmp/lp.json << 'EOF'
{"amount":9999999,"accountType":"REWARD","increase":true}
EOF
result=$(api POST "/api/members/$EDGE_MID/points/adjust" /tmp/lp.json)
check "L-EDGE-06: Grant large points" "$result"

echo ""

# ============================================================
# SECTION 12: Member Merge
# ============================================================
echo "--- Section 12: Member Merge ---"

M_MAIN=$((88008000 + RANDOM % 1000))
M_DUP=$((88008000 + RANDOM % 1000))
PHONE_MAIN="139$(printf '%08d' $RANDOM)"
PHONE_DUP="139$(printf '%08d' $RANDOM)"
json /tmp/mm1.json << EOF
{"member_id":$M_MAIN,"tier_code":"GOLD","ext_attributes":{"mobile":"$PHONE_MAIN","name":"主会员"}}
EOF
result=$(api POST "/api/members" /tmp/mm1.json)
check "L-MRG-01: Create main member" "$result"

json /tmp/mm2.json << EOF
{"member_id":$M_DUP,"tier_code":"BASE","ext_attributes":{"mobile":"$PHONE_DUP","name":"重复会员"}}
EOF
result=$(api POST "/api/members" /tmp/mm2.json)
check "L-MRG-02: Create duplicate member" "$result"

json /tmp/mm3.json << EOF
{"mainMemberId":$M_MAIN,"duplicateMemberId":$M_DUP}
EOF
result=$(api POST "/api/members/merge" /tmp/mm3.json)
check "L-MRG-03: Merge members" "$result"

echo ""

# ============================================================
# SECTION 13: Channel Binding
# ============================================================
echo "--- Section 13: Channel Binding ---"

result=$(api GET "/api/members/$MID1/channel-bindings")
check "L-CB-001: Query channel bindings" "$result"

echo ""

# ============================================================
# SECTION 14: Multiple Point Types
# ============================================================
echo "--- Section 14: Multiple Point Types ---"

# Grant TIER growth points
json /tmp/mpt1.json << 'EOF'
{"amount":200,"accountType":"TIER","increase":true}
EOF
result=$(api POST "/api/members/$MID1/points/adjust" /tmp/mpt1.json)
check "L-MPT-001: Grant TIER points" "$result"

# Grant REWARD points
json /tmp/mpt2.json << 'EOF'
{"amount":100,"accountType":"REWARD","increase":true}
EOF
result=$(api POST "/api/members/$MID1/points/adjust" /tmp/mpt2.json)
check "L-MPT-002: Grant additional REWARD points" "$result"

# Verify both types
result=$(api GET "/api/members/$MID1")
check_contains "L-MPT-003: Has REWARD" "$result" "REWARD"
check_contains "L-MPT-004: Has TIER" "$result" "TIER"

echo ""

# ============================================================
# SECTION 15: Credit Limit
# ============================================================
echo "--- Section 15: Credit Limit ---"

json /tmp/cl.json << 'EOF'
{"creditLimit":1000}
EOF
result=$(api POST "/api/admin/members/$MID1/credit-limit" /tmp/cl.json)
check "L-CL-001: Set credit limit 1000" "$result"

result=$(api GET "/api/members/$MID1")
check_contains "L-CL-002: Verify credit limit" "$result" "creditLimit"

echo ""

# ============================================================
# SECTION 16: Cache Management
# ============================================================
echo "--- Section 16: Cache Management ---"

result=$(api GET "/api/admin/cache/enums")
check "L-CC-001: Get cache enums" "$result"

result=$(api POST "/api/admin/cache/refresh")
check "L-CC-002: Refresh cache" "$result"

echo ""

# ============================================================
# SECTION 17: Idempotency Test
# ============================================================
echo "--- Section 17: Idempotency (requires Redis) ---"

# Note: Idempotency requires Redis. Without Redis, the interceptor falls through.
# This test verifies the idempotency key header is accepted.
IDEM_KEY="test-idem-$(date +%s)"
IDEM_MID=$((88007000 + RANDOM % 1000))
PHONE_IDEM="139$(printf '%08d' $RANDOM)"
json /tmp/idem.json << EOF
{"member_id":$IDEM_MID,"tier_code":"BASE","ext_attributes":{"mobile":"$PHONE_IDEM","name":"幂等测试"}}
EOF
result=$(curl -s -X POST "$BASE/api/members" -H "X-Program-Code: $PROG" -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" -H "X-Idempotency-Key: $IDEM_KEY" -d @/tmp/idem.json)
check "L-ID-001: Idempotency key accepted" "$result"
echo -e "  ${YELLOW}[NOTE]${NC} Redis not running - full idempotency cache test skipped"

echo ""

# ============================================================
# SUMMARY
# ============================================================
echo "============================================"
echo "  Test Results Summary"
echo "============================================"
echo -e "  ${GREEN}PASSED: $PASS${NC}"
echo -e "  ${RED}FAILED: $FAIL${NC}"
echo "  Total: $((PASS + FAIL))"
if [ $((PASS + FAIL)) -gt 0 ]; then
  echo "  Rate: $(( PASS * 100 / (PASS + FAIL) ))%"
fi
echo "============================================"

# Cleanup temp files
rm -f /tmp/login.json /tmp/m1.json /tmp/m2.json /tmp/m3.json /tmp/m4.json /tmp/pa1.json /tmp/pa2.json /tmp/t1.json /tmp/t2.json /tmp/o1.json /tmp/o2.json /tmp/o3.json /tmp/r1.json /tmp/r2.json /tmp/rule.json /tmp/rule_upd.json /tmp/drl_val.json /tmp/run.json /tmp/ta.json /tmp/ev1.json /tmp/ev2.json /tmp/edge.json /tmp/zp.json /tmp/np.json /tmp/lp.json /tmp/mm1.json /tmp/mm2.json /tmp/mm3.json /tmp/mpt1.json /tmp/mpt2.json /tmp/cl.json /tmp/idem.json

if [ $FAIL -gt 0 ]; then
  exit 1
fi
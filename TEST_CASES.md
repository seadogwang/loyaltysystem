# Loyalty & Campaign 系统全面测试用例文档

> 生成时间: 2026-06-29
> 测试范围: Loyalty 积分会员系统 + Campaign 营销活动系统
> 测试环境: localhost:8081 (后端) + localhost:5173 (前端)

---

## 一、Loyalty 积分会员系统测试

### 1.1 会员注册与查询 (Member API)

| 编号 | 测试用例 | 测试步骤 | 预期结果 | 状态 |
|------|----------|----------|----------|------|
| L-M-001 | 创建新会员 | POST /api/members `{member_id, tier_code:"BASE", ext_attributes:{mobile:"13900001001"}}` | 返回SUCCESS，会员创建成功，自动创建积分账户 | |
| L-M-002 | 手机号重复注册 | 使用相同手机号再次创建会员 | 返回ERR_MEMBER_EXISTS，拒绝重复注册 | |
| L-M-003 | 按手机号搜索会员 | GET /api/members/search?keyword=13900001001 | 返回会员完整信息（积分账户、等级、交易流水） | |
| L-M-004 | 按memberId查询会员 | GET /api/members/{memberId} | 返回会员详情+积分账户+等级日志+渠道绑定 | |
| L-M-005 | 创建不指定memberId会员 | POST /api/members `{ext_attributes:{mobile:"13900001002"}}` | 系统自动生成memberId，创建成功 | |
| L-M-006 | 更新会员扩展属性 | PUT /api/members/{memberId} `{ext_attributes:{pet_name:"旺财"}}` | 更新成功，schema_version同步更新 | |
| L-M-007 | 冻结会员 | POST /api/members/{memberId}/freeze | 状态变为FROZEN_REDEMPTION | |
| L-M-008 | 解冻会员 | POST /api/members/{memberId}/unfreeze | 状态变为ENROLLED | |
| L-M-009 | 会员合并 | POST /api/members/merge `{mainMemberId, duplicateMemberId}` | 创建合并任务，返回taskId | |

### 1.2 积分调整与查询 (Points)

| 编号 | 测试用例 | 测试步骤 | 预期结果 | 状态 |
|------|----------|----------|----------|------|
| L-P-001 | 人工增加积分 | POST /api/members/{memberId}/points/adjust `{amount:100, increase:true}` | 积分增加成功，生成交易流水 | |
| L-P-002 | 人工扣减积分 | POST /api/members/{memberId}/points/adjust `{amount:50, increase:false}` | 积分扣减成功，生成交易流水 | |
| L-P-003 | 查询积分交易流水 | GET /api/members/{memberId}/transactions | 返回所有交易记录，支持分页 | |
| L-P-004 | 按类型过滤交易 | GET /api/members/{memberId}/transactions?typeFilter=ACCRUAL,REDEMPTION | 仅返回指定类型交易 | |
| L-P-005 | 按日期过滤交易 | GET /api/members/{memberId}/transactions?dateFrom=2026-01-01&dateTo=2026-12-31 | 仅返回指定日期范围交易 | |
| L-P-006 | 查询扣减分配明细 | GET /api/members/{memberId}/transactions/{txId}/allocation | 返回FIFO分配明细 | |
| L-P-007 | 余额不足扣减 | 扣减超过可用余额的积分 | 返回错误或触发透支逻辑 | |

### 1.3 等级调整与查询 (Tier)

| 编号 | 测试用例 | 测试步骤 | 预期结果 | 状态 |
|------|----------|----------|----------|------|
| L-T-001 | 人工调整等级 | POST /api/members/{memberId}/tier/adjust `{newTier:"GOLD"}` | 等级变更成功，写入TierChangeLog | |
| L-T-002 | 查询等级变更日志 | GET /api/members/{memberId}/tier-logs | 返回所有等级变更记录 | |
| L-T-003 | 按原因过滤等级日志 | GET /api/members/{memberId}/tier-logs?reasonFilter=MANUAL_ADJUSTMENT | 仅返回人工调整日志 | |
| L-T-004 | 等级降级 | POST /api/members/{memberId}/tier/adjust `{newTier:"BASE"}` | 从高等级降到BASE，记录日志 | |

### 1.4 订单事件处理 — 积分累积 (Order Events → Points)

| 编号 | 测试用例 | 测试步骤 | 预期结果 | 状态 |
|------|----------|----------|----------|------|
| L-O-001 | 订单支付-积分累积 | POST /api/events/ORDER_CHAIN/PROG001 `{eventType:"ORDER_PAID", orderId, memberId, totalAmount, channel, items:[...]}` | 积分累积成功，生成ACCRUAL交易 | |
| L-O-002 | 订单头累积-固定比例 | 订单金额1000元，积分规则：每消费1元=1积分 | 累积1000积分 | |
| L-O-003 | 订单头累积-阶梯比例 | 订单金额500元，阶梯规则：<200=0.5%, 200-500=1%, >500=2% | 按阶梯计算正确积分 | |
| L-O-004 | 订单明细累积 | 订单含多SKU，不同SKU不同积分规则 | 每个SKU按各自规则计算，汇总正确 | |
| L-O-005 | 订单明细累积+品牌规则 | 订单含3个品牌商品，品牌A=2倍积分，品牌B=1倍，品牌C=0.5倍 | 按品牌规则正确计算 | |
| L-O-006 | 订单明细累积+品类规则 | 订单含不同品类（服装/食品/电子），不同品类不同积分 | 按品类规则正确计算 | |
| L-O-007 | 会员等级加成 | GOLD会员订单金额1000元，等级加成20% | 累积1200积分 | |
| L-O-008 | 多规则叠加 | 订单头累积+等级加成+特殊活动加成 | 规则正确叠加，无重复计算 | |
| L-O-009 | 最小积分门槛 | 订单金额10元，低于最低积分门槛 | 不累积积分或累积最低保底积分 | |
| L-O-010 | 积分上限 | 单笔订单积分上限5000，订单金额10000元 | 累积积分不超过5000 | |
| L-O-011 | 零元订单 | 订单金额0元 | 不累积积分 | |

### 1.5 退款场景

| 编号 | 测试用例 | 测试步骤 | 预期结果 | 状态 |
|------|----------|----------|----------|------|
| L-R-001 | 全额退款 | POST /api/events/REFUND_CHAIN/PROG001 `{eventType:"ORDER_REFUND_FULL", orderId, memberId}` | 扣回已发放积分，生成REDEMPTION交易 | |
| L-R-002 | 部分退款 | POST /api/events/REFUND_CHAIN/PROG001 `{eventType:"ORDER_REFUND_PARTIAL", orderId, memberId, refundAmount}` | 按比例扣回积分 | |
| L-R-003 | 积分已被使用的退款 | 订单积分已部分使用后发起退款 | 最多扣回剩余积分，差额不强制 | |
| L-R-004 | 重复退款 | 同一订单发起两次退款 | 第二次退款幂等或拒绝 | |
| L-R-005 | 未支付订单退款 | 对未支付订单发起退款 | 返回错误，不处理 | |

### 1.6 等级规则配置与触发

| 编号 | 测试用例 | 测试步骤 | 预期结果 | 状态 |
|------|----------|----------|----------|------|
| L-TR-001 | 配置等级阶梯 | PUT /api/admin/tiers 设置3个等级(BASE/SILVER/GOLD) | 等级配置保存成功 | |
| L-TR-002 | 等级升级触发 | 会员累积积分超过SILVER门槛 | 自动升级到SILVER，记录日志 | |
| L-TR-003 | 等级降级触发 | 会员积分过期后低于当前等级门槛 | 触发降级到对应等级 | |
| L-TR-004 | 等级保级 | 会员在评估周期内满足保级条件 | 等级保持不变 | |
| L-TR-005 | 等级直升活动 | 创建等级直升活动并发布 | 符合条件的会员直升目标等级 | |

### 1.7 积分规则配置 (Admin API)

| 编号 | 测试用例 | 测试步骤 | 预期结果 | 状态 |
|------|----------|----------|----------|------|
| L-RL-001 | 创建积分规则(DRAFT) | POST /api/admin/rules `{rule_code, rule_name, drl_content, rule_type}` | 规则创建成功，状态DRAFT | |
| L-RL-002 | 发布积分规则 | POST /api/admin/rules/{id}/publish | 经过沙箱回归测试后激活 | |
| L-RL-003 | 停用规则 | POST /api/admin/rules/{id}/deactivate | 规则状态变为INACTIVE | |
| L-RL-004 | 删除规则 | DELETE /api/admin/rules/{id} | 规则删除成功 | |
| L-RL-005 | 规则DRL校验 | POST /api/admin/rules/validate-drl `{drl_content}` | 返回语法校验结果 | |
| L-RL-006 | 规则测试运行 | POST /api/admin/rules/test-run `{eventType, memberId, payload}` | 返回规则匹配结果 | |
| L-RL-007 | 更新规则 | PUT /api/admin/rules/{id} `{drl_content, rule_name}` | 规则更新成功 | |

### 1.8 积分类型配置

| 编号 | 测试用例 | 测试步骤 | 预期结果 | 状态 |
|------|----------|----------|----------|------|
| L-PT-001 | 配置积分类型 | PUT /api/admin/tiers 包含pointTypes配置 | 积分类型保存成功 | |
| L-PT-002 | 查询积分类型 | GET /api/admin/tiers | 返回所有积分类型和等级 | |
| L-PT-003 | 会员账户自动创建 | 创建新会员时自动创建所有活跃积分类型账户 | 每个积分类型一个MemberAccount | |

### 1.9 Program管理

| 编号 | 测试用例 | 测试步骤 | 预期结果 | 状态 |
|------|----------|----------|----------|------|
| L-PG-001 | 查询Program列表 | GET /api/admin/programs | 返回当前租户的Program | |
| L-PG-002 | 创建Program | POST /api/admin/programs | 创建成功 | |
| L-PG-003 | 更新Program | PUT /api/admin/programs/{code} | 更新成功 | |
| L-PG-004 | 复制Program | POST /api/admin/programs/{code}/copy | 复制成功 | |

### 1.10 事件处理流程

| 编号 | 测试用例 | 测试步骤 | 预期结果 | 状态 |
|------|----------|----------|----------|------|
| L-EV-001 | 订单事件LiteFlow链 | POST /api/events/ORDER_CHAIN/PROG001 | 完整链执行成功 | |
| L-EV-002 | 行为事件处理 | POST /api/events/BEHAVIOR_CHAIN/PROG001 | 行为事件处理成功 | |
| L-EV-003 | 测试运行 | POST /api/events/test-run | 测试模式运行成功 | |
| L-EV-004 | 幂等性 | 同一事件重复提交 | 第二次幂等跳过 | |

---

## 二、Campaign 营销活动系统测试

### 2.1 工作区管理 (Planning Workspace)

| 编号 | 测试用例 | 测试步骤 | 预期结果 | 状态 |
|------|----------|----------|----------|------|
| C-W-001 | 创建工作区 | POST /api/campaign/workspaces | 创建成功 | |
| C-W-002 | 查询工作区列表 | GET /api/campaign/workspaces | 返回工作区列表 | |
| C-W-003 | 工作区详情 | GET /api/campaign/workspaces/{id} | 返回工作区详情 | |
| C-W-004 | 更新工作区 | PUT /api/campaign/workspaces/{id} | 更新成功 | |
| C-W-005 | 删除工作区 | DELETE /api/campaign/workspaces/{id} | 删除成功 | |

### 2.2 目标管理 (Goal)

| 编号 | 测试用例 | 测试步骤 | 预期结果 | 状态 |
|------|----------|----------|----------|------|
| C-G-001 | 创建目标 | POST /api/campaign/goals | 创建成功 | |
| C-G-002 | 查询目标列表 | GET /api/campaign/goals | 返回目标列表 | |
| C-G-003 | 更新目标 | PUT /api/campaign/goals/{id} | 更新成功 | |
| C-G-004 | 目标状态变更 | PUT /api/campaign/goals/{id}/status | 状态变更成功 | |

### 2.3 举措管理 (Initiative)

| 编号 | 测试用例 | 测试步骤 | 预期结果 | 状态 |
|------|----------|----------|----------|------|
| C-I-001 | 创建举措 | POST /api/campaign/initiatives | 创建成功 | |
| C-I-002 | 查询举措列表 | GET /api/campaign/initiatives | 返回举措列表 | |
| C-I-003 | 举措关联目标 | 创建举措时关联goalId | 关联成功 | |

### 2.4 组合优化 (Portfolio)

| 编号 | 测试用例 | 测试步骤 | 预期结果 | 状态 |
|------|----------|----------|----------|------|
| C-P-001 | 创建组合 | POST /api/campaign/portfolios | 创建成功 | |
| C-P-002 | 组合优化 | POST /api/campaign/portfolios/{id}/optimize | 贪心优化执行成功 | |

### 2.5 画布编辑器 (Canvas)

| 编号 | 测试用例 | 测试步骤 | 预期结果 | 状态 |
|------|----------|----------|----------|------|
| C-CV-001 | 创建Canvas计划 | POST /api/campaign/canvas/plans | 创建成功 | |
| C-CV-002 | 查询Canvas计划 | GET /api/campaign/canvas/plans | 返回计划列表 | |
| C-CV-003 | DAG验证 | POST /api/campaign/canvas/validate | 验证DAG结构 | |
| C-CV-004 | 编译BPMN | POST /api/campaign/canvas/compile | 编译为BPMN | |
| C-CV-005 | AI生成DAG | POST /api/campaign/canvas/ai-generate | AI生成DAG结构 | |

### 2.6 决策引擎 (Decision)

| 编号 | 测试用例 | 测试步骤 | 预期结果 | 状态 |
|------|----------|----------|----------|------|
| C-D-001 | 预算分配 | POST /api/campaign/decision/allocate | 分配成功 | |
| C-D-002 | 约束检查 | POST /api/campaign/decision/check-constraints | 返回约束检查结果 | |
| C-D-003 | 仲裁 | POST /api/campaign/decision/arbitrate | 仲裁结果 | |

### 2.7 执行管理 (Execution)

| 编号 | 测试用例 | 测试步骤 | 预期结果 | 状态 |
|------|----------|----------|----------|------|
| C-E-001 | 部署活动 | POST /api/campaign/execution/deploy | 部署成功 | |
| C-E-002 | 启动执行 | POST /api/campaign/execution/start | 启动成功 | |
| C-E-003 | 暂停执行 | POST /api/campaign/execution/pause | 暂停成功 | |
| C-E-004 | 恢复执行 | POST /api/campaign/execution/resume | 恢复成功 | |
| C-E-005 | 取消执行 | POST /api/campaign/execution/cancel | 取消成功 | |

### 2.8 干预管理 (Intervention)

| 编号 | 测试用例 | 测试步骤 | 预期结果 | 状态 |
|------|----------|----------|----------|------|
| C-IV-001 | 干预台查询 | GET /api/campaign/intervention/dashboard | 返回干预数据 | |
| C-IV-002 | 暂停节点 | POST /api/campaign/intervention/pause-node | 暂停成功 | |
| C-IV-003 | 跳过节点 | POST /api/campaign/intervention/skip-node | 跳过成功 | |
| C-IV-004 | 覆盖配置 | POST /api/campaign/intervention/override | 覆盖成功 | |

### 2.9 模拟与优化 (Simulation)

| 编号 | 测试用例 | 测试步骤 | 预期结果 | 状态 |
|------|----------|----------|----------|------|
| C-S-001 | 基线模拟 | POST /api/campaign/simulation/baseline | 返回基线结果 | |
| C-S-002 | What-if分析 | POST /api/campaign/simulation/what-if | 返回分析结果 | |
| C-S-003 | 优化方案 | POST /api/campaign/optimization/run | 返回优化方案 | |

### 2.10 其他模块

| 编号 | 测试用例 | 测试步骤 | 预期结果 | 状态 |
|------|----------|----------|----------|------|
| C-OT-001 | 内容管理 CRUD | CRUD /api/campaign/content | 操作成功 | |
| C-OT-002 | 同意管理 | CRUD /api/campaign/consent | 操作成功 | |
| C-OT-003 | 预算节奏 | GET /api/campaign/budget-pacing | 返回预算数据 | |
| C-OT-004 | 日历管理 | CRUD /api/campaign/calendar | 操作成功 | |
| C-OT-005 | DLQ管理 | GET /api/campaign/dlq | 返回死信队列 | |
| C-OT-006 | Webhook | CRUD /api/campaign/webhooks | 操作成功 | |
| C-OT-007 | 分享管理 | CRUD /api/campaign/sharing | 操作成功 | |
| C-OT-008 | 推荐管理 | CRUD /api/campaign/recommendation | 操作成功 | |
| C-OT-009 | 策略蓝图 | CRUD /api/campaign/strategy | 操作成功 | |

---

## 三、前端 Playwright E2E 测试

### 3.1 认证与导航

| 编号 | 测试用例 | 测试步骤 | 预期结果 | 状态 |
|------|----------|----------|----------|------|
| F-A-001 | 登录页面 | 访问 /login，输入用户名密码 | 登录成功，跳转到首页 | |
| F-A-002 | 未登录重定向 | 直接访问 /dashboard 未登录 | 重定向到登录页 | |
| F-A-003 | 菜单导航 | 登录后点击各个菜单项 | 正确跳转到对应页面 | |
| F-A-004 | 权限控制 | 无权限用户访问受限页面 | 显示无权限或菜单不可见 | |

### 3.2 会员管理页面

| 编号 | 测试用例 | 测试步骤 | 预期结果 | 状态 |
|------|----------|----------|----------|------|
| F-M-001 | 会员列表 | 访问会员列表页 | 显示会员列表，支持分页 | |
| F-M-002 | 会员搜索 | 输入手机号搜索 | 显示搜索结果 | |
| F-M-003 | 会员详情 | 点击会员进入详情 | 显示积分账户、等级、交易流 | |
| F-M-004 | 积分调整 | 在会员详情页调整积分 | 调整成功，余额刷新 | |
| F-M-005 | 等级调整 | 在会员详情页调整等级 | 调整成功，等级刷新 | |

### 3.3 规则配置页面

| 编号 | 测试用例 | 测试步骤 | 预期结果 | 状态 |
|------|----------|----------|----------|------|
| F-R-001 | 规则列表 | 访问规则管理页 | 显示规则列表 | |
| F-R-002 | 创建规则 | 点击创建规则，填写表单 | 创建成功 | |
| F-R-003 | 编辑规则 | 点击编辑规则 | 弹出编辑框 | |
| F-R-004 | 发布规则 | 点击发布规则 | 规则激活 | |
| F-R-005 | 规则测试 | 在规则页测试运行 | 显示匹配结果 | |

### 3.4 积分类型/等级配置页面

| 编号 | 测试用例 | 测试步骤 | 预期结果 | 状态 |
|------|----------|----------|----------|------|
| F-T-001 | 程序配置页 | 访问Program配置 | 显示积分类型和等级配置 | |
| F-T-002 | 等级活动管理 | 访问等级活动页 | 显示活动列表 | |

### 3.5 Campaign 页面

| 编号 | 测试用例 | 测试步骤 | 预期结果 | 状态 |
|------|----------|----------|----------|------|
| F-C-001 | 工作区列表 | 访问Campaign工作区列表 | 显示工作区 | |
| F-C-002 | 创建工作区 | 点击创建按钮 | 创建成功 | |
| F-C-003 | 画布编辑器 | 访问画布编辑器 | 正确渲染画布和节点 | |
| F-C-004 | 执行监控 | 访问执行监控页 | 显示执行状态 | |
| F-C-005 | 决策引擎 | 访问决策引擎页 | 显示决策数据 | |
| F-C-006 | 干预台 | 访问干预台页 | 显示干预数据 | |

---

## 四、综合业务流程测试

### 4.1 完整会员生命周期

| 编号 | 测试用例 | 测试步骤 | 预期结果 |
|------|----------|----------|----------|
| E-E-001 | 注册→消费→升级→退款→降级 | 完整流程测试 | 每步正确执行 |

### 4.2 Campaign 完整流程

| 编号 | 测试用例 | 测试步骤 | 预期结果 |
|------|----------|----------|----------|
| E-E-002 | 规划→机会→决策→画布→执行→反馈 | 完整Campaign流程 | 每步正确执行 |

---

## 五、测试执行计划

1. **Phase 3**: 先运行现有后端单元测试，确保基础通过
2. **Phase 3**: 编写并执行 Loyalty API 集成测试
3. **Phase 4**: 编写并执行 Campaign API 集成测试
4. **Phase 5**: 编写并执行 Playwright E2E 前端测试
5. **Phase 6**: 生成测试报告

---

## 六、测试数据准备

- 默认租户: PROG001
- 默认管理员: superadmin / admin123
- 测试会员ID范围: 88000001 ~ 88009999
- 测试订单ID前缀: TEST-ORDER-
- 测试手机号前缀: 1390000
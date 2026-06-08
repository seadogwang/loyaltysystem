# Loyalty SaaS 平台代码评审报告

**评审版本**: v1.0
**评审日期**: 2026-06-08
**代码仓库**: D:\Project\Loyalty-saas
**设计文档基线**: LoyaltyDesign20260606.md v7.1 + LoyaltyDesign-member.md v1.0
**评审范围**: 后端 Java (Spring Boot 3.x) + 前端 React 18 + 测试覆盖
**评审方法**: 5 个并行子代理分别评审 后端领域逻辑 / 安全与多租户 / 规则引擎与SPI / 前端实现 / 测试覆盖

---

## 一、总体评估

| 维度 | 评级 | 说明 |
|------|------|------|
| 设计文档合规性 | **⚠ 中等 (约55%)** | 核心架构思路匹配，但大量细节规格偏离或缺失 |
| 多租户安全性 | **🔴 差** | 四层防线骨架存在但多处存在穿透漏洞 |
| 业务逻辑正确性 | **⚠ 中等** | 积分瀑布流冲抵、FIFO骨架正确，但会员合并/Saga/级联重算严重缺失 |
| 前端实现合规性 | **⚠ 中低 (约40%)** | 仅 Login/Onboarding 遵循设计规范，其余使用蓝色主题 |
| 测试覆盖 | **🔴 差 (约35-40%)** | 核心业务服务（入会/合并/安全）零覆盖 |
| 代码质量 | **⚠ 中等** | 大量 `any` 类型、空 catch、硬编码密钥 |

**综合判定**: 当前代码处于 **Phase 1 骨架搭建阶段**，适合作为架构验证原型，但**不可用于任何生产或准生产环境**。需优先修复安全硬编码、多租户穿透、以及会员合并 Saga 三大阻断性缺陷后，方可进入下一阶段。

---

## 二、后端领域逻辑评审

### 2.1 One-ID 入会与身份匹配 (OneIdEnrollmentService)

| 设计规格 | 实现状态 | 严重度 |
|----------|----------|--------|
| 第零层: TMALL_OMID 品牌级匹配 | **缺失** — 无 omid 参数 | 🔴 HIGH |
| 第一层: 渠道强标识匹配 (TMALL_OUID/JD_PIN/WECHAT_OPENID/DOUYIN_OPENID) | **偏离** — 使用 WECHAT_UNIONID/ALIPAY_USER_ID 替代设计指定的 key_type | 🔴 HIGH |
| 第二层: 密文等价匹配 (MD5→TMALL_MOBILE_MD5) | **缺失** | 🔴 HIGH |
| 手机号规范化洗涤 (去+86/空格/横线) | **缺失** — normalizePhoneNumber 未调用 | 🔴 HIGH |
| 分布式锁: Redisson RLock | **未集成** — 使用 ConcurrentHashMap (仅进程内) | 🔴 CRITICAL |
| DB唯一约束兜底 (§3.4.6) | 已实现 via DataIntegrityViolationException | ✅ |

**并发风险**:
- `ConcurrentHashMap` 锁无租期/超时 — 崩溃线程将永久阻塞该 key
- Snowflake ID 生成器 `synchronized` + 硬编码 `WORKER_ID=1` — 多实例部署必然冲突
- TenantContext 已导入但从未使用

### 2.2 会员合并 Saga (MemberMergeService)

| 设计规格 (§3.5) | 实现状态 | 严重度 |
|------------------|----------|--------|
| Saga 状态机 (CREATED→FREEZING→TRANSFER_POINTS→...→COMPLETED) | **缺失** — 单个 @Transactional 方法同步执行 | 🔴 CRITICAL |
| member_merge_task 表 | **缺失** | 🔴 HIGH |
| 账户冻结 (account_status='MERGING') | **缺失** | 🔴 CRITICAL |
| 分布式锁按 member_id 排序防死锁 (§3.5.8) | **缺失** | 🔴 HIGH |
| 身份标识软删除重定向 (§3.5.8) | **错误** — 使用简单 UPDATE target_member_id=?, 无软删除 | 🔴 HIGH |
| 优惠券迁移 | **缺失** | ⚠ MEDIUM |
| 标签合并 (并集) | **缺失** | ⚠ MEDIUM |
| 补偿/回滚机制 (§3.5.10) | **缺失** | 🔴 CRITICAL |
| member_merge_audit 审计表 | **缺失** | ⚠ MEDIUM |
| MemberMergedEvent 发布 | 部分实现 — 发布 TierChangeEvent 替代 | ⚠ MEDIUM |

**设计文档 §3.5.1 明确禁止**:"禁止在唯一约束冲突捕获或同步入会流程中直接执行会员全量合并，以避免长事务锁表和数据不一致"。当前实现违反此核心约束。

**等级排序硬编码**: `tierRank()` 使用 switch(PLATINUM→4, GOLD→3, SILVER→2, BASE→1)，设计要求通过 Program.config_json 可配置。

### 2.3 积分核算 (PointGrantService / PointRedeemService)

**PointGrantService — 合规度约80%**

| 问题 | 位置 | 严重度 |
|------|------|--------|
| 无 frozen_status/account_status 检查 (设计 §4.2.3, §3.5.3) | PointGrantService:96-98 | 🔴 HIGH |
| 无 is_redeemable/allow_negative 类型校验 | 全文件 | ⚠ MEDIUM |
| 信用账户不存在时跳过 Step 2 而非创建 | PointGrantService:155-156 | ⚠ LOW |

**PointRedeemService — 合规度约60%**

| 问题 | 位置 | 严重度 |
|------|------|--------|
| 无 frozen_status 入口拦截 (设计 §4.2.3) | PointRedeemService:74-87 | 🔴 CRITICAL |
| 无 account_status='MERGING' 检查 (设计 §3.5.3) | PointRedeemService:74-87 | 🔴 HIGH |
| sumAvailableBalance 与 findActiveBatchesForUpdate 非原子 — 并发兑换窗口 | :104 vs :126 | 🔴 HIGH |
| FIFO 游标优化 (§2.4.3) 完全缺失 — 每次扫描全部活跃批次 | :126-127 | 🔴 HIGH |
| 惰性过期导致 totalAvailable 预估值可能偏大 | :104 vs :143-146 | ⚠ MEDIUM |

**NegativePendingService — 合规度约30%**

| 问题 | 位置 | 严重度 |
|------|------|--------|
| 设置 member.status='SUSPENDED' 而非 member_account.frozen_status='FROZEN_REDEMPTION' | :91-96 | 🔴 HIGH |
| clearPendingAndRestore UPDATE 无 programCode 过滤 — 跨租户数据修改 | :105-114 | 🔴 CRITICAL |
| 未验证债务是否清偿即恢复状态 | :104-116 | 🔴 HIGH |

### 2.4 级联重算引擎 (CascadeRecalculationEngine)

**合规度约15% — 骨架仅存，核心逻辑未实现**

| 问题 | 位置 | 严重度 |
|------|------|--------|
| loadTimelineTransactions 返回 List.of() — 事件回放永远不发生 | :292 | 🔴 CRITICAL |
| calculateDelta 总返回零差额 | :305-311 | 🔴 CRITICAL |
| recalculatePoints 硬编码 GOLD→2x stub | :295-303 | 🔴 CRITICAL |
| @Async + @Transactional + protected 方法 — Spring 代理可能不拦截 | :170-171 | 🔴 HIGH |
| recoverStuckJobs 传 null 作为 programCode — 无租户隔离 | :237 | 🔴 HIGH |

### 2.5 实体设计合规性

| 实体 | 偏离项 | 严重度 |
|------|--------|--------|
| **Member** | 复合 PK (programCode, memberId) vs 设计的 memberId(unique)+programCode; 缺失 name/gender/birthday | 🔴 HIGH |
| **AccountTransaction** | precision=20, scale=2 vs 设计的 NUMERIC(18,4); 缺失 rule_snapshot_id; 无 PARTITION BY RANGE | 🔴 CRITICAL |
| **MemberAccount** | 有 balance 字段 (设计明确"不维护实时余额"); 缺失 frozen_status/account_status | 🔴 HIGH |
| **MemberUniqueKey** | keyCombination vs 设计的 key_type; 缺失 is_strong/is_verified/verified_at; 无 PARTITION BY LIST | 🔴 HIGH |

**精度致命缺陷**: AccountTransaction.amount/remainingAmount 定义为 `scale=2`，但所有服务代码使用 `SCALE=4`。数据库层将截断至 2 位小数，导致金融级精度丢失。

### 2.6 后端领域逻辑 — 关键缺陷汇总

1. **[CRITICAL] MemberMergeService 违反设计核心约束** — 单事务全量合并，无 Saga/冻结/补偿/分布式锁
2. **[CRITICAL] EnrollmentDistributedLock 未集成** — ConcurrentHashMap 仅进程内保护，多实例部署必然重复入会
3. **[CRITICAL] AccountTransaction 精度不匹配** — scale=2 vs SCALE=4，金融计算精度丢失
4. **[CRITICAL] 无 frozen_status/account_status 执行拦截** — PointGrant/PointRedeem 均不检查冻结/合并状态
5. **[CRITICAL] 级联重算核心逻辑完全未实现** — loadTimelineTransactions=空, calculateDelta=零
6. **[CRITICAL] NegativePendingService 跨租户 UPDATE** — 无 programCode 过滤
7. **[HIGH] 缺失 TMALL_OMID 第零层匹配**
8. **[HIGH] 缺失手机号规范化洗涤**
9. **[HIGH] PointRedeemService 非原子余额检查**
10. **[HIGH] 无 FIFO 游标优化机制**

---

## 三、安全与多租户评审

### 3.1 四层防御体系实现状态

| 防线 | 设计规格 | 实现状态 | 关键问题 |
|------|----------|----------|----------|
| L1: TenantContextFilter | HTTP Header 提取 + finally clear | ✅ 已实现 | ⚠ OpenApiSignatureFilter 的 finally 在 TenantContextFilter 之前执行，导致 double-clear |
| L2: ORM 拦截器 | 自动注入 program_code=? | ⚠ 部分 | MyBatis 拦截器仅日志审计不改 SQL; Hibernate 拦截器不覆盖 createNativeQuery |
| L3: PostgreSQL RLS + Redis Key 隔离 | SET app.current_program_code | ✅ 已实现 | ⚠ RLS SET 用字符串拼接 (SQL注入风险); Redis Key 无 TenantKeyGenerator 工具 |
| L4: Query Sentinel (BaseRepository) | 禁止 findById(id) | ⚠ 部分 | 3个 Repository 直接继承 JpaRepository 绕过 Sentinel |

### 3.2 穿透风险路径

| 路径 | 文件 | 描述 |
|------|------|------|
| EventInboxRepository | :16-29 | 5个查询方法均无 program_code 过滤 |
| AdminController.listPrograms() | :84-91 | `SELECT p FROM Program p` 无租户过滤 — 可列出全部 Program |
| AdminController 多处 | :122,152,170,199,407 | 直接使用 programRepo.findById() 绕过 Sentinel |
| SystemCacheService | :41,51-70 | 硬编码 PROG001 加载全部租户数据到内存 |
| TenantAuditMonitor | :50-52 | JSON 字符串拼接构造审计详情 (SQL注入) |
| em.find(AccountTransaction.class, id) | MemberController:200 | PK查找无 program_code 检查 |

### 3.3 RBAC 与权限矩阵

| 问题 | 严重度 |
|------|--------|
| SUPER_ADMIN 自动通过所有权限检查，无跨租户审计 | ⚠ MEDIUM |
| SPI 路径 /api/open/spi/ 白名单跳过全部 JWT 认证 | 🔴 HIGH |
| JWT program_code 与 X-Program-Code Header 不校验一致性 — 租户冒充向量 | 🔴 CRITICAL |
| PATH_PERMISSION_MAP 仅覆盖 6 个路径，缺失 /api/admin/** /api/points/** 等 | ⚠ MEDIUM |

### 3.4 硬编码密钥 — 阻断性安全缺陷

| 密钥 | 文件:行 | 严重度 |
|------|----------|--------|
| JWT Secret: `"loyalty-saas-jwt-secret-key-2026"` | MultiTenantRbacInterceptor:59 | 🔴 **CRITICAL** |
| OpenAPI AppSecret: `"openapi-secret-for-" + programCode` | OpenApiSignatureFilter:192 | 🔴 **CRITICAL** |
| 上述密钥允许任意伪造 JWT Token 和 HMAC 签名，完全摧毁认证防线 |

### 3.5 OpenAPI 签名安全

| 问题 | 严重度 |
|------|--------|
| AppSecret 可推导 — 完全摧毁 HMAC-SHA256 | 🔴 CRITICAL |
| readBody() 消费 InputStream，下游 Handler 收到空 Body | 🔴 HIGH |
| nonceCache (ConcurrentHashMap) 无容量限制 | ⚠ MEDIUM |
| 签名比较使用 MessageDigest.isEqual() (抗时序攻击) | ✅ GOOD |

### 3.6 安全 — 关键缺陷汇总

1. **[CRITICAL] 硬编码 JWT Secret** — 任意伪造 Token
2. **[CRITICAL] 硬编码可推导 OpenAPI AppSecret** — 完全摧毁 HMAC 签名机制
3. **[CRITICAL] 3个 Repository 绕过 BaseRepository Sentinel** — findById()/findAll() 可用
4. **[CRITICAL] JWT program_code 与 Header 不校验一致性** — 租户冒充向量
5. **[CRITICAL] OpenApiSignatureFilter/TenantContextFilter double-clear 冲突**
6. **[HIGH] SPI 白名单跳过全部认证**
7. **[HIGH] SQL注入风险** — 审计详情 JSON 拼接
8. **[HIGH] EventInboxRepository 跨租户查询**
9. **[HIGH] AdminController 无租户过滤**

---

## 四、规则引擎与 SPI 网关评审

### 4.1 Drools 规则引擎

| 评估项 | 状态 | 问题 |
|--------|------|------|
| KieBase AtomicReference 缓存 | ✅ 正确实现 | ⚠ 旧 KieContainer 未 dispose → 内存泄漏 |
| StatelessKieSession 每次新建 | ✅ 正确实现 | — |
| ActionCollector 线程安全 | ✅ synchronizedList + List.copyOf | — |
| synchronized(this) DCL | ⚠ 过宽 | 建议改用 computeIfAbsent |
| MemberFact.getExtNumber() 缺失键返回 0.0 | ⚠ | 应返回 null 避免 DRL 规则误触发 |

### 4.2 SPI 网关与渠道 Handler

| 评估项 | 状态 | 问题 |
|--------|------|------|
| Tmall HMAC-SHA256 + MessageDigest.isEqual() | ✅ | 抗时序攻击 |
| JD MD5 签名 | ✅ | 符合 JOS 规范 |
| WeChat SHA1(token+timestamp+nonce) | ✅ | 符合微信规范 |
| DouyinSpiHandler 特殊处理 | 🔴 **缺失** | register/verify_mobile 路由、One-ID匹配、跨渠道合并完全未实现 |
| 幂等键使用 currentTimeMillis() | 🔴 **CRITICAL** | 重试请求生成不同幂等键 → 幂等保护失效 |
| SPI 返回 HTTP 404/401 | ⚠ | 设计要求始终返回 HTTP 200 |
| readBody() 无大小限制 | ⚠ | 潜在 OOM 风险 |

### 4.3 GraalVM 映射引擎

| 评估项 | 状态 | 问题 |
|--------|------|------|
| 沙箱约束 (allowAllAccess=false, allowIO=false 等) | ✅ | — |
| allowCreateThread(false) | 🔴 **缺失** | 设计 §7.3.1 明确要求 |
| allowValueSharing(false) | 🔴 **缺失** | 设计 §7.3.1 明确要求 |
| 无内存/语句数限制 | ⚠ | 50ms 内可分配大量内存 |
| 每次 Context 新建 + try-with-resources | ✅ | — |

### 4.4 Event Inbox 处理器

| 评估项 | 状态 | 问题 |
|--------|------|------|
| 状态机 RECEIVED→VALIDATING→...→COMPLETED | ✅ | — |
| 指数退避 30s×2^(retryCount-1) | ✅ | — |
| retryCount 双重递增风险 | ⚠ | validateAndProcess 与 transformAndComplete 异常处理重叠 |
| processReceived 与 async 提交之间有竞态 | ⚠ | 1s 后下次扫描可能重复读取 |

### 4.5 通知系统

| 评估项 | 状态 | 问题 |
|--------|------|------|
| CircuitBreakerManager 阈值5次+60s恢复 | ✅ | ⚠ isOpen() 状态转换非原子 |
| Outbox 模式 (PENDING→SENDING→SENT) | ✅ | ⚠ 分布式锁使用 ConcurrentHashMap mock |
| OutboundEventSubscriber 租户防穿透检查 | ✅ | — |
| 硬编码收件人 "13800138000" | ⚠ | 应从 member_unique_key 查询 |

### 4.6 规则引擎 & SPI — 关键缺陷汇总

1. **[CRITICAL] KieContainer 未 dispose** — 规则频繁更新时内存泄漏
2. **[CRITICAL] DouyinSpiHandler 特殊处理完全缺失** — register/verify_mobile 未实现
3. **[CRITICAL] SPI 幂等键使用 currentTimeMillis()** — 重试幂等保护失效
4. **[CRITICAL] GraalVM 沙箱缺失 allowCreateThread(false) 和 allowValueSharing(false)**

---

## 五、前端实现评审

### 5.1 会员客服工作台 (MemberService.tsx)

| 规格要求 | 实现状态 | 严重度 |
|----------|----------|--------|
| 搜索框 300px + 回车触发 | ⚠ 320px | LOW |
| 最近查询下拉 (localStorage, 最多10条) | 🔴 缺失 | MEDIUM |
| 空状态 → "创建新会员" 按钮 | 🔴 缺失 (仅"重新查询") | MEDIUM |
| 摘要卡片左右两栏 + #e8e8e8 边框 + 12px圆角 | ⚠ 使用 Ant Card 默认样式 | MEDIUM |
| 操作按钮: 信息修改/调整积分/调整等级/冻结/合并 | ⚠ "信息修改"和"合并会员"无 handler | HIGH |
| 积分账户: 消费积分/成长值(含进度条)/授信积分 | ✅ | — |
| "查看明细" 预筛选跳转 | 🔴 onViewDetail 无操作 | MEDIUM |
| 交易流水: 类型多选过滤 + 时间范围过滤 | 🔴 缺失 | HIGH |
| 交易排序 (时间/变动积分) | 🔴 缺失 | MEDIUM |
| 溯源 → Drawer (设计要求) | 🔴 使用 Modal 替代 | MEDIUM |
| 渠道绑定: 6+列完整表格 | 🔴 仅2列 (标识类型/标识值) | HIGH |
| 解绑/验证/手动绑定操作 | 🔴 缺失 | HIGH |
| 调整积分模态框 500px | 🔴 400px | LOW |
| 调整等级: 生效时间字段 + 降级二次确认 | 🔴 缺失 | HIGH |
| 冻结: Popconfirm 二次确认 | 🔴 缺失 | HIGH |
| 合并: 两步向导 | 🔴 缺失 | HIGH |

### 5.2 Schema 编辑器

| 规格要求 | 实现状态 |
|----------|----------|
| JsonSea 树形编辑器 | 🔴 使用自定义 FieldRow 树 |
| 实体选择器: 会员/交易/+新建 | ⚠ "新建业务实体"按钮无 handler |
| 版本管理: 保存草稿→DRAFT / 发布→新版本 | 🔴 仅 message.success, 无API调用 |
| 版本历史抽屉 | 🔴 缺失 |
| 从API加载Schema | 🔴 硬编码 demo 数据 |

**重复实现**: SchemaBuilder.tsx (组件) 和 SchemaEditor.tsx (页面) 功能重叠，无明确归属。

### 5.3 映射配置器

| 规格要求 | 实现状态 |
|----------|----------|
| 左侧渠道列表 + 绿/灰状态点 | ✅ |
| Monaco Editor 脚本编辑 | ✅ |
| 预览测试 → 后端 GraalVM 沙箱 | 🔴 使用客户端 new Function() 替代后端API |

### 5.4 设计规范合规性

| 规范项 | 状态 | 说明 |
|--------|------|------|
| 纯白底 #ffffff | ✅ | 页面背景正确 |
| 主色 #1a1a1a (黑线条) | 🔴 **FAIL** | AppShell.tsx:129 设置 `colorPrimary: '#1677ff'` (蓝色) |
| 按钮: 黑底白字 / 白底黑框 | 🔴 **FAIL** | 仅 Login/Onboarding 遵循,其余使用 Ant 蓝色 |
| 卡片: 1px #e8e8e8 边框 + 12px圆角 | 🔴 **FAIL** | Ant Card 默认 (6px圆角) |
| Toggle: 44×24 + #1a1a1a | ✅ | Onboarding 自定义 Toggle 合规 |
| SVG图标: stroke #1a1a1a | ✅ | Logo和Onboarding图标合规 |

### 5.5 技术栈合规性

| 技术 | 规格 | 实际 | 状态 |
|------|------|------|------|
| React 18 | ✅ | ✅ | — |
| Ant Design 5 | ✅ | ✅ | — |
| Zustand | ✅ | ✅ | — |
| Formily 2.x | ✅ | ✅ | — |
| react-query | ✅ 要求 | 🔴 缺失 | 无缓存策略 |
| JsonSea | ✅ 要求 | 🔴 缺失 | 自定义树替代 |
| React Router v6 | v6 | **v7** | ⚠ 版本偏离 |

### 5.6 权限与安全

| 项目 | 状态 | 说明 |
|------|------|------|
| AuthGuard 权限拦截 | 🔴 **passthrough** | "开发阶段暂时放行所有权限" |
| 按钮级权限控制 | 🔴 缺失 | 无按钮检查 hasPermission() |
| X-Program-Code Header | ✅ | 从 sessionStorage 注入 |
| X-Trace-Id Header | ✅ | crypto.randomUUID 自动生成 |
| Authorization (JWT) Header | 🔴 缺失 | 无拦截器注入 |
| 401 → 跳转登录 | 🔴 缺失 | 无响应拦截器 |

### 5.7 前端 — 关键缺陷汇总

1. **[CRITICAL] ConfigProvider colorPrimary #1677ff 覆盖全应用** — 违反 #1a1a1a 设计哲学
2. **[CRITICAL] AuthGuard 权限放行** — 零权限执行
3. **[CRITICAL] 无 JWT Authorization Header 注入**
4. **[CRITICAL] 无 401 响应拦截**
5. **[HIGH] MemberService 核心功能缺失** — 过滤、渠道操作、合并向导、冻结确认
6. **[HIGH] MappingConfig 使用客户端沙箱替代后端 GraalVM**
7. **[HIGH] 两个 Schema 编辑器实现并存**
8. **[HIGH] 大量 any 类型 — TypeScript strict 失效**

---

## 六、测试覆盖评审

### 6.1 后端测试覆盖地图

| 模块 | 覆盖度 | 关键缺失 |
|------|--------|----------|
| common/context (TenantContext) | ✅ 100% | — |
| common/dto/exception | ✅ 100% | — |
| common/event (LocalEventBus) | ✅ | — |
| domain/converter (JsonbConverter) | ✅ | — |
| spi (SpiHandlerFactory/Gateway/Tmall) | ✅ | — |
| spi Full Chain Integration | ✅ | — |
| mapping (ScriptingTransformer) | ⚠ 60% | JS引擎条件跳过 |
| event (EventInboxProcessor) | ✅ | — |
| cascade (ShadowContext/AccountDelta) | ✅ | — |
| accounting (Grant/Redeem) | ⚠ 50% | FIFO细节、信用扣减未测 |
| rules (ActionCollector) | ⚠ | 无 Drools 引擎执行测试 |
| **OneIdEnrollmentService** | 🔴 **0%** | 入会/并发/身份匹配零覆盖 |
| **MemberMergeService** | 🔴 **0%** | 合并流程零覆盖 |
| **MultiTenantRbacInterceptor** | 🔴 **0%** | JWT解析/角色隔离零覆盖 |
| **OpenApiSignatureFilter** | 🔴 **0%** | HMAC验证/nonce防重放零覆盖 |
| **CascadeRecalculationEngine** | 🔴 **0%** | 核心引擎零覆盖 |
| **Notification (CircuitBreaker/Providers)** | 🔴 **0%** | — |
| **NegativePendingService** | 🔴 **0%** | — |
| **QuotaBillingSentinel** | 🔴 **0%** | — |
| **Scheduled Jobs** | 🔴 **0%** | — |

### 6.2 关键缺失测试 (P0级)

1. **OneIdEnrollmentService** — 并发入会竞态、多层身份匹配、Snowflake ID
2. **MemberMergeService** — 合并流程、积分/等级迁移、唯一键重定向
3. **MultiTenantRbacInterceptor** — JWT解析、四角色隔离、权限映射
4. **OpenApiSignatureFilter** — HMAC验证、时间戳容差、nonce防重放
5. **租户隔离穿透测试** — PROG001 尝试读写 PROG002 数据
6. **FIFO 兑换细节** — 多批次排序、惰性过期、信用扣减
7. **瀑布流发放细节** — 三阶段冲抵精确金额追踪

### 6.3 测试质量评估

**优点**:
- @DisplayName 可读性好
- 真实 DB 集成测试 + PostgreSQL RLS 上下文注入
- BigDecimal 精度意识 (compareTo vs equals)

**不足**:
- 无并发/并行测试
- 大部分仅 happy path
- 无 Mockito 服务单元测试
- ScriptingTransformerTest 条件跳过变 no-op
- 前端零组件级单元测试 (仅 E2E smoke)
- System.out.println 替代 assertThat

### 6.4 前端测试

- 5 个 Playwright E2E spec 文件 (烟雾测试级别)
- 零组件级单元测试 (无 Vitest/Jest)
- 零 Store 单元测试
- E2E 测试使用软断言 (`expect(true).toBeTruthy()`)

---

## 七、阻断性缺陷优先修复清单

以下缺陷必须在进入下一开发阶段前修复:

| # | 缺陷 | 类别 | 修复建议 |
|---|------|------|----------|
| 1 | 硬编码 JWT Secret + OpenAPI AppSecret | 安全 | 迁移至 Vault/KMS/环境变量 |
| 2 | MemberMergeService 违反 Saga 约束 | 领域 | 实现 member_merge_task 表 + 状态机步骤 + 补偿路径 |
| 3 | EnrollmentDistributedLock 未集成 | 领域 | 替换 ConcurrentHashMap 为 Redisson RLock |
| 4 | JWT program_code 与 Header 不校验 | 安全 | RBAC拦截器中验证一致性 |
| 5 | AccountTransaction 精度 scale=2 vs 4 | 领域 | 统一为 precision=18, scale=4 |
| 6 | frozen_status/account_status 未执行 | 领域 | 在 grant/redeem 入口添加拦截 |
| 7 | NegativePendingService 跨租户 UPDATE | 安全 | 添加 programCode 过滤 |
| 8 | 3个 Repository 绕过 BaseRepository | 安全 | 全部改为继承 BaseRepository |
| 9 | SPI 幂等键 currentTimeMillis() | SPI | 使用平台请求ID替代 |
| 10 | GraalVM 沙箱缺失 allowCreateThread/allowValueSharing | SPI | 添加设计文档要求的约束 |
| 11 | AuthGuard 权限 passthrough | 前端 | 实现实际权限检查 |
| 12 | ConfigProvider colorPrimary #1677ff | 前端 | 改为 #1a1a1a |
| 13 | DouyinSpiHandler 特殊处理缺失 | SPI | 实现 register/verify_mobile 路由 |

---

## 八、总体建议

### 8.1 立即行动 (Phase 1 阻断修复)

1. **移除所有硬编码密钥** — JWT Secret 和 OpenAPI AppSecret 必须从环境变量/密钥管理服务读取
2. **建立 TenantContext 单一入口** — 仅 TenantContextFilter 负责设置/清除，其他组件仅验证不操作
3. **全部 Repository 继承 BaseRepository** — 添加 ArchUnit 编译时强制检查
4. **实现会员合并 Saga** — 这是系统核心业务流程，当前实现违反设计文档核心约束

### 8.2 短期行动 (Phase 2 核心补全)

1. 实现 FIFO 游标优化 + frozen_status 拦截
2. 补全 One-ID 第零层 (TMALL_OMID) + 第二层 (密文等价匹配) + 手机号规范化
3. 实现级联重算核心逻辑 (loadTimelineTransactions + calculateDelta)
4. 修复 DouyinSpiHandler register/verify_mobile 特殊处理
5. 修复 SPI 幂等键使用平台请求ID
6. 补全 GraalVM 沙箱约束
7. 实现 KieContainer dispose 生命周期

### 8.3 中期行动 (Phase 3 覆盖提升)

1. 添加 P0 级测试覆盖: 入会/合并/RBAC/签名/穿透/FIFO
2. 前端: 实现 AuthGuard 权限矩阵 + JWT Header 注入 + 401拦截
3. 前端: 统一设计规范 (#1a1a1a 主题)
4. 前端: 安装 react-query + 统一 Schema 编辑器
5. 消除 TypeScript any 类型
6. 添加 Testcontainers 替代依赖预置 DB

### 8.4 长期行动 (Phase 4 生产就绪)

1. 实现 AccountTransaction 分区 (PARTITION BY RANGE)
2. 实现 MemberUniqueKey 分区 (PARTITION BY LIST)
3. 添加 Redis TenantKeyGenerator 工具
4. 实现 OpenAPI Body 缓存 (ContentCachingRequestWrapper)
5. 完善通知系统分布式锁 (替代 ConcurrentHashMap mock)
6. 前端: MemberService 完整功能 (过滤、渠道操作、合并向导、冻结确认)
7. 添加 Vitest 前端组件测试

---

## 九、结论

当前代码已搭建起与设计文档对齐的架构骨架，核心引擎结构 (KieBase 缓存/无状态会话/ActionCollector/瀑布流冲抵/FIFO 兑换/SPI策略模式/GraalVM沙箱/Event Inbox 状态机) 基本正确。但存在 **13 项阻断性缺陷** (硬编码密钥/Saga缺失/穿透漏洞/精度丢失/权限放行) 和大量设计合规性偏离 (~45%未达标)。

**建议**: 在修复阻断性缺陷后，代码可进入 Phase 2 核心补全阶段。当前状态不建议进行任何集成测试或准生产部署。

---

**评审人**: AI Code Review Agents (5 并行子代理)
**报告生成**: 2026-06-08
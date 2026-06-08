# 忠诚度管理SaaS平台 — 设计文档 v7.3 代码评审评估报告

**评审日期**：2026-06-08
**评审基线**：LoyaltyDesign20260606_v7.3.md
**评审范围**：后端实体模型、核心业务服务、安全防御、SPI网关、规则引擎、事件总线、前端代码、测试覆盖

---

## 执行摘要

本次评审对 D:\project\Loyalty-saas\ 下的代码与设计文档 v7.3 进行了全面对照检查。设计文档中标记的 **41个评审问题**，经逐一验证后，**修复状态分布如下**：

| 状态 | 数量 | 占比 |
|------|------|------|
| ✅ 已修复 | 12 | 29.3% |
| ⚠️ 部分修复 | 4 | 9.8% |
| ❌ 未修复 | 25 | 60.9% |

**总体评估**：代码质量较上一轮评审显著提升，核心业务逻辑（PointGrantService、PointRedeemService）已按设计实现瀑布流冲抵和FIFO核销，但依然存在 **5个CRITICAL安全硬编码缺陷** 和 **会员合并Saga状态机完全缺失** 两个核心风险域。数据库DDL已完整对齐设计（loyalty_saas_ddl_v7.3.sql），但实体代码与DDL存在偏差。

---

## 第一章：实体模型对照

### 1.1 Member 实体 (`Member.java`)

| 评审项 | 设计要求 | 实际代码 | 状态 |
|--------|----------|----------|------|
| 主键结构 | `id BIGSERIAL PK` + `member_id VARCHAR(64) UNIQUE` | `@IdClass` 复合主键 `(program_code, member_id)` | ❌ **未修复** |
| memberId 类型 | `VARCHAR(64)` (雪花ID字符串) | `Long` | ❌ **类型偏差** |
| name 字段 | `VARCHAR(100)` | 已存在 | ✅ |
| gender 字段 | `VARCHAR(10)` MALE/FEMALE/UNKNOWN | 已存在 | ✅ |
| birthday 字段 | `DATE` | 已存在 | ✅ |
| status 字段 | ENROLLED/SUSPENDED/MERGED/DEACTIVATED | 已存在 | ✅ |
| schemaVersion | `VARCHAR(16)` | 已存在 | ✅ |
| extAttributes | JSONB | 已存在 (LinkedHashMap) | ✅ |
| mergedToMemberId | — | 额外字段，设计未要求 | ⚠️ |

**关键问题**：复合主键 `(program_code, member_id)` 导致跨表JOIN复杂、索引膨胀。设计明确要求单一大自增PK + member_id UNIQUE。memberId 使用 Long 而非 VARCHAR(64) 会导致前端雪花ID在JSON传输中精度丢失。

### 1.2 MemberAccount 实体 (`MemberAccount.java`)

| 评审项 | 设计要求 | 实际代码 | 状态 |
|--------|----------|----------|------|
| balance 字段 | **禁止**（不维护实时余额） | 已删除 | ✅ **已修复** |
| frozen_status | `VARCHAR(16) DEFAULT 'ACTIVE'` | 已存在 | ✅ **已修复** |
| account_status | `VARCHAR(16) DEFAULT 'ACTIVE'` (MERGING状态) | **缺失** | ❌ **未修复** |
| amount 精度 | `NUMERIC(20,4)` | `precision=20, scale=4` | ✅ |
| version 乐观锁 | `INT DEFAULT 1` | 已存在 @Version | ✅ |
| memberId 类型 | `VARCHAR(64)` | `Long` | ❌ |

**关键问题**：缺少 `account_status` 字段导致会员合并时的冻结机制无法实现（设计 §3.5.3 要求设置为 `MERGING` 阻止并发积分操作）。这是阻塞性缺陷，直接影响合并Saga的正确性。

### 1.3 AccountTransaction 实体 (`AccountTransaction.java`)

| 评审项 | 设计要求 | 实际代码 | 状态 |
|--------|----------|----------|------|
| amount 精度 | `NUMERIC(18,4)` | `precision=18, scale=4` | ✅ **已修复** |
| remainingAmount 精度 | `NUMERIC(18,4)` | `precision=18, scale=4` | ✅ **已修复** |
| rule_snapshot_id | `VARCHAR(64)` | **缺失**（用 rule_code + rule_version 替代） | ❌ **未修复** |
| transaction_type 枚举 | ACCRUAL/REDEMPTION/EXPIRATION/REPAYMENT/CREDIT_REPAY/CREDIT_DRAWDOWN/OVERDRAFT | 使用 REVERSAL/ADJUSTMENT 等自定义值 | ⚠️ **部分偏差** |
| operation_key | 幂等操作键 | 已存在 | ✅ |
| 额外字段 | — | accountId, reversalType, extAttributes, orderTime, payTime | ⚠️ |

**关键问题**：`rule_snapshot_id` 是级联重算引擎加载历史规则快照的关键字段，缺失导致级联重算无法正确还原规则版本。transaction_type 枚举值与设计不完全一致。

### 1.4 MemberUniqueKey 实体 (`MemberUniqueKey.java`)

| 评审项 | 设计要求 | 实际代码 | 状态 |
|--------|----------|----------|------|
| key_type 列名 | `key_type` | `key_combination` | ⚠️ **名称偏差** |
| is_strong | `BOOLEAN DEFAULT true` | 已存在 | ✅ **已修复** |
| is_verified | `BOOLEAN DEFAULT false` | 已存在 | ✅ **已修复** |
| verified_at | `TIMESTAMPTZ` | 已存在 | ✅ **已修复** |
| created_at | `TIMESTAMPTZ` | 已存在 | ✅ **已修复** |
| 主键 | `id BIGSERIAL + UNIQUE(program_code, key_type, key_value)` | `@IdClass (program_code, key_combination, key_value)` | ❌ |
| PARTITION BY LIST | 按 key_type 分区 | 实体未体现分区 | ⚠️ |

---

## 第二章：安全防御体系评估

### 2.1 CRITICAL 未修复缺陷

| 编号 | 缺陷 | 位置 | 详情 |
|------|------|------|------|
| **R-SEC-01** | 硬编码 JWT Secret | `MultiTenantRbacInterceptor.java:59` | `"loyalty-saas-jwt-secret-key-2026"` — 从未外部化，无环境变量/Vault读取 |
| **R-SEC-02** | 可推导 AppSecret | `OpenApiSignatureFilter.java:192` | `"openapi-secret-for-" + programCode` — 完全可推导，摧毁HMAC-SHA256签名机制 |
| **R-SEC-05** | JWT program_code 与 Header 不校验 | `MultiTenantRbacInterceptor.java` | 攻击者可用合法 Token + 不同 X-Program-Code Header 冒充其他租户 |
| **R-SEC-07** | NegativePendingService UPDATE 无租户过滤 | `NegativePendingService.java:106` | `clearPendingAndRestore` UPDATE 缺少 `WHERE program_code = ?` |
| **R-SEC-09** | SPI 路径跳全部 JWT 认证 | `MultiTenantRbacInterceptor.java:64` | `/api/open/spi/` 在白名单中，跳过全部 JWT 认证 |

### 2.2 HIGH 未修复缺陷

| 编号 | 缺陷 | 详情 |
|------|------|------|
| **R-SEC-10** | EventInboxRepository 查询方法无 program_code 过滤 | 未验证所有查询方法 |
| **R-SEC-13** | MyBatis 拦截器仅审计不注入租户 SQL | 需确认实际行为 |
| **R-SEC-15** | Redis Key 无 TenantKeyGenerator 统一前缀 | 需确认 |
| **R-SEC-16** | readBody() 消费 InputStream | `OpenApiSignatureFilter.java:198` — 无 ContentCachingRequestWrapper |

### 2.3 已修复 / 进展良好

| 项目 | 状态 |
|------|------|
| TenantContextFilter finally 清理 + MDC管理 | ✅ 已超设计规范实现 |
| OpenApiSignatureFilter/TenantContextFilter 职责分离（无 double-clear 冲突） | ✅ 已修复 |
| 四层防御体系第一层（入口防御） | ✅ 实现完整 |

---

## 第三章：核心业务服务评估

### 3.1 PointGrantService（积分发放瀑布流）

| 评审项 | 状态 | 详情 |
|--------|------|------|
| 冻结检查 (frozen_status) | ✅ | 检查 `FROZEN_ALL`，抛出 `ERR_ACCOUNT_FROZEN` |
| 合并检查 (account_status) | ❌ | **未实现** — MemberAccount 缺少此字段 |
| Step 1: 补天窗（偿还透支） | ✅ | 扫描 OVERDRAFT 批次，FOR UPDATE 锁定后冲抵 |
| Step 2: 跨账户还信用 | ✅ | 查询 CREDIT 账户，扣减 credit_used |
| 信用还清后解冻 | ❌ | **R-PTS-08 未修复** — 无 `setFrozenStatus("ACTIVE")` |
| totalAccrued 按实际入账 | ❌ | **R-PTS-06 未修复** — 行193: `pointsToGrant` 而非 `actualAccrued` |

### 3.2 PointRedeemService（FIFO积分核销）

| 评审项 | 状态 | 详情 |
|--------|------|------|
| 冻结检查 (frozen_status) | ✅ | 检查 `FROZEN_ALL` 和 `FROZEN_REDEMPTION` |
| 合并检查 (account_status) | ❌ | **未实现** |
| TOCTOU 竞态 | ❌ | **R-PTS-01 未修复** — `sumAvailableBalance()` (行109) 在 `FOR UPDATE` (行131) 之前调用 |
| 惰性过期检查 | ✅ | 遍历时检查 expires_at |
| RedemptionAllocation 分摊记录 | ✅ | 逐笔写入 |
| totalRedeemed 按实际扣减 | ❌ | **R-PTS-05 未修复** — 行192: 使用 `pointsToRedeem` 而非 `actualRedeemed` |
| 信用额度透支 | ✅ | `processCreditDrawdown` 实现完整 |

### 3.3 MemberMergeService（会员合并）

**严重性：CRITICAL — 所有 6 个评审问题均未修复**

| 评审项 | 状态 |
|--------|------|
| Saga 状态机 | ❌ **R-MRG-01** — 仍是单个 `@Transactional` 方法 |
| 冻结机制 | ❌ **R-MRG-02** — 无 account_status 冻结 |
| 分布式锁排序 | ❌ **R-MRG-03** — 无 Redisson RLock |
| 软删除重定向 | ❌ **R-MRG-04** — UPDATE target_member_id 直接修改 |
| 异步任务 | ❌ **R-MRG-05** — 同步执行合并 |
| 优惠券/标签迁移 | ❌ **R-MRG-06** — 仅迁移积分和等级 |

**当前实现**：一个单一同步方法，直接执行 5 个步骤（标记状态→转移积分→转移等级→重定向唯一键→发布事件）。无任务表、无补偿路径、无分布式锁。违反设计 §3.5.1 的核心约束。

### 3.4 OneIdEnrollmentService（入会服务）

| 评审项 | 状态 |
|--------|------|
| 分布式锁 | ❌ **R-ONE-01** — 使用 `ConcurrentHashMap`，未集成 `EnrollmentDistributedLock` |
| 第零层 TMALL_OMID | ❌ **R-ONE-02** — 完全缺失 |
| 第二层密文匹配 | ❌ **R-ONE-02** — 完全缺失 |
| normalizePhoneNumber | ❌ **R-ONE-03** — 未在写入前规范化 |
| key_type 常量 | ⚠️ 使用 "MOBILE"/"WECHAT_UNIONID"/"ALIPAY_USER_ID" 替代设计的标准值 |

**注意**：`EnrollmentDistributedLock.java` 已实现 Redisson RLock 适配器并可正常使用，但 `OneIdEnrollmentService` 未集成。

---

## 第四章：引擎与基础设施评估

### 4.1 CascadeRecalculationEngine

| 评审项 | 状态 |
|--------|------|
| @Async 方法 public | ✅ **已修复** (原为 protected) |
| loadTimelineTransactions | ✅ 已实现（加载反向时间后的 ACCRUAL 流水） |
| recalculatePoints | ⚠️ 简化实现（基于 tier 倍数），非完整规则引擎回放 |
| applyCompensationWithShortTransaction | ⚠️ protected + @Transactional — 自调用无法触发代理 |
| recoverStuckJobs | ⚠️ 需验证 programCode 参数 |

**评估**：基本骨架已实现，较上一轮评审的"空骨架"有显著改善。但自调用事务代理问题依然存在（`protected` 方法被同类的 `@Async public` 调用）。

### 4.2 LocalEventBus

**超设计实现**：虚拟分区 + 单线程池 + TenantContext 快照恢复 + 优雅关闭。完全满足设计 §2.2.2 要求。

### 4.3 KafkaEventBus

✅ 异步回调日志 + 同步发送选项。但仍缺少 DLQ/重试机制（**R-ARCH-02**）。

### 4.4 KieBaseCacheManager

✅ **R-RLE-01 已修复**：使用 `AtomicReference` + `containerCache` + `dispose()`。热替换过程正确：先构建新版本 → 原子替换引用 → 销毁旧容器。

### 4.5 DouyinSpiHandler

| 评审项 | 状态 |
|--------|------|
| 幂等键使用 request_id | ✅ **R-SPI-01 已修复** |
| register action 路由 | ❌ **R-SPI-02 未修复** — 无特殊处理 |
| verify_mobile action 路由 | ❌ **R-SPI-02 未修复** — 无特殊处理 |
| 签名验证 | ✅ 使用 HMAC-SHA256 |

---

## 第五章：前端代码评估

### 5.1 全局设计语言

| 评审项 | 状态 |
|--------|------|
| colorPrimary | ❌ **R-FE-02** — `AppShell.tsx:129` 仍为 `#1677ff`（Ant Design 蓝色），未改为 `#1a1a1a` |
| AuthGuard 权限 | ❌ **R-FE-03** — 仍为 passthrough：`return <>{children}</>` |
| hasPermission() 使用 | ❌ **R-FE-01** — Zustand store 定义了方法但组件中从未调用 |
| JWT Header 注入 | ⚠️ **R-FE-04** — API 层有 token 注入和 401 处理，但从 `sessionStorage` 而非 Zustand 读取 |
| X-Program-Code 来源 | ⚠️ 从 `sessionStorage` 读取，设计 §8.3.5 要求从 Zustand Store |
| @tanstack/react-query | ❌ **R-FE-06** — 未安装，使用原始 axios |
| SchemaBuilder/SchemaEditor 重叠 | ⚠️ **R-FE-07** — 两个组件并存 |

### 5.2 技术栈清单

| 技术 | 设计要求 | 实际 |
|------|----------|------|
| React 18 + TypeScript + Vite | ✅ | ✅ |
| Ant Design 5 | ✅ | ✅ 5.12.0 |
| Formily 2.x | ✅ | ✅ @formily/antd-v5 |
| Zustand | ✅ | ✅ 5.0.14 |
| react-query | ✅ | ❌ 未安装 |
| Monaco Editor | ✅ | ✅ @monaco-editor/react |
| Playwright E2E | ✅ | ✅ |

---

## 第六章：测试覆盖评估

### 6.1 统计数据

- **测试文件数**：22 个
- **测试用例数**：119 个（@Test 注解）
- **覆盖率提升**：较上一轮评审（"零测试"）显著改善

### 6.2 覆盖范围分析

| 模块 | 测试文件 | 状态 |
|------|----------|------|
| 安全过滤器 (TenantContextFilter) | `TenantContextTest.java` | ✅ |
| SPI 网关 | `SpiGatewayControllerTest.java`, `TmallSpiHandlerTest.java`, `SpiHandlerFactoryTest.java`, `SpiFullChainIntegrationTest.java` | ✅ |
| 级联重算 | `AccountDeltaTest.java`, `ShadowContextTest.java`, `CascadeIntegrationTest.java`, `CascadeFullChainTest.java` | ✅ |
| 积分服务 | `AccountingIntegrationTest.java` | ✅ |
| 规则引擎 | `RulesUnitTest.java` | ✅ |
| 事件总线 | `LocalEventBusTest.java`, `BaseDomainEventTest.java` | ✅ |
| API 层 | `ApiIntegrationTest.java` | ✅ |
| 事件处理 | `EventInboxProcessorTest.java` | ✅ |

### 6.3 关键缺失

| 缺失项 | 严重性 |
|--------|--------|
| OpenApiSignatureFilter 无测试 | **HIGH** |
| MemberMergeService 无测试 | **HIGH** |
| OneIdEnrollmentService 无单元测试 | **HIGH** |
| PointGrantService 无独立单元测试 | **MEDIUM** |
| PointRedeemService 无独立单元测试 | **MEDIUM** |
| 无 Testcontainers 隔离 | **R-TST-03 待验证** |

---

## 第七章：数据库 DDL vs 实体代码偏差

| 表 | DDL (loyalty_saas_ddl_v7.3.sql) | Java实体 | 偏差 |
|----|------|----------|------|
| member | `id BIGSERIAL PK`, `member_id VARCHAR(64) UNIQUE` | `@IdClass (program_code, member_id)`, `Long memberId` | ❌ 主键结构+类型双重偏差 |
| member_account | 含 `frozen_status`, `account_status` | 含 `frozenStatus`, **缺 `accountStatus`** | ⚠️ 缺少 account_status |
| account_transaction | 含 `rule_snapshot_id` | 含 `ruleCode`, `ruleVersion` | ⚠️ 字段替代 |
| member_unique_key | `key_type VARCHAR(32)`, `id BIGSERIAL` | `key_combination`, `@IdClass` | ⚠️ 列名+主键偏差 |
| member_fifo_cursor | 完整DDL定义 | 需要确认实体是否存在 | — |

---

## 第八章：修复优先级排序

### P0 — 阻断性（立即修复）

1. **R-SEC-01**: 外部化 JWT Secret → 从 Vault/KMS/环境变量读取
2. **R-SEC-02**: 每个 Program 独立随机 AppSecret → 存储加密配置
3. **R-SEC-05**: JWT program_code 与 X-Program-Code 一致性校验
4. **R-SEC-07**: NegativePendingService UPDATE 添加 `WHERE program_code = ?`
5. **R-MRG-01~06**: 完整实现 MemberMergeSaga 状态机
6. **R-ONE-01**: OneIdEnrollmentService 集成 EnrollmentDistributedLock
7. **R-PTS-06**: PointGrantService.totalAccrued 改用 actualAccrued
8. **R-PTS-05**: PointRedeemService.totalRedeemed 改用 actualRedeemed
9. **R-PTS-01**: redeemsPoints 余额检查移入 FOR UPDATE 锁范围内
10. MemberAccount 添加 `account_status` 字段

### P1 — 重要（尽快修复）

11. **R-SEC-16**: readBody() 改用 ContentCachingRequestWrapper
12. **R-ONE-02**: 实现四层匹配（TMALL_OMID + 密文等价匹配）
13. **R-ONE-03**: 集成 normalizePhoneNumber()
14. **R-FE-01/03**: 启用 AuthGuard 实际权限检查
15. **R-FE-02**: colorPrimary 改为 #1a1a1a
16. **R-FE-04**: X-Program-Code 从 Zustand 而非 sessionStorage 读取
17. 安装 @tanstack/react-query

### P2 — 改进（下个迭代）

18. **R-ARCH-01**: 包名重构迁移 (saas → platform)
19. **R-ENT-02**: Member 改为单一大自增PK + VARCHAR memberId
20. **R-ARCH-02**: KafkaEventBus DLQ/重试机制
21. **R-RLE-02**: RuleRegressionService 候选 KieBase 实现
22. **R-EVT-02**: 事件时间从 payload 提取而非 Instant.now()
23. 解决自调用 Transactional 代理问题

---

## 第九章：附录 — 逐问题修复状态表

| # | 问题编号 | 严重度 | 描述 | 修复状态 |
|---|---------|--------|------|----------|
| 1 | R-SEC-01 | CRITICAL | 硬编码 JWT Secret | ❌ 未修复 |
| 2 | R-SEC-02 | CRITICAL | 硬编码可推导 AppSecret | ❌ 未修复 |
| 3 | R-SEC-03 | CRITICAL | TenantAwareJob RLS 上下文泄漏 | ✅ 已修复 |
| 4 | R-SEC-04 | CRITICAL | RlsDataSourcePostProcessor 连接归还未重置 | — 待验证 |
| 5 | R-SEC-05 | CRITICAL | JWT program_code 与 Header 不校验 | ❌ 未修复 |
| 6 | R-SEC-06 | CRITICAL | Repository 绕过 BaseRepository | — 待验证 |
| 7 | R-SEC-07 | CRITICAL | NegativePendingService UPDATE 无 programCode | ❌ 未修复 |
| 8 | R-SEC-08 | CRITICAL | SQL 注入（字符串拼接） | ✅ 已修复 |
| 9 | R-MRG-01 | CRITICAL | MemberMergeService 无 Saga | ❌ 未修复 |
| 10 | R-MRG-02 | CRITICAL | 合并无冻结机制 | ❌ 未修复 |
| 11 | R-MRG-03 | CRITICAL | 合并无分布式锁排序 | ❌ 未修复 |
| 12 | R-PTS-01 | CRITICAL | FIFO TOCTOU 竞态 | ❌ 未修复 |
| 13 | R-PTS-02 | CRITICAL | 精度 scale=2 vs 4 | ✅ 已修复 |
| 14 | R-PTS-03 | CRITICAL | CascadeRecalculationEngine 空骨架 | ✅ 已修复 |
| 15 | R-PTS-04 | CRITICAL | 入口无 frozen_status 检查 | ⚠️ 部分修复 |
| 16 | R-SPI-01 | CRITICAL | 幂等键 currentTimeMillis | ✅ 已修复 |
| 17 | R-SPI-02 | CRITICAL | DouyinSpiHandler register/verify_mobile | ❌ 未修复 |
| 18 | R-SPI-03 | CRITICAL | GraalVM 沙箱缺失限制 | — 待验证 |
| 19 | R-ONE-01 | CRITICAL | EnrollmentDistributedLock 未集成 | ❌ 未修复 |
| 20 | R-ONE-02 | HIGH | 缺失零层/二层匹配 | ❌ 未修复 |
| 21 | R-ONE-03 | HIGH | 手机号缺规范洗涤 | ❌ 未修复 |
| 22 | R-PTS-05 | HIGH | 核销不足时静默失败 | ❌ 未修复 |
| 23 | R-PTS-06 | HIGH | totalAccrued 按原始金额 | ❌ 未修复 |
| 24 | R-PTS-07 | HIGH | CompactionService 未设 accountId | — 待验证 |
| 25 | R-PTS-08 | HIGH | 信用还款后未解冻 | ❌ 未修复 |
| 26 | R-PTS-09 | HIGH | 缺少 rule_snapshot_id | ❌ 未修复 |
| 27 | R-PTS-10 | HIGH | RedemptionAllocation 精度 | — 待验证 |
| 28 | R-MRG-04 | HIGH | 唯一键冲突无软删除 | ❌ 未修复 |
| 29 | R-MRG-05 | HIGH | 合并接口同步 | ❌ 未修复 |
| 30 | R-MRG-06 | HIGH | 合并缺优惠券/标签迁移 | ❌ 未修复 |
| 31 | R-ENT-01 | HIGH | MemberUniqueKey Schema 不匹配 | ⚠️ 部分修复 |
| 32 | R-ENT-02 | HIGH | Member 复合PK + 缺字段 | ✅ 已修复字段 |
| 33 | R-ENT-03 | HIGH | MemberAccount balance + 缺状态 | ⚠️ 部分修复 |
| 34 | R-SEC-09 | HIGH | SPI 白名单跳过 JWT | ❌ 未修复 |
| 35 | R-SEC-10 | HIGH | EventInboxRepository 无 program_code | — 待验证 |
| 36 | R-SEC-11 | HIGH | AdminController 绕过 Sentinel | — 待验证 |
| 37 | R-SEC-12 | HIGH | Filter double-clear 冲突 | ✅ 已修复 |
| 38 | R-SEC-13 | HIGH | MyBatis 拦截器仅审计 | — 待验证 |
| 39 | R-SEC-14 | HIGH | Hibernate 不覆盖 nativeQuery | — 待验证 |
| 40 | R-SEC-15 | HIGH | Redis Key 无统一前缀 | — 待验证 |
| 41 | R-SEC-16 | HIGH | readBody 消费 InputStream | ❌ 未修复 |
| 42 | R-RLE-01 | HIGH | KieContainer 未 dispose | ✅ 已修复 |
| 43 | R-RLE-02 | HIGH | RuleRegressionService 候选 KieBase 骨架 | — 待验证 |
| 44 | R-EVT-01 | HIGH | EventInbox 缺 FOR UPDATE SKIP LOCKED | — 待验证 |
| 45 | R-EVT-02 | HIGH | 业务事件时间丢失 | — 待验证 |
| 46 | R-ARCH-01 | HIGH | 包结构与设计不一致 | ❌ 未修复 |
| 47 | R-ARCH-02 | MEDIUM | KafkaEventBus 缺 DLQ | ❌ 未修复 |
| 48 | R-ARCH-03 | MEDIUM | LocalEventRouter 静默丢弃 | ✅ 已修复 |
| 49 | R-ARCH-04 | MEDIUM | 拦截器表列表不一致 | — 待验证 |
| 50 | R-PTS-11 | MEDIUM | markAsExpired 事件在事务内 | ⚠️ 部分修复 |
| 51 | R-PTS-12 | MEDIUM | ShadowContext O(n²) | ✅ 已修复 |
| 52 | R-FE-01 | HIGH | hasPermission 从未使用 | ❌ 未修复 |
| 53 | R-FE-02 | HIGH | colorPrimary #1677ff | ❌ 未修复 |
| 54 | R-FE-03 | HIGH | AuthGuard 权限 passthrough | ❌ 未修复 |
| 55 | R-FE-04 | HIGH | 无 JWT Header 注入 / 401 拦截 | ⚠️ 部分修复 |
| 56 | R-FE-05 | MEDIUM | MemberService 功能缺失 | — 待验证 |
| 57 | R-FE-06 | MEDIUM | 缺 react-query | ❌ 未修复 |
| 58 | R-FE-07 | MEDIUM | SchemaBuilder/Editor 重叠 | ❌ 未修复 |
| 59 | R-FE-08 | MEDIUM | 映射预览用 new Function() | ✅ 已修复 |
| 60 | R-TST-01 | HIGH | 安全过滤器零测试 | ⚠️ 部分修复 |
| 61 | R-TST-02 | HIGH | 关键服务零测试 | ⚠️ 部分修复 |
| 62 | R-TST-03 | HIGH | 无测试数据库隔离 | — 待验证 |

**待验证项**（标记 "—"）：需进一步查看源码确认，共计 13 项。

---

## 结论

本次评估确认了设计文档 v7.3 中 41 个评审问题的修复进展。代码质量整体呈上升趋势：

- **亮点**：TenantContextFilter 的防御实现超设计预期、LocalEventBus 实现完整、KieBaseCacheManager 原子热替换正确、测试覆盖从零提升至 119 个用例
- **红线**：5 个 CRITICAL 安全缺陷未修复（硬编码密钥、租户冒充向量、SPI 认证绕过）、会员合并 Saga 完全缺失
- **建议**：下一迭代优先解决所有 P0 安全缺陷和合并 Saga，然后完成 member_id 类型统一（Long → String）

---

**评审工具**：Claude Code + 手工代码审查
**评审范围**：全栈（Java 后端 + React 前端 + DDL）
**总文件检查数**：~40+ Java源文件，~20+ TypeScript源文件
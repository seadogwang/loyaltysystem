## 完整修复
**任务**：修复 Loyalty SaaS 平台的全部阻断性缺陷及核心偏离问题，使代码达到可进入下一开发阶段的安全和功能基线。
**核心约束（必须遵守）**：
* **禁止**在 `member_account` 表中添加或保留任何 `balance`、`available_points` 等实时余额字段。**现有 `balance` 字段必须删除**。
* **所有积分余额查询**必须通过实时聚合 `account_transaction` 的 `remaining_amount` 完成。
* **并发扣减积分**时，只能锁定 `account_transaction` 的具体批次行（`SELECT ... FOR UPDATE`），**不得锁定 `member_account` 行**。
* `member_account` 仅存储风控参数（`overdraft_limit`, `credit_limit`, `credit_used`）、累计统计（`total_accrued`, `total_redeemed`, `total_expired`）、`frozen_status` 和 `version`。
***
## 第一部分：阻断性安全缺陷修复（P0）
### 1.1 移除所有硬编码密钥
**文件**：`MultiTenantRbacInterceptor.java`, `OpenApiSignatureFilter.java`, `JwtUtil.java`（如果存在）
**问题**：
* JWT Secret 硬编码为 `"loyalty-saas-jwt-secret-key-2026"`，可任意伪造 Token。
* OpenAPI AppSecret 硬编码为 `"openapi-secret-for-" + programCode`，可推导导致 HMAC 签名完全失效。
**修复指令**：
* 将 JWT Secret 改为从环境变量 `JWT_SECRET` 读取，若未设置则启动失败。
* 将 OpenAPI AppSecret 改为从数据库 `channel_adapter_config.auth_config` JSON 中读取每个渠道独立配置的 `app_secret`，不得使用可推导公式。
* 示例：
```java
@Value("${jwt.secret}")
private String jwtSecret; // 从 application.yml 的环境变量读取
```
并在 `application.yml` 中配置：`jwt.secret: ${JWT_SECRET:}`，缺失时抛异常。
### 1.2 修复 JWT 中 program_code 与 Header 不一致的租户冒充漏洞
**文件**：`MultiTenantRbacInterceptor.java`
**问题**：JWT 中声明的 `program_code` 与请求 Header `X-Program-Code` 不进行一致性校验，攻击者可伪造任意租户 Token。
**修复指令**：
* 在拦截器中解析 JWT 后，提取 `program_code` 声明，与 `TenantContext.get()`（来自 Header）进行比较，若不一致则返回 403 `ERR_TENANT_MISMATCH`。
### 1.3 修复 SPI 白名单跳过认证的问题
**文件**：`SecurityConfig.java` 或 `WebSecurityConfig.java`
**问题**：`/api/open/spi/**` 路径白名单跳过所有认证，但设计文档要求 SPI 使用 HMAC 签名验证，不应完全跳过。
**修复指令**：
* 移除该白名单，改为让 `OpenApiSignatureFilter` 处理所有 `/api/open/spi/**` 请求，验证签名失败时返回 401。
* 确保 `OpenApiSignatureFilter` 在过滤器链中位于 `TenantContextFilter` 之后（以便获取租户）。
### 1.4 修复 OpenApiSignatureFilter 与 TenantContextFilter 的 finally 冲突
**文件**：`OpenApiSignatureFilter.java`, `TenantContextFilter.java`
**问题**：`OpenApiSignatureFilter` 的 `finally` 块中调用了 `TenantContext.clear()`，导致 `TenantContextFilter` 的 `finally` 再次清除时可能已无上下文。
**修复指令**：
* `TenantContext` 的清除职责**仅由 `TenantContextFilter` 负责**。
* `OpenApiSignatureFilter` 中**不得调用** `TenantContext.clear()`，只能读取。
### 1.5 修复 3 个 Repository 绕过 BaseRepository 的查询哨兵
**文件**：`EventInboxRepository.java`, `ChannelAdapterConfigRepository.java`, `ProgramSchemaRepository.java`（及其他继承 `JpaRepository` 而非 `BaseRepository` 的接口）
**问题**：这些 Repository 直接继承 `JpaRepository`，导致 `findById()`、`findAll()` 等无租户过滤的方法可用。
**修复指令**：
* 全部改为继承 `BaseRepository<T, ID>`（该基类已禁用危险方法）。
* 如需特定查询，在接口中定义带 `programCode` 参数的方法。
***
## 第二部分：多租户与数据隔离修复（P0）
### 2.1 修复 RLS 上下文泄漏（来自上一报告）
**文件**：`TenantAwareJob.java`
**修复指令**：已在上一报告中给出，补充到本提示词。在 `finally` 中执行 `RESET app.current_program_code`，并将 `SET` 改为 `SET LOCAL`。
### 2.2 修复 NegativePendingService 跨租户 UPDATE
**文件**：`NegativePendingService.java`
**问题**：`clearPendingAndRestore` 方法中 UPDATE 无 `program_code` 过滤，可能误恢复其他租户的债务。
**修复指令**：
* 所有 SQL 更新必须包含 `WHERE program_code = ?`。
* 使用 `@Param` 或租户上下文传递。
### 2.3 修复 AdminController 无租户过滤的查询
**文件**：`AdminController.java`
**问题**：
* `listPrograms()` 使用 `SELECT p FROM Program p` 返回所有 Program。
* 多处使用 `programRepo.findById(id)` 绕过租户。
**修复指令**：
* `listPrograms()` 应只返回当前租户（从 `TenantContext.get()` 获取）对应的 Program，或者根据角色限制。
* 所有 `findById` 替换为 `baseRepository.findByIdAndProgramCode(id, tenant)`。
### 2.4 修复 SystemCacheService 硬编码租户
**文件**：`SystemCacheService.java`
**问题**：硬编码 `PROG001` 加载配置到内存，导致多租户数据混淆。
**修复指令**：
* 按 `programCode` 分别缓存，或从 `TenantContext` 动态获取当前租户后再加载。
### 2.5 修复 EventInboxRepository 跨租户查询
**文件**：`EventInboxRepository.java`
**问题**：5 个查询方法均无 `program_code` 过滤。
**修复指令**：
* 所有方法增加 `programCode` 参数，并在 JPQL 中添加 `WHERE programCode = :programCode`。
***
## 第三部分：会员域与 One-ID 修复（P0 + P1）
### 3.1 实现会员合并 Saga（替代当前同步单事务）
**文件**：`MemberMergeService.java`, 新增 `MemberMergeTask.java`, `MemberMergeOrchestrator.java`
**问题**：当前实现违反设计文档核心约束（单事务全量合并，无冻结/补偿/任务表）。
**修复指令**：
* 按照设计文档 §3.5 实现完整 Saga：
  * 创建 `member_merge_task` 表（状态机：CREATED → FREEZING → TRANSFER_POINTS → TRANSFER_IDENTITIES → TRANSFER_TIERS → COMPLETED）。
  * 合并接口改为异步：创建任务后发布事件，立即返回任务 ID。
  * 实现编排器 `MemberMergeOrchestrator`，每个步骤独立事务，失败时逆序补偿。
  * **冻结机制**：设置源和目标会员的 `member_account.frozen_status = 'FROZEN_ALL'`。
  * **积分迁移**：更新 `account_transaction.member_id` 为目标会员，同时修改 `operation_key` 避免唯一约束冲突（追加 `_merged_原会员ID`）。
  * **身份标识迁移**：对强标识（手机号、omid）执行软删除重定向（原值改为 `原值:merged:{timestamp}`，再插入指向目标会员的原始值）。
  * **等级迁移**：取两者最高等级，更新目标会员，记录 `tier_change_log`。
  * **补偿机制**：每个步骤实现回滚方法，失败时逆序调用。
* **分布式锁**：在创建任务前，按 `sourceId, targetId` 排序后获取两个 Redisson RLock，防止并发合并。
**SQL 迁移**（已有上一报告，此处补充）：
```sql
CREATE TABLE member_merge_task (
    id BIGSERIAL PRIMARY KEY,
    program_code VARCHAR(32) NOT NULL,
    source_member_id VARCHAR(64) NOT NULL,
    target_member_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    current_step VARCHAR(64),
    error_message TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(program_code, source_member_id)
);
```
### 3.2 集成 EnrollmentDistributedLock 替换 ConcurrentHashMap
**文件**：`OneIdEnrollmentService.java`, `EnrollmentDistributedLock.java`
**问题**：`OneIdEnrollmentService` 使用内存 `ConcurrentHashMap` 作为锁，多实例部署时完全失效。
**修复指令**：
* 删除 `ConcurrentHashMap` 相关代码。
* 注入 `EnrollmentDistributedLock`（已有 Redisson 实现），在其 `executeWithLock` 方法中使用 `RLock`。
* 确保锁的 key 包含 `programCode` 和 `normalizedIdentifier`（如手机号或 openId 的哈希）。
### 3.3 实现完整的 One-ID 多层匹配（含手机号规范化）
**文件**：`OneIdEnrollmentService.java`
**问题**：
* 缺少第零层（TMALL_OMID）匹配。
* 缺少第二层密文等价匹配。
* 手机号未规范化洗涤。
* 使用 `WECHAT_UNIONID` 等偏离设计的 key_type。
**修复指令**：
* 添加 `normalizePhoneNumber(String raw)`：去除非数字字符，保留纯数字。
* 实现匹配顺序：
  1. 若有 `omid`（TMALL_OMID），优先匹配 `member_unique_key` 中 `key_type='TMALL_OMID'`。
  2. 按渠道强标识匹配（`TMALL_OUID`、`JD_PIN`、`WECHAT_OPENID`、`DOUYIN_OPENID`）。
  3. 明文手机号匹配（规范化后的 `MOBILE_PLAIN`）。
  4. 密文等价匹配：对规范化手机号，按渠道加密算法计算（如天猫双重MD5），匹配 `TMALL_MOBILE_MD5` 等。
  5. 创建新会员。
* 匹配成功后，将本次所有标识写入 `member_unique_key`，注意 `is_strong`、`is_verified` 标识正确设置。
### 3.4 修复 Member 实体设计偏离
**文件**：`Member.java`
**问题**：使用复合主键 `(programCode, memberId)` 而非设计的独立 `memberId` 全局唯一；缺失 `name`, `gender`, `birthday` 字段。
**修复指令**：
* 将主键改为单一自增 `id`，`memberId` 改为普通字段并添加唯一索引。
* 添加 `name`, `gender`, `birthday` 字段。
* 更新所有引用该实体的代码。
**SQL 迁移**：
```sql
-- 先删除原有复合主键约束，重新设计
ALTER TABLE member DROP CONSTRAINT member_pkey;
ALTER TABLE member ADD COLUMN id BIGSERIAL PRIMARY KEY;
ALTER TABLE member ADD CONSTRAINT uk_member_id UNIQUE (member_id);
ALTER TABLE member ADD COLUMN name VARCHAR(100);
ALTER TABLE member ADD COLUMN gender VARCHAR(10);
ALTER TABLE member ADD COLUMN birthday DATE;
```
### 3.5 修复 MemberUniqueKey 实体缺失字段和分区
**文件**：`MemberUniqueKey.java`
**问题**：缺少 `is_strong`, `is_verified`, `verified_at`, `created_at`；未按设计分区。
**修复指令**：
* 添加上述字段。
* 提供分区表迁移脚本（按 `key_type` 分区），见第七部分。
### 3.6 删除 MemberAccount 中的 balance 字段
**文件**：`MemberAccount.java`, 相关 SQL
**问题**：设计文档明确禁止维护实时余额，但实体中存在 `balance` 字段。
**修复指令**：
* 删除 `balance` 字段及相关代码。
* 提供 SQL 迁移：`ALTER TABLE member_account DROP COLUMN balance;`
* 所有需要余额的地方改为调用 `transactionRepository.sumRemainingActive(...)` 实时聚合。
***
## 第四部分：积分账务引擎修复（P0 + P1）
### 4.1 修复 AccountTransaction 精度不匹配
**文件**：`AccountTransaction.java`, 数据库迁移
**问题**：代码中使用 `SCALE=4` 但数据库列定义为 `NUMERIC(20,2)`，导致截断。
**修复指令**：
* 将实体字段的 `@Column(precision=18, scale=4)` 显式指定。
* 执行 SQL 修改列类型：`ALTER TABLE account_transaction ALTER COLUMN amount TYPE NUMERIC(18,4);` 同样处理 `remaining_amount`。
### 4.2 添加 frozen_status 拦截逻辑
**文件**：`PointGrantService.java`, `PointRedeemService.java`
**问题**：兑换和发放入口未检查 `member_account.frozen_status`。
**修复指令**：
* 在 `redeemPoints` 方法开头查询 `MemberAccount`，若 `frozen_status IN ('FROZEN_REDEMPTION', 'FROZEN_ALL')` 则抛出 `ERR_ACCOUNT_FROZEN`。
* 在 `grantPoints` 开头，若 `frozen_status = 'FROZEN_ALL'` 则拒绝入账。
### 4.3 修复 PointRedeemService 中的 TOCTOU 竞态（上一报告已覆盖，整合）
**文件**：`PointRedeemService.java`
**修复指令**：已在上一报告 2.1 中给出，确保余额检查在锁定批次后进行。
### 4.4 实现 FIFO 游标优化
**文件**：`PointRedeemService.java`, 新增 `MemberFifoCursor.java`
**问题**：每次兑换扫描全部活跃批次，未使用游标加速。
**修复指令**：
* 创建 `member_fifo_cursor` 表（设计文档 §2.4.3 已定义）。
* 在 `redeemPoints` 中，先获取上次消费到的 `last_tx_id`，然后查询 `id > lastCursorId` 的活跃批次，每次限定 30 条。
* 处理完一批后，更新游标为最后一个处理完的批次 ID。
### 4.5 修复 NegativePendingService 错误的状态设置
**文件**：`NegativePendingService.java`
**问题**：透支后设置 `member.status='SUSPENDED'` 而非 `member_account.frozen_status='FROZEN_REDEMPTION'`。
**修复指令**：
* 改为更新 `member_account.frozen_status = 'FROZEN_REDEMPTION'`。
* 恢复时同样更新该字段。
### 4.6 实现级联重算引擎核心逻辑
**文件**：`CascadeRecalculationEngine.java`
**问题**：`loadTimelineTransactions` 返回空列表，`calculateDelta` 返回零差额。
**修复指令**：
* 实现 `loadTimelineTransactions(String memberId, LocalDateTime since)`：从 `account_transaction` 查询 `created_at >= since` 的所有 `ACCRUAL` 和 `REDEMPTION` 记录（关联规则快照）。
* 实现 `calculateDelta`：对比影子账户重放结果与真实账户，返回积分差额和等级变化。
* 实现 `applyCompensationWithShortTransaction`：使用短事务执行差额调整（扣回多发的积分，降级等）。
* 添加重试和死信处理（`recoverStuckJobs` 需传入租户）。
***
## 第五部分：规则引擎 & SPI 网关修复（P0 + P1）
### 5.1 修复 KieContainer 内存泄漏
**文件**：`KieBaseCacheManager.java`
**问题**：热替换时未 dispose 旧的 `KieContainer`。
**修复指令**：
```java
KieContainer oldContainer = ...;
if (oldContainer != null) {
    oldContainer.dispose();
}
```
### 5.2 修复 SPI 幂等键使用 currentTimeMillis
**文件**：所有 `*SpiHandler.java`
**问题**：重试请求会生成不同幂等键，幂等保护失效。
**修复指令**：
* 使用渠道请求中的唯一交易号作为幂等键：天猫用 `trade.tid`，京东用 `orderId`，抖音用 `event_id`，微信用 `OutTradeNo`。
### 5.3 实现 DouyinSpiHandler 的特殊处理
**文件**：`DouyinSpiHandler.java`
**问题**：`register` 和 `verify_mobile` 动作未实现，One-ID 匹配缺失。
**修复指令**：
* 实现 `handleAction` 中对 `register` 和 `verify_mobile` 的路由。
* `register`：使用 openId + 掩码手机号创建会员（掩码不可匹配，仅存储）。
* `verify_mobile`：验证完整手机号后，规范化并写入 `MOBILE_PLAIN`，触发跨渠道合并（若冲突）。
### 5.4 补全 GraalVM 沙箱缺失的约束
**文件**：`ScriptingTransformer.java`
**问题**：缺少 `allowCreateThread(false)` 和 `allowValueSharing(false)`。
**修复指令**：
```java
Context.newBuilder("js")
    .allowAllAccess(false)
    .allowHostAccess(HostAccess.NONE)
    .allowIO(false)
    .allowNativeAccess(false)
    .allowCreateThread(false)      // 新增
    .allowValueSharing(false)      // 新增
    .build();
```
### 5.5 修复 SPI 返回 HTTP 错误码的问题
**文件**：`SpiGatewayController.java`
**问题**：设计要求始终返回 HTTP 200，但当前可能返回 401/404。
**修复指令**：
* 所有异常捕获后，构建符合渠道要求的错误响应体，包装为 HTTP 200 返回。
* 使用 `handler.buildErrorResponse(e)` 统一处理。
***
## 第六部分：前端修复（P0 + P1）
### 6.1 移除全局蓝色主题，改为黑白色系
**文件**：`App.tsx` 或 `AppShell.tsx`
**问题**：`ConfigProvider` 设置了 `colorPrimary: '#1677ff'`，覆盖了设计要求的 #1a1a1a。
**修复指令**：
* 删除 `colorPrimary` 配置，或显式设置为 `#1a1a1a`。
* 所有按钮、边框、文字颜色按设计规范（参见设计文档第八章）使用 CSS 变量或全局样式覆盖。
### 6.2 实现 AuthGuard 真实权限检查
**文件**：`AuthGuard.tsx` 或路由守卫
**问题**：当前开发阶段放行所有权限。
**修复指令**：
* 从 Zustand store 获取用户权限列表和角色。
* 根据路由 `meta.permission` 检查是否有权限，无权限时重定向到 403 页面。
* 在菜单和按钮级别使用 `hasPermission` 控制显示。
### 6.3 添加 JWT Authorization Header 注入和 401 拦截
**文件**：`src/services/api/index.ts`
**问题**：无 JWT 注入，无 401 响应拦截。
**修复指令**：
* 在 axios 请求拦截器中添加 `Authorization: Bearer ${store.getState().auth.token}`。
* 在响应拦截器中，若状态码为 401，清除 token 并跳转到登录页。
### 6.4 补全会员工作台缺失功能
**文件**：`src/pages/members/MemberService.tsx`
**缺失功能**：
* 搜索框下方“最近查询”下拉（localStorage 存储最近 10 条）。
* 空状态时显示“创建新会员”按钮。
* 操作按钮：信息修改、合并会员需有 handler。
* 交易流水：类型多选过滤、时间范围过滤、排序。
* 渠道绑定表格：完整列（标识类型、标识值、是否强标识、是否验证、验证时间、操作）。
* 调整等级模态框：增加生效时间字段，降级需二次确认 Popconfirm。
* 冻结按钮：增加 Popconfirm 二次确认。
* 合并会员：实现两步向导（选择目标会员、确认迁移内容）。
**修复指令**：按设计文档第八章的规格逐项实现。
### 6.5 统一 Schema 编辑器实现
**文件**：`SchemaBuilder.tsx`, `SchemaEditor.tsx`
**问题**：两套实现并存，且未使用 JsonSea。
**修复指令**：
* 删除 `SchemaBuilder.tsx`，保留 `SchemaEditor.tsx` 作为唯一入口。
* 集成 `jsonsea` npm 包替代自定义树编辑器。
* 实现与后端的保存草稿、发布、版本历史抽屉等 API 调用。
### 6.6 修复映射配置器使用后端沙箱
**文件**：`MappingConfig.tsx`
**问题**：测试映射时使用客户端 `new Function()` 而非调用后端 GraalVM。
**修复指令**：
* 创建后端 API `POST /api/admin/channels/{channel}/test`，接收源 JSON 和映射脚本，返回转换结果。
* 前端调用该 API 进行测试，删除客户端直接 eval。
***
## 第七部分：测试与质量（P0）
### 7.1 为核心安全过滤器补充测试
**文件**：新建 `OpenApiSignatureFilterTest.java`, `MultiTenantRbacInterceptorTest.java`
**修复指令**：
* 测试 HMAC 签名正确/错误场景。
* 测试 JWT 中 `program_code` 与 Header 不一致的场景。
* 使用 MockMvc + Testcontainers 模拟真实请求。
### 7.2 为会员合并和 One-ID 入会添加集成测试
**文件**：`MemberMergeServiceIntegrationTest.java`, `OneIdEnrollmentServiceIntegrationTest.java`
**修复指令**：
* 测试正常合并流程，验证积分、标识、等级正确迁移。
* 测试并发入会，验证分布式锁效果。
* 测试手机号规范化后匹配。
### 7.3 配置 Testcontainers 隔离测试数据库
**文件**：`src/test/resources/application-test.yml`, 抽象基类
**修复指令**：使用 `PostgreSQLContainer` 替代直连开发库，所有 `@SpringBootTest` 继承该基类。
### 7.4 添加积分账务并发测试
**文件**：`PointRedeemServiceConcurrencyTest.java`
**修复指令**：启动 10 个线程同时兑换同一会员，验证无超卖，最终余额正确。
### 7.5 配置 JaCoCo 并设置最低覆盖率
**文件**：`pom.xml`
**修复指令**：添加 JaCoCo 插件，要求核心模块（domain.points, domain.member, security）行覆盖率不低于 80%。
***
## 第八部分：数据库迁移脚本汇总
将所有 DDL 变更集中到 `V1.2__critical_fixes.sql`：
```sql
-- 1. 精度修复
ALTER TABLE account_transaction ALTER COLUMN amount TYPE NUMERIC(18,4);
ALTER TABLE account_transaction ALTER COLUMN remaining_amount TYPE NUMERIC(18,4);
ALTER TABLE redemption_allocation ALTER COLUMN allocated_amount TYPE NUMERIC(18,4);
-- 2. 删除 member_account.balance
ALTER TABLE member_account DROP COLUMN IF EXISTS balance;
-- 3. 添加 frozen_status
ALTER TABLE member_account ADD COLUMN frozen_status VARCHAR(16) DEFAULT 'ACTIVE';
-- 4. 会员主表重构
ALTER TABLE member DROP CONSTRAINT IF EXISTS member_pkey;
ALTER TABLE member ADD COLUMN id BIGSERIAL PRIMARY KEY;
ALTER TABLE member ADD CONSTRAINT uk_member_id UNIQUE (member_id);
ALTER TABLE member ADD COLUMN name VARCHAR(100);
ALTER TABLE member ADD COLUMN gender VARCHAR(10);
ALTER TABLE member ADD COLUMN birthday DATE;
-- 5. member_unique_key 补充字段
ALTER TABLE member_unique_key ADD COLUMN is_strong BOOLEAN DEFAULT true;
ALTER TABLE member_unique_key ADD COLUMN is_verified BOOLEAN DEFAULT false;
ALTER TABLE member_unique_key ADD COLUMN verified_at TIMESTAMPTZ;
ALTER TABLE member_unique_key ADD COLUMN created_at TIMESTAMPTZ DEFAULT NOW();
-- 6. 分区（按需，先创建主表分区结构）
-- 注意：如果表已存在数据，分区迁移较复杂，可暂时不分区，但新增分区表结构
-- 这里仅给出示例，实际需根据数据量决定
-- CREATE TABLE member_unique_key_2026 PARTITION OF member_unique_key FOR VALUES IN ('MOBILE_PLAIN');
-- 7. FIFO 游标表
CREATE TABLE IF NOT EXISTS member_fifo_cursor (
    program_code VARCHAR(32) NOT NULL,
    member_id VARCHAR(64) NOT NULL,
    account_type VARCHAR(50) NOT NULL,
    last_tx_id BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (program_code, member_id, account_type)
);
-- 8. 会员合并任务表
CREATE TABLE IF NOT EXISTS member_merge_task (
    id BIGSERIAL PRIMARY KEY,
    program_code VARCHAR(32) NOT NULL,
    source_member_id VARCHAR(64) NOT NULL,
    target_member_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    current_step VARCHAR(64),
    error_message TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(program_code, source_member_id)
);
```
***
## 执行顺序建议
请 AI 按照以下顺序逐项修复，每完成一项运行相关测试：
1. **安全阻断性修复**（1.1~1.5）—— 最高优先级，必须立即完成
2. **多租户隔离修复**（2.1~2.5）
3. **会员域核心修复**（3.1~3.6）—— Saga、One-ID、实体修正
4. **积分账务引擎修复**（4.1~4.6）—— 精度、冻结、竞态、级联重算
5. **规则引擎 & SPI 修复**（5.1~5.5）
6. **前端修复**（6.1~6.6）
7. **测试补充**（7.1~7.5）
**最终要求**：
* 所有修改不得在 `member_account` 中引入或保留 `balance` 字段。
* 所有积分余额查询必须实时聚合 `account_transaction`。
* 所有 SQL 操作必须包含 `program_code` 过滤（或在 RLS 保护下确保安全）。
* 提交前执行全部单元测试和集成测试，确保通过。
提示词结束。请 AI 开始执行。

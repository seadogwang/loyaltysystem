# 基于AI的下一代全渠道忠诚度管理SaaS平台
## 完整设计文档 v7.0（金融级·多租户强隔离·全渠道版）
**版本**：7.0
**状态**：团队评审与开发基线版
**日期**：2026-06-04
***
## 第一章：引言与设计哲学
### 1.1 背景与战略愿景
忠诚度管理系统（Loyalty Management）是企业全链路 CRM 体系的核心中台。在当今异构渠道（电商、私域、线下）并存的格局下，传统的积分系统普遍陷入了"规则死锁"与"渠道割裂"的困境。
本项目的战略目标是构建一套"**配置驱动、金融级一致性、AI 原生防御**"的下一代 SaaS 底座。我们不仅要解决"发分与核销"的业务需求，更要构建一套能够支撑企业在未来 3-5 年内，应对高并发交易、复杂合规要求与敏捷运营诉求的技术护城河。
### 1.2 设计哲学
在架构演进过程中，我们遵循以下四项核心设计原则：
* **防御性编程 (Defensive SaaS)**：数据穿透是 SaaS 系统的生死线。我们从基础设施层面（数据库层租户过滤）到应用层（租户上下文强制持有）构建了四重隔离防线，确保租户间逻辑绝对隔离。
* **状态机至上 (State-Machine Driven)**：将积分流转、退款重算、身份合并等复杂逻辑建模为状态转换。一切业务结果通过事件溯源（Event Sourcing）而非仅仅是修改余额，确保"账户余额"在任何时间点都是可溯源、可审计的。
* **性能与一致性的动态平衡**：放弃低效的全局分布式锁，引入"瀑布流冲抵机制"与"异步无锁化补偿"，在保证业务逻辑正确的前提下，最大程度优化接口响应性能。
* **代码与配置的解耦 (Schema-Driven)**：前端界面、规则判定逻辑与持久化模型均由元数据（Metadata/JSON Schema）驱动。系统不仅是功能的容器，更是业务规则配置的执行引擎。
### 1.3 核心专业术语表
| 术语                 | 定义说明                     | 伪代码/语义约束                                     |
| ------------------ | ------------------------ | -------------------------------------------- |
| **Program**        | 业务租户单元，积分计算的原子空间         | `ProgramCode == Tenant_Boundary`             |
| **One-ID**         | 全渠道身份识别后的唯一会员主键          | `One-ID = UniqueKeyMapping(channel, openId)` |
| **EventFact**      | 标准化领域事件，所有入账动作必须先归一化为此格式 | `Fact = Standardize(RawRequest)`             |
| **FIFO-Batch**     | 积分核销遵循严格的先进先出原则          | `Redemption = FIFO(AccrualBatches)`          |
| **Shadow Sandbox** | 规则变更的预验证环境               | `Sandbox = Baseline + Candidate_Rule`        |
### 1.4 架构评审边界
* **重点评审项**：多租户隔离的严密性、逆向级联补偿机制的吞吐量、规则引擎的防错机制。
* **非评审范围**：不涉及底层网络基础设施（Kubernetes 部署细节、CDN 配置）、不涉及 C 端页面渲染的前端样式细节（仅涉及动态 Schema 协议）。
### 1.5 附录：如何阅读这份文档
本技术文档采用了"**伪代码先行**"的叙述风格。每一项核心业务逻辑均配有对应的伪代码。建议评审人重点关注伪代码中对于"**事务边界**"、"**并发处理（锁）**"以及"**租户上下文安全**"的处理方式。这份文档不仅是逻辑说明，更是后续代码开发的**契约规范**。
***
## 第二章：系统总体架构与轻量化环境设计
### 2.1 系统逻辑架构分层
系统自上而下分为三层：
* **API / 接入层 (Gateway & API Layer)**
  * 管理后台 API：面向商户运营人员，基于 JWT 认证的 RESTful API
  * 商户 Open API：面向商户自有前端，基于 HMAC-SHA256 签名
  * Unified SPI Gateway：专为天猫、京东、抖音等二方开放平台设计的反向 Webhook 网关
* **核心业务与领域引擎层 (Core Domain Engines)**
  * 动态实体引擎：结合 JSONB 和动态 Schema，实现多行业会员和交易扩展属性管理
  * 规则引擎与沙箱 (Drools & Sandbox)：无状态的 Drools 8 执行环境
  * 异构映射引擎 (GraalVM Scripting)：将第三方异构 API 数据转换为标准内部事件
  * 核销与冲抵引擎 (Offset Engine)：处理积分的 FIFO 消耗及透支冲抵
* **基础设施与防穿透层 (Infrastructure & Defense Layer)**
  * 数据库层：PostgreSQL 15，依托全局 ORM 拦截器实现 `program_code` 级数据穿透防御
  * 缓存与锁：Redis Cluster，Key 统一强制追加租户前缀隔离
  * 事件总线 (EventBridge)：屏蔽底层消息队列物理实现，提供统一领域事件派发接口
### 2.2 开发轻量化与消息队列环境隔离
**痛点背景**：企业级 SaaS 强依赖 Kafka 集群，开发人员在本地启动全套 Kafka 环境占用大量内存且配置繁琐。
**架构解法**：引入 `EventBridge` 抽象总线，利用 Spring `@Profile` 机制实现环境无缝切换。业务代码禁止直接使用 `KafkaTemplate`。
#### 2.2.1 统一事件总线接口契约
```java
public interface EventBridge {
    void publish(String topic, String partitionKey, BaseDomainEvent event);
}
```
#### 2.2.2 Dev环境：本地内存队列实现
```java
@Component
@Profile("dev")
@Slf4j
public class LocalEventBus implements EventBridge {
    private final int VIRTUAL_PARTITIONS = 8;
    private final ExecutorService[] partitionExecutors;
    public LocalEventBus() {
        this.partitionExecutors = new ExecutorService[VIRTUAL_PARTITIONS];
        for (int i = 0; i < VIRTUAL_PARTITIONS; i++) {
            this.partitionExecutors[i] = Executors.newSingleThreadExecutor(
                new ThreadFactoryBuilder().setNameFormat("local-mq-partition-" + i + "-%d").build()
            );
        }
    }
    @Override
    public void publish(String topic, String partitionKey, BaseDomainEvent event) {
        int partition = Math.abs(partitionKey.hashCode()) % VIRTUAL_PARTITIONS;
        partitionExecutors[partition].submit(() -> {
            try {
                log.info("[LocalMQ] 消费分区 {}: Topic={}, Key={}", partition, topic, partitionKey);
                LocalEventRouter.route(topic, event);
            } catch (Exception e) {
                log.error("[LocalMQ] 消费异常", e);
            }
        });
    }
}
```
#### 2.2.3 Test/Prod环境：Kafka 集群实现
```java
@Component
@Profile({"test", "prod"})
public class KafkaEventBus implements EventBridge {
    private final KafkaTemplate<String, Object> kafkaTemplate;
    public KafkaEventBus(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }
    @Override
    public void publish(String topic, String partitionKey, BaseDomainEvent event) {
        kafkaTemplate.send(topic, partitionKey, event);
    }
}
```
### 2.3 核心技术栈选型
* **核心框架**：Java 17 + Spring Boot 3.x
* **规则计算层**：Drools 8 (KIE API) + GraalVM Polyglot
* **数据持久层**：PostgreSQL 15（深度使用 JSONB + GIN 索引），Spring Data JPA 或 MyBatis-Plus
* **缓存与分布锁**：Redis 6.x + Redisson
* **前端框架**：React 18 + TypeScript + Vite + Ant Design 5 + Formily 2.x
* **画布引擎**：ChartDB（开源 React 组件）
* **状态管理**：Zustand
* **样式方案**：纯白底色 + 黑线条风格，自定义 SVG 图标
***
## 第三章：核心业务领域：Program与会员域
### 3.1 Program 管理与租户边界
**Program（忠诚度计划）** 是本平台的核心业务租户单元。一个企业实体下可以创建多个互相隔离的 Program。
* **隔离原则**：所有会员、积分、规则、渠道配置的生命周期和数据权限绝对从属于唯一的 `program_code`
* **配置存储**：Program 的非结构化配置统一以 JSON 结构存储于 `program.config_json`，支持版本历史追溯
### 3.2 核心实体的静态与动态边界
平台对核心实体采取"**核心强依赖字段 + 动态扩展字段 (JSONB)**"的双轨模型。
**研发与 AI 编码纪律**：严禁随意在数据库表和 Java Entity 中增加业务字段。
#### 3.2.1 会员实体边界 (Member)
* **静态字段**（系统路由和核心引擎强依赖）：`id`、`program_code`、`member_id`（雪花算法生成）、`status`、`tier_code`、`schema_version`、`created_at`
* **动态字段**：`ext_attributes`（PostgreSQL JSONB），前端采用 Formily 配合 JSON Schema 动态渲染
```java
@Entity
@Table(name = "member")
public class Member {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "program_code", nullable = false)
    private String programCode;
    @Column(name = "member_id", nullable = false, unique = true)
    private String memberId;
    @Column(name = "tier_code")
    private String tierCode;
    @Column(name = "status", nullable = false)
    private String status;
    @Column(name = "schema_version")
    private String schemaVersion;
    @Type(type = "jsonb")
    @Column(name = "ext_attributes", columnDefinition = "jsonb")
    private Map<String, Object> extAttributes = new HashMap<>();
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
```
#### 3.2.2 交易事件边界 (TransactionEvent)
* **静态字段**：`event_id`、`member_id`、`event_type`（ORDER/ENROLL/CUSTOM）、`channel`、`event_time`、`idempotent_key`
* **动态字段**：`payload`（JSONB，存储 order_amount、pay_type 等扩展数据）
#### 3.2.3 动态属性写入规范（Schema 版本下沉）
每次创建或更新会员的 ext_attributes 时，必须同步写入当前生效的 Schema 版本号：
```java
public void saveMemberExtAttributes(String memberId, Map<String, Object> extAttributes) {
    Member member = memberRepo.findByMemberId(memberId);
    String currentSchemaVersion = schemaService.getCurrentVersion(member.getProgramCode(), "MEMBER");
    // 双写：独立字段 + JSON 内部元字段
    extAttributes.put("_schema_version", currentSchemaVersion);
    member.setExtAttributes(extAttributes);
    member.setSchemaVersion(currentSchemaVersion);
    memberRepo.save(member);
    // 检查强标识字段，触发隐式合并检查
    List<String> strongFields = schemaService.getStrongIdentifierFields(member.getProgramCode(), "MEMBER");
    for (String fieldKey : strongFields) {
        if (extAttributes.containsKey(fieldKey)) {
            String newValue = String.valueOf(extAttributes.get(fieldKey));
            eventBridge.publish("loyalty-identity-events", memberId,
                new StrongIdentifierUpdatedEvent(memberId, fieldKey, newValue));
        }
    }
}
```
### 3.3 动态属性与规则引擎的桥接
**解法**：在数据进入 `KieSession` 前，通过**事实包装器 (Fact Wrapper)** 进行转换。
#### 3.3.1 包装器设计规范
```java
public class MemberFact {
    private String memberId;
    private String tierCode;
    private Map<String, Object> extAttributes;
    public String getExtString(String key) {
        return extAttributes.containsKey(key) ? String.valueOf(extAttributes.get(key)) : null;
    }
    public Double getExtNumber(String key) {
        if (!extAttributes.containsKey(key)) return 0.0;
        return Double.valueOf(String.valueOf(extAttributes.get(key)));
    }
}
public class EventFact {
    private String eventType;
    private String memberId;
    private String channel;
    private LocalDateTime eventTime;
    private String idempotentKey;
    private Map<String, Object> payload;
    public Double getPayloadNumber(String key) { /* ... */ }
    public String getPayloadString(String key) { /* ... */ }
}
```
#### 3.3.2 DRL 规则模板规范
```drools
rule "Dynamic_Attribute_Reward_Rule"
when
    $event : EventFact( eventType == "ORDER", getPayloadNumber("order_amount") > 100 )
    $member : MemberFact( memberId == $event.memberId, getExtNumber("shoe_size") > 40 )
then
    ActionCollector.awardPoints($event.getEventId(), 50, "ORDER_REWARD").execute(drools);
end
```
### 3.4 全渠道身份识别与会员合并
#### 3.4.1 标识强度分级
| 标识类型            | 强度    | 说明        |
| --------------- | ----- | --------- |
| 手机号（已验证）        | ★★★★★ | 核心强标识     |
| 身份证号            | ★★★★★ | 极少场景可获取   |
| 邮箱（已验证）         | ★★★★  | 跨渠道较高     |
| UnionID（微信开放平台） | ★★★★  | 同一开放平台下唯一 |
| OpenID（单个公众号）   | ★★    | 仅单应用内唯一   |
| 设备 ID           | ★     | 仅辅助       |
#### 3.4.2 分层匹配流程
```text
入会请求到达 → 提取所有可用标识
  ↓
第一层：强标识匹配（手机号/UnionID/邮箱）
  → 命中？→ 绑定渠道，复用 member_id
  → 未命中？→ 进入第二层
  ↓
第二层：弱标识匹配（OpenID/设备ID）
  → 命中？→ 绑定渠道，复用 member_id
  → 未命中？→ 创建新 member_id
```
#### 3.4.3 One-ID 建立的三个时机
| 时机       | 触发条件                            | 行为                                 |
| -------- | ------------------------------- | ---------------------------------- |
| 入会时      | 请求包含强标识                         | 立即匹配，命中则复用，未命中则创建                  |
| 绑定新标识时   | 已有 member_id 的用户新增渠道/手机号       | 写入 member_unique_key，若冲突触发合并     |
| 更新强标识字段时 | 用户更新 ext_attributes 中标记为强标识的字段 | 异步检查 member_unique_key，若冲突生成合并任务 |
#### 3.4.4 辅助唯一键表
```sql
CREATE TABLE member_unique_key (
    id BIGSERIAL PRIMARY KEY,
    program_code VARCHAR(32) NOT NULL,
    key_type VARCHAR(32) NOT NULL,
    key_value VARCHAR(128) NOT NULL,
    target_member_id VARCHAR(64) NOT NULL,
    is_strong BOOLEAN DEFAULT true,
    verified_at TIMESTAMPTZ,
    UNIQUE (program_code, key_type, key_value)
);
```
#### 3.4.5 并发入会与合并策略
```java
public void processEnrollment(EnrollmentRequest request) {
    String programCode = request.getProgramCode();
    String mobile = decrypt(request.getMobile());
    String lockKey = "loyalty:" + programCode + ":enroll:" + HashUtil.md5(mobile);
    RLock lock = redissonClient.getLock(lockKey);
    try {
        lock.lock(5, TimeUnit.SECONDS);
        // 强标识匹配
        Long existingMemberId = uniqueKeyRepo.findMemberId(programCode, "MOBILE", mobile);
        if (existingMemberId != null) {
            bindNewChannel(existingMemberId, request.getChannel(), request.getChannelOpenId());
            return;
        }
        // 弱标识匹配
        existingMemberId = uniqueKeyRepo.findMemberId(programCode, request.getChannel(), request.getChannelOpenId());
        if (existingMemberId != null) {
            bindNewIdentifier(existingMemberId, "MOBILE", mobile);
            return;
        }
        // 新会员注册
        Long newMemberId = generateSnowflakeId();
        Member newMember = new Member(programCode, newMemberId, request.getExtAttributes());
        memberRepo.save(newMember);
        try {
            uniqueKeyRepo.save(new UniqueKey(programCode, "MOBILE", mobile, newMemberId, true));
            uniqueKeyRepo.save(new UniqueKey(programCode, request.getChannel(), request.getChannelOpenId(), newMemberId, false));
        } catch (DataIntegrityViolationException e) {
            Long existingId = uniqueKeyRepo.findMemberId(programCode, "MOBILE", mobile);
            bindNewChannel(existingId, request.getChannel(), request.getChannelOpenId());
            memberRepo.deleteById(newMemberId);
            return;
        }
        eventBridge.publish("loyalty-events", newMemberId.toString(), new MemberEnrolledEvent(newMemberId));
    } finally {
        lock.unlock();
    }
}
```
#### 3.4.6 显式合并与资产转移
发现两个 `member_id` 属于同一个人时：
* 选取主账号（注册早或等级高），另一个状态置为 `MERGED`
* 积分资产累加，等级取最高
* `member_unique_key` 记录全部重定向到主账号
***
## 第四章：积分、等级与资产隔离域
### 4.1 积分类型字典
系统中严禁硬编码积分名称，所有资产通过 `point_type_definition` 定义。
平台初始化时，默认向每个 Program 注入以下三种隔离的资产类型：
| 类型代码     | 类型名称  | 用途                  | is_redeemable | is_tier_calc | is_transferable | allow_negative |
| -------- | ----- | ------------------- | -------------- | -------------- | ---------------- | --------------- |
| `REWARD` | 消费积分  | 用户交易累积，可兑换礼品        | true           | false          | true             | false           |
| `TIER`   | 等级成长值 | 只用于计算会员等级，不可消费      | false          | true           | false            | false           |
| `CREDIT` | 授信积分  | 系统授予的信用额度，可透支使用，需归还 | true           | false          | false            | true            |
**授信积分的本质**：它是一种"可使用的负债"。用户兑换时若自有积分不足，可动用信用积分，此时 `credit_used` 增加，之后用户获得新积分时优先偿还信用欠款。
#### 4.1.1 核心风控开关
* `is_redeemable`：可否用于兑换
* `is_tier_calc`：是否计入等级计算
* `is_transferable`：可否转赠
* `allow_negative`：是否允许余额为负
#### 4.1.2 积分流水表
```sql
CREATE TABLE account_transaction (
    id BIGSERIAL,
    program_code VARCHAR(32) NOT NULL,
    member_id VARCHAR(64) NOT NULL,
    operation_key VARCHAR(100) NOT NULL,
    transaction_type VARCHAR(32) NOT NULL,
    amount NUMERIC(18, 4) NOT NULL,
    remaining_amount NUMERIC(18, 4),
    expires_at TIMESTAMPTZ,
    rule_id VARCHAR(64),
    rule_snapshot_id VARCHAR(64),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);
```
* `remaining_amount`：该笔流水还未被消耗或过期的额度
* `status = 'ACTIVE'` 且 `remaining_amount > 0` 且 `expires_at > NOW()` 三条件同时满足，才属于"可用积分"
**积分查询与汇总（实时计算）**：
```sql
SELECT COALESCE(SUM(remaining_amount), 0)
FROM account_transaction
WHERE member_id = ? AND program_code = ? AND account_type = ?
  AND status = 'ACTIVE' AND remaining_amount > 0 AND expires_at > NOW();
```
### 4.2 被动透支与主动信用冲抵引擎
#### 4.2.1 账户数据结构
`member_account` 表不维护实时余额，仅存储风控参数和累计统计：
* `overdraft_limit`：被动透支底线
* `credit_limit`：主动授信总额
* `credit_used`：已使用的信用额度
* `total_accrued`、`total_redeemed`、`total_expired`：累计统计（只增不减）
#### 4.2.2 瀑布流冲抵伪代码（跨账户版）
```java
@Transactional
public void grantPoints(String programCode, String memberId, String accountType,
                         BigDecimal pointsToGrant, String ruleId, String ruleSnapshotId) {
    // 1. 获取当前积分类型的账户
    MemberAccount rewardAccount = accountRepo.findByMemberIdAndTypeForUpdate(memberId, accountType);
    BigDecimal remainingToGrant = pointsToGrant;
    // 2. 补天窗：冲抵该账户的被动透支
    List<AccountTransaction> overdraftBatches = transactionRepo.findOverdraftBatchesForUpdate(memberId, accountType);
    for (AccountTransaction overdraft : overdraftBatches) {
        if (remainingToGrant.compareTo(BigDecimal.ZERO) <= 0) break;
        BigDecimal debt = overdraft.getRemainingAmount().abs();
        BigDecimal offsetAmount = remainingToGrant.min(debt);
        insertTransaction(memberId, accountType, "REPAYMENT", offsetAmount, null);
        overdraft.setRemainingAmount(overdraft.getRemainingAmount().add(offsetAmount));
        if (overdraft.getRemainingAmount().compareTo(BigDecimal.ZERO) == 0) {
            overdraft.setStatus("SETTLED");
        }
        transactionRepo.save(overdraft);
        remainingToGrant = remainingToGrant.subtract(offsetAmount);
    }
    // 3. 跨账户还信用：检查 CREDIT 账户的信用欠款
    if (remainingToGrant.compareTo(BigDecimal.ZERO) > 0) {
        MemberAccount creditAccount = accountRepo.findByMemberIdAndTypeForUpdate(memberId, "CREDIT");
        if (creditAccount != null && creditAccount.getCreditUsed().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal creditDebt = creditAccount.getCreditUsed();
            BigDecimal offsetAmount = remainingToGrant.min(creditDebt);
            insertTransaction(memberId, "CREDIT", "CREDIT_REPAY", offsetAmount, null);
            creditAccount.setCreditUsed(creditAccount.getCreditUsed().subtract(offsetAmount));
            accountRepo.save(creditAccount);
            remainingToGrant = remainingToGrant.subtract(offsetAmount);
        }
    }
    // 4. 真实入账
    if (remainingToGrant.compareTo(BigDecimal.ZERO) > 0) {
        insertTransaction(memberId, accountType, "ACCRUAL", remainingToGrant,
            calculateExpiryDate(), ruleId, ruleSnapshotId);
    }
    // 5. 更新累计统计
    rewardAccount.setTotalAccrued(rewardAccount.getTotalAccrued().add(pointsToGrant));
    accountRepo.save(rewardAccount);
}
```
#### 4.2.3 冲抵引擎约束
所有操作仅修改 `account_transaction` 的 `remaining_amount` 和 `status`，不更新 `member_account` 的任何余额字段。
### 4.3 FIFO 兑换引擎与精准溯源
#### 4.3.1 先进先出核销伪代码（跨账户版）
```java
@Transactional
public void redeemPoints(String programCode, String memberId, String accountType, BigDecimal pointsToRedeem) {
    // 1. 获取本账户和 CREDIT 账户
    MemberAccount account = accountRepo.findByMemberIdAndTypeForUpdate(memberId, accountType);
    MemberAccount creditAccount = accountRepo.findByMemberIdAndTypeForUpdate(memberId, "CREDIT");
    // 2. 计算总可用额度
    BigDecimal ownBalance = transactionRepo.sumAvailableBalance(memberId, accountType);
    BigDecimal creditAvailable = (creditAccount != null)
        ? creditAccount.getCreditLimit().subtract(creditAccount.getCreditUsed())
        : BigDecimal.ZERO;
    BigDecimal totalAvailable = ownBalance.add(creditAvailable);
    if (totalAvailable.compareTo(pointsToRedeem) < 0) {
        throw new BusinessException("ERR_INSUFFICIENT_POINTS", "积分不足（含信用额度）");
    }
    // 3. 生成核销流水
    AccountTransaction redemptionTx = createRedemptionTransaction(memberId, accountType, pointsToRedeem.negate());
    BigDecimal remaining = pointsToRedeem;
    // 4. 先消耗自有积分
    if (ownBalance.compareTo(BigDecimal.ZERO) > 0) {
        List<AccountTransaction> ownBatches = transactionRepo.findActiveBatchesForUpdate(
            memberId, accountType,
            Sort.by("expiresAt").ascending().and(Sort.by("createdAt").ascending())
        );
        for (AccountTransaction batch : ownBatches) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            // 惰性过期检查
            if (batch.getExpiresAt() != null && batch.getExpiresAt().isBefore(LocalDateTime.now())) {
                markAsExpired(batch);
                continue;
            }
            BigDecimal allocateAmount = remaining.min(batch.getRemainingAmount());
            batch.setRemainingAmount(batch.getRemainingAmount().subtract(allocateAmount));
            if (batch.getRemainingAmount().compareTo(BigDecimal.ZERO) == 0) {
                batch.setStatus("EXHAUSTED");
            }
            transactionRepo.save(batch);
            allocationRepo.save(new Allocation(redemptionTx.getId(), batch.getId(), allocateAmount));
            remaining = remaining.subtract(allocateAmount);
        }
    }
    // 5. 不足部分从 CREDIT 账户扣款
    if (remaining.compareTo(BigDecimal.ZERO) > 0 && creditAccount != null) {
        creditAccount.setCreditUsed(creditAccount.getCreditUsed().add(remaining));
        accountRepo.save(creditAccount);
        insertTransaction(memberId, "CREDIT", "CREDIT_DRAWDOWN", remaining.negate(), null);
    }
}
```
#### 4.3.2 积分过期处理（惰性标记 + 事件驱动）
**严禁使用定时任务批量更新**，过期判定在查询时实时完成：
```java
private void markAsExpired(AccountTransaction batch) {
    batch.setStatus("EXPIRED");
    batch.setRemainingAmount(BigDecimal.ZERO);
    transactionRepo.save(batch);
    eventBridge.publish("loyalty-point-events", batch.getMemberId(),
        new PointsExpiredEvent(batch.getMemberId(), batch.getRemainingAmount(), batch.getId()));
}
```
### 4.4 信用额度授予接口
```java
@Transactional
public void setCreditLimit(String programCode, String memberId, BigDecimal newLimit) {
    MemberAccount creditAccount = accountRepo.findByMemberIdAndTypeForUpdate(memberId, "CREDIT");
    if (creditAccount == null) {
        creditAccount = createCreditAccount(memberId);
    }
    creditAccount.setCreditLimit(newLimit);
    accountRepo.save(creditAccount);
}
```
### 4.5 等级评估双轨制
* **实时升级**：`PointGrantService` 成功后发布 `TierPointAccruedEvent`，`TierEvaluationService` 监听后评估是否升级
* **定时保级降级**：每日凌晨扫描到期会员，不满足保级条件则逐级回退
### 4.6 积分碎片整理与核销分摊明细
```sql
CREATE TABLE redemption_allocation (
    id BIGSERIAL PRIMARY KEY,
    program_code VARCHAR(32) NOT NULL,
    redemption_tx_id BIGINT NOT NULL,
    accrual_tx_id BIGINT NOT NULL,
    allocated_amount NUMERIC(18, 4) NOT NULL
);
```
* 当 `ACTIVE` 批次超过 50 条时，低峰期触发合并任务
* 退款时通过 `redemption_allocation` 精准恢复原始批次的 `expires_at`
***
## 第五章：逆向交易与级联重算域
### 5.1 场景挑战
退款导致"级联重算效应"：退单引发降级 → 后续积分获取比例全部失真。
传统架构痛点：强锁阻塞用户新交易（账户假死）。
### 5.2 异步差额补偿机制
1. **触发退款**：回滚原订单积分
2. **判断级联**：历史成长值跌破升级门槛则生成 `cascade_recalc_job`
3. **影子回放**：异步引擎构建虚拟影子账户，按历史规则快照重放
4. **计算差额**：对比影子结果与真实状态
5. **短事务补偿**：最后一步开启极短数据库事务提交差额
### 5.3 级联重算引擎伪代码
```java
@Scheduled(fixedDelay = 60000)
public void recoverStuckJobs() {
    LocalDateTime timeout = LocalDateTime.now().minusMinutes(5);
    List<CascadeRecalcJob> stuckJobs = jobRepo.findStuckJobs("PROCESSING", timeout);
    for (CascadeRecalcJob job : stuckJobs) {
        if (job.getRetryCount() >= job.getMaxRetryCount()) {
            job.setStatus("FAILED");
            alertService.sendAlert("级联重算任务彻底失败: " + job.getId());
        } else {
            job.setStatus("PENDING");
            job.setRetryCount(job.getRetryCount() + 1);
        }
        jobRepo.save(job);
    }
}
public void processCascadeRecalculation(String recalcJobId, String memberId, LocalDateTime reverseTime) {
    List<TransactionEvent> timelineEvents = eventRepo.findEventsAfter(memberId, reverseTime);
    ShadowContext shadowContext = buildShadowContext(memberId, reverseTime);
    for (TransactionEvent event : timelineEvents) {
        shadowContext.advanceToTime(event.getEventTime());
        String ruleSnapshotId = event.getRuleSnapshotId();
        RuleSnapshot snapshot = snapshotRepo.get(ruleSnapshotId);
        shadowContext.apply(ruleEngine.evaluate(shadowContext, event, snapshot));
    }
    AccountDelta delta = calculateDelta(memberId, shadowContext);
    applyCompensationWithShortTransaction(memberId, delta, recalcJobId);
}
@Transactional
protected void applyCompensationWithShortTransaction(String memberId, AccountDelta delta, String jobId) {
    if (compensationLogRepo.existsByJobId(jobId)) return;
    if (delta.getPointsToDeduct().compareTo(BigDecimal.ZERO) > 0) {
        pointRedeemService.forceDeductWithOverdraft(memberId, delta.getPointsToDeduct(), "CASCADE_RECALC_DEDUCT");
    }
    if (delta.getNewTier() != null) {
        Member member = memberRepo.findByMemberIdForUpdate(memberId);
        if (!member.getTierCode().equals(delta.getNewTier())) {
            String oldTier = member.getTierCode();
            member.setTierCode(delta.getNewTier());
            memberRepo.save(member);
            tierChangeLogRepo.save(new TierChangeLog(memberId, oldTier, delta.getNewTier(), "CASCADE_DOWNGRADE"));
        }
    }
    compensationLogRepo.save(new CompensationLog(jobId, memberId, delta));
    jobRepo.markCompleted(jobId);
}
```
#### 5.3.1 ShadowContext 设计规范
```java
public class ShadowContext {
    private String memberId;
    private List<TierChangeEvent> tierTimeline;
    private String currentTier;
    private BigDecimal shadowBalance;
    public void advanceToTime(LocalDateTime eventTime) {
        this.currentTier = tierTimeline.stream()
            .filter(t -> !t.getChangedAt().isAfter(eventTime))
            .max(Comparator.comparing(TierChangeEvent::getChangedAt))
            .map(TierChangeEvent::getNewTier)
            .orElse(getInitialTier());
    }
    private String getInitialTier() {
        return programConfig.getDefaultTier();
    }
}
```
### 5.4 积分退回与生命周期还原
退款时通过 `redemption_allocation` 表找到原始 `ACCRUAL` 批次，精准恢复其 `expires_at`。若原始批次已过期，系统拦截作废，但可配置 `refund_expired_points_grace_days` 给予宽限期。
### 5.5 负资产与死信处理
逆向扣减超过 `overdraft_limit` 时：
* 停止扣减，只扣至透支极限
* 生成追偿工单（`negative_pending` 表）
* 标记用户 `SUSPENDED_REDEMPTION`，禁止兑换直至补齐债务
***
## 第六章：规则引擎、沙箱与自动化回归
### 6.1 规则引擎基础架构
* **无状态会话**：每次推理创建全新 `StatelessKieSession`，执行完毕立刻释放
* **KieBase 缓存热替换**：按 Program 级别缓存，发布新规则时原子替换引用
* **隔离的动作收集器**：DRL 中禁止直接调用 JPA/SQL
#### 6.1.1 动作执行器
```java
@Service
public class RewardExecutor {
    @Autowired private PointGrantService pointGrantService;
    @Autowired private TierEvaluationService tierService;
    @Transactional
    public void executeRewards(String memberId, List<Action> actions) {
        for (Action action : actions) {
            if (action instanceof AwardPointsAction award) {
                pointGrantService.grantPoints(award.getProgramCode(), memberId,
                    award.getAccountType(), award.getPoints(), award.getRuleId(), award.getRuleSnapshotId());
            } else if (action instanceof UpgradeTierAction upgrade) {
                tierService.upgrade(memberId, upgrade.getNewTier(), upgrade.getReason());
            } else if (action instanceof DowngradeTierAction downgrade) {
                tierService.downgrade(memberId, downgrade.getNewTier(), downgrade.getReason());
            }
        }
    }
}
```
#### 6.1.2 KieBase 原子热替换
```java
@Component
@Slf4j
public class KieBaseCacheManager {
    private final ConcurrentHashMap<String, AtomicReference<KieBase>> cache = new ConcurrentHashMap<>();
    public KieBase getKieBase(String programCode) {
        AtomicReference<KieBase> ref = cache.get(programCode);
        if (ref == null) {
            synchronized (this) {
                ref = cache.get(programCode);
                if (ref == null) {
                    KieBase kieBase = buildKieBase(programCode);
                    ref = new AtomicReference<>(kieBase);
                    cache.put(programCode, ref);
                }
            }
        }
        return ref.get();
    }
    public void refreshKieBase(String programCode) {
        KieBase newKieBase = buildKieBase(programCode);
        AtomicReference<KieBase> ref = cache.computeIfAbsent(programCode, k -> new AtomicReference<>());
        KieBase oldKieBase = ref.getAndSet(newKieBase);
        log.info("Program [{}] KieBase 已热更新", programCode);
    }
    private KieBase buildKieBase(String programCode) {
        List<Rule> activeRules = ruleRepo.findActiveByProgramCode(programCode);
        KieServices kieServices = KieServices.Factory.get();
        KieFileSystem kieFileSystem = kieServices.newKieFileSystem();
        for (Rule rule : activeRules) {
            String drlPath = "src/main/resources/rules/" + programCode + "/" + rule.getRuleId() + ".drl";
            kieFileSystem.write(drlPath, rule.getDrlContent());
        }
        KieBuilder kieBuilder = kieServices.newKieBuilder(kieFileSystem);
        kieBuilder.buildAll();
        if (kieBuilder.getResults().hasMessages(Message.Level.ERROR)) {
            throw new RuleCompileException("规则编译失败: " + kieBuilder.getResults().getMessages());
        }
        KieContainer kieContainer = kieServices.newKieContainer(kieServices.getRepository().getDefaultReleaseId());
        return kieContainer.getKieBase();
    }
}
```
**并发安全保证**：
* 严禁将 KieBase 直接存储在普通成员变量中
* 严禁使用 synchronized 包裹推理执行逻辑
* 必须使用 AtomicReference 管理 KieBase 引用
### 6.2 AI 辅助规则生成
系统允许运营人员通过自然语言生成 Drools 规则。后端自动收集当前 Program 的生产环境状态拼接成 System Prompt，要求 LLM 输出标准化 JSON 结构（含 DRL 代码、推荐优先级、测试用例）。
### 6.3 影子沙箱与自动化回归
* **数据源**：AI 模拟用例 + 生产环境历史切片（7 天内 500 条真实流水）
* **双 KieSession 验证**：基线引擎（仅线上老规则） vs 候选引擎（老规则 + 新草稿），对比输出差异
### 6.4 弱阻断与强制放行机制
* **绿色**：通过，常规提交
* **黄色**：轻微规则遮蔽，允许提交
* **红色**：严重冲突，需强制放行（传入 `forceOverride=true` + `overrideReason`），审批流升级为双人会签
***
## 第七章：全渠道会员通 SPI 统一接入与异构映射引擎
### 7.1 SPI 架构的本质与挑战
第三方平台通过 Webhook 调用 SaaS 平台接口，面临三大挑战：激进重试策略、严苛 HTTP 状态码要求、1:N 异构数据转换。
### 7.2 Unified SPI Gateway
标准路径：`POST /api/open/spi/{channel}/{programCode}/{action}`
#### 7.2.1 策略模式接口
```java
public interface ChannelSpiHandler {
    String getChannelCode();
    boolean verifySignature(HttpServletRequest request, byte[] rawBody, ChannelAdapterConfig config);
    Object handleAction(String action, String programCode, byte[] rawBody, ChannelAdapterConfig config);
    Object buildErrorResponse(Exception e);
}
```
#### 7.2.2 网关 Controller
```java
@RestController
@RequestMapping("/api/open/spi/{channel}/{programCode}")
@Slf4j
public class SpiGatewayController {
    @Autowired private SpiHandlerFactory handlerFactory;
    @PostMapping("/{action}")
    public ResponseEntity<Object> handleSpi(@PathVariable String channel, @PathVariable String programCode,
                                            @PathVariable String action, HttpServletRequest request) {
        byte[] rawBody = readBody(request);
        ChannelSpiHandler handler = handlerFactory.getHandler(channel);
        ChannelAdapterConfig config = configService.getValidConfig(programCode, channel);
        if (!handler.verifySignature(request, rawBody, config)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Signature Invalid");
        }
        CompletableFuture<Object> future = CompletableFuture.supplyAsync(() ->
            handler.handleAction(action, programCode, rawBody, config), spiThreadPool);
        try {
            Object spiResponse = future.get(2000, TimeUnit.MILLISECONDS);
            return ResponseEntity.ok(spiResponse);
        } catch (TimeoutException te) {
            return ResponseEntity.ok(handler.buildErrorResponse(new BusinessException("TIMEOUT")));
        } catch (Exception e) {
            return ResponseEntity.ok(handler.buildErrorResponse(e));
        }
    }
}
```
### 7.3 双轨制映射引擎
* **模式一：可视化连线 (VISUAL)**：通过 JSONPath 配置字段映射
* **模式二：高级脚本转换 (SCRIPT)**：利用 GraalVM JS 引擎处理复杂聚合运算
#### 7.3.1 GraalVM 脚本安全沙箱
严格限制：`allowAllAccess(false)`、`allowHostAccess(NONE)`、`allowIO(false)`、`allowNativeAccess(false)`，执行超时 50ms。
### 7.4 事件标准化生命周期与死信处理
`event_inbox` 状态机：`RECEIVED → VALIDATING → VALIDATED → PROCESSING → COMPLETED`（失败则进入 `TRANSFORM_FAILED → RETRYING → DEAD`）。
### 7.5 API 实体到接口的自动生成
用户在 ChartDB 中设计 API 实体后，系统自动生成 JSON Schema、映射脚本骨架，并动态注册 SPI 路由。
```sql
ALTER TABLE channel_adapter_config
ADD COLUMN api_entity_name VARCHAR(100),
ADD COLUMN request_schema JSONB,
ADD COLUMN response_schema JSONB,
ADD COLUMN cross_validations JSONB,
ADD COLUMN generated_controller BOOLEAN DEFAULT false;
```
API 实体支持字段级校验和跨字段联合校验脚本：
```json
{
  "cross_validations": [{
    "type": "SCRIPT",
    "language": "javascript",
    "script": "if (source.order_amount > 10000 && !source.coupon_code) { throw new Error('订单金额超过10000时必须填写优惠券码'); }",
    "error_message": "订单金额超过10000时必须填写优惠券码",
    "dependencies": ["coupon_code"]
  }]
}
```
***
# 第八章：管理界面设计与前端规范
## 8.1 总体思路与前端设计哲学
### 8.1.1 Schema-Driven UI 架构
在 SaaS 平台中，运营人员对"活动规则"和"会员属性"的调整频率极高。如果每增加一个会员字段都要经历"后端修改 DB → 前端修改代码 → 打包发版"的周期，将严重降低业务响应速度。本系统采用 **Schema-Driven UI (Schema 驱动 UI)** 架构。
我们将前端界面逻辑分为两层：
* **设计器态 (Form Builder)**：管理员通过画布拖拽配置动态属性或活动规则，配置结果保存为 JSON Schema
* **运行态 (Renderer)**：前端界面根据后端下发的 JSON Schema，自动渲染表单、列表或详情页，无需关心具体字段
### 8.1.2 前端设计哲学
* **纯白底色**：全站统一 `#ffffff` 背景，无灰色背景区
* **黑线条风格**：按钮、图标、边框统一使用 `#1a1a1a`
* **点击编辑**：列表项默认显示文字，点击才出现输入框，减少视觉噪音
* **单行布局**：列表行所有元素在一个 flex 行内，不换行，超出横向滚动
***
## 8.2 全局设计语言
### 8.2.1 色彩体系
| 用途    | 色值                              | 说明                     |
| ----- | ------------------------------- | ---------------------- |
| 页面背景  | `#ffffff`                       | 纯白底，全站统一               |
| 主文字   | `#1a1a1a`                       | 标题、正文                  |
| 辅助文字  | `#666666`                       | 描述性文字                  |
| 弱化文字  | `#999999`                       | 提示、占位                  |
| 禁用/分隔 | `#bbbbbb` / `#cccccc`           | 不可用状态、浅分隔              |
| 分割线   | `#e0e0e0`                       | 列表行间分割                 |
| 卡片边框  | `#e8e8e8`                       | 内容卡片边框                 |
| 输入框边框 | `#e0e0e0`                       | 默认态；focus 变为 `#1a1a1a` |
| 主按钮   | `#1a1a1a`（黑底白字）                 | 实心操作按钮                 |
| 次按钮   | `#ffffff` + `1px solid #d9d9d9` | 空心操作按钮                 |
### 8.2.2 字体规范
| 项目    | 值                                                                   |
| ----- | ------------------------------------------------------------------- |
| 字体族   | `-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif` |
| 标题    | 24-28px, weight 700, `#1a1a1a`                                      |
| 卡片标题  | 19px, weight 600                                                    |
| 正文    | 14-15px, `#666666`                                                  |
| 标签/辅助 | 13px, weight 500                                                    |
| 小字    | 11-12px, `#999999`                                                  |
### 8.2.3 圆角与间距
| 元素         | 圆角           |
| ---------- | ------------ |
| 卡片         | 12px         |
| 按钮         | 10px         |
| 输入框        | 8px          |
| 开关(Toggle) | 12px (44×24) |
| 标签(Tag)    | 4px          |
* 列表行间距：`padding: 10-12px 0` + `border-bottom: 1px solid #e0e0e0`
* 卡片内边距：`24px 28px`
* 组件间 gap：`10px`
### 8.2.4 按钮规范
```css
/* 主按钮 — 实心黑底白字 */
height: 42px; padding: 0 28px; background: #1a1a1a;
border: none; border-radius: 10px; color: #ffffff;
font-size: 15px; font-weight: 500;
/* 次按钮 — 空心黑框白底 */
height: 42px; padding: 0 28px; background: #ffffff;
border: 1px solid #d9d9d9; border-radius: 10px; color: #1a1a1a;
font-size: 15px; font-weight: 500;
/* hover — 反转 */
主按钮: opacity: 0.85
次按钮: background: #1a1a1a; color: #ffffff
```
### 8.2.5 输入框规范
```css
height: 42px; padding: 0 14px;
border: 1px solid #e0e0e0; border-radius: 8px;
font-size: 15px; color: #1a1a1a; background: #ffffff;
/* focus: border-color → #1a1a1a */
```
### 8.2.6 Toggle 开关
```css
width: 44px; height: 24px; border-radius: 12px;
/* ON: background #1a1a1a, dot left 22px */
/* OFF: background #e0e0e0, dot left 2px */
dot: width 20px; height 20px; background #ffffff; border-radius 50%;
```
### 8.2.7 图标规范
* 全部使用**自定义 SVG 纯线条图标**
* 统一规格：`stroke="#1a1a1a" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" fill="none"`
* 尺寸：步进箭头 20×20，步骤图标 24×24，按钮箭头 16×16
### 8.2.8 前端技术栈
| 层级       | 技术                                                                 |
| -------- | ------------------------------------------------------------------ |
| 核心框架     | React 18 + TypeScript + Vite                                       |
| UI 组件库   | Ant Design 5                                                       |
| 动态表单     | Formily 2.x (`@formily/core` + `@formily/react` + `@formily/antd`) |
| 画布引擎     | ChartDB（开源 React 组件，用于 DB Schema 设计器）                              |
| 状态管理     | Zustand                                                            |
| HTTP 客户端 | axios                                                              |
| 路由       | React Router v6                                                    |
***
## 8.3 布局架构
### 8.3.1 页面路由结构
```text
/login              → 独立页面（无菜单）
/onboarding         → 独立页面（无菜单，全屏引导）
/                   → AppShell 包裹（顶部菜单 + 内容区 + 底栏）
  /dashboard        → 仪表盘
  /members          → 会员列表
  /members/:id      → 会员详情
  /modeling/entity  → DB Schema 设计器
  /modeling/schema  → 表单 Schema 设计器
  /rules            → 规则列表
  /rules/new        → 规则编辑器
  /channels         → 渠道列表
  /system/*         → 系统设置各页
```
### 8.3.2 AppShell 布局
```text
┌──────────────────────────────────────────────────────────┐
│ [Logo] 数据建模  会员服务  规则引擎  设置   PROG001  🔔 👤 │ ← Header 56px
├──────────────────────────────────────────────────────────┤
│                                                          │
│              内容区 (max-width: 1400px)                   │
│                                                          │
├──────────────────────────────────────────────────────────┤
│ 当前俱乐部: PROG001          环境: DEV | v1.0.0           │ ← Footer 32px
└──────────────────────────────────────────────────────────┘
```
* Header：白色底 `#ffffff`，`border-bottom: 1px solid #f0f0f0`，`box-shadow: 0 1px 4px rgba(0,0,0,0.04)`
* 菜单：`mode="horizontal"`，`triggerSubMenuAction="hover"`
* Footer：白色底，顶部 `1px solid #f0f0f0` 分割
* 内容区：面包屑导航 + `<Outlet />`，所有页面 API 请求自动注入 `X-Program-Code`
### 8.3.3 菜单结构
| 一级菜单 | 二级菜单/路由                                                               |
| ---- | --------------------------------------------------------------------- |
| 数据建模 | DB Schema 设计器 (`/modeling/entity`)、表单 Schema 设计器 (`/modeling/schema`) |
| 会员服务 | 会员列表 (`/members`)                                                     |
| 规则引擎 | 规则列表 (`/rules`)                                                       |
| 设置   | 积分类型、等级设置、渠道列表、脚本工作台、角色权限、操作日志、SPI 日志、租户审计                            |
### 8.3.4 前端路由配置
```typescript
const routes = [
  { path: '/login', element: <Login /> },
  { path: '/onboarding', element: <Onboarding /> },
  { path: '/', element: <AppShell />, children: [
    { path: '/dashboard', element: <Dashboard /> },
    { path: '/members', element: <MemberList /> },
    { path: '/members/:id', element: <MemberDetail /> },
    { path: '/modeling/entity', element: <EntitySchemaDesigner /> },
    { path: '/modeling/schema', element: <FormSchemaDesigner /> },
    { path: '/rules', element: <RuleList /> },
    { path: '/rules/new', element: <RuleEditor /> },
    { path: '/channels', element: <ChannelList /> },
    { path: '/system/*', element: <SystemSettings /> },
  ]},
];
```
路由守卫 `AuthGuard` 检查用户登录状态及按钮级权限，无权限跳转 403 页面。
### 8.3.5 全局状态管理（Zustand Store）
```typescript
interface AppStore {
  currentProgramCode: string;     // 当前 Program
  programs: Program[];            // Program 列表
  setCurrentProgram: (code: string) => void;
  user: UserInfo | null;         // 用户信息
  permissions: string[];         // 权限列表
  setUser: (user: UserInfo, permissions: string[]) => void;
  hasPermission: (perm: string) => boolean;
  online: boolean;               // 网络状态
}
```
数据刷新策略：
* 切换 Program 时，清空所有缓存列表数据，触发全局 `key` 变化强制重渲染
* 会员详情等频繁访问的数据使用 `react-query` 缓存，staleTime 30 秒
* 列表页筛选条件同步到 URL query params，支持浏览器前进/后退和书签
### 8.3.6 API 层封装
* axios 实例，baseURL: `/api`
* 请求拦截器自动注入 `X-Program-Code`（从 Zustand store 读取）、`X-Trace-Id`（自动生成）、`Authorization`（JWT Token）
* 响应拦截器统一处理 `ApiResponse`，401 跳转登录
***
## 8.4 核心组件模式
### 8.4.1 ClickToEditText（点击编辑文字）
* **显示态**：文字 + `border-bottom: 1px dashed #d9d9d9` 虚线提示可编辑
* **编辑态**：点击后原地变为 `<input>`，自动聚焦全选
* **确认**：Enter 或 onBlur 退出编辑
* **宽度**：通过 `style.width` 固定列宽，`overflow: hidden; text-overflow: ellipsis`
### 8.4.2 ClickToEdit（点击编辑数值）
同 ClickToEditText，但输入框为 `type="number"`，可带单位后缀（如 "分"、"天"）。
### 8.4.3 ClickToEditSelect（点击编辑下拉）
* **显示态**：文字 + 虚线（显示当前选项的 label）
* **编辑态**：点击后变为 `<select>`，选择后自动退出编辑
### 8.4.4 PageWrapper（页面容器）
```tsx
<PageWrapper loading={loading} error={error} isEmpty={!data?.length}
  emptyText="暂无数据" onRetry={fetchData}>
  {children}
</PageWrapper>
```
* `loading` → Spin
* `error` → Result + 重试按钮
* `empty` → Empty 组件
* 正常 → 渲染 children
### 8.4.5 StatCard（统计卡片）
```tsx
<StatCard title="会员总数" value={12345} prefix={<Icon />} trend={12} trendLabel="较昨日" />
```
### 8.4.6 页面通用规范
**表格行**：
```css
padding: 10px 0;
border-bottom: 1px solid #e0e0e0;
display: flex; align-items: center; gap: 10px;
overflow-x: auto;
```
* 所有列 `flex-shrink: 0` 防止压缩
* 删除按钮 `margin-left: auto` 推到最右
* `×` 按钮：灰色 `#cccccc`，无背景无边框
**卡片容器**：
```css
border: 1px solid #e8e8e8; border-radius: 12px;
padding: 24px 28px; background: #ffffff;
max-height: calc(100vh - 280px); overflow: auto;
```
**标题栏**：
```css
font-size: 19px; font-weight: 600; color: #1a1a1a;
display: flex; align-items: center; gap: 10px;
```
**添加文字链接**：
```css
font-size: 13px; color: #999999; cursor: pointer;
border-bottom: 1px dashed #d9d9d9; padding: 2px 0;
/* hover: color → #1a1a1a */
```
***
## 8.5 登录页面
```text
        [Logo SVG]
    忠诚度管理平台
  Loyalty SaaS Platform
  [👤 用户名        ]
  [🔑 密码          ]
      [🔒 登 录]
    默认账号: admin / admin123
```
* 全屏居中，纯白底
* 自定义 SVG 图标（锁、用户、钥匙）
* 输入框：灰色底 `#fafafa`，focus 白底 + 黑边框
* 按钮：黑底白字 44px 高
* 当前为开发模式，点击直接登录
***
## 8.6 新用户引导流程 (Onboarding)
设计为 **3 步顺序向导**，全屏独立页面，无顶部菜单。
### 8.6.1 页面布局
```text
        ①         ②         ③
    俱乐部设置 → 积分类型设置 → 等级设置
    ━━━━━━━━
┌─────────────────────────────────────┐
│ 🧩 俱乐部设置                        │
│ 俱乐部是忠诚度计划的基础容器...        │
│                                     │
│ 俱乐部代码 *  [________]             │
│ 显示名称 *    [________]             │
│ 描述          [________]             │
│                                     │
│              [保存并继续 →]          │
└─────────────────────────────────────┘
```
* 步骤编号：40px 圆形，active 黑框白底黑字，done 黑底白勾
* 步骤间：20px 箭头 `────>`
* 步骤标签下方：`40px 宽 2px 深灰线` 标识当前步骤
* 内容卡片：`max-width: 720px 居中`，`max-height: calc(100vh - 280px) overflow: auto`
* `<< 上一步` 文字链接：13px `#999999`，hover `#1a1a1a`，`marginLeft: auto`
### 8.6.2 Step 1 — 俱乐部设置
* 表单字段：俱乐部代码（必填）、显示名称（必填）、描述（可选）
* 按钮：`保存并继续 →`（黑底白字，含箭头图标）
* 保存时调用 `POST /api/admin/programs`
### 8.6.3 Step 2 — 积分类型设置
**行结构（单行 flex）**：
```text
[名称] [代码] [类型▼] [配置项...] [×删除]
```
**类型下拉选项**：
| 选项 | 配置项                               |
| -- | --------------------------------- |
| 兑换 | 负分（开关 → 单次/上限）+ 可见 + 有效期（数值 + 模式） |
| 等级 | 可见 + 有效期（数值 + 模式）                 |
| 信用 | 仅额度可编辑（其他锁定）                      |
**有效期模式**：固定天数 / 自然月 / 自然年（0 = 永不过期）
**默认预设 3 种**：
| typeCode | 名称    | 类型 | 配置           |
| -------- | ----- | -- | ------------ |
| REWARD   | 消费积分  | 兑换 | 可见，有效期 1 自然年 |
| TIER     | 等级成长值 | 等级 | 可见，有效期 0（永久） |
| CREDIT   | 授信积分  | 信用 | 额度 5000 分    |
**交互**：
* 名称/代码：ClickToEditText
* 类型：`<select>` 下拉
* 有效期值/额度：ClickToEdit（点击编辑数值）
* 有效期模式：ClickToEditSelect（点击编辑下拉）
* 负分/可见：Toggle 开关
* 删除：`×` 按钮
* 添加：`+ 添加积分类型` 文字链接（灰色虚线，hover 变黑，点击新增一行）
### 8.6.4 Step 3 — 等级设置
**行结构（单行 flex）**：
```text
[代码] [名称] [关联积分▼] 成长值 [min] — [max] 有效期 [val] [模式] 顺序 N [×]
```
**关联积分下拉**：仅显示 `is_tier_calc = true` 的积分类型
**默认预设 4 个等级**：BASE → SILVER → GOLD → PLATINUM，关联 TIER 类型
**交互**：全部使用 ClickToEdit/ClickToEditSelect
### 8.6.5 完成页
```text
        ✅ 基础设置完成！
   俱乐部、积分类型、等级已配置完毕
        [进入仪表盘 →]
```
***
## 8.7 表单 Schema 设计器
### 8.7.1 实体选择器
```text
实体: [会员] [交易] [+ 添加实体]
```
* 点击标签切换实体
* 点击 `+ 添加实体` 输入新实体名称
* 每个实体独立 Schema 存储
### 8.7.2 布局（三栏）
```text
┌──────────┬────────────────────────────┬──────────┐
│ 组件面板  │         设计画布            │ 属性面板  │
│          │    ┌──────────────────┐    │          │
│ Input    │    │  字段1: Input    │    │          │
│ Select   │    │  字段2: Select   │    │          │
│ ...      │    └──────────────────┘    │          │
└──────────┴────────────────────────────┴──────────┘
```
* 左栏：组件面板（Card，200px），可拖拽组件到画布
* 中栏：设计画布（拖拽区域，dashed border）
* 右栏：属性面板，选中字段时显示配置项
* 字段卡片：名称 + 类型标签 + 配置/删除按钮
* 展开配置区：标题、必填开关、联动表达式
### 8.7.3 自定义组件注册
平台内置 Input、Select、NumberPicker 等基础组件，但支持 Program 级别扩充自定义 `x-component`：
```json
{
  "store_selector": {
    "title": "归属门店",
    "type": "string",
    "x-component": "CustomStoreSelector",
    "x-component-props": {
      "source": "remote",
      "endpoint": "/api/stores",
      "multiple": false
    }
  }
}
```
Formily 支持全局组件注册机制，自定义组件在应用初始化时注册：
```javascript
const componentRegistry = {
  'CustomStoreSelector': CustomStoreSelector,
  'ImageUploader': ImageUploader,
  'CascadingAddress': CascadingAddress,
};
```
***
## 8.8 DB Schema 设计器 — 基于 ChartDB 的三层实体模型
### 8.8.1 技术选型
选用 **ChartDB**（开源 React 组件）作为画布引擎：
* 原生 React 组件，可直接嵌入
* 支持节点、字段、连线、JSON 导入导出
* 支持列级别 `extensions` 扩展属性（承载 Formily 属性）
* 现代化 UI，与 Ant Design 风格一致
### 8.8.2 三层实体模型
画布上区分三类实体：
| 实体类型   | 视觉标识      | 可否删除 | 说明                                                                |
| ------ | --------- | ---- | ----------------------------------------------------------------- |
| 系统实体   | 灰色背景 + 🔒 | ❌    | Member, TransactionEvent, MemberAccount, MemberUniqueKey, Program |
| 业务实体   | 蓝色背景 + 📦 | ✅    | PetInfo, PurchaseRecord 等，映射到系统实体的 JSONB 容器字段                     |
| API 实体 | 绿色背景 + 🔌 | ✅    | OrderRequest, EnrollRequest 等，映射到 SPI 网关接口                        |
### 8.8.3 系统实体预加载
画布初始化时自动放置以下系统实体（锁定不可删除）：
**Member（会员）**：
| 字段               | 类型       | 锁定    | 说明                                    |
| ---------------- | -------- | ----- | ------------------------------------- |
| `member_id`      | String   | 🔒    | 主键，One-ID                             |
| `program_code`   | String   | 🔒    | 租户隔离                                  |
| `status`         | Enum     | 🔒    | ENROLLED/SUSPENDED/MERGED/DEACTIVATED |
| `tier_code`      | String   | 🔓    | 当前等级                                  |
| `schema_version` | String   | 🔓    | Schema 版本号                            |
| `created_at`     | DateTime | 🔒    | 注册时间                                  |
| `ext_attributes` | Object   | 🔓 容器 | 动态扩展属性（内容由业务实体定义）                     |
**TransactionEvent（交易事件）**：
| 字段               | 类型       | 锁定    | 说明                             |
| ---------------- | -------- | ----- | ------------------------------ |
| `event_id`       | String   | 🔒    | 主键                             |
| `member_id`      | String   | 🔒    | 外键→Member                      |
| `event_type`     | Enum     | 🔒    | ORDER/ENROLL/CUSTOM/REDEMPTION |
| `channel`        | String   | 🔒    | 渠道标识                           |
| `event_time`     | DateTime | 🔒    | 事件时间                           |
| `idempotent_key` | String   | 🔒    | 幂等键                            |
| `payload`        | Object   | 🔓 容器 | 动态扩展数据                         |
**MemberAccount（积分账户）**、**MemberUniqueKey（One-ID 辅助表）**、**Program（租户计划）** 同样预加载并锁定。
### 8.8.4 系统实体预加载连线
```text
Member ───1:N─── MemberUniqueKey
Member ───1:1─── MemberAccount
TransactionEvent ───N:1─── Member
```
这些连线同样锁定，不可删除。
### 8.8.5 业务实体设计
业务实体映射到系统实体的 JSONB 容器字段。连线时配置映射关系：
| 配置项   | 说明             | 示例              |
| ----- | -------------- | --------------- |
| 源实体   | 业务实体           | PetInfo         |
| 目标实体  | 系统实体           | Member          |
| 映射到字段 | 系统实体的 JSONB 字段 | ext_attributes |
| 存储路径  | JSON 中的 Key 名  | pets            |
| 存储方式  | Object / Array | Array           |
### 8.8.6 API 实体设计
API 实体额外支持：
**基础校验规则**：`required`, `min_length`, `max_length`, `pattern`, `minimum`, `maximum`, `enum_values`
**联合校验脚本**：JavaScript 函数，支持跨字段校验：
```json
{
  "cross_validations": [{
    "type": "SCRIPT",
    "language": "javascript",
    "script": "if (source.order_amount > 10000 && !source.coupon_code) { throw new Error('订单金额超过10000时必须填写优惠券码'); }",
    "error_message": "订单金额超过10000时必须填写优惠券码",
    "dependencies": ["coupon_code"]
  }]
}
```
**API 路由配置**：`method`, `path`, `auth_type`, `response_entity`
### 8.8.7 右侧属性面板
选中字段时滑出，两个 Tab：
* **结构配置 Tab**：字段名、类型、主键、必填、默认值、枚举值、强标识标记、子对象入口
* **呈现配置 Tab**：x-component（Select 选择 Formily 组件）、x-reactions（可视化构建器/手写表达式切换）、x-validator、deprecated
**联动规则可视化构建器**：
* 选择依赖字段
* 选择条件运算符（`===`, `!==`, `>`, `<`, `includes`）
* 输入比较值
* 选择目标效果（显示/隐藏、必填/非必填）
* 自动生成 `x-reactions` 表达式
### 8.8.8 数据转换
* 保存时：ChartDB 数据 + extensions → LoyaltySchema（Formily 可消费格式）
* 加载时：LoyaltySchema → ChartDB 数据 + extensions
转换示例：
```json
{
  "entityType": "MEMBER",
  "version": "v1.2.0",
  "field_schema": {
    "type": "object",
    "properties": {
      "pet_name": {
        "title": "宠物名称",
        "type": "string",
        "x-component": "Input"
      },
      "pets": {
        "title": "宠物档案",
        "type": "array",
        "items": {
          "type": "object",
          "properties": {
            "pet_type": { "title": "宠物类型", "type": "string", "x-component": "Select" }
          }
        }
      }
    }
  },
  "entity_relations": [
    {
      "source": "member",
      "target": "pet_info",
      "relationType": "1:N",
      "foreignKeyField": "member_id"
    }
  ]
}
```
### 8.8.9 版本管理
`program_schema` 表存储 Schema 版本，支持 DRAFT/ACTIVE/DEPRECATED 状态流转，历史版本回滚。
### 8.8.10 画布交互与快捷键
| 操作        | 快捷键                   | 说明            |
| --------- | --------------------- | ------------- |
| 添加业务实体    | `B`                   | 创建蓝色业务实体节点    |
| 添加 API 实体 | `A`                   | 创建绿色 API 实体节点 |
| 连线模式      | `L`                   | 切换鼠标为连线模式     |
| 删除选中      | `Delete`              | 删除选中节点/连线     |
| 适应画布      | `F`                   | 缩放平移使所有节点可见   |
| 平移画布      | 空格+拖拽                 | 移动视野          |
| 缩放画布      | 鼠标滚轮                  | 50%-200%      |
| 撤销/重做     | Ctrl+Z / Ctrl+Shift+Z | 操作历史          |
### 8.8.11 子对象嵌套编辑
* 当字段类型为 `Object` 或 `Array` 时，右侧面板显示"编辑子对象"按钮
* 点击后弹出嵌套画布 Modal，背景变暗，主画布不可操作
* Modal 顶部显示面包屑：`会员(Member) > 宠物档案(PetInfo) > 疫苗记录(VaccineRecord)`
* 完成编辑后，子 Schema 序列化嵌入父字段的 `subSchema` 属性
### 8.8.12 导出与导入
* 导出：将画布当前状态导出为 JSON 文件下载
* 导入：支持上传 JSON 文件或粘贴 JSON 文本，解析后渲染到画布（清空现有内容前二次确认）
* 版本历史抽屉：展示版本列表，支持预览和回滚
***
## 8.9 动态渲染实现流程
### 8.9.1 后端元数据存储
```json
{
  "program_code": "BRAND-A",
  "field_schema": {
    "type": "object",
    "properties": {
      "pet_name": { "title": "宠物名称", "type": "string", "x-component": "Input" },
      "member_level_index": { "title": "会员等级指数", "type": "number", "x-component": "NumberPicker" }
    }
  }
}
```
### 8.9.2 运行态渲染（React + Formily）
在会员详情页，前端通过 `useEffect` 拉取 Schema 并动态加载：
```javascript
import { createForm } from '@formily/core';
import { FormProvider } from '@formily/react';
import { SchemaField } from '@formily/react';
const DynamicMemberEditor = ({ programCode, memberId }) => {
  const [schema, setSchema] = useState(null);
  const form = useMemo(() => createForm(), []);
  useEffect(() => {
    api.getMemberSchema(programCode).then(res => setSchema(res.data.field_schema));
  }, [programCode]);
  if (!schema) return <Loading />;
  return (
    <FormProvider form={form}>
      <SchemaField schema={schema} />
      <Button onClick={async () => {
        const values = await form.submit();
        api.saveMemberExt(memberId, values);
      }}>保存</Button>
    </FormProvider>
  );
};
```
### 8.9.3 历史数据与 Schema 版本兼容渲染
```javascript
const DynamicMemberDetail = ({ memberData, currentSchema, schemaVersions }) => {
  // 1. 取数据写入时的 Schema 版本
  const dataVersion = memberData.ext_attributes?._schema_version || memberData.schema_version;
  // 2. 获取对应版本的 Schema 定义
  const effectiveSchema = schemaVersions[dataVersion] || currentSchema;
  // 3. 用对应版本的 Schema 渲染表单
  return (
    <FormProvider form={form}>
      <SchemaField schema={effectiveSchema} />
      {renderDeprecatedFields(memberData, dataVersion, currentSchema)}
    </FormProvider>
  );
};
```
### 8.9.4 复杂联动逻辑
对于"当宠物种类选择为'狗'时，显示'犬种明细'字段"这类联动，通过 `x-reactions` 声明：
```json
{
  "pet_type": { "title": "宠物类型", "x-component": "Select" },
  "dog_breed": {
    "title": "犬种明细",
    "x-component": "Input",
    "x-reactions": "{{ $self.visible = ($deps[0] === 'dog') }}",
    "x-dependencies": ["pet_type"]
  }
}
```
***
## 8.10 兼容性策略：字段废弃处理
### 8.10.1 废弃标记
运营在设计器中删除字段时，系统不执行 SQL DROP COLUMN，而是将 Schema 中的字段属性标记为 `deprecated: true`。
### 8.10.2 向下兼容展示
* **只读态**：历史会员数据中包含已废弃字段的，前端提供"显示历史遗留字段"开关
* **编辑态**：废弃字段默认从编辑表单中隐藏，防止误操作
* 当 `dataVersion < currentVersion` 时，前端自动显示"数据版本已过期"提示，并提供"升级到最新版本"按钮
### 8.10.3 DRL 引用检查
后端在废弃字段前检查所有 ACTIVE 规则的 DRL 内容：
java
```
@Transactional
public void deprecateField(String programCode, String entityType, String fieldKey) {
    List<Rule> activeRules = ruleRepo.findActiveByProgramCode(programCode);
    for (Rule rule : activeRules) {
        if (rule.getDrlContent().contains("extAttributes["" + fieldKey + ""]") ||
            rule.getDrlContent().contains("getExtString("" + fieldKey + "")") ||
            rule.getDrlContent().contains("getExtNumber("" + fieldKey + "")") ||
            rule.getDrlContent().contains("getPayloadString("" + fieldKey + "")") ||
            rule.getDrlContent().contains("getPayloadNumber("" + fieldKey + "")")) {
            throw new BusinessException("ERR_FIELD_IN_USE",
                "字段 [" + fieldKey + "] 被规则 [" + rule.getRuleId() + "] 引用，请先修改规则后再废弃该字段");
        }
    }
    schemaService.markFieldDeprecated(programCode, entityType, fieldKey);
}
```
***
## 8.11 全局交互规范
### 8.11.1 加载状态
所有数据请求必须覆盖以下状态，并在 UI 上体现：
| 状态   | UI 表现                         | 触发条件                  |
| ---- | ----------------------------- | --------------------- |
| 首次加载 | 页面级 Spin + 骨架屏                | 页面初次进入，无缓存数据          |
| 刷新加载 | 表格上方细进度条（`nprogress` 风格）      | 筛选条件变化、翻页、手动刷新        |
| 局部加载 | 按钮 `loading` 图标               | 提交表单、行内操作             |
| 空数据  | Empty 插图 + 引导文案 + 操作按钮        | 列表无数据                 |
| 错误   | Result 组件 + 错误信息 + 重试按钮       | 网络异常、接口 5xx           |
| 网络断开 | 全局顶部 Banner "网络连接已断开，正在重连..." | `navigator.onLine` 监听 |
### 8.11.2 表单交互规范
* **必填标识**：所有必填字段标签旁红色星号 `*`
* **实时校验**：输入框 `onBlur` 触发校验，错误信息红色文字显示在输入框下方
* **提交防抖**：提交按钮点击后立即 `loading`，禁用 2 秒内重复点击
* **未保存离开**：表单 `dirty` 状态为 true 时，浏览器 `beforeunload` 事件拦截，弹出确认对话框
* **批量操作**：表格上方出现"已选择 N 项"操作栏，提供批量删除、批量导出等按钮
### 8.11.3 移动端适配
* 左侧菜单在屏幕宽度 < 768px 时自动隐藏，通过汉堡按钮呼出 Drawer 抽屉
* 表格列在移动端自动折叠，只显示关键列（如会员ID+等级），点击展开查看详情
* 画布设计器在移动端降级为只读模式，不支持拖拽编辑
***
## 8.12 仪表盘
### 8.12.1 数据流
```text
useEffect on mount 和 programCode 变化
  → 并行请求：GET /api/dashboard/summary   → 指标卡片
               GET /api/dashboard/trend    → 趋势图数据
               GET /api/dashboard/alerts   → 告警列表
  → 轮询告警列表（每 30 秒静默刷新，不影响 loading 状态）
```
### 8.12.2 指标卡片
四张 `StatCard`：会员总数、今日新增、积分发放、积分核销。点击可跳转到对应列表页。
趋势图使用 `@ant-design/charts` 的 Line 图，支持日/周/月 Tab 切换。
### 8.12.3 告警类型与跳转映射
| 告警类型                     | 图标 | 跳转路径                       | 操作按钮 |
| ------------------------ | -- | -------------------------- | ---- |
| CASCADE_RECALC_BACKLOG | ⚠️ | `/system/logs?type=recalc` | 查看积压 |
| SPI_TIMEOUT_RATE_HIGH | 🚨 | `/system/spi-logs`         | 查看日志 |
| TENANT_POLLUTION        | 🔴 | `/system/audit`            | 查看详情 |
| RULE_COMPILE_FAILED    | ❌  | `/rules`                   | 编辑规则 |
***
## 8.13 各功能页面设计
### 8.13.1 会员列表页
* 搜索栏：手机号、会员ID、等级、状态、注册时间范围、**动态属性搜索**（根据当前 Program 的 Schema 动态生成输入框）
  * `String` → `Input`（模糊搜索）
  * `Number` → `InputNumber` + 范围选择
  * `Enum` → `Select` 多选
  * `Date` → `DatePicker.RangePicker`
  * 搜索参数统一以 `ext_{fieldKey}` 格式传递给后端
* 表格列：会员ID（可复制）、手机号（脱敏）、等级、状态、注册渠道、注册时间
* 批量导入：多步骤 Modal（下载模板 → 上传文件 → 预览数据 → 确认导入 → 进度条 → 结果报告）
* 行右键菜单：查看详情、合并会员
* 列头支持排序，列宽可拖拽调整并记忆在 localStorage
### 8.13.2 会员详情页
* 顶部信息卡片：会员ID、等级徽章、状态标签、手机号、注册渠道；操作按钮：[停用] [合并]
* Tab 页签：
**基本信息 Tab**：只读表单，展示静态字段和未废弃的动态字段。
**积分账户 Tab**：
* 按积分类型分组展示卡片，每个卡片显示可用余额（大号数字）、信用使用进度条、累计获得/消耗/过期
* 按钮：[查看流水] [调整积分]
* 调整积分弹窗：增减类型、积分类型、金额、原因（必填），二次确认
**交易流水 Tab**：
* 带筛选的表格（类型、时间范围）
* 点击核销流水 → 弹出 Drawer，上半部分核销详情，下半部分 `redemption_allocation` 明细表
* 支持点击批次ID跳转到发分批次详情
**渠道关联 Tab**：
* 表格展示绑定的所有渠道（渠道名、OpenID、绑定时间）
* 手动绑定按钮 → 弹窗选择渠道类型、输入 OpenID
* 解绑操作需二次确认
**属性扩展 Tab**：
* 嵌入 `DynamicFormRenderer` 组件
* 根据会员数据的 `schema_version` 加载对应 Schema 渲染表单
* 版本不一致时显示 Alert："当前数据为旧版 Schema (v1.0.0)，部分字段可能已变更。 [升级到最新版本]"
* 编辑模式下废弃字段隐藏，详情模式下废弃字段折叠在「历史遗留字段」区域
### 8.13.3 积分管理
**账户总览页**：按会员 ID 或手机号搜索，选择后展示其所有积分类型账户卡片。
**积分流水页**：全局流水列表（跨会员），支持按会员ID、积分类型、交易类型、时间范围筛选。导出功能异步生成 Excel。
### 8.13.4 规则引擎
**规则列表页**：左侧规则树（按 Program → 规则分组），右侧表格（状态、优先级等），操作：编辑、复制、停用/启用、沙箱测试。
**规则编辑器页**：
* 左栏：基本信息表单（名称、分组、优先级）
* 中栏：AI 辅助区域（自然语言输入 → 调用后端 LLM 接口 → 弹出预览 Modal）+ Monaco 编辑器（Drools 语法高亮、自动补全）
* 右栏：属性面板
**AI 生成交互**：输入自然语言 → 后端 LLM → 弹出预览 Modal（分析 + DRL 代码 + 测试用例）→ [采纳并编辑]。
**发布流程**：先沙箱测试 → 绿色直接发布 / 黄色警告可仍要发布 / 红色需强制放行（输入理由 + `forceOverride=true`）。
**沙箱回归测试页**：选择测试数据集 + 双列对比视图（基线 vs 候选），差异行高亮，汇总报告。
### 8.13.5 渠道配置
**渠道列表页**：表格（渠道标识、名称、映射模式、状态）+ 新建/编辑/测试连接。
**渠道编辑页**：基本信息表单 + Tab（映射配置 / 验签配置）。
**映射编辑器**：
* 可视模式：左源 JSON 树（可粘贴示例）→ 右目标 Schema 树（拖拽连线生成 JSONPath 映射）
* 脚本模式：Monaco 编辑器 + 测试输入/输出区 + [格式化] [测试运行] [保存]
### 8.13.6 系统设置
**角色权限管理**：角色列表 + 权限树（菜单和按钮级），权限节点数据从后端 `GET /api/permissions/tree` 获取。
**操作日志**：全局操作日志，按用户/操作类型/时间筛选，可展开查看详情 JSON。
**SPI 调用日志**：按渠道/时间/状态筛选，展开行显示请求/响应 JSON（语法高亮）。DEAD 事件提供 [重放] 按钮。
**租户污染审计**：跨租户访问尝试记录，红色警示图标，支持导出 CSV。
***
## 8.14 设计器与业务模块的衔接
### 8.14.1 会员详情页
会员详情页的属性扩展 Tab 直接消费设计器产出的 Schema：
* 前端请求 `GET /api/admin/schemas/MEMBER/current`
* 后端返回 LoyaltySchema → `DynamicFormRenderer` 渲染动态表单
* 保存时调用 `MemberExtService.saveMemberExtAttributes`（含双写 + 强标识检查）
### 8.14.2 SPI 网关
API 实体生成的接口自动注册到 SPI 网关：
* 第三方回调 → `SpiGatewayController` 接收
* 查找对应 API 实体配置 → JSON Schema 校验 → 联合校验脚本执行
* 映射引擎转换（API 实体 → TransactionEvent）→ 投递 EventBridge
### 8.14.3 规则引擎引用
业务实体的字段自动成为 DRL 可引用的 Fact 属性：
```drools
rule "宠物主人专属优惠"
when
    $event : EventFact( eventType == "ORDER" )
    $member : MemberFact( memberId == $event.memberId, getExtNumber("pets.length") > 0 )
then
    ActionCollector.awardPoints($event.getEventId(), 50, "PET_OWNER_REWARD");
end
```
### 8.14.4 画布初始化状态
用户首次进入 DB Schema 设计器时，画布预加载：
* 5 个系统实体节点（锁定，灰色背景 + 🔒）
* 3 条系统连线（锁定）
* 用户可在此基础上创建业务实体和 API 实体
***
本章规定了系统的前端设计语言、布局架构、核心组件、所有功能页面的交互设计、以及设计器与业务模块的衔接方式。前端开发时需严格遵循本章的色彩、字体、组件和交互规范。
## 第九章：多租户数据穿透与绝对防御体系
### 9.1 全局上下文持有器
```java
public class TenantContext {
    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();
    public static void set(String programCode) { CURRENT_TENANT.set(programCode); }
    public static String get() { return CURRENT_TENANT.get(); }
    public static void clear() { CURRENT_TENANT.remove(); }
}
```
**强制纪律**：每个请求进入时设置，处理完毕 `finally` 块中 clear。
### 9.2 四层绝对防御体系
**第一层：入口防御**：`TenantFilter` 强制要求所有请求携带 `program_code`。
**第二层：ORM 语法树拦截器**：自动为多租户表追加 `AND program_code = ?`。
**第三层：中间件沙箱**：
* Redis Key 格式：`tenant:{program_code}:member:{id}`
* EventBridge 消费前校验 `event.getProgramCode()` 一致性
**第四层：查询校验哨兵**：禁止 `repo.findById(id)`，必须 `repo.findByIdAndProgramCode(id, tenant)`。
### 9.3 异常处理与租户污染监控
* 防御审计：`TenantContext.clear()` 前检查线程残留
* 越权访问追踪：跨租户 404 记录异步审计日志
***
## 第十章：前后端 API 与技术规范
### 10.1 统一请求响应协议
```json
{
  "code": "SUCCESS",
  "message": "操作成功",
  "trace_id": "REQ-123",
  "data": { }
}
```
### 10.2 通用 Header 约束
* `X-Program-Code`：租户 Program 代码
* `X-Trace-Id`：链路追踪 ID
* `Authorization`：Bearer JWT Token
### 10.3 API 幂等性规范
前端传入 `X-Idempotency-Key`（UUID），后端 Redis 缓存处理结果 24 小时。
### 10.4 错误处理规范
禁止返回 HTTP 500。业务失败返回 HTTP 200，Body 中包含业务错误码。
***
## 第十一章：数据库物理模型设计
```sql
-- 1. 核心租户计划表
CREATE TABLE program (
    id SERIAL PRIMARY KEY,
    program_code VARCHAR(32) NOT NULL UNIQUE,
    config_json JSONB NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
-- 2. 会员主表 (双轨模型)
CREATE TABLE member (
    id BIGSERIAL PRIMARY KEY,
    program_code VARCHAR(32) NOT NULL,
    member_id VARCHAR(64) NOT NULL UNIQUE,
    tier_code VARCHAR(16),
    status VARCHAR(16) NOT NULL,
    ext_attributes JSONB,
    schema_version VARCHAR(16),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
-- 3. 全渠道唯一键表 (One-ID 核心)
CREATE TABLE member_unique_key (
    id BIGSERIAL PRIMARY KEY,
    program_code VARCHAR(32) NOT NULL,
    key_type VARCHAR(32) NOT NULL,
    key_value VARCHAR(128) NOT NULL,
    target_member_id VARCHAR(64) NOT NULL,
    is_strong BOOLEAN DEFAULT true,
    verified_at TIMESTAMPTZ,
    UNIQUE (program_code, key_type, key_value)
);
-- 4. 积分流水表 (分区表)
CREATE TABLE account_transaction (
    id BIGSERIAL,
    program_code VARCHAR(32) NOT NULL,
    member_id VARCHAR(64) NOT NULL,
    operation_key VARCHAR(100) NOT NULL,
    transaction_type VARCHAR(32) NOT NULL,
    amount NUMERIC(18, 4) NOT NULL,
    remaining_amount NUMERIC(18, 4),
    expires_at TIMESTAMPTZ,
    rule_id VARCHAR(64),
    rule_snapshot_id VARCHAR(64),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);
CREATE UNIQUE INDEX uk_at_idempotent_operation ON account_transaction(program_code, operation_key, created_at);
-- 5. 核销分摊明细
CREATE TABLE redemption_allocation (
    id BIGSERIAL PRIMARY KEY,
    program_code VARCHAR(32) NOT NULL,
    redemption_tx_id BIGINT NOT NULL,
    accrual_tx_id BIGINT NOT NULL,
    allocated_amount NUMERIC(18, 4) NOT NULL
);
-- 6. 会员账户表
CREATE TABLE member_account (
    account_id BIGSERIAL PRIMARY KEY,
    program_code VARCHAR(100) NOT NULL,
    member_id BIGINT NOT NULL,
    account_type VARCHAR(50) NOT NULL,
    overdraft_limit DECIMAL(20,4) DEFAULT 0,
    credit_limit DECIMAL(20,4) DEFAULT 0,
    credit_used DECIMAL(20,4) DEFAULT 0,
    total_accrued DECIMAL(20,4) DEFAULT 0,
    total_redeemed DECIMAL(20,4) DEFAULT 0,
    total_expired DECIMAL(20,4) DEFAULT 0,
    version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (program_code, member_id, account_type)
);
-- 7. 渠道适配器配置
CREATE TABLE channel_adapter_config (
    id BIGSERIAL PRIMARY KEY,
    program_code VARCHAR(100) NOT NULL,
    channel VARCHAR(50) NOT NULL,
    mapping_mode VARCHAR(20) NOT NULL DEFAULT 'VISUAL' CHECK (mapping_mode IN ('VISUAL', 'SCRIPT')),
    transform_script TEXT,
    spi_webhook_url VARCHAR(500),
    auth_config JSONB,
    api_entity_name VARCHAR(100),
    request_schema JSONB,
    response_schema JSONB,
    cross_validations JSONB,
    generated_controller BOOLEAN DEFAULT false,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (program_code, channel)
);
-- 8. SPI 调用审计日志
CREATE TABLE channel_spi_log (
    id BIGSERIAL PRIMARY KEY,
    program_code VARCHAR(32) NOT NULL,
    channel VARCHAR(50) NOT NULL,
    action VARCHAR(100) NOT NULL,
    request_id VARCHAR(100),
    http_headers JSONB,
    request_payload JSONB,
    response_payload JSONB,
    status VARCHAR(20) NOT NULL,
    execution_time_ms INT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
-- 9. 事件收件箱
CREATE TABLE event_inbox (
    id BIGSERIAL PRIMARY KEY,
    program_code VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(300) NOT NULL,
    payload JSONB NOT NULL,
    transform_logs JSONB,
    retry_count INT DEFAULT 0,
    max_retry INT DEFAULT 3,
    last_error TEXT,
    reject_reason VARCHAR(50),
    next_retry_at TIMESTAMPTZ,
    status VARCHAR(30) NOT NULL DEFAULT 'RECEIVED',
    created_at TIMESTAMPTZ DEFAULT NOW()
);
-- 10. 等级变更历史表
CREATE TABLE tier_change_log (
    id BIGSERIAL PRIMARY KEY,
    program_code VARCHAR(32) NOT NULL,
    member_id VARCHAR(64) NOT NULL,
    old_tier VARCHAR(16),
    new_tier VARCHAR(16) NOT NULL,
    change_reason VARCHAR(50) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_tier_log_member_time ON tier_change_log(member_id, changed_at);
-- 11. 规则版本快照表
CREATE TABLE rule_snapshot (
    id VARCHAR(64) PRIMARY KEY,
    program_code VARCHAR(32) NOT NULL,
    rule_id VARCHAR(64) NOT NULL,
    drl_content TEXT NOT NULL,
    salience INT,
    activation_group VARCHAR(50),
    created_at TIMESTAMPTZ DEFAULT NOW()
);
-- 12. Schema 版本管理表
CREATE TABLE program_schema (
    id BIGSERIAL PRIMARY KEY,
    program_code VARCHAR(32) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    version VARCHAR(16) NOT NULL,
    field_schema JSONB NOT NULL,
    entity_relations JSONB,
    chartdb_data JSONB,
    api_config JSONB,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (program_code, entity_type, version)
);
-- 索引
CREATE INDEX idx_all_tables_program_code ON member(program_code);
CREATE INDEX idx_all_tables_program_code_tx ON account_transaction(program_code, member_id);
```

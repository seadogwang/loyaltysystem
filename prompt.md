# 第一阶段：数字堡垒与标准化底座 — 标准提示词
## 系统提示（角色设定）
你是一位金融级 SaaS 系统的资深 Java 基础设施架构师。你信奉“防御性编程”，深知数据穿透是 SaaS 系统的生死线。你编写的所有代码都必须严格遵循设计文档中给出的伪代码契约，严禁私自简化或偏离防御逻辑。当前项目使用 **Spring Boot 3.x** 和 **Java 17**，构建工具使用 Maven/Gradle（可自行决定，但需在项目结构说明中注明），依赖管理遵循 Spring Boot 官方最佳实践。
## 任务背景
我们要构建一个下一代全渠道忠诚度管理 SaaS 平台。设计文档已经完成，现在需要从零开始落地系统的底层基础设施。本阶段为第一阶段，目标是建立最底层的安全防线与通讯协议，不包含任何业务逻辑。这一阶段的成果是所有后续业务的“空气”。
你将收到的文档章节如下（请先完整阅读再生成代码）：
- 第一章：引言与设计哲学（重点关注 1.2 防御性编程）
### 1.2 设计哲学 (Architectural Philosophy)
在架构演进过程中，我们遵循以下四项核心设计原则：
* **防御性编程 (Defensive SaaS)**：数据穿透是 SaaS 系统的生死线。我们从基础设施层面（数据库层租户过滤）到应用层（租户上下文强制持有）构建了四重隔离防线，确保租户间逻辑绝对隔离。
* **状态机至上 (State-Machine Driven)**：将积分流转、退款重算、身份合并等复杂逻辑建模为状态转换。一切业务结果通过事件溯源（Event Sourcing）而非仅仅是修改余额，确保“账户余额”在任何时间点都是可溯源、可审计的。
* **性能与一致性的动态平衡**：放弃低效的全局分布式锁，引入“瀑布流冲抵机制”与“异步无锁化补偿”，在保证业务逻辑正确的前提下，最大程度优化接口响应性能。
* **代码与配置的解耦 (Schema-Driven)**：前端界面、规则判定逻辑与持久化模型均由元数据（Metadata/JSON Schema）驱动。系统不仅是功能的容器，更是业务规则配置的执行引擎。
- 第九章：多租户数据穿透与绝对防御体系（精读 9.1 TenantContext 和 9.2 四层防御）
在多租户 SaaS 系统中，最严重的生产事故莫过于“数据穿透”（IDOR），即 A 租户的运营人员通过构造特殊的 API 请求，意外查看到 B 租户的会员数据。为了保障平台的金融级安全，我们建立了一套**四层物理防御架构**，从基础设施到底层代码逻辑进行全方位拦截。
### 9.1 全局上下文持有器 (TenantContext)
所有租户隔离的起点是 `TenantContext`。它利用 `ThreadLocal` 在当前执行线程中维护 `program_code`。
```java
public class TenantContext {
    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();
    public static void set(String programCode) { CURRENT_TENANT.set(programCode); }
    public static String get() { return CURRENT_TENANT.get(); }
    public static void clear() { CURRENT_TENANT.remove(); }
}
```
**强制纪律**：
* 每个 HTTP 请求进入时，`TenantFilter` 必须解析 Token 获取 `program_code` 并放入 `TenantContext`。
* 每个请求处理完毕（无论是成功还是异常），`finally` 块中必须调用 `TenantContext.clear()`，防止线程复用时污染下一个租户的上下文。
### 9.2 四层绝对防御体系
#### 第一层：入口防御 (Filter/Interceptor)
在 API 请求的最外层（如 `TenantFilter`），强制要求所有 Request 参数必须携带 `program_code`。若缺失，直接拒绝访问。
#### 第二层：ORM 语法树拦截器 (AST Interceptor)
这是最关键的防线。研发不需要在每一条 SQL 中手写 `AND program_code = ?`，而是通过底层拦截器自动注入：
```java
// 使用 MyBatis-Plus 或 JPA 拦截器示例
public class TenantSqlInterceptor implements InnerInterceptor {
    @Override
    public void beforeQuery(...) {
        // 自动解析当前执行的 SQL，判断是否为多租户表
        if (isMultiTenantTable(tableName)) {
            // 自动追加 WHERE program_code = '...'
            String tenantFilter = " AND program_code = '" + TenantContext.get() + "'";
            sql = sql + tenantFilter;
        }
    }
}
```
**防御意义**：即便开发人员写了 `memberRepo.findAll()`，系统也会自动变为 `SELECT * FROM member WHERE program_code = 'XYZ'`，物理杜绝穿透。
#### 第三层：中间件沙箱 (Cache & MQ Isolation)
* **Redis 防穿透**：Cache 统一通过 `TenantKeyGenerator` 生成，确保 Key 格式为 `tenant:{program_code}:member:{id}`。调用 `RedisTemplate` 时，若未感知租户前缀，统一抛出 Runtime 异常。
* **Kafka/EventBridge 校验**：消费者在 `handleEvent` 前，强制校验 `event.getProgramCode()` 是否与当前系统上下文一致，如果不一致，立即丢弃并触发熔断告警。
#### 第四层：查询校验哨兵 (Query Sentinel)
在 Service 层，禁止使用底层的 `repo.findById(Long id)`。所有查询方法必须强制要求 `program_code` 作为必传参数：
```java
// 错误写法
public Member getMember(Long id) {
    return memberRepo.findById(id); // 严禁使用，无法感知租户
}
// 正确写法 (Query Sentinel)
public Member getMember(Long id) {
    String tenant = TenantContext.get();
    return memberRepo.findByIdAndProgramCode(id, tenant); // 必须带上租户
}
```
- 第二章：2.2 开发轻量化与消息队列环境隔离（精读 EventBridge 接口定义与 LocalEventBus 伪代码）
#### 2.2.2 Dev环境：本地内存队列实现 (LocalEventBus)
在 dev 环境下，系统不连接任何外部 MQ，而是利用 Java 内存队列与线程池，完美模拟 Kafka 的分区有序消费特性。这样开发人员可以在本地“单机”验证高并发下的时序逻辑。
```java
@Component
@Profile("dev")
@Slf4j
public class LocalEventBus implements EventBridge {
    
    // 模拟 Kafka 的 Partition 数量，创建多个单线程池
    private final int VIRTUAL_PARTITIONS = 8;
    private final ExecutorService[] partitionExecutors;
    public LocalEventBus() {
        this.partitionExecutors = new ExecutorService[VIRTUAL_PARTITIONS];
        for (int i = 0; i < VIRTUAL_PARTITIONS; i++) {
            // 【核心设计】每个虚拟分区单线程执行，保证落入同一分区的 memberId 事件绝对有序
            this.partitionExecutors[i] = Executors.newSingleThreadExecutor(
                new ThreadFactoryBuilder().setNameFormat("local-mq-partition-" + i + "-%d").build()
            );
        }
    }
    @Override
    public void publish(String topic, String partitionKey, BaseDomainEvent event) {
        // 计算当前 partitionKey 对应的虚拟分区
        int partition = Math.abs(partitionKey.hashCode()) % VIRTUAL_PARTITIONS;
        
        // 异步提交到对应的本地单线程池中执行，非阻塞主线程
        partitionExecutors[partition].submit(() -> {
            try {
                log.info("[LocalMQ] 消费分区 {}: Topic={}, Key={}", partition, topic, partitionKey);
                // 路由到 Spring 容器中对应的本地监听器进行消费
                LocalEventRouter.route(topic, event);
            } catch (Exception e) {
                log.error("[LocalMQ] 消费异常，本地可直接抛出或进入本地重试表", e);
            }
        });
    }
}
```
- 第十章：前后端 API 与技术规范（精读 10.1 统一响应体、10.2 Header 约束、10.3 幂等性规范）
### 10.1 统一请求响应协议 (Uniform Response Schema)
为了降低前端适配成本，所有 API 必须采用统一的包裹结构，禁止直接返回数据实体。
```json
{
  "code": "SUCCESS",       // 业务状态码
  "message": "操作成功",    // 给用户看的提示
  "trace_id": "REQ-123",  // 全链路追踪 ID
  "data": { ... }         // 业务数据
}
```
### 10.2 通用 Header 约束
每个 API 请求必须携带以下 Header：
* `X-Program-Code`: 当前操作的租户 Program 代码。
* `X-Trace-Id`: 链路追踪 ID（若无，后端自动生成）。
* `Authorization`: Bearer JWT Token。
### 10.3 API 幂等性规范
所有涉及积分变动、订单回流的 POST/PUT 接口，必须实现幂等。
* 强制要求前端传入 `X-Idempotency-Key`（UUID）。
* 后端利用 Redis 存储该 Key 及其处理结果，过期时间 24 小时。
* 前置防重流程：
```java
public Response handleRequest(String idempotencyKey, RequestBody body) {
    // 1. Redis 检查：是否已处理过此幂等键
    String cacheKey = "idempotent:" + TenantContext.get() + ":" + idempotencyKey;
    Response cached = redisTemplate.opsForValue().get(cacheKey);
    if (cached != null) {
        return cached;  // 直接返回上次处理结果
    }
    
    // 2. 未处理过，执行业务逻辑
    Response result = doBusinessLogic(body);
    
    // 3. 将结果缓存（24 小时过期）
    redisTemplate.opsForValue().set(cacheKey, result, 24, TimeUnit.HOURS);
    return result;
}
```
**注意：**依赖 created_at 的 DB 唯一索引仅作为最后防线，不能替代 Redis 前置防重。原因：account_transaction 为分区表，唯一索引必须包含 created_at，同一条业务请求若在不同秒级时间写入，created_at 可能不同，导致唯一索引失效。
## 任务要求
请基于上述章节，生成以下五项“零业务”基础设施代码。每一项都必须严格对照文档中的设计约束。
### 1. TenantContext 持有器与 TenantFilter
- 使用 `ThreadLocal` 实现 `TenantContext`，提供 `set(programCode)`、`get()` 和 `clear()` 方法。
- 实现一个 `Filter`（或 Spring 的 `HandlerInterceptor`），在请求到达时从 HTTP Header 中提取 `X-Program-Code` 并设置到 `TenantContext`，在 `finally` 块中必须调用 `TenantContext.clear()` 防止线程池污染。
- 如果 Header 缺失，应返回 HTTP 400 错误（统一使用后续的响应体格式）。
### 2. ORM 多租户拦截器（MyBatis-Plus 或 JPA）
- 要求实现 MyBatis-Plus 的 `InnerInterceptor`（优先）或 JPA 的拦截机制，在构建 SQL 时自动为所有多租户表追加 `AND program_code = ?` 条件。
- 必须提供一种**标记机制**来识别哪些表是多租户表（例如自定义注解 `@MultiTenant` 或接口 `MultiTenantEntity`）。拦截器只对标记过的表注入租户条件。
- 拦截器应从 `TenantContext.get()` 获取当前租户，若为空则抛出严重异常（防止漏防）。
### 3. GlobalExceptionHandler 与统一响应体 `ApiResponse<T>`
- 所有 Controller 的返回值统一包裹为 `ApiResponse<T>` 格式：
```json
  {
    "code": "SUCCESS",
    "message": "操作成功",
    "trace_id": "REQ-123",
    "data": { ... }
  }
```
* 实现一个 `@RestControllerAdvice` 全局异常处理器，捕获所有异常并转换为 `ApiResponse<Void>`，HTTP 状态码始终为 **200**（业务错误通过 `code` 和 `message` 体现，如 `ERR_INSUFFICIENT_POINTS`）。
* 为常见的业务异常定义基类 `BusinessException`（含错误码 `code` 和消息），全局异常处理器能识别它并返回对应的错误码。
* 处理参数校验异常 `MethodArgumentNotValidException`，返回 `ERR_BAD_REQUEST` 等标准错误码。
### 4. EventBridge 接口与 LocalEventBus 实现（Dev 环境）
* 严格遵照文档 2.2.1 定义的接口契约实现 `EventBridge` 接口：
```java
  public interface EventBridge {
      void publish(String topic, String partitionKey, BaseDomainEvent event);
  }
```
* 按照文档 2.2.2 的伪代码实现 `LocalEventBus`（使用 `@Profile("dev")`），关键点：
  * 内部定义 8 个虚拟分区（`VIRTUAL_PARTITIONS = 8`）。
  * 每个分区对应一个**单线程的 `ExecutorService`**，保证落入同一分区的 `memberId` 事件绝对串行消费。
  * 分区路由算法：`Math.abs(partitionKey.hashCode()) % VIRTUAL_PARTITIONS`。
  * 消费逻辑通过 `LocalEventRouter.route(topic, event)` 模拟；请提供 `LocalEventRouter` 的骨架实现（可基于 Spring 容器根据事件类型查找对应的 `@EventListener` 或 `Consumer` Bean）。
  * 必须包含异常捕获和日志记录。
### 5. 幂等性拦截器（基于 Header + Redis）
* 实现一个拦截器或 AOP 切面，对所有标注 `@Idempotent` 的 Controller 方法进行幂等控制。
* 从前端传入的 `X-Idempotency-Key` Header 中获取幂等键。
* 使用 Redis（假设已配置 `RedisTemplate<String, Object>`）进行幂等缓存：Key 格式为 `idempotent:{programCode}:{key}`，缓存该 Key 对应的完整响应体，过期时间 24 小时。
* 如果 Redis 中已存在该 Key，则直接返回缓存的 `ApiResponse`，不再执行方法体。
* 如果方法执行成功，将最终 `ApiResponse` 存入 Redis。
* 如果 Header 缺失，且方法标注了 `@Idempotent`，应返回错误。
## 输出要求
* 生成完整的 Java 代码文件（包含 package 声明、import 语句、类定义和方法实现）。
* 项目结构采用标准的 Spring Boot 分层结构，包名建议为 `com.example.loyalty`。
* 在代码中适当添加注释，说明关键防御逻辑和与设计文档的对应点。
* 同时输出所需的 Maven 依赖片段（如 Redisson、MyBatis-Plus、Hibernate 相关），以确保项目可编译。
请开始生成代码。





第一阶段的基础设施代码已完成。现在进入第二阶段，请阅读以下文档章节并生成代码。
# 第二阶段：基础模型与动态实体引擎 — 标准提示词

## 系统提示（角色设定）
你是一位资深 Java 后端开发专家与数据库架构师。你精通 Spring Data JPA、PostgreSQL 的高级特性（特别是 JSONB 类型），并对 SaaS 多租户环境下的实体建模有深刻理解。你编写的代码严格遵循设计文档的伪代码契约，且在第一阶段已经建立的多租户防御体系基础上进行扩展。当前项目使用 **Spring Boot 3.x**、**Java 17** 和 **PostgreSQL 15**。

## 任务背景
我们正在构建一个下一代全渠道忠诚度管理 SaaS 平台，第一阶段已完成底层安全防线与标准化底座的建设，包括：
- `TenantContext`：基于 ThreadLocal 的租户上下文持有器
- 多租户 ORM 拦截器：自动为多租户表注入 `program_code` 过滤条件，通过 `@MultiTenant` 注解识别
- `ApiResponse<T>`：统一响应体格式
- `EventBridge`：事件总线抽象接口及其 LocalEventBus 实现

本阶段是第二阶段，目标是将设计文档中的核心物理表结构落地为 Java 实体类，并实现“静态字段 + 动态 JSONB”的双轨模型。

你将收到的文档章节如下（请先完整阅读再生成代码）：
- 第十一章：数据库物理模型设计（精读 `program`、`member`、`member_unique_key` 三张表及索引）
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
    schema_version VARCHAR(16),           -- ← 新增：写入该数据时的 Schema 版本号
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. 全渠道唯一键表 (One-ID 核心)
CREATE TABLE member_unique_key (
    id BIGSERIAL PRIMARY KEY,
    program_code VARCHAR(32) NOT NULL,
    key_type VARCHAR(32) NOT NULL, -- MOBILE, WECHAT_UNIONID...
    key_value VARCHAR(128) NOT NULL,
    target_member_id VARCHAR(64) NOT NULL,
    UNIQUE (program_code, key_type, key_value)
);
- 第三章：3.2 核心实体的静态与动态边界（精读 `Member`、`TransactionEvent` 的 Java 伪代码）
### 3.2 核心实体的静态与动态边界 (Fixed vs. Dynamic Fields)
为了让一套底层代码同时支撑“美妆行业（需要肤质、生肖属性）”和“汽车行业（需要车牌号、购车门店属性）”，平台对核心实体采取“**核心强依赖字段 + 动态扩展字段 (JSONB)**”的双轨模型。
**研发与 AI 编码纪律**：严禁随意在数据库表和 Java Entity 中增加业务字段。
#### 3.2.1 会员实体边界 (Member)
* **静态字段 (Fixed)**：系统路由和核心引擎强依赖的字段，必须作为独立的物理列。
  * `id` (底层自增主键)
  * `program_code` (归属计划)
  * `member_id` (业务主键/对外暴露的会员号，雪花算法生成)
  * `status` (状态：`ENROLLED`, `SUSPENDED`, `MERGED`, `DEACTIVATED`)
  * `tier_code` (当前等级代码)
* **动态字段 (Dynamic)**：所有因行业而异的字段统一存入 `ext_attributes` (PostgreSQL JSONB)。前端采用 Formily 引擎配合 JSON Schema 动态渲染。
```java
// 会员实体 JPA 伪代码示例
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
    // 动态属性，映射为 JSONB
    @Type(type = "jsonb")
    @Column(name = "ext_attributes", columnDefinition = "jsonb")
    private Map<String, Object> extAttributes = new HashMap<>();
}
```
#### 3.2.2 交易事件边界 (TransactionEvent)
* **静态字段**：`event_id`, `member_id`, `event_type` (枚举：`ORDER`, `ENROLL`, `CUSTOM`), `channel`, `event_time`, `idempotent_key`。
* **动态字段**：如 `order_amount`, `pay_type` 等统统塞入 `payload` JSONB 字段。

- 第三章：3.2.3 动态属性写入规范（精读 Schema 版本下沉逻辑）
#### 3.2.3 动态属性写入规范（Schema 版本下沉）
每次创建或更新会员的 ext_attributes 时，必须同步写入当前生效的 Schema 版本号：
```java
public void saveMemberExtAttributes(String memberId, Map<String, Object> extAttributes) {
    Member member = memberRepo.findByMemberId(memberId);
    
    // 获取当前 Program 生效的会员 Schema 版本
    String currentSchemaVersion = schemaService.getCurrentVersion(member.getProgramCode(), "MEMBER");
    
    // 注入元字段到 JSON 内部
    extAttributes.put("_schema_version", currentSchemaVersion);
    
    member.setExtAttributes(extAttributes);
    member.setSchemaVersion(currentSchemaVersion);  // 同时写入独立字段，便于 SQL 查询
    memberRepo.save(member);
}
```
双写（独立字段 + JSON 内部元字段）的设计意图：
member.schema_version：用于 SQL 层面的批量查询和统计（如「找出所有仍在使用 v1.0 版本 Schema 的会员数」）
ext_attributes._schema_version：用于前端渲染时的版本识别，无需额外查表
`member` 表中的 ext_attributes JSON 内部也冗余存储一份（便于前后端统一解析）：
业务约定：每次写入 ext_attributes 时，在 JSON 根节点自动注入 _schema_version 元字段：
```json
{
  "_schema_version": "v1.2.0",
  "pet_name": "旺财",
  "shoe_size": 42
}
```
## 任务要求
请基于上述章节，生成以下三大类产出。每一项都必须严格对照文档中的设计约束。

### 1. JPA Entity 定义

#### 1.1 Program 实体
- 表名：`program`
- 字段：
  - `id`：自增主键（`GenerationType.IDENTITY`）
  - `program_code`：唯一，非空，`VARCHAR(32)`
  - `config_json`：`JSONB` 类型，存储 Program 的非结构化配置
  - `created_at`：时间戳，默认当前时间
- 类上添加 `@MultiTenant` 注解（与第一阶段的多租户拦截器配合），标记此表为多租户表。

#### 1.2 Member 实体
- 表名：`member`
- **静态字段**（强类型物理列）：
  - `id`：自增主键（`GenerationType.IDENTITY`）
  - `program_code`：`VARCHAR(32)`，非空
  - `member_id`：`VARCHAR(64)`，非空，唯一约束
  - `tier_code`：`VARCHAR(16)`，当前等级代码
  - `status`：`VARCHAR(16)`，非空，会员状态（`ENROLLED`, `SUSPENDED`, `MERGED`, `DEACTIVATED`）
  - `schema_version`：`VARCHAR(16)`，记录写入该数据时的 Schema 版本号
  - `created_at`：时间戳，默认当前时间
- **动态字段**：
  - `ext_attributes`：`JSONB` 类型，`Map<String, Object>`，存储所有行业扩展属性
- 类上添加 `@MultiTenant` 注解。
- 提供与文档 3.2.1 中伪代码一致的结构。

#### 1.3 MemberUniqueKey 实体
- 表名：`member_unique_key`
- 字段：
  - `id`：自增主键（`GenerationType.IDENTITY`）
  - `program_code`：`VARCHAR(32)`，非空
  - `key_type`：`VARCHAR(32)`，非空（如 `MOBILE`, `WECHAT_UNIONID`）
  - `key_value`：`VARCHAR(128)`，非空
  - `target_member_id`：`VARCHAR(64)`，非空，指向 `member.member_id`
- 添加表级唯一约束：`UNIQUE (program_code, key_type, key_value)`
- 类上添加 `@MultiTenant` 注解。

#### 1.4 TransactionEvent 实体（基础骨架）
- 表名：`transaction_event`
- **静态字段**：
  - `id`：自增主键
  - `program_code`：非空
  - `event_id`：事件唯一标识
  - `member_id`：关联会员
  - `event_type`：事件类型枚举（`ORDER`, `ENROLL`, `CUSTOM`）
  - `channel`：渠道标识
  - `event_time`：事件发生时间
  - `idempotent_key`：幂等键
- **动态字段**：
  - `payload`：`JSONB` 类型，`Map<String, Object>`，存储 `order_amount`、`pay_type` 等扩展数据
- 类上添加 `@MultiTenant` 注解。

### 2. JSONB 类型处理器

#### 2.1 实现 JPA 的 JSONB 映射
- 使用 Hibernate 6 的 `@JdbcTypeCode(SqlTypes.JSON)` 注解，或自定义 `AttributeConverter<Map<String, Object>, String>`，将 Java 的 `Map<String, Object>` 映射为 PostgreSQL 的 `JSONB` 列。
- 确保转换器能正确序列化/反序列化 JSON 中的数字、字符串、嵌套对象和数组。
- 如果使用自定义 Converter，需通过 `@Convert` 注解标注在 Entity 字段上。

### 3. Schema 版本下沉服务（MemberExtService）

#### 3.1 核心逻辑
严格按照文档 3.2.3 的伪代码实现 `saveMemberExtAttributes` 方法：
- 接收 `memberId` 和 `extAttributes`（`Map<String, Object>`）。
- 调用 `SchemaService.getCurrentVersion(programCode, "MEMBER")` 获取当前 Program 的会员 Schema 版本号。
- **双写逻辑**：
  - 在 `extAttributes` 内部注入 `_schema_version` 字段（值为当前版本号）。
  - 同时将版本号写入 `Member.schema_version` 独立字段。
- 保存 Member 实体。

#### 3.2 SchemaService 接口定义
- 提供 `getCurrentVersion(String programCode, String entityType)` 方法的骨架，返回 `String` 类型的版本号（如 `"v1.2.0"`）。
- 当前可返回一个硬编码的默认值（如 `"v1.0.0"`），后续阶段会连接数据库获取真实版本。

## 输出要求
- 生成完整的 Java 代码文件（包含 `package` 声明、`import` 语句、类定义和方法实现）。
- 包结构建议为 `com.example.loyalty.domain`（存放 Entity）和 `com.example.loyalty.service`（存放 Schema 相关服务）。
- 在代码中适当添加注释，说明：
  - 哪些字段是静态字段（用于核心路由和引擎依赖）
  - 哪些字段是动态字段（JSONB 扩展）
  - `@MultiTenant` 注解的作用（与第一阶段拦截器配合）
  - 双写逻辑的必要性（独立字段用于 SQL 查询，JSON 内部字段用于前端解析）
- 同时输出 Maven 依赖片段（如 `hibernate-jpamodelgen`、`jackson-databind` 等），确保 JSONB 处理可正常编译。
- 若有需要补充的数据库索引建议（如为 `member.program_code` 创建索引），请在注释中说明。

## 上下文衔接约定
- 第一阶段已实现 `TenantContext` 和 `@MultiTenant` 注解，请确保本阶段的 Entity 正确标注该注解。
- 第一阶段已实现 `ApiResponse<T>`，本阶段无需重复生成。
- 本阶段生成的所有类应保持与第一阶段相同的 `com.example.loyalty` 基础包名。

请开始生成代码。



# 第三阶段：全渠道 One-ID 身份合并引擎 — 标准提示词

## 系统提示（角色设定）
你是一位高并发业务逻辑专家，擅长分布式锁、数据库唯一约束与竞态条件处理。你深刻理解在多渠道会员通场景下，如何保证“同一用户”在并发注册时只创建唯一身份。你编写的代码严格遵循设计文档的伪代码契约，并完全基于前两阶段已建立的多租户防御体系和实体模型进行扩展。当前项目使用 **Spring Boot 3.x**、**Java 17**、**PostgreSQL 15** 和 **Redisson**。

## 任务背景
我们正在构建一个下一代全渠道忠诚度管理 SaaS 平台。前两个阶段已完成：
- **第一阶段**：实现了 `TenantContext`、多租户 ORM 拦截器（`@MultiTenant`）、`ApiResponse<T>`、`EventBridge` 事件总线（含 `LocalEventBus` Dev 实现）、幂等性拦截器。
- **第二阶段**：定义了核心 JPA 实体，包括 `Program`、`Member`（含 `ext_attributes` 双轨模型）、`MemberUniqueKey`（含联合唯一约束）、`TransactionEvent`，以及 `MemberExtService`（Schema 版本下沉逻辑）。

本阶段是第三阶段，目标是对外提供全渠道入会（Enrollment）接口，通过分布式锁和唯一键辅助表实现 One-ID 身份合并，彻底解决“同一手机号从天猫和微信同时注册”产生的并发冲突。

你将收到的文档章节如下（请先完整阅读再生成代码）：
- 第三章：3.4 全渠道身份识别与会员合并（精读 3.4.1 强弱标识与辅助唯一键表、3.4.2 并发入会与合并策略伪代码、3.4.3 显式合并与资产转移）
### 3.4 全渠道身份识别与会员合并 (Identity & Merge Strategy)
在全渠道体系下（天猫、微信、抖音同时引流），用户身份的统一（One-ID）是会员域最核心的挑战。系统采用 `member_unique_key` 表与分布式锁结合，保证并发场景下不产生“幽灵账号”。
#### 3.4.1 强弱标识与辅助唯一键表
系统不以手机号或 UnionID 作为会员主表的物理主键或唯一约束。```drools
而是建立一张辅助表 `member_unique_key (program_code, key_type, key_value, target_member_id)`。
* 记录 A：`("BRAND-A", "MOBILE", "13800000000", 8821)`
* 记录 B：`("BRAND-A", "WECHAT_UNIONID", "oxc123...", 8821)`
#### 3.4.2 并发入会与合并策略 (Concurrent Merge Flow)
当外部渠道（如天猫 SPI）发来一个包含 `mix_mobile` 和 `ouid` 的入会请求时，处理流转如下：
```java
public void processEnrollment(EnrollmentRequest request) {
    String programCode = request.getProgramCode();
    String mobile = decrypt(request.getMobile());
    
    // 1. 获取细粒度分布式锁，防止双渠道同时触发注册导致同一手机号创建两个 Member
    String lockKey = "loyalty:" + programCode + ":enroll:" + HashUtil.md5(mobile);
    RLock lock = redissonClient.getLock(lockKey);
    
    try {
        lock.lock(5, TimeUnit.SECONDS);
        
        // 2. 多维度交集匹配查询 (Intersection Matching)
        Long existingMemberId = uniqueKeyRepo.findMemberId(programCode, "MOBILE", mobile);
        
        if (existingMemberId != null) {
            // 场景 A：老会员，执行信息补全或渠道绑定
            bindNewChannel(existingMemberId, request.getChannel(), request.getChannelOpenId());
            return;
        }
        
        // 场景 B：新会员，执行注册
        Long newMemberId = generateSnowflakeId();
        Member newMember = new Member(programCode, newMemberId, request.getExtAttributes());
        memberRepo.save(newMember);
        
        // 写入唯一键辅助表
        uniqueKeyRepo.save(new UniqueKey(programCode, "MOBILE", mobile, newMemberId));
        uniqueKeyRepo.save(new UniqueKey(programCode, request.getChannel(), request.getChannelOpenId(), newMemberId));
        
        // 3. 发布内部入会事件，交由规则引擎判定是否送“新人礼”
        eventBridge.publish("loyalty-events", newMemberId.toString(), new MemberEnrolledEvent(newMemberId));
        
    } finally {
        lock.unlock();
    }
}
```
##### 3.4.2.1 极端竞态防护与兜底机制
当两个不同渠道（如天猫和微信）并发传入同一手机号的入会请求时，若锁 Key 不一致（一个用 MOBILE 哈希，一个用 WECHAT_UNIONID 哈希），可能导致两个线程同时判定“无记录”，分别创建会员账号。

**防护策略：**
1.统一锁 Key：只要请求中包含手机号，分布式锁的 Key 统一基于手机号哈希生成，忽略渠道标识。
```java
// 正确：统一用手机号加锁
String lockKey = "loyalty:" + programCode + ":enroll:" + HashUtil.md5(mobile);

// 错误：不要把渠道拼入锁 Key
// String lockKey = "loyalty:" + programCode + ":enroll:" + channel + ":" + mobile;
```
2.数据库唯一约束兜底：member_unique_key 表已设置 UNIQUE (program_code, key_type, key_value)，当两个线程最终同时尝试插入时，后者会触发 DataIntegrityViolationException。
```java
try {
    uniqueKeyRepo.save(new UniqueKey(programCode, "MOBILE", mobile, newMemberId));
} catch (DataIntegrityViolationException e) {
    // 并发冲突：另一个线程已抢先创建，转为查询已创建的会员并绑定渠道
    Long existingMemberId = uniqueKeyRepo.findMemberId(programCode, "MOBILE", mobile);
    bindNewChannel(existingMemberId, request.getChannel(), request.getChannelOpenId());
    // 回滚本次创建的重复会员账号
    memberRepo.deleteById(newMemberId);
    return;
}
```
3.新人礼防重：MemberEnrolledEvent 的消费端需基于 memberId + eventType 做幂等校验，确保即使短时间创建了重复会员，新人礼也只发放一次。
#### 3.4.3 显式合并与资产转移 (Explicit Merge)
如果因为极端情况或人工客服操作，发现了两个 `member_id` 实际上属于同一个人，触发显式合并 (`MERGE` 操作)：
* 选取一个作为主账号（通常是注册早或等级高的），另一个状态置为 `MERGED`。
* 积分资产处理：根据 Program 配置，通常将 `CONSUMPTION` 积分进行算数累加。
* 等级资产处理：取两个账号中等级序列（Sequence）最高的一方作为新等级。
* 被合并账号的 `member_unique_key` 记录全部重定向指向主账号 `target_member_id`。
## 任务要求
请基于上述章节，生成以下三大类产出。每一项都必须严格对照文档中的设计约束。

### 1. 分布式锁工具类封装
- 基于 Redisson 封装一个 `LockUtil` 或 `DistributedLock` 组件，提供带超时时间的锁获取/释放方法。
- 锁 Key 的生成规则必须与文档一致：**强制使用手机号哈希**，格式为 `"loyalty:" + programCode + ":enroll:" + HashUtil.md5(mobile)`。严禁拼接渠道（channel）前缀。
- 建议提供一个函数式接口（如 `executeWithLock(String key, long leaseTime, TimeUnit unit, Supplier<T> task)`），便于在 Service 层使用 try-with-resources 或回调方式。

### 2. 入会服务 `EnrollmentService`
#### 2.1 入会请求对象 `EnrollmentRequest`
- 字段至少包含：`programCode`、`mobile`（加密，可使用 `decrypt()` 方法脱敏）、`channel`、`channelOpenId`、`extAttributes`。
- 提供 `getMobileHash()` 等辅助方法（用于锁 Key 生成）。

#### 2.2 核心入会方法 `processEnrollment`
严格遵循文档 3.4.2 的伪代码实现完整流程：
- **锁**：基于手机号哈希获取分布式锁（使用第 1 步封装的工具）。
- **匹配**：从 `member_unique_key` 表查询 `(program_code, "MOBILE", mobile)` 是否已存在，若存在则走“老会员绑定渠道”分支；否则走“新会员注册”分支。
- **新会员注册**：
  - 使用雪花算法生成 `memberId`（可使用 Hutool 的 `IdUtil.getSnowflake()` 或 `SnowflakeIdWorker`）。
  - 插入 `member` 表。
  - 向 `member_unique_key` 表同时插入两条记录：
    - `("MOBILE", mobile, memberId)`
    - `(channel, channelOpenId, memberId)`
  - 发布 `MemberEnrolledEvent` 内部事件（通过 `EventBridge`）。
- **老会员渠道绑定**：调用 `bindNewChannel(existingMemberId, channel, channelOpenId)`，将新渠道的 `UniqueKey` 与已有 `memberId` 关联。
- **异常处理与兜底**：
  - 捕获数据库唯一键冲突异常（`DataIntegrityViolationException` 或 `DuplicateKeyException`），在 catch 块中执行“查询已有会员 → 绑定渠道 → 删除本次创建的重复会员”的补偿逻辑。**严禁直接抛出异常导致 API 报错**。
  - 保证 `Member` 和 `MemberUniqueKey` 的保存操作在同一个数据库事务中（`@Transactional`）。

#### 2.3 渠道绑定方法 `bindNewChannel`
- 私有方法，负责插入 `member_unique_key` 记录（若已存在则忽略）。
- 可封装为“插入若忽略”（INSERT … ON CONFLICT DO NOTHING）逻辑，或使用 `existsByProgramCodeAndKeyTypeAndKeyValue` 预判断。

#### 2.4 新人礼防重说明
- `MemberEnrolledEvent` 的消费端（不在本阶段实现）后续会基于 `memberId + eventType` 做幂等，但本阶段应确保事件体包含 `memberId`、`programCode`、`channel` 等完整上下文。

### 3. 显式合并接口 `MergeService`（骨架）
- 提供方法 `mergeMembers(String programCode, String mainMemberId, String duplicateMemberId)`。
- 逻辑暂按文档 3.4.3 的简化描述实现骨架：
  - 将被合并会员状态改为 `MERGED`。
  - 将其所有 `member_unique_key` 记录的 `target_member_id` 重定向为主账号 ID。
  - 积分转移和等级选取（具体资产合并）留待第四阶段实现，此处仅保留注释。
- 操作需加分布式锁（锁定两个会员 ID），保证合并过程串行。

## 输出要求
- 生成完整的 Java 代码文件，包结构建议：
  - `com.example.loyalty.util`：`LockUtil`、`HashUtil`
  - `com.example.loyalty.dto`：`EnrollmentRequest`、`MemberEnrolledEvent`
  - `com.example.loyalty.service`：`EnrollmentService`、`MergeService`
- 在代码中适当添加注释，说明：
  - 分布式锁 Key 为何必须基于手机号哈希
  - 唯一键冲突补偿逻辑的必要性（竞态兜底）
  - 事务边界与 `EventBridge` 发布的时机
- 如果涉及 Spring 依赖注入，需明确 `@Autowired` 或构造函数注入的依赖项（如 `MemberRepository`、`MemberUniqueKeyRepository`、`EventBridge`、`RedissonClient`）。
- 输出所需的 Maven 依赖片段（如 `hutool-core` 用于雪花算法，`redisson-spring-boot-starter`），确保代码可编译。
- 若需要自定义异常（如 `EnrollmentException`），请一并定义，并确保异常能按第一阶段的 `GlobalExceptionHandler` 规范返回 `ApiResponse`。

## 上下文衔接约定
- 第二阶段已生成以下 Repository 接口（你需要基于它们编写代码）：
  - `MemberRepository`：包含 `findByMemberId(String memberId)`、`save(Member)`、`deleteById(Long id)` 等。
  - `MemberUniqueKeyRepository`：包含 `findMemberId(programCode, keyType, keyValue)`、`save`、`findByTargetMemberId` 等。
- 第一阶段已实现 `EventBridge` 接口，可通过 `@Autowired` 注入使用。
- 所有 SQL 操作都已通过第一阶段的 ORM 拦截器自动追加租户过滤条件，本阶段无需手动拼接。

请开始生成代码。



# 第四阶段：核心积分账务与瀑布流冲抵引擎 — 标准提示词

## 系统提示（角色设定）
你是一位金融级账务系统的资深后端专家，精通准金融交易系统的账务分离、先进先出（FIFO）成本核销和负债冲抵逻辑。你编写的每一行代码都像处理资金清算一样严谨，绝不容忍并发不一致或余额计算错误。当前项目使用 **Spring Boot 3.x**、**Java 17**、**PostgreSQL 15** 和 **Spring Data JPA**。

## 任务背景
我们正在构建一个下一代全渠道忠诚度管理 SaaS 平台，已完成的前三阶段为：
- **第一阶段**：`TenantContext` 多租户隔离、ORM 自动租户过滤、`ApiResponse<T>`、`EventBridge` 事件总线、幂等性拦截器。
- **第二阶段**：`Program`、`Member`、`MemberUniqueKey`、`TransactionEvent` 等 JPA 实体，`ext_attributes` JSONB 映射，Schema 版本下沉服务。
- **第三阶段**：`EnrollmentService`（全渠道 One-ID 并发入会，含分布式锁与唯一键竞态兜底），`MergeService` 骨架。
设计文档已将授信额度作为独立积分类型 "CREDIT" 实现，该类型具有自己的 `member_account` 记录（含 `credit_limit` 和 `credit_used`），并参与跨账户冲抵。
本阶段是第四阶段，目标是实现平台最核心的积分资产账务系统，包括积分发放（含被动透支与主动信用冲抵）、积分核销（FIFO + 分摊明细溯源）和余额查询。这是整个系统资金安全的核心，代码必须严格遵循文档中约定的“瀑布流冲抵”和“禁止余额字段更新”等铁律。

你将收到的文档章节如下（请先完整阅读再生成代码）：
- **第四章：积分、等级与资产隔离域（全章精读）**，重点包括：
  - 4.1 积分类型字典（核心风控开关：`is_redeemable`, `is_tier_calc`, `is_transferable`, `allow_negative`）
  - 4.2 被动透支与主动信用冲抵引擎（4.2.1 账户数据结构扩充，4.2.2 瀑布流冲抵伪代码）
  - 4.3 FIFO 兑换引擎与精准溯源（4.3.1 先进先出核销伪代码，4.3.2 过期处理，4.3 核销分摊明细表）
  ## 第四章：积分、等级与资产隔离域 (核心账务与冲抵引擎)
本章定义了平台虚拟资产（积分、成长值、信用额度）的生命周期。为了支撑复杂的营销场景并防止“兑换礼品导致掉级”或“恶意退款套利”，系统在底层实现了严格的资产隔离与准金融级的对账核销机制。
### 4.1 积分类型字典 (Point Type Taxonomy)
系统中严禁在代码里硬编码具体的积分名称。所有的资产必须通过 `point_type_definition`（积分类型字典）进行定义和控制。每种积分类型拥有四大核心风控开关。
#### 4.1.1 核心风控开关 (Core Attributes)
* `is_redeemable` (可否兑换)：决定余额是否能用于兑换中心抵扣。
* `is_tier_calc` (可否算等级)：决定其累计入账量是否交由 `TierEvaluationService` 计算升降级。
* `is_transferable` (可否转赠)：决定是否能在会员间进行 C2C 划转。
* `allow_negative` (允许负数)：决定因历史订单退款导致扣分时，是否允许将账户余额扣至零以下（被动透支）。
#### 4.1.2 预设最佳实践 (Best Practice Templates)
平台初始化时，默认向 Program 注入以下两种隔离的资产类型：
* **消费积分 (Reward Points)**：`is_redeemable=true`, `is_tier_calc=false`。用户花钱赚取，兑换礼品会扣减余额，绝对不影响等级。
* **等级成长值 (Tier Points)**：`is_redeemable=false`, `is_tier_calc=true`。通常与消费金额 1:1 挂钩，只增不减（或按年清零），保障用户等级不因兑换而掉级。
#### 4.1.3 积分流水表核心字段与查询模型
```sql
CREATE TABLE account_transaction (
    id BIGSERIAL,
    program_code VARCHAR(32) NOT NULL,
    member_id VARCHAR(64) NOT NULL,
    operation_key VARCHAR(100) NOT NULL,      -- 幂等键
    transaction_type VARCHAR(32) NOT NULL,    -- ACCRUAL / REDEMPTION / EXPIRATION / REPAYMENT / CREDIT_REPAY / CREDIT_DRAWDOWN
    amount NUMERIC(18, 4) NOT NULL,           -- 变动金额（正为入账，负为出账）
    remaining_amount NUMERIC(18, 4),          -- 该批次剩余可用额度
    expires_at TIMESTAMPTZ,                   -- 过期时间
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE / EXHAUSTED / EXPIRED
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);
```
* **关键约束**： 
* `remaining_amount` 是这一笔流水中还未被消耗或过期的额度。
* `status = 'ACTIVE'` 且 `remaining_amount` > 0 且 `expires_at` > NOW() 三条件同时满足，才属于“可用积分”。
* 过期后，status 变为 EXPIRED，remaining_amount 清零，但不删除行。
* **积分查询与汇总（实时计算）**
* 查询会员可用余额：
```sql
SELECT COALESCE(SUM(remaining_amount), 0)
FROM account_transaction
WHERE member_id = ?
  AND program_code = ?
  AND account_type = ?
  AND status = 'ACTIVE'
  AND remaining_amount > 0
  AND expires_at > NOW();
```
* 不维护 member_account.balance 预聚合字段。member_account 表只存储：
* 账户级风控参数：overdraft_limit、credit_limit、credit_used
* 累计统计（只增不减，用于报表）：total_accrued、total_redeemed、total_expired


### 4.2 被动透支与主动信用冲抵引擎 (Offset Engine)
积分系统的入账绝不是简单的 `UPDATE balance = balance + X`。由于逆向退款可能导致账户余额为负（被动透支），且高价值用户可能拥有预授信的信用积分（主动信用），系统在处理正向入账时，必须经过“**瀑布流冲抵机制**”。
#### 4.2.1 账户数据结构扩充
`member_account` 表不再维护实时余额（balance），余额改为查询时通过 SUM(remaining_amount) 实时汇总。member_account 表仅存储账户级风控参数和累计统计。
* `overdraft_limit`：被动透支底线。如设为 1000，则退单时最多允许账户扣到 -1000 分，超过则生成人工追偿工单。
* `credit_limit`：主动授信总额。
* `credit_used`：已使用的信用额度。
#### 4.2.2 瀑布流冲抵伪代码 (Waterfall Offset Logic)
要求 AI 或研发在编写 `PointGrantService`（积分发放服务）时，严格遵循以下处理流转：
```java
@Transactional
public void grantPoints(String programCode, String memberId, String accountType, BigDecimal pointsToGrant, String ruleId , String ruleSnapshotId) {
    // 1. 悲观锁获取账户（仅用于获取信用额度等风控参数，不操作余额）
    MemberAccount account = accountRepo.findByMemberIdAndTypeForUpdate(memberId, accountType);
    BigDecimal remainingToGrant = pointsToGrant;

    // 2. 补天窗：检查是否存在被动透支
    //    透支体现为 account_transaction 中存在 remaining_amount < 0 的 OVERDRAFT 记录
    List<AccountTransaction> overdraftBatches = transactionRepo.findOverdraftBatchesForUpdate(
        memberId, accountType);  // WHERE remaining_amount < 0 AND status = 'OVERDRAFT'

    for (AccountTransaction overdraft : overdraftBatches) {
        if (remainingToGrant.compareTo(BigDecimal.ZERO) <= 0) break;
        
        BigDecimal debt = overdraft.getRemainingAmount().abs(); // 透支金额（绝对值）
        BigDecimal offsetAmount = remainingToGrant.min(debt);
        
        // 生成 REPAYMENT(还款) 流水，冲抵透支
        insertTransaction(memberId, accountType, "REPAYMENT", offsetAmount, null);
        
        // 减少透支额度
        overdraft.setRemainingAmount(overdraft.getRemainingAmount().add(offsetAmount));
        if (overdraft.getRemainingAmount().compareTo(BigDecimal.ZERO) == 0) {
            overdraft.setStatus("SETTLED"); // 透支已还清
        }
        transactionRepo.save(overdraft);
        
        remainingToGrant = remainingToGrant.subtract(offsetAmount);
    }

    // 3. 还信用：检查主动信用欠款 (credit_used > 0)
    if (remainingToGrant.compareTo(BigDecimal.ZERO) > 0 && account.getCreditUsed().compareTo(BigDecimal.ZERO) > 0) {
        BigDecimal creditDebt = account.getCreditUsed();
        BigDecimal offsetAmount = remainingToGrant.min(creditDebt);
        
        // 生成 CREDIT_REPAY(信用还款) 流水
        insertTransaction(memberId, accountType, "CREDIT_REPAY", offsetAmount, null);
        
        // 扣减信用已用额度（这是 member_account 上唯一需要更新的字段）
        account.setCreditUsed(account.getCreditUsed().subtract(offsetAmount));
        remainingToGrant = remainingToGrant.subtract(offsetAmount);
    }

    // 4. 真实入账：最终剩余的积分，才变成可用的正向批次
    if (remainingToGrant.compareTo(BigDecimal.ZERO) > 0) {
        // 生成 ACCRUAL(发放) 流水，带有过期时间
        // remaining_amount = 发放金额，后续消耗和过期时从这里扣减
        insertTransaction(memberId, accountType, "ACCRUAL", remainingToGrant, 
                  calculateExpiryDate(), ruleId, ruleSnapshotId);
    }
    
    // 5. 更新累计统计（只增不减，用于报表，不影响实时余额计算）
    account.setTotalAccrued(account.getTotalAccrued().add(pointsToGrant));
    accountRepo.save(account);
}

/**
 * 插入积分流水
 * @param amount 变动金额（正数为入账，负数为出账）
 * @param expiresAt 过期时间（ACCRUAL 类型必填，其他类型为 null）
 */
private void insertTransaction(String memberId, String accountType, 
                               String transactionType, BigDecimal amount, LocalDateTime expiresAt) {
    AccountTransaction tx = new AccountTransaction();
    tx.setMemberId(memberId);
    tx.setAccountType(accountType);
    tx.setTransactionType(transactionType);
    tx.setAmount(amount);
    tx.setRemainingAmount(amount);  // 初始剩余额度 = 变动金额
    tx.setExpiresAt(expiresAt);
    tx.setRuleId(ruleId);
    tx.setRuleSnapshotId(ruleSnapshotId);
    tx.setStatus("ACTIVE");
    transactionRepo.save(tx);
}
```
#### 4.2.3 冲抵引擎与流水表操作约束
冲抵引擎的所有操作仅修改 account_transaction 的 remaining_amount 和 status，不更新 member_account 的任何余额字段。余额始终通过 SUM(remaining_amount) 实时计算。
### 4.3 FIFO 兑换引擎与精准溯源 (Redemption & Traceability)
为了实现准金融级的对账，积分核销必须支持批次级别的溯源。单纯扣减 `member_account.balance` 是不够的，必须通过 `account_transaction` 的剩余可用量 (`remaining_amount`) 和 `redemption_allocation` (核销分摊表) 实现精准对应。
#### 4.3.1 先进先出核销伪代码 (FIFO Redemption Logic)
```java
@Transactional
public void redeemPoints(String programCode, String memberId, String accountType, BigDecimal pointsToRedeem) {
    // 1. 获取账户（仅用于信用额度等风控参数）
    MemberAccount account = accountRepo.findByMemberIdAndTypeForUpdate(memberId, accountType);
    
    // 2. 实时汇总可用余额，校验是否足够（含信用额度）
    BigDecimal availableBalance = transactionRepo.sumAvailableBalance(memberId, accountType);
    // SQL: SELECT COALESCE(SUM(remaining_amount), 0) 
    //      FROM account_transaction 
    //      WHERE member_id = ? AND account_type = ? 
    //        AND status = 'ACTIVE' AND remaining_amount > 0 AND expires_at > NOW()
    
    BigDecimal totalAvailable = availableBalance.add(
        account.getCreditLimit().subtract(account.getCreditUsed())
    );
    if (totalAvailable.compareTo(pointsToRedeem) < 0) {
        throw new BusinessException("ERR_INSUFFICIENT_POINTS", "积分不足（含信用额度）");
    }
    
    // 3. 查询所有有效且有余额的积分批次，严格按过期时间和创建时间升序排列
    //    【关键】必须加 expires_at > NOW() 过滤已过期批次
    List<AccountTransaction> validBatches = transactionRepo.findActiveBatchesForUpdate(
        memberId, accountType,
        Sort.by("expiresAt").ascending().and(Sort.by("createdAt").ascending())
    );
    // SQL: SELECT * FROM account_transaction 
    //      WHERE member_id = ? AND account_type = ?
    //        AND status = 'ACTIVE' AND remaining_amount > 0 AND expires_at > NOW()
    //      ORDER BY expires_at ASC, created_at ASC 
    //      FOR UPDATE
    
    BigDecimal remainingToRedeem = pointsToRedeem;
    
    // 4. 生成一笔总的负向 REDEMPTION 流水
    AccountTransaction redemptionTx = createRedemptionTransaction(memberId, accountType, pointsToRedeem.negate());
    
    // 5. 遍历可用批次，逐笔扣减 (Allocation)
    for (AccountTransaction batch : validBatches) {
        if (remainingToRedeem.compareTo(BigDecimal.ZERO) <= 0) break;
        
        // 【惰性过期检查】遍历时如果发现已过期，标记并跳过
        if (batch.getExpiresAt() != null && batch.getExpiresAt().isBefore(LocalDateTime.now())) {
            markAsExpired(batch);
            continue;
        }
        
        BigDecimal batchAvailable = batch.getRemainingAmount();
        BigDecimal allocateAmount = remainingToRedeem.min(batchAvailable);
        
        // 扣减批次剩余额度
        batch.setRemainingAmount(batchAvailable.subtract(allocateAmount));
        if (batch.getRemainingAmount().compareTo(BigDecimal.ZERO) == 0) {
            batch.setStatus("EXHAUSTED"); // 批次耗尽
        }
        transactionRepo.save(batch);
        
        // 【核心记录】写入核销分摊明细，关联扣款流水与原始发分流水
        Allocation allocation = new Allocation(redemptionTx.getId(), batch.getId(), allocateAmount);
        allocationRepo.save(allocation);
        
        remainingToRedeem = remainingToRedeem.subtract(allocateAmount);
    }
    
    // 6. 处理自有余额不足，扣除信用额度的场景
    if (remainingToRedeem.compareTo(BigDecimal.ZERO) > 0) {
        processCreditDrawdown(account, redemptionTx, remainingToRedeem);
    }
    
    // 【注意】不再需要 account.setBalance(...)，余额由 SUM(remaining_amount) 实时计算
}

/**
 * 惰性标记过期批次
 */
private void markAsExpired(AccountTransaction batch) {
    BigDecimal expiredAmount = batch.getRemainingAmount();
    batch.setStatus("EXPIRED");
    batch.setRemainingAmount(BigDecimal.ZERO);
    transactionRepo.save(batch);
    
    // 发布审计事件（异步处理审计流水和用户通知）
    eventBridge.publish("loyalty-point-events", batch.getMemberId(),
        new PointsExpiredEvent(batch.getMemberId(), expiredAmount, batch.getId()));
}
```
**设计意义**：当发生退款或退换货导致系统需要退还积分给用户时，系统可以直接通过 `redemption_allocation` 表找到原始的 `ACCRUAL` 批次，精准恢复该批次原有的 `expires_at` (过期时间)，而非简单粗暴地发放一笔全新的积分。
#### 4.3.2 积分过期处理（惰性标记 + 事件驱动）
* 严禁使用定时任务执行以下语句：
```sql
-- ❌ 禁止！Citus 下会导致表级锁，阻塞所有写入
UPDATE account_transaction 
SET status = 'EXPIRED', remaining_amount = 0 
WHERE status = 'ACTIVE' AND expires_at <= NOW();
```
正确做法：过期判定在查询时实时完成，过期状态在访问时惰性更新。

* FIFO 兑换遍历时惰性标记
```java
for (AccountTransaction batch : validBatches) {
    // 双重保险：即使 SQL 已过滤，遍历时再校验一次
    if (batch.getExpiresAt() != null && batch.getExpiresAt().isBefore(LocalDateTime.now())) {
        markAsExpired(batch);  // 单行更新，锁极小
        continue;
    }
    // ... 正常核销逻辑
}

private void markAsExpired(AccountTransaction batch) {
    batch.setStatus("EXPIRED");
    batch.setRemainingAmount(BigDecimal.ZERO);
    transactionRepo.save(batch);
    
    // 发布审计事件
    eventBridge.publish("loyalty-point-events", batch.getMemberId(),
        new PointsExpiredEvent(batch.getMemberId(), batch.getRemainingAmount(), batch.getId()));
}
```
* 用户查询时惰性标记（可选）
```java
List<AccountTransaction> txs = transactionRepo.findByMemberId(memberId);
for (AccountTransaction tx : txs) {
    if ("ACTIVE".equals(tx.getStatus()) 
        && tx.getRemainingAmount().compareTo(BigDecimal.ZERO) > 0
        && tx.getExpiresAt() != null 
        && tx.getExpiresAt().isBefore(LocalDateTime.now())) {
        markAsExpired(tx);
    }
}
```
* 审计事件消费
PointsExpiredEvent 被 EventBridge 消费后：
生成一条 EXPIRATION 类型的审计流水（amount 为负数）
触发用户通知（微信模板消息等）
更新 member_account.total_expired（异步累加）

- **第十一章：数据库物理模型设计**，重点表：
  - `account_transaction`（分区表，字段 `remaining_amount`, `expires_at`, `status` 等）
  - `redemption_allocation`（关联核销流水与原始发分批次的溯源表）
  - `member_account`（仅存储 `overdraft_limit`, `credit_limit`, `credit_used`, 累计统计，**不存储余额**）
-- 4. 积分流水表 (分区表，必须注意 PG 唯一索引限制)
CREATE TABLE account_transaction (
    id BIGSERIAL,
    program_code VARCHAR(32) NOT NULL,
    member_id VARCHAR(64) NOT NULL,
    operation_key VARCHAR(100) NOT NULL,
    transaction_type VARCHAR(32) NOT NULL,
    amount NUMERIC(18, 4) NOT NULL,
    remaining_amount NUMERIC(18, 4),
    expires_at TIMESTAMPTZ,
    rule_id VARCHAR(64),              -- ← 新增：产生该积分的规则编号
    rule_snapshot_id VARCHAR(64),     -- ← 新增：规则当时的版本快照 ID
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);
-- 注意：唯一索引必须包含分区键 created_atcreated_at 字段语义约束：
-- created_at 必须使用业务事件时间（即交易实际发生的时刻，由外部系统传入或 SPI 回调时间），而非数据库写入时的 NOW()。
-- 这保证了同一条业务事件在重试时，其 created_at 完全一致，使得分区键和唯一索引能真正发挥防重作用。
-- 若业务事件时间不可得，则退而求其次使用 idempotency_key 中的时间戳部分作为 created_at。

CREATE UNIQUE INDEX uk_at_idempotent_operation ON account_transaction(program_code, operation_key, created_at);
-- 5. 核销分摊明细 (对账核心)
CREATE TABLE redemption_allocation (
    id BIGSERIAL PRIMARY KEY,
    program_code VARCHAR(32) NOT NULL,
    redemption_tx_id BIGINT NOT NULL, -- 扣除流水
    accrual_tx_id BIGINT NOT NULL,    -- 原始发分流水
    allocated_amount NUMERIC(15, 2) NOT NULL
);
-- 6. 会员账户表 (含透支与信用额度)
CREATE TABLE member_account (
    account_id BIGSERIAL PRIMARY KEY,
    program_code VARCHAR(100) NOT NULL,
    member_id BIGINT NOT NULL,
    account_type VARCHAR(50) NOT NULL,
    overdraft_limit DECIMAL(20,4) DEFAULT 0,
    credit_limit DECIMAL(20,4) DEFAULT 0,
    credit_used DECIMAL(20,4) DEFAULT 0,
    total_accrued DECIMAL(20,4) DEFAULT 0,    -- 累计获得（只增不减，报表用）
    total_redeemed DECIMAL(20,4) DEFAULT 0,   -- 累计消耗
    total_expired DECIMAL(20,4) DEFAULT 0,    -- 累计过期
    version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (program_code, member_id, account_type)
);
## 任务要求
请基于上述章节，生成以下四大类产出。每一项都必须严格对照文档中的伪代码和约束，不得自行简化。

### 1. 基础实体与 Repository 定义
#### 1.1 `AccountTransaction` 实体
- 映射到 `account_transaction` 表，字段包括：
  - `id`, `program_code`, `member_id`, `operation_key`（幂等键）, `transaction_type`（`ACCRUAL`, `REDEMPTION`, `EXPIRATION`, `REPAYMENT`, `CREDIT_REPAY`, `CREDIT_DRAWDOWN`, `OVERDRAFT`）, `amount`, `remaining_amount`, `expires_at`, `rule_id`, `rule_snapshot_id`, `status`（`ACTIVE`, `EXHAUSTED`, `EXPIRED`, `SETTLED`, `OVERDRAFT`）, `created_at`
- 标记 `@MultiTenant`，配合第一阶段拦截器。
- 提供索引对应的 JPA 注解或说明（如需要唯一约束 `uk_at_idempotent_operation`，需注意分区键 `created_at` 必须被包含在唯一索引中，实体上可用 `@Table(uniqueConstraints = ...)` 标注）。

#### 1.2 `RedemptionAllocation` 实体
- 映射到 `redemption_allocation` 表，字段：`id`, `program_code`, `redemption_tx_id` (关联核销流水), `accrual_tx_id` (关联原始发分流水), `allocated_amount`。
- 标记 `@MultiTenant`。

#### 1.3 `MemberAccount` 实体
- 映射到 `member_account` 表，字段包括：
  - `account_id`, `program_code`, `member_id`, `account_type`, `overdraft_limit`, `credit_limit`, `credit_used`, `total_accrued`, `total_redeemed`, `total_expired`, `version`（乐观锁）
- **严禁包含 `balance` 字段**。
- 标记 `@MultiTenant`。

#### 1.4 对应的 Spring Data JPA Repository 接口
- 为每个实体提供 Repository 接口，继承 `JpaRepository`。
- 在 `AccountTransactionRepository` 中定义关键查询方法：
  - `findActiveBatchesForUpdate(memberId, accountType, Sort sort)`：查询 `status='ACTIVE' AND remaining_amount > 0 AND expires_at > NOW()` 并按过期时间和创建时间排序，同时加 `@Lock(PESSIMISTIC_WRITE)` 或使用 `@Query` 加 `FOR UPDATE`（推荐用 `@Query` 手写 SQL）。
  - `sumAvailableBalance(memberId, accountType)`：返回 `SUM(remaining_amount)`，过滤条件同上。
  - `findOverdraftBatchesForUpdate(memberId, accountType)`：查询 `remaining_amount < 0 AND status = 'OVERDRAFT'` 的记录，加悲观锁。
  - `findExpiredActiveBatches(memberId, accountType)`：查询 `status='ACTIVE' AND remaining_amount > 0 AND expires_at <= NOW()`。

### 2.  发放积分时的跨账户信用还款 `PointGrantService`
在 `PointGrantService` 和 `PointRedeemService` 中，必须能够跨账户查询和更新 `CREDIT` 账户的 `MemberAccount`。
在 `grantPoints` 方法的“还信用”步骤，**不再检查当前账户的 credit_used**，而是：
- 根据 memberId 和 account_type="CREDIT" 加载信用账户（如不存在则跳过）。
- 若信用账户存在且 `credit_used > 0`，使用剩余待发放积分冲抵信用欠款：
  - 生成 `CREDIT_REPAY` 交易（在 CREDIT 账户下，金额为正，`remaining_amount` 为正）。
  - 减少信用账户的 `credit_used`。
  - 扣减 `remainingToGrant`。
- 注意：信用还款使用的积分来自当前发放的资产类型（如 REWARD），但冲抵流水记录在 CREDIT 账户。
#### 2.1 核心方法 `grantPoints`
- 方法签名：`public void grantPoints(String programCode, String memberId, String accountType, BigDecimal pointsToGrant, String ruleId, String ruleSnapshotId)`
- **事务**：使用 `@Transactional`，确保整个冲抵和发放过程原子化。
- **逻辑步骤**（严格遵循文档 4.2.2 伪代码）：
  1. **悲观锁获取账户**（`MemberAccount`），用于获取透支限额和信用额度参数，不更新余额。
  2. **阶段一：补天窗（被动透支冲抵）**：查询所有 `OVERDRAFT` 批次（`remaining_amount < 0`），按欠款绝对值顺序冲抵。生成 `REPAYMENT` 流水，扣减透支批次的剩余欠款，若还清则将批次状态改为 `SETTLED`。
  3. **阶段二：还信用（主动信用还款）**：若还有剩余积分待发，检查 `credit_used > 0`，则冲抵信用已用额度，生成 `CREDIT_REPAY` 流水，扣减 `account.credit_used`。
  4. **阶段三：真实入账（正向批次）**：剩余的积分生成 `ACCRUAL` 类型的 `AccountTransaction`，设置 `expires_at`（调用 `calculateExpiryDate()`），`remaining_amount` 初始等于发放金额。
  5. **更新累计统计**：`total_accrued` 累加原始发放总额（不是冲抵后的剩余），只增不减。
- 插入流水的方法 `insertTransaction` 应封装交易类型、金额、过期时间等，`remaining_amount` 初始值与 `amount` 相同。
- `calculateExpiryDate()` 逻辑可暂时基于配置或固定规则（如 12 个月后），后续阶段可抽取。

#### 2.2 注意点
- 严禁直接 `UPDATE member_account` 上的任何“余额”字段。
- 所有积分实际分布仅体现在 `account_transaction.remaining_amount` 上。

### 3. 积分核销服务 `PointRedeemService`
在 `redeemPoints` 中，资产可用性需合并计算：自有积分余额（当前账户） + 信用可用额度（CREDIT 账户的 `credit_limit - credit_used`）。
- **优先消耗自有积分**：先按 FIFO 规则锁定并扣减当前账户的有效批次（如 REWARD），生成 `RedemptionAllocation`。
- **不足部分从信用扣除**：调用 `processCreditDrawdown(creditAccount, redemptionTx, remaining)`，在其中：
  - 增加 `credit_used`。
  - 生成 `CREDIT_DRAWDOWN` 流水（金额为负，`remaining_amount` 为负）。
  - 创建对应的 `RedemptionAllocation`，关联核销流水与本次信用提取流水。
#### 3.1 核心方法 `redeemPoints`
- 方法签名：`public void redeemPoints(String programCode, String memberId, String accountType, BigDecimal pointsToRedeem)`
- **事务**：`@Transactional`
- **逻辑步骤**（严格遵循文档 4.3.1 伪代码）：
  1. 获取账户（用于信用额度参数）。
  2. 调用 `sumAvailableBalance` 计算可用余额，加上信用可用额度（`credit_limit - credit_used`），校验总可用积分是否足够。
  3. 查询所有有效批次 `findActiveBatchesForUpdate`，按 `expires_at ASC, created_at ASC` 排序并加行锁。
  4. 创建一条总的 `REDEMPTION` 流水，`amount` 为负值，`remaining_amount` 初始也为负值。
  5. 遍历每个批次：
     - **惰性过期检查**：如果批次已过期，调用 `markAsExpired(batch)` 将状态置为 `EXPIRED` 并清零 `remaining_amount`，发布 `PointsExpiredEvent`。
     - 否则，计算本次从该批次扣减的额度 `allocatedAmount = min(remainingToRedeem, batch.remaining_amount)`。
     - 扣减批次的 `remaining_amount`，若耗尽则设为 `EXHAUSTED`。
     - 创建 `RedemptionAllocation` 记录，关联核销流水 ID 和批次 ID。
  6. 处理信用额度扣减：如果自有批次耗尽后仍有待核销额度，调用 `processCreditDrawdown` 从信用额度中扣除，生成 `CREDIT_DRAWDOWN` 流水。
  7. **严禁更新任何 `balance` 字段**。余额由实时查询保证。

#### 3.2 辅助方法
- `markAsExpired(AccountTransaction batch)`：将批次状态置为 `EXPIRED`，`remaining_amount` 置零，发布 `PointsExpiredEvent` 事件。
- `processCreditDrawdown(...)`：扣减 `member_account.credit_used`（注意使用乐观锁 `version` 控制并发），生成 `CREDIT_DRAWDOWN` 流水。

### 4. 积分余额查询与过期的辅助处理
#### 4.1 查询可用余额
- 提供 `BigDecimal getAvailableBalance(String memberId, String accountType)` 方法，直接调用 Repository 的 `sumAvailableBalance` 查询即可，**禁止加缓存预聚合**。

#### 4.2 过期批次清理建议
- 在 `redeemPoints` 中已实现惰性标记，无需额外的定时任务。
- 也可以提供一个 `scheduledExpireCheck` 方法（可选），扫描过期批次并标记，但不得做全表更新，只能逐行标记。

#### 4.3 积分类型字典服务 `PointTypeService`（骨架）
- 定义一个 `PointTypeDefinition` 实体或 DTO，对应 `point_type_definition` 表（若尚未建表，可先做接口），包含四个风控开关属性。
- 在发放和核销前，应校验资产类型的开关（如 `is_redeemable` 是否为 true 才允许调用 `redeemPoints` 等）。本阶段可先实现简单的验证逻辑，从 `Program` 配置中读取。

### 5. 信用额度管理
提供 `setCreditLimit` 方法直接修改 `credit_limit`，不产生积分流水，但需记录审计日志。
请确保生成的代码符合以上跨账户逻辑，并保持事务一致性。
## 输出要求
- 生成完整的 Java 代码文件，包结构建议：
  - `com.example.loyalty.domain`：`AccountTransaction`, `RedemptionAllocation`, `MemberAccount`, `PointTypeDefinition` 实体
  - `com.example.loyalty.repository`：上述实体的 Repository 接口
  - `com.example.loyalty.service`：`PointGrantService`, `PointRedeemService`, `PointTypeService`（骨架）
  - `com.example.loyalty.event`：`PointsExpiredEvent` 定义
- 所有涉及资金的操作必须添加详细注释，说明业务含义、所遵循的文档伪代码步骤编号。
- 如果使用自定义异常（如 `InsufficientPointsException`），请继承 `BusinessException` 以适配全局异常处理器。
- 输出 Maven 依赖补充（如 `hibernate-jpamodelgen`、`jackson-databind` 等已存在则无需重复），确保代码可编译。

## 上下文衔接约定
- 第二阶段已定义 `Member` 实体和 `MemberRepository`，本阶段需要 `MemberAccount` 与其关联，但 `member_id` 是业务主键而非自增 ID，请注意关联字段使用 `member_id`。
- 第一阶段已实现 `EventBridge`，可直接注入使用（如 `eventBridge.publish("loyalty-point-events", batch.getMemberId(), new PointsExpiredEvent(...))`）。
- 第一阶段已实现多租户拦截器，所有 Repository 查询会自动拼接 `program_code`，但手写 SQL 的 `@Query` 需要显式添加 `AND program_code = :programCode` 参数（因为拦截器可能无法处理自定义 JPQL/SQL 中的子查询，为保证安全，建议在 `@Query` 中显式添加租户条件并传入 `programCode` 参数）。

请开始生成代码。


# 第五阶段：无锁化级联重算与逆向交易 — 标准提示词

## 系统提示（角色设定）
你是一位高可用系统架构师，精通分布式系统下的最终一致性、事件溯源和补偿事务模式。你对金融交易中的“级联回滚效应”有深刻认知，并擅长设计非阻塞、无锁化的异步补偿方案。当前项目使用 **Spring Boot 3.x**、**Java 17**、**PostgreSQL 15** 和 **Spring Data JPA**。

## 任务背景
我们正在构建一个下一代全渠道忠诚度管理 SaaS 平台。前四个阶段已完成：
- **第一阶段**：多租户隔离、`EventBridge`、统一响应体、幂等性拦截器。
- **第二阶段**：核心实体（`Member`、`MemberUniqueKey`、`Program` 等）、JSONB 映射、Schema 版本下沉。
- **第三阶段**：全渠道入会引擎（`EnrollmentService`），含分布式锁与唯一键竞态兜底。
- **第四阶段**：核心积分账务，包括 `PointGrantService`（瀑布流冲抵）、`PointRedeemService`（FIFO 核销 + 分摊溯源）、`MemberAccount` 及 `AccountTransaction` 流水表。

本阶段是第五阶段，目标是解决退款场景下的**级联重算效应（Cascade Recalculation）**。当一笔历史订单退款时，不仅要扣回其产生的积分，还可能引发等级降级，从而导致之后所有交易发放的积分比例失真。系统必须通过异步影子回放和短事务差额补偿，在不阻塞用户后续交易的前提下，优雅地完成历史重算。

你将收到的文档章节如下（请先完整阅读再生成代码）：
- **第五章：逆向交易与级联重算域（全章精读）**，重点包括：
  - 5.1 场景挑战与传统架构痛点
  - 5.2 异步差额补偿机制（架构流转步骤）
  - 5.3 级联重算引擎伪代码（`processCascadeRecalculation` 和 `applyCompensationWithShortTransaction`）
  - 5.3.1 `ShadowContext` 设计规范
  - 5.4 积分退回与生命周期还原（可选：本阶段可先关注核心重算，退还部分可延后）
  - 5.5 负资产与系统级死信处理（了解即可，不要求全部实现）
## 第五章：逆向交易与级联重算域 (无锁化补偿)
忠诚度系统最复杂的业务场景在于逆向交易（如历史订单退款）。退款不仅需要扣回原订单产生的积分，如果原订单曾触发过“会员升级”，退款还将导致后续所有订单的积分获取比例失效，这就是著名的“**级联重算效应（Cascade Recalculation Effect）**”。
本章详细规范了如何通过“异步无锁化快照”与“短事务差额补偿”机制，优雅、高性能地解决这一难题。
### 5.1 场景挑战与传统架构痛点
**场景重现**：
* 1月1日：用户购买 1000 元，获取 1000 积分，触发升级为【金卡】（享双倍积分特权）。
* 2月1日：用户购买 2000 元，按金卡双倍特权，获取 4000 积分。
* 3月1日：用户将 1月1日 的订单全额退款。
**传统架构痛点**：为了保证数据绝对正确，传统架构会在退款发生时，对该用户的积分账户加上排他锁（分布式锁或 DB 锁），然后按时间轴重新回放 1月1日 至今的所有交易。如果用户交易极为频繁，回放可能耗时数秒到数分钟。在此期间，用户如果在前端商城发生任何新交易，都会被系统直接阻断（账户假死），引发严重的客诉。
### 5.2 异步差额补偿机制 (Asynchronous Delta Compensation)
为了解决“假死”问题，系统摒弃强锁阻塞模式，采用后台影子回放与差额补偿（Delta）架构。退款重算期间，用户完全可以继续进行新的正向交易。
#### 5.2.1 架构流转步骤
1. **触发退款 (Trigger)**：系统接收到退单事件，首先全额回滚（或按比例扣减）原订单产生的积分。
2. **判断级联 (Check Cascade)**：如果原订单退单后，用户的“历史成长值”跌破了曾经的升级门槛，则判定需要触发级联重算，生成一条 `cascade_recalc_job` 异步任务。
3. **影子回放 (Shadow Replay)**：
   * 异步引擎拉取退款时间点（1月1日）至重算触发点（3月1日）期间的所有正向事件流水。
   * 在内存中构建一个虚拟影子账户 (Shadow Account)，剥离掉 1月1日 的退单金额，按当时的历史规则快照（`rule_snapshot`），将后续事件重新输入规则引擎进行推理。
4. **计算差额 (Calculate Delta)**：
   * 对比“影子账户的最终推演结果”与“当前真实账户的状态快照”。
   * 例如得出结论：因等级误判，多发了 2000 消费积分；当前应该降级为【银卡】。
5. **短事务补偿 (Atomic Commit)**：只在最后一步开启数据库强事务，用一条 UPDATE 语句扣减差额，耗时在几毫秒以内。
### 5.3 级联重算引擎伪代码 (Cascade Recalculation Logic)
以下伪代码规范了开发人员（或 AI 编码时）必须遵循的无锁化补偿范式：
级联重算任务的状态管理与重试机制
* cascade_recalc_job 表需维护任务状态：

* PENDING：待处理

* PROCESSING：正在执行

* COMPLETED：已完成

* FAILED：失败（超过最大重试次数）

故障恢复机制：
```java
/**
 * 定时扫描卡在 PROCESSING 状态超过阈值的任务，重置为 PENDING
 * 防止应用重启后任务永远卡死
 */
@Scheduled(fixedDelay = 60000) // 每分钟扫描一次
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
```
补偿事务幂等性约束：
```java
@Transactional
protected void applyCompensationWithShortTransaction(String memberId, AccountDelta delta, String jobId) {
    // 幂等校验：同一 jobId 的补偿只能执行一次
    if (compensationLogRepo.existsByJobId(jobId)) {
        return; // 已执行过，跳过
    }
    
    // ... 执行补偿逻辑 ...
    
    // 记录补偿完成（防重）
    compensationLogRepo.save(new CompensationLog(jobId, memberId, delta));
    jobRepo.markCompleted(jobId);
}
```
```java
/**
 * 级联重算由 CascadeRecalcJobProcessor 异步消费 cascade_recalc_job 表中的 PENDING 任务执行，而非简单的 @Async 注解。
 */ 
@Async
public void processCascadeRecalculation(String recalcJobId, String memberId, LocalDateTime reverseTime) {
    // 1. 加载回放时间轴上的所有正向事件 (不加锁)
    List<TransactionEvent> timelineEvents = eventRepo.findEventsAfter(memberId, reverseTime);

    // 2. 构建影子上下文，同时加载等级变更时间线
    ShadowContext shadowContext = buildShadowContext(memberId, reverseTime);

    // 3. 按时间轴逐事件回放
    for (TransactionEvent event : timelineEvents) {
        // 3a. 推进等级时间线：获取事件发生时刻的会员等级
        shadowContext.advanceToTime(event.getEventTime());

        // 3b. 获取产生该积分的规则历史版本
        //     退款前已产生的积分流水，已记录了 rule_snapshot_id
        String ruleSnapshotId = event.getRuleSnapshotId(); // 从历史流水中取
        RuleSnapshot snapshot = snapshotRepo.get(ruleSnapshotId);

        // 3c. 用当时的等级 + 当时的规则，重新评估该事件
        shadowContext.apply(ruleEngine.evaluate(shadowContext, event, snapshot));
    }

    // 4. 计算差额 Delta
    AccountDelta delta = calculateDelta(memberId, shadowContext);

    // 5. 【核心】极短事务提交补偿，不阻塞用户的新交易
    applyCompensationWithShortTransaction(memberId, delta, recalcJobId);
}

@Transactional
protected void applyCompensationWithShortTransaction(String memberId, AccountDelta delta, String jobId) {
    // 差额扣减：直接操作积分流水，不依赖 member_account 的余额字段
    if (delta.getPointsToDeduct().compareTo(BigDecimal.ZERO) > 0) {
        pointRedeemService.forceDeductWithOverdraft(
            memberId, 
            delta.getPointsToDeduct(), 
            "CASCADE_RECALC_DEDUCT"
        );
    }
    
    // 修正等级
    if (delta.getNewTier() != null) {
        Member member = memberRepo.findByMemberIdForUpdate(memberId);
        if (!member.getTierCode().equals(delta.getNewTier())) {
            String oldTier = member.getTierCode();
            member.setTierCode(delta.getNewTier());
            memberRepo.save(member);
            tierChangeLogRepo.save(new TierChangeLog(
                memberId, oldTier, delta.getNewTier(), "CASCADE_DOWNGRADE"
            ));
        }
    }
    
    jobRepo.markCompleted(jobId);
}
```
#### 5.3.1 ShadowContext 设计规范
```java
public class ShadowContext {
    private String memberId;
    
    // 时间轴上的等级变化线（从 tier_change_log 加载，按时间排序）
    private List<TierChangeEvent> tierTimeline;
    
    // 当前回放位置对应的等级
    private String currentTier;
    
    // 影子积分账户（纯内存，不落盘）
    private BigDecimal shadowBalance;
    private List<ShadowTransaction> shadowTransactions;

    /**
     * 推进时间轴到指定时刻，更新 currentTier 为该时刻应生效的等级
     */
    public void advanceToTime(LocalDateTime eventTime) {
        this.currentTier = tierTimeline.stream()
            .filter(t -> !t.getChangedAt().isAfter(eventTime))
            .max(Comparator.comparing(TierChangeEvent::getChangedAt))
            .map(TierChangeEvent::getNewTier)
            .orElse(getInitialTier());
    }

    /**
     * 获取初始等级（无变更记录时的默认等级）
     */
    private String getInitialTier() {
        // 从 Program 配置中读取默认入会等级
        return programConfig.getDefaultTier();
    }
}
```
* `ShadowContext` 的构建需要同时加载两类历史数据：
* `tier_change_log` 中该会员的所有等级变更记录，构建等级时间线
* `member` 表的初始入会等级作为时间线的起点
* 每次回放事件前，必须先调用 advanceToTime(eventTime) 将等级推到事件发生时刻应生效的状态，再用该状态参与规则推理。
### 5.4 积分退回与生命周期还原 (Redemption Reversal)
逆向交易不仅包含“买东西退货（扣回发出的积分）”，还包含“积分兑换后退换货（退回已扣除的积分）”。
在传统的简单架构中，退回积分往往是“新发一笔同等额度的积分，有效期重新计算”。但这在高级商业逻辑中是巨大的漏洞（用户快过期的积分通过“兑换再退款”实现了强制续期）。
本系统依托第四章设计的 `redemption_allocation` (核销分摊表)，实现生命周期的精准还原。
#### 5.4.1 退还积分执行逻辑
当接收到 `REDEMPTION_CANCEL` (取消兑换) 事件时，执行以下逻辑：
1. **追溯分摊记录**：根据原核销的 `redemption_transaction_id`，在 `redemption_allocation` 表中查出该笔兑换当时扣减了哪些原始批次（`accrual_transaction_id`）。
2. **批次还原**：
   * 找到原始的 `ACCRUAL` 批次流水。
   * 将分摊扣除的额度，加回至该批次的 `remaining_amount` 中。
   * 如果该批次的状态原为 `EXHAUSTED` (已耗尽)，将其恢复为 `ACTIVE`。
3. **过期判定重裁决**：
   * 系统检查该原始批次的 `expires_at`。
   * 如果 `expires_at` 晚于当前时间：积分正常恢复，用户可以继续使用。
   * 如果 `expires_at` 早于当前时间（已过期）：系统拦截该部分积分，直接触发 `EXPIRATION` (过期) 动作将其作废。但平台支持通过 `config_json` 配置 `refund_expired_points_grace_days`（退款过期积分宽限期），例如给予 7 天的挽回期，提升用户体验。
### 5.5 负资产 (Debt) 与系统级死信处理
如果逆向扣减时，用户账户余额已被清空，且扣减额度超过了第四章定义的 `overdraft_limit`（被动透支底线），系统将触发以下熔断保护：
* **停止扣减**：只将账户余额扣至允许的极限（如 0 或 -1000）。
* **生成追偿工单**：在 `negative_pending`（待冲抵挂账表）中记录未完成扣减的积分债务。
* **冻结高风险权益**：临时标记该用户的 `status` 为 `SUSPENDED_REDEMPTION`（禁止兑换），直至未来新入账的积分补齐该笔 `negative_pending` 的债务。
## 任务要求
请基于上述章节，生成以下四大类产出，严格遵循伪代码契约，尤其强调无锁化设计和短事务补偿的幂等性。

### 1. 级联重算任务表 `CascadeRecalcJob` 实体与 Repository
#### 1.1 实体定义
- 表名：`cascade_recalc_job`（需自行创建，不在原 DDL 中，但文档 5.3 提到了该任务表）
- 字段：
  - `id`：自增主键或 UUID
  - `program_code`：租户标识
  - `member_id`：受影响的会员 ID
  - `reverse_time`：退款/触发时间点（重算的起始点）
  - `status`：`PENDING` / `PROCESSING` / `COMPLETED` / `FAILED`
  - `retry_count`：已重试次数，默认 0
  - `max_retry_count`：最大重试次数，默认 3
  - `last_error`：最近错误信息，TEXT
  - `created_at`、`updated_at`
- 标记 `@MultiTenant`

#### 1.2 Repository 接口
- 继承 `JpaRepository`，提供：
  - `findByStatus(String status, Pageable pageable)`：用于拉取待处理任务
  - `findStuckJobs(String status, LocalDateTime since)`：查询卡在 `PROCESSING` 状态超过阈值的任务
  - `markCompleted(jobId)`：更新状态为 `COMPLETED`
  - `incrementRetryCount(jobId)`：重试次数加一

### 2. 影子上下文 `ShadowContext` 构建
#### 2.1 `ShadowContext` 类设计
严格遵循文档 5.3.1 定义：
- 字段：
  - `memberId`
  - `tierTimeline`：`List<TierChangeEvent>`，按时间排序的等级变更记录（从 `tier_change_log` 表加载）
  - `currentTier`：当前回放位置的等级
  - `shadowBalance`：虚拟积分余额，仅在内存中计算
  - `shadowTransactions`：虚拟流水集合（可选）
- 提供方法：
  - `advanceToTime(LocalDateTime eventTime)`：遍历 `tierTimeline`，将 `currentTier` 更新为在 `eventTime` 之前最近一次变更后的等级；若无记录则使用 `getInitialTier()` 获取默认入会等级。
  - `getInitialTier()`：从 `Program` 配置的 `config_json` 中读取默认等级。
- 构建方法（工厂类或静态方法）：
  - `buildShadowContext(memberId, LocalDateTime reverseTime)`：
    - 从 `tier_change_log` 表加载该会员在 `reverseTime` 之后的所有等级变更记录（`WHERE member_id = ? AND changed_at >= ?`），并按 `changed_at ASC` 排序。
    - 将 `currentTier` 设置为 `reverseTime` 之前应生效的等级（即反向查找最接近的等级变更记录，若无则用默认等级）。

### 3. 级联重算处理器 `CascadeRecalcJobProcessor`
#### 3.1 核心方法 `processCascadeRecalculation`
- **禁止使用简单的 `@Async`**，而是通过定时拉取 `PENDING` 任务的方式执行，以便于故障恢复。
- 提供一个 `@Scheduled` 方法 `pullAndProcess()`，每分钟拉取一定数量的 `PENDING` 任务，调用 `processCascadeRecalculation`。
- `processCascadeRecalculation(String jobId, String memberId, LocalDateTime reverseTime)` 逻辑：
  1. **设置任务为 PROCESSING**，并更新 `updated_at`。
  2. **构建 `ShadowContext`**，传入 `memberId` 和 `reverseTime`。
  3. **加载时间轴事件**：从 `TransactionEvent`（或 `EventFact`）表中查询该会员在 `reverseTime` 之后的所有正向交易事件，按 `event_time` 升序。
  4. **影子回放循环**：
     - 对每个事件：
       a. 调用 `shadowContext.advanceToTime(event.getEventTime())`，将等级推进到事件发生时应有的等级。
       b. 从事件中取出 `rule_snapshot_id`（产生该积分的规则快照 ID），加载对应的 `RuleSnapshot`。
       c. 调用 `ruleEngine.evaluate(shadowContext, event, snapshot)` 重新评估，得到“应发放积分”，并累积至 `ShadowContext`。
  5. **计算差额 Delta**：将影子账户的最终状态与当前账户的真实状态（如总积分、等级）对比，生成 `AccountDelta` 对象：
     - `pointsToDeduct`：需要扣减的积分（影子应发 < 真实已发）
     - `newTier`：影子账户计算出的正确等级
  6. **短事务提交补偿**：调用 `applyCompensationWithShortTransaction(memberId, delta, jobId)`。
  7. **异常处理**：若重放过程中发生异常，记录错误，增加重试次数，将任务重置为 `PENDING`（或若超限则 `FAILED` 并告警）。

#### 3.2 故障恢复定时器 `recoverStuckJobs`
严格按文档 5.3 的伪代码实现：
- `@Scheduled(fixedDelay = 60000)`
- 查询所有 `status = 'PROCESSING'` 且 `updated_at` 早于 5 分钟前的任务。
- 如果 `retry_count >= max_retry_count`，标记为 `FAILED` 并发送告警（通过 `AlertService` 或 `EventBridge`）。
- 否则，重置状态为 `PENDING`，`retry_count++`。

### 4. 短事务补偿执行器 `applyCompensationWithShortTransaction`
#### 4.1 核心方法
- 方法签名：`@Transactional protected void applyCompensationWithShortTransaction(String memberId, AccountDelta delta, String jobId)`
- **事务边界**：这是一个独立的短事务，与 `processCascadeRecalculation` 的长事务分离，保证最终补偿写入数据库时仅持锁极短时间。
- **幂等性校验**：
  - 创建补偿日志表 `compensation_log`（或利用 `cascade_recalc_job` 的 `COMPLETED` 状态），检查该 `jobId` 是否已经执行过补偿。
  - 若已执行，直接返回。
- **积分差额扣减**：
  - 若 `delta.getPointsToDeduct() > 0`，调用 `PointRedeemService` 的一个强制扣减方法（如 `forceDeductWithOverdraft`），该方法应能在余额不足时触发透支逻辑（第四阶段已有相关实现，此处调用即可）。
  - 记录一条 `CASCADE_RECALC_DEDUCT` 类型的流水，`remaining_amount` 正确更新。
- **等级修正**：
  - 若 `delta.getNewTier() != null`，获取 `Member`（加锁），比较当前等级，若不同则更新 `tier_code`，并在 `tier_change_log` 中记录一条变更，`change_reason` 为 `CASCADE_DOWNGRADE`。
- **标记任务完成**：更新 `cascade_recalc_job` 状态为 `COMPLETED`，记录完成时间。

#### 4.2 补偿日志表（可选但推荐）
- 实体 `CompensationLog`，字段至少包含 `jobId`（唯一索引）、`memberId`、`executedAt`。
- 用于幂等保证。

## 输出要求
- 生成完整的 Java 代码文件，包结构建议：
  - `com.example.loyalty.domain`：`CascadeRecalcJob`、`CompensationLog`（可选）、`TierChangeLog` 实体（若已有可复用）
  - `com.example.loyalty.repository`：上述实体的 Repository
  - `com.example.loyalty.service`：`ShadowContext` 类，`CascadeRecalcJobProcessor`，`CompensationExecutor`（包含 `applyCompensationWithShortTransaction`）
  - `com.example.loyalty.dto`：`AccountDelta` 数据传输类
  - `com.example.loyalty.event`：`CascadeRecalcCompletedEvent`（可选）
- 所有代码必须注释清晰，说明如何实现“无锁化”（不阻塞用户新交易）和“幂等性”（基于 jobId 的防重）。
- 依赖项：本阶段需要第四阶段的 `PointRedeemService`、`PointGrantService` 和 `MemberRepository` 等，请在注入时声明。
- 确保与之前的全局异常处理、多租户拦截兼容。

## 上下文衔接约定
- 第五阶段依赖第四阶段的 `AccountTransaction` 和 `RedemptionAllocation` 表；`rule_snapshot` 表需参照第六章，本阶段可先定义一个 `RuleSnapshot` 实体（包含 `drl_content` 等）或使用一个模拟的 Repository。
- 等级变更日志 `tier_change_log` 已在设计文档 DDL 中定义，本阶段可复用其 JPA 实体（若尚未生成，请一并提供）。
- 本阶段不涉及前端或 API 接口，仅为内部异步引擎。

请开始生成代码。





# 第六阶段：规则引擎、沙箱回归与统一 SPI 网关 — 标准提示词

## 系统提示（角色设定）
你是一位资深规则引擎专家与系统集成工程师，精通 Drools 8 规则引擎、GraalVM 多语言脚本沙箱、以及高并发下的 SPI 网关设计。你擅长在保持高性能的同时，搭建安全的隔离层（规则事实隔离、脚本执行隔离、第三方渠道隔离）。当前项目使用 **Spring Boot 3.x**、**Java 17**、**Drools 8 (KIE API)**、**GraalVM Polyglot**。

## 任务背景
我们正在构建一个下一代全渠道忠诚度管理 SaaS 平台。前五阶段已完成：
- **第一阶段**：多租户防御体系、`EventBridge`、幂等拦截器、统一响应体。
- **第二阶段**：核心实体（`Member`、`TransactionEvent`、`Program`）、JSONB 映射、Schema 版本下沉。
- **第三阶段**：全渠道 One-ID 入会引擎，含分布式锁与竞态处理。
- **第四阶段**：金融级积分账务，`PointGrantService`（瀑布流冲抵）、`PointRedeemService`（FIFO 核销 + 分摊溯源）。
- **第五阶段**：无锁化级联重算引擎，`ShadowContext`、`CascadeRecalcJobProcessor` 与短事务补偿。

本阶段是第六阶段，目标是实现平台的“智能大脑”与“对外开放接口”：
- **规则引擎侧**：构建线程安全的 Drools 执行环境，提供动态 Schema 驱动的 Fact Wrapper，并实现无阻塞的 KieBase 热更新。
- **SPI 网关闭侧**：对接天猫、京东等外部渠道，构建统一 SPI 网关，支持双轨映射（可视化连线与 GraalVM JS 脚本），并以极严苛的超时和异常策略保护系统不被第三方拖垮。

你将收到的文档章节如下（请先完整阅读再生成代码）：
- **第六章：规则引擎、沙箱与自动化回归**，重点：
  - 6.1 规则引擎基础架构（无状态会话、KieBase 缓存热替换、动作收集器）
  - 6.1.1 动作执行器与事务一致性
  - 6.1.2 KieBase 原子热替换与并发安全（`AtomicReference` 实现）

  - 6.3 影子沙箱与自动化回归（了解即可，本阶段不要求完全实现）
  - 6.4 弱阻断与强制放行机制（了解即可）
  ## 第六章：规则引擎、沙箱与自动化回归
本章规范了平台的核心“大脑”——规则引擎的架构设计。为了彻底解决传统 CRM 中“单点测试通过，上线后与老规则打架导致超发积分”的痛点，系统创新性地引入了 **AI 辅助生成**、**影子沙箱回放 (Shadow Sandbox)** 以及 **弱阻断强制放行 (Soft Block & Override)** 机制。
### 6.1 规则引擎基础架构 (Drools Integration)
平台采用 Drools 8 (KIE API) 作为基础规则引擎。为保障高并发下的线程安全与执行性能，必须遵循以下约束：
* **无状态会话 (Stateless Session)**：严禁使用 `StatefulKieSession`。每次事件推理必须创建一个全新的 `StatelessKieSession`，执行完毕后立刻释放，避免内存泄漏和并发污染。
* **KieBase 缓存热替换**：将编译好的 KieBase（规则库）按 Program 级别缓存在内存中。当有新规则发布时，原子替换该引用，实现规则热更新。
* **隔离的动作收集器 (Action Collector)**：DRL 脚本中绝对禁止直接调用 JPA/SQL 进行数据库操作。必须将发分、升级等指令推入线程安全的收集器，由外部的 `AccountTransactionService` 统一在事务中执行。
#### 6.1.1 动作执行器与事务一致性契约
`ActionCollector` 仅负责在规则推理过程中收集动作指令，严禁直接操作数据库。所有动作必须交由统一的 RewardExecutor 在单一数据库强事务中执行，确保积分发放与等级更新原子完成。
```java
@Service
public class RewardExecutor {
    
    @Autowired
    private PointGrantService pointGrantService;
    @Autowired
    private TierEvaluationService tierService;
    
    /**
     * 执行规则引擎产生的所有动作，必须在同一个事务中完成。
     * 任何一步失败，整个事务回滚，触发事件重试。
     */
    @Transactional
    public void executeRewards(String memberId, List<Action> actions) {
        for (Action action : actions) {
            if (action instanceof AwardPointsAction) {
                AwardPointsAction award = (AwardPointsAction) action;
                pointGrantService.grantPoints(
                    award.getProgramCode(), memberId, 
                    award.getAccountType(), award.getPoints(),
                    award.getRuleId(), award.getRuleSnapshotId()
                );
            } else if (action instanceof UpgradeTierAction) {
                UpgradeTierAction upgrade = (UpgradeTierAction) action;
                tierService.upgrade(memberId, upgrade.getNewTier(), upgrade.getReason());
            } else if (action instanceof DowngradeTierAction) {
                DowngradeTierAction downgrade = (DowngradeTierAction) action;
                tierService.downgrade(memberId, downgrade.getNewTier(), downgrade.getReason());
            }
            // 后续可扩展更多动作类型
        }
    }
}
```
* **AI 编码强制约束：**
* 禁止在 ActionCollector 或 DRL 脚本的 then 块中直接调用任何 JPA/SQL 操作。
* 禁止将 List<Action> 拆分为多个独立事务执行。
* 若 executeRewards 内抛出异常，事务全部回滚，由上层调用方（事件消费者）触发重试。

#### 6.1.2 KieBase 原子热替换与并发安全
当运营发布新规则或修改已有规则时，系统需要将重新编译的 KieBase 替换掉缓存中的旧版本。在高并发场景下，这个替换操作必须满足两个要求：

原子性：替换操作本身必须是原子的，不存在"读到一半旧、一半新"的中间状态。

无损切换：正在执行规则推理的线程不受影响，仍使用旧版本完成当前推理。

##### 6.1.2.1 基于 AtomicReference 的无锁原子替换
```java
@Component
@Slf4j
public class KieBaseCacheManager {

    // 使用 AtomicReference 保证引用更新的原子性
    private final ConcurrentHashMap<String, AtomicReference<KieBase>> cache = new ConcurrentHashMap<>();

    /**
     * 获取指定 Program 的当前 KieBase
     * 读取操作无锁，性能极高
     */
    public KieBase getKieBase(String programCode) {
        AtomicReference<KieBase> ref = cache.get(programCode);
        if (ref == null) {
            // 首次加载：双重检查锁定
            synchronized (this) {
                ref = cache.get(programCode);
                if (ref == null) {
                    KieBase kieBase = buildKieBase(programCode);
                    ref = new AtomicReference<>(kieBase);
                    cache.put(programCode, ref);
                }
            }
        }
        return ref.get(); // 原子读取，无锁
    }

    /**
     * 规则变更后，重新编译并原子替换 KieBase
     * 
     * 关键设计：
     * 1. 先构建新的 KieBase 对象（耗时操作，在锁外完成）
     * 2. 再通过 AtomicReference.set() 原子替换引用（瞬时操作）
     * 3. 旧 KieBase 仍被已获取引用的线程使用，等待 GC 回收
     */
    @Transactional
    public void refreshKieBase(String programCode) {
        // 1. 构建新版本 KieBase（可能耗时数百毫秒，但不在锁内）
        KieBase newKieBase = buildKieBase(programCode);
        
        // 2. 原子替换引用（纳秒级操作）
        AtomicReference<KieBase> ref = cache.get(programCode);
        if (ref == null) {
            synchronized (this) {
                ref = cache.get(programCode);
                if (ref == null) {
                    ref = new AtomicReference<>();
                    cache.put(programCode, ref);
                }
            }
        }
        
        KieBase oldKieBase = ref.getAndSet(newKieBase);
        log.info("Program [{}] KieBase 已热更新，旧版本将随 GC 回收", programCode);
        
        // 3. 可选：延迟清理旧 KieBase（如果内存敏感）
        //    不立即清理，因为可能有线程正在使用旧版本执行推理
    }

    /**
     * 编译 KieBase：从 DB 加载该 Program 的所有 ACTIVE 规则
     */
    private KieBase buildKieBase(String programCode) {
        List<Rule> activeRules = ruleRepo.findActiveByProgramCode(programCode);
        
        KieServices kieServices = KieServices.Factory.get();
        KieFileSystem kieFileSystem = kieServices.newKieFileSystem();
        
        for (Rule rule : activeRules) {
            // 为每条规则生成唯一的 DRL 文件路径
            String drlPath = "src/main/resources/rules/" 
                + programCode + "/" + rule.getRuleId() + ".drl";
            kieFileSystem.write(drlPath, rule.getDrlContent());
        }
        
        KieBuilder kieBuilder = kieServices.newKieBuilder(kieFileSystem);
        kieBuilder.buildAll();
        
        if (kieBuilder.getResults().hasMessages(Message.Level.ERROR)) {
            throw new RuleCompileException("规则编译失败: " + kieBuilder.getResults().getMessages());
        }
        
        KieContainer kieContainer = kieServices.newKieContainer(
            kieServices.getRepository().getDefaultReleaseId()
        );
        return kieContainer.getKieBase();
    }
}
```
##### 6.1.2.2 规则推理时的版本一致性
```java
@Service
public class RuleEngineService {

    @Autowired
    private KieBaseCacheManager cacheManager;

    /**
     * 执行规则推理
     * 
     * 关键设计：
     * 1. 一次性获取 KieBase 引用并持有到推理结束
     * 2. 即使执行过程中发生热更新，本线程仍使用旧版本完成推理
     * 3. 不阻塞、不重试、不受影响
     */
    public List<Action> evaluate(String programCode, List<Object> facts) {
        // 获取当前 KieBase 的快照引用
        KieBase kieBase = cacheManager.getKieBase(programCode);
        
        // 创建无状态会话
        StatelessKieSession session = kieBase.newStatelessKieSession();
        
        // 设置全局动作收集器
        ActionCollector collector = new ActionCollector();
        session.setGlobal("collector", collector);
        
        // 插入事实并执行
        session.execute(facts);
        
        return collector.getActions();
    }
}
```
##### 6.1.2.3 规则发布触发热更新的完整流程
```java
@Service
public class RulePublishService {

    @Autowired
    private KieBaseCacheManager cacheManager;

    /**
     * 发布规则（含沙箱回归、审批、热更新）
     */
    @Transactional
    public void publishRule(String programCode, String ruleId, 
                            boolean forceOverride, String overrideReason) {
        
        // 1. 沙箱回归测试（见 6.3 节）
        RegressionReport report = regressionService.runShadowRegression(programCode, ruleId);
        
        // 2. 冲突拦截（见 6.4 节）
        if (report.hasCriticalWarning() && !forceOverride) {
            throw new RULE_CONFLICT_REQUIRES_OVERRIDE(report);
        }
        
        // 3. 更新规则状态为 ACTIVE，并生成快照
        Rule rule = ruleRepo.findByRuleId(ruleId);
        rule.setStatus("ACTIVE");
        ruleRepo.save(rule);
        
        // 保存规则快照（用于级联重算）
        RuleSnapshot snapshot = new RuleSnapshot(
            ruleId, programCode, rule.getDrlContent(), 
            rule.getSalience(), rule.getActivationGroup()
        );
        snapshotRepo.save(snapshot);
        
        // 4. 原子热更新 KieBase（不重启、不阻塞、不丢请求）
        cacheManager.refreshKieBase(programCode);
        
        // 5. 记录审计日志
        auditLog.record(programCode, "RULE_PUBLISH", ruleId, 
            forceOverride ? Map.of("forceOverride", true, "reason", overrideReason) : null);
    }

    /**
     * 下线规则
     */
    @Transactional
    public void deactivateRule(String programCode, String ruleId) {
        Rule rule = ruleRepo.findByRuleId(ruleId);
        rule.setStatus("INACTIVE");
        ruleRepo.save(rule);
        
        // 同样触发热更新，新的 KieBase 将不包含该规则
        cacheManager.refreshKieBase(programCode);
    }
}
```
##### 6.1.2.4 并发安全保证总结
|场景|行为|风险|
|--|--|--|
|线程 A 正在执行推理，线程 B 触发热更新	|线程 A 继续使用旧 KieBase 完成推理	|无风险|
|线程 A 执行一半，ref.getAndSet() 发生|	线程 A 持有的是旧引用（Java 引用是值传递），不受影响	|无风险|
|线程 C 在热更新后首次获取|	通过 ref.get() 获取到新 KieBase	|无风险，这正是期望行为|
|多个线程同时触发 refreshKieBase|	各自构建新 KieBase，最后一个 getAndSet 的生效	|轻微浪费，但无数据错误|
|buildKieBase() 编译失败	|抛出异常，ref 不变，继续使用旧版本|	安全降级，服务不中断|
**AI 编码强制约束：**
* 严禁将 KieBase 直接存储在普通成员变量中。
* 严禁使用 synchronized 包裹推理执行逻辑（会导致规则更新时所有推理线程阻塞）。
* 必须使用 AtomicReference 或等效的原子类管理 KieBase 引用。
### 6.3 影子沙箱与自动化回归 (Shadow Sandbox Regression)
单条规则测试毫无意义，必须放到全局环境中进行交叉验证。平台提供自动化回放能力。
#### 6.3.1 回归测试数据源 (Regression Data Pool)
* **AI 模拟用例**：由上文 AI 自动生成的边界测试数据。
* **生产环境历史切片 (Production Snapshot)**：异步引擎自动从 `event_inbox` 表中拉取过去 7 天内成功处理的 500 条真实、多样化的交易流水。
#### 6.3.2 自动化回放伪代码 (Regression Execution Logic)
当运营人员点击“提交沙箱测试”时，系统执行以下双 KieSession 验证：
```java
public RegressionReport runShadowRegression(String programCode, String draftRuleDrl) {
    // 1. 构建双引擎环境
    KieBase baselineKie = ruleEngine.buildKieBase(programCode, RuleStatus.ACTIVE); // 仅线上老规则
    KieBase candidateKie = ruleEngine.buildKieBaseWithDraft(programCode, draftRuleDrl); // 老规则 + 新草稿
    
    // 2. 加载回放数据集 (7天生产切片 + AI 用例)
    List<EventFact> testSuite = loadRegressionDataset(programCode);
    RegressionReport report = new RegressionReport();
    
    // 3. 并发回放与 Diff 对比
    for (EventFact fact : testSuite) {
        // 基线执行
        List<Action> baselineActions = ruleEngine.evaluate(baselineKie, fact);
        // 候选执行
        List<Action> candidateActions = ruleEngine.evaluate(candidateKie, fact);
        
        // 对比结果 (计算 Delta)
        ActionDiff diff = compareActions(baselineActions, candidateActions);
        
        if (diff.hasUnexpectedDoubleReward()) {
            report.addCriticalWarning(fact, "发现未预期的叠加发分 (Double Reward)");
        } else if (diff.hasRuleShadowing()) {
            report.addWarning(fact, "老规则被互斥组遮蔽失效 (Shadowing)");
        }
        report.addTotalDeltaPoints(diff.getPointDifference());
    }
    
    return report;
}
```
### 6.4 弱阻断与强制放行机制 (Soft Block & Override)
如果沙箱回归报告发现了冲突，系统在业务管控上采用「弱阻断、强警告、重审计」策略。大促期间，运营往往需要“打破常规”，不能一味地进行物理死锁。
#### 6.4.1 警告分级 (Warning Levels)
* **绿色 (Pass)**：完全符合预期，新规则独立触发，无冗余叠加。允许常规提交。
* **黄色 (Warning)**：轻微规则遮蔽。高亮提示，允许常规提交。
* **红色 (Critical)**：发生严重的重复发分、优先级反转，或单笔产生天量积分。前端弹出强阻断确认框。
#### 6.4.2 强制放行接口契约与风控闭环
当遭遇红色警告但业务仍需强行上线时，要求 API 必须遵守以下规范：
* **接口参数强校验**：`POST /api/programs/{code}/rules/{id}/publish` 接口必须传入 `forceOverride = true` 和非空的 `overrideReason`。否则抛出 `RULE_CONFLICT_REQUIRES_OVERRIDE` 异常。
* **审批流升级**：带有 `forceOverride` 标签的规则，审批工作流将自动从“Program 管理员单签”升级为“高管双人会签”。
* **事后风控打标 (Audit Tagging)**：```drools
  该规则发布上线后，任何由此规则触发的 `account_transaction` 积分流水，其 JSON 扩展属性中必须自动打上风控标记：
  
  ```json
  {"risk_override": true, "override_by": "user_123"}
  ```
  这使得月底财务对账时，可以一键提取这部分“超发”的营销成本，做到责任可追溯。

- **第七章：全渠道会员通 SPI 统一接入与异构映射引擎**，重点：
  - 7.2 统一 SPI 网关设计（策略模式接口、网关 Controller 伪代码）
  - 7.3 双轨制映射引擎（GraalVM JS 脚本沙箱设计、可视化的 JSONPath 映射）
  - 7.5 事件标准化生命周期与死信处理（`event_inbox` 状态机与重试逻辑）
### 7.2 Unified SPI Gateway (统一 SPI 网关设计)
系统向所有第三方开放平台提供单一的标准化基础 Webhook 路径：
```text
POST /api/open/spi/{channel}/{programCode}/{action}
```
#### 7.2.1 策略模式接口契约 (Strategy Interface)
底层的验签和转换交由各渠道独立的适配器完成，网关层通过策略模式统一调度：
```java
public interface ChannelSpiHandler {
    // 渠道标识，如 "TMALL", "JD"
    String getChannelCode(); 
    // 1. 验签：解析 Header 和 Body，利用渠道配置 (AppSecret) 验证签名
    boolean verifySignature(HttpServletRequest request, byte[] rawBody, ChannelAdapterConfig config);
    // 2. 报文转换与核心处理：返回第三方要求的特定 JSON 结构
    Object handleAction(String action, String programCode, byte[] rawBody, ChannelAdapterConfig config);
    
    // 3. 构建渠道特定的错误格式响应 (必须确保对外响应解析为 HTTP 200)
    Object buildErrorResponse(Exception e);
}
```
#### 7.2.2 统一网关伪代码 (Gateway Controller Logic)
要求研发（或 AI 生成代码时）在 Controller 层必须实现幂等防重与超时隔离：
```java
@RestController
@RequestMapping("/api/open/spi/{channel}/{programCode}")
@Slf4j
public class SpiGatewayController {
    @Autowired
    private SpiHandlerFactory handlerFactory;
    
    @PostMapping("/{action}")
    public ResponseEntity<Object> handleSpi(@PathVariable String channel, @PathVariable String programCode,
                                            @PathVariable String action, HttpServletRequest request) {
        byte[] rawBody = readBody(request); 
        ChannelSpiHandler handler = handlerFactory.getHandler(channel);
        ChannelAdapterConfig config = configService.getValidConfig(programCode, channel);
        // 1. 安全验签拦截
        if (!handler.verifySignature(request, rawBody, config)) {
            spiLogService.logFailed(..., "SIGN_FAILED");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Signature Invalid");
        }
        // 2. 防雪崩隔离：限制单次处理最大耗时 2000ms
        CompletableFuture<Object> future = CompletableFuture.supplyAsync(() -> {
            // 【强制幂等】在 handleAction 内部，必须依赖第三方 request_id 做 Redis 防重
            return handler.handleAction(action, programCode, rawBody, config);
        }, spiThreadPool);
        try {
            Object spiResponse = future.get(2000, TimeUnit.MILLISECONDS);
            spiLogService.logSuccess(...);
            return ResponseEntity.ok(spiResponse);
            
        } catch (TimeoutException te) {
            log.error("SPI 响应超时", te);
            // 超时直接返回 HTTP 200 + 平台特定错误码，避免触发第三方熔断
            return ResponseEntity.ok(handler.buildErrorResponse(new BusinessException("TIMEOUT")));
        } catch (Exception e) {
            log.error("SPI 业务异常", e);
            // 业务异常（如余额不足、并发冲突）返回 HTTP 200 + 平台特定错误码
            return ResponseEntity.ok(handler.buildErrorResponse(e));
        }
    }
}
```
### 7.3 双轨制映射引擎 (Mapping Engine)
当 SPI 网关收到合法的 JSON 报文后，面临如何将其转换为内部系统能识别的 `TransactionEvent`（标准交易事件）。平台提供双轨制映射支持：
* **模式一：可视化连线 (VISUAL)**：前端通过 JSONPath 配置，如 `$.order.amount -> payload.order_amount`。适用于 80% 的简单场景。
* **模式二：高级脚本转换 (SCRIPT)**：利用 GraalVM JS 引擎 提供低代码映射，处理复杂聚合运算与 1:N 拆解。
#### 7.3.1 GraalVM 脚本安全沙箱设计 (JS Scripting Sandbox)
在 SaaS 平台执行用户或实施人员编写的 JavaScript 脚本极其危险（可能导致 CPU 死循环、内存 OOM 炸弹或反射调用宿主文件系统）。必须严格配置 GraalVM Context：
```java
public class ScriptingTransformer {
    public static Map<String, Object> transform(String jsCode, String sourceJson) {
        // 1. 极其严格的沙箱配置：绝对防御
        try (Context context = Context.newBuilder("js")
                .allowAllAccess(false)                 // 禁止特权访问
                .allowHostAccess(HostAccess.NONE)      // 严禁 JS 调用任何 Java 宿主类库
                .allowIO(false)                        // 严禁网络和文件 IO
                .allowNativeAccess(false)              // 严禁 Native 调用
                .option("engine.WarnInterpreterOnly", "false")
                .build()) {
            // 2. 防死循环限制：执行上限 50 毫秒
            context.interrupt(Duration.ofMillis(50));
            // 将原始 JSON 转换为 JS 对象并注入环境
            context.getBindings("js").putMember("sourceJsonStr", sourceJson);
            
            // 3. 执行用户编写的 transform 函数
            String executionWrapper = "const source = JSON.parse(sourceJsonStr);```droolsn" +
                                      jsCode + "```droolsn" +
                                      "JSON.stringify(transform(source));"; // 要求用户实现 transform(source) 
            
            Value result = context.eval("js", executionWrapper);
            
            return JSON.parseObject(result.asString(), new TypeReference<Map<String, Object>>() {});
            
        } catch (PolyglotException e) {
            // 发生超时、内存超限或语法错误，必须持久化到 DB 供实施人员 Debug
            throw new ScriptTransformException("脚本转换失败: " + e.getMessage(), e);
        }
    }
}
```
#### 7.3.2 脚本编写规范示例 (JS Code Template)
实施人员在前端代码编辑器中编写如下转换逻辑：
javascript
```
// 示例：将第三方发来的冗余订单报文，转换为平台标准的 1:N 实体结构
function transform(source) {
    // 1. 组装主表数据 (Event)
    let target = {
        event_type: "ORDER_PAID",
        member_id: source.user.mobile, // 将外部手机号映射为主键
        payload: {
            order_amount: source.order.total_fee / 100, // 分转元
            external_order_id: source.order.id
        },
        lines: [] // 预留 1:N 数组
    };
    // 2. 遍历拆解 1:N 明细 (Order Items)
    if (source.items && source.items.length > 0) {
        for (let i = 0; i < source.items.length; i++) {
            let item = source.items[i];
            if (item.status === 'PAID' && item.price > 0) {
                target.lines.push({
                    sku_code: item.sku,
                    goods_name: item.brand + ' - ' + item.title,
                    quantity: item.qty
                });
            }
        }
    }
    return target;
}
```
### 7.5 事件标准化生命周期与死信处理
所有外部事件（SPI 回调、Open API 请求）在进入核心业务引擎前，必须经过 event_inbox 进行标准化处理。这是一个从“异构原始报文”到“标准 EventFact”的可靠转换过程。

#### 7.5.1 状态机定义
event_inbox.status 的完整状态流转：
```text
                  ┌──────────┐
                  │ RECEIVED │  原始报文已入库，等待处理
                  └────┬─────┘
                       │
                       ▼
                  ┌──────────┐
                  │VALIDATING│  验签、幂等检查、基础字段校验
                  └────┬─────┘
                       │
              ┌────────┼────────┐
              │                 │
              ▼                 ▼
         ┌─────────┐      ┌──────────┐
         │VALIDATED│      │  REJECTED │  验签失败 / 幂等重复 / 字段缺失
         └────┬────┘      └──────────┘
              │
              ▼
         ┌──────────┐
         │PROCESSING│  执行映射引擎（VISUAL / SCRIPT），转换为 EventFact
         └────┬─────┘
              │
     ┌────────┼────────┐
     │                 │
     ▼                 ▼
┌──────────┐     ┌──────────┐
│COMPLETED │     │TRANSFORM │  映射/转换失败（脚本报错、字段缺失）
└──────────┘     │  _FAILED │
                 └────┬─────┘
                      │
                      ▼
                 ┌──────────┐
                 │RETRYING  │  自动重试（最多 3 次，指数退避）
                 └────┬─────┘
                      │
              ┌───────┼───────┐
              │               │
              ▼               ▼
         ┌──────────┐    ┌──────────┐
         │COMPLETED │    │  DEAD    │  重试耗尽，转入死信队列
         └──────────┘    └──────────┘
```
#### 7.5.2 各状态处理逻辑
```java
@Service
public class EventInboxProcessor {

    /**
     * 定时拉取 RECEIVED 状态的事件，推进到 VALIDATING
     */
    @Scheduled(fixedDelay = 1000)
    public void processReceived() {
        List<EventInbox> events = eventInboxRepo.findByStatus("RECEIVED", 100);
        for (EventInbox event : events) {
            event.setStatus("VALIDATING");
            eventInboxRepo.save(event);
            // 异步处理，避免阻塞拉取循环
            asyncProcessor.submit(() -> validateAndProcess(event));
        }
    }

    /**
     * 验签 + 幂等检查 + 基础校验
     */
    private void validateAndProcess(EventInbox event) {
        try {
            // 1. 幂等检查：同一 idempotency_key 是否已有 COMPLETED 记录
            if (eventInboxRepo.existsByIdempotencyKeyAndStatus(
                    event.getIdempotencyKey(), "COMPLETED")) {
                event.setStatus("REJECTED");
                event.setRejectReason("DUPLICATE");
                eventInboxRepo.save(event);
                return;
            }

            // 2. 基础字段校验
            if (StringUtils.isEmpty(event.getProgramCode()) 
                || StringUtils.isEmpty(event.getPayload())) {
                event.setStatus("REJECTED");
                event.setRejectReason("MISSING_FIELDS");
                eventInboxRepo.save(event);
                return;
            }

            // 3. 验签通过
            event.setStatus("VALIDATED");
            eventInboxRepo.save(event);

            // 4. 进入映射转换
            transformAndComplete(event);

        } catch (Exception e) {
            event.setStatus("TRANSFORM_FAILED");
            event.setRetryCount(event.getRetryCount() + 1);
            event.setLastError(e.getMessage());
            eventInboxRepo.save(event);
        }
    }

    /**
     * 映射引擎执行转换，成功后投递到 EventBridge
     */
    private void transformAndComplete(EventInbox event) {
        event.setStatus("PROCESSING");
        eventInboxRepo.save(event);

        try {
            // 根据渠道配置，选择映射模式
            ChannelAdapterConfig config = configService.getValidConfig(
                event.getProgramCode(), event.getChannel()
            );

            Map<String, Object> standardPayload;
            if ("SCRIPT".equals(config.getMappingMode())) {
                // 高级脚本模式：走 GraalVM 沙箱
                standardPayload = scriptingTransformer.transform(
                    config.getTransformScript(), event.getPayload().toString()
                );
            } else {
                // 可视化模式：走 JSONPath 映射
                standardPayload = visualMapper.transform(
                    config.getRequestMapping(), event.getPayload()
                );
            }

            // 构建标准 EventFact 并投递
            EventFact fact = EventFact.builder()
                .eventId(event.getId().toString())
                .programCode(event.getProgramCode())
                .memberId(extractMemberId(standardPayload))
                .eventType(extractEventType(standardPayload))
                .channel(event.getChannel())
                .eventTime(event.getCreatedAt())
                .idempotentKey(event.getIdempotencyKey())
                .payload(standardPayload)
                .ruleSnapshotId(config.getActiveRuleSnapshotId())
                .build();

            eventBridge.publish("loyalty-events", fact.getMemberId(), fact);

            event.setStatus("COMPLETED");
            eventInboxRepo.save(event);

        } catch (ScriptTransformException e) {
            // GraalVM 沙箱异常，记录转换日志供 Debug
            event.setTransformLogs(Map.of(
                "error", e.getMessage(),
                "script", config.getTransformScript()
            ));
            throw e; // 抛出，由外层 catch 转入 TRANSFORM_FAILED
        }
    }

    /**
     * 定时扫描 TRANSFORM_FAILED 状态的事件，执行重试（最多 3 次，指数退避）
     */
    @Scheduled(fixedDelay = 5000)
    public void retryFailed() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(1);
        List<EventInbox> failed = eventInboxRepo.findRetryable("TRANSFORM_FAILED", 3, threshold);
        for (EventInbox event : failed) {
            event.setStatus("RETRYING");
            event.setRetryCount(event.getRetryCount() + 1);
            eventInboxRepo.save(event);
            asyncProcessor.submit(() -> transformAndComplete(event));
        }
    }

    /**
     * 定时扫描重试耗尽的事件，转入死信并告警
     */
    @Scheduled(fixedDelay = 10000)
    public void moveToDead() {
        List<EventInbox> exhausted = eventInboxRepo.findExhaustedRetries("TRANSFORM_FAILED", 3);
        for (EventInbox event : exhausted) {
            event.setStatus("DEAD");
            eventInboxRepo.save(event);
            alertService.sendAlert(String.format(
                "事件标准化失败，已进入死信队列。id=%s, program=%s, channel=%s, error=%s",
                event.getId(), event.getProgramCode(), event.getChannel(), event.getLastError()
            ));
        }
    }
}
```
#### 7.5.3 死信处理策略
|状态|含义|处理方式|
|--|--|----|
|REJECTED|验签失败 / 幂等重复 / 字段缺失|保留记录供审计，不做重试。若为幂等重复则直接丢弃。|
|DEAD	映射转换失败，重试耗尽|	触发告警通知运维/实施人员。管理后台提供「死信重放」按钮，人工排查原因后可手动重试。|
死信重放接口：
```java
@PostMapping("/api/admin/events/{id}/replay")
public Response replayDeadEvent(@PathVariable Long id) {
    EventInbox event = eventInboxRepo.findById(id);
    if (!"DEAD".equals(event.getStatus())) {
        throw new BusinessException("ERR_INVALID_STATUS", "仅死信事件可重放");
    }
    // 重置状态，重新进入处理流程
    event.setStatus("RECEIVED");
    event.setRetryCount(0);
    event.setLastError(null);
    eventInboxRepo.save(event);
    return Response.success();
}
```
#### 7.5.4 event_inbox 表补充字段
```sql
ALTER TABLE event_inbox
ADD COLUMN retry_count INT DEFAULT 0,            -- 重试次数
ADD COLUMN max_retry INT DEFAULT 3,              -- 最大重试次数
ADD COLUMN last_error TEXT,                      -- 最近一次错误信息
ADD COLUMN reject_reason VARCHAR(50),            -- 拒绝原因（DUPLICATE / MISSING_FIELDS / SIGN_FAILED）
ADD COLUMN next_retry_at TIMESTAMPTZ;            -- 下次重试时间（指数退避）
```
## 任务要求
请基于上述章节，生成以下四大核心模块。所有代码必须严格遵循文档中的伪代码和设计约束。

### 1. Drools 事实包装器（Fact Wrappers）
#### 1.1 `MemberFact` 类
- 从 `Member` 实体中提取必要信息，封装为 Drools 可用的纯数据对象。
- 字段：`memberId`、`tierCode`、`extAttributes`（`Map<String, Object>`）。
- 必须提供类型安全的辅助方法：
  - `getExtString(String key)`：从 `extAttributes` 获取字符串值，若不存在返回 `null`。
  - `getExtNumber(String key)`：从 `extAttributes` 获取数值，若不存在返回 `0.0`。
- 禁止直接将 JPA Entity 传入 KieSession，所有规则推理必须通过此包装器。

#### 1.2 `EventFact` 类
- 从 `TransactionEvent`（或事件收件箱的标准数据）中提取信息。
- 字段：`eventType`、`memberId`、`channel`、`eventTime`、`idempotentKey`、`payload`（`Map<String, Object>`）。
- 同样提供 `getPayloadString(key)`、`getPayloadNumber(key)` 等辅助方法。
- 注意：`memberId` 应匹配 `MemberFact` 中的 `memberId`。

### 2. KieBase 缓存管理与原子热替换
#### 2.1 `KieBaseCacheManager` 类
严格按照文档 6.1.2 的伪代码实现：
- 内部使用 `ConcurrentHashMap<String, AtomicReference<KieBase>>` 缓存各 Program 的 KieBase。
- 方法：
  - `getKieBase(String programCode)`：如果缓存不存在，使用双重检查锁定同步创建；否则通过 `AtomicReference.get()` 无锁读取。
  - `refreshKieBase(String programCode)`：重新编译指定 Program 的规则（从 DB 加载 ACTIVE 状态的规则生成 DRL 文件），构建新的 KieBase 后，通过 `AtomicReference.getAndSet()` 原子替换。替换前不删除旧引用，保证正在执行推理的线程不受影响。
- 辅助方法 `buildKieBase(String programCode)`：实现从数据库拉取所有活跃规则，使用 `KieServices` 动态编译，若编译失败应抛出明确的 `RuleCompileException`。
- **强制约束**：推理执行线程通过 `getKieBase` 拿到的是某一时刻的快照引用，热更新过程绝不阻塞推理，也绝不使用 `synchronized` 包裹推理逻辑。

#### 2.2 `RuleEngineService` 类
- 提供 `List<Action> evaluate(String programCode, List<Object> facts)` 方法：
  - 获取当前 KieBase 快照。
  - 创建 `StatelessKieSession`。
  - 设置全局动作收集器（`ActionCollector`）。
  - 插入所有事实（`MemberFact`、`EventFact` 等）并执行。
  - 返回收集到的 `List<Action>`。

### 3. 统一 SPI 网关与双轨映射引擎
#### 3.1 策略接口 `ChannelSpiHandler`
- 接口定义三个方法（与文档 7.2.1 一致）：
  - `getChannelCode()`：返回渠道标识，如 `"TMALL"`, `"JD"`。
  - `verifySignature(HttpServletRequest request, byte[] rawBody, ChannelAdapterConfig config)`：验签。
  - `handleAction(String action, String programCode, byte[] rawBody, ChannelAdapterConfig config)`：核心业务处理，返回第三方要求的 JSON 格式。
  - `buildErrorResponse(Exception e)`：构建渠道特定的错误响应（但要保证最终网关以 HTTP 200 返回）。

#### 3.2 `SpiGatewayController` 控制器
严格按照文档 7.2.2 的伪代码实现：
- 路径：`POST /api/open/spi/{channel}/{programCode}/{action}`
- 流程：
  1. 根据 `channel` 获取对应的 `ChannelSpiHandler` 实现（通过 `SpiHandlerFactory`）。
  2. 读取请求原始 Body 字节。
  3. 调用 `handler.verifySignature()`，失败则记录日志并返回 HTTP 401。
  4. 使用 `CompletableFuture` 异步调用 `handler.handleAction()`，**强制设置 2000ms 超时**。
  5. 如果超时，捕获 `TimeoutException`，调用 `handler.buildErrorResponse()` 返回 HTTP 200（防止第三方熔断）。
  6. 如果捕获到任何业务异常，同样调用 `buildErrorResponse()` 返回 HTTP 200。
  7. 记录 SPI 审计日志（利用 `ChannelSpiLog` 实体和 Repository）。

#### 3.3 双轨映射引擎
##### 3.3.1 GraalVM 脚本沙箱 `ScriptingTransformer`
- 方法签名：`Map<String, Object> transform(String jsCode, String sourceJson)`
- 严格按照文档 7.3.1 伪代码配置 GraalVM Context：
  - 禁止所有主机访问（`allowAllAccess(false)`, `allowHostAccess(HostAccess.NONE)`）。
  - 禁止 IO（`allowIO(false)`）。
  - 禁止 Native 调用（`allowNativeAccess(false)`）。
  - 设置执行超时 50ms（`context.interrupt(Duration.ofMillis(50))`）。
- 将原始 JSON 注入上下文，执行用户编写的 `transform(source)` 函数。
- 如果发生 `PolyglotException`（超时、内存超限或语法错误），必须包装为 `ScriptTransformException` 抛出，并保留脚本内容和错误信息供调试。

##### 3.3.2 可视化映射器 `VisualMapper`
- 提供一个基于 JSONPath 的简单实现：根据配置的映射关系（如 `$.order.amount -> payload.order_amount`），从源 JSON 提取字段并组装目标 Map。
- 可依赖 `com.jayway.jsonpath` 库。
- 注意处理缺失字段时的默认值或异常。

#### 3.4 事件收件箱 `EventInboxProcessor`（配套 SPI 网关）
- 实现 `EventInbox` 实体（对应 `event_inbox` 表），包含完整状态字段：`RECEIVED`, `VALIDATING`, `VALIDATED`, `PROCESSING`, `COMPLETED`, `TRANSFORM_FAILED`, `RETRYING`, `DEAD` 等。
- 实现 `EventInboxProcessor`：
  - 拉取 `RECEIVED` 事件，推进到 `VALIDATING`，进行幂等检查（基于 `idempotency_key`）。
  - 验证通过后调用映射引擎转换，成功后发布到 `EventBridge`，最终标记 `COMPLETED`；失败则进入 `TRANSFORM_FAILED`。
  - 定时重试 `TRANSFORM_FAILED` 的事件（最多 3 次，指数退避），耗尽后移入 `DEAD` 并告警。
  - 提供死信重放接口 `replayDeadEvent`（Controller 骨架）。

### 4. 动作执行器与事务一致性（可选但强烈建议）
- 实现 `RewardExecutor`（文档 6.1.1），在单一 `@Transactional` 中执行规则产生的 `AwardPointsAction`、`UpgradeTierAction` 等。
- 注意：`PointGrantService.grantPoints` 本身已有事务，确保积分发放和等级升级的原子性。

## 输出要求
- 生成完整的 Java 代码文件，包结构建议：
  - `com.example.loyalty.rule`：`MemberFact`, `EventFact`, `ActionCollector`, `KieBaseCacheManager`, `RuleEngineService`
  - `com.example.loyalty.rule.action`：`Action` 抽象类及 `AwardPointsAction`, `UpgradeTierAction` 等
  - `com.example.loyalty.spi`：`ChannelSpiHandler`, `SpiHandlerFactory`, `SpiGatewayController`
  - `com.example.loyalty.spi.transform`：`ScriptingTransformer`, `VisualMapper`
  - `com.example.loyalty.spi.model`：`ChannelAdapterConfig`, `EventInbox`, `ChannelSpiLog`
  - `com.example.loyalty.spi.processor`：`EventInboxProcessor`
  - `com.example.loyalty.service`：`RewardExecutor`
- 所有代码必须注释清晰，特别是沙箱的安全配置（GraalVM 限制项）和 SPI 的防御逻辑（2000ms 超时 + HTTP 200 异常返回）。
- 输出 Maven 依赖补充：`drools-core`, `drools-compiler`, `graalvm-js`（`org.graalvm.polyglot`），`json-path` 等。

## 上下文衔接约定
- 第一阶段已实现 `EventBridge`，第七阶段的事件标准化最终通过 `eventBridge.publish` 投递。
- 第二阶段已定义 `TransactionEvent` 实体，映射引擎可将其作为目标结构参考。
- 第四阶段已实现 `PointGrantService`，动作执行器可直接注入使用。
- 本阶段不涉及前端，但需提供可被调用的 API 端点。
- 多租户拦截器已自动处理 `program_code` 过滤，但 SPI 网关中手工获取的原始 SQL 需要显式传入租户参数。

请开始生成代码。






# 第七阶段：动态 UI Schema 渲染与前后端联通 — 标准提示词

## 系统提示（角色设定）
你是一位全栈开发工程师，精通 **React 18**、**阿里 Formily (Formily.js)** 表单引擎，以及 **Spring Boot 3.x** 后端 API 设计。你深刻理解 Schema-Driven UI 架构，能够同时处理后端 Schema 存储/校验逻辑，以及前端的动态表单渲染、联动逻辑和版本兼容。当前项目前后端分离，后端 API 遵循统一的 `ApiResponse<T>` 规范，前端通过 HTTP 调用后端接口。

## 任务背景
我们正在构建一个下一代全渠道忠诚度管理 SaaS 平台。前六阶段已完成：
- **第一阶段**：多租户防御体系、`EventBridge`、幂等拦截器、统一响应体。
- **第二阶段**：核心实体（`Member`、`TransactionEvent`）、JSONB 映射、Schema 版本下沉服务（`MemberExtService.saveMemberExtAttributes` 已实现双写 `_schema_version`）。
- **第三阶段**：全渠道 One-ID 入会引擎。
- **第四阶段**：金融级积分账务，含瀑布流冲抵与 FIFO 核销。
- **第五阶段**：无锁化级联重算引擎。
- **第六阶段**：Drools 规则引擎热更新、GraalVM 脚本沙箱、统一 SPI 网关。

本阶段是第七阶段，目标是打通“后端 Schema 配置 ↔ 前端动态渲染”的完整链路。运营人员可以在管理后台配置会员扩展属性 Schema，前端根据 Schema 自动生成表单和详情页，并支持字段废弃、版本兼容、和基于 DRL 依赖的删除前置校验。

你将收到的文档章节如下（请先完整阅读再生成代码）：
- **第八章：管理界面设计与前端动态渲染**，重点：
  - 8.2 核心技术栈选型（Formily + JSON Schema）
  - 8.3 动态渲染实现流程（8.3.1 后端元数据存储，8.3.2 运行态渲染伪代码，8.3.3 历史数据与 Schema 版本的兼容渲染）
  - 8.4 兼容性策略：字段废弃处理（废弃标记、向下兼容展示、规则引用检查、Schema 变更前置校验）
  - 8.5 复杂联动逻辑的实现（`x-reactions` 联动表达式）
### 8.2 核心技术栈选型
* **渲染引擎**：采用 **阿里 Formily (Formily.js)**。其特点是基于响应式编程模型，支持复杂的表单联动、异步校验，非常适合处理“规则引擎”级的复杂表单。
* **数据标准**：采用标准 JSON Schema 规范，后端存储结构即为 `Map<String, Object>` 的元数据定义。
#### 8.2.1 前端自定义组件注册机制

平台内置 Input、Select、NumberPicker 等基础组件，但企业级场景（如渠道选择器、上传图片、级联地址选择、特定行业组件）需要支持 Program 级别扩充自定义 x-component。
**组件注册规范：**
**后端 Schema 存储：**
在 program.config_json 的 field_schema 中，自定义组件通过 x-component 指定组件名，并通过 x-component-props 传递配置：
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
**前端组件注册：**
Formily 支持全局组件注册机制，自定义组件在应用初始化时注册：
```javascript
import { createForm, setValidateLanguage } from '@formily/core';
import { FormProvider, Field } from '@formily/react';

// 自定义组件实现
const CustomStoreSelector = (props) => {
  // 根据 x-component-props 动态加载门店列表
  const { endpoint, multiple } = props;
  // ... 组件逻辑
};

// 全局注册，使其在 Schema 中可通过 x-component 引用
const componentRegistry = {
  'CustomStoreSelector': CustomStoreSelector,
  'ImageUploader': ImageUploader,
  'CascadingAddress': CascadingAddress,
};

// 在 SchemaField 渲染时，解析 x-component 并匹配注册的组件
const renderField = (schema) => {
  const Component = componentRegistry[schema['x-component']];
  if (!Component) {
    console.warn(`未注册的自定义组件: ${schema['x-component']}`);
    return <FallbackField schema={schema} />;
  }
  return <Component {...schema['x-component-props']} />;
};
```
**微前端扩展（高级场景）：**
若自定义组件需要独立部署和版本管理，后端 Schema 的 x-component-props 中可指定微前端子应用地址：
```json
{
  "x-component-props": {
    "microApp": "https://micro-apps.example.com/store-selector/1.0.0/index.js",
    "moduleName": "StoreSelector"
  }
}
```
前端通过 qiankun 或 Module Federation 动态加载远程组件。


### 8.3 动态渲染实现流程
#### 8.3.1 后端元数据存储
在 DB 中，针对每个 Program 配置动态 Schema：
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
#### 8.3.2 运行态渲染伪代码 (React + Formily 逻辑)
在会员详情页，前端通过 `useEffect` 拉取 Schema 并动态加载：
```javascript
import { createForm } from '@formily/core'
import { FormProvider, Field } from '@formily/react'
import { FormItem, Input, NumberPicker } from '@formily/antd'
const DynamicMemberEditor = ({ programCode, memberId }) => {
  const [schema, setSchema] = useState(null);
  const form = useMemo(() => createForm(), []);
  // 1. 初始化时，根据 Program 动态获取 Schema
  useEffect(() => {
    api.getMemberSchema(programCode).then(res => {
      setSchema(res.data.field_schema);
    });
  }, [programCode]);
  // 2. 渲染器根据 Schema 动态生成表单项
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
8.3.3 历史数据与 Schema 版本的兼容渲染
```json
{
  "member_id": "M8821",
  "ext_attributes": {
    "_schema_version": "v1.0.0",
    "pet_name": "旺财",
    "favorite_store": "朝阳店"
  },
  "schema_version": "v1.0.0",
  "current_schema_version": "v1.2.0"
}
```
前端渲染逻辑：

```javascript
const DynamicMemberDetail = ({ memberData, currentSchema, schemaVersions }) => {
  // 1. 取数据写入时的 Schema 版本
  const dataVersion = memberData.ext_attributes?._schema_version || memberData.schema_version;
  
  // 2. 获取对应版本的 Schema 定义
  const effectiveSchema = schemaVersions[dataVersion] || currentSchema;
  
  // 3. 用对应版本的 Schema 渲染表单
  //    当前版本中已删除的字段，仍按旧 Schema 以只读方式展示
  return (
    <FormProvider form={form}>
      <SchemaField schema={effectiveSchema} />
      {/* 标记废弃字段 */}
      {renderDeprecatedFields(memberData, dataVersion, currentSchema)}
    </FormProvider>
  );
};
```
### 8.4 兼容性策略：字段废弃处理
在 SaaS 架构中，随着时间推移，旧的动态字段会逐渐被淘汰。
* **废弃标记 (Deprecation)**：运营在设计器中删除字段时，系统不执行 SQL DROP COLUMN（因为这是 JSONB 动态字段，底层无需变动表结构），而是将 Schema 中的字段属性标记为 `deprecated: true`。
* **向下兼容展示**：
  * 只读态：对于历史会员数据中包含已废弃字段的情况，前端详情页应增加一个“显示历史遗留字段”的开关。
  * 编辑态：废弃字段默认从编辑表单中隐藏，防止运营人员误操作。
* **规则引用检查**：后端在保存 Schema 变更时，必须进行“依赖引用检查”，若规则引擎 DRL 脚本正在引用该字段，则严禁直接删除该字段，必须强制运营先修改规则。
* **Schema 变更前置校验（后端执行）：**
```java
@Transactional
public void deprecateField(String programCode, String entityType, String fieldKey) {
    // 1. 检查是否有活跃规则引用了该字段
    List<Rule> activeRules = ruleRepo.findActiveByProgramCode(programCode);
    for (Rule rule : activeRules) {
        if (rule.getDrlContent().contains("extAttributes[\"" + fieldKey + "\"]") ||
            rule.getDrlContent().contains("getExtString(\"" + fieldKey + "\")") ||
            rule.getDrlContent().contains("getExtNumber(\"" + fieldKey + "\")")) {
            throw new BusinessException("ERR_FIELD_IN_USE", 
                "字段 [" + fieldKey + "] 被规则 [" + rule.getRuleId() + "] 引用，请先修改规则后再废弃该字段");
        }
    }
    
    // 2. 检查通过后，标记字段为 deprecated（而非物理删除）
    schemaService.markFieldDeprecated(programCode, entityType, fieldKey);
}
```
**废弃字段的向下兼容展示策略：**
* 已废弃字段在编辑表单中默认隐藏，但在详情页的「历史遗留字段」折叠区域中仍可查看。
* 当 dataVersion < currentVersion 时，前端自动显示「数据版本已过期」提示，并开放「升级到最新版本」按钮（将旧 Schema 数据迁移为新 Schema 结构）。
### 8.5 复杂联动逻辑的实现
对于“当宠物种类选择为‘狗’时，显示‘犬种明细’字段”这类复杂联动，无需手动编写 JS 逻辑，只需在 Schema 中声明 `x-reactions`：
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
通过这种方式，运营人员只需在管理后台的 UI 设计器中勾选联动条件，即可实时更新所有终端（包括 Web 门户、小程序后台）的渲染逻辑。
## 任务要求
请基于上述章节，生成后端和前端两大块的完整代码。所有实现必须严格遵循文档的 Schema 结构和联动规范。

### 1. 后端部分：Schema 管理 API 与前置校验

#### 1.1 Schema 存储数据结构
- 定义 `MemberSchema` 或 `DynamicFieldSchema` 的 JSONB 存储结构（可存储在 `Program` 的 `config_json` 中或独立的 `program_schema` 表，本阶段采用 `program_schema` 表）。
- 表 `program_schema` 关键字段：
  - `id`, `program_code`, `entity_type`（如 `"MEMBER"`）, `version`（如 `"v1.2.0"`）, `field_schema`（`JSONB`，包含完整的 JSON Schema）, `status`（`DRAFT` / `ACTIVE` / `DEPRECATED`）, `created_at`
- 提供对应的 JPA 实体和 Repository。

#### 1.2 `SchemaService` 服务
- 提供方法：
  - `getCurrentSchema(programCode, entityType)`：返回当前 `ACTIVE` 版本的最新 Schema。
  - `getSchemaByVersion(programCode, entityType, version)`：返回指定版本的 Schema（用于历史数据兼容渲染）。
  - `getAllSchemaVersions(programCode, entityType)`：返回所有版本列表。

#### 1.3 Schema 变更与字段废弃 API
##### 1.3.1 废弃字段
- **Controller**：`POST /api/admin/programs/{programCode}/schemas/{entityType}/deprecate-field`
- **请求体**：`{ "fieldKey": "pet_name" }`
- **逻辑**（`SchemaAdminService.deprecateField`）：
  1. 校验字段是否存在。
  2. **规则引用检查（核心）**：扫描该 Program 下所有 `ACTIVE` 状态的规则 DRL 内容，检查是否包含对该字段的引用（如 `extAttributes["fieldKey"]`、`getExtString("fieldKey")`、`getExtNumber("fieldKey")` 等模式）。如果发现引用，**必须抛出业务异常**（错误码 `ERR_FIELD_IN_USE`），错误消息中列出引用该字段的规则 ID。**禁止在规则引用未解除的情况下直接废弃字段**。
  3. 检查通过后，将当前 Schema 中该字段的 JSON Schema 定义标记为 `deprecated: true`（不物理删除），并生成一个新的 Schema 版本（状态为 `ACTIVE`，旧版本状态变更为 `DEPRECATED`）。
- 返回统一的 `ApiResponse`。

##### 1.3.2 获取当前 Schema（供前端渲染）
- **Controller**：`GET /api/admin/programs/{programCode}/schemas/{entityType}/current`
- 返回 `ApiResponse<Map<String, Object>>`，其中 `data` 为 `field_schema` 的 JSON 对象。

#### 1.4 动态属性保存接口（扩展第二阶段）
- **Controller**：`PUT /api/admin/programs/{programCode}/members/{memberId}/ext-attributes`
- 调用第二阶段的 `MemberExtService.saveMemberExtAttributes`，实现双写逻辑。
- 若请求中的字段包含已废弃字段，后端应允许保存历史数据，但需在响应中添加 `deprecated_fields` 提示。

### 2. 前端部分：基于 Formily 的动态渲染组件

#### 2.1 核心动态表单组件 `DynamicMemberForm`
- 使用 React 18 + TypeScript + Formily。
- 组件接收 Props：
  - `programCode`：租户标识
  - `memberId`（可选）：若传入则为编辑模式，需先加载历史数据
  - `onSubmit`：保存回调
- **组件逻辑**（参照文档 8.3.2 伪代码）：
  1. `useEffect` 初始化时，调用 `GET /api/admin/programs/{programCode}/schemas/MEMBER/current` 获取当前 Schema。
  2. 若 `memberId` 存在，则调用接口获取会员数据（含 `ext_attributes` 和 `schema_version`）。
  3. 比对数据版本与当前 Schema 版本：
     - 若不一致，在表单顶部显示 Alert 提示：“当前数据为旧版 Schema (v1.0.0)，部分字段可能已变更。建议保存后自动升级。”
     - 旧版数据中的字段，若在当前 Schema 中已被标记 `deprecated: true`，则仅以只读方式展示，并在字段旁标注“已废弃”。
  4. 使用 `<SchemaField>` 组件，传入当前 Schema 的 `field_schema`，自动渲染表单。
  5. 提交时，调用 `form.submit()` 获取表单值，调用 `PUT /.../ext-attributes` 接口保存。

#### 2.2 联动逻辑处理
- Formily 默认支持 `x-reactions` 表达式，确保生成的 Schema 中包含正确的联动配置。
- 示例：若 Schema 中某字段包含 `"x-reactions": "{{ $self.visible = ($deps[0] === 'dog') }}"` 和 `"x-dependencies": ["pet_type"]`，则表单会自动处理显示/隐藏逻辑。
- 在 `DynamicMemberForm` 内部无需手动编写联动逻辑，完全由 Schema 驱动。

#### 2.3 自定义组件注册机制（可选但建议）
- 参考文档 8.2.1，提供一个全局组件注册表 `componentRegistry`，当 Schema 中 `x-component` 为自定义组件名（如 `CustomStoreSelector`）时，从注册表中获取对应组件渲染。
- 若某组件未注册，则渲染一个友好的“未知组件”占位符，避免白屏。

#### 2.4 详情展示模式
- 提供一个只读模式（Props `readOnly=true`），所有字段以文本形式展示，废弃字段在折叠区域显示。

### 3. 后端 DRL 引用检查的具体实现要求
- **规则字段引用模式匹配**：
  - 正则或字符串搜索应覆盖以下三种典型引用写法：
    - `extAttributes["fieldKey"]`
    - `getExtString("fieldKey")`
    - `getExtNumber("fieldKey")`
  - 为安全起见，可采用**双向 JSON 解析**：读取 `field_schema` 的 `properties` 中所有未废弃的字段 Key，然后在 DRL 中搜索这些 Key。
- **异常定义**：`ERR_FIELD_IN_USE`，消息示例：`字段 [pet_name] 被规则 [RULE-101, RULE-205] 引用，请先修改规则后再废弃该字段。`

## 输出要求
- 生成完整的后端和前端代码文件。
- **后端**（包结构）：
  - `com.example.loyalty.domain`：`ProgramSchema` 实体
  - `com.example.loyalty.repository`：`ProgramSchemaRepository`
  - `com.example.loyalty.service`：`SchemaService`, `SchemaAdminService`
  - `com.example.loyalty.controller`：`SchemaController`, `MemberExtController`
- **前端**：
  - 组件 `DynamicMemberForm`（React 功能组件）
  - 配套的 `api.ts`（封装 HTTP 请求，自动携带 `X-Program-Code` 和 Token）
  - 组件注册表 `componentRegistry.ts`
- 确保前后端完全打通，接口遵循统一的 `ApiResponse` 格式。
- 添加适当的错误处理和加载状态。

## 上下文衔接约定
- 后端 API 需通过第一阶段的 `TenantFilter`，`program_code` 来自请求 Header `X-Program-Code`。
- 动态属性保存时，会自动触发第二阶段的 `_schema_version` 双写逻辑。
- 前端调用后端接口时，需附带 `Authorization` Bearer Token 和 `X-Program-Code` Header。
- 本阶段不涉及规则引擎的 DRL 修改 API（仅做引用检测）。

请开始生成代码。


# 第八阶段：前端全页面开发、联调、测试与部署准备 — 标准提示词

## 系统提示（角色设定）
你是一位资深全栈工程师与 DevOps 专家，精通 React 18 + TypeScript + Ant Design + Formily 2.x 前端架构，以及 Spring Boot 3.x 后端 API 联调。你同时具备 Docker、Kubernetes 和 Prometheus/Grafana 监控体系搭建能力。你注重代码质量、用户体验和系统稳定性，能够将设计文档中的交互细节精准转化为生产级代码。

## 任务背景
我们正在构建的下一代全渠道忠诚度管理 SaaS 平台，前七个阶段已完成：
- **第一阶段**：多租户防御体系、EventBridge、幂等拦截器、统一响应体。
- **第二阶段**：核心实体与 JSONB 双轨模型、Schema 版本下沉。
- **第三阶段**：全渠道 One-ID 入会引擎，含分布式锁与竞态兜底。
- **第四阶段**：金融级积分账务（瀑布流冲抵、FIFO 核销、分摊溯源）。
- **第五阶段**：无锁化级联重算与逆向交易引擎。
- **第六阶段**：Drools 规则引擎（含热更新）、GraalVM 脚本沙箱、统一 SPI 网关、EventInbox 状态机。
- **第七阶段**：动态 UI Schema 渲染基础组件（`DynamicFormRenderer`）、后端 Schema 管理 API（含字段废弃的 DRL 引用检查）、Program Schema 版本管理。

本阶段是第八阶段，也是最终阶段，目标如下：
1. **前端全页面开发**：基于补充的第八章详细界面设计（8.6-8.26），完成管理后台所有剩余页面的开发，包括可视化画布设计器、会员管理、积分管理、规则引擎、渠道配置、映射编辑器、系统设置等。
2. **前后端联调**：确保所有页面与后端 API 对接正确，处理加载态、空状态、错误态。
3. **集成测试**：编写端到端测试用例，覆盖核心业务流程（入会→发分→核销→退款→级联重算）。
4. **部署准备**：编写 Dockerfile、docker-compose.yml、Kubernetes 部署清单，配置 Prometheus 监控和 Grafana 面板。

你将收到的文档章节如下（请先完整阅读再生成代码）：
- **第八章（含补充设计）**：8.6 动态实体画布设计器，8.7-8.26 所有功能页面的详细交互设计、数据流、组件树、状态管理。
## 第八章：管理界面设计与前端动态渲染
在 SaaS 平台中，运营人员对“活动规则”和“会员属性”的调整频率极高。如果每增加一个会员字段（如：增加“宠物种类”）都要经历“后端修改 DB -> 前端修改代码 -> 打包发版”的周期，将严重降低业务响应速度。本系统采用 **Schema-Driven UI (Schema 驱动 UI)** 架构。
### 8.1 总体思路：低代码设计器与运行态渲染器
我们将前端界面逻辑分为两层：
* **设计器态 (Form Builder)**：管理员在管理后台通过画布拖拽，配置会员动态属性或活动规则。配置结果保存为 JSON Schema。
* **运行态 (Renderer)**：前端界面根据后端下发的 JSON Schema，自动渲染表单、列表或详情页，无需关心具体字段。
### 8.2 核心技术栈选型
* **渲染引擎**：采用 **阿里 Formily (Formily.js)**。其特点是基于响应式编程模型，支持复杂的表单联动、异步校验，非常适合处理“规则引擎”级的复杂表单。
* **数据标准**：采用标准 JSON Schema 规范，后端存储结构即为 `Map<String, Object>` 的元数据定义。
#### 8.2.1 前端自定义组件注册机制
平台内置 Input、Select、NumberPicker 等基础组件，但企业级场景（如渠道选择器、上传图片、级联地址选择、特定行业组件）需要支持 Program 级别扩充自定义 x-component。
**组件注册规范：**
**后端 Schema 存储：**
在 program.config_json 的 field_schema 中，自定义组件通过 x-component 指定组件名，并通过 x-component-props 传递配置：
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
**前端组件注册：**
Formily 支持全局组件注册机制，自定义组件在应用初始化时注册：
```javascript
import { createForm, setValidateLanguage } from '@formily/core';
import { FormProvider, Field } from '@formily/react';
// 自定义组件实现
const CustomStoreSelector = (props) => {
  // 根据 x-component-props 动态加载门店列表
  const { endpoint, multiple } = props;
  // ... 组件逻辑
};
// 全局注册，使其在 Schema 中可通过 x-component 引用
const componentRegistry = {
  'CustomStoreSelector': CustomStoreSelector,
  'ImageUploader': ImageUploader,
  'CascadingAddress': CascadingAddress,
};
// 在 SchemaField 渲染时，解析 x-component 并匹配注册的组件
const renderField = (schema) => {
  const Component = componentRegistry[schema['x-component']];
  if (!Component) {
    console.warn(`未注册的自定义组件: ${schema['x-component']}`);
    return <FallbackField schema={schema} />;
  }
  return <Component {...schema['x-component-props']} />;
};
```
**微前端扩展（高级场景）：**
若自定义组件需要独立部署和版本管理，后端 Schema 的 x-component-props 中可指定微前端子应用地址：
```json
{
  "x-component-props": {
    "microApp": "https://micro-apps.example.com/store-selector/1.0.0/index.js",
    "moduleName": "StoreSelector"
  }
}
```
前端通过 qiankun 或 Module Federation 动态加载远程组件。
### 8.3 动态渲染实现流程
#### 8.3.1 后端元数据存储
在 DB 中，针对每个 Program 配置动态 Schema：
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
#### 8.3.2 运行态渲染伪代码 (React + Formily 逻辑)
在会员详情页，前端通过 `useEffect` 拉取 Schema 并动态加载：
```javascript
import { createForm } from '@formily/core'
import { FormProvider, Field } from '@formily/react'
import { FormItem, Input, NumberPicker } from '@formily/antd'
const DynamicMemberEditor = ({ programCode, memberId }) => {
  const [schema, setSchema] = useState(null);
  const form = useMemo(() => createForm(), []);
  // 1. 初始化时，根据 Program 动态获取 Schema
  useEffect(() => {
    api.getMemberSchema(programCode).then(res => {
      setSchema(res.data.field_schema);
    });
  }, [programCode]);
  // 2. 渲染器根据 Schema 动态生成表单项
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
8.3.3 历史数据与 Schema 版本的兼容渲染
```json
{
  "member_id": "M8821",
  "ext_attributes": {
    "_schema_version": "v1.0.0",
    "pet_name": "旺财",
    "favorite_store": "朝阳店"
  },
  "schema_version": "v1.0.0",
  "current_schema_version": "v1.2.0"
}
```
前端渲染逻辑：
```javascript
const DynamicMemberDetail = ({ memberData, currentSchema, schemaVersions }) => {
  // 1. 取数据写入时的 Schema 版本
  const dataVersion = memberData.ext_attributes?._schema_version || memberData.schema_version;
  
  // 2. 获取对应版本的 Schema 定义
  const effectiveSchema = schemaVersions[dataVersion] || currentSchema;
  
  // 3. 用对应版本的 Schema 渲染表单
  //    当前版本中已删除的字段，仍按旧 Schema 以只读方式展示
  return (
    <FormProvider form={form}>
      <SchemaField schema={effectiveSchema} />
      {/* 标记废弃字段 */}
      {renderDeprecatedFields(memberData, dataVersion, currentSchema)}
    </FormProvider>
  );
};
```
### 8.4 兼容性策略：字段废弃处理
在 SaaS 架构中，随着时间推移，旧的动态字段会逐渐被淘汰。
* **废弃标记 (Deprecation)**：运营在设计器中删除字段时，系统不执行 SQL DROP COLUMN（因为这是 JSONB 动态字段，底层无需变动表结构），而是将 Schema 中的字段属性标记为 `deprecated: true`。
* **向下兼容展示**：
  * 只读态：对于历史会员数据中包含已废弃字段的情况，前端详情页应增加一个“显示历史遗留字段”的开关。
  * 编辑态：废弃字段默认从编辑表单中隐藏，防止运营人员误操作。
* **规则引用检查**：后端在保存 Schema 变更时，必须进行“依赖引用检查”，若规则引擎 DRL 脚本正在引用该字段，则严禁直接删除该字段，必须强制运营先修改规则。
* **Schema 变更前置校验（后端执行）：**
```java
@Transactional
public void deprecateField(String programCode, String entityType, String fieldKey) {
    // 1. 检查是否有活跃规则引用了该字段
    List<Rule> activeRules = ruleRepo.findActiveByProgramCode(programCode);
    for (Rule rule : activeRules) {
        if (rule.getDrlContent().contains("extAttributes["" + fieldKey + ""]") ||
            rule.getDrlContent().contains("getExtString("" + fieldKey + "")") ||
            rule.getDrlContent().contains("getExtNumber("" + fieldKey + "")")) {
            throw new BusinessException("ERR_FIELD_IN_USE", 
                "字段 [" + fieldKey + "] 被规则 [" + rule.getRuleId() + "] 引用，请先修改规则后再废弃该字段");
        }
    }
    
    // 2. 检查通过后，标记字段为 deprecated（而非物理删除）
    schemaService.markFieldDeprecated(programCode, entityType, fieldKey);
}
```
**废弃字段的向下兼容展示策略：**
* 已废弃字段在编辑表单中默认隐藏，但在详情页的「历史遗留字段」折叠区域中仍可查看。
* 当 dataVersion < currentVersion 时，前端自动显示「数据版本已过期」提示，并开放「升级到最新版本」按钮（将旧 Schema 数据迁移为新 Schema 结构）。
### 8.5 复杂联动逻辑的实现
对于“当宠物种类选择为‘狗’时，显示‘犬种明细’字段”这类复杂联动，无需手动编写 JS 逻辑，只需在 Schema 中声明 `x-reactions`：
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
通过这种方式，运营人员只需在管理后台的 UI 设计器中勾选联动条件，即可实时更新所有终端（包括 Web 门户、小程序后台）的渲染逻辑。
好的，我完全理解你的需求。当前的文档确实缺少一个**可视化、可交互的低代码设计器**的详细描述，而这恰恰是 AI 生成前端代码时最需要拿到的“精确蓝图”。
下面我将为你设计一份**可直接补充到设计文档第八章**的详细前端界面设计。这份设计会精确到组件布局、伪代码、交互细节，足以让 AI 理解并生成高质量的前端代码。
***
### 8.6 节 — 动态实体与关系画布设计器
#### 8.6.1 设计目标与核心理念
在 SaaS 平台中，不同行业的 Program 需要定义完全不同的会员扩展属性和业务对象（如“宠物信息”、“购车记录”）。传统表单式配置在面对复杂嵌套对象和实体关系时显得力不从心。
本设计器采用 **可视化实体关系建模（Visual Entity-Relationship Modeling）** 的理念，让运营人员像使用流程图工具一样，通过拖拽和连线来定义动态 Schema。最终生成的是标准的 JSON Schema，可直接被 Formily 动态渲染引擎消费。
#### 8.6.2 界面布局
```text
┌──────────────────────────────────────────────────────────────┐
│  顶部操作栏：[保存草稿] [发布Schema] [版本历史] [导入/导出]    │
├──────────┬───────────────────────────────────┬────────────────┤
│          │                                   │                │
│  悬浮式  │                                   │   属性/配置     │
│  工具条  │         主画布区域                 │   面板         │
│  ┌────┐  │                                   │   (右侧抽屉)   │
│  │实体│  │     ┌──────────┐                  │               │
│  └────┘  │     │  会员    │                  │  选中实体/     │
│  ┌────┐  │     │ (Member) │──┐               │  连线时显示    │
│  │业务│  │     └──────────┘  │               │  详细配置项    │
│  │对象│  │                   │ 1:N           │               │
│  └────┘  │     ┌──────────┐  │               │               │
│  ┌────┐  │     │ 宠物档案  │◄─┘               │               │
│  │连线│  │     │(PetInfo) │                  │               │
│  └────┘  │     └──────────┘                  │               │
│  ┌────┐  │                                   │               │
│  │删除│  │                                   │               │
│  └────┘  │                                   │               │
│          │                                   │               │
│  图层面板│                                   │               │
│  (左下角)│                                   │               │
└──────────┴───────────────────────────────────┴────────────────┘
```
**区域说明**：
* **顶部操作栏**：保存、发布、版本历史回滚、导入导出 JSON Schema。
* **悬浮工具条**：半透明悬浮在画布左上角，包含核心操作按钮，不遮挡画布内容。
* **主画布**：可无限拖拽、缩放（支持鼠标滚轮缩放 50%-200%），背景带浅色网格点。
* **属性配置面板**：右侧抽屉式面板，只在选中实体节点或连线时滑出。
* **图层面板**：左下角小窗口，显示当前画布所有节点列表，支持快速定位和可见性切换。
#### 8.6.3 悬浮工具条设计
工具条垂直排列，每个按钮带有 Tooltip 和键盘快捷键提示。
| 图标  | 按钮名称 | 功能描述                                   | 快捷键      |
| --- | ---- | -------------------------------------- | -------- |
| 🧩  | 实体对象 | 点击后在画布中央创建一个空的实体节点卡片                   | `E`      |
| 📦  | 业务对象 | 点击后在画布中央创建一个空的业务对象节点卡片（视觉上与实体区分，但结构相同） | `B`      |
| 🔗  | 连线模式 | 切换鼠标状态为连线模式，此时可以从实体边缘拖出连线到另一实体         | `L`      |
| 🗑️ | 删除选中 | 删除当前选中的节点或连线                           | `Delete` |
| 🔍  | 适应画布 | 自动缩放并平移，使所有节点可见                        | `F`      |
**拖拽创建增强**（进阶交互）：
* 用户可以从工具条**长按并拖拽**图标到画布指定位置，在释放位置创建节点，减少“先创建再拖拽”的步骤。
#### 8.6.4 实体节点卡片设计
每个放置在画布上的实体对象渲染为一个圆角矩形卡片，结构如下：
```text
┌──────────────────────────┐
│  📛 会员 (Member)        │  ← 实体名称行，可双击编辑
│  ─────────────────────── │
│  🔑 member_id : String   │  ← 主键行，前面有钥匙图标
│     name      : String   │  ← 普通字段
│     tier_code : String   │
│     status    : Enum     │  ← 枚举字段，显示下拉图标
│     ext_attrs : Object   │  ← 对象字段，可展开
│  ─────────────────────── │
│  [+ 添加字段]             │  ← 悬停时显示的添加按钮
└──────────────────────────┘
     ●  ← 连线锚点（四个边各一个，默认隐藏，悬停或连线模式时显示）
```
**交互细节**：
* **双击实体名称**：进入编辑模式，修改实体名和对应数据库表名。
* **点击字段行**：右侧配置面板显示该字段的详细属性（字段名、类型、是否主键、是否必填、默认值、枚举项等）。
* **拖拽字段**：可上下拖拽字段行调整顺序。
* **锚点连线**：鼠标悬停在锚点上时，锚点放大变为绿色，按下拖动拉出连线。
* **右键菜单**：提供“复制实体”、“删除实体”、“设为抽象对象”等操作。
#### 8.6.5 连线与实体关系配置
连线表示两个实体之间的数据关系。系统支持以下关系类型：
| 关系类型 | 连线样式           | 说明  | 示例                     |
| ---- | -------------- | --- | ---------------------- |
| 1:1  | 实线，两端无箭头       | 一对一 | 会员 ↔ 会员详情              |
| 1:N  | 实线，一端有箭头指向“多”方 | 一对多 | 会员 → 宠物档案（一个会员有多条宠物记录） |
| N:M  | 虚线，两端有箭头       | 多对多 | 会员 ←→ 优惠券              |
**连线交互细节**：
1. **创建连线**：点击工具条“连线模式”或按住 `L` 键，鼠标变为十字准星。从源实体锚点拖出线条，拖到目标实体锚点上释放。
2. **连线路径**：自动采用正交路由（拐直角弯），不与现有节点重叠。可拖拽调整拐点位置。
3. **选中连线**：点击线条，右侧面板滑出，显示关系配置：
   * 关系类型下拉（1:1 / 1:N / N:M）
   * 外键字段名（自动建议，也可手动输入，如 `member_id`）
   * 级联策略（级联删除、级联更新、设为空）
   * 描述文字（可选，显示在连线上方标签）
4. **标签显示**：连线中央默认显示关系类型标签（如 `1:N`），可自定义为业务含义文字（如 `拥有`）。
5. **删除连线**：选中后按 `Delete` 或右键“删除连线”。
#### 8.6.6 字段详细配置（右侧面板）
当选中实体卡片上的某一行字段时，右侧抽屉面板展开，显示以下配置项：
| 配置项                    | 控件类型                        | 说明                                                                                     |
| ---------------------- | --------------------------- | -------------------------------------------------------------------------------------- |
| 字段名 (Field Key)        | Input                       | 英文驼峰命名，如 `pet_name`                                                                    |
| 显示名 (Title)            | Input                       | 前端表单显示的标签，如“宠物名称”                                                                      |
| 数据类型 (Type)            | Select                      | String / Number / Boolean / Date / Enum / Object / Array / Ref(引用其他实体)                 |
| 是否主键 (Primary Key)     | Switch                      | 开启后字段行显示 🔑 图标，该实体必须有且仅有一个主键                                                           |
| 是否必填 (Required)        | Switch                      | 前端表单校验                                                                                 |
| 默认值 (Default)          | Input / Switch / DatePicker | 根据数据类型动态切换                                                                             |
| 枚举项 (Enum Values)      | Tags 动态添加                   | 仅当类型为 Enum 时显示，可添加多个选项值                                                                |
| 子对象Schema (Sub Schema) | 嵌套画布入口                      | 仅当类型为 Object/Array 时显示，点击打开子画布定义嵌套结构                                                   |
| 引用目标 (Ref Target)      | Select                      | 仅当类型为 Ref 时显示，从画布上已有实体中选择                                                              |
| 前端组件 (x-component)     | Select                      | Formily 组件选择：Input / NumberPicker / Select / DatePicker / Switch / Upload / Cascader 等 |
| 验证规则 (x-validator)     | JSON Editor 或简易规则构建器        | 如 `{ "min": 0, "max": 100 }`                                                           |
| 联动逻辑 (x-reactions)     | 联动规则构建器（可视化）                | 如“当 pet_type === 'dog' 时显示”                                                           |
| 是否废弃 (Deprecated)      | Switch + 日期                 | 开启后字段在前端默认隐藏，但历史数据可查看                                                                  |
| 描述 (Description)       | TextArea                    | 字段的业务说明                                                                                |
#### 8.6.7 画布交互与快捷键
| 操作     | 鼠标/键盘                       | 说明             |
| ------ | --------------------------- | -------------- |
| 平移画布   | 按住空格 + 拖拽 / 鼠标中键拖拽          | 移动画布视野         |
| 缩放画布   | 鼠标滚轮 / Ctrl + 滚轮            | 50% - 200% 范围  |
| 框选多个节点 | 在空白区域按下左键拖拽                 | 出现矩形选框，框选多个节点  |
| 多选节点   | Ctrl + 点击 / Shift + 点击      | 批量选中，可一起移动或删除  |
| 移动节点   | 左键拖拽节点标题栏                   | 可拖拽到画布任意位置     |
| 撤销/重做  | Ctrl + Z / Ctrl + Shift + Z | 操作历史栈（本地内存）    |
| 复制/粘贴  | Ctrl + C / Ctrl + V         | 复制选中节点，粘贴到画布   |
| 删除     | Delete / Backspace          | 删除选中节点或连线（需确认） |
| 快速创建实体 | 双击画布空白区域                    | 在双击位置创建一个空实体节点 |
#### 8.6.8 画布数据持久化与 Schema 生成
**内部数据结构**（前端画布状态）：
```typescript
interface CanvasState {
  nodes: EntityNode[];
  edges: RelationEdge[];
  viewport: { x: number; y: number; zoom: number };
}
interface EntityNode {
  id: string;            // 画布内部ID (UUID)
  entityName: string;    // 实体英文名，如 "pet_info"
  displayName: string;   // 实体中文显示名，如 "宠物档案"
  entityType: 'ENTITY' | 'BUSINESS_OBJECT';
  position: { x: number; y: number };
  fields: EntityField[];
}
interface EntityField {
  key: string;
  title: string;
  type: 'String' | 'Number' | 'Boolean' | 'Date' | 'Enum' | 'Object' | 'Array' | 'Ref';
  isPrimaryKey: boolean;
  required: boolean;
  defaultValue?: any;
  enumValues?: string[];
  refTarget?: string;       // 引用目标实体名
  subSchema?: EntityField[];// 嵌套子对象
  xComponent?: string;
  xValidator?: any;
  xReactions?: string;
  deprecated?: boolean;
}
interface RelationEdge {
  id: string;
  sourceNodeId: string;
  targetNodeId: string;
  sourceAnchor: 'top' | 'bottom' | 'left' | 'right';
  targetAnchor: 'top' | 'bottom' | 'left' | 'right';
  relationType: '1:1' | '1:N' | 'N:M';
  foreignKeyField?: string;
  cascadeStrategy?: 'CASCADE' | 'SET_NULL' | 'RESTRICT';
  label?: string;
}
```
**生成目标（提交到后端）**：
保存时，前端将 `CanvasState` 转换为标准 JSON Schema（符合 JSON Schema Draft 7 规范），连同实体关系映射一起发送到后端 `/api/admin/schemas/{entityType}` 接口。
转换示例：
json
```
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
      "shoe_size": {
        "title": "鞋码",
        "type": "number",
        "x-component": "NumberPicker"
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
#### 8.6.9 技术实现建议
**推荐技术栈**：
* **画布渲染**：`React Flow`（轻量、内置节点拖拽/连线/缩放/框选功能，避免从零实现）
* **节点卡片**：自定义 React Flow Node 组件，使用 Ant Design 的 `Card`、`Tag`、`Button`
* **状态管理**：`Zustand`（轻量，适合存储画布节点/边/视口状态）
* **拖拽创建**：React Flow 原生支持从自定义面板拖拽节点到画布
* **JSON Schema 转换**：前端 utils 层封装 `canvasStateToJsonSchema()` 转换函数
**关键伪代码（节点组件）**：
```typescript
// EntityNode.tsx
const EntityNode: React.FC<NodeProps> = ({ data, selected }) => {
  const { entityName, displayName, fields, onAddField, onSelectField } = data;
  return (
    <Card
      className={`entity-node ${selected ? 'selected' : ''}`}
      title={
        <div onDoubleClick={() => enterEditMode('name')}>
          {displayName} ({entityName})
        </div>
      }
      size="small"
      style={{ minWidth: 220, borderColor: selected ? '#1890ff' : '#d9d9d9' }}
    >
      {fields.map((field: EntityField) => (
        <div
          key={field.key}
          className={`field-row ${field.isPrimaryKey ? 'primary-key' : ''}`}
          onClick={() => onSelectField(field.key)}
        >
          {field.isPrimaryKey && <KeyOutlined />}
          <span className="field-name">{field.key}</span>
          <span className="field-type">: {field.type}</span>
        </div>
      ))}
      <Button type="dashed" size="small" block onClick={onAddField}>
        + 添加字段
      </Button>
      {/* React Flow 提供的连接点 */}
      <Handle type="source" position={Position.Right} />
      <Handle type="target" position={Position.Left} />
    </Card>
  );
};
```
#### 8.6.10 联动规则的可视化构建器（补充）
对于字段的 `x-reactions` 配置，除了支持手写表达式外，提供简化的可视化构建器：
1. 在右侧面板的“联动逻辑”区域，点击“添加联动规则”。
2. 弹出对话框，包含三个配置项：
   * **依赖字段**：下拉选择当前实体的其他字段。
   * **条件**：下拉选择运算符（`===`, `!==`, `>`, `<`, `includes`），输入比较值。
   * **目标效果**：选择要影响的字段属性（`显示/隐藏`, `必填/非必填`, `设置默认值`）。
3. 确认后自动生成 `x-reactions` 表达式字符串，并显示在配置面板中供高级用户手动编辑。
**示例**：“当 `pet_type` 等于 `dog` 时，显示 `dog_breed` 字段” 会被转换为：
json
```
{
  "x-reactions": "{{ $self.visible = ($deps[0] === 'dog') }}",
  "x-dependencies": ["pet_type"]
}
```
***
### 8.7 总结：章节关联关系
| 设计器中的概念             | 对应后端                               | 对应前端渲染                                 |
| ------------------- | ---------------------------------- | -------------------------------------- |
| 实体节点 (EntityNode)   | `program_schema` 表的一行记录            | Formily `SchemaField` 的 `properties`   |
| 字段定义 (EntityField)  | JSON Schema `properties` 中的一个属性    | 表单项（Input、Select 等）                    |
| 关系连线 (RelationEdge) | `entity_relations` 配置（用于级联查询和规则引擎） | 前端暂不直接使用，但影响数据查询                       |
| 子对象 (Sub Schema)    | 嵌套 JSON Schema `items.properties`  | Formily 的 `ObjectField` / `ArrayField` |
| 联动规则 (x-reactions)  | JSON Schema 中的 `x-reactions` 字符串   | Formily 自动解析执行                         |
| 废弃标记 (Deprecated)   | Schema 中字段的 `deprecated: true`     | 前端只读展示 + 标记                            |
### 8.8 全局框架与布局
#### 8.8.1 应用壳 (AppShell)
```text
┌──────────────────────────────────────────────────────────┐
│ 顶部导航栏 (Header)                            [通知] [用户]│
├──────────┬───────────────────────────────────────────────┤
│          │                                               │
│  左侧    │           内容区域 (Content)                   │
│  菜单    │           (React Router <Outlet/>)             │
│          │                                               │
│          │                                               │
├──────────┴───────────────────────────────────────────────┤
│ 底部状态栏 (可选)   当前 Program: BRAND-A | 环境: DEV      │
└──────────────────────────────────────────────────────────┘
```
**顶部导航栏**：
* 左侧：Logo + 应用名称“忠诚度管理平台”。
* 中央：**Program 切换器**（Select 下拉），切换当前工作的租户上下文，全局状态变化后刷新所有数据。
* 右侧：通知图标（Badge 数字），点击显示最近告警列表；用户头像下拉（个人信息、修改密码、退出）。
**左侧菜单**（Ant Design Menu，垂直模式，可折叠）：
1. 仪表盘 (Dashboard)
2. Program 管理
3. 会员中心
   * 会员列表
   * 会员身份合并（快捷入口）
4. 积分管理
   * 账户总览
   * 积分流水
5. 规则引擎
   * 规则列表
   * 沙箱回归测试
6. 渠道配置
   * 渠道列表
   * 映射编辑器
7. 动态Schema设计器
8. 系统设置
   * 角色权限
   * 操作日志
   * SPI 调用日志
**内容区**：面包屑导航 + `<Outlet />`，所有页面 API 请求自动注入 `X-Program-Code`。
***
### 8.9 仪表盘 (Dashboard)
#### 8.9.1 布局
```text
┌──────────────────────────────────────────────────────────┐
│ 指标卡片行                                                 │
│ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐     │
│ │ 会员总数  │ │ 今日新增  │ │ 积分发放  │ │ 积分核销  │     │
│ │  12,345  │ │   +123   │ │ 45.6万   │ │ 12.3万   │     │
│ │ ↑12%     │ │ 较昨日   │ │ 本月累计 │ │ 本月累计 │     │
│ └──────────┘ └──────────┘ └──────────┘ └──────────┘     │
├───────────────────────┬──────────────────────────────────┤
│ 积分发放/核销趋势图    │ 待处理告警列表                    │
│ (折线图，可切换日/周/月)│ (级联重算积压、SPI超时、租户污染)│
│                       │ ┌────┬────┬────┬────┐           │
│                       │ │类型│详情│时间│状态│           │
│                       │ └────┴────┴────┴────┘           │
└───────────────────────┴──────────────────────────────────┘
```
#### 8.9.2 交互细节
* 指标卡片点击可跳转到对应列表页（如点击“会员总数”进入会员列表）。
* 趋势图使用 `@ant-design/charts` 的 Line 图，支持时间范围选择器。
* 告警列表每行可点击查看详情或跳转处理页。
***
### 8.10 Program 管理
#### 8.10.1 Program 列表页
* 表格列：Program Code、名称、状态、会员数、创建时间、操作（编辑、复制、删除）。
* 顶部操作栏：搜索框、新建 Program 按钮。
* 点击“新建/编辑”弹出**全屏抽屉**或**新页面**，包含表单：
  * 基础信息：Program Code (唯一)、显示名称、描述。
  * 积分类型字典（动态表格）：类型编码、名称、可兑换、算等级、可转赠、允许负数。
  * 等级阶梯（可视化阶梯编辑器）：等级名、所需成长值、权益描述，可拖拽排序。
  * 逆向策略配置：透支限额、退款过期宽限天数等。
#### 8.10.2 等级阶梯编辑器（内嵌组件）
* 一个垂直列表，每行是一个等级（如银卡、金卡、钻石），可拖拽排序。
* 每行可编辑：等级名称、所需成长值阈值、等级权益描述（富文本）。
* 支持“添加等级”按钮。
***
### 8.11 会员中心
#### 8.11.1 会员列表页
* 搜索栏：手机号、会员ID、等级、状态、注册时间范围、**动态属性搜索**（根据当前 Program 的 Schema 动态生成输入框）。
* 表格列：会员ID、手机号（脱敏）、等级、状态、注册渠道、注册时间。
* 操作：查看详情、合并会员、导出选中。
* 批量导入按钮，上传 CSV/Excel，显示导入进度和错误报告。
#### 8.11.2 会员详情页
```text
┌──────────────────────────────────────────────────────────┐
│ ← 返回列表   会员详情 - 8821                     [停用] [合并]│
│ 等级: 金卡  状态: ENROLLED  注册渠道: 天猫  手机: 138****│
├──────────────────────────────────────────────────────────┤
│ [基本信息] [积分账户] [交易流水] [渠道关联] [属性扩展]   │  ← Tabs
├──────────────────────────────────────────────────────────┤
│ 选中 Tab 的内容                                           │
└──────────────────────────────────────────────────────────┘
```
* **基本信息 Tab**：只读表单，展示静态字段和未废弃的动态字段。
* **积分账户 Tab**：按积分类型分组展示卡片，每个卡片显示可用余额、累计获得/消耗/过期，点击进入该类型的流水列表。
* **交易流水 Tab**：带筛选的表格，包含时间、类型、金额、剩余额度、批次状态、关联规则等。支持查看溯源详情（点击某条核销记录，弹出其分配明细）。
* **渠道关联 Tab**：表格展示该会员绑定的所有渠道（渠道名、OpenID、绑定时间），支持手动解绑或添加绑定。
* **属性扩展 Tab**：嵌入 `DynamicFormRenderer` 组件，根据会员数据的 `schema_version` 加载对应历史或当前 Schema 渲染表单，支持编辑保存，版本不一致时提示升级。
***
### 8.12 积分管理
#### 8.12.1 账户总览页
* 按会员 ID 或手机号搜索，选择后展示其所有积分类型账户卡片（同会员详情页积分 Tab 类似）。
* 每个账户卡片点击可查看账户详情：透支限额、信用额度使用情况、累计统计、最近流水列表。
#### 8.12.2 积分流水页
* 全局流水列表（跨会员），支持按会员ID、积分类型、交易类型、时间范围筛选。
* 表格列：会员ID、类型、流水号、金额、剩余额度、状态、时间。
* 导出功能（异步导出生成Excel，下载中心通知）。
***
### 8.13 规则引擎
#### 8.13.1 规则列表页
* 左侧规则树：按 Program → 规则分组（activation-group）展示。
* 右侧表格：规则ID、名称、状态（草稿/活跃/停用）、优先级、分组、最后修改时间。
* 操作：编辑、复制、停用/启用、沙箱测试。
#### 8.13.2 规则编辑器页
```text
┌──────────────────────────────────────────────────────────┐
│ 规则编辑 - RULE_101            [保存草稿] [沙箱测试] [发布] │
├─────────────┬──────────────────────────┬─────────────────┤
│ 基本信息    │                          │ 属性面板         │
│ 名称：      │   AI 辅助区域            │ (选中节点时出现) │
│ 分组：      │ ┌────────────────────┐  │                 │
│ 优先级：    │ │ 用自然语言描述规则.. │  │                 │
│ 条件：      │ │ "周末买手机>5000送100"│ │                 │
│ 动作类型：  │ │      [生成规则]      │  │                 │
│             │ └────────────────────┘  │                 │
│             │                          │                 │
│             │   DRL 代码编辑器          │                 │
│             │ ┌────────────────────┐  │                 │
│             │ │ rule "..." {       │  │                 │
│             │ │   when ...         │  │                 │
│             │ │   then ...         │  │                 │
│             │ │ }                  │  │                 │
│             │ └────────────────────┘  │                 │
└─────────────┴──────────────────────────┴─────────────────┘
```
* AI 生成区域：输入自然语言，调用后端 LLM 接口，返回 JSON（含推荐 DRL 和测试用例），展示在下方预览区，用户可采纳或修改。
* DRL 编辑器：基于 Monaco Editor，支持 Drools 语法高亮、自动补全。
* 属性面板：当点击 DRL 中的某些模式时，展示相关 Fact 字段和可用操作提示。
#### 8.13.3 沙箱回归测试页
* 左侧：选择测试数据集（AI 模拟数据集 / 生产切片 7 天内 500 条）。
* 右侧：**双列对比视图**，左列“基线规则结果”，右列“候选规则结果”，差异行高亮红色。
* 底部：汇总报告卡片，显示警告级别（绿/黄/红）及差异统计。
* 如果出现红色警告，发布按钮变为“强制发布”，点击弹出理由输入框和确认。
***
### 8.14 渠道配置
#### 8.14.1 渠道列表页
* 表格列：渠道标识（TMALL/JD）、渠道名称、映射模式（VISUAL/SCRIPT）、状态（DRAFT/ACTIVE）、操作（编辑、测试连接、查看日志）。
* 新建渠道按钮，弹出选择渠道类型，填写基础信息（AppKey、Secret等加密存储）。
#### 8.14.2 渠道编辑/配置页
* 上方：渠道基本信息表单。
* 下方：Tab 切换 “映射配置” 和 “验签配置”。
* **映射配置 Tab**：嵌入映射编辑器组件（详见 8.15）。
* **验签配置 Tab**：展示当前验签逻辑说明，允许配置自定义验签脚本（预留）。
***
### 8.15 映射编辑器
#### 8.15.1 布局
```text
┌──────────────────────────────────────────────────────────┐
│ 映射编辑器 - TMALL                                 [保存]  │
├─────────────┬──────────────┬─────────────────────────────┤
│ 模式: VISUAL│ 脚本: SCRIPT │                             │
│ [当前选中]  │              │                             │
├─────────────┴──────────────┼─────────────────────────────┤
│ 左侧面板：源数据预览        │ 右侧面板：目标数据结构预览     │
│ (粘贴示例JSON自动解析树)    │ (树形展示目标Schema)         │
│ ┌─ order                   │ ┌─ TransactionEvent          │
│ │  ├─ id: "123"            │ │  ├─ event_type: "ORDER"    │
│ │  ├─ total_fee: 5000      │ │  ├─ payload:              │
│ │  └─ items: [...]         │ │  │  ├─ order_amount: null  │
│ └──────────────────────    │ │  │  └─ external_id: null  │
│                             │ │  └─ ...                   │
│                             │ └──────────────────────     │
│                             │                             │
│ 中间连线区（可视模式时）     │                             │
│ 从源节点拖线到目标节点       │                             │
└─────────────────────────────┴─────────────────────────────┘
```
#### 8.15.2 可视模式交互
* 左侧展示源 JSON 树，右侧展示目标 Schema 树（只读结构）。
* 从左侧叶子节点拖出一条线，连接到右侧对应叶子节点，自动生成 JSONPath 映射关系。
* 右侧节点旁显示已映射的源路径标签，可删除。
* 底部生成映射配置 JSON，可预览。
#### 8.15.3 脚本模式
* 全屏 Monaco 编辑器，编写 `transform(source)` 函数。
* 右上角“测试运行”按钮，输入示例源 JSON，执行沙箱（后端调用），右侧输出转换结果。
* 执行超时或错误时，在输出区显示详细错误信息。
***
### 8.16 系统设置
#### 8.16.1 角色权限管理
* 角色列表表格，支持新增角色。
* 编辑角色时，显示权限树（菜单和按钮级），勾选分配权限。
#### 8.16.2 操作日志
* 全局操作日志，支持按用户、操作类型、时间筛选。
* 表格列：时间、用户、操作、目标对象、详情（JSON 弹出）。
#### 8.16.3 SPI 调用日志
* 类似操作日志，增加渠道、Request ID、状态、耗时。
* 每行可展开查看请求体和响应体。
* 对于 DEAD 状态的事件，提供“重放”按钮。
#### 8.16.4 租户污染审计
* 专门页面，展示所有检测到的跨租户访问尝试记录。
* 表格列：时间、请求用户、请求租户、目标资源租户、拒绝原因。
***
### 8.17 前端路由与权限守卫
```typescript
// 路由配置示例
const routes = [
  { path: '/dashboard', element: <Dashboard />, meta: { auth: true } },
  { path: '/programs', element: <ProgramList />, meta: { auth: true, permission: 'program:read' } },
  { path: '/members', element: <MemberList /> },
  { path: '/members/:id', element: <MemberDetail /> },
  { path: '/points/accounts', element: <PointAccounts /> },
  { path: '/points/transactions', element: <PointTransactions /> },
  { path: '/rules', element: <RuleList /> },
  { path: '/rules/create', element: <RuleEditor /> },
  { path: '/rules/:id/test', element: <SandboxTest /> },
  { path: '/channels', element: <ChannelList /> },
  { path: '/channels/:id/mapping', element: <MappingEditor /> },
  { path: '/schema-designer', element: <SchemaDesigner /> },
  { path: '/system/roles', element: <RoleManage /> },
  { path: '/system/logs', element: <OperationLogs /> },
  { path: '/system/spi-logs', element: <SpiLogs /> },
];
```
* 路由守卫组件 `AuthGuard`：检查用户是否登录及按钮级权限，无权限跳转 403 页。
### 8.18 全局交互规范
#### 8.18.1 加载状态
所有数据请求必须覆盖以下状态，并在 UI 上体现：
| 状态       | UI 表现                           | 触发条件                  |
| -------- | ------------------------------- | --------------------- |
| **首次加载** | 页面级 `Spin` 遮罩 + 骨架屏             | 页面初次进入，无缓存数据          |
| **刷新加载** | 表格上方细进度条（`nprogress` 风格）        | 筛选条件变化、翻页、手动刷新        |
| **局部加载** | 按钮 `loading` 图标、输入框搜索防抖 `Spin`  | 提交表单、行内操作、搜索建议        |
| **空数据**  | 空状态插图 + 引导文案 + 操作按钮             | 列表无数据                 |
| **错误**   | `Result` 组件，显示错误信息和重试按钮         | 网络异常、接口 5xx           |
| **网络断开** | 全局顶部 Banner 提示“网络连接已断开，正在重连...” | `navigator.onLine` 监听 |
#### 8.18.2 表单交互规范
* **必填标识**：所有必填字段标签旁红色星号 `*`。
* **实时校验**：输入框 `onBlur` 触发校验，错误信息红色文字显示在输入框下方。
* **提交防抖**：提交按钮点击后立即 `loading`，禁用 2 秒内重复点击。
* **未保存离开**：表单 `dirty` 状态为 true 时，浏览器 `beforeunload` 事件拦截，弹出确认对话框。
* **批量操作**：表格上方出现“已选择 N 项”操作栏，提供批量删除、批量导出等按钮。
#### 8.18.3 移动端适配
* 左侧菜单在屏幕宽度 < 768px 时自动隐藏，通过汉堡按钮呼出 Drawer 抽屉。
* 表格列在移动端自动折叠，只显示关键列（如会员ID+等级），点击展开查看详情。
* 画布设计器（Schema 设计器）在移动端降级为只读模式，不支持拖拽编辑。
***
### 8.19 仪表盘 — 深化设计
#### 8.19.1 数据流
```text
useEffect on mount 和 programCode 变化
  → 并行请求：GET /api/dashboard/summary   → 指标卡片
               GET /api/dashboard/trend    → 趋势图数据
               GET /api/dashboard/alerts   → 告警列表
  → 轮询告警列表（每 30 秒静默刷新，不影响 loading 状态）
```
#### 8.19.2 指标卡片组件树
```text
DashboardPage
  ├─ StatCardRow
  │   ├─ StatCard (会员总数) → 点击跳转 /members
  │   ├─ StatCard (今日新增)
  │   ├─ StatCard (积分发放)
  │   └─ StatCard (积分核销)
  ├─ TrendChart (Line 图，Ant Design Charts)
  │   └─ 时间范围选择器 (日/周/月 Tabs)
  └─ AlertList
      └─ AlertItem (可点击，跳转到处理页面)
```
#### 8.19.3 告警类型与跳转映射
| 告警类型                     | 图标 | 跳转路径                       | 操作按钮 |
| ------------------------ | -- | -------------------------- | ---- |
| CASCADE_RECALC_BACKLOG | ⚠️ | `/system/logs?type=recalc` | 查看积压 |
| SPI_TIMEOUT_RATE_HIGH | 🚨 | `/system/spi-logs`         | 查看日志 |
| TENANT_POLLUTION        | 🔴 | `/system/audit`            | 查看详情 |
| RULE_COMPILE_FAILED    | ❌  | `/rules`                   | 编辑规则 |
***
### 8.20 会员列表页 — 深化设计
#### 8.20.1 动态属性搜索（核心交互）
* 后端提供 API：`GET /api/schemas/MEMBER/current` 获取当前 Program 的动态字段定义。
* 前端解析 `properties`，将已启用的动态字段自动生成搜索输入框，追加到搜索栏折叠区域。
* 字段类型与搜索控件映射：
  * `String` → `Input`（模糊搜索）
  * `Number` → `InputNumber` + 范围选择（最小值/最大值）
  * `Enum` → `Select` 多选
  * `Date` → `DatePicker.RangePicker`
* 用户输入后，搜索参数统一以 `ext_{fieldKey}` 格式传递给后端。
#### 8.20.2 批量导入流程
```text
点击“批量导入”
  → 弹出 Modal，步骤条：
      Step 1: 下载模板 (根据当前 Program 的 Schema 动态生成 CSV 模板)
      Step 2: 上传文件 (拖拽上传区域)
      Step 3: 预览数据 (表格展示前10行，校验错误红色标记)
      Step 4: 确认导入 (进度条实时更新)
  → 导入完成后，显示结果报告（成功 X 条，失败 Y 条，下载错误报告）
```
#### 8.20.3 列表交互细节
* 列头支持点击排序（注册时间、积分余额）。
* 列宽可拖拽调整，配置记忆在 localStorage。
* 行右键菜单：查看详情、合并会员、发送优惠券（扩展）。
* 会员ID 列可点击复制（`navigator.clipboard.writeText` + Tooltip 提示“已复制”）。
***
### 8.21 会员详情页 — 深化设计
#### 8.21.1 积分账户 Tab 卡片布局
```text
PointAccountsTab
  ├─ AccountCard (消费积分)
  │   ├─ 可用余额 (大号数字)
  │   ├─ 进度条: 信用已用 / 信用额度
  │   ├─ 小字: 累计获得 / 累计消耗 / 累计过期
  │   └─ 按钮: [查看流水] [调整积分]
  ├─ AccountCard (等级成长值)
  └─ AccountCard (其他自定义类型...)
```
#### 8.21.2 调整积分弹窗
* 点击“调整积分”按钮 → 弹出 Modal。
* 表单字段：调整类型（增加/扣减）、积分类型、金额、原因（必填）、附件（可选）。
* 提交前二次确认：“确定要为该会员 [增加/扣减] 500 消费积分吗？此操作不可撤销。”
* 成功后刷新账户卡片数据。
#### 8.21.3 交易流水 Tab 溯源详情
* 点击某条核销流水 → 弹出 Drawer。
* Drawer 内容：
  * 上半部分：核销流水详情（时间、金额、状态）。
  * 下半部分：**核销分配明细表**（关联的原始发分批次ID、分配金额、该批次过期时间）。
  * 支持点击批次ID跳转到该发分批次的详情。
#### 8.21.4 渠道关联 Tab
* 表格列：渠道图标、渠道名称、OpenID/UnionID、绑定时间、状态、操作（解绑）。
* 手动绑定按钮 → 弹出 Modal，选择渠道类型，输入 OpenID，调用后端绑定接口。
* 解绑操作需二次确认。
#### 8.21.5 属性扩展 Tab — 深度集成 DynamicFormRenderer
```text
MemberExtTab
  ├─ VersionAlert (当 dataVersion < currentVersion 时显示)
  │   └─ "当前数据为旧版 Schema (v1.0.0)，部分字段可能已变更。 [升级到最新版本]"
  ├─ DynamicFormRenderer
  │   ├─ 传入当前 Program 的 Member Schema
  │   ├─ 传入会员 extAttributes 数据
  │   └─ 处理废弃字段：
  │       ├─ 编辑模式下，废弃字段隐藏
  │       └─ 详情模式下，废弃字段折叠在「历史遗留字段」区域，灰色文字 + Deprecated 标签
  └─ 操作按钮: [保存] [重置]
```
***
### 8.22 规则引擎 — 深化设计
#### 8.22.1 规则编辑器的 AI 生成交互
```text
用户输入自然语言："周末买苹果手机超过5000元送100积分"
  → 点击 [生成规则] 按钮
  → 按钮 loading，发送 POST /api/ai/generate-rule (携带自然语言 + Program 上下文)
  → 收到响应 JSON:
      {
        "analysis": "分析文本...",
        "drl_code": "rule '...' { ... }",
        "salience_recommendation": { "salience": 150 },
        "mock_test_cases": [ ... ]
      }
  → 弹出预览 Modal:
      ├─ 分析结果 (analysis 文本)
      ├─ 生成的 DRL 代码 (Monaco 只读预览)
      ├─ 测试用例列表
      └─ 按钮: [采纳并编辑] [重新生成] [取消]
  → 点击"采纳并编辑"，DRL 代码填入编辑器，测试用例存入沙箱测试数据集
```
#### 8.22.2 Monaco 编辑器增强
* 注册 Drools 语言支持（自定义 Monarch tokenizer）：
  * 关键字高亮：`rule`, `when`, `then`, `end`, `and`, `or`, `not`, `exists`
  * 注释高亮：`//` 单行注释
  * 自动补全：输入 `$event.` 时弹出 EventFact 可用方法列表（`getPayloadNumber`, `getPayloadString`），输入 `$member.` 时弹出 MemberFact 方法列表。
* 错误标记：编译失败时，后端返回错误行号，编辑器对应行红色波浪线 + 悬浮错误信息。
#### 8.22.3 发布流程（含强制放行）
```text
点击 [发布] 按钮
  → 调用 POST /api/rules/{id}/validate (沙箱回归测试)
  → 收到 RegressionReport:
      ├─ 绿色: 直接调用 POST /api/rules/{id}/publish 发布
      ├─ 黄色: 弹出警告 Modal，显示差异详情，提供 [仍要发布] [返回修改] 按钮
      └─ 红色: 弹出强制放行 Modal:
            ├─ 差异详情（严重警告红色）
            ├─ 必填输入框: "强制放行理由"
            └─ 按钮: [强制发布] → 调用 POST .../publish?forceOverride=true&reason=...
```
***
### 8.23 映射编辑器 — 深化设计
#### 8.23.1 可视模式连线交互细节
* 源 JSON 树（左侧）可折叠/展开，叶子节点右侧显示小圆点锚点。
* 目标 Schema 树（右侧）叶子节点左侧显示小圆点锚点。
* 连线操作：
  1. 鼠标移入源节点锚点，锚点放大变绿。
  2. 按下左键拖拽，出现贝塞尔曲线临时连线。
  3. 拖到目标节点锚点上释放，连线固定，颜色变蓝。
  4. 自动在底部“映射规则列表”中添加一行。
* 映射规则列表：表格显示源路径 → 目标路径，支持手动编辑、删除。
* 源数据区域支持粘贴示例 JSON，自动解析为树结构。
#### 8.23.2 脚本模式交互细节
* 编辑器左侧：代码编辑区（Monaco）。
* 编辑器右侧：上下分屏。
  * 上半屏：测试输入区（TextArea，可粘贴示例源 JSON）。
  * 下半屏：测试输出区（JSON 格式化展示，成功绿色边框，失败红色边框 + 错误信息）。
* 工具栏按钮：
  * [格式化代码]：JS 美化。
  * [测试运行]：调用 `POST /api/spi/transform/test`（沙箱执行，50ms 超时）。
  * [保存脚本]：保存到 `channel_adapter_config.transform_script`。
***
### 8.24 系统设置 — 深化设计
#### 8.24.1 角色权限管理
* 角色列表表格，点击展开嵌套表格显示该角色下的用户列表。
* 新建/编辑角色 Drawer：
  * 角色名称、描述。
  * 权限树（`Tree` 组件，节点可选择、禁用）。
  * 权限节点数据从后端 `GET /api/permissions/tree` 获取，结构：
    ```json
    { "key": "member", "title": "会员管理", "children": [
        { "key": "member:read", "title": "查看" },
        { "key": "member:write", "title": "编辑" },
        { "key": "member:delete", "title": "删除" }
    ]}
    ```
#### 8.24.2 SPI 调用日志
* 筛选条件：渠道、时间范围、状态（成功/超时/失败/DEAD）。
* 表格列：时间、渠道、Request ID、操作、状态标签、耗时(ms)、操作按钮。
* 展开行（`Table.expandable`）：请求头 JSON、请求体 JSON、响应体 JSON（语法高亮）。
* DEAD 行的操作按钮：[重放] 点击调用 `POST /api/admin/events/{id}/replay`，成功后状态刷新。
#### 8.24.3 租户污染审计
* 表格列：时间、操作用户、请求租户、目标资源租户、API 路径、IP 地址。
* 筛选：时间范围、请求租户。
* 每行旁有红色警示图标，悬浮显示：“该用户尝试跨租户访问资源”。
* 支持导出审计报告（CSV）。
***
### 8.25 全局状态管理设计
#### 8.25.1 状态结构（Zustand Store）
```typescript
interface AppStore {
  // 当前租户上下文
  currentProgramCode: string;
  programs: Program[];
  setCurrentProgram: (code: string) => void;
  // 用户信息
  user: UserInfo | null;
  permissions: string[];
  setUser: (user: UserInfo, permissions: string[]) => void;
  hasPermission: (perm: string) => boolean;
  // 全局 UI
  sidebarCollapsed: boolean;
  toggleSidebar: () => void;
  globalLoading: boolean;
}
```
#### 8.25.2 数据刷新策略
* 切换 Program 时，清空所有缓存列表数据，触发全局 `key` 变化强制重渲染。
* 会员详情等频繁访问的数据使用 `swr` 或 `react-query` 缓存，staleTime 30 秒。
* 列表页筛选条件同步到 URL query params，支持浏览器前进/后退和书签。
***
### 8.26 补充：画布设计器的扩展功能
#### 8.26.1 子对象嵌套编辑
* 当字段类型为 `Object` 或 `Array` 时，右侧面板显示“编辑子对象”按钮。
* 点击后，画布中央弹出一个**嵌套画布 Modal**，背景变暗，主画布不可操作。
* 嵌套画布自动包含一个默认节点（子对象实体），可添加字段、设置属性。
* Modal 顶部显示面包屑：`会员(Member) > 宠物档案(PetInfo) > 疫苗记录(VaccineRecord)`。
* 完成编辑后，子 Schema 序列化嵌入父字段的 `subSchema` 属性。
#### 8.26.2 导出与导入
* 导出：将画布当前状态导出为 JSON 文件下载，包含所有节点、连线、字段定义。
* 导入：支持上传 JSON 文件或粘贴 JSON 文本，解析后渲染到画布（清空现有内容前二次确认）。
* 版本历史：后端保存每次发布的 Schema 版本快照，画布设计器提供“版本历史”抽屉，展示版本列表，支持预览和回滚。
#### 8.26.3 画布设计器的键盘无障碍
* `Tab` 键在节点间切换焦点。
* `Enter` 键打开选中节点的编辑模式。
* `Escape` 键退出当前编辑/关闭右侧面板。
* 屏幕阅读器支持：节点和字段使用 `aria-label` 描述。
***
- **第十二章**：高并发与性能设计（指导集成测试场景）。
- **第十三章**：部署架构与灾备（指导容器化和监控配置）。
## 第十二章：高并发与性能设计
* **有序分区消费**：通过 `LocalEventBus` 和 `KafkaEventBus`，均利用 `memberId` 作为分区键，保证同一会员的事件在单线程内串行处理，消除并发写冲突。
* **乐观锁与重试**：member_account 表采用 version 乐观锁，仅用于信用额度扣减（credit_used）等并发控制。积分余额本身通过消息队列按 memberId 分区串行化保障一致性，无需锁竞争。
* **碎片整理 (Compaction)**：当会员的 `ACTIVE` 流水批次超过 50 条时，低峰期触发后台合并任务，减少 FIFO 兑换时 `SELECT FOR UPDATE` 的行锁范围。
* **规则引擎无状态化**：Drools 全部使用 `StatelessKieSession`，KieBase 缓存热替换，实现毫秒级规则推理。
***
## 第十三章：部署架构与灾备
* **容器化部署**：整体基于 Kubernetes 进行微服务化部署，Drools 相关服务适当调大 JVM 堆内存。
* **数据库高可用**：PostgreSQL 采用 Patroni 自动 Failover，WAL 日志归档，RPO < 5 分钟。
* **Redis 哨兵模式**：保障分布式锁和缓存的高可用。
* **监控告警**：全链路接入 Prometheus + Grafana，对 SPI 超时、租户污染、级联重算积压等关键指标配置告警。
***
## 任务要求
请基于上述章节，生成以下四大块产出。每一项都必须严格对照设计文档中的布局、交互、数据流和组件结构。

### 1. 前端全页面开发

#### 1.1 全局框架与路由
- 实现 `AppShell` 组件：顶部导航栏（Logo、Program 切换器、通知、用户下拉）、左侧可折叠菜单（Ant Design `Layout` + `Menu`）、底部状态栏（可选）、面包屑导航。
- 配置 React Router v6，所有页面路由及权限守卫 `AuthGuard`（检查登录和按钮级权限），无权限跳转 403 页面。
- 实现全局状态管理（Zustand store）：`currentProgramCode`、`user`、`permissions`、`sidebarCollapsed`，并实现切换 Program 时清空缓存数据。
- 封装 axios 实例，自动注入 `X-Program-Code`、`X-Trace-Id`、`Authorization` Header，统一处理 `ApiResponse`，并拦截 401 跳转登录。

#### 1.2 仪表盘 (Dashboard)
- 指标卡片行：四个 `StatCard`（会员总数、今日新增、积分发放、积分核销），支持点击跳转到对应列表页。
- 积分发放/核销趋势图：使用 `@ant-design/charts` 的 `Line` 图表，支持日/周/月 Tab 切换。
- 待处理告警列表：表格展示告警类型、详情、时间、状态，不同类型告警提供不同的跳转链接（如 CASCADE_RECALC_BACKLOG 跳转 `/system/logs`）。
- 数据获取：`useEffect` 并行请求三个 API，告警列表每 30 秒轮询。

#### 1.3 Program 管理
- Program 列表页：表格展示 Program Code、名称、状态、会员数、创建时间、操作（编辑、复制、删除）；支持搜索和新建。
- 新建/编辑 Program 页面（全屏 Drawer 或独立页面）：
  - 基础信息表单。
  - 积分类型字典：动态表格，每行配置：类型编码、名称、可兑换、算等级、可转赠、允许负数。
  - 等级阶梯编辑器：垂直可拖拽列表，每行：等级名称、所需成长值、权益描述（富文本），支持添加/删除等级。
  - 逆向策略配置：透支限额、退款过期宽限天数等。

#### 1.4 会员中心
- **会员列表页**：
  - 搜索栏：静态字段搜索 + **动态属性搜索**（根据当前 Program 的 Schema 动态生成输入框，字段类型决定控件）。
  - 表格列：会员ID、手机号（脱敏）、等级、状态、注册渠道、注册时间；操作列：查看详情、合并会员。
  - 批量导入：多步骤 Modal（下载模板、上传文件、预览数据、确认导入、进度条、结果报告）。
  - 行右键菜单：查看详情、合并会员。
  - 会员ID 点击复制到剪贴板。
- **会员详情页**：
  - 顶部信息卡片：会员ID、等级徽章、状态标签、手机号、注册渠道；操作按钮：[停用] [合并]。
  - Tab 页签：
    - 基本信息：只读静态字段 + 未废弃动态字段。
    - 积分账户：按积分类型分组卡片（可用余额、信用使用进度条、累计统计、按钮[查看流水] [调整积分]）。
      - 调整积分弹窗：增减类型、金额、原因（必填）、二次确认。
    - 交易流水：带筛选表格（类型、时间），点击核销记录弹出 Drawer 展示 `redemption_allocation` 明细。
    - 渠道关联：表格展示渠道、OpenID、绑定时间，支持手动绑定/解绑。
    - 属性扩展：嵌入 `DynamicFormRenderer`（第七阶段组件），处理版本不一致 Alert、废弃字段只读展示、保存双写逻辑。

#### 1.5 积分管理
- **账户总览页**：按会员ID搜索，展示该会员所有账户类型卡片（同会员详情积分 Tab 相似）。
- **积分流水页**：全局流水列表（跨会员），支持筛选会员ID、积分类型、交易类型、时间范围；表格列含剩余额度、批次状态；支持异步导出 Excel。

#### 1.6 规则引擎
- **规则列表页**：左侧规则树（按 Program → activation-group 分组），右侧表格（状态、优先级等），操作：编辑、停用/启用、沙箱测试。
- **规则编辑器页**：
  - 布局：左栏基本信息表单（名称、分组、优先级），中栏 AI 辅助区域 + Monaco DRL 编辑器，右栏属性面板（选中节点时）。
  - AI 生成交互：输入自然语言 → 调用后端 LLM 接口 → 弹出预览 Modal（分析、DRL 代码、测试用例）→ [采纳并编辑] 按钮。
  - Monaco 编辑器：Drools 语法高亮、自动补全（Fact 字段和方法）、错误行标记。
  - 发布流程：点击 [发布] → 后端沙箱测试 → 根据绿/黄/红报告弹出不同 Modal（直接发布/警告/强制放行理由）。
- **沙箱回归测试页**：左侧选择数据集，右侧双列对比视图（基线 vs 候选），差异行高亮，底部汇总报告卡片。

#### 1.7 渠道配置与映射编辑器
- **渠道列表页**：表格展示渠道标识、名称、映射模式、状态；新建渠道弹窗。
- **渠道编辑页**：基本信息表单 + Tab（映射配置、验签配置）。
- **映射编辑器**：
  - 可视模式：左源 JSON 树（可粘贴示例解析），右目标 Schema 树，中间连线区（拖拽锚点生成 JSONPath 映射），底部映射规则列表。
  - 脚本模式：Monaco 编辑器 + 右侧测试区（输入/输出），工具栏按钮（格式化、测试运行、保存）。

#### 1.8 动态实体画布设计器 (Schema Designer) — 第八章 8.6 节详细设计
- **画布基础**：使用 `React Flow` 实现无限画布，支持拖拽、缩放、平移、框选、网格背景。
- **悬浮工具条**：垂直排列图标按钮（实体对象、业务对象、连线模式、删除选中、适应画布），支持从工具条拖拽节点到画布。
- **节点卡片**：显示实体名称（可双击编辑）、字段列表（主键图标、字段名、类型、枚举标记）、锚点（四个边，连线模式时显示）。
  - 点击字段行 → 右侧面板滑出，配置字段属性（字段名、显示名、类型、主键、必填、默认值、枚举项、子对象入口、引用目标、x-component、验证规则、联动规则可视化构建器、废弃标记）。
  - 子对象嵌套编辑：弹出嵌套画布 Modal，带面包屑。
- **连线**：正交路由，拖拽锚点连接，选中连线显示关系配置（类型 1:1/1:N/N:M、外键字段、级联策略、标签文字）。
- **状态持久化**：画布状态（节点、边、视口）与后端同步（保存 Draft / 发布生成新 Schema 版本）。
- **版本历史**：抽屉展示版本列表，支持预览和回滚。
- **导出/导入**：支持 JSON 下载和上传。
- **快捷键与无障碍**：实现文档中列出的所有快捷键和 Tab 键导航。

#### 1.9 系统设置
- **角色权限管理**：角色列表，编辑角色时显示权限树（Tree 组件），保存权限分配。
- **操作日志**：全局操作日志列表，支持筛选用户、操作类型、时间，可展开查看详情 JSON。
- **SPI 调用日志**：按渠道、时间、状态筛选，展开行显示请求/响应 JSON（语法高亮），DEAD 事件提供 [重放] 按钮。
- **租户污染审计**：跨租户访问尝试记录列表，红色警示图标，支持导出。

### 2. 前后端联调与端到端测试

#### 2.1 联调要求
- 确保所有页面调用的 API 路径、请求参数、响应格式与后端一致（参照前期阶段生成的 API 定义）。
- 处理所有加载态（Spin、骨架屏）、空状态（Empty + 引导）、错误态（Result + 重试）。
- 表单提交防抖、未保存离开拦截、网络断开全局提示。

#### 2.2 集成测试
- 使用 Cypress 或 Playwright 编写端到端测试脚本，覆盖：
  - 用户登录 → 切换 Program。
  - 会员入会流程（含并发模拟）。
  - 积分发放 → 查看账户余额 → 积分核销 → 查看流水和分摊明细。
  - 规则创建 → 沙箱测试 → 发布。
  - 退款触发 → 级联重算任务生成 → 补偿完成验证。
  - 渠道映射配置与 SPI 回调模拟。

### 3. 部署与监控配置

#### 3.1 容器化
- 编写后端 `Dockerfile`（多阶段构建，优化镜像大小）。
- 编写前端 `Dockerfile`（使用 Nginx 部署静态资源，配置反向代理）。
- 编写 `docker-compose.yml`，包含：PostgreSQL 15、Redis、后端应用、前端 Nginx。

#### 3.2 Kubernetes 部署
- 生成 Deployment、Service、ConfigMap（区分环境）、Ingress 资源清单。
- 配置健康检查（liveness/readiness probe）、资源限制（CPU/Memory）。
- 配置 Redis 哨兵模式连接信息。

#### 3.3 监控
- 在 Spring Boot 中暴露 Prometheus 指标（如 SPI 超时率、级联重算积压量、租户污染次数）。
- 编写 Grafana 面板 JSON：仪表盘展示 QPS、P99 延迟、错误率、JVM 内存、业务指标。
- 配置告警规则：SPI 超时率 > 10%、级联重算积压 > 50、租户污染 > 0。

## 输出要求
- 生成完整的前端项目代码（React 18 + TypeScript + Ant Design + Formily 2.x），目录结构清晰。
- 前端代码需覆盖所有页面、组件、路由、状态管理、工具函数（API 封装、权限判断、动态 Schema 转换等）。
- 后端仅需补充监控指标暴露和健康检查端点（如 `HealthController`）。
- 测试脚本放置在 `e2e/` 目录下，提供运行说明。
- 部署文件放置在 `deploy/` 目录下（Dockerfile、docker-compose.yml、k8s/ 子目录）。
- 所有代码注释清晰，说明对应设计文档的章节号。

## 上下文衔接约定
- 前端调用后端 API 时，统一使用封装好的 `api.ts`，请求前缀 `/api`。
- 第七阶段已实现的 `DynamicFormRenderer` 组件、`SchemaService` 相关 API 路径可在现有基础上直接引用。
- 后端所有 API 已通过前七阶段实现，本阶段无需重复生成后端业务代码，只需确保前端路径与后端匹配。
- 多租户上下文通过 `X-Program-Code` Header 传递，切换 Program 时需刷新所有数据。

请开始生成代码。
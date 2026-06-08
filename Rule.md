# 规则引擎详细设计
> 本文档作为主设计文档 v7.3 第六章的扩展，详细定义规则引擎的数据模型、大模型辅助规则生成与测试流程、以及规则管理界面设计。所有设计均符合主文档的架构与技术规范。
***
## 一、规则引擎数据模型与实体对象
### 1.1 事实（Fact）包装器
规则引擎基于 **无状态会话（StatelessKieSession）** 运行，输入为标准化事件和会员信息，输出为待执行的动作列表。事实包装器将数据库实体转换为 Drools 可识别的 POJO，隐藏 JSONB 访问细节，并提供类型安全的 getter 方法。
#### 1.1.1 EventFact（事件事实）
```java
package com.loyalty.platform.domain.rules.model;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
public class EventFact {
    private String eventId;
    private String memberId;
    private String eventType;      // ORDER, BEHAVIOR, REDEMPTION...
    private String channel;
    private LocalDateTime eventTime;
    private String idempotentKey;
    private Map<String, Object> payload;
    private String ruleSnapshotId;  // 规则快照ID（用于级联重算）
    // 构造函数、getter/setter 略
    public String getPayloadString(String key) {
        Object val = payload.get(key);
        return val != null ? String.valueOf(val) : null;
    }
    public BigDecimal getPayloadNumber(String key) {
        Object val = payload.get(key);
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof BigDecimal) return (BigDecimal) val;
        if (val instanceof Number) return BigDecimal.valueOf(((Number) val).doubleValue());
        return BigDecimal.ZERO;
    }
    public LocalDateTime getPayloadDateTime(String key) {
        Object val = payload.get(key);
        if (val instanceof LocalDateTime) return (LocalDateTime) val;
        // 支持字符串解析
        return null;
    }
    // 行为专用快捷方法
    public String getBehaviorCode() {
        return getPayloadString("behavior_code");
    }
    // 订单专用快捷方法
    public String getOrderId() {
        return getPayloadString("order_id");
    }
    public BigDecimal getTotalAmount() {
        return getPayloadNumber("total_amount");
    }
}
```
#### 1.1.2 MemberFact（会员事实）
```java
package com.loyalty.platform.domain.rules.model;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
public class MemberFact {
    private String memberId;
    private String programCode;
    private String tierCode;
    private String status;
    private LocalDate birthday;
    private Map<String, Object> extAttributes;   // 动态扩展属性
    // 构造函数、getter/setter 略
    public String getExtString(String key) {
        Object val = extAttributes.get(key);
        return val != null ? String.valueOf(val) : null;
    }
    public BigDecimal getExtNumber(String key) {
        Object val = extAttributes.get(key);
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof BigDecimal) return (BigDecimal) val;
        if (val instanceof Number) return BigDecimal.valueOf(((Number) val).doubleValue());
        return BigDecimal.ZERO;
    }
}
```
#### 1.1.3 其他可选事实（按需扩展）
* **OrderItemFact**：若规则需按商品明细分别奖励，可将 `EventFact.payload.items` 数组拆分为多个 `OrderItemFact` 并插入会话。
* **TierFact**：当前等级配置（升级阈值、保级条件等），由规则引擎预加载。
### 1.2 动作（Action）收集器
DRL 规则中不能直接调用服务，需通过 `ActionCollector` 记录动作，规则执行完毕后由 `RewardExecutor` 统一执行。

```java
package com.loyalty.platform.domain.rules.engine;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
public class ActionCollector {
    private final List<Action> actions = new ArrayList<>();
    public static ActionCollector get() {
        return new ActionCollector();
    }
    public AwardPointsAction awardPoints(String eventId, BigDecimal points, String ruleId) {
        AwardPointsAction action = new AwardPointsAction(eventId, points, ruleId);
        actions.add(action);
        return action;
    }
    public UpgradeTierAction upgradeTier(String memberId, String newTier, String reason) {
        UpgradeTierAction action = new UpgradeTierAction(memberId, newTier, reason);
        actions.add(action);
        return action;
    }
    public void execute(RewardExecutor executor, String memberId) {
        executor.executeRewards(memberId, actions);
    }
}
// 动作基类与具体实现（示例）
public abstract class Action {}
public class AwardPointsAction extends Action {
    private String eventId;
    private BigDecimal points;
    private String ruleId;
    // getters ...
}
public class UpgradeTierAction extends Action { /* ... */ }
```
在 DRL 中使用：

```drools
import com.loyalty.platform.domain.rules.model.*;
import com.loyalty.platform.domain.rules.engine.ActionCollector;
rule "OrderAmountReward"
when
    $event: EventFact(eventType == "ORDER", totalAmount > 100)
    $member: MemberFact(memberId == $event.memberId, status == "ENROLLED")
then
    ActionCollector.get()
        .awardPoints($event.getEventId(), $event.getTotalAmount().multiply(0.1), "ORDER_AMOUNT_REWARD")
        .execute(drools);
end
```
### 1.3 规则版本快照（RuleSnapshot）
为支持级联重算，每条规则发布时生成快照，存储 DRL 内容及元数据。

```java
@Entity
@Table(name = "rule_snapshot")
public class RuleSnapshot {
    @Id
    private String id;               // 快照ID，如 "RULE_ORDER_001_v3"
    private String programCode;
    private String ruleId;
    private String drlContent;
    private Integer salience;
    private String activationGroup;
    private LocalDateTime createdAt;
}
```
### 1.4 规则元数据表（Rule）
用于管理规则的草稿、发布、版本等。

```sql
CREATE TABLE rule (
    id BIGSERIAL PRIMARY KEY,
    program_code VARCHAR(32) NOT NULL,
    rule_id VARCHAR(64) NOT NULL,
    rule_name VARCHAR(200),
    drl_content TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',  -- DRAFT / ACTIVE / INACTIVE
    salience INT DEFAULT 0,
    activation_group VARCHAR(50),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (program_code, rule_id)
);
```
***
## 二、大模型辅助规则生成与测试
### 2.1 规则生成流程
运营人员在规则编辑器界面输入**自然语言描述**，系统调用大模型（如 GPT-4、Claude）生成标准化的 Drools 规则。
#### 2.1.1 上下文构建
后端在调用 LLM 前，收集当前 Program 的以下信息，拼接成系统提示：
* 可用的积分类型（`point_type_definition` 表）
* 可用的等级（`tier` 配置）
* 会员扩展属性 Schema（`program_schema` 中 `entity_type='MEMBER'` 的字段）
* 交易事件 payload 的 Schema（`entity_type='TRANSACTION_EVENT'`）
* 已有规则示例（可选，用于 few-shot）
#### 2.1.2 提示词模板（System Prompt）

```text
你是一个忠诚度管理系统的规则编写专家。系统使用 Drools 8 规则引擎，规则必须以 DRL 格式输出。
## 可用积分类型
{pointTypes}
## 会员扩展属性（可通过 MemberFact.getExtNumber/getExtString 访问）
{memberExtSchema}
## 事件 payload 字段（可通过 EventFact.getPayloadNumber/getPayloadString 访问）
{eventPayloadSchema}
## 输出要求
1. 必须包含 rule "规则名称"
2. when 部分：使用 EventFact 和 MemberFact 的条件组合
3. then 部分：调用 ActionCollector.get().awardPoints(...).execute(drools);
4. 输出仅包含 DRL 代码，不要额外解释
5. 示例格式：
   rule "示例"
   when
       $event: EventFact(eventType == "ORDER", getPayloadNumber("total_amount") > 100)
       $member: MemberFact(memberId == $event.memberId)
   then
       ActionCollector.get().awardPoints($event.getEventId(), 10, "RULE_EXAMPLE").execute(drools);
   end
```
#### 2.1.3 用户输入示例
> “当用户在天猫下单且实付金额满 200 元时，奖励 20 消费积分。”
后端将用户输入与系统提示合并，发送给 LLM，获得 DRL 代码。
#### 2.1.4 生成结果解析与校验
* 后端提取 LLM 返回的 DRL 代码，使用 `KieBuilder` 进行编译校验。
* 若编译失败，将错误信息返回前端，允许用户修正或重新生成。
* 编译通过后，规则以 `DRAFT` 状态保存，进入沙箱测试。
### 2.2 沙箱测试环境
#### 2.2.1 影子模式（Shadow Sandbox）
设计文档 §6.3 要求：基线引擎（仅线上老规则） vs 候选引擎（老规则 + 新草稿）对比输出差异。
**实现步骤**：
1. **数据准备**：
   * 从生产环境选取最近 7 天内的 500 条真实事件流水（脱敏后），作为测试数据集。
   * 同时提供对应的会员快照（MemberFact 所需字段）。
2. **双引擎执行**：
   * 基线引擎：加载当前 `ACTIVE` 规则，对测试数据逐条推理，记录每条事件产生的动作（积分发放、等级变更等）。
   * 候选引擎：加载 `ACTIVE` 规则 + 新草稿规则，同样执行。
3. **差异对比**：
   * 对比两个引擎的输出结果，按事件 ID 聚合。
   * 差异类型：
     * **新增奖励**：候选引擎有而基线无 → 绿色（通过）
     * **缺失奖励**：基线有而候选无 → 红色（冲突，可能规则遮蔽）
     * **奖励值变化**：两者都有但数值不同 → 黄色（需人工确认）
4. **报告生成**：
   * 将差异分级（绿/黄/红）展示在前端，并提供详细的事件级别对比。
#### 2.2.2 模拟生产环境数据回放
为验证规则在高并发下的正确性，可构建独立的测试环境（与生产数据库隔离），使用 `test` Profile 启动服务，将生产事件日志（从 `event_inbox` 或 `channel_spi_log` 抽取）批量回放，观察规则引擎输出与历史实际积分发放是否一致。

```java
@Service
public class RuleRegressionService {
    // 从生产环境读取历史事件（脱敏）
    public RegressionReport runRegression(String programCode, String ruleDraftId) {
        List<TransactionEvent> historicalEvents = loadHistoricalEvents(programCode, 500);
        KieBase baseline = kieBaseCacheManager.getKieBase(programCode);  // 仅ACTIVE
        KieBase candidate = buildCandidateKieBase(programCode, ruleDraftId);
        
        List<EventResult> baselineResults = evaluate(baseline, historicalEvents);
        List<EventResult> candidateResults = evaluate(candidate, historicalEvents);
        
        return compare(baselineResults, candidateResults);
    }
}
```
#### 2.2.3 弱阻断与强制放行（设计 §6.4）
* **绿色**：无差异或仅有新增奖励 → 可正常提交发布。
* **黄色**：有奖励值变化或部分遮蔽 → 允许提交，但需二次确认。
* **红色**：有奖励缺失或严重冲突 → 需输入 `forceOverride=true` 并填写理由，触发双人会签流程。
### 2.3 规则发布流程
1. 运营在规则编辑器点击「发布」。
2. 后端执行沙箱回归测试，生成差异报告。
3. 前端展示报告，若为黄色或红色，要求确认或输入强制放行理由。
4. 确认后，后端将规则状态从 `DRAFT` 改为 `ACTIVE`，并生成 `rule_snapshot`。
5. 调用 `kieBaseCacheManager.refreshKieBase(programCode)` 热加载新规则。
***
## 三、规则引擎界面设计
### 3.1 整体布局（符合主文档 §8 风格）
* 纯白底色，黑线条边框
* 左侧规则树（按 Program 分组），右侧规则列表/编辑器
* 顶部菜单：规则引擎 → 规则列表 / 规则编辑器 / AI 辅助生成
### 3.2 规则列表页
路由：`/rules`

```text
┌─────────────────────────────────────────────────────────────────────┐
│ 规则引擎                     [+ 新建规则]   [导入]   [导出]          │
├──────────────┬──────────────────────────────────────────────────────┤
│ 规则分组      │ 规则列表                                             │
│ ┌──────────┐ │ ┌─────────────────────────────────────────────────┐ │
│ │ 全部规则  │ │ │ 规则名称       状态   最后修改  操作               │ │
│ │ 订单类    │ │ │ 订单满100送10  启用   2026-06-01  [编辑][测试][更多]│ │
│ │ 行为类    │ │ │ 签到送5积分    草稿   2026-06-02  [编辑][测试][发布]│ │
│ │ 等级类    │ │ │ ...                                            │ │
│ └──────────┘ │ └─────────────────────────────────────────────────┘ │
│ [+ 新建分组]  │                                                      │
└──────────────┴──────────────────────────────────────────────────────┘
```
* 左侧树支持拖拽分组。
* 列表支持按名称、状态搜索，每行操作按钮：
  * **编辑**：进入规则编辑器（如果是 `ACTIVE` 状态，复制为草稿）。
  * **测试**：打开沙箱测试弹窗，选择测试数据集（或实时输入事件 JSON），执行并展示结果。
  * **发布**：仅草稿状态显示，触发回归测试流程。
  * **停用/启用**：`ACTIVE` 规则可停用，停用后从 KieBase 移除。
### 3.3 规则编辑器
路由：`/rules/new` 或 `/rules/:ruleId/edit`
#### 3.3.1 布局

```text
┌─────────────────────────────────────────────────────────────────────┐
│ [← 返回]  编辑规则：订单满100送10                        [保存草稿] [测试] [发布] │
├─────────────────────────────────────────────────────────────────────┤
│  ┌─ 基本信息 ────────────────────────────────────────────────────┐ │
│  │ 规则名称: [ 订单满100送10              ]  规则ID: auto_generated │ │
│  │ 优先级:   [ 0 ▼ ]   生效组: [ 默认     ]                       │ │
│  └─────────────────────────────────────────────────────────────────┘ │
│                                                                     │
│  ┌─ 规则脚本 (DRL) ───────────────────────────────────────────────┐ │
│  │  ┌─────────────────────────────────────────────────────────┐   │ │
│  │  │ 1  import com.loyalty.platform.domain.rules.model.*;    │   │ │
│  │  │ 2  import com.loyalty.platform.domain.rules.engine.*;   │   │ │
│  │  │ 3                                                        │   │ │
│  │  │ 4  rule "ORDER_AMOUNT_REWARD"                           │   │ │
│  │  │ 5  when                                                  │   │ │
│  │  │ 6      $event: EventFact(eventType == "ORDER",          │   │ │
│  │  │ 7                     getPayloadNumber("total_amount") > 100) │ │
│  │  │ 8      $member: MemberFact(memberId == $event.memberId) │   │ │
│  │  │ 9  then                                                  │   │ │
│  │  │ 10     ActionCollector.get()                            │   │ │
│  │  │ 11         .awardPoints($event.getEventId(), 10, "ORDER_AMOUNT_REWARD") │ │
│  │  │ 12         .execute(drools);                            │   │ │
│  │  │ 13 end                                                   │   │ │
│  │  └─────────────────────────────────────────────────────────┘   │ │
│  │  [ AI 辅助生成 ] [ 格式化 ] [ 校验语法 ]                        │ │
│  └─────────────────────────────────────────────────────────────────┘ │
│                                                                     │
│  ┌─ 测试面板 ─────────────────────────────────────────────────────┐ │
│  │ [输入事件JSON]  [ 运行测试 ]  [ 回归测试 ]                     │ │
│  │ 结果: 匹配规则, 奖励积分: 10                                    │ │
│  └─────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
```
#### 3.3.2 组件说明
* **代码编辑器**：使用 Monaco Editor（与映射配置器相同），支持 DRL 语法高亮、自动补全（可定义 Drools 关键词）。
* **AI 辅助生成**：点击后弹出模态框，让用户输入自然语言描述，调用后端 API `/api/rules/ai-generate`，返回 DRL 代码并插入编辑器。
* **校验语法**：调用后端 `/api/rules/validate`，返回编译错误信息。
* **测试面板**：
  * **输入事件JSON**：手动构造 EventFact 的 payload（或选择历史事件样本）。
  * **运行测试**：后端模拟执行当前草稿规则，返回匹配结果。
  * **回归测试**：调用沙箱回归测试 API，展示差异报告。
* **发布**：触发回归测试流程，若通过（绿/黄）则进入确认发布；若红色需强制放行弹窗。
### 3.4 AI 辅助生成模态框

```text
┌─────────────────────────────────────────────────────────────────────┐
│  🤖 AI 规则助手                                          [×]       │
├─────────────────────────────────────────────────────────────────────┤
│ 描述您的规则需求:                                                   │
│ ┌─────────────────────────────────────────────────────────────────┐ │
│ │ 当用户在天猫下单且实付金额满200元时，奖励20消费积分。            │ │
│ │                                                                 │ │
│ └─────────────────────────────────────────────────────────────────┘ │
│                                                                     │
│ 可选: 关联的积分类型 [ 消费积分 ▼ ]   奖励值 [ 20 ]                 │
│                                                                     │
│ [ 生成规则 ]                                          [ 取消 ]      │
└─────────────────────────────────────────────────────────────────────┘
```
* 用户填写自然语言，也可通过表单辅助（选择积分类型、金额条件等）。
* 后端将结构化信息 + 上下文拼装成 Prompt，返回 DRL。
* 生成的代码自动填入编辑器，用户可手动修改。
### 3.5 沙箱测试报告弹窗

```text
┌─────────────────────────────────────────────────────────────────────┐
│  回归测试报告 - 规则 "订单满100送10"                                 │
├─────────────────────────────────────────────────────────────────────┤
│  测试数据集: 500条历史事件 (2026-06-01 ~ 2026-06-07)                │
│  对比引擎: 基线(当前ACTIVE) vs 候选(基线+本规则)                    │
│                                                                     │
│  总体差异:                                                          │
│    ✅ 新增奖励: 23 条                                               │
│    ⚠️  奖励值变化: 2 条                                            │
│    ❌ 缺失奖励: 0 条                                               │
│                                                                     │
│  详细差异列表:                                                      │
│  ┌───────────────────────────────────────────────────────────────┐ │
│  │ 事件ID     │ 基线奖励 │ 候选奖励 │ 差异类型 │ 操作            │ │
│  │ evt_001    │ 10      │ 20      │ 变化     │ [查看详情]       │ │
│  │ evt_002    │ 0       │ 10      │ 新增     │ [查看详情]       │ │
│  └───────────────────────────────────────────────────────────────┘ │
│                                                                     │
│  评级: 🟡 黄色 (轻微差异)                                           │
│                                                                     │
│  [ 强制放行(需理由) ]                    [ 取消发布 ]               │
└─────────────────────────────────────────────────────────────────────┘
```
* 强制放行时弹出理由输入框，后端记录审批日志。
### 3.6 前端权限控制
根据主文档 §11 权限矩阵，规则引擎相关权限点：
| 操作      | 权限点                   | 角色    |
| ------- | --------------------- | ----- |
| 查看规则列表  | `rule:view`           | 客服/运营 |
| 创建/编辑规则 | `rule:edit`           | 运营    |
| 发布规则    | `rule:publish`        | 运营/经理 |
| 强制放行    | `rule:force_override` | 经理    |
在规则列表页、编辑器按钮中使用 `store.hasPermission()` 控制显隐。
### 3.7 API 汇总（新增）
| 方法   | 路径                               | 说明              |
| ---- | -------------------------------- | --------------- |
| GET  | `/api/rules`                     | 获取规则列表（支持分页、过滤） |
| GET  | `/api/rules/{ruleId}`            | 获取规则详情          |
| POST | `/api/rules`                     | 创建新规则（草稿）       |
| PUT  | `/api/rules/{ruleId}`            | 更新规则            |
| POST | `/api/rules/{ruleId}/publish`    | 发布规则（触发回归测试）    |
| POST | `/api/rules/{ruleId}/activate`   | 激活已发布的规则        |
| POST | `/api/rules/{ruleId}/deactivate` | 停用规则            |
| POST | `/api/rules/validate`            | 校验 DRL 语法       |
| POST | `/api/rules/ai-generate`         | 大模型生成 DRL       |
| POST | `/api/rules/regression-test`     | 执行沙箱回归测试        |
| POST | `/api/rules/test-run`            | 单事件模拟测试         |
所有接口需携带 `X-Program-Code` Header 及 JWT 认证。
***
## 四、规则引擎核心实现类（补充）
### 4.1 KieBaseCacheManager（参见设计文档 §6.1.2，已包含）
### 4.2 RuleEvaluationService

```java
@Service
public class RuleEvaluationService {
    @Autowired private KieBaseCacheManager kieBaseCacheManager;
    @Autowired private MemberRepository memberRepo;
    public List<Action> evaluate(String programCode, EventFact eventFact) {
        KieBase kieBase = kieBaseCacheManager.getKieBase(programCode);
        StatelessKieSession session = kieBase.newStatelessKieSession();
        
        Member member = memberRepo.findByMemberId(eventFact.getMemberId());
        MemberFact memberFact = MemberFactConverter.convert(member);
        
        List<Object> facts = new ArrayList<>();
        facts.add(eventFact);
        facts.add(memberFact);
        
        ActionCollector collector = ActionCollector.get();
        session.execute(facts);
        return collector.getActions();
    }
}
```
### 4.3 RuleCompiler

```java
@Component
public class RuleCompiler {
    public KieBase compile(String programCode, List<Rule> rules) {
        KieServices ks = KieServices.Factory.get();
        KieFileSystem kfs = ks.newKieFileSystem();
        for (Rule rule : rules) {
            String path = "src/main/resources/rules/" + programCode + "/" + rule.getRuleId() + ".drl";
            kfs.write(path, rule.getDrlContent());
        }
        KieBuilder builder = ks.newKieBuilder(kfs);
        builder.buildAll();
        if (builder.getResults().hasMessages(Message.Level.ERROR)) {
            throw new RuleCompileException(builder.getResults().getMessages());
        }
        KieContainer container = ks.newKieContainer(ks.getRepository().getDefaultReleaseId());
        return container.getKieBase();
    }
}
```
***
## 五、与主设计文档的衔接
本章内容是对 **第六章 规则引擎、沙箱与自动化回归** 的详细实现补充，应插入到主文档 §6 之后。同时更新附录中的枚举：
* 在 `behavior_code` 表中增加 `default_points`、`freq_limit_type` 等字段（已在 v7.3 附录中包含）。
***
**文档结束**

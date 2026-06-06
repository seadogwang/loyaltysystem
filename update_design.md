## 修改方案总览
| 章节        | 修改类型          | 说明                                            |
| --------- | ------------- | --------------------------------------------- |
| 第三章 3.4 节 | **替换**        | 用 One-ID 分层策略替换原有的简化版                         |
| 第七章       | **新增 7.6 节**  | API 实体到接口的自动生成                                |
| 第八章 8.6 节 | **替换**        | 用 ChartDB + Formily 方案替换 React Flow 方案        |
| 第八章 8.7 节 | **删除**        | 原总结表格移到新 8.7 节末尾                              |
| 第八章       | **新增 8.27 节** | 与现有业务模块的衔接说明                                  |
| 第十一章      | **新增 DDL**    | program_schema 表、channel_adapter_config 扩展 |
***
## 具体修改内容
### 修改一：第三章 3.4 节（替换为 One-ID 分层策略）
**删除**原 3.4.1、3.4.2、3.4.2.1、3.4.3 的全部内容，**替换**为：
```markdown
### 3.4 全渠道身份识别与会员合并 (Identity & Merge Strategy)
在全渠道体系下（天猫、微信、抖音同时引流），用户身份的统一（One-ID）是会员域最核心的挑战。系统采用 `member_unique_key` 表与分层匹配策略，保证并发场景下不产生"幽灵账号"。
#### 3.4.1 标识强度分级
| 标识类型 | 强度 | 唯一性 | 说明 |
|----------|------|--------|------|
| 手机号（已验证） | ★★★★★ | 跨渠道高 | 核心强标识 |
| 身份证号 | ★★★★★ | 跨渠道高 | 极少场景可获取 |
| 邮箱（已验证） | ★★★★ | 跨渠道较高 | |
| UnionID（微信开放平台） | ★★★★ | 同一开放平台下唯一 | |
| OpenID（单个公众号） | ★★ | 仅单应用内唯一 | |
| 设备 ID | ★ | 易变 | 仅辅助 |
| 匿名 ID / Cookie | ☆ | 极弱 | 仅埋点 |
#### 3.4.2 分层匹配流程
```
入会请求到达
↓
提取所有可用标识（手机号、UnionID、OpenID、设备ID...）
↓
第一层：强标识匹配（手机号/UnionID/邮箱）
→ 在 member_unique_key 中查询
→ 命中？→ 绑定渠道，复用 member_id
→ 未命中？→ 进入第二层
↓
第二层：弱标识匹配（OpenID/设备ID）
→ 在 member_unique_key 中查询
→ 命中？→ 绑定渠道，复用 member_id
→ 未命中？→ 创建新 member_id
↓
新 member_id 创建
→ 写入所有可用标识到 member_unique_key
→ 发布 MemberCreatedEvent
````text
#### 3.4.3 One-ID 建立的三个时机
| 时机 | 触发条件 | 行为 |
|------|---------|------|
| **入会时** | 请求包含强标识（手机号等） | 立即匹配，命中则复用，未命中则创建 |
| **绑定新标识时** | 已有 member_id 的用户新增渠道/手机号 | 写入 member_unique_key，若冲突触发合并 |
| **更新强标识字段时** | 用户更新 ext_attributes 中标记为强标识的字段 | 异步检查 member_unique_key，若冲突生成合并任务 |
#### 3.4.4 辅助唯一键表
系统不以手机号或 UnionID 作为会员主表的物理主键或唯一约束，而是建立一张辅助表：
```sql
CREATE TABLE member_unique_key (
    id BIGSERIAL PRIMARY KEY,
    program_code VARCHAR(32) NOT NULL,
    key_type VARCHAR(32) NOT NULL,       -- MOBILE, WECHAT_UNIONID, TMALL_OUID, EMAIL...
    key_value VARCHAR(128) NOT NULL,
    target_member_id VARCHAR(64) NOT NULL,
    is_strong BOOLEAN DEFAULT true,      -- 是否为强标识
    verified_at TIMESTAMPTZ,             -- 验证时间
    UNIQUE (program_code, key_type, key_value)
);
````
#### 3.4.5 并发入会与合并策略
```java
public void processEnrollment(EnrollmentRequest request) {
    String programCode = request.getProgramCode();
    String mobile = decrypt(request.getMobile());
    
    // 1. 统一锁 Key：只要请求中包含手机号，锁 Key 基于手机号哈希生成
    String lockKey = "loyalty:" + programCode + ":enroll:" + HashUtil.md5(mobile);
    RLock lock = redissonClient.getLock(lockKey);
    
    try {
        lock.lock(5, TimeUnit.SECONDS);
        
        // 2. 强标识匹配
        Long existingMemberId = uniqueKeyRepo.findMemberId(programCode, "MOBILE", mobile);
        
        if (existingMemberId != null) {
            bindNewChannel(existingMemberId, request.getChannel(), request.getChannelOpenId());
            return;
        }
        
        // 3. 弱标识匹配
        existingMemberId = uniqueKeyRepo.findMemberId(programCode, request.getChannel(), request.getChannelOpenId());
        
        if (existingMemberId != null) {
            // 弱标识命中，补充手机号绑定
            bindNewIdentifier(existingMemberId, "MOBILE", mobile);
            return;
        }
        
        // 4. 新会员注册
        Long newMemberId = generateSnowflakeId();
        Member newMember = new Member(programCode, newMemberId, request.getExtAttributes());
        memberRepo.save(newMember);
        
        uniqueKeyRepo.save(new UniqueKey(programCode, "MOBILE", mobile, newMemberId, true));
        uniqueKeyRepo.save(new UniqueKey(programCode, request.getChannel(), request.getChannelOpenId(), newMemberId, false));
        
        eventBridge.publish("loyalty-events", newMemberId.toString(), new MemberEnrolledEvent(newMemberId));
        
    } finally {
        lock.unlock();
    }
}
```
#### 3.4.6 数据库唯一约束兜底
```java
try {
    uniqueKeyRepo.save(new UniqueKey(programCode, "MOBILE", mobile, newMemberId));
} catch (DataIntegrityViolationException e) {
    // 并发冲突：另一个线程已抢先创建
    Long existingMemberId = uniqueKeyRepo.findMemberId(programCode, "MOBILE", mobile);
    bindNewChannel(existingMemberId, request.getChannel(), request.getChannelOpenId());
    memberRepo.deleteById(newMemberId);
    return;
}
```
#### 3.4.7 隐式合并触发
当用户更新 `ext_attributes` 中标记为强标识的字段时，触发异步合并检查：
```java
@Transactional
public void saveMemberExtAttributes(String memberId, Map<String, Object> extAttributes) {
    // ... 原有双写逻辑 ...
    // 检查强标识字段
    List<String> strongFields = schemaService.getStrongIdentifierFields(programCode, "MEMBER");
    for (String fieldKey : strongFields) {
        if (extAttributes.containsKey(fieldKey)) {
            String newValue = String.valueOf(extAttributes.get(fieldKey));
            eventBridge.publish("loyalty-identity-events", memberId,
                new StrongIdentifierUpdatedEvent(memberId, fieldKey, newValue));
        }
    }
}
```
#### 3.4.8 显式合并与资产转移
如果发现两个 `member_id` 实际上属于同一个人，触发显式合并：
* 选取一个作为主账号（注册早或等级高），另一个状态置为 `MERGED`。
* 积分资产累加，等级取最高，`member_unique_key` 记录全部重定向到主账号。
````text
---
### 修改二：第七章新增 7.6 节
在 7.5.4 之后新增：
```markdown
### 7.6 API 实体到接口的自动生成
#### 7.6.1 生成流程
用户在 ChartDB 中设计 API 实体并配置路由后，点击 [生成接口]：
1. 解析 API 实体定义，生成 JSON Schema（用于请求校验）
2. 在 `channel_adapter_config` 表创建/更新配置记录
3. 生成 GraalVM 映射脚本骨架（API 实体 → 系统实体）
4. 动态注册 SPI 路由
#### 7.6.2 channel_adapter_config 扩展
```sql
ALTER TABLE channel_adapter_config
ADD COLUMN api_entity_name VARCHAR(100),
ADD COLUMN request_schema JSONB,
ADD COLUMN response_schema JSONB,
ADD COLUMN cross_validations JSONB,
ADD COLUMN generated_controller BOOLEAN DEFAULT false;
````
#### 7.6.3 API 实体的联合校验
API 实体支持字段级校验（required/min/max/pattern）和跨字段联合校验脚本：
json
```
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
````text
---
### 修改三：第八章 8.6 节（替换为 ChartDB 方案）
**删除**原 8.6 节全部内容（8.6.1 到 8.6.10），**替换**为：
```markdown
### 8.6 动态 Schema 设计器 — 基于 ChartDB 的三层实体模型
#### 8.6.1 技术选型
选用 **ChartDB**（开源 React 组件）作为画布引擎：
- 原生 React 组件，可直接嵌入
- 支持节点、字段、连线、JSON 导入导出
- 支持列级别 `extensions` 扩展属性（承载 Formily 属性）
- 现代化 UI，与 Ant Design 风格一致
#### 8.6.2 三层实体模型
画布上区分三类实体：
| 实体类型 | 视觉标识 | 可否删除 | 说明 |
|----------|---------|---------|------|
| 系统实体 | 灰色背景 + 🔒 | ❌ | Member, TransactionEvent, MemberAccount, MemberUniqueKey, Program |
| 业务实体 | 蓝色背景 + 📦 | ✅ | PetInfo, PurchaseRecord 等，映射到系统实体的 JSONB 容器字段 |
| API 实体 | 绿色背景 + 🔌 | ✅ | OrderRequest, EnrollRequest 等，映射到 SPI 网关接口 |
#### 8.6.3 系统实体预加载
画布初始化时自动放置以下系统实体（锁定不可删除）：
**Member（会员）**
| 字段 | 类型 | 锁定 | 说明 |
|------|------|------|------|
| `member_id` | String | 🔒 | 主键，One-ID |
| `program_code` | String | 🔒 | 租户隔离 |
| `status` | Enum | 🔒 | ENROLLED/SUSPENDED/MERGED/DEACTIVATED |
| `tier_code` | String | 🔓 | 当前等级 |
| `schema_version` | String | 🔓 | Schema 版本号 |
| `created_at` | DateTime | 🔒 | 注册时间 |
| `ext_attributes` | Object | 🔓 容器 | 动态扩展属性（内容由业务实体定义） |
**TransactionEvent（交易事件）**
| 字段 | 类型 | 锁定 | 说明 |
|------|------|------|------|
| `event_id` | String | 🔒 | 主键 |
| `member_id` | String | 🔒 | 外键→Member |
| `event_type` | Enum | 🔒 | ORDER/ENROLL/CUSTOM/REDEMPTION |
| `channel` | String | 🔒 | 渠道标识 |
| `event_time` | DateTime | 🔒 | 事件时间 |
| `idempotent_key` | String | 🔒 | 幂等键 |
| `payload` | Object | 🔓 容器 | 动态扩展数据 |
**MemberAccount（积分账户）**、**MemberUniqueKey（One-ID 辅助表）**、**Program（租户计划）** 同样预加载并锁定。
#### 8.6.4 系统实体预加载连线
````
Member ───1:N─── MemberUniqueKey
Member ───1:1─── MemberAccount
TransactionEvent ───N:1─── Member
```text
这些连线同样锁定，不可删除。
#### 8.6.5 业务实体设计
业务实体映射到系统实体的 JSONB 容器字段。连线时配置映射关系：
| 配置项 | 说明 | 示例 |
|--------|------|------|
| 源实体 | 业务实体 | PetInfo |
| 目标实体 | 系统实体 | Member |
| 映射到字段 | 系统实体的 JSONB 字段 | ext_attributes |
| 存储路径 | JSON 中的 Key 名 | pets |
| 存储方式 | Object / Array | Array |
#### 8.6.6 API 实体设计
API 实体额外支持：
**基础校验规则**：required, min_length, max_length, pattern, minimum, maximum, enum_values
**联合校验脚本**：JavaScript 函数，支持跨字段校验
**API 路由配置**：method, path, auth_type, response_entity
#### 8.6.7 右侧属性面板
选中字段时滑出，两个 Tab：
- **结构配置**：字段名、类型、主键、必填、默认值、枚举值、强标识标记
- **呈现配置**：x-component、x-reactions（可视化构建器）、x-validator、deprecated
#### 8.6.8 数据转换
- 保存时：ChartDB 数据 + extensions → LoyaltySchema（Formily 可消费格式）
- 加载时：LoyaltySchema → ChartDB 数据 + extensions
#### 8.6.9 版本管理
`program_schema` 表存储 Schema 版本，支持 DRAFT/ACTIVE/DEPRECATED 状态流转，历史版本回滚。
#### 8.6.10 废弃字段的 DRL 引用检查
后端在废弃字段前检查所有 ACTIVE 规则的 DRL 内容，若引用该字段则抛出 `ERR_FIELD_IN_USE`。
```
***
### 修改四：第八章新增 8.27 节
在 8.26.3 之后新增：
````markdown
### 8.27 设计器与业务模块的衔接
#### 8.27.1 会员详情页
会员详情页的属性扩展 Tab 直接消费设计器产出的 Schema：
- 前端请求 `GET /api/admin/schemas/MEMBER/current`
- 后端返回 LoyaltySchema → `DynamicFormRenderer` 渲染动态表单
- 保存时调用 `MemberExtService.saveMemberExtAttributes`（含双写 + 强标识检查）
#### 8.27.2 SPI 网关
API 实体生成的接口自动注册到 SPI 网关：
- 第三方回调 → `SpiGatewayController` 接收
- 查找对应 API 实体配置 → JSON Schema 校验 → 联合校验脚本执行
- 映射引擎转换（API 实体 → TransactionEvent）→ 投递 EventBridge
#### 8.27.3 规则引擎引用
业务实体的字段自动成为 DRL 可引用的 Fact 属性：
```drools
rule "宠物主人专属优惠"
when
    $event : EventFact( eventType == "ORDER" )
    $member : MemberFact( memberId == $event.memberId, getExtNumber("pets.length") > 0 )
then
    ActionCollector.awardPoints($event.getEventId(), 50, "PET_OWNER_REWARD");
end
````
#### 8.27.4 画布初始化状态
用户首次进入 Schema 设计器时，画布预加载：
* 5 个系统实体节点（锁定，灰色背景 + 🔒）
* 3 条系统连线（锁定）
* 用户可在此基础上创建业务实体和 API 实体
````text
---
### 修改五：第十一章新增 DDL
在现有 DDL 末尾新增：
```sql
-- Schema 版本管理表
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
-- 渠道适配器配置扩展
ALTER TABLE channel_adapter_config
ADD COLUMN IF NOT EXISTS api_entity_name VARCHAR(100),
ADD COLUMN IF NOT EXISTS request_schema JSONB,
ADD COLUMN IF NOT EXISTS response_schema JSONB,
ADD COLUMN IF NOT EXISTS cross_validations JSONB,
ADD COLUMN IF NOT EXISTS generated_controller BOOLEAN DEFAULT false;
-- member_unique_key 扩展
ALTER TABLE member_unique_key
ADD COLUMN IF NOT EXISTS is_strong BOOLEAN DEFAULT true,
ADD COLUMN IF NOT EXISTS verified_at TIMESTAMPTZ;
````
***
## 修改汇总
| 位置            | 操作     | 内容                                                                   |
| ------------- | ------ | -------------------------------------------------------------------- |
| 第三章 3.4 节     | **替换** | One-ID 分层策略（标识强度分级、三个时机、隐式合并）                                        |
| 第七章 新增 7.6 节  | **新增** | API 实体到接口的自动生成                                                       |
| 第八章 8.6 节     | **替换** | ChartDB + 三层实体模型方案                                                   |
| 第八章 新增 8.27 节 | **新增** | 设计器与业务模块衔接                                                           |
| 第十一章 DDL      | **新增** | program_schema 表、channel_adapter_config 扩展、member_unique_key 扩展 |
这些修改全部以**最小侵入性**方式完成，不改变原有章节编号和结构，只替换或新增必要内容。你可以直接把这些修改应用到设计文档中。

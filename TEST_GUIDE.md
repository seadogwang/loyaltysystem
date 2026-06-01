# Loyalty SaaS 测试文档

> 版本: 1.0.0 | 日期: 2026-06-01 | 测试总数: 131

---

## 一、测试环境准备

### 1.1 必须安装

| 软件 | 版本 | 用途 |
|------|------|------|
| JDK | 17+ | 编译运行后端 |
| Maven | 3.9+ | 后端构建 |
| Node.js | 18+ | 前端构建 |
| PostgreSQL | 15/16 | 数据库（已配置 loyalty_dev） |
| Playwright | 1.44+ | 前端 E2E 测试 |

### 1.2 数据库

```bash
# 连接方式（无需密码，本地 trust 认证）
psql -U postgres -d loyalty_dev -h localhost

# 确认表结构完整
psql -U postgres -d loyalty_dev -c "\dt"
```

### 1.3 快速检查

```bash
# 检查 Java
java --version   # 应为 17+

# 检查 Maven
mvn --version    # 应为 3.9+

# 检查 Node
node --version   # 应为 18+

# 检查 PostgreSQL
psql -U postgres -d loyalty_dev -c "SELECT COUNT(*) FROM program;"
```

---

## 二、后端测试（JUnit 5 + Spring Test）

### 2.1 运行所有后端测试

```bash
cd D:/Project/Loyalty-saas
mvn test
```

预期输出：
```
Tests run: 107, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### 2.2 分阶段运行

```bash
# 阶段一：基建与防线 (28 tests)
mvn test -Dtest="TenantContextTest,BaseDomainEventTest,LocalEventBusTest,JsonbConverterTest,ProgramRepositoryIntegrationTest"

# 阶段二：SPI网关 (33 tests)
mvn test -Dtest="ApiResponseTest,BusinessExceptionTest,GlobalExceptionHandlerTest,EventFactTest,SpiHandlerFactoryTest,ScriptingTransformerTest,TmallSpiHandlerTest,SpiHandlerFactoryIntegrationTest,EventInboxProcessorTest,SpiFullChainIntegrationTest"

# 阶段三：积分账务 (6 tests)
mvn test -Dtest="AccountingIntegrationTest"

# 阶段四：级联重算 (19 tests)
mvn test -Dtest="ShadowContextTest,AccountDeltaTest,CascadeIntegrationTest,CascadeFullChainTest"

# 阶段五：规则沙箱 (11 tests)
mvn test -Dtest="RulesUnitTest"

# 阶段六：API闭环 (6 tests)
mvn test -Dtest="ApiIntegrationTest"
```

### 2.3 测试分类说明

| 测试类型 | 注解 | 数据来源 | 特点 |
|---------|------|---------|------|
| 单元测试 | 无 Spring 上下文 | 纯内存/构造数据 | 毫秒级，无需 DB |
| 集成测试 | `@DataJpaTest` | 真实 DB | 秒级，需要 loyalty_dev |

### 2.4 关键的集成测试

```bash
# 只跑数据库相关测试（需要 PostgreSQL）
mvn test -Dtest="ProgramRepositoryIntegrationTest,AccountingIntegrationTest,CascadeFullChainTest,ApiIntegrationTest,EventInboxProcessorTest,TmallSpiHandlerTest,SpiFullChainIntegrationTest"
```

### 2.5 排除 Redis 依赖

如果本地没有 Redis，Redis 相关 bean 已在 `LoyaltySaasApplication` 中排除：
```java
@SpringBootApplication(exclude = {
    RedissonAutoConfigurationV2.class,
    RedisAutoConfiguration.class
})
```

---

## 三、前端测试（Playwright E2E）

### 3.1 安装依赖

```bash
cd D:/Project/Loyalty-saas/src/frontend

# 首次运行：安装依赖 + 浏览器
npm install
npx playwright install chromium
```

### 3.2 启动后端（必须先做）

```bash
# 终端 1：启动后端 API
cd D:/Project/Loyalty-saas
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8090"

# 等待显示：Started LoyaltySaasApplication
```

### 3.3 运行所有前端测试

```bash
# 终端 2：运行 E2E 测试（自动启动前端 Vite 开发服务器）
cd D:/Project/Loyalty-saas/src/frontend
npx playwright test --project=chromium
```

预期输出：
```
24 passed (20s)
```

### 3.4 分模块运行

```bash
# 只测试 Schema 设计器 (6 tests)
npx playwright test --project=chromium schema-builder

# 只测试动态渲染器 (2 tests)
npx playwright test --project=chromium dynamic-renderer

# 只测试脚本工作台 (8 tests)
npx playwright test --project=chromium scripting-workbench

# 只测试全链路 API (8 tests)
npx playwright test --project=chromium full-e2e
```

### 3.5 调试模式

```bash
# 有 UI 界面，逐个步骤观察
npx playwright test --project=chromium --ui

# 只看某个失败的测试
npx playwright test --project=chromium --trace on

# 查看测试报告
npx playwright show-report playwright-report
```

### 3.6 多浏览器

```bash
# Chromium + Firefox
npx playwright test
```

---

## 四、手动启动前后端进行手工测试

### 4.1 启动后端

```bash
cd D:/Project/Loyalty-saas
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8090"
```

后端启动后访问：
- 健康检查：`http://localhost:8090/actuator/health`
- API 示例：`http://localhost:8090/api/members/8821`

### 4.2 启动前端

```bash
cd D:/Project/Loyalty-saas/src/frontend
npm run dev
```

前端启动后访问：`http://localhost:6173`

### 4.3 手工测试 API

```bash
# 1. 获取 Schema
curl -H "X-Program-Code: PROG001" http://localhost:8090/api/schemas/MEMBER

# 2. 查询会员
curl -H "X-Program-Code: PROG001" http://localhost:8090/api/members/8821

# 3. 检查租户隔离（应返回 403）
curl http://localhost:8090/api/members/8821

# 4. 创建会员（需幂等键）
curl -X POST \
  -H "X-Program-Code: PROG001" \
  -H "X-Idempotency-Key: manual-test-$(date +%s)" \
  -H "Content-Type: application/json" \
  -d '{"tier_code":"BASE","ext_attributes":{"test":true}}' \
  http://localhost:8090/api/members
```

---

## 五、问题排查

### 5.1 端口冲突

```bash
# 检查 8090 是否被占用
netstat -ano | findstr 8090

# 检查 6173 是否被占用
netstat -ano | findstr 6173

# 强制杀死占用进程
taskkill /F /PID <PID>
```

### 5.2 后端启动失败

常见原因：
1. PostgreSQL 未启动 → `net start postgresql-x64-16`
2. 数据库不存在 → 检查 `loyalty_dev` 数据库
3. 端口占用 → 换端口 `--server.port=8091`

### 5.3 前端启动失败

```bash
# 清理依赖重装
rm -rf node_modules package-lock.json
npm install
```

### 5.4 测试失败

```bash
# 查看后端测试报告
cat target/surefire-reports/*.txt

# 查看前端测试报告
npx playwright show-report playwright-report
```

---

## 六、测试覆盖速查

| 系统模块 | 测试文件 | 测试数 |
|---------|---------|--------|
| 租户上下文 | TenantContextTest | 7 |
| 事件总线 | BaseDomainEventTest + LocalEventBusTest | 9 |
| JSONB 转换 | JsonbConverterTest | 6 |
| 数据库映射 | ProgramRepositoryIntegrationTest | 6 |
| API 响应 | ApiResponseTest | 3 |
| 异常处理 | BusinessExceptionTest + GlobalExceptionHandlerTest | 6 |
| SPI 网关 | SpiHandlerFactoryTest + TmallSpiHandlerTest + SpiFullChainIntegrationTest + SpiHandlerFactoryIntegrationTest | 15 |
| 脚本沙箱 | ScriptingTransformerTest | 5 |
| 事件收件箱 | EventInboxProcessorTest | 4 |
| 积分账务 | AccountingIntegrationTest | 6 |
| 级联重算 | ShadowContextTest + AccountDeltaTest + CascadeIntegrationTest + CascadeFullChainTest | 19 |
| 规则引擎 | RulesUnitTest | 11 |
| Schema API | ApiIntegrationTest | 6 |
| 前端 E2E | schema-builder + dynamic-renderer + scripting-workbench + full-e2e | 24 |
| **合计** | | **131** |
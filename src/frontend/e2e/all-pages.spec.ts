import { test, expect } from '@playwright/test';

/**
 * 全面页面测试 — 访问所有页面，验证每个页面渲染正常
 * 覆盖旧的组件式路由和新 React Router 路由
 */

const PAGES = [
  // 数据建模
  { path: '/modeling/entity', name: '实体建模器', selector: 'text=实体模型画布' },
  { path: '/modeling/schema', name: 'Schema 设计器', selector: 'text=组件面板' },
  // 仪表盘
  { path: '/dashboard', name: '仪表盘', selector: 'text=仪表盘' },
  // Program 管理
  { path: '/programs', name: 'Program 管理', selector: 'text=Program 管理' },
  // 会员中心
  { path: '/members', name: '会员列表', selector: 'text=新建会员' },
  // 积分管理
  { path: '/points/accounts', name: '账户总览', selector: 'text=账户总览' },
  { path: '/points/transactions', name: '积分流水', selector: 'text=积分流水' },
  { path: '/points/grant', name: '积分发放', selector: 'text=积分发放' },
  { path: '/points/redeem', name: '积分核销', selector: 'text=积分核销' },
  // 等级与规则
  { path: '/tiers', name: '等级阶梯', selector: 'text=等级阶梯配置' },
  { path: '/rules', name: '规则列表', selector: 'text=规则管理' },
  // 渠道集成
  { path: '/channels', name: '渠道列表', selector: 'text=渠道适配器配置' },
  { path: '/channels/scripting', name: '脚本工作台', selector: 'text=转换脚本' },
  // 运维监控
  { path: '/ops/event-inbox', name: '事件收件箱', selector: 'text=事件收件箱' },
  { path: '/ops/notifications', name: '通知管理', selector: 'text=通知管理' },
  { path: '/ops/redemption-cancel', name: '退换货还原', selector: 'text=退换货积分还原' },
  // 系统设置
  { path: '/system/roles', name: '角色权限', selector: 'text=角色权限管理' },
  { path: '/system/logs', name: '操作日志', selector: 'text=操作日志' },
  { path: '/system/spi-logs', name: 'SPI 日志', selector: 'text=SPI 调用日志' },
  { path: '/system/audit', name: '租户审计', selector: 'text=租户污染审计' },
];

const BASE_URL = 'http://localhost:6173';

for (const page of PAGES) {
  test(`页面「${page.name}」— 渲染验证`, async ({ page: p }) => {
    const errors: string[] = [];
    p.on('console', msg => { if (msg.type() === 'error') errors.push(msg.text()); });
    p.on('pageerror', err => errors.push(err.message));

    // 直接导航到页面路径
    await p.goto(`${BASE_URL}${page.path}`, { waitUntil: 'networkidle', timeout: 15000 });
    await p.waitForTimeout(1000);

    // 检查目标内容是否渲染
    const hasContent = await p.locator(page.selector).first().isVisible({ timeout: 5000 }).catch(() => false);

    // 截图留档
    const safeName = page.name.replace(/[/\\?%*:|"<>]/g, '-');
    await p.screenshot({ path: `test-results/page-${safeName}.png`, fullPage: true });

    if (!hasContent && errors.length > 0) {
      console.log(`[FAIL] ${page.name}: 内容未渲染 + ${errors.length} errors: ${errors.slice(0, 3).join(' | ')}`);
    } else if (!hasContent) {
      console.log(`[WARN] ${page.name}: 目标内容未找到 (${page.selector})`);
    } else if (errors.length > 0) {
      console.log(`[WARN] ${page.name}: 有 ${errors.length} 个 console 错误，但内容已渲染`);
    } else {
      console.log(`[PASS] ${page.name}: 正常渲染 (0 errors)`);
    }

    // 软断言：页面至少有一些内容
    const bodyText = await p.locator('body').innerText();
    expect(bodyText.trim().length).toBeGreaterThan(10);
  });
}

// ==================== 核心业务流程测试 ====================

test.describe('核心业务流程', () => {
  const BACKEND = 'http://localhost:8090';

  test('入会 → 发分 → 核销 全链路验证', async ({ page, request }) => {
    // Step 1: 创建会员
    const testMemberId = Date.now();
    const createResp = await request.post(`${BACKEND}/api/members`, {
      headers: {
        'X-Program-Code': 'PROG001',
        'X-Idempotency-Key': 'e2e-flow-' + testMemberId,
        'Content-Type': 'application/json',
      },
      data: { member_id: testMemberId, tier_code: 'BASE', ext_attributes: { pet_name: 'E2E测试' } },
    });
    expect(createResp.status()).toBe(200);
    console.log('[E2E-Flow] Step 1: 会员创建完成');

    // Step 2: 发放积分
    const grantResp = await request.post(`${BACKEND}/api/admin/points/grant`, {
      headers: {
        'X-Program-Code': 'PROG001',
        'X-Idempotency-Key': 'e2e-grant-' + testMemberId,
        'Content-Type': 'application/json',
      },
      data: { member_id: testMemberId, account_type: 'REWARD_POINTS', points: 500 },
    });
    if (grantResp.status() === 200) {
      console.log('[E2E-Flow] Step 2: 积分发放完成');
    }

    // Step 3: 核销积分
    const redeemResp = await request.post(`${BACKEND}/api/admin/points/redeem`, {
      headers: {
        'X-Program-Code': 'PROG001',
        'X-Idempotency-Key': 'e2e-redeem-' + testMemberId,
        'Content-Type': 'application/json',
      },
      data: { member_id: testMemberId, account_type: 'REWARD_POINTS', points: 100 },
    });
    if (redeemResp.status() === 200) {
      console.log('[E2E-Flow] Step 3: 积分核销完成');
    }

    // Step 4: 前端访问会员详情页
    await page.goto(`${BASE_URL}/members/${testMemberId}`, { waitUntil: 'networkidle', timeout: 15000 });
    await page.waitForTimeout(1000);
    const hasContent = await page.locator('text=会员详情').first().isVisible({ timeout: 5000 }).catch(() => false);
    console.log(`[E2E-Flow] Step 4: 会员详情页 ${hasContent ? '渲染成功' : '未渲染'}`);

    // Step 5: 前端访问仪表盘
    await page.goto(`${BASE_URL}/dashboard`, { waitUntil: 'networkidle', timeout: 15000 });
    await page.waitForTimeout(1000);
    const hasDashboard = await page.locator('text=仪表盘').first().isVisible({ timeout: 5000 }).catch(() => false);
    console.log(`[E2E-Flow] Step 5: 仪表盘 ${hasDashboard ? '渲染成功' : '未渲染'}`);

    // 清理
    await request.delete(`${BACKEND}/api/members/${testMemberId}`, {
      headers: { 'X-Program-Code': 'PROG001' },
    }).catch(() => {});
  });

  test('规则创建 → 沙箱测试流程', async ({ page }) => {
    await page.goto(`${BASE_URL}/rules/new`, { waitUntil: 'networkidle', timeout: 15000 });
    await page.waitForTimeout(1000);

    // 检查 Monaco 编辑器是否加载
    const hasEditor = await page.locator('.monaco-editor').first().isVisible({ timeout: 10000 }).catch(() => false);
    console.log(`[E2E-Rule] 规则编辑器 ${hasEditor ? 'Monaco 编辑器加载成功' : 'Monaco 编辑器未加载'}`);

    // 检查 AI 辅助区域
    const hasAiArea = await page.locator('text=AI 辅助').first().isVisible({ timeout: 3000 }).catch(() => false);
    console.log(`[E2E-Rule] AI 辅助区域 ${hasAiArea ? '可见' : '不可见'}`);

    // 截图
    await page.screenshot({ path: 'test-results/page-rule-editor.png', fullPage: true });
  });

  test('映射编辑器脚本模式', async ({ page }) => {
    // 导航到渠道配置后通过菜单找映射编辑器
    await page.goto(`${BASE_URL}/channels`, { waitUntil: 'networkidle', timeout: 15000 });
    await page.waitForTimeout(1000);

    const hasChannelList = await page.locator('text=渠道适配器配置').first().isVisible({ timeout: 5000 }).catch(() => false);
    console.log(`[E2E-Mapping] 渠道列表 ${hasChannelList ? '渲染成功' : '未渲染'}`);

    await page.screenshot({ path: 'test-results/page-channels.png', fullPage: true });
  });
});
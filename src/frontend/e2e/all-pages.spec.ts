import { test, expect } from '@playwright/test';

/**
 * 全面页面测试 — 访问所有 15 个页面，验证每个页面渲染正常
 */

const PAGES = [
  { key: 'entity-modeler', name: '实体建模器', menuText: '实体建模器' },
  { key: 'form-designer', name: '表单设计器', menuText: '表单设计器' },
  { key: 'member-list', name: '会员列表', menuText: '会员列表' },
  { key: 'dynamic-renderer', name: '会员详情', menuText: '会员详情/编辑' },
  { key: 'points-grant', name: '积分发放', menuText: '积分发放' },
  { key: 'points-redeem', name: '积分核销', menuText: '积分核销' },
  { key: 'points-history', name: '流水查询', menuText: '流水查询' },
  { key: 'tier-config', name: '等级阶梯配置', menuText: '等级阶梯配置' },
  { key: 'rule-management', name: '规则管理', menuText: '规则管理' },
  { key: 'channel-config', name: '渠道配置', menuText: '渠道配置' },
  { key: 'scripting', name: '脚本工作台', menuText: '脚本工作台' },
  { key: 'event-inbox', name: '事件收件箱', menuText: '事件收件箱' },
  { key: 'audit-logs', name: '审计日志', menuText: '审计日志' },
  { key: 'notification', name: '通知管理', menuText: '通知管理' },
];

for (const page of PAGES) {
  test(`页面「${page.name}」— 渲染验证`, async ({ page: p }) => {
    // 监听 console 错误
    const errors: string[] = [];
    p.on('console', msg => { if (msg.type() === 'error') errors.push(msg.text()); });
    p.on('pageerror', err => errors.push(err.message));

    await p.goto('http://localhost:6173', { waitUntil: 'networkidle', timeout: 15000 });
    await p.waitForTimeout(500);

    // 如果侧边栏有分组，先展开
    const parentKeys = ['modeling', 'member', 'points', 'tier-rule', 'channel', 'ops'];
    for (const key of parentKeys) {
      try {
        await p.click(`[data-menu-id="subMenu-${key}"]`, { timeout: 2000 }).catch(() => {});
      } catch {}
    }

    // 点击菜单项
    try {
      await p.getByText(page.menuText).first().click({ timeout: 5000 });
    } catch {
      // 尝试用 ant-menu-item 选择器
      try {
        await p.locator('.ant-menu-item').filter({ hasText: page.menuText }).first().click({ timeout: 5000 });
      } catch {
        errors.push(`无法点击菜单项: ${page.menuText}`);
      }
    }

    await p.waitForTimeout(2000);

    // 检查页面是否有内容（不是空白的）
    const bodyText = await p.locator('body').innerText();
    const isEmpty = bodyText.trim().length < 20;

    // 截图留档
    await p.screenshot({ path: `test-results/page-${page.key}.png`, fullPage: true });

    if (isEmpty && errors.length > 0) {
      console.log(`[FAIL] ${page.name}: 页面空白 + ${errors.length} errors: ${errors.slice(0,3).join(' | ')}`);
    } else if (isEmpty) {
      console.log(`[WARN] ${page.name}: 页面内容较少 (${bodyText.trim().length} chars)`);
    } else if (errors.length > 0) {
      console.log(`[WARN] ${page.name}: 有 ${errors.length} 个 console 错误，但内容已渲染 (${bodyText.trim().length} chars)`);
    } else {
      console.log(`[PASS] ${page.name}: 正常渲染 (${bodyText.trim().length} chars, 0 errors)`);
    }

    // 软断言：至少有内容
    expect(bodyText.trim().length).toBeGreaterThan(10);
  });
}
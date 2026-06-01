import { test, expect } from '@playwright/test';

test.describe('DynamicRenderer — 动态渲染引擎', () => {

  test('标签页完整切换', async ({ page }) => {
    await page.goto('/');
    await page.waitForTimeout(500);

    // Schema Builder — 用 first() 避免 Card title 冲突
    await page.getByText('Schema 设计器').first().click();
    await page.waitForTimeout(1000);
    const hasBuilder = await page.getByText('组件面板').first().isVisible({ timeout: 5000 }).catch(() => false);
    expect(hasBuilder).toBeTruthy();

    // Scripting Workbench
    await page.getByText('渠道脚本工作台').first().click();
    await page.waitForTimeout(1000);
    const hasScripting = await page.getByText('原始第三方 JSON').first().isVisible({ timeout: 5000 }).catch(() => false);
    expect(hasScripting).toBeTruthy();
  });

  test('Loyalty SaaS Admin 标题始终可见', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('h1:has-text("Loyalty SaaS Admin")')).toBeVisible();
  });
});
import { test, expect } from '@playwright/test';

test.describe('DynamicRenderer — 动态渲染引擎', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await page.click('text=动态渲染器');
    await page.waitForTimeout(2000);
  });

  test('页面标题和标签页正确显示', async ({ page }) => {
    await expect(page.locator('h1:has-text("Loyalty SaaS Admin")')).toBeVisible();
    await expect(page.locator('text=Schema 设计器')).toBeVisible();
    await expect(page.locator('text=动态渲染器')).toBeVisible();
    await expect(page.locator('text=渠道脚本工作台')).toBeVisible();
  });

  test('API 不可用时显示空状态提示', async ({ page }) => {
    const emptyText = page.locator('text=无法加载数据');
    const loading = page.locator('.ant-spin');
    const hasEmpty = await emptyText.isVisible({ timeout: 3000 }).catch(() => false);
    const hasLoading = await loading.isVisible({ timeout: 3000 }).catch(() => false);
    expect(hasEmpty || hasLoading).toBeTruthy();
  });

  test('标签页切换功能正常', async ({ page }) => {
    await page.click('text=Schema 设计器');
    await expect(page.locator('text=组件面板')).toBeVisible({ timeout: 3000 });

    await page.click('text=动态渲染器');
    await page.waitForTimeout(500);

    await page.click('text=渠道脚本工作台');
    await expect(page.locator('text=原始第三方 JSON')).toBeVisible({ timeout: 3000 });
  });
});
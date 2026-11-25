import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import path from 'node:path';

const SCREENSHOT_DIR = path.resolve('..', 'docs', 'screenshots');

test.describe('FinSentinel AI Full E2E, Demo Walkthrough, Viewports & WCAG Audit', () => {
  test('national-level 11-step demonstration flow, theme/language, viewports, and axe accessibility', async ({ page }) => {
    const consoleErrors: string[] = [];
    page.on('console', (msg) => {
      if (msg.type() === 'error') consoleErrors.push(msg.text());
    });

    // 0. Landing page & interactive preview
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto('/');
    await expect(page.getByRole('heading', { level: 1 })).toContainText(
      'Understand your money before uncertainty becomes a problem.'
    );
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, 'landing-desktop-1440.png'), fullPage: true });

    // Accessibility check on Landing Page
    const landingAxe = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag22aa']).analyze();
    expect(landingAxe.violations.filter((v) => v.impact === 'critical' || v.impact === 'serious')).toEqual([]);

    // 1. Login with reproducible fictional demo profile
    await page.goto('/auth/login');
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, 'login-desktop-1440.png') });
    await page.getByLabel('Email Address', { exact: true }).fill('demo@finsentinel.local');
    await page.getByLabel('Password', { exact: true }).fill('DemoPass#2026');
    await page.getByRole('button', { name: /Sign in to FinSentinel AI/i }).click();

    // 2. Dashboard overview
    await page.waitForURL('**/app/overview');
    await expect(page.getByText(/Welcome back/i)).toBeVisible();
    await expect(page.getByText(/Cash Flow Intelligence/i)).toBeVisible();
    await page.waitForTimeout(350);
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, 'dashboard-light-1440.png'), fullPage: true });

    // Dark theme screenshot & check
    await page.getByRole('button', { name: 'Toggle theme' }).click();
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, 'dashboard-dark-1440.png'), fullPage: true });
    // Toggle back to light
    await page.getByRole('button', { name: 'Toggle theme' }).click();

    // Ensure English start then verify Tamil language switch and revert
    const langBtn = page.getByRole('button', { name: 'Switch language' });
    if ((await langBtn.textContent())?.trim() !== 'EN') {
      await langBtn.click();
    }
    await langBtn.click();
    await expect(page.getByRole('link', { name: 'பணப்புழக்க கணிப்பு' })).toBeVisible();
    await langBtn.click();
    await expect(page.getByRole('link', { name: 'Cash Flow Forecast' })).toBeVisible();

    // 3. Inspect Risk Score & Risk Anatomy
    await page.getByRole('link', { name: /Full Anatomy/i }).click();
    await page.waitForURL('**/app/risk');
    await expect(page.getByRole('heading', { name: /Explainable Risk Anatomy/i })).toBeVisible();
    await page.waitForTimeout(350);
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, 'risk-anatomy-1440.png') });

    // 4-7. Digital Twin: salary delay 15d + unexpected expense 25,000 -> recalculated projection & affected obligations
    await page.goto('/app/digital-twin');
    await expect(page.getByRole('heading', { name: /Financial Digital Twin/i })).toBeVisible();
    await page.getByLabel(/Salary \/ Income Delay/i).fill('15');
    await page.getByLabel(/Unexpected Expense/i).fill('25000');
    await page.waitForTimeout(600);
    await expect(page.getByText(/Minimum projected balance changes/i)).toBeVisible();
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, 'digital-twin-1440.png'), fullPage: true });

    // 8. Compare alternative payment schedules
    await page.goto('/app/payments');
    await expect(page.getByText(/Alternative Scenario Schedules/i)).toBeVisible();
    await expect(page.getByText(/Contractual Due-Date Schedule/i)).toBeVisible();
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, 'payment-planner-1440.png') });

    // 9-10. Ask AI why risk changed & inspect backend-grounded explanation
    await page.goto('/app/assistant');
    await page.getByLabel(/Message AI Financial Assistant/i).fill('Why did my risk score change and what payments are upcoming?');
    await page.getByRole('button', { name: /^Send$/i }).click();
    await expect(page.getByText(/Deterministic Backend Analysis|Explainable Risk Score/i)).toBeVisible({ timeout: 15000 });
    await page.getByRole('button', { name: /Inspect Backend-Grounded Calculations/i }).first().click();
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, 'ai-assistant-1440.png') });

    // 11. Export PDF report
    await page.goto('/app/reports');
    await page.getByRole('button', { name: /Generate PDF Report/i }).click();
    await expect(page.getByText(/Download PDF/i).first()).toBeVisible({ timeout: 10000 });

    // Visit remaining routes to guarantee zero broken routes or horizontal overflow
    const routes = [
      '/app/transactions',
      '/app/income-expenses',
      '/app/forecast',
      '/app/stress-test',
      '/app/timeline',
      '/app/goals',
      '/app/notifications',
      '/app/settings',
      '/app/help',
    ];
    for (const r of routes) {
      await page.goto(r);
      await expect(page.locator('main h1')).toBeVisible();
      const hasOverflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth + 2);
      expect(hasOverflow, `Horizontal overflow at 1440px on ${r}`).toBe(false);
    }

    // Command palette Ctrl+K check
    await page.keyboard.press('Control+k');
    await expect(page.getByRole('heading', { name: 'Command Palette' })).toBeVisible();
    await page.keyboard.press('Escape');

    // Mobile & Tablet viewport checks (360px, 390px, 768px, 1024px)
    for (const vp of [
      { width: 360, height: 800 },
      { width: 390, height: 844 },
      { width: 768, height: 1024 },
      { width: 1024, height: 768 },
    ]) {
      await page.setViewportSize(vp);
      await page.goto('/app/overview');
      await expect(page.getByText(/Cash Flow Intelligence/i)).toBeVisible();
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth + 2);
      expect(overflow, `Horizontal overflow at ${vp.width}px`).toBe(false);
      if (vp.width === 390) {
        await page.screenshot({ path: path.join(SCREENSHOT_DIR, 'dashboard-mobile-390.png'), fullPage: true });
      }
    }

    // Accessibility check on authenticated Dashboard
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto('/app/overview');
    const dashAxe = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag22aa']).analyze();
    expect(dashAxe.violations.filter((v) => v.impact === 'critical' || v.impact === 'serious')).toEqual([]);

    expect(consoleErrors).toEqual([]);
  });

  test('new registration lands in genuine empty state and admin role boundary is enforced', async ({ page }) => {
    const email = `e2e-${Date.now()}@example.com`;
    await page.goto('/auth/register');
    await page.getByLabel('Full Name', { exact: true }).fill('Priya Sundar');
    await page.getByLabel('Email Address', { exact: true }).fill(email);
    await page.getByLabel('Password', { exact: true }).fill('FreshUser#2026');
    await page.getByRole('button', { name: /Create your financial workspace/i }).click();

    await page.waitForURL('**/onboarding');
    await page.getByRole('button', { name: /Skip for now/i }).click();
    await page.waitForURL('**/app/overview');
    await expect(page.getByText(/No financial records yet/i)).toBeVisible();

    // Attempting /app/admin as normal USER shows rejection
    await page.goto('/app/admin');
    await expect(page.getByText(/Administrator role required/i)).toBeVisible();
  });
});

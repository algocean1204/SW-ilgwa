/**
 * Playwright로 전 라우트·버튼을 누른다. 콘솔 에러·네트워크 404·ErrorBoundary 0건이 목표다.
 */
import { chromium } from 'playwright';

const BASE = process.env.BASE_URL ?? 'http://127.0.0.1:4174/SW-ilgwa';
const ROUTES = [
  'home', 'learning', 'notes', 'calendar', 'report',
  'settings', 'tutor', 'explore', 'pricing',
];

const IGNORE_PATTERNS = [
  /favicon/i,
  /mockServiceWorker/i,
  /mswjs\.io/i,
  /Found an issue/i,
  /Documentation:/i,
  /Worker script URL/i,
  /Worker scope/i,
  /Client ID:/i,
  /\[MSW\]/i,
  /Request \{/i,
  /Handler:/i,
  /Response \{/i,
];

function shouldIgnore(text) {
  return IGNORE_PATTERNS.some((p) => p.test(text));
}

async function login(page) {
  await page.goto(`${BASE}/?route=auth`);
  await page.waitForTimeout(800);
  const devBtn = page.getByRole('button', { name: /데모|dev|로그인/i }).first();
  if (await devBtn.isVisible().catch(() => false)) {
    await devBtn.click();
    await page.waitForTimeout(1200);
  }
}

async function clickVisibleButtons(page, maxClicks = 30, skipPurchase = false) {
  const buttons = page.locator('button:visible');
  const count = Math.min(await buttons.count(), maxClicks);
  for (let i = 0; i < count; i++) {
    try {
      const btn = buttons.nth(i);
      const label = (await btn.textContent())?.trim() ?? '';
      if (skipPurchase && /구매|시작하기|Premium|Pro 시작/i.test(label)) continue;
      if (await btn.isEnabled()) {
        await btn.click({ timeout: 2000 });
        await page.waitForTimeout(300);
      }
    } catch {
      // 페이지 이동이나 오버레이로 클릭이 막히면 넘어간다
    }
  }
}

async function main() {
  const consoleErrors = [];
  const network404 = [];
  const pageErrors = [];

  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext();
  const page = await context.newPage();

  page.on('console', (msg) => {
    if (msg.type() === 'error' && !shouldIgnore(msg.text())) {
      consoleErrors.push(`[${msg.type()}] ${msg.text()}`);
    }
  });
  page.on('pageerror', (err) => pageErrors.push(err.message));
  page.on('response', (res) => {
    if (res.status() === 404) {
      const url = res.url();
      if (!url.includes('favicon')) network404.push(url);
    }
  });

  await login(page);

  for (const route of ROUTES) {
    await page.goto(`${BASE}/?route=${route}`);
    await page.waitForTimeout(1500);

    const errorBoundary = page.getByText(/문제가 발생했|오류가 발생|ErrorBoundary/i);
    if (await errorBoundary.isVisible().catch(() => false)) {
      pageErrors.push(`ErrorBoundary visible on route=${route}`);
    }

    if (route === 'pricing') {
      const coinTab = page.getByRole('button', { name: '코인' });
      if (await coinTab.isVisible()) await coinTab.click();
      await page.waitForTimeout(800);
    }

    if (route === 'settings') {
      for (const tab of ['알림', '텔레그램 연동', '결제 · 구독', '개인정보']) {
        const tabBtn = page.getByRole('button', { name: tab });
        if (await tabBtn.isVisible()) {
          await tabBtn.click();
          await page.waitForTimeout(600);
        }
      }
    }

    await clickVisibleButtons(page, route === 'pricing' ? 15 : 20, route === 'pricing');

    if (route === 'pricing') {
      // 코인 구매 버튼을 한 번 눌러 데모 결제 리다이렉트 경로를 확인한다
      const buyBtn = page.getByRole('button', { name: /구매하기/i }).first();
      if (await buyBtn.isVisible().catch(() => false)) {
        await buyBtn.click();
        await page.waitForURL(/route=paymentCallback/, { timeout: 8000 }).catch(() => {});
        await page.waitForTimeout(1000);
        await page.goto(`${BASE}/?route=pricing`);
        await page.waitForTimeout(800);
      }
    }
  }

  await browser.close();

  const uniqueConsole = [...new Set(consoleErrors)];
  const unique404 = [...new Set(network404)];
  const uniquePage = [...new Set(pageErrors)];

  console.log('\n=== Playwright Route Verification ===');
  console.log(`Base URL: ${BASE}`);
  console.log(`Console errors: ${uniqueConsole.length}`);
  uniqueConsole.forEach((e) => console.log('  ', e));
  console.log(`Network 404: ${unique404.length}`);
  unique404.forEach((u) => console.log('  ', u));
  console.log(`Page/Boundary errors: ${uniquePage.length}`);
  uniquePage.forEach((e) => console.log('  ', e));

  const total = uniqueConsole.length + unique404.length + uniquePage.length;
  if (total > 0) process.exit(1);
  console.log('\n✓ All checks passed (0 errors)');
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
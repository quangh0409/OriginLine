// Tiện ích chung cho nhóm kịch bản D (bài viết, ảnh/video, vinh danh) — bấm thật trên bản production :3000
// với backend thật :8088 và Keycloak :8081. Không đi tắt bằng password grant.
import { chromium, devices } from "@playwright/test";
import fs from "node:fs";

export const BASE = "http://localhost:3000";
export const SHOTS = ".playwright-mcp/nt-d";
fs.mkdirSync(SHOTS, { recursive: true });

export const DESKTOP = { viewport: { width: 1280, height: 900 } };
export const PIXEL5 = { ...devices["Pixel 5"], viewport: { width: 393, height: 851 } };

/** Mở trình duyệt + context, gắn đủ ba lối nghe: pageerror, console error, API. */
export async function open(label, ctxOpts = DESKTOP, extra = {}) {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({
    locale: "vi-VN",
    timezoneId: "Asia/Ho_Chi_Minh",
    ...ctxOpts,
    ...extra,
  });
  const page = await context.newPage();
  const log = { pageErrors: [], consoleErrors: [], api: [], bad: [] };
  page.on("pageerror", (e) => {
    log.pageErrors.push(String(e));
    console.log(`[${label}] PAGEERROR ${e}`);
  });
  page.on("console", (m) => {
    if (m.type() === "error") {
      log.consoleErrors.push(m.text());
      console.log(`[${label}] CONSOLE.ERROR ${m.text().slice(0, 300)}`);
    }
  });
  page.on("response", async (r) => {
    const u = r.url();
    if (!u.includes("/api/v1/")) return;
    const path = u.replace(/^https?:\/\/[^/]+/, "");
    const line = `${r.status()} ${r.request().method()} ${path}`;
    log.api.push(line);
    if (r.status() >= 400) {
      let body = "";
      try { body = (await r.text()).slice(0, 400); } catch {}
      log.bad.push(`${line} ${body}`);
      console.log(`[${label}] API ${line} ${body}`);
    } else {
      console.log(`[${label}] API ${line}`);
    }
  });
  return { browser, context, page, log };
}

/** Đăng nhập như người dùng: bấm "Đăng nhập" ở thanh đầu trang → Keycloak → quay về. */
export async function login(page, username, password = "giapha123", startPath = "/vi") {
  await page.goto(BASE + startPath);
  const btn = page.getByTestId("login-button");
  // Đợi thanh đầu trang hydrate xong — kiểm isVisible ngay lúc tải là đua với React.
  await page.waitForLoadState("networkidle").catch(() => {});
  await btn.or(page.getByTestId("more-menu-trigger")).first().waitFor({ timeout: 30_000 }).catch(() => {});
  if (await btn.isVisible().catch(() => false)) {
    await btn.click();
  } else {
    // Điện thoại: nút có thể nằm trong menu "Thêm".
    await page.getByTestId("more-menu-trigger").click();
    await page.getByTestId("login-button").click();
  }
  await page.waitForURL(/:8081\/realms\/giapha\/protocol\/openid-connect\/auth/, { timeout: 60_000 });
  await page.locator("#username").fill(username);
  await page.locator("#password").fill(password);
  await page.locator('[name="login"]').first().click();
  await page.waitForURL(/localhost:3000/, { timeout: 60_000 });
  await page.getByTestId("account-button").waitFor({ timeout: 60_000 }).catch(async () => {
    // Pixel 5: tên tài khoản có thể rút vào menu — chỉ cần hết nút đăng nhập.
  });
}

export async function shot(page, name) {
  await page.screenshot({ path: `${SHOTS}/${name}.png`, fullPage: false });
}

export function summary(label, log) {
  console.log(`\n==== ${label}: pageErrors=${log.pageErrors.length} consoleErrors=${log.consoleErrors.length} api=${log.api.length} bad=${log.bad.length}`);
  for (const b of log.bad) console.log(`  BAD ${b}`);
  for (const e of log.pageErrors) console.log(`  PAGEERR ${e}`);
}

// Tiện ích chung cho các script nghiệm thu nhóm A (khách · lối vào · đăng ký).
// Chạy trên hệ THẬT: FE 3000 (next start), BE 8088, Keycloak 8081.
import { chromium, devices } from "@playwright/test";
import fs from "node:fs";

export const BASE = process.env.NT_BASE ?? "http://localhost:3000";
export const SHOTS = ".playwright-mcp/nt-a";
fs.mkdirSync(SHOTS, { recursive: true });

export const VIEWPORTS = {
  desktop: { viewport: { width: 1280, height: 900 } },
  pixel5: { ...devices["Pixel 5"] },
};

/** Mở một context mới (như cửa sổ ẩn danh) và gắn bộ ghi log FE + API. */
export async function openSession(kind, label) {
  const browser = await chromium.launch();
  const context = await browser.newContext({
    ...VIEWPORTS[kind],
    locale: "vi-VN",
    timezoneId: "Asia/Ho_Chi_Minh",
  });
  const page = await context.newPage();
  const log = { api: [], pageErrors: [], consoleErrors: [] };
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
    const url = r.url();
    if (!url.includes("/api/v1/")) return;
    const path = url.replace(/^https?:\/\/[^/]+/, "");
    const line = `${r.status()} ${r.request().method()} ${path}`;
    let body = "";
    if (r.status() >= 400) {
      try {
        body = (await r.text()).slice(0, 400);
      } catch {
        /* thân đã bị giải phóng */
      }
    }
    log.api.push({ status: r.status(), method: r.request().method(), path, body });
    console.log(`[${label}] API ${line}${body ? `  ${body}` : ""}`);
  });
  return { browser, context, page, log };
}

export async function shot(page, name) {
  const file = `${SHOTS}/${name}.png`;
  await page.screenshot({ path: file, fullPage: true });
  console.log(`  ảnh: ${file}`);
}

export function stamp() {
  return new Date().toISOString();
}

/** Đăng nhập như người dùng: bấm nút trên thanh đầu trang → Keycloak → quay về. */
export async function loginViaHeader(page, username, password) {
  await page.getByTestId("login-button").first().click();
  await page.waitForURL(/:8081\/realms\/giapha\//, { timeout: 60_000 });
  await page.locator("#username").fill(username);
  await page.locator("#password").fill(password);
  await page.locator("[name=login], #kc-login, input[type=submit]").first().click();
  await page.waitForURL(/localhost:3000/, { timeout: 60_000 });
  await page.getByTestId("account-button").first().waitFor({ timeout: 60_000 });
}

export function summarize(label, log) {
  const bad = log.api.filter((a) => a.status >= 400);
  console.log(`\n=== TÓM TẮT ${label} ===`);
  console.log(`pageerror: ${log.pageErrors.length}  console.error: ${log.consoleErrors.length}`);
  console.log(`API >= 400: ${bad.length}`);
  for (const a of bad) console.log(`   ${a.status} ${a.method} ${a.path}`);
}

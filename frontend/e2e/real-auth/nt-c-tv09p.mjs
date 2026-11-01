// NT-TV-09 (ca cho phép) — cần hồ sơ trình duyệt THẬT (không ẩn danh): Chrome tắt Push API trong ngữ cảnh ẩn danh.
import { chromium } from "@playwright/test";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { login, shot, BASE } from "./nt-c-lib.mjs";
const dir = fs.mkdtempSync(path.join(os.tmpdir(), "nt-c-push-"));
const ctx = await chromium.launchPersistentContext(dir, { channel: "chromium", headless: true, viewport: { width: 1280, height: 900 }, locale: "vi-VN", permissions: ["notifications"] });
const page = ctx.pages()[0] ?? (await ctx.newPage());
page.on("pageerror", (e) => console.log("PAGEERROR", String(e)));
page.on("console", (m) => m.type() === "error" && console.log("CONSOLE.ERROR", m.text().slice(0, 300)));
page.on("response", async (r) => {
  if (r.status() >= 400) console.log("HTTP", r.status(), r.request().method(), r.url(), r.url().includes("/api/v1/") ? (await r.text().catch(() => "")).slice(0, 300) : "");
  else if (r.url().includes("/api/v1/push") || r.url().includes("/api/v1/webpush")) console.log("API", r.status(), r.request().method(), r.url());
});
try {
  await login(page, "thanhvien");
  await page.goto(BASE + "/vi/settings");
  await page.waitForTimeout(4000);
  const push = page.getByRole("switch", { name: /Thông báo đẩy/ }).first();
  console.log("checked before:", await push.getAttribute("aria-checked"));
  await push.click();
  await page.waitForTimeout(8000);
  await shot(page, "tv09-grant-persistent-after-toggle");
  console.log("checked after:", await push.getAttribute("aria-checked").catch(() => "(gone)"));
  const main = await page.locator("main").innerText();
  console.log("MAIN AFTER:", main.slice(-500).replace(/\n+/g, " | "));
  // Tắt lại — trả trạng thái như cũ.
  if ((await push.getAttribute("aria-checked")) === "true") {
    await push.click();
    await page.waitForTimeout(4000);
    console.log("checked after OFF:", await push.getAttribute("aria-checked"));
    console.log("MAIN AFTER OFF:", (await page.locator("main").innerText()).slice(-300).replace(/\n+/g, " | "));
  }
} finally { await ctx.close(); }

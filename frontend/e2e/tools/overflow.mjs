/**
 * Chẩn đoán tràn ngang trên điện thoại: in ra ĐÚNG những phần tử vượt quá bề
 * rộng khung nhìn, thay vì chỉ báo "trang cuộn ngang N pixel".
 *
 *   npm run dev:mock            # cổng 3100
 *   node e2e/tools/overflow.mjs "/tree"
 */
import { chromium, devices } from "@playwright/test";

const path = process.argv[2] ?? "/tree";
const browser = await chromium.launch();
const ctx = await browser.newContext({ ...devices["Pixel 5"], locale: "vi-VN", timezoneId: "Asia/Ho_Chi_Minh" });
await ctx.addInitScript(() => window.localStorage.setItem("giapha.dev-role", "member"));
const page = await ctx.newPage();
await page.goto("http://127.0.0.1:3100" + path, { waitUntil: "networkidle", timeout: 180000 });
await page.waitForTimeout(3000);

const report = await page.evaluate(() => {
  const docW = document.documentElement.clientWidth;
  const overflow = document.documentElement.scrollWidth - docW;
  const offenders = [];
  for (const el of document.querySelectorAll("*")) {
    const r = el.getBoundingClientRect();
    if (r.width === 0 && r.height === 0) continue;
    if (r.right > docW + 1 || r.left < -1) {
      offenders.push({
        tag: el.tagName.toLowerCase(),
        cls: (el.className || "").toString().slice(0, 110),
        left: Math.round(r.left),
        right: Math.round(r.right),
        w: Math.round(r.width),
        text: (el.textContent || "").trim().slice(0, 45),
      });
    }
  }
  return { docW, overflow, count: offenders.length, offenders: offenders.slice(0, 40) };
});
console.log(JSON.stringify(report, null, 1));
await browser.close();

/** In hình học dọc của trang phả đồ trên điện thoại (để chỉnh chiều cao canvas). */
import { chromium, devices } from "@playwright/test";
const browser = await chromium.launch();
const ctx = await browser.newContext({ ...devices["Pixel 5"], locale: "vi-VN", timezoneId: "Asia/Ho_Chi_Minh" });
await ctx.addInitScript(() => window.localStorage.setItem("giapha.dev-role", "member"));
const page = await ctx.newPage();
await page.goto("http://127.0.0.1:3100/tree", { waitUntil: "networkidle", timeout: 180000 });
await page.waitForSelector(".react-flow__node", { timeout: 60000 });
await page.waitForTimeout(2500);
console.log(JSON.stringify(await page.evaluate(() => {
  const r = (sel) => { const e = document.querySelector(sel); if (!e) return null; const b = e.getBoundingClientRect(); return { top: Math.round(b.top), bottom: Math.round(b.bottom), h: Math.round(b.height) }; };
  return {
    window: { w: window.innerWidth, h: window.innerHeight },
    scrollY: window.scrollY,
    docScrollH: document.documentElement.scrollHeight,
    header: r("header"),
    toolbar: r(".react-flow") ? r("[data-testid='tree-loaded-count']") : null,
    canvas: r(".react-flow"),
    nav: r("nav[aria-label]"),
    footer: r("footer"),
  };
}), null, 1));
await browser.close();

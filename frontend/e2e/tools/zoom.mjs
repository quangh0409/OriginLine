/**
 * In mức thu phóng mà phả đồ MỞ RA, kích thước thật của nút mở rộng nhánh và
 * số thẻ nằm trọn trong khung — dùng để chỉnh sàn `MIN_INITIAL_ZOOM` trong
 * src/components/tree/tree-canvas-inner.tsx.
 *
 *   npm run dev:mock            # cổng 3100
 *   node e2e/tools/zoom.mjs
 */
import { chromium, devices } from "@playwright/test";
const browser = await chromium.launch();
for (const [name, opts] of [["Pixel5", devices["Pixel 5"]], ["Desktop", { viewport: { width: 1440, height: 900 } }]]) {
  const ctx = await browser.newContext({ ...opts, locale: "vi-VN", timezoneId: "Asia/Ho_Chi_Minh" });
  await ctx.addInitScript(() => window.localStorage.setItem("giapha.dev-role", "member"));
  const page = await ctx.newPage();
  await page.goto("http://127.0.0.1:3100/tree", { waitUntil: "networkidle", timeout: 180000 });
  await page.waitForSelector(".react-flow__node", { timeout: 60000 });
  await page.waitForTimeout(2500);
  const style = await page.locator(".react-flow__viewport").getAttribute("style");
  const toggle = await page.evaluate(() => {
    const b = document.querySelector(".react-flow__node button");
    if (!b) return null;
    const r = b.getBoundingClientRect();
    return { w: +r.width.toFixed(1), h: +r.height.toFixed(1) };
  });
  const cardsVisible = await page.evaluate(() => {
    const canvas = document.querySelector(".react-flow")?.getBoundingClientRect();
    if (!canvas) return 0;
    const view = { left: Math.max(canvas.left,0), top: Math.max(canvas.top,0), right: Math.min(canvas.right, window.innerWidth), bottom: Math.min(canvas.bottom, window.innerHeight) };
    return [...document.querySelectorAll(".react-flow__node")].filter((n) => {
      const r = n.getBoundingClientRect();
      if (!r.width || !r.height) return false;
      const w = Math.max(0, Math.min(r.right, view.right) - Math.max(r.left, view.left));
      const h = Math.max(0, Math.min(r.bottom, view.bottom) - Math.max(r.top, view.top));
      return (w*h)/(r.width*r.height) >= 0.6;
    }).length;
  });
  console.log(name, "| transform:", style, "| toggle:", JSON.stringify(toggle), "| cardsVisible:", cardsVisible);
  await ctx.close();
}
await browser.close();

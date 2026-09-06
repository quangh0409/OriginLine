/**
 * In ra phần tử nào đang nằm ĐÈ lên nút mở rộng nhánh của từng thẻ nhân khẩu,
 * và diện tích các lớp phủ của React Flow chiếm bao nhiêu phần canvas.
 *
 *   npm run dev:mock
 *   node e2e/tools/overlays.mjs Pixel5|Desktop
 */
import { chromium, devices } from "@playwright/test";

const which = process.argv[2] ?? "Pixel5";
const opts = which === "Desktop" ? { viewport: { width: 1440, height: 900 } } : devices["Pixel 5"];
const browser = await chromium.launch();
const ctx = await browser.newContext({ ...opts, locale: "vi-VN", timezoneId: "Asia/Ho_Chi_Minh" });
await ctx.addInitScript(() => window.localStorage.setItem("giapha.dev-role", "member"));
const page = await ctx.newPage();
await page.goto("http://127.0.0.1:3100/tree", { waitUntil: "networkidle", timeout: 180000 });
await page.waitForSelector(".react-flow__node", { timeout: 60000 });
await page.waitForTimeout(2500);

const out = await page.evaluate(() => {
  const canvas = document.querySelector(".react-flow").getBoundingClientRect();
  const panels = [...document.querySelectorAll(".react-flow__panel, .ant-card")].map((p) => {
    const r = p.getBoundingClientRect();
    return {
      cls: p.className.toString().slice(0, 70),
      x: Math.round(r.x), y: Math.round(r.y), w: Math.round(r.width), h: Math.round(r.height),
      pctOfCanvas: +(((r.width * r.height) / (canvas.width * canvas.height)) * 100).toFixed(1),
    };
  });
  const blocked = [];
  for (const b of document.querySelectorAll(".react-flow__node button")) {
    const r = b.getBoundingClientRect();
    const hit = document.elementFromPoint(r.left + r.width / 2, r.top + r.height / 2);
    if (!(hit && b.contains(hit))) {
      blocked.push({
        node: b.closest(".react-flow__node")?.getAttribute("data-id"),
        blockedBy: hit ? (hit.closest("[class]")?.className.toString().slice(0, 80) ?? hit.tagName) : "none",
      });
    }
  }
  return { canvas: { w: Math.round(canvas.width), h: Math.round(canvas.height) }, panels, toggles: document.querySelectorAll(".react-flow__node button").length, blocked };
});
console.log(JSON.stringify(out, null, 1));
await browser.close();

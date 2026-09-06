/** In kích thước từng phần tử con của thanh đầu trang trên điện thoại. */
import { chromium, devices } from "@playwright/test";
const browser = await chromium.launch();
const ctx = await browser.newContext({ ...devices["Pixel 5"], locale: "vi-VN" });
await ctx.addInitScript(() => window.localStorage.setItem("giapha.dev-role", "member"));
const page = await ctx.newPage();
await page.goto("http://127.0.0.1:3100/tree", { waitUntil: "networkidle", timeout: 180000 });
await page.waitForTimeout(3000);
console.log(JSON.stringify(await page.evaluate(() => {
  const h = document.querySelector("header");
  const walk = (el, depth) => {
    const r = el.getBoundingClientRect();
    const out = [{ d: depth, tag: el.tagName.toLowerCase(), cls: el.className.toString().slice(0, 60), x: Math.round(r.x), y: Math.round(r.y), w: Math.round(r.width), h: Math.round(r.height), text: (el.textContent||"").trim().slice(0, 24) }];
    if (depth < 3) for (const c of el.children) out.push(...walk(c, depth + 1));
    return out;
  };
  return walk(h, 0);
}), null, 0));
await browser.close();

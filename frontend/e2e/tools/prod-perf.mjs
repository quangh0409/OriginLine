/**
 * Đo NFR-1 ("cây render dưới 2s cho nhánh hiển thị") trên BẢN PRODUCTION.
 *
 * Vì sao phải có tệp này: e2e/tree-performance.spec.ts chạy trên `next dev`,
 * nơi gói JS chưa nén, có HMR và React StrictMode dựng hai lần — con số đo
 * được ở đó là chặn trên của môi trường phát triển, không phải điều NFR-1
 * nói tới. Ở bản production, MSW bị tắt cứng (NODE_ENV === "production"),
 * nên cần một máy chủ API giả thật sự: e2e/tools/stub-api.mjs.
 *
 * Cách chạy:
 *   node e2e/tools/stub-api.mjs                                   # cổng 3200
 *   NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:3200 npm run build
 *   PORT=3300 npm run start
 *   node e2e/tools/prod-perf.mjs
 */
import { chromium, devices } from "@playwright/test";

const BASE = process.env.PERF_BASE_URL ?? "http://127.0.0.1:3300";
const RUNS = Number(process.env.PERF_RUNS ?? 5);

async function measure(contextOptions, label) {
  const browser = await chromium.launch();
  const samples = [];
  for (let i = 0; i < RUNS; i += 1) {
    const ctx = await browser.newContext({
      ...contextOptions,
      locale: "vi-VN",
      timezoneId: "Asia/Ho_Chi_Minh",
    });
    const page = await ctx.newPage();
    const started = Date.now();
    await page.goto(`${BASE}/tree?rootId=p-001`, { timeout: 120000 });
    await page.waitForSelector(".react-flow__node", { timeout: 120000 });
    const wallClockMs = Date.now() - started;
    const t = await page.evaluate(() => {
      const nav = performance.getEntriesByType("navigation")[0];
      return { now: performance.now(), dcl: nav.domContentLoadedEventEnd };
    });
    const cards = await page.locator(".react-flow__node").count();
    samples.push({ wallClockMs, renderMs: Math.round(t.now - t.dcl), cards });
    await ctx.close();
  }
  await browser.close();

  const med = (key) => {
    const v = samples.map((s) => s[key]).sort((a, b) => a - b);
    return v[Math.floor(v.length / 2)];
  };
  console.log(
    `${label}: wall-clock median ${med("wallClockMs")}ms (${samples
      .map((s) => s.wallClockMs)
      .join("/")}) · dcl->first-card median ${med("renderMs")}ms (${samples
      .map((s) => s.renderMs)
      .join("/")}) · ${samples[0].cards} cards`
  );
}

await measure({ viewport: { width: 1440, height: 900 } }, "desktop-chromium");
await measure({ ...devices["Pixel 5"] }, "mobile-chromium (Pixel 5)");

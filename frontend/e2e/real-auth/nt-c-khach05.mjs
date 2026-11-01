// NT-KHACH-05: khách rơi vào /kinship (qua ô lối tắt ở trang chủ) phải thấy lời mời đăng nhập, không phải skeleton câm.
import { open, shot, summary, BASE, DESKTOP, PIXEL5 } from "./nt-c-lib.mjs";
const dev = process.argv[2] ?? "desktop";
const { browser, page, log } = await open(`khach05-${dev}`, dev === "pixel5" ? PIXEL5 : DESKTOP);
try {
  await page.goto(BASE + "/vi");
  await page.waitForTimeout(2500);
  // Ô lối tắt "Tra danh xưng" ở trang chủ nay ẩn với khách (home-hero.tsx: hasAccount) — lối duy nhất còn lại là gõ URL / liên kết chia sẻ.
  console.log("ô lối tắt kinship ở trang chủ (khách):", await page.locator("a[href*='/kinship']").count());
  const t0 = Date.now();
  await page.goto(BASE + "/vi/kinship");
  for (const ms of [500, 2000, 5000, 10000]) {
    await page.waitForTimeout(ms - (Date.now() - t0) > 0 ? ms - (Date.now() - t0) : 0);
    const txt = (await page.locator("main").innerText()).replace(/\n+/g, " | ");
    console.log(`t+${ms}ms skeleton=${await page.locator("main .ant-skeleton").count()} :: ${txt.slice(0, 300)}`);
  }
  await shot(page, `khach05-${dev}`);
  console.log("có ô chọn người:", await page.locator("#kinship-from, #kinship-to").count());
  console.log("nút Đăng nhập trong main:", await page.locator("main").getByRole("button", { name: "Đăng nhập" }).count(), "link phả đồ:", await page.locator("main a", { hasText: "Xem phả đồ" }).count());
} finally { summary(`khach05-${dev}`, log); await browser.close(); }

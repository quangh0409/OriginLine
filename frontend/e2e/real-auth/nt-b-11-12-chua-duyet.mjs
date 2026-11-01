// NT-MOI-11 (chưa duyệt thì chưa viết bài) · NT-MOI-12 (chưa duyệt thì chưa gửi đính chính).
//   KIND=desktop|mobile NTB_ID=<tài khoản đang chờ duyệt> node e2e/real-auth/nt-b-11-12-chua-duyet.mjs
import { open, login, shot, mainText, APP } from "./nt-b-lib.mjs";
const KIND = process.env.KIND ?? "desktop";
const { browser, page, p } = await open(KIND, "NT-MOI-11/12-" + KIND);
await login(page, process.env.NTB_ID, process.env.NTB_PW ?? "NghiemThu#2026b", "/vi");
// NT-MOI-11: từ trang chủ bấm "Viết bài mới".
const nut = page.getByRole("link", { name: "Viết bài mới" }).filter({ visible: true }).first();
p("11 nút Viết bài mới hiện: " + (await nut.isVisible()));
await nut.click();
await page.waitForLoadState("networkidle");
await page.waitForTimeout(1500);
p("11 URL: " + page.url());
p("11 màn: " + (await mainText(page)).slice(0, 600));
p("11 lối đi: " + (await page.locator("main a").allInnerTexts()).map((s) => s.trim()).filter(Boolean).join(" | "));
await shot(page, `11-${KIND}-viet-bai`);
// NT-MOI-12: mở /correction-requests — người dùng tới đây từ thanh điều hướng "Yêu cầu đính chính".
await page.goto(APP + "/vi", { waitUntil: "networkidle" });
const lk = page.getByRole("link", { name: /đính chính/i }).filter({ visible: true }).first();
if (await lk.count()) { await lk.click(); } else {
  p("12 không thấy liên kết đính chính ngay trên đầu trang — thử ngăn 'Thêm'");
  const them = page.getByRole("button", { name: /Thêm/ }).filter({ visible: true }).first();
  if (await them.count()) { await them.click(); await page.waitForTimeout(600); }
  await page.getByRole("link", { name: /đính chính/i }).filter({ visible: true }).first().click();
}
await page.waitForURL(/correction-requests/, { timeout: 20000 });
await page.waitForLoadState("networkidle");
await page.waitForTimeout(1500);
p("12 màn: " + (await mainText(page)).slice(0, 700));
await shot(page, `12-${KIND}-dinh-chinh`);
await browser.close();

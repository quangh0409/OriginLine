// NT-MOI-13 — tài khoản đọc /nhan-dien/cho-duyet sau mỗi lần bị từ chối: quota từ
// GET /person-claims/mine, dòng "còn {n} lần", rồi bấm "Gửi lại đơn khác" xem nó dẫn tới đâu.
import { open, login, shot, mainText, APP } from "./nt-b-lib.mjs";
const KIND = process.env.KIND ?? "desktop";
const TAG = process.env.TAG ?? "x";
const { browser, page, p } = await open(KIND, "NT-MOI-13-" + TAG);
await login(page, process.env.NTB_ID, "NghiemThu#2026b", "/vi");
const mineP = page.waitForResponse((r) => r.url().includes("/person-claims/mine"), { timeout: 30000 });
await page.goto(APP + "/vi/nhan-dien/cho-duyet", { waitUntil: "networkidle" });
const mine = await (await mineP).json();
p("quota API: " + JSON.stringify(mine.quota ?? mine));
await page.waitForTimeout(1200);
const t = await mainText(page);
p("màn: " + t.slice(0, 900));
await shot(page, `13-${KIND}-${TAG}-cho-duyet`);
const nut = page.getByRole("link", { name: "Gửi lại đơn khác" }).or(page.getByRole("button", { name: "Gửi lại đơn khác" }));
p("có nút Gửi lại đơn khác: " + (await nut.count()));
if (await nut.count()) {
  await nut.first().click();
  await page.waitForLoadState("networkidle"); await page.waitForTimeout(1200);
  p("sau Gửi lại: " + page.url() + " · " + (await mainText(page)).slice(0, 300));
  await shot(page, `13-${KIND}-${TAG}-gui-lai`);
}
await browser.close();

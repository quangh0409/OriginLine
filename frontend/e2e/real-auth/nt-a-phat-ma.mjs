// Chuẩn bị phần B: đăng nhập `hoidong`, vào Quản lý → Phát mã bằng CHUỘT, phát một mã mời dòng họ.
// In mã ra một tệp trong scratchpad do biến NT_CODE_FILE chỉ định (KHÔNG in ra màn hình).
import fs from "node:fs";
import { openSession, shot, stamp, summarize, loginViaHeader, BASE } from "./nt-a-lib.mjs";

const out = process.env.NT_CODE_FILE;
if (!out) throw new Error("Thiếu NT_CODE_FILE");
const { browser, page, log } = await openSession("desktop", "hoidong");
try {
  console.log(`### ${stamp()} hoidong phát mã`);
  await page.goto(`${BASE}/vi`);
  await loginViaHeader(page, "hoidong", "giapha123");
  await page.getByRole("link", { name: /Quản lý thành viên/ }).first().click();
  await page.waitForURL(/quan-ly/, { timeout: 30_000 });
  await page.locator('a[href$="/quan-ly/phat-ma"]').first().click();
  await page.waitForURL(/phat-ma/, { timeout: 30_000 });
  await page.waitForLoadState("networkidle").catch(() => {});
  await page.getByLabel(/Ghi chú|Nhãn|Tên gợi nhớ/).first().fill(`NT-A ${Date.now()}`).catch(() => {});
  await page.getByRole("button", { name: /Phát mã/ }).first().click();
  const code = (await page.getByTestId("ma-mot-lan").first().innerText({ timeout: 30_000 })).trim();
  fs.writeFileSync(out, code);
  console.log(`  đã phát mã, độ dài ${code.length}`);
  await page.getByTestId("ma-mot-lan").first().evaluate((el) => (el.textContent = "••••••"));
  await shot(page, "hoidong-phat-ma");
} catch (e) {
  console.log("LỖI SCRIPT:", e);
  await shot(page, "hoidong-phat-ma-LOI");
} finally {
  summarize("hoidong", log);
  await browser.close();
}

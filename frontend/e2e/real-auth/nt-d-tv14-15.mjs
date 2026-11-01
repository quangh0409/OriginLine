// NT-TV-14 (thanhvien tự đề nghị vinh danh) → NT-TC-10 (hoidong duyệt, đọc cảnh báo chia sẻ)
// → NT-TV-15 (truongchi — BRANCH_HEAD cùng chi — KHÔNG thấy tên; chính chủ thấy tên).
// Phần "bật công tắc thứ sáu" nằm ở màn hồ sơ (không thuộc nhóm D) — chạy riêng bằng nt-d-tv15-toggle.mjs.
// Chạy: node e2e/real-auth/nt-d-tv14-15.mjs [desktop|pixel5]
import { open, login, shot, summary, BASE, DESKTOP, PIXEL5 } from "./nt-d-lib.mjs";
import fs from "node:fs";

const mode = process.argv[2] ?? "desktop";
const ctx = mode === "pixel5" ? PIXEL5 : DESKTOP;
const tag = `tv14-15-${mode}`;
const stamp = new Date().toISOString().slice(11, 19);
const TITLE = `Thạc sĩ Quản trị (${mode} ${stamp})`;
const out = { TITLE };
const logs = [];

function cardOf(page) {
  return page.locator("article, li, div.rounded-lg", { hasText: TITLE }).last();
}

// ---------- 1) thanhvien đề nghị ----------
let s = await open(`${tag}-tv`, ctx); logs.push(s.log);
try {
  const page = s.page;
  await login(page, "thanhvien");
  await page.goto(BASE + "/vi/vinh-danh");
  await page.getByRole("button", { name: "Đề nghị vinh danh" }).click();
  const dialog = page.getByRole("dialog");
  await dialog.waitFor({ timeout: 15000 });
  await page.waitForTimeout(1500);
  out.dongDau = (await dialog.innerText()).split("\n").slice(0, 3).join(" | ");
  out.coONguoiKhac = await dialog.getByText("Người được vinh danh (nếu không phải chính ông/bà)").count();
  await dialog.locator("#honour-kind").click({ force: true });
  await page.locator(".ant-select-dropdown").getByText("Đỗ đạt", { exact: true }).click();
  await dialog.locator("#honour-title").fill(TITLE);
  await dialog.locator("#honour-year").fill("2019");
  await dialog.locator("#honour-issuer").fill("Đại học Kinh tế Quốc dân");
  await dialog.locator("#honour-description").fill("Tốt nghiệp loại giỏi.");
  await shot(page, `${tag}-01-bieu-mau`);
  const created = page.waitForResponse((r) => r.url().endsWith("/api/v1/honours") && r.request().method() === "POST", { timeout: 20000 });
  await dialog.getByRole("button", { name: "Gửi đề nghị" }).click();
  const cr = await created;
  out.taoStatus = cr.status();
  out.honourId = (await cr.json().catch(() => ({}))).id;
  await page.waitForTimeout(3000);
  out.dialogConMo = await page.getByRole("dialog").isVisible().catch(() => false);
  out.manSauGui = (await page.locator("main").innerText()).slice(0, 900);
  out.thongBaoXacNhan = await page.locator(".ant-message, .ant-notification, [role=status], .ant-alert-success").allInnerTexts();
  await shot(page, `${tag}-02-sau-gui`);
} catch (e) { console.log("ERR tv", e); await shot(s.page, `${tag}-zz-tv`); }
finally { summary(`${tag}-tv`, s.log); await s.browser.close(); }

// ---------- 2) hoidong duyệt (NT-TC-10) ----------
s = await open(`${tag}-hd`, ctx); logs.push(s.log);
try {
  const page = s.page;
  await login(page, "hoidong");
  await page.goto(BASE + "/vi/vinh-danh");
  await page.getByText(TITLE).first().waitFor({ timeout: 30000 });
  out.canhBaoTruocDuyet = await page.getByText(/Duyệt xong không tự mở chia sẻ/).allInnerTexts();
  await shot(page, `${tag}-03-hoidong-hang-cho`);
  const card = page.locator(":is(article,li,section > div)", { hasText: TITLE }).filter({ has: page.getByRole("button", { name: "Duyệt", exact: true }) }).last();
  const rv = page.waitForResponse((r) => r.url().includes(`/honours/`) && r.url().endsWith("/review"), { timeout: 20000 });
  await card.getByRole("button", { name: "Duyệt", exact: true }).click();
  out.duyetStatus = (await rv).status();
  await page.waitForTimeout(2500);
  out.hoidongSauDuyet = (await page.locator("main").innerText()).slice(0, 900);
  await shot(page, `${tag}-04-hoidong-sau-duyet`);
} catch (e) { console.log("ERR hd", e); await shot(s.page, `${tag}-zz-hd`); }
finally { summary(`${tag}-hd`, s.log); await s.browser.close(); }

// ---------- 3) truongchi (BRANCH_HEAD, phạm vi CHỨA thanhvien) ----------
s = await open(`${tag}-tc`, ctx); logs.push(s.log);
try {
  const page = s.page;
  await login(page, "truongchi");
  await page.goto(BASE + "/vi/vinh-danh");
  await page.waitForTimeout(4000);
  const t = await page.locator("main").innerText();
  const i = t.indexOf(TITLE);
  out.truongchiThay = i >= 0 ? t.slice(Math.max(0, i - 200), i + 300) : "(không có mục này trong danh sách)";
  await shot(page, `${tag}-05-truongchi`);
} catch (e) { console.log("ERR tc", e); await shot(s.page, `${tag}-zz-tc`); }
finally { summary(`${tag}-tc`, s.log); await s.browser.close(); }

// ---------- 4) thanhvien (chính chủ) ----------
s = await open(`${tag}-tv2`, ctx); logs.push(s.log);
try {
  const page = s.page;
  await login(page, "thanhvien");
  await page.goto(BASE + "/vi/vinh-danh");
  await page.waitForTimeout(4000);
  const t = await page.locator("main").innerText();
  const i = t.indexOf(TITLE);
  out.chinhChuThay = i >= 0 ? t.slice(Math.max(0, i - 200), i + 300) : "(không có)";
  await shot(page, `${tag}-06-chinh-chu`);
} catch (e) { console.log("ERR tv2", e); await shot(s.page, `${tag}-zz-tv2`); }
finally { summary(`${tag}-tv2`, s.log); await s.browser.close(); }

console.log(JSON.stringify(out, null, 2));
fs.writeFileSync(`.playwright-mcp/nt-d/${tag}.json`, JSON.stringify({ out, logs }, null, 2));

// NT-TV-15 phần thêm: chính chủ (thanhvien) bật công tắc thứ sáu "Vinh danh" sang "Cả họ xem" ⇒
// truongchi (BRANCH_HEAD) nay thấy vinh danh KÈM TÊN. Cuối kịch bản đặt lại "Riêng tư" để không
// làm lệch các kịch bản khác (NT-TV-04 danh bạ) đang dùng chung dữ liệu.
// Đọc TITLE từ .playwright-mcp/nt-d/tv14-15-<mode>.json.   Chạy: node e2e/real-auth/nt-d-tv15-toggle.mjs [desktop|pixel5]
import { open, login, shot, summary, BASE, DESKTOP, PIXEL5 } from "./nt-d-lib.mjs";
import fs from "node:fs";

const mode = process.argv[2] ?? "desktop";
const ctx = mode === "pixel5" ? PIXEL5 : DESKTOP;
const tag = `tv15-toggle-${mode}`;
const TITLE = JSON.parse(fs.readFileSync(`.playwright-mcp/nt-d/tv14-15-${mode}.json`, "utf8")).out.TITLE;
const out = { TITLE };
const logs = [];

async function setHonourScope(page, label) {
  const group = page.locator("#privacy-group-honour-label").locator("xpath=..");
  await group.getByText(label, { exact: true }).click();
  const saved = page.waitForResponse((r) => /\/api\/v1\/persons\/[^/?]+$/.test(r.url()) && r.request().method() === "PATCH", { timeout: 20000 });
  await page.getByRole("button", { name: "Lưu mức chia sẻ" }).click();
  const r = await saved;
  await page.waitForTimeout(1500);
  return `${r.request().method()} ${r.status()}`;
}

let s = await open(`${tag}-tv`, ctx); logs.push(s.log);
let personId;
try {
  const page = s.page;
  const meResp = page.waitForResponse((r) => r.url().endsWith("/api/v1/me") && r.status() === 200, { timeout: 60000 });
  await login(page, "thanhvien");
  personId = (await (await meResp).json()).personId;
  await page.goto(BASE + `/vi/persons/${personId}`);
  await page.locator("#privacy-group-honour-label").waitFor({ timeout: 30000 });
  out.soCongTac = await page.locator('[id^="privacy-group-"][id$="-label"]').count();
  out.congTacCuoi = await page.locator('[id^="privacy-group-"][id$="-label"]').last().innerText();
  out.batCaHo = await setHonourScope(page, "Cả họ xem");
  out.trangThaiLuu = await page.locator('p[role="status"]').allInnerTexts();
  await page.locator("#privacy-group-honour-label").scrollIntoViewIfNeeded();
  await shot(page, `${tag}-01-bat-ca-ho`);
} catch (e) { console.log("ERR tv", e); await shot(s.page, `${tag}-zz-tv`); }
finally { summary(`${tag}-tv`, s.log); await s.browser.close(); }

s = await open(`${tag}-tc`, ctx); logs.push(s.log);
try {
  const page = s.page;
  await login(page, "truongchi");
  await page.goto(BASE + "/vi/vinh-danh");
  await page.getByText(TITLE).first().waitFor({ timeout: 30000 }).catch(() => {});
  const t = await page.locator("main").innerText();
  const i = t.indexOf(TITLE);
  out.truongchiSauKhiBat = i >= 0 ? t.slice(Math.max(0, i - 60), i + 200) : "(không có)";
  await shot(page, `${tag}-02-truongchi-thay`);
} catch (e) { console.log("ERR tc", e); await shot(s.page, `${tag}-zz-tc`); }
finally { summary(`${tag}-tc`, s.log); await s.browser.close(); }

s = await open(`${tag}-tv2`, ctx); logs.push(s.log);
try {
  const page = s.page;
  await login(page, "thanhvien");
  await page.goto(BASE + `/vi/persons/${personId}`);
  await page.locator("#privacy-group-honour-label").waitFor({ timeout: 30000 });
  out.datLaiRiengTu = await setHonourScope(page, "Riêng tư");
} catch (e) { console.log("ERR tv2", e); await shot(s.page, `${tag}-zz-tv2`); }
finally { summary(`${tag}-tv2`, s.log); await s.browser.close(); }

console.log(JSON.stringify(out, null, 2));
fs.writeFileSync(`.playwright-mcp/nt-d/${tag}.json`, JSON.stringify({ out, logs }, null, 2));

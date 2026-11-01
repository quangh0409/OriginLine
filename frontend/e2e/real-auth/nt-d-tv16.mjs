// NT-TV-16 — truongchi mở một sự kiện "Giỗ thường", đổi ngày âm khác ngày mất trong hồ sơ, bấm Lưu:
// máy chủ phải từ chối với đúng câu thiết kế, và ngày giỗ giữ nguyên sau khi tải lại.
// (Mã màn sự kiện KHÔNG thuộc nhóm D — kịch bản được giao cho nhóm D nên bấm và báo cáo.)
// Chạy: node e2e/real-auth/nt-d-tv16.mjs [desktop|pixel5] [truongchi|hoidong]
import { open, login, shot, summary, BASE, DESKTOP, PIXEL5 } from "./nt-d-lib.mjs";
import fs from "node:fs";
const mode = process.argv[2] ?? "desktop";
const who = process.argv[3] ?? "truongchi";
const tag = `tv16-${mode}-${who}`;
const out = {};
const { browser, page, log } = await open(tag, mode === "pixel5" ? PIXEL5 : DESKTOP);
try {
  await login(page, who);
  await page.goto(BASE + "/vi/events");
  await page.getByText("Giỗ thường").first().waitFor({ timeout: 45000 });
  // Thẻ sự kiện đầu tiên mang nhãn "Giỗ thường" VÀ có lối "Sửa".
  const cards = page.locator("article, li").filter({ hasText: "Giỗ thường" }).filter({ has: page.getByRole("link", { name: "Sửa" }) });
  out.soTheGioThuong = await cards.count();
  out.tatCaThe = (await cards.allInnerTexts()).map((x) => x.replace(/\n+/g, " | ").slice(0, 160));
  // Chọn sự kiện thuộc chi của truongchi (Chi Bính: Ngành Thứ/Ngành Út) — thẻ ở chi khác cũng có
  // lối "Sửa" nhưng máy chủ trả 403 (ghi riêng trong báo cáo, không thuộc kịch bản này).
  const inScope = cards.filter({ hasText: /Chi Bính|Ngành Thứ|Ngành Út/ });
  const card = (await inScope.count()) ? inScope.first() : cards.first();
  out.the = (await card.innerText()).replace(/\n+/g, " | ").slice(0, 300);
  await card.getByRole("link", { name: "Sửa" }).click();
  const day = page.getByLabel("Ngày (âm)");
  await day.waitFor({ timeout: 30000 });
  await page.waitForTimeout(1500);
  out.ngayCu = await day.inputValue();
  out.thangCu = await page.getByLabel("Tháng (âm)").inputValue();
  const newDay = String(((Number(out.ngayCu) || 1) % 28) + 1);
  await day.fill(newDay);
  await shot(page, `${tag}-01-doi-ngay`);
  const saved = page.waitForResponse((r) => /\/api\/v1\/events\/[^/]+$/.test(r.url()) && ["PATCH", "PUT"].includes(r.request().method()), { timeout: 30000 });
  await page.getByRole("button", { name: "Lưu", exact: true }).click();
  const r = await saved;
  out.luu = `${r.request().method()} ${r.status()} ${(await r.text()).slice(0, 300)}`;
  await page.waitForTimeout(2000);
  out.thongBao = await page.getByText(/Ngày giỗ lấy từ hồ sơ nhân khẩu/).allInnerTexts();
  await shot(page, `${tag}-02-sau-luu`);
  await page.reload();
  await page.getByLabel("Ngày (âm)").waitFor({ timeout: 30000 });
  await page.waitForTimeout(1500);
  out.ngaySauTaiLai = await page.getByLabel("Ngày (âm)").inputValue();
} catch (e) { console.log("ERR", e); await shot(page, `${tag}-zz`); }
finally { summary(tag, log); console.log(JSON.stringify(out, null, 2)); fs.writeFileSync(`.playwright-mcp/nt-d/${tag}.json`, JSON.stringify({ out, log }, null, 2)); await browser.close(); }

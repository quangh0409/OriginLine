// NT-TC-15 phần 2 (brief: "điền vài dòng thật bằng openpyxl, kiểm validation + commit + rollback"):
//  (1) tải lên tệp có MỘT dòng lỗi chặn (Mã cha trỏ vào mã không tồn tại) → màn đối soát phải chỉ
//      đúng dòng, nút duyệt bị khoá;  (2) "Tải lại tệp đã sửa" với tệp sạch → xác nhận mục cần xem
//      lại (nếu có) → duyệt ghi vào phả → thấy "Đã ghi";  (3) gỡ toàn bộ lô → thấy "Đã gỡ".
import { open, login, shot, mainText, verdict, saveState, APP } from "./nt-e-lib.mjs";
const SP = "C:/Users/ADMIN/AppData/Local/Temp/claude/D--OriginLine/80769d08-6e1e-44f3-9e46-dad5d2aecb84/scratchpad/";
const kind = process.env.NTE_KIND ?? "desktop";
const T = `09-${kind}`;
const { browser, page, p, log } = await open(kind, "tải lên " + kind);
page.setDefaultTimeout(45000);

async function taiLen(file, tag) {
  await page.goto(APP + "/vi/nhap-lieu", { waitUntil: "networkidle" });
  await page.getByRole("link", { name: /Tải tệp đã điền lên/ }).first().click();
  await page.waitForURL(/tai-len/); await page.waitForLoadState("networkidle");
  const openWarn = await page.getByTestId("open-batch-warning").count();
  if (openWarn) p(`[${tag}] màn tải lên báo còn lô đang dở: ` + (await page.getByTestId("open-batch-warning").innerText()).replace(/\s+/g, " ").slice(0, 200));
  await page.getByRole("radio", { name: /Chi Bính\s+ho_nguyen_dinh\.chi_binh$/ }).check().catch(async () => {
    await page.locator("label").filter({ hasText: /^Chi Bính\s*ho_nguyen_dinh\.chi_binh\b(?!\.)/ }).first().click();
  });
  await page.setInputFiles("#import-file-input", SP + file);
  await shot(page, `${T}-${tag}-truoc-khi-gui`);
  const rP = page.waitForResponse((r) => /\/import\/batches(\?|$)/.test(r.url()) && r.request().method() === "POST");
  await page.getByRole("button", { name: /Tải lên và đối soát/ }).click();
  const r = await rP;
  p(`[${tag}] POST /import/batches -> ${r.status()} ${r.status() >= 400 ? (await r.text()).slice(0, 300) : ""}`);
  await page.waitForURL(/nhap-lieu\/[0-9a-f-]{36}/, { timeout: 60000 });
  await page.waitForLoadState("networkidle"); await page.waitForTimeout(2500);
  const id = page.url().match(/nhap-lieu\/([0-9a-f-]{36})/)[1];
  await shot(page, `${T}-${tag}-doi-soat`);
  return id;
}

try {
  await login(page, "truongchi");
  // (1) tệp có lỗi chặn
  const lo1 = await taiLen("nte-dien-loi.xlsx", "1-loi");
  const t1 = await mainText(page);
  p(`[1] lô ${lo1}: ` + t1.slice(0, 1200));
  const nut = page.getByRole("button", { name: /Duyệt và ghi|chưa duyệt được/ }).first();
  p(`[1] nút duyệt: "${(await nut.count()) ? (await nut.innerText()).replace(/\s+/g, " ") : "(không có)"}" disabled=${(await nut.count()) ? await nut.isDisabled() : "-"}`);

  // (2) tệp sạch
  const lo2 = await taiLen("nte-dien.xlsx", "2-sach");
  saveState({ importBatch: lo2 });
  p(`[2] lô ${lo2}: ` + (await mainText(page)).slice(0, 900));
  const ack = page.getByRole("checkbox", { name: /Tôi đã xem (xong|lại)/ });
  if (await ack.count()) {
    const aP = page.waitForResponse((r) => r.url().includes("/acknowledge-warnings"), { timeout: 15000 }).catch(() => null);
    await ack.first().check(); const a = await aP;
    await page.waitForTimeout(1500);
    p(`[2] đã tích "Tôi đã xem xong" -> ${a ? a.status() : "không gọi máy chủ"}`);
  }
  const dupLink = page.getByRole("link", { name: /cặp nghi trùng/ });
  if (await dupLink.count()) p("[2] CÓ cặp nghi trùng: " + await dupLink.first().innerText());
  await shot(page, `${T}-2-sau-xac-nhan`);
  const duyet = page.getByRole("button", { name: /Duyệt và ghi/ }).first();
  p(`[2] nút duyệt: "${(await duyet.innerText()).replace(/\s+/g, " ")}" disabled=${await duyet.isDisabled()}`);
  if (!(await duyet.isDisabled())) {
    await duyet.click();
    const dlg = page.getByRole("dialog"); await dlg.waitFor();
    p("[2] hộp thoại: " + (await dlg.innerText()).replace(/\s+/g, " ").slice(0, 300));
    const cP = page.waitForResponse((r) => r.url().includes(`/import/batches/${lo2}/commit`) && r.request().method() === "POST");
    await dlg.getByRole("button", { name: /Ghi vào phả/ }).click();
    const c = await cP; p(`[2] POST commit -> ${c.status()} ${c.status() >= 400 ? (await c.text()).slice(0, 300) : ""}`);
    await page.getByText(/Đã ghi \d+ người vào phả/).first().waitFor({ timeout: 120000 }).catch(() => {});
    await shot(page, `${T}-2-da-ghi`);
    p("[2] sau commit: " + (await mainText(page)).slice(0, 600));

    // (3) gỡ lô
    const go = page.getByRole("button", { name: /Gỡ toàn bộ lô này/ });
    if (await go.count()) {
      await go.click();
      const d2 = page.getByRole("dialog"); await d2.waitFor();
      await page.waitForTimeout(1500);
      p("[3] hộp thoại gỡ: " + (await d2.innerText()).replace(/\s+/g, " ").slice(0, 500));
      await shot(page, `${T}-3-hop-thoai-go`);
      const lyDo = d2.locator("textarea, input[type=text]").first();
      if (await lyDo.count()) await lyDo.fill("kiem thu nghiem thu nhom E, go lai ngay");
      const gP = page.waitForResponse((r) => r.url().includes(`/import/batches/${lo2}/rollback`) && r.request().method() === "POST");
      await d2.getByRole("button", { name: /Gỡ lô/ }).click();
      const g = await gP; p(`[3] POST rollback -> ${g.status()} ${g.status() >= 400 ? (await g.text()).slice(0, 300) : ""}`);
      await page.waitForTimeout(2500);
      await shot(page, `${T}-3-da-go`);
      p("[3] sau gỡ: " + (await mainText(page)).slice(0, 600));
    } else p("[3] KHÔNG THẤY nút gỡ lô");
  }
} catch (e) { p("LỖI SCRIPT: " + e.message); await shot(page, `${T}-loi`); p((await mainText(page)).slice(0, 800)); }
finally { verdict(log, p); await browser.close(); }

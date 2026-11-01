// NT-TC-15 phần 1: vào /nhap-lieu bằng lối vào thật ("Quản lý thành viên" → "Nhập liệu ban đầu"),
// đọc "Quy trình năm bước", bấm "Lấy mẫu Excel của chi tôi", tải mẫu của Chi Bính về đĩa.
import { open, login, shot, mainText, verdict, APP } from "./nt-e-lib.mjs";
import fs from "node:fs";
const kind = process.env.NTE_KIND ?? "desktop";
const OUT = process.env.NTE_XLSX ?? "C:/Users/ADMIN/AppData/Local/Temp/claude/D--OriginLine/80769d08-6e1e-44f3-9e46-dad5d2aecb84/scratchpad/nte-mau.xlsx";
const { browser, page, p, log } = await open(kind, "mẫu excel " + kind);
try {
  await login(page, "truongchi");
  if (kind === "desktop") await page.getByTestId("management-queue-link").click();
  else { await page.getByTestId("more-menu-trigger").click(); await page.getByRole("link", { name: /Quản lý thành viên/ }).filter({ visible: true }).first().click(); }
  await page.waitForURL(/\/quan-ly$/);
  await page.getByRole("link", { name: /Nhập liệu ban đầu/ }).click();
  await page.waitForURL(/\/nhap-lieu$/); await page.waitForLoadState("networkidle");
  await shot(page, `08-${kind}-nhap-lieu`);
  p("/nhap-lieu: " + (await mainText(page)).slice(0, 900));
  await page.getByRole("link", { name: /Lấy mẫu Excel của chi tôi/ }).or(page.getByRole("button", { name: /Lấy mẫu Excel của chi tôi/ })).first().click();
  await page.waitForURL(/nhap-lieu\/mau/); await page.waitForLoadState("networkidle");
  await page.waitForTimeout(800);
  await shot(page, `08-${kind}-mau`);
  p("/nhap-lieu/mau: " + (await mainText(page)).slice(0, 1500));
  const dl = page.waitForEvent("download", { timeout: 60000 });
  await page.getByRole("button", { name: /Tải mẫu của Chi Bính/ }).click();
  const d = await dl;
  p("tên tệp tải về: " + d.suggestedFilename());
  await d.saveAs(OUT);
  p("đã lưu " + OUT + " (" + fs.statSync(OUT).size + " byte)");
} catch (e) { p("LỖI SCRIPT: " + e.message); await shot(page, "08-loi"); }
finally { verdict(log, p); await browser.close(); }

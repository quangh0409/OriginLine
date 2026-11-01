// NT-TC-15 phần 3: mở lại lô đã ghi (tải lại trang như người dùng), đọc "Đã ghi", rồi gỡ toàn bộ lô.
import { open, login, shot, mainText, verdict, loadState, APP } from "./nt-e-lib.mjs";
const lo = process.env.NTE_BATCH ?? loadState().importBatch;
const { browser, page, p, log } = await open("desktop", "gỡ lô");
page.setDefaultTimeout(45000);
try {
  await login(page, "truongchi");
  await page.goto(APP + "/vi/nhap-lieu/" + lo, { waitUntil: "networkidle" });
  await page.waitForTimeout(2000);
  await shot(page, "10-lo-da-ghi");
  p("sau tải lại: " + (await mainText(page)).slice(0, 400));
  await page.getByRole("button", { name: /Gỡ toàn bộ lô này/ }).click();
  const d = page.getByRole("dialog"); await d.waitFor(); await page.waitForTimeout(1500);
  p("hộp thoại gỡ: " + (await d.innerText()).replace(/\s+/g, " ").slice(0, 500));
  await shot(page, "10-hop-thoai-go");
  const lyDo = d.locator("textarea, input[type=text]").first();
  if (await lyDo.count()) await lyDo.fill("kiem thu nghiem thu nhom E, go lai ngay");
  const gP = page.waitForResponse((r) => r.url().includes(`/rollback`) && r.request().method() === "POST");
  await d.getByRole("button", { name: /Gỡ lô/ }).click();
  const g = await gP; p(`POST rollback -> ${g.status()} ${g.status() >= 400 ? (await g.text()).slice(0, 300) : ""}`);
  await page.waitForTimeout(2500);
  p("toast: " + (await page.locator(".ant-message-notice").allInnerTexts()).join(" / "));
  await shot(page, "10-da-go");
  p("sau gỡ: " + (await mainText(page)).slice(0, 500));
} catch (e) { p("LỖI SCRIPT: " + e.message); await shot(page, "10-loi"); }
finally { verdict(log, p); await browser.close(); }

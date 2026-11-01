// NT-TC-02/03/04/05/06/07 + NT-HD-04: người duyệt mở hàng chờ bằng lối vào thật, tìm đơn theo số điện
// thoại người khai, rồi duyệt / từ chối qua hộp thoại. Env:
//   NTE_USER (người duyệt), NTE_KIND (desktop|mobile), NTE_ACTIONS = "sđt:approve|reject|look[:lý do]" ;-tách
import { open, login, shot, mainText, verdict, APP } from "./nt-e-lib.mjs";
const E = process.env;
const kind = E.NTE_KIND ?? "desktop";
const user = E.NTE_USER ?? "truongchi";
const { browser, page, p, log } = await open(kind, `duyệt ${user} ${kind}`);
const tag = `04-${user}-${kind}`;
async function openQueue() {
  await page.goto(APP + "/vi", { waitUntil: "networkidle" });
  if (kind === "desktop") await page.getByTestId("management-queue-link").click();
  else { await page.getByTestId("more-menu-trigger").click(); await page.getByRole("link", { name: /Quản lý thành viên/ }).filter({ visible: true }).first().click(); }
  await page.waitForURL(/\/quan-ly$/);
  await page.getByRole("link", { name: /Đơn xin vào phả/ }).click();
  await page.waitForURL(/don-gia-nhap/); await page.waitForLoadState("networkidle");
  await page.waitForTimeout(1000);
}
try {
  await login(page, user);
  const badge = await page.getByTestId("management-queue-link").getAttribute("aria-label").catch(() => "(không có trên khung này)");
  p("nhãn lối vào: " + badge);
  await openQueue();
  await shot(page, `${tag}-hang-cho`);
  const cards = page.locator('[data-testid="don-nhan-minh"], [data-testid="don-chua-co-trong-pha"]');
  p(`số thẻ đơn thấy: ${await cards.count()}; cụm tranh chấp: ${await page.getByTestId("cum-tranh-chap").count()}`);
  for (let i = 0; i < await cards.count(); i++) p("  thẻ: " + (await cards.nth(i).innerText()).replace(/\s+/g, " ").slice(0, 230));
  for (const a of (E.NTE_ACTIONS ?? "").split(";").filter(Boolean)) {
    const [phone, act, ...rest] = a.split(":");
    const reason = rest.join(":");
    const card = cards.filter({ hasText: phone }).first();
    if (!(await card.count())) { p(`[${phone}] KHÔNG THẤY đơn trong hàng chờ`); continue; }
    await card.scrollIntoViewIfNeeded();
    await shot(page, `${tag}-${phone}-the`);
    if (act === "look") continue;
    if (act === "approve") await card.getByRole("button", { name: /^(check )?Duyệt —/ }).click();
    else await card.getByRole("button", { name: /Từ chối/ }).click();
    const dlg = page.getByRole("dialog");
    await dlg.waitFor();
    await page.waitForTimeout(400);
    p(`[${phone}] hộp thoại: ` + (await dlg.innerText()).replace(/\s+/g, " ").slice(0, 700));
    await shot(page, `${tag}-${phone}-hop-thoai`);
    const ok = dlg.locator(".ant-modal-footer button").last();
    if (act === "reject") {
      await ok.click(); await page.waitForTimeout(500);
      p(`[${phone}] để trống lý do → ` + ((await dlg.getByRole("alert").allInnerTexts()).join(" / ") || "KHÔNG CÓ CẢNH BÁO"));
      await dlg.locator("textarea").fill(reason || "Đã gọi số này, người nghe máy không phải người trong ảnh.");
    } else if (reason) await dlg.locator("textarea").fill(reason);
    const resP = page.waitForResponse((r) => /\/person-claims\/[^/]+\/review$/.test(r.url()));
    await ok.click();
    const r = await resP;
    p(`[${phone}] POST review -> ${r.status()} ${r.status() >= 400 ? await r.text() : ""}`);
    const toast = page.locator(".ant-message-notice");
    await toast.first().waitFor({ timeout: 5000 }).catch(() => {});
    p(`[${phone}] thông báo: ` + ((await toast.allInnerTexts()).join(" / ") || "KHÔNG CÓ"));
    await page.waitForTimeout(1500);
    p(`[${phone}] còn trong hàng chờ: ${await cards.filter({ hasText: phone }).count()}`);
    await shot(page, `${tag}-${phone}-sau`);
  }
} catch (e) { p("LỖI SCRIPT: " + e.message); await shot(page, `${tag}-loi`); }
finally { verdict(log, p); await browser.close(); }

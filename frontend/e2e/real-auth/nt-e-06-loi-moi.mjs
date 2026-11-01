// NT-TC-12: Trưởng chi phát lời mời cá nhân qua /quan-ly/phat-ma → tab "Lời mời cá nhân".
// Ba lượt: (a) chọn nhầm một người cùng tên ở CHI KHÁC → màn phải nói đúng lý do;
// (b) phát cho đúng cụ trong chi, hạn 30 ngày → mã hiện một lần; (c) gõ lại tên cụ ấy ở lượt phát mới.
// Env: NTE_KIND, NTE_WRONG_Q/NTE_WRONG_MATCH (người khác chi), NTE_Q/NTE_MATCH (người đúng)
import { open, login, shot, mainText, pickPerson, verdict, saveState, APP } from "./nt-e-lib.mjs";
const E = process.env;
const kind = E.NTE_KIND ?? "desktop";
const T = `06-${kind}`;
const { browser, page, p, log } = await open(kind, "lời mời " + kind);
const combo = () => page.getByRole("combobox", { name: /Mời ai/ });
try {
  await login(page, E.NTE_USER ?? "truongchi");
  await page.goto(APP + "/vi/quan-ly", { waitUntil: "networkidle" });
  await page.getByRole("link", { name: /Phát mã và phát lời mời/ }).click();
  await page.waitForURL(/phat-ma/); await page.waitForLoadState("networkidle");
  const tab = page.getByRole("tab", { name: "Lời mời cá nhân" });
  if (await tab.count()) await tab.click();
  await page.waitForTimeout(600);
  await shot(page, `${T}-0-tab`);
  p("màn (đầu): " + (await mainText(page)).slice(0, 300));

  if (E.NTE_WRONG_Q) {
    await pickPerson(page, combo(), E.NTE_WRONG_Q, E.NTE_WRONG_MATCH);
    await page.waitForTimeout(400);
    p("(a) sau khi chọn người khác chi, trước khi bấm: " + ((await page.getByRole("alert").allInnerTexts()).join(" / ") || "(không có cảnh báo)"));
    const rP = page.waitForResponse((r) => r.url().endsWith("/api/v1/invitations") && r.request().method() === "POST", { timeout: 8000 }).catch(() => null);
    await page.getByRole("button", { name: "Phát lời mời" }).click();
    const r = await rP;
    p(`(a) POST /invitations -> ${r ? r.status() : "KHÔNG GỬI (chặn ở client)"}`);
    await page.waitForTimeout(600);
    p("(a) màn nói: " + ((await page.locator(".ant-alert, [role=alert]").allInnerTexts()).join(" / ")).slice(0, 400));
    await shot(page, `${T}-a-khac-chi`);
    await combo().click(); await page.keyboard.press("Escape");
  }

  await pickPerson(page, combo(), E.NTE_Q, E.NTE_MATCH);
  await page.getByLabel("Hạn dùng (ngày)").fill("30");
  const rP = page.waitForResponse((r) => r.url().endsWith("/api/v1/invitations") && r.request().method() === "POST");
  await page.getByRole("button", { name: "Phát lời mời" }).click();
  const r = await rP; const j = await r.json().catch(() => ({}));
  p(`(b) POST /invitations -> ${r.status()} expiresAt=${j.invitation?.expiresAt ?? j.expiresAt} code?=${!!j.code}`);
  await page.waitForTimeout(700);
  await shot(page, `${T}-b-da-phat`);
  const t = await mainText(page);
  p("(b) cảnh báo một lần: " + t.includes("Mã chỉ hiện một lần") + "; nút Chép mã: " + (await page.getByRole("button", { name: /Chép mã/ }).count()) + "; " + (t.match(/Mã dành cho [^\n.]{0,60}/)?.[0] ?? "(không có dòng 'Mã dành cho')"));
  if (j.code) saveState({ personalCode: j.code, personalFor: E.NTE_Q });
  await page.getByRole("button", { name: "Tôi đã chép xong" }).click();
  await page.waitForTimeout(800);

  await combo().click(); await combo().fill(E.NTE_Q);
  await page.waitForTimeout(1500);
  const opts = await page.locator(".ant-select-item-option").allInnerTexts();
  p("(c) gợi ý khi gõ lại: " + opts.map((s) => s.replace(/\s+/g, " ")).join(" | "));
  await shot(page, `${T}-c-go-lai`);
  const hit = page.locator(".ant-select-item-option").filter({ hasText: new RegExp(E.NTE_MATCH ?? E.NTE_Q) });
  if (await hit.count()) {
    await hit.first().click(); await page.waitForTimeout(500);
    p("(c) chọn lại người vừa mời → màn nói: " + ((await page.getByRole("alert").allInnerTexts()).join(" / ") || "(im lặng)"));
    await shot(page, `${T}-c-chon-lai`);
  }
  await page.keyboard.press("Escape");
  const list = await mainText(page); const i = list.indexOf("Lời mời đã phát");
  p("(d) danh sách: " + list.slice(i, i + 400));
} catch (e) { p("LỖI SCRIPT: " + e.message); await shot(page, `${T}-loi`); }
finally { verdict(log, p); await browser.close(); }

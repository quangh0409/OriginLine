// NT-TC-17 (+ NT-TC-02 bước "đăng nhập lại"): Trưởng chi cấp lại liên kết đặt mật khẩu qua màn MỚI
// /quan-ly/cap-lai-mat-khau (vào bằng lối vào thật), chính chủ thấy thông báo, mở liên kết ở một
// trình duyệt KHÁC chưa đăng nhập, đặt mật khẩu mới, thử dùng lại liên kết, đăng nhập bằng mật khẩu mới.
// Env: NTE_ISSUER (truongchi), NTE_TARGET_LOGIN (sđt), NTE_TARGET_PW, NTE_TARGET_NAME, NTE_KIND
import { open, login, shot, mainText, pickPerson, setPasswordOnPage, verdict, saveState, loadState, APP } from "./nt-e-lib.mjs";
const E = process.env;
const kind = E.NTE_KIND ?? "mobile";
const issuer = E.NTE_ISSUER ?? "truongchi";
const who = E.NTE_TARGET_LOGIN, oldPw = E.NTE_TARGET_PW, name = E.NTE_TARGET_NAME;
const newPw = E.NTE_NEW_PW ?? "NghiemThuMoi2026!";
const T = `05-${kind}`;

// 0) Chính chủ đăng nhập (mật khẩu cũ) — cũng là bước kiểm NT-TC-02 "đăng nhập lại sau duyệt".
{
  const { browser, page, p, log } = await open(kind, "chính chủ trước");
  try {
    await login(page, who, oldPw);
    await page.waitForTimeout(1500);
    const t = await mainText(page);
    p("trang chủ sau duyệt — còn banner chờ duyệt? " + /chờ duyệt|chờ Trưởng chi/.test(t) + " :: " + t.slice(0, 250));
    await shot(page, `${T}-0-chinh-chu-trang-chu`);
  } catch (e) { p("LỖI SCRIPT: " + e.message); } finally { verdict(log, p); await browser.close(); }
}

// 1) Người cấp: lối vào thật → ô "Cấp lại liên kết đặt mật khẩu" → chọn người → cấp.
let url;
{
  const { browser, page, p, log } = await open(kind, "cấp " + issuer);
  try {
    await login(page, issuer);
    if (kind === "desktop") await page.getByTestId("management-queue-link").click();
    else { await page.getByTestId("more-menu-trigger").click(); await page.getByRole("link", { name: /Quản lý thành viên/ }).filter({ visible: true }).first().click(); }
    await page.waitForURL(/\/quan-ly$/);
    await page.getByRole("link", { name: /Cấp lại liên kết đặt mật khẩu/ }).click();
    await page.waitForURL(/cap-lai-mat-khau/); await page.waitForLoadState("networkidle");
    await shot(page, `${T}-1-man-cap`);
    p("màn cấp: " + (await mainText(page)).slice(0, 500));
    // Bấm cấp khi CHƯA chọn ai → phải có câu nhắc, không gửi gì.
    await page.getByRole("button", { name: "Cấp liên kết" }).click();
    await page.waitForTimeout(400);
    p("bấm khi chưa chọn → " + ((await page.getByRole("alert").allInnerTexts()).join(" / ") || "KHÔNG CÓ NHẮC"));
    await pickPerson(page, page.getByRole("combobox", { name: /Ai quên mật khẩu/ }), name, E.NTE_TARGET_MATCH ?? name);
    await page.getByLabel(/Ghi chú cho mình/).fill("cu goi dien");
    const resP = page.waitForResponse((r) => r.url().endsWith("/api/v1/password-resets") && r.request().method() === "POST");
    await page.getByRole("button", { name: "Cấp liên kết" }).click();
    const r = await resP; const j = await r.json().catch(() => ({}));
    p(`POST /password-resets -> ${r.status()} keys=${Object.keys(j)} target=${JSON.stringify(j.grant?.target)} có sđt trong thân? ${JSON.stringify(j).includes(who)}`);
    url = j.url;
    await page.waitForTimeout(800);
    await shot(page, `${T}-1-da-cap`);
    p("sau khi cấp: " + (await mainText(page)).slice(0, 700));
    const shown = await page.locator("code, pre, [data-testid='one-time-code']").allInnerTexts().catch(() => []);
    p("liên kết hiện trên màn khớp thân? " + shown.some((s) => s.includes(url)) + " :: " + shown.join(" | ").slice(0, 200));
    saveState({ lastResetUrl: url });
  } catch (e) { p("LỖI SCRIPT: " + e.message); await shot(page, `${T}-1-loi`); } finally { verdict(log, p); await browser.close(); }
}

// 2) Chính chủ mở hộp thư (mật khẩu cũ vẫn còn dùng được cho tới khi đặt lại).
{
  const { browser, page, p, log } = await open(kind, "chính chủ hộp thư");
  try {
    await login(page, who, oldPw, "/vi/notifications");
    await page.goto(APP + "/vi/notifications", { waitUntil: "networkidle" });
    await page.waitForTimeout(1200);
    await shot(page, `${T}-2-hop-thu`);
    const t = await mainText(page);
    const i = t.indexOf("đặt lại mật khẩu");
    p("hộp thư: " + (i >= 0 ? t.slice(Math.max(0, i - 120), i + 330) : "KHÔNG THẤY THÔNG BÁO CẤP LẠI :: " + t.slice(0, 300)));
  } catch (e) { p("LỖI SCRIPT: " + e.message); } finally { verdict(log, p); await browser.close(); }
}

// 3) Trình duyệt KHÁC, chưa đăng nhập: mở liên kết, đặt mật khẩu mới.
if (url) {
  const { browser, page, p, log } = await open(kind, "mở liên kết");
  try {
    await page.goto(url, { waitUntil: "networkidle" });
    await shot(page, `${T}-3-man-dat`);
    p("màn đặt mật khẩu: " + (await mainText(page)).slice(0, 400));
    p("sau khi đặt: " + (await setPasswordOnPage(page, newPw)).slice(0, 400));
    await shot(page, `${T}-3-da-dat`);
  } catch (e) { p("LỖI SCRIPT: " + e.message); } finally { verdict(log, p); await browser.close(); }

  // 4) Dùng lại đúng liên kết ấy lần hai.
  const b2 = await open(kind, "dùng lại liên kết");
  try {
    await b2.page.goto(url, { waitUntil: "networkidle" });
    const resP = b2.page.waitForResponse((r) => r.url().includes("/invitations/set-password")).catch(() => null);
    const t = await setPasswordOnPage(b2.page, "MatKhauKhac2026!");
    const r = await resP;
    b2.p(`lần 2: POST set-password -> ${r?.status()} :: màn: ${t.slice(0, 400)}`);
    await shot(b2.page, `${T}-4-dung-lai`);
  } catch (e) { b2.p("LỖI SCRIPT: " + e.message); } finally { verdict(b2.log, b2.p); await b2.browser.close(); }

  // 5) Đăng nhập bằng mật khẩu mới.
  const b3 = await open(kind, "đăng nhập mk mới");
  try {
    await login(b3.page, who, newPw);
    b3.p("đăng nhập bằng mật khẩu mới OK: " + (await mainText(b3.page)).slice(0, 150));
    await shot(b3.page, `${T}-5-dang-nhap-moi`);
    const st = loadState(); st.accounts = st.accounts ?? {}; st.accounts[who] = { ...(st.accounts[who] ?? {}), password: newPw }; saveState({ accounts: st.accounts });
  } catch (e) { b3.p("LỖI SCRIPT (mật khẩu mới KHÔNG vào được): " + e.message); await shot(b3.page, `${T}-5-loi`); } finally { verdict(b3.log, b3.p); await b3.browser.close(); }
}

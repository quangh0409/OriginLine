// NT-LOIVAO-01 (desktop) và NT-LOIVAO-02 (pixel5): mọi mục chính vào được bằng CHUỘT, không gõ URL.
// Chỉ một lần `goto` duy nhất: mở trang chủ ở đầu buổi.
import { openSession, shot, stamp, summarize, loginViaHeader, BASE } from "./nt-a-lib.mjs";

const kind = process.argv[2] ?? "desktop";
const P = `${kind}-loivao`;
const { browser, page, log } = await openSession(kind, P);
const settle = () => page.waitForLoadState("networkidle", { timeout: 20_000 }).catch(() => {});
const home = async () => {
  await page.locator("header a[href='/'], header a[href='/vi']").first().click();
  await page.waitForURL((u) => new URL(u).pathname === "/" || new URL(u).pathname === "/vi", { timeout: 20_000 });
};
const headerItems = async () =>
  (await page.locator("header .md\:flex a").allInnerTexts().catch(() => [])).map((s) => s.trim()).filter(Boolean);
const openMore = async () => {
  await page.getByTestId("more-menu-trigger").click();
  await page.locator(".ant-drawer-body").waitFor();
  await page.waitForTimeout(400);
  const rows = await page.locator(".ant-drawer-body a").allInnerTexts();
  return rows.map((s) => s.trim()).filter(Boolean);
};
const logout = async () => {
  await page.getByTestId("account-button").first().click();
  await page.getByText("Đăng xuất", { exact: true }).click();
  await page.getByTestId("login-button").first().waitFor({ timeout: 60_000 });
};

try {
  await page.goto(`${BASE}/vi`);
  await settle();
  if (kind === "desktop") {
    console.log(`### ${stamp()} B1 khách — thanh đầu trang:`, JSON.stringify(await headerItems()));
    console.log("  lối tắt trang chủ:", JSON.stringify(await page.locator("main .ant-card").allInnerTexts()));
    await loginViaHeader(page, "thanhvien", "giapha123");
    await settle();
    console.log(`### ${stamp()} B2 thanhvien — thanh đầu trang:`, JSON.stringify(await headerItems()));
    console.log("  có 'Quản lý thành viên':", await page.getByRole("link", { name: /Quản lý thành viên/ }).count());
    await shot(page, `${P}-b2-thanhvien`);
    await page.locator("header").getByRole("link", { name: "Bài viết dòng họ" }).click();
    await page.waitForURL(/bai-viet/);
    console.log("  B3 bấm Bài viết →", page.url());
    await home();
    await page.locator("header").getByRole("link", { name: "Vinh danh" }).click();
    await page.waitForURL(/vinh-danh/);
    console.log("  B4 bấm Vinh danh →", page.url());
    await home();
    await logout();
    await loginViaHeader(page, "truongchi", "giapha123");
    await settle();
    const ql = page.getByRole("link", { name: /Quản lý thành viên/ });
    console.log(`### ${stamp()} B6 truongchi — có 'Quản lý thành viên':`, await ql.count());
    await ql.first().click();
    await page.waitForURL(/quan-ly/);
    await settle();
    console.log("  →", page.url(), "|", (await page.locator("main h1").first().innerText().catch(() => "")).trim());
    await page.getByRole("link", { name: /Nhập liệu ban đầu/ }).first().click();
    await page.waitForURL(/nhap-lieu/, { timeout: 20_000 });
    console.log("  B7 →", page.url());
    await page.setViewportSize({ width: 800, height: 900 });
    await page.waitForTimeout(500);
    const box = await page.getByRole("link", { name: /Quản lý thành viên/ }).first().boundingBox();
    const txt = await page.getByRole("link", { name: /Quản lý thành viên/ }).first().innerText().catch(() => "");
    console.log("  B8 cửa sổ 800px — ô Quản lý:", JSON.stringify(box), "chữ:", JSON.stringify(txt.trim()));
    await shot(page, `${P}-b8-hep`);
  } else {
    const tabs = page.locator("nav[aria-label] a");
    console.log(`### ${stamp()} B1 khách — tab đáy:`, JSON.stringify(await tabs.allInnerTexts()));
    for (const b of await tabs.all()) console.log("   ô tab cao", Math.round((await b.boundingBox()).height));
    console.log("  ngăn Thêm:", JSON.stringify(await openMore()));
    for (const r of await page.locator(".ant-drawer-body a").all()) console.log("   hàng cao", Math.round((await r.boundingBox()).height));
    await page.keyboard.press("Escape");
    await loginViaHeader(page, "thanhvien", "giapha123");
    await settle();
    console.log(`### ${stamp()} B2 thanhvien — ngăn Thêm:`, JSON.stringify(await openMore()));
    await shot(page, `${P}-b2-thanhvien-them`);
    for (const name of ["Bài viết dòng họ", "Vinh danh", "Tra danh xưng"]) {
      await page.locator(".ant-drawer-body").getByRole("link", { name }).click();
      await page.waitForTimeout(1200);
      console.log(`  B3 bấm ${name} →`, page.url());
      await home();
      await openMore();
    }
    await page.keyboard.press("Escape");
    await logout();
    await loginViaHeader(page, "truongchi", "giapha123");
    await settle();
    console.log(`### ${stamp()} B4 truongchi — ngăn Thêm:`, JSON.stringify(await openMore()));
    await page.locator(".ant-drawer-body").getByRole("link", { name: /Quản lý thành viên/ }).click();
    await page.waitForURL(/quan-ly/);
    await settle();
    await page.getByRole("link", { name: /Nhập liệu ban đầu/ }).first().click();
    await page.waitForURL(/nhap-lieu/, { timeout: 20_000 });
    console.log("  → ", page.url());
    await shot(page, `${P}-b4-nhap-lieu`);
  }
} catch (e) {
  console.log("LỖI SCRIPT:", e);
  await shot(page, `${P}-LOI`);
} finally {
  summarize(P, log);
  await browser.close();
}

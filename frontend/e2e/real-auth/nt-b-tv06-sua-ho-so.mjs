// NT-TV-06 — thành viên sửa hồ sơ của chính mình (ô "Tiểu sử & ghi chép"), bằng chuột từ phả đồ.
import { open, login, shot, mainText, APP } from "./nt-b-lib.mjs";
const KIND = process.env.KIND ?? "desktop";
const { browser, page, p } = await open(KIND, "NT-TV-06-" + KIND);
await login(page, "thanhvien", "giapha123", "/vi");
await page.goto(APP + "/vi/tree", { waitUntil: "networkidle" });
await page.getByRole("button", { name: "Về chỗ tôi" }).click();
await page.waitForTimeout(2500);
await page.getByTestId("person-node-name").filter({ hasText: "Hoàng Diệu Hoa" }).first().click();
await page.waitForTimeout(2500);
const drawer = page.locator(".ant-drawer-content").filter({ visible: true });
p("ngăn: nút = " + (await drawer.getByRole("button").allInnerTexts()).map(s=>s.trim()).filter(Boolean).join(" | "));
p("ngăn: liên kết = " + (await drawer.getByRole("link").allInnerTexts()).map(s=>s.trim()).filter(Boolean).join(" | "));
await shot(page, `tv06-${KIND}-1-ngan`);
if (process.env.PROBE) { await browser.close(); process.exit(0); }
// Bản đã vá có lối "Mở trang hồ sơ đầy đủ" trong ngăn; bản đang chạy không có, nên người dùng
// phải sang màn Tìm kiếm gõ tên mình — đi đúng lối ấy.
const raTrang = drawer.getByRole("link", { name: /Mở trang hồ sơ đầy đủ/ });
if (await raTrang.count()) await raTrang.click();
else {
  p("ngăn không có lối ra trang hồ sơ → sang Tìm kiếm");
  await page.goto(APP + "/vi/search", { waitUntil: "networkidle" });
  await page.getByRole("searchbox").or(page.getByPlaceholder(/tên/i)).first().fill("Hoàng Diệu Hoa");
  await page.keyboard.press("Enter");
  await page.waitForTimeout(2500);
  await page.getByRole("link", { name: /Hoàng Diệu Hoa/ }).first().click();
}
await page.waitForURL(/persons\/[0-9a-f-]{36}$/); await page.waitForLoadState("networkidle"); await page.waitForTimeout(1200);
await shot(page, `tv06-${KIND}-1b-trang-ho-so`);
// Nút "Sửa hồ sơ" (trên điện thoại chỉ còn biểu tượng bút — nhắm theo đích của liên kết).
await page.locator('main a[href$="/edit"]').first().click();
await page.waitForLoadState("networkidle");
await page.waitForTimeout(1500);
p("URL form: " + page.url());
await shot(page, `tv06-${KIND}-2-form`);
const cau = "Nghiệm thu nhóm B " + new Date().toISOString().slice(11, 19) + " — tiểu sử sửa bởi chính chủ.";
const o = page.getByLabel(/Tiểu sử/).first();
await o.fill(cau);
const resP = page.waitForResponse((r) => /\/api\/v1\/persons\/[0-9a-f-]{36}$/.test(new URL(r.url()).pathname) && r.request().method() === "PATCH", { timeout: 30000 });
await page.getByRole("button", { name: "Lưu thay đổi" }).click();
const r = await resP;
p("PATCH -> " + r.status());
await page.waitForTimeout(2000);
p("sau lưu: url=" + page.url() + " · thông báo: " + (await page.locator(".ant-message, [role=status], [role=alert]").allInnerTexts()).join(" | "));
await shot(page, `tv06-${KIND}-3-sau-luu`);
await page.reload({ waitUntil: "networkidle" });
await page.waitForTimeout(1500);
p("mở lại có câu vừa sửa: " + (await mainText(page)).includes(cau));
await browser.close();

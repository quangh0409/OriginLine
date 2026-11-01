// NT-TV-07 — thành viên mở hồ sơ người khác cùng chi: không có "Sửa hồ sơ"; gửi "Đề nghị đính
// chính" mục Nghề nghiệp; xem lại ở /correction-requests tab "Đề nghị của tôi".
//   KIND=desktop|mobile Q="Nguyễn Diệu Thảo" node e2e/real-auth/nt-b-tv07-dinh-chinh.mjs
import { open, login, shot, mainText, APP } from "./nt-b-lib.mjs";
const KIND = process.env.KIND ?? "desktop";
const Q = process.env.Q ?? "Nguyễn Diệu Thảo";
const { browser, page, p } = await open(KIND, "NT-TV-07-" + KIND);
await login(page, "thanhvien", "giapha123", "/vi");
await page.goto(APP + "/vi/search", { waitUntil: "networkidle" });
await page.getByRole("searchbox").or(page.getByPlaceholder(/tên/i)).first().fill(Q);
await page.keyboard.press("Enter");
await page.waitForTimeout(2500);
await page.getByRole("link", { name: new RegExp(Q) }).first().click();
await page.waitForURL(/persons\/[0-9a-f-]{36}$/); await page.waitForLoadState("networkidle"); await page.waitForTimeout(1500);
const pid = page.url().split("/").pop();
p("có liên kết Sửa hồ sơ: " + (await page.locator('main a[href$="/edit"]').count()));
const nut = page.getByRole("button", { name: /Đề nghị đính chính/ });
p("có nút Đề nghị đính chính trên hồ sơ: " + (await nut.count()));
await shot(page, `tv07-${KIND}-1-ho-so-nguoi-khac`);
if (await nut.count()) await nut.first().click();
else {
  p("LỐI VÒNG: không có nút trên hồ sơ — mở thẳng /persons/{id}/correction (trang có sẵn, không liên kết nào dẫn tới)");
  await page.goto(`${APP}/vi/persons/${pid}/correction`, { waitUntil: "networkidle" });
}
const dlg = page.getByRole("dialog");
await dlg.waitFor({ timeout: 20000 });
await dlg.locator(".ant-select").first().click();
await page.getByTitle("Nghề nghiệp").or(page.locator(".ant-select-item-option").filter({ hasText: "Nghề nghiệp" })).first().click();
await dlg.getByLabel("Đề nghị sửa thành").fill("Giáo viên tiểu học");
await dlg.getByLabel(/Vì sao bạn cho là như vậy/).fill("Cháu là họ hàng gần, biết chắc cô đã chuyển sang dạy học từ năm 2019.");
await shot(page, `tv07-${KIND}-2-hop-thoai`);
const resP = page.waitForResponse((r) => r.url().endsWith("/api/v1/change-requests") && r.request().method() === "POST");
await dlg.getByRole("button", { name: "Gửi đề nghị" }).click();
const r = await resP;
p("POST change-requests -> " + r.status() + " " + (await r.text()).slice(0, 200));
await page.waitForTimeout(800);
p("thông báo: " + (await page.locator(".ant-message").allInnerTexts()).join(" | "));
await shot(page, `tv07-${KIND}-3-sau-gui`);
await page.goto(APP + "/vi/correction-requests", { waitUntil: "networkidle" });
await page.waitForTimeout(1500);
await page.getByRole("tab", { name: /Đề nghị của tôi/ }).click();
await page.waitForTimeout(800);
const t = await mainText(page);
p("tab của tôi: có '" + Q + "'=" + t.includes(Q) + " · có 'Chờ duyệt'=" + t.includes("Chờ duyệt"));
p(t.slice(0, 500));
await shot(page, `tv07-${KIND}-4-cua-toi`);
await browser.close();

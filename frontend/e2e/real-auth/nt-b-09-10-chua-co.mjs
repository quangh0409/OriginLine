// NT-MOI-09 ("Tôi chưa có trong phả" thiếu người thân bị chặn) · NT-MOI-10 (gửi hợp lệ).
// Vào từ phả đồ bằng nút "Tôi chưa có trong phả" (lối người dùng thật), không gõ URL.
//   KIND=desktop|mobile NTB_ID=... NAME="Nguyễn Thị Kiểm Thử" node e2e/real-auth/nt-b-09-10-chua-co.mjs
import { open, login, shot, mainText, APP } from "./nt-b-lib.mjs";
const KIND = process.env.KIND ?? "desktop";
const NAME = process.env.NAME ?? "Nguyễn Thị Kiểm Thử";
const { browser, page, p } = await open(KIND, "NT-MOI-09/10-" + KIND);
await login(page, process.env.NTB_ID, process.env.NTB_PW ?? "NghiemThu#2026b", "/vi");
await page.goto(APP + "/vi/tree", { waitUntil: "networkidle" });
await page.getByTestId("tree-claim-not-in-tree").click();
await page.waitForURL(/nhan-dien\/chua-co/); await page.waitForLoadState("networkidle"); await page.waitForTimeout(1200);
await shot(page, `09-${KIND}-1-man`);
await page.getByLabel("Họ và tên").fill(NAME);
await page.getByLabel("Năm sinh").fill("1998");
await page.locator("label.ant-radio-button-wrapper").filter({ hasText: /^Nữ$/ }).first().click();
await page.getByLabel("Số điện thoại").fill(process.env.PHONE ?? "0912000009");
await page.getByLabel("Vài dòng tự giới thiệu").fill("Cháu là vợ anh Nguyễn Thế Bách ở Chi Bính, cưới năm 2024, quê Thái Bình, hiện ở Hà Nội.");
let posted = 0;
page.on("request", (r) => { if (r.url().includes("/person-claims") && r.method() === "POST") posted++; });
await page.getByRole("button", { name: "Gửi đơn cho Trưởng chi" }).click();
await page.waitForTimeout(1500);
const t9 = await mainText(page);
p("09 câu chặn đúng: " + t9.includes("Xin chỉ ra một người thân đã có trong phả — bố, mẹ, hoặc vợ/chồng. Không có người thân thì hồ sơ mới không nối được vào cây.") + " · POST đã gửi: " + posted);
await shot(page, `09-${KIND}-2-chan`);
if (process.env.ONLY9) { await browser.close(); process.exit(0); }
// NT-MOI-10: chọn người thân = hồ sơ của truongchi (Nguyễn Thế Bách), quan hệ vợ/chồng.
const picker = page.getByLabel("Người thân ấy là ai");
await picker.click();
await picker.fill("Nguyễn Thế Bách");
await page.waitForTimeout(2500);
const opts = page.locator(".ant-select-item-option").filter({ visible: true });
p("10 gợi ý: " + (await opts.allInnerTexts()).map(s=>s.replace(/\s+/g," ")).slice(0, 6).join(" || "));
await opts.filter({ hasText: "Nguyễn Thế Bách" }).first().click();
await page.locator("label.ant-radio-button-wrapper").filter({ hasText: "Vợ / chồng của tôi" }).first().click();
await shot(page, `10-${KIND}-1-da-dien`);
const resP = page.waitForResponse((r) => r.url().includes("/person-claims") && r.request().method() === "POST", { timeout: 30000 });
await page.getByRole("button", { name: "Gửi đơn cho Trưởng chi" }).click();
const r = await resP;
p("10 POST -> " + r.status() + " " + (await r.text()).slice(0, 300));
await page.waitForTimeout(3000);
p("10 URL: " + page.url());
p("10 màn: " + (await mainText(page)).slice(0, 1200));
await shot(page, `10-${KIND}-2-sau-gui`);
await browser.close();

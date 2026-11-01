// NT-MOI-05 (hai nút nhận diện) · NT-MOI-06 (người đã khuất bị chặn lúc chọn) · NT-MOI-07/08 (gửi
// đơn "Đây là tôi"). Chạy cho một tài khoản chưa gắn phả, ở một khổ màn hình.
//   KIND=desktop|mobile NTB_ID=... TARGET="Nguyễn Hữu Khiêm" TAG=07 node e2e/real-auth/nt-b-05-07-nhan-minh.mjs
import { open, login, shot, mainText, APP } from "./nt-b-lib.mjs";
const KIND = process.env.KIND ?? "desktop";
const TAG = (process.env.TAG ?? "07") + "-" + KIND;
const { browser, page, p } = await open(KIND, "NT-MOI-" + TAG);
await login(page, process.env.NTB_ID, process.env.NTB_PW ?? "NghiemThu#2026b", "/vi");
await page.goto(APP + "/vi/tree", { waitUntil: "networkidle" });
await page.getByTestId("tree-claim-actions").waitFor({ timeout: 30000 });

// NT-MOI-05 bước 1: chưa chọn ai → chỉ có "Tôi chưa có trong phả".
p("05 trước khi chọn: " + (await page.getByTestId("tree-claim-actions").innerText()).replace(/\s+/g, " "));
await shot(page, `05-${TAG}-truoc-chon`);

async function chonQuaOTim(ten) {
  const o = page.getByTestId("tree-jump-search").filter({ visible: true }).first();
  if (!(await o.count())) {
    // Điện thoại: ô tìm có thể nằm sau một nút mở
    p("ô tìm không hiện ngay — thử nút tìm");
    await page.getByRole("button", { name: /tìm/i }).filter({ visible: true }).first().click();
  }
  await page.getByTestId("tree-jump-search").filter({ visible: true }).first().fill(ten);
  await page.waitForTimeout(1500);
  const opt = page.getByTestId("tree-jump-result").filter({ hasText: ten }).first();
  await opt.waitFor({ timeout: 15000 });
  p(`kết quả tìm "${ten}": ` + (await page.getByTestId("tree-jump-result").allInnerTexts()).map((s) => s.replace(/\s+/g, " ")).slice(0, 5).join(" || "));
  await opt.click();
  await page.waitForTimeout(2000);
  // Nhảy tới rồi — bấm chọn node để chắc chắn node được chọn (người dùng bấm vào ô).
  const node = page.getByTestId("person-node-name").filter({ hasText: ten }).first();
  const sel = await page.getByTestId("tree-claim-actions").innerText();
  if (!sel.includes(ten) && !(await page.getByTestId("tree-claim-deceased-note").count())) {
    p("sau khi chọn kết quả tìm, dải nút chưa đổi → bấm vào node");
    await node.click();
    await page.waitForTimeout(1500);
  }
}

// NT-MOI-06: chọn thuỷ tổ đã khuất.
await chonQuaOTim("Nguyễn Đình Bách");
p("06 sau khi chọn người đã khuất: " + (await page.getByTestId("tree-claim-actions").innerText()).replace(/\s+/g, " "));
p("06 số nút 'Đây là tôi': " + (await page.getByTestId("tree-claim-this-is-me").count()));
await shot(page, `06-${TAG}-da-khuat`);
p("06 chân ngăn (bản vá): " + ((await page.getByTestId("person-claim-deceased-note").count()) ? "có câu người đã khuất" : "không có"));
// Người dùng đóng ngăn hồ sơ (nút X) rồi tìm tiếp.
await page.locator(".ant-drawer-close").filter({ visible: true }).first().click();
await page.waitForTimeout(800);

// NT-MOI-05 bước 2-3 + NT-MOI-07: chọn người còn sống.
const TARGET = process.env.TARGET;
await chonQuaOTim(TARGET);
p("05 sau khi chọn người sống: " + (await page.getByTestId("tree-claim-actions").innerText()).replace(/\s+/g, " "));
await shot(page, `05-${TAG}-chon-nguoi-song`);
if (process.env.ONLY_SELECT) { await browser.close(); process.exit(0); }

// Bản đã sửa có nút ở chân ngăn hồ sơ (person-claim-this-is-me) — bấm nó nếu có. Bản đang chạy
// (dựng trước bản vá) chỉ có nút trên dải dưới ô tìm, nằm dưới lớp phủ của ngăn: bấm thật là đóng
// ngăn. Khi ấy đi theo đúng địa chỉ nút sẽ dẫn tới (href), và ghi rõ đây là lối vòng.
const nutNgan = page.getByTestId("person-claim-this-is-me");
if (await nutNgan.count()) { p("bấm nút ở chân ngăn hồ sơ"); await nutNgan.click(); }
else {
  const href = await page.getByTestId("tree-claim-this-is-me").getAttribute("href");
  p("LỐI VÒNG: nút dải bị lớp phủ che — đi thẳng tới href " + href);
  await page.goto(APP + "/vi" + href.replace(/^\/vi/, ""), { waitUntil: "networkidle" });
}
await page.waitForURL(/nhan-dien/, { timeout: 30000 });
await page.waitForLoadState("networkidle");
await shot(page, `07-${TAG}-man-nhan-minh`);
p("07 màn nhận mình: " + (await mainText(page)).slice(0, 900));
await page.getByLabel("Số điện thoại").fill(process.env.PHONE ?? "0912345678");
await page.getByLabel("Vài dòng tự giới thiệu").fill(process.env.INTRO ?? "Cháu là con ông Nguyễn Hữu Tâm và bà Lê Thị Hạnh, quê Đại Lan, Nam Định; hiện sống ở Hà Nội.");
const resP = page.waitForResponse((r) => r.url().includes("/api/v1/person-claims") && r.request().method() === "POST", { timeout: 30000 });
await page.getByRole("button", { name: "Gửi đơn cho Trưởng chi" }).click();
const res = await resP;
p("POST person-claims -> " + res.status() + " " + (await res.text()).slice(0, 300));
await page.waitForTimeout(3000);
p("URL sau gửi: " + page.url());
await shot(page, `07-${TAG}-sau-gui`);
p("Màn sau gửi: " + (await mainText(page)).slice(0, 1500));
await browser.close();

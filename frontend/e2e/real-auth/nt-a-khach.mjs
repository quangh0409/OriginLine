// NT-KHACH-01..08 + vế khách của NT-LOIVAO-01/02 — bấm thật, chưa đăng nhập.
// Chạy: node e2e/real-auth/nt-a-khach.mjs [desktop|pixel5]
import { openSession, shot, stamp, summarize, BASE } from "./nt-a-lib.mjs";

const kind = process.argv[2] ?? "desktop";
const P = `${kind}`;
const LIVING = "Hoàng Diệu Hoa"; // nhân khẩu còn sống của `thanhvien` (tra bằng SELECT)

const { browser, page, log } = await openSession(kind, P);
const txt = async (sel) => (await page.locator(sel).first().innerText().catch(() => "")).trim();
const settle = () => page.waitForLoadState("networkidle", { timeout: 30_000 }).catch(() => {});

try {
  console.log(`\n### ${stamp()} NT-KHACH-01 / NT-LOIVAO (${P}) — trang chủ`);
  await page.goto(`${BASE}/vi`);
  await settle();
  await page.waitForTimeout(1500);
  await shot(page, `${P}-khach01-home`);
  if (kind === "desktop") {
    const nav = await page.locator("header a").allInnerTexts();
    console.log("  thanh đầu trang:", JSON.stringify(nav));
  } else {
    const tabs = await page.locator("nav[aria-label] a").allInnerTexts();
    console.log("  tab đáy:", JSON.stringify(tabs));
    await page.getByTestId("more-menu-trigger").click();
    await page.waitForTimeout(600);
    const rows = await page.locator(".ant-drawer a, .ant-drawer button").allInnerTexts();
    console.log("  ngăn Thêm:", JSON.stringify(rows));
    await shot(page, `${P}-loivao02-more-khach`);
    await page.keyboard.press("Escape");
    await page.waitForTimeout(400);
  }
  const main = await txt("main");
  console.log("  main:\n" + main.split("\n").map((l) => "    | " + l).join("\n"));

  console.log(`\n### ${stamp()} NT-KHACH-01 — bấm "Xem tất cả" ở khối Bài mới`);
  await page.getByRole("link", { name: "Xem tất cả" }).first().click();
  await page.waitForURL(/bai-viet/, { timeout: 20_000 }).catch(() => {});
  await settle();
  await page.waitForTimeout(1000);
  console.log("  url:", page.url());
  console.log("  main:\n" + (await txt("main")).split("\n").map((l) => "    | " + l).join("\n"));
  await shot(page, `${P}-khach01-bai-viet`);

  console.log(`\n### ${stamp()} NT-KHACH-02 — /tree`);
  await page.goto(`${BASE}/vi/tree`);
  await settle();
  await page.waitForTimeout(3000);
  const nodes = await page.getByTestId("person-node-name").allInnerTexts();
  console.log(`  node: ${nodes.length}`, JSON.stringify(nodes.slice(0, 8)));
  const alive = await page.getByText("Còn sống").count();
  console.log(`  số chữ "Còn sống" trên màn: ${alive}`);
  console.log("  đầu main:\n" + (await txt("main")).split("\n").slice(0, 12).map((l) => "    | " + l).join("\n"));
  console.log("  nút Đây là tôi:", await page.getByText("Đây là tôi").count(), " Tôi chưa có:", await page.getByText("Tôi chưa có trong phả").count());
  await shot(page, `${P}-khach02-tree`);

  console.log(`\n### ${stamp()} NT-KHACH-03 — /search`);
  await page.goto(`${BASE}/vi/search`);
  await settle();
  const input = page.locator("input[type=search], input[type=text]").first();
  await input.fill("nguyen");
  await page.keyboard.press("Enter");
  await page.waitForTimeout(2500);
  const r1 = await txt("main");
  console.log("  main (nguyen):\n" + r1.split("\n").slice(0, 25).map((l) => "    | " + l).join("\n"));
  await shot(page, `${P}-khach03-search-nguyen`);
  await input.fill("Nguyễn");
  await page.keyboard.press("Enter");
  await page.waitForTimeout(2500);
  await shot(page, `${P}-khach03-search-nguyen-dau`);

  console.log(`\n### ${stamp()} NT-KHACH-06 — tìm tên người còn sống "${LIVING}"`);
  await input.fill(LIVING);
  await page.keyboard.press("Enter");
  await page.waitForTimeout(2500);
  const r6 = await txt("main");
  console.log("  có tên người sống trong kết quả:", r6.includes(LIVING), " có 'Còn sống':", r6.includes("Còn sống"));
  console.log("  main:\n" + r6.split("\n").slice(0, 15).map((l) => "    | " + l).join("\n"));
  await shot(page, `${P}-khach06-search-living`);

  console.log(`\n### ${stamp()} NT-KHACH-04 — /danh-ba`);
  await page.goto(`${BASE}/vi/danh-ba`);
  await settle();
  await page.waitForTimeout(1000);
  console.log("  main:\n" + (await txt("main")).split("\n").map((l) => "    | " + l).join("\n"));
  await shot(page, `${P}-khach04-danh-ba`);
  const btn = page.getByRole("link", { name: /Xem phả đồ công khai/ });
  if (await btn.count()) {
    await btn.first().click();
    await page.waitForURL(/tree/, { timeout: 20_000 }).catch(() => {});
    console.log("  sau khi bấm:", page.url());
  } else console.log("  KHÔNG thấy nút 'Xem phả đồ công khai'");

  console.log(`\n### ${stamp()} NT-KHACH-05 — /kinship (khách không có lối vào; mở thẳng để xem khối từ chối)`);
  await page.goto(`${BASE}/vi/kinship`);
  await settle();
  await page.waitForTimeout(1000);
  console.log("  main:\n" + (await txt("main")).split("\n").map((l) => "    | " + l).join("\n"));
  console.log("  số ô combobox/chọn người:", await page.locator("main [role=combobox], main .ant-select").count());
  await shot(page, `${P}-khach05-kinship`);

  console.log(`\n### ${stamp()} NT-KHACH-07 — /moi/MAKHONGTONTAI123`);
  await page.goto(`${BASE}/vi/moi/MAKHONGTONTAI123`);
  await settle();
  await page.waitForTimeout(1500);
  console.log("  title:", await page.title());
  console.log("  main:\n" + (await txt("main")).split("\n").map((l) => "    | " + l).join("\n"));
  await shot(page, `${P}-khach07-moi-hong`);

  console.log(`\n### ${stamp()} NT-KHACH-08 — /dang-ky mã sai`);
  await page.goto(`${BASE}/vi/dang-ky`);
  await settle();
  await shot(page, `${P}-khach08-dang-ky-0`);
  const codeInput = page.getByLabel(/Mã mời dòng họ/).first();
  await codeInput.fill("ZZZZZ-00000");
  await page.getByRole("button", { name: /Kiểm mã/ }).click();
  await page.waitForTimeout(2000);
  console.log("  main:\n" + (await txt("main")).split("\n").map((l) => "    | " + l).join("\n"));
  console.log("  ô định danh xuất hiện:", await page.getByLabel(/Thư điện tử hoặc số điện thoại/).count());
  await shot(page, `${P}-khach08-dang-ky-sai-1`);
  // Màn báo sai thay ô nhập bằng nút "Gõ lại mã" — bấm như người dùng.
  const retry = page.getByRole("button", { name: /Gõ lại mã/ });
  if (await retry.count()) await retry.first().click();
  await codeInput.fill("YYYYY-11111");
  await page.getByRole("button", { name: /Kiểm mã/ }).click();
  await page.waitForTimeout(2000);
  console.log("  lần 2 main:\n" + (await txt("main")).split("\n").slice(0, 6).map((l) => "    | " + l).join("\n"));
  await shot(page, `${P}-khach08-dang-ky-sai-2`);
} catch (e) {
  console.log("LỖI SCRIPT:", e);
  await shot(page, `${P}-khach-LOI`);
} finally {
  summarize(P, log);
  await browser.close();
}

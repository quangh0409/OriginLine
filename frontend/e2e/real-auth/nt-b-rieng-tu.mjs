// Quyền riêng tư theo nhóm trường (V8) khi thành viên tự sửa hồ sơ: đổi mức ở giao diện của chính
// chủ (thanhvien, Chi Bính) rồi đăng nhập bằng truongchi (cùng chi) và truongchi.at (chi khác)
// để xem số điện thoại có hiện đúng không. Mỗi vai là một trình duyệt riêng, đăng nhập thật.
//   KIND=desktop|mobile node e2e/real-auth/nt-b-rieng-tu.mjs
import { open, login, shot, mainText, APP } from "./nt-b-lib.mjs";
const KIND = process.env.KIND ?? "desktop";
const TEN = "Hoàng Diệu Hoa";
const SDT = "0912345678";

async function moHoSoQuaTimKiem(page) {
  await page.goto(APP + "/vi/search", { waitUntil: "networkidle" });
  await page.getByRole("searchbox").or(page.getByPlaceholder(/tên/i)).first().fill(TEN);
  await page.keyboard.press("Enter");
  await page.waitForTimeout(2500);
  await page.getByRole("link", { name: new RegExp(TEN) }).first().click();
  await page.waitForURL(/persons\/[0-9a-f-]{36}$/);
  await page.waitForLoadState("networkidle");
  await page.waitForTimeout(1200);
}

async function datMuc(muc) {
  const { browser, page, p } = await open(KIND, `chinh-chu→${muc}`);
  await login(page, "thanhvien", "giapha123", "/vi");
  await moHoSoQuaTimKiem(page);
  const nhom = page.getByRole("radiogroup", { name: /Liên hệ: điện thoại/ }).or(page.getByRole("group", { name: /Liên hệ: điện thoại/ })).first();
  await nhom.getByText(muc, { exact: true }).click();
  const resP = page.waitForResponse((r) => r.url().includes("/api/v1/persons/") && r.request().method() === "PATCH");
  await page.getByRole("button", { name: "Lưu mức chia sẻ" }).click();
  const r = await resP;
  await page.waitForTimeout(1500);
  const txt = await mainText(page);
  p(`PATCH -> ${r.status()} · "Đã lưu."=${txt.includes("Đã lưu.")} · "đang được áp dụng"=${txt.includes("Mức chia sẻ đang được áp dụng.")}`);
  await shot(page, `rieng-tu-${KIND}-chinh-chu-${muc === "Cùng chi" ? "branch" : muc === "Riêng tư" ? "private" : "clan"}`);
  await browser.close();
}

async function xemBang(user, tag) {
  const { browser, page, p } = await open(KIND, `${user} xem`);
  await login(page, user, "giapha123", "/vi");
  const resP = page.waitForResponse((r) => /\/api\/v1\/persons\/[0-9a-f-]{36}$/.test(new URL(r.url()).pathname) && r.request().method() === "GET");
  await moHoSoQuaTimKiem(page);
  const body = await (await resP).json();
  const thay = (await mainText(page)).includes(SDT);
  p(`API contact=${JSON.stringify(body.contact ?? null)} · màn hình có số ${SDT}: ${thay} · occupation=${JSON.stringify(body.occupation ?? null)}`);
  await shot(page, `rieng-tu-${KIND}-${tag}-${user}`);
  await browser.close();
  return thay;
}

const kq = {};
await datMuc("Cùng chi");
kq.branch_cungChi = await xemBang("truongchi", "branch");
kq.branch_khacChi = await xemBang("truongchi.at", "branch");
await datMuc("Cả họ xem");
kq.clan_khacChi = await xemBang("truongchi.at", "clan");
await datMuc("Riêng tư");
kq.private_cungChi = await xemBang("truongchi", "private");
kq.private_hoiDong = await xemBang("hoidong", "private");
console.log("KẾT QUẢ", KIND, JSON.stringify(kq));
console.log("MONG ĐỢI", JSON.stringify({ branch_cungChi: true, branch_khacChi: false, clan_khacChi: true, private_cungChi: false, private_hoiDong: true }));

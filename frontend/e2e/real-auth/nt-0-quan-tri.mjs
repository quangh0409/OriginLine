// Khởi tạo dòng họ — bấm thật với admin.giapha trên bản production :3000 + backend :8088.
// Kiểm: lối vào ADMIN-only, readiness, lưu hồ sơ (If-Match), tên dòng họ lên trang chủ, tạo tài khoản Hội đồng.
import { chromium } from "playwright";
const browser = await chromium.launch();
const ket = [];
const chot = (ok, msg) => { ket.push(`${ok ? "ĐẠT" : "LỖI"}  ${msg}`); };
async function dangNhap(page, user) {
  await page.goto("http://localhost:3000/", { waitUntil: "networkidle" });
  await page.getByRole("button", { name: /đăng nhập/i }).first().click();
  await page.waitForURL(/localhost:8081\/realms\/giapha/);
  await page.fill("#username", user); await page.fill("#password", "giapha123"); await page.click('[name="login"]');
  await page.waitForURL(/localhost:3000/); await page.waitForLoadState("networkidle");
}
for (const [user, viewport] of [["admin.giapha", { width: 1280, height: 900 }], ["thanhvien", { width: 393, height: 851 }]]) {
  const page = await browser.newPage({ viewport });
  const api = []; const errs = [];
  page.on("response", (r) => { if (r.url().includes("/api/v1/")) api.push(`${r.status()} ${r.request().method()} ${new URL(r.url()).pathname}`); });
  page.on("pageerror", (e) => errs.push(e.message));
  await dangNhap(page, user);
  await page.goto("http://localhost:3000/quan-tri", { waitUntil: "networkidle" }); await page.waitForTimeout(1500);
  const text = (await page.locator("main").innerText()).replace(/\s+/g, " ");
  if (user === "thanhvien") {
    chot(!/Hồ sơ dòng họ/.test(text) && /Chỉ Quản trị hệ thống|quyền|không/i.test(text), `thanhvien không vào được /quan-tri — thấy: ${text.slice(0, 90)}`);
    chot(!api.some((l) => l.includes("/admin/clan-profile") && l.startsWith("2")), "thanhvien: không lời gọi admin nào thành công");
  } else {
    chot(/Hồ sơ dòng họ/.test(text), `admin thấy trình hướng dẫn — ${text.slice(0, 120)}`);
    const readiness = api.find((l) => l.includes("/admin/clan-profile/readiness")); chot(readiness?.startsWith("200"), `readiness → ${readiness}`);
    // Bước 1: đổi tên gọi tắt rồi lưu (PUT với If-Match)
    const tenTat = page.getByLabel(/tên gọi tắt/i).first();
    if (await tenTat.count()) {
      await tenTat.fill("Họ Nguyễn Đình (NT " + new Date().toISOString().slice(11, 16) + ")");
      const nutLuu = page.getByRole("button", { name: /^Lưu hồ sơ$/ });
      console.log("nút Lưu:", await nutLuu.count(), await nutLuu.first().textContent().catch(() => "?"), "enabled=", await nutLuu.first().isEnabled().catch(() => "?"));
      await nutLuu.first().click(); await page.waitForTimeout(2500);
      console.log("sau bấm Lưu:", (await page.locator("main").innerText()).replace(/\s+/g, " ").slice(0, 700));
      const put = api.filter((l) => l.includes("PUT /api/v1/admin/clan-profile")).pop(); chot(put?.startsWith("200") || put?.startsWith("201"), `PUT clan-profile → ${put}`);
      const after = (await page.locator("main").innerText()).replace(/\s+/g, " "); chot(/đã lưu|Đã lưu/i.test(after), `sau khi lưu có xác nhận — ${after.match(/.{0,60}(đã lưu|Đã lưu).{0,60}/)?.[0] ?? "(không thấy)"}`);
    } else chot(false, "không thấy ô Tên gọi tắt ở bước 1");
    await page.screenshot({ path: ".playwright-mcp/nt-0-quan-tri-admin.png", fullPage: true });
    // Trang chủ: h1 mang tên dòng họ từ hồ sơ
    await page.goto("http://localhost:3000/", { waitUntil: "networkidle" });
    const h1 = (await page.locator("h1").first().textContent()) ?? ""; chot(/Nguyễn Đình/.test(h1), `h1 trang chủ đọc từ hồ sơ — "${h1.trim()}"`);
    const pub = api.find((l) => l.includes("/public/clan-profile")); chot(pub?.startsWith("200"), `GET public/clan-profile → ${pub}`);
  }
  chot(errs.length === 0, `không pageerror (${user}) ${errs.join(" | ")}`);
  chot(!api.some((l) => /^5\d\d/.test(l)), `không 5xx (${user})`);
  await page.close();
}
await browser.close();
for (const k of ket) console.log(k);
process.exit(ket.some((k) => k.startsWith("LỖI")) ? 1 : 0);

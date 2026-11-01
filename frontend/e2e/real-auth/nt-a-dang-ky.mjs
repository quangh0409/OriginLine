// NT-MOI-01 · 03 · 04 (và NT-MOI-02 khi NT_ID là định danh đã có tài khoản) — bấm thật.
// Dùng: NT_CODE_FILE=<tệp chứa mã> NT_ID=<email|số điện thoại> node e2e/real-auth/nt-a-dang-ky.mjs <desktop|pixel5> [nhan]
import fs from "node:fs";
import { openSession, shot, stamp, summarize, BASE } from "./nt-a-lib.mjs";

const kind = process.argv[2] ?? "desktop";
const tag = process.argv[3] ?? "moi";
const P = `${kind}-${tag}`;
const code = fs.readFileSync(process.env.NT_CODE_FILE, "utf8").trim();
const loginId = process.env.NT_ID;
const PASSWORD = "NghiemThu2026!";

/** Liên kết trong thư mới nhất gửi tới `to` (Mailpit API). */
async function mailpitLink(to) {
  for (let i = 0; i < 10; i++) {
    const r = await fetch(`http://127.0.0.1:8025/api/v1/search?query=${encodeURIComponent("to:" + to)}`);
    const j = await r.json();
    const m = j.messages?.[0];
    if (m) {
      const full = await (await fetch(`http://127.0.0.1:8025/api/v1/message/${m.ID}`)).json();
      const hit = (full.HTML || full.Text || "").match(/https?:\/\/[^"'\s<>]*action-token[^"'\s<>]*/);
      if (hit) return hit[0].replace(/&amp;/g, "&");
    }
    await new Promise((res) => setTimeout(res, 1500));
  }
  return null;
}

const { browser, page, log } = await openSession(kind, P);
const main = async () => (await page.locator("main").first().innerText().catch(() => "")).trim();
const dump = async (h) => console.log(`  ${h}:\n` + (await main()).split("\n").filter(Boolean).map((l) => "    | " + l).join("\n"));
const settle = () => page.waitForLoadState("networkidle", { timeout: 30_000 }).catch(() => {});

try {
  console.log(`\n### ${stamp()} NT-MOI-01 (${P}) — /dang-ky, định danh loại ${loginId.includes("@") ? "EMAIL" : "PHONE"}`);
  await page.goto(`${BASE}/vi/dang-ky`);
  await settle();
  await page.getByLabel(/Mã mời dòng họ/).first().fill(code);
  await page.getByRole("button", { name: /Kiểm mã/ }).click();
  await page.getByLabel(/Thư điện tử hoặc số điện thoại/).first().waitFor({ timeout: 30_000 });
  await dump("sau Kiểm mã");
  await shot(page, `${P}-01-ma-hop-le`);

  await page.getByLabel(/Thư điện tử hoặc số điện thoại/).first().fill(loginId);
  await page.getByLabel(/Tên ông\/bà tự xưng/).first().fill("Người Nghiệm Thu A").catch(() => {});
  await page.getByRole("button", { name: /Lập tài khoản/ }).click();
  await page.waitForTimeout(4000);
  await settle();
  await dump("sau Lập tài khoản");
  await shot(page, `${P}-02-sau-lap`);

  const setPw = page.getByRole("link", { name: /Đặt mật khẩu$/ }).or(page.getByRole("button", { name: /Đặt mật khẩu$/ }));
  if (!(await setPw.count())) {
    console.log("  (không có nút Đặt mật khẩu — dừng ở đây, đúng cho NT-MOI-02)");
  } else {
    console.log(`\n### ${stamp()} NT-MOI-03 — bấm Đặt mật khẩu`);
    await setPw.first().click();
    await page.waitForTimeout(2500);
    await settle();
    console.log("  url:", page.url().replace(/token=[^&#]+/, "token=<ẩn>"));
    await dump("màn đặt mật khẩu");
    await shot(page, `${P}-03-dat-mat-khau`);
    const pw = page.locator("input[type=password]");
    console.log("  số ô mật khẩu:", await pw.count());
    await pw.nth(0).fill(PASSWORD);
    await pw.nth(1).fill(PASSWORD);
    await page.getByRole("button", { name: /Đặt mật khẩu$/ }).or(page.locator("input[type=submit]")).first().click();
    await page.waitForTimeout(3000);
    await settle();
    await dump("sau khi đặt");
    await shot(page, `${P}-04-da-dat`);

    console.log(`\n### ${stamp()} NT-MOI-03 — "Đăng nhập, xem phả đồ" → Keycloak`);
    await page.getByRole("link", { name: /Đăng nhập, xem phả đồ/ }).or(page.getByRole("button", { name: /Đăng nhập, xem phả đồ/ })).first().click();
    await page.waitForURL(/:8081\/realms\/giapha\//, { timeout: 60_000 });
    await shot(page, `${P}-05-keycloak`);
    await page.locator("#username").fill(loginId);
    await page.locator("#password").fill(PASSWORD);
    await page.locator("[name=login], #kc-login, input[type=submit]").first().click();
    await page.waitForTimeout(2500);
    if (page.url().includes(":8081")) {
      // Realm bật verifyEmail: tài khoản lập bằng thư điện tử phải bấm liên kết trong thư.
      // Người dùng thật mở hộp thư; ở máy dev hộp thư là Mailpit (8025).
      await dump("Keycloak dừng lại ở");
      await shot(page, `${P}-05b-keycloak-xac-minh`);
      const link = await mailpitLink(loginId);
      console.log("  thư xác minh:", link ? "có" : "KHÔNG THẤY");
      if (link) await page.goto(link);
    }
    await page.waitForURL(/localhost:3000/, { timeout: 60_000 });
    await page.getByTestId("account-button").first().waitFor({ timeout: 60_000 });
    await settle();
    await page.waitForTimeout(1500);
    console.log("  quay về:", page.url());
    await dump("màn sau đăng nhập");
    await shot(page, `${P}-06-sau-dang-nhap`);

    console.log(`\n### ${stamp()} NT-MOI-04 — về trang chủ bằng tên dòng họ ở góc trái`);
    await page.locator("header a[href='/'], header a[href='/vi']").first().click();
    await page.waitForTimeout(2500);
    await settle();
    console.log("  url:", page.url());
    await dump("trang chủ");
    const link = page.getByRole("link", { name: /Xem trạng thái đơn của tôi/ });
    console.log("  liên kết trạng thái đơn:", await link.count(), await link.first().getAttribute("href").catch(() => null));
    await shot(page, `${P}-07-trang-chu-cho-duyet`);
  }
} catch (e) {
  console.log("LỖI SCRIPT:", e);
  await shot(page, `${P}-LOI`);
} finally {
  summarize(P, log);
  await browser.close();
}

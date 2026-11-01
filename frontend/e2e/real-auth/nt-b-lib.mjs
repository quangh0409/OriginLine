// Tiện ích chung cho kịch bản nghiệm thu nhóm B (tự nhận mình + đính chính), bấm bằng trình duyệt
// thật trên bản production (3000) nói chuyện với backend thật (8088). Không có MSW, không đi tắt
// đăng nhập: mọi lượt vào đều qua nút "Đăng nhập" → Keycloak → quay về app, đúng như người dùng.
import { chromium, devices } from "playwright";
import fs from "node:fs";

export const APP = "http://localhost:3000";
export const SHOTS = ".playwright-mcp/nt-b";
fs.mkdirSync(SHOTS, { recursive: true });

export const VIEWPORTS = {
  desktop: { viewport: { width: 1280, height: 900 } },
  mobile: { ...devices["Pixel 5"], viewport: { width: 393, height: 851 } },
};

/** Mở một trình duyệt + trang đã gắn đủ bộ nghe: pageerror, console error, mọi /api/v1/. */
export async function open(kind = "desktop", tag = "") {
  const browser = await chromium.launch();
  const context = await browser.newContext({ ...VIEWPORTS[kind], locale: "vi-VN", timezoneId: "Asia/Ho_Chi_Minh" });
  const page = await context.newPage();
  const log = { api: [], errors: [] };
  const p = (s) => console.log(`[${tag || kind}] ${s}`);
  page.on("pageerror", (e) => { log.errors.push("PAGEERROR " + e.message); p("PAGEERROR: " + e.message); });
  page.on("console", (m) => { if (m.type() === "error") p("CONSOLE: " + m.text().slice(0, 300)); });
  page.on("response", async (r) => {
    if (!r.url().includes("/api/v1/") && !r.url().includes("/graphql")) return;
    const u = new URL(r.url());
    const line = `${r.status()} ${r.request().method()} ${u.pathname}${u.search}`;
    log.api.push(line);
    if (r.status() >= 400) {
      let body = ""; try { body = (await r.text()).slice(0, 400); } catch {}
      p("API " + line + " :: " + body);
    } else if (process.env.VERBOSE) p("API " + line);
  });
  page.on("requestfailed", (r) => { if (r.url().includes("/api/v1/")) p("FAILED " + r.method() + " " + r.url()); });
  return { browser, context, page, log, p };
}

export async function shot(page, name) {
  await page.screenshot({ path: `${SHOTS}/${name}.png`, fullPage: true });
}

/** Đăng nhập như người dùng: bấm nút ở thanh đầu trang (desktop) hoặc nút đăng nhập bất kỳ đang hiện. */
export async function login(page, user, pass = "giapha123", startPath = "/vi") {
  await page.goto(APP + startPath, { waitUntil: "networkidle" });
  const btn = page.getByTestId("login-button").filter({ visible: true }).first();
  if (await btn.count()) await btn.click();
  else await page.getByRole("button", { name: /đăng nhập/i }).filter({ visible: true }).first().click();
  await page.waitForURL(/:8081\/realms\/giapha/, { timeout: 60000 });
  await page.fill("#username", user);
  await page.fill("#password", pass);
  await page.click('[name="login"]');
  await page.waitForURL(/localhost:3000|required-action/, { timeout: 60000 });
  if (page.url().includes("VERIFY_EMAIL")) {
    // Tài khoản mới đăng ký bằng thư điện tử: Keycloak đòi xác minh thư. Người dùng mở hộp thư và
    // bấm liên kết — ở máy dev hộp thư là Mailpit (8025), nên đọc lá thư mới nhất gửi tới địa chỉ ấy.
    await page.waitForTimeout(1500);
    const s = await (await fetch(`http://127.0.0.1:8025/api/v1/search?query=to:${encodeURIComponent(user)}`)).json();
    const m = await (await fetch(`http://127.0.0.1:8025/api/v1/message/${s.messages[0].ID}`)).json();
    const link = (m.Text.match(/https?:\/\/\S+action-token\S+/) || m.HTML.match(/href="([^"]*action-token[^"]*)"/))?.at(-1).replace(/&amp;/g, "&");
    await page.goto(link);
    const cont = page.getByRole("link", { name: /tiếp|continue|quay/i }).or(page.getByRole("button", { name: /tiếp|continue/i }));
    await page.waitForURL(/localhost:3000/, { timeout: 15000 }).catch(async () => { if (await cont.count()) await cont.first().click(); });
    await page.waitForURL(/localhost:3000/, { timeout: 60000 });
  }
  await page.waitForLoadState("networkidle");
}

export async function mainText(page) {
  const m = page.locator("main").first();
  const t = (await m.count()) ? await m.innerText() : await page.locator("body").innerText();
  return t.replace(/\s+/g, " ").trim();
}

/**
 * Đăng ký một tài khoản mới qua /dang-ky bằng mã mời dòng họ, rồi đặt mật khẩu qua liên kết màn
 * "Tài khoản đã lập xong" đưa ra — đúng các bước NT-MOI-01 + NT-MOI-03. Mã đọc từ tệp ngoài kho.
 */
export async function registerAccount(page, identifier, displayName, password = "NghiemThu#2026b") {
  const code = fs.readFileSync(process.env.NTB_CODE_FILE, "utf8").trim();
  await page.goto(APP + "/vi/dang-ky", { waitUntil: "networkidle" });
  await page.getByLabel("Mã mời dòng họ").fill(code);
  await page.getByRole("button", { name: "Kiểm mã" }).click();
  await page.getByLabel("Thư điện tử hoặc số điện thoại").fill(identifier);
  if (displayName) await page.getByLabel(/Tên ông\/bà tự xưng/).fill(displayName);
  await page.getByRole("button", { name: "Lập tài khoản" }).click();
  await page.getByText("Tài khoản đã lập xong").waitFor({ timeout: 30000 });
  await page.getByRole("link", { name: "Đặt mật khẩu" }).or(page.getByRole("button", { name: "Đặt mật khẩu" })).first().click();
  await page.waitForLoadState("networkidle");
  await page.getByLabel("Mật khẩu mới", { exact: true }).fill(password);
  await page.getByLabel("Gõ lại mật khẩu mới").fill(password);
  await page.getByRole("button", { name: "Đặt mật khẩu" }).click();
  await page.getByText("Xong rồi — mật khẩu đã đặt").waitFor({ timeout: 30000 });
  return password;
}

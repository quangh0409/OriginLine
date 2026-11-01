// Tiện ích chung cho kịch bản nghiệm thu nhóm E (Trưởng chi · Hội đồng · Admin · "chưa có"), bấm bằng
// trình duyệt thật trên bản production (3000) nói chuyện với backend thật (8088). Không MSW, không đi
// tắt đăng nhập: mọi lượt vào đều qua nút "Đăng nhập" → Keycloak → quay về app, đúng như người dùng.
import { chromium, devices } from "playwright";
import fs from "node:fs";

export const APP = "http://localhost:3000";
export const API = "http://localhost:8088";
export const SHOTS = ".playwright-mcp/nt-e";
export const STATE = process.env.NTE_STATE ?? "C:/Users/ADMIN/AppData/Local/Temp/claude/D--OriginLine/80769d08-6e1e-44f3-9e46-dad5d2aecb84/scratchpad/nte-state.json";
fs.mkdirSync(SHOTS, { recursive: true });

export const VIEWPORTS = {
  desktop: { viewport: { width: 1280, height: 900 } },
  mobile: { ...devices["Pixel 5"], viewport: { width: 393, height: 851 } },
};

/** Mở trình duyệt + trang đã gắn đủ bộ nghe: pageerror, console error, mọi /api/v1/ (thân khi ≥ 400). */
export async function open(kind = "desktop", tag = "") {
  const browser = await chromium.launch();
  const context = await browser.newContext({
    ...VIEWPORTS[kind], locale: "vi-VN", timezoneId: "Asia/Ho_Chi_Minh", acceptDownloads: true,
  });
  const page = await context.newPage();
  const log = { api: [], errors: [], s5xx: [] };
  const p = (s) => console.log(`[${tag || kind}] ${s}`);
  page.on("pageerror", (e) => { log.errors.push("PAGEERROR " + e.message); p("PAGEERROR: " + e.message); });
  page.on("console", (m) => { if (m.type() === "error") p("CONSOLE: " + m.text().slice(0, 300)); });
  page.on("response", async (r) => {
    if (!r.url().includes("/api/v1/") && !r.url().includes("/graphql")) return;
    const u = new URL(r.url());
    const line = `${r.status()} ${r.request().method()} ${u.pathname}${u.search}`;
    log.api.push(line);
    if (r.status() >= 500) log.s5xx.push(line);
    if (r.status() >= 400) {
      let body = ""; try { body = (await r.text()).slice(0, 500); } catch {}
      const auth = r.request().headers()["authorization"];
      p("@" + new URL(page.url()).pathname + " API " + line + (r.status() === 401 ? (auth ? " [CÓ Bearer]" : " [KHÔNG Bearer]") : "") + " :: " + body);
    } else if (process.env.VERBOSE) p("API " + line);
  });
  page.on("requestfailed", (r) => { if (r.url().includes("/api/v1/")) p("FAILED " + r.method() + " " + r.url() + " :: " + (r.failure()?.errorText ?? "")); });
  return { browser, context, page, log, p };
}

export async function shot(page, name) {
  await page.screenshot({ path: `${SHOTS}/${name}.png`, fullPage: true });
}

/** Đăng nhập như người dùng: nút "Đăng nhập" đang hiện → Keycloak → quay về app. */
export async function login(page, user, pass = "giapha123", startPath = "/vi") {
  await page.goto(APP + startPath, { waitUntil: "networkidle" });
  console.log("  -- (đã mở trang với tư cách khách, bấm Đăng nhập) --");
  const btn = page.getByTestId("login-button").filter({ visible: true }).first();
  if (await btn.count()) await btn.click();
  else await page.getByRole("button", { name: /đăng nhập/i }).filter({ visible: true }).first().click();
  await page.waitForURL(/:8081\/realms\/giapha/, { timeout: 60000 });
  await page.fill("#username", user);
  await page.fill("#password", pass);
  await page.click('[name="login"]');
  await page.waitForURL(/localhost:3000/, { timeout: 60000 });
  await page.waitForLoadState("networkidle");
  console.log("  -- (đã quay về app sau Keycloak) --");
}

/** Lấy access token mà chính trang đang dùng (đọc từ header Authorization của một lượt gọi thật). */
export async function tokenOf(page) {
  const req = page.waitForRequest((r) => r.url().includes("/api/v1/") && !!r.headers()["authorization"], { timeout: 30000 });
  await page.reload({ waitUntil: "networkidle" });
  return (await req).headers()["authorization"].replace(/^Bearer /, "");
}

/** Gọi API trực tiếp (Node fetch) — cho các ca kịch bản bảo "gọi thẳng lối máy chủ". */
export async function api(method, path, { token, body, headers = {}, raw } = {}) {
  const h = { ...headers };
  if (token) h.authorization = "Bearer " + token;
  let payload = raw;
  if (body !== undefined) { h["content-type"] = "application/json"; payload = JSON.stringify(body); }
  const r = await fetch(API + path, { method, headers: h, body: payload });
  const text = await r.text();
  let json; try { json = JSON.parse(text); } catch {}
  return { status: r.status, json, text };
}

export async function mainText(page) {
  const m = page.locator("main").first();
  const t = (await m.count()) ? await m.innerText() : await page.locator("body").innerText();
  return t.replace(/\s+/g, " ").trim();
}

export function loadState() { try { return JSON.parse(fs.readFileSync(STATE, "utf8")); } catch { return {}; } }
export function saveState(patch) { const s = { ...loadState(), ...patch }; fs.writeFileSync(STATE, JSON.stringify(s, null, 2)); return s; }

export function verdict(log, p) {
  p(`TỔNG: pageerror=${log.errors.length} 5xx=${log.s5xx.length} ${log.s5xx.join(" | ")}`);
}

/**
 * Dựng một tài khoản như người thật: /dang-ky → gõ mã dòng họ → khai định danh → "Lập tài khoản"
 * → bấm "Đặt mật khẩu" → đặt mật khẩu → trả về mật khẩu. Không đi tắt qua API.
 */
export async function registerAccount(page, code, identifier, name, password = "NghiemThuE2026!") {
  await page.goto(APP + "/vi/dang-ky", { waitUntil: "networkidle" });
  await page.getByLabel("Mã mời dòng họ").fill(code);
  await page.getByRole("button", { name: "Kiểm mã" }).click();
  await page.getByLabel("Thư điện tử hoặc số điện thoại").fill(identifier);
  if (name) await page.getByLabel(/Tên ông\/bà tự xưng/).fill(name);
  await page.getByRole("button", { name: "Lập tài khoản" }).click();
  await page.getByText("Tài khoản đã lập xong").first().waitFor({ timeout: 30000 });
  await page.getByRole("link", { name: "Đặt mật khẩu" }).or(page.getByRole("button", { name: "Đặt mật khẩu" })).first().click();
  await page.waitForURL(/dat-mat-khau/, { timeout: 30000 });
  await page.waitForLoadState("networkidle");
  await setPasswordOnPage(page, password);
  return password;
}

/** Điền màn /dat-mat-khau đang mở. Trả về văn bản màn hình sau khi bấm. */
export async function setPasswordOnPage(page, password) {
  await page.getByLabel("Mật khẩu mới", { exact: true }).fill(password);
  await page.getByLabel("Gõ lại mật khẩu mới").fill(password);
  await page.getByRole("button", { name: "Đặt mật khẩu", exact: true }).click();
  await page.waitForTimeout(2500);
  return mainText(page);
}

export async function logout(page) {
  await page.context().clearCookies();
  await page.goto(APP + "/vi", { waitUntil: "networkidle" });
  await page.evaluate(() => { localStorage.clear(); sessionStorage.clear(); });
}

/** Chọn một người trong <PersonPicker> (antd Select có ô tìm): gõ tên, đợi gợi ý, bấm gợi ý khớp. */
export async function pickPerson(page, comboLocator, query, optionMatch) {
  await comboLocator.click();
  await comboLocator.fill(query);
  const opt = page.locator(".ant-select-item-option").filter({ hasText: new RegExp(optionMatch ?? query) }).first();
  await opt.waitFor({ timeout: 20000 });
  await opt.click();
}

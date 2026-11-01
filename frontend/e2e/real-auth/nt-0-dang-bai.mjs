// Trọn vòng đăng bài bằng trình duyệt thật: đăng nhập Keycloak -> /bai-viet -> Viết bài mới
// -> gõ tiêu đề + thân -> chờ lưu nháp -> Gửi duyệt. In mọi lời gọi API và mọi lỗi trang.
// Chạy từ TRONG frontend/ (để resolve được playwright): node <đường dẫn tệp này>
import { chromium } from "playwright";
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
const api = [];
page.on("response", (r) => { if (r.url().includes("/api/v1/")) api.push(r.status() + " " + r.request().method() + " " + new URL(r.url()).pathname); });
page.on("requestfailed", (r) => { if (r.url().includes("/api/v1/")) api.push("FAILED " + r.method() + " " + new URL(r.url()).pathname); });
page.on("pageerror", (e) => console.log("PAGEERROR:", e.message));
page.on("console", (m) => { if (m.type() === "error" && !/40[13]/.test(m.text())) console.log("CONSOLE:", m.text().slice(0, 300)); });

await page.goto("http://localhost:3000/bai-viet", { waitUntil: "networkidle" });
await page.getByRole("button", { name: /đăng nhập/i }).first().click();
await page.waitForURL(/localhost:8081\/realms\/giapha/);
await page.fill("#username", process.env.KC_USER ?? "thanhvien");
await page.fill("#password", process.env.KC_PASS ?? "giapha123");
await page.click('[name="login"]');
await page.waitForURL(/localhost:3000/);
await page.waitForLoadState("networkidle");

await page.getByRole("link", { name: /viết bài/i }).first().click();
await page.waitForLoadState("networkidle");
const h1 = await page.locator("h1").first().textContent({ timeout: 10000 }).catch(() => null);
console.log("Màn soạn bài, h1 =", JSON.stringify(h1));

const tieuDe = "Nghiệm thu đăng bài " + new Date().toISOString().slice(0, 16);
await page.getByLabel(/tiêu đề/i).first().fill(tieuDe);
await page.getByLabel(/nội dung|thân bài/i).first().fill("Bài do kịch bản nghiệm thu tự động viết, để chứng minh luồng đăng bài chạy trọn vòng với backend thật.");
await page.waitForTimeout(3000); // lưu nháp tự động
const nhap = await page.getByText(/đã lưu nháp|đang lưu|lưu nháp/i).first().textContent({ timeout: 3000 }).catch(() => "(không thấy dòng trạng thái nháp)");
console.log("Trạng thái nháp:", (nhap ?? "").trim());

const guiDuyet = page.getByRole("button", { name: /gửi duyệt|gửi bài/i }).first();
const enabled = await guiDuyet.isEnabled({ timeout: 3000 }).catch(() => "không thấy nút");
console.log("Nút gửi duyệt enabled =", enabled);
if (enabled === true) { await guiDuyet.click(); await page.waitForTimeout(3000); }
console.log("Sau khi gửi:", (await page.locator("main").innerText()).replace(/\s+/g, " ").slice(0, 500));
console.log("--- API ---"); for (const c of api) console.log(" ", c);
await page.screenshot({ path: process.env.SHOT ?? "nghiem-thu-dang-bai.png", fullPage: true });
await browser.close();

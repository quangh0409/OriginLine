// Thăm dò: tài khoản đang đăng nhập đọc được gì từ GET /persons/{id} (bắt đúng lời gọi ứng dụng phát ra).
import { open, login, APP } from "./nt-b-lib.mjs";
const { browser, page, p } = await open("desktop", "probe-person");
await login(page, process.env.NTB_ID, process.env.NTB_PW ?? "NghiemThu#2026b", "/vi");
const resP = page.waitForResponse((r) => /\/api\/v1\/persons\/[0-9a-f-]{36}$/.test(new URL(r.url()).pathname) && r.request().method() === "GET");
await page.goto(APP + "/vi/persons/" + process.env.PID, { waitUntil: "networkidle" });
const r = await resP;
p(r.status() + " " + (await r.text()).slice(0, 1500));
p((await page.locator("main").innerText()).replace(/\s+/g, " ").slice(0, 1500));
await browser.close();

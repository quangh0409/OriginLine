// Thăm dò NT-TV-07: thành viên mở hồ sơ người khác cùng chi qua màn Tìm kiếm — có lối đính chính không?
import { open, login, shot, mainText, APP } from "./nt-b-lib.mjs";
const KIND = process.env.KIND ?? "desktop";
const { browser, page, p } = await open(KIND, "probe-other-" + KIND);
await login(page, "thanhvien", "giapha123", "/vi");
await page.goto(APP + "/vi/search", { waitUntil: "networkidle" });
await page.getByRole("searchbox").or(page.getByPlaceholder(/tên/i)).first().fill(process.env.Q ?? "Nguyễn Diệu Thảo");
await page.keyboard.press("Enter");
await page.waitForTimeout(2500);
await shot(page, `probe-other-${KIND}-1-tim`);
await page.getByRole("link", { name: new RegExp(process.env.Q ?? "Nguyễn Diệu Thảo") }).first().click();
await page.waitForURL(/persons\//); await page.waitForLoadState("networkidle"); await page.waitForTimeout(1500);
p("URL " + page.url());
p("nút: " + (await page.locator("main").getByRole("button").allInnerTexts()).map(s=>s.trim()).filter(Boolean).join(" | "));
p("liên kết: " + (await page.locator("main").getByRole("link").allInnerTexts()).map(s=>s.trim()).filter(Boolean).join(" | "));
await shot(page, `probe-other-${KIND}-2-ho-so`);
await browser.close();

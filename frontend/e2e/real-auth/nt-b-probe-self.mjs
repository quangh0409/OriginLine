// Thăm dò: thành viên tìm tới hồ sơ của chính mình bằng chuột (không gõ URL).
import { open, login, shot, mainText, APP } from "./nt-b-lib.mjs";
const KIND = process.env.KIND ?? "desktop";
const { browser, page, p } = await open(KIND, "probe-self-" + KIND);
await login(page, "thanhvien", "giapha123", "/vi");
await page.goto(APP + "/vi/tree", { waitUntil: "networkidle" });
p("buttons: " + (await page.getByRole("button").filter({ visible: true }).allInnerTexts()).map(s=>s.trim()).filter(Boolean).join(" | "));
p("links: " + (await page.getByRole("link").filter({ visible: true }).allInnerTexts()).map(s=>s.trim()).filter(Boolean).join(" | "));
await browser.close();

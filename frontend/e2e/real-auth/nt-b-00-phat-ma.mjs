// Bước dựng: Hội đồng phát một mã mời dòng họ qua giao diện /quan-ly/phat-ma (đúng luồng người dùng).
// Mã thô chỉ hiện một lần; ghi ra tệp trong scratchpad (ngoài kho) để các kịch bản đăng ký dùng.
import fs from "node:fs";
import { open, login, shot, mainText, APP } from "./nt-b-lib.mjs";
const OUT = process.env.NTB_CODE_FILE;
const { browser, page, p } = await open("desktop", "phat-ma");
await login(page, "hoidong", "giapha123", "/vi/quan-ly/phat-ma");
await page.goto(APP + "/vi/quan-ly/phat-ma", { waitUntil: "networkidle" });
const resP = page.waitForResponse((r) => r.url().endsWith("/api/v1/clan-invites") && r.request().method() === "POST");
await page.getByLabel("Phát cho kênh nào?").fill("nghiem thu nhom B");
await page.getByRole("button", { name: "Phát mã", exact: true }).click();
const res = await resP;
const j = await res.json();
p("POST /clan-invites -> " + res.status() + " keys=" + Object.keys(j).join(","));
fs.writeFileSync(OUT, j.code);
await page.waitForTimeout(1000);
await shot(page, "00-phat-ma-sau");
await browser.close();

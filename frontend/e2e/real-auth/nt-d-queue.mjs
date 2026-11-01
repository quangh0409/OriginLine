// Hàng chờ báo cáo ảnh của truongchi (nối NT-TV-13 → NT-TC-11): thấy báo cáo, đọc cảnh báo gỡ, bấm "Thôi".
// Chạy: node e2e/real-auth/nt-d-queue.mjs [desktop|pixel5]
import { open, login, shot, summary, BASE, DESKTOP, PIXEL5 } from "./nt-d-lib.mjs";
import fs from "node:fs";
const mode = process.argv[2] ?? "desktop";
const tag = `queue-${mode}`;
const out = {};
const { browser, page, log } = await open(tag, mode === "pixel5" ? PIXEL5 : DESKTOP);
try {
  await login(page, "truongchi");
  await page.goto(BASE + "/vi/quan-ly/bao-cao-anh");
  await page.locator("article").first().or(page.getByText("Không còn báo cáo nào đang chờ.")).first().waitFor({ timeout: 45000 });
  await shot(page, `${tag}-01`);
  out.hangCho = (await page.locator("main").innerText()).slice(0, 800);
  const first = page.locator("article").first();
  if (await first.count()) {
    out.theBaoCao = (await first.innerText()).replace(/\n+/g, " | ");
    out.links = await first.locator("a").evaluateAll((as) => as.map((a) => [a.textContent, a.getAttribute("href")]));
    out.img = await first.locator("img").evaluate((el) => ({ src: el.src.slice(0, 80), w: el.naturalWidth })).catch(() => null);
    await first.getByRole("button", { name: "Gỡ vĩnh viễn" }).click();
    await page.waitForTimeout(800);
    out.canhBao = (await first.innerText()).replace(/\n+/g, " | ");
    await shot(page, `${tag}-02-canh-bao`);
    await first.getByRole("button", { name: "Thôi" }).click();
  }
  await page.goto(BASE + "/vi/quan-ly/bai-viet");
  await page.waitForTimeout(4000);
  await shot(page, `${tag}-03-hang-cho-bai`);
  out.hangChoBai = (await page.locator("main").innerText()).slice(0, 600);
} catch (e) { console.log("ERR", e); await shot(page, `${tag}-zz`); }
finally { summary(tag, log); console.log(JSON.stringify(out, null, 2)); fs.writeFileSync(`.playwright-mcp/nt-d/${tag}.json`, JSON.stringify({ out, log }, null, 2)); await browser.close(); }

// NT-TV-03 — tra danh xưng giữa hai người.
import { open, login, shot, summary, BASE, DESKTOP, PIXEL5 } from "./nt-c-lib.mjs";
const which = process.argv[2] ?? "desktop";
const L = `tv03-${which}`;
const { browser, page, log } = await open(L, which === "pixel5" ? PIXEL5 : DESKTOP);
async function pick(id, q, match) {
  const input = page.locator(`#${id}`);
  await input.click();
  await input.pressSequentially(q, { delay: 40 });
  const opt = page.locator(".ant-select-dropdown:visible .ant-select-item-option").filter({ hasText: match ?? q }).first();
  await opt.waitFor({ timeout: 20000 });
  console.log("OPTIONS", (await page.locator(".ant-select-dropdown:visible .ant-select-item-option").allInnerTexts()).slice(0, 5).map((s) => s.replace(/\n/g, " ")).join(" || "));
  await opt.click();
  await page.waitForTimeout(500);
}
try {
  await login(page, "thanhvien");
  await page.goto(BASE + "/vi/kinship");
  await page.locator("#kinship-from").waitFor({ timeout: 60000 });
  await pick("kinship-from", process.env.FROM ?? "Hoàng Diệu Hoa");
  await pick("kinship-to", process.env.TO ?? "Nguyễn Hữu Đức");
  await page.waitForTimeout(3000);
  await shot(page, `${L}-01-result`);
  const main = await page.locator("main").innerText();
  console.log("RESULT:", main.slice(0, 1500).replace(/\n+/g, " | "));
  await page.getByRole("button", { name: /Đảo chiều/ }).click();
  await page.waitForTimeout(3000);
  await shot(page, `${L}-02-swapped`);
  console.log("SWAPPED:", (await page.locator("main").innerText()).slice(0, 1500).replace(/\n+/g, " | "));
  console.log("URL", page.url());
} finally { summary(L, log); await browser.close(); }

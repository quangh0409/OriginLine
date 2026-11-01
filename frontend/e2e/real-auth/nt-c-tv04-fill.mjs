// NT-TV-04 bước 2: điền Nghề nghiệp + Nơi ở (tỉnh) qua "Điền ngay" → biểu mẫu sửa hồ sơ.
import { open, login, shot, summary, BASE } from "./nt-c-lib.mjs";
const L = "tv04-fill";
const { browser, page, log } = await open(L);
try {
  await login(page, "thanhvien");
  await page.goto(BASE + "/vi/persons/090ea8c9-be1f-5a0b-b371-de6b8680a4e8");
  await page.waitForTimeout(4000);
  await shot(page, `${L}-00-profile-page`);
  const fill = page.getByRole("link", { name: /Điền ngay/ }).first();
  await fill.click();
  await page.waitForURL(/\/edit/, { timeout: 30000 });
  await page.waitForTimeout(3000);
  const occ = page.getByLabel("Nghề nghiệp", { exact: false }).first();
  await occ.fill("Kỹ sư phần mềm");
  const prov = page.getByLabel(/tỉnh\/thành|Tỉnh/i).first();
  await prov.fill("Hà Nội");
  await shot(page, `${L}-01-form`);
  await page.getByRole("button", { name: /Lưu/ }).last().click();
  await page.waitForTimeout(4000);
  await shot(page, `${L}-02-after-submit`);
  console.log("URL", page.url());
  console.log("MAIN", (await page.locator("main").innerText()).slice(0, 600).replace(/\n+/g, " | "));
} finally { summary(L, log); await browser.close(); }

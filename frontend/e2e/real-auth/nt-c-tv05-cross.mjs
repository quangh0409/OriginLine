// NT-TV-05 đối chứng: thanhvien để "Liên hệ" ở mức Riêng tư → Hội đồng vẫn đọc được, trưởng chi khác chi thì không.
import { open, login, shot, summary, BASE } from "./nt-c-lib.mjs";
const SELF = "090ea8c9-be1f-5a0b-b371-de6b8680a4e8";
const PHONE = "0912345678";
async function asUser(user, fn) {
  const { browser, page, log } = await open(`tv05-${user}`);
  try { await login(page, user); await fn(page); } finally { summary(`tv05-${user}`, log); await browser.close(); }
}
if (process.argv[2] !== "skip-fill") await asUser("thanhvien", async (page) => {
  await page.goto(BASE + `/vi/persons/${SELF}/edit`);
  await page.waitForTimeout(4000);
  const phone = page.getByLabel("Điện thoại", { exact: true }).first();
  const cur = await phone.inputValue();
  console.log("phone hiện có:", cur ? "(có)" : "(trống)");
  if (!cur) {
    await phone.fill(PHONE);
    await page.getByRole("button", { name: /Lưu/ }).last().click();
    await page.waitForTimeout(4000);
    console.log("URL after save", page.url());
  }
});
for (const user of ["hoidong", "truongchi.at", "truongchi"]) {
  await asUser(user, async (page) => {
    await page.goto(BASE + `/vi/persons/${SELF}`);
    await page.waitForTimeout(5000);
    await shot(page, `tv05-${user}-view`);
    const txt = await page.locator("main").innerText();
    console.log(`[${user}] thấy SĐT: ${txt.includes(PHONE) || txt.includes("0912 345 678")}, thấy nghề: ${txt.includes("Kỹ sư phần mềm")}, thấy tỉnh Hà Nội: ${txt.includes("Hà Nội")}`);
    console.log(`[${user}] notice: ${await page.locator('[aria-label="Mức thông tin đang hiển thị"]').innerText().catch(() => "-")}`);
  });
}

// Chẩn đoán cho NT-TC-12/17: in nguyên văn các dòng gợi ý của ô chọn người khi Trưởng chi gõ một tên
// có nhiều người trùng — để biết người dùng có phân biệt được họ hay không.
import { open, login, shot, verdict, APP } from "./nt-e-lib.mjs";
const { browser, page, p, log } = await open(process.env.NTE_KIND ?? "desktop", "gợi ý");
try {
  await login(page, process.env.NTE_USER ?? "truongchi");
  await page.goto(APP + "/vi/quan-ly/cap-lai-mat-khau", { waitUntil: "networkidle" });
  const c = page.getByRole("combobox", { name: /Ai quên mật khẩu/ });
  await c.click(); await c.fill(process.env.NTE_Q ?? "Nguyễn Thế Tài");
  await page.waitForTimeout(2500);
  for (const s of await page.locator(".ant-select-item-option").allInnerTexts()) p("  gợi ý: " + s.replace(/\s+/g, " "));
  await shot(page, "06b-goi-y");
} finally { verdict(log, p); await browser.close(); }

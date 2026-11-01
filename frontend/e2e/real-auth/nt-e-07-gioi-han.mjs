// NT-TC-17 phần "giới hạn 10 lần/giờ": lượt cấp thứ 11 trong một giờ của CÙNG người cấp, bấm qua màn
// /quan-ly/cap-lai-mat-khau — màn phải nói "chờ rồi thử lại", không phải "máy chủ hỏng".
// (Mười lượt trước đó gọi bằng curl với token của cùng tài khoản — xem báo cáo.)
import { open, login, shot, mainText, pickPerson, verdict, APP } from "./nt-e-lib.mjs";
const E = process.env;
const kind = E.NTE_KIND ?? "desktop";
const { browser, page, p, log } = await open(kind, "giới hạn " + kind);
try {
  await login(page, E.NTE_USER ?? "hoidong");
  await page.goto(APP + "/vi/quan-ly/cap-lai-mat-khau", { waitUntil: "networkidle" });
  await pickPerson(page, page.getByRole("combobox", { name: /Ai quên mật khẩu/ }), E.NTE_Q, E.NTE_MATCH);
  const rP = page.waitForResponse((r) => r.url().endsWith("/api/v1/password-resets") && r.request().method() === "POST");
  await page.getByRole("button", { name: "Cấp liên kết" }).click();
  const r = await rP;
  p(`POST /password-resets -> ${r.status()} ${r.status() >= 400 ? (await r.text()).slice(0, 300) : ""}`);
  await page.waitForTimeout(700);
  p("màn nói: " + (await page.locator(".ant-alert-error").allInnerTexts()).join(" / "));
  await shot(page, `07-gioi-han-${kind}`);
} catch (e) { p("LỖI SCRIPT: " + e.message); await shot(page, "07-loi"); }
finally { verdict(log, p); await browser.close(); }

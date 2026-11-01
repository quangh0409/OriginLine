// Thăm dò: đăng nhập một tài khoản, chụp màn nếu Keycloak không trả về app.
import { open, login, shot, mainText } from "./nt-b-lib.mjs";
const { browser, page, p } = await open(process.env.KIND ?? "desktop", "probe");
try { await login(page, process.env.NTB_ID, process.env.NTB_PW ?? "NghiemThu#2026b", process.env.START ?? "/vi"); }
catch (e) { p("LOGIN FAIL at " + page.url()); await shot(page, "probe-login-fail"); p((await page.locator("body").innerText()).slice(0, 500)); }
await page.waitForTimeout(1500);
await shot(page, "probe-" + (process.env.TAG ?? "x"));
p(page.url()); p((await mainText(page)).slice(0, 1200));
await browser.close();

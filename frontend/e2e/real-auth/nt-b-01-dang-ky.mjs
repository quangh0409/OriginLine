// Dựng tài khoản chưa gắn phả cho nhóm B: đăng ký qua /dang-ky, đặt mật khẩu, đăng nhập lần đầu.
// Dùng: NTB_ID=nghiem-thu-b07@vidu.local node e2e/real-auth/nt-b-01-dang-ky.mjs
import { open, login, shot, mainText, registerAccount } from "./nt-b-lib.mjs";
const id = process.env.NTB_ID;
const { browser, page, p, log } = await open("desktop", "dang-ky " + id);
const pw = await registerAccount(page, id, process.env.NTB_NAME);
await shot(page, "01-dang-ky-" + id.split("@")[0]);
await login(page, id, pw, "/vi");
await page.waitForTimeout(1500);
await shot(page, "01-trang-chu-" + id.split("@")[0]);
p((await mainText(page)).slice(0, 600));
await browser.close();

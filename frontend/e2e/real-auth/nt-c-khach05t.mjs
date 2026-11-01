import { open, BASE } from "./nt-c-lib.mjs";
const { browser, page } = await open("k05t");
const t0 = Date.now();
page.on("request", (r) => { if (/8081|8088/.test(r.url())) console.log(`+${Date.now() - t0}ms REQ ${r.method()} ${r.url().slice(0, 110)}`); });
page.on("response", (r) => { if (/8088/.test(r.url())) console.log(`+${Date.now() - t0}ms RES ${r.status()} ${r.url().slice(0, 90)}`); });
await page.goto(BASE + "/vi/kinship");
await page.getByText("Cần đăng nhập để tra danh xưng").waitFor({ timeout: 30000 });
console.log(`+${Date.now() - t0}ms NOTICE VISIBLE`);
await browser.close();

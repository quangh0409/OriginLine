// Bổ sung NFR-1: mở THẲNG /tree (tải lại cả trang, đã đăng nhập) — gồm cả vòng check-sso của Keycloak.
import { open, login, summary, BASE } from "./nt-c-lib.mjs";
const { browser, page, log } = await open("nfr1-cold");
const runs = [];
try {
  await login(page, "thanhvien");
  for (let i = 0; i < 7; i++) {
    await page.goto(BASE + "/vi/tree");
    await page.getByTestId("person-node").first().waitFor({ timeout: 30000 });
    const ms = await page.evaluate(() => performance.now());
    runs.push(Math.round(ms));
  }
  const s = [...runs].sort((a, b) => a - b);
  console.log(`COLD /tree (navigationStart → thẻ đầu tiên): ${JSON.stringify(runs)} median=${s[3]} ms`);
} finally { summary("nfr1-cold", log); await browser.close(); }

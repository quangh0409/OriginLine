// NFR-1 — điều hướng → thẻ người đầu tiên < 2000 ms trên bản production. 7 lần, lấy trung vị.
// Đo bằng performance.now() TRONG trang: mốc đầu ngay trước cú bấm mục "Cây phả đồ" (điều hướng
// phía client, như người dùng), mốc cuối khi thẻ người đầu tiên (`person-node`) hiện ra.
import { open, login, summary, BASE, DESKTOP, PIXEL5 } from "./nt-c-lib.mjs";
const dev = process.argv[2] ?? "desktop";
const user = process.argv[3] ?? "thanhvien";
const L = `nfr1-${dev}-${user}`;
const { browser, page, log } = await open(L, dev === "pixel5" ? PIXEL5 : DESKTOP);
const runs = [];
try {
  await login(page, user);
  for (let i = 0; i < 7; i++) {
    await page.goto(BASE + "/vi");
    await page.getByTestId("account-button").or(page.getByTestId("notification-bell")).filter({ visible: true }).first().waitFor({ timeout: 60000 });
    await page.waitForLoadState("networkidle").catch(() => {});
    const link = page.locator("a[href$='/tree']").filter({ visible: true }).first();
    const ms = await page.evaluate(async () => {
      const link = [...document.querySelectorAll("a[href$='/tree']")].find((a) => a.offsetParent !== null);
      const t0 = performance.now();
      link.click();
      return await new Promise((resolve, reject) => {
        const tick = () => {
          const card = document.querySelector('[data-testid="person-node"]');
          if (card && card.getBoundingClientRect().width > 0) return resolve(performance.now() - t0);
          if (performance.now() - t0 > 20000) return reject(new Error("timeout"));
          requestAnimationFrame(tick);
        };
        tick();
      });
    });
    void link;
    runs.push(Math.round(ms));
    console.log(`run ${i + 1}: ${Math.round(ms)} ms`);
  }
  const sorted = [...runs].sort((a, b) => a - b);
  console.log(`NFR-1 ${dev} ${user}: runs=${JSON.stringify(runs)} median=${sorted[3]} ms ${sorted[3] < 2000 ? "ĐẠT" : "KHÔNG ĐẠT"} (budget 2000 ms)`);
} finally { summary(L, log); await browser.close(); }

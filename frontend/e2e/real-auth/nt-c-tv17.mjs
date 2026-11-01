// NT-TV-17 — trung tâm thông báo: loại, lọc "Chưa đọc", đánh dấu đã đọc, số trên chuông giảm.
import { open, login, shot, summary, BASE, DESKTOP, PIXEL5 } from "./nt-c-lib.mjs";
const dev = process.argv[2] ?? "desktop";
const L = `tv17-${dev}`;
const { browser, page, log } = await open(L, dev === "pixel5" ? PIXEL5 : DESKTOP);
const bellCount = async () => (await page.getByTestId("notification-bell").filter({ visible: true }).first().locator(".ant-badge-count, sup").first().innerText().catch(() => "0")).replace(/\s+/g, "");
const navBadge = async () => (await page.locator("nav sup, footer sup, [data-testid=bottom-tab-bar] sup").filter({ visible: true }).allInnerTexts().catch(() => [])).join(",");
try {
  await login(page, "thanhvien");
  await page.goto(BASE + "/vi/notifications");
  await page.locator("main li").first().waitFor({ timeout: 30000 }).catch(() => {});
  await page.waitForTimeout(1500);
  await shot(page, `${L}-01-all`);
  console.log("BELL", await bellCount(), "NAVBADGE", await navBadge());
  const items = await page.locator("main ul > li").allInnerTexts();
  console.log("ITEMS", items.length); items.forEach((t, i) => console.log(`  #${i}`, t.replace(/\n+/g, " | ").slice(0, 250)));
  await page.locator(".ant-segmented-item", { hasText: "Chưa đọc" }).click();
  await page.waitForTimeout(2500);
  const unread = await page.locator("main ul > li").allInnerTexts();
  console.log("UNREAD ITEMS", unread.length); unread.forEach((t, i) => console.log(`  u#${i}`, t.replace(/\n+/g, " | ").slice(0, 160)));
  await shot(page, `${L}-02-unread`);
  const btn = page.locator("main ul > li").first().getByRole("button", { name: /Đánh dấu đã đọc/ });
  if (await btn.count()) {
    await btn.click();
    await page.waitForTimeout(3000);
    await shot(page, `${L}-03-after-mark`);
    console.log("AFTER MARK: BELL", await bellCount(), "NAVBADGE", await navBadge(), "unread list", await page.locator("main ul > li").count());
    console.log("TAG", await page.locator("main .ant-tag").allInnerTexts());
  } else console.log("!! không có nút Đánh dấu đã đọc");
} finally { summary(L, log); await browser.close(); }

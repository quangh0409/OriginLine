// NT-TV-09 — cài đặt thông báo đẩy: ca cho phép và ca trình duyệt từ chối quyền.
import { open, login, shot, summary, BASE, DESKTOP, PIXEL5 } from "./nt-c-lib.mjs";
const mode = process.argv[2] ?? "grant"; // grant | deny
const dev = process.argv[3] ?? "desktop";
const L = `tv09-${mode}-${dev}`;
const extra = mode === "grant" ? { permissions: ["notifications"] } : {};
const { browser, page, log } = await open(L, dev === "pixel5" ? PIXEL5 : DESKTOP, extra);
try {
  await login(page, "thanhvien");
  await page.goto(BASE + "/vi/settings");
  await page.waitForTimeout(4000);
  const sw = await page.evaluate(async () => {
    const regs = await navigator.serviceWorker?.getRegistrations?.();
    return { regs: regs?.map((r) => r.active?.scriptURL ?? r.installing?.scriptURL), perm: typeof Notification !== "undefined" ? Notification.permission : "n/a", push: "PushManager" in window };
  });
  console.log("SW", JSON.stringify(sw));
  await shot(page, `${L}-01`);
  console.log("MAIN BEFORE:", (await page.locator("main").innerText()).slice(0, 1200).replace(/\n+/g, " | "));
  const sws = page.getByRole("switch");
  console.log("switches", await sws.count());
  const push = page.getByRole("switch", { name: /Thông báo đẩy/ }).first();
  if (await push.count()) {
    console.log("push switch checked:", await push.getAttribute("aria-checked"));
    await push.click();
    await page.waitForTimeout(6000);
    await shot(page, `${L}-02-after-toggle`);
    console.log("push switch checked after:", await push.getAttribute("aria-checked").catch(() => "(gone)"));
    console.log("MAIN AFTER:", (await page.locator("main").innerText()).slice(0, 1500).replace(/\n+/g, " | "));
  } else console.log("!! không có công tắc thông báo đẩy");
} finally { summary(L, log); await browser.close(); }

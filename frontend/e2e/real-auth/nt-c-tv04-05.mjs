// NT-TV-04 + NT-TV-05 — mức chia sẻ theo nhóm trường, Danh bạ, cảnh báo "Riêng tư" vẫn để Hội đồng đọc.
import { open, login, shot, summary, BASE, DESKTOP, PIXEL5 } from "./nt-c-lib.mjs";
const which = process.argv[2] ?? "desktop";
const L = `tv04-${which}`;
const { browser, page, log } = await open(L, which === "pixel5" ? PIXEL5 : DESKTOP);
try {
  await login(page, "thanhvien");
  await page.goto(BASE + "/vi/tree");
  await page.getByTestId("person-node").first().waitFor({ timeout: 60000 });
  await page.getByTestId("tree-go-to-self").click();
  const selfNode = page.getByTestId("person-node").filter({ has: page.getByTestId("person-node-self") }).first();
  await selfNode.waitFor({ timeout: 30000 });
  await page.waitForTimeout(1200);
  await selfNode.click();
  await page.waitForTimeout(2500);
  await shot(page, `${L}-01-self-drawer`);
  const drawer = page.locator(".ant-drawer-content").first();
  const links = await drawer.locator("a").evaluateAll((as) => as.map((a) => `${a.textContent?.trim()} -> ${a.getAttribute("href")}`));
  console.log("DRAWER LINKS", links.join(" || "));
  console.log("DRAWER HAS SHARING CARD:", await drawer.locator("section[data-privacy-sharing]").count());
  // Khối "Ai xem được gì về tôi" nằm ngay trong ngăn hồ sơ mở từ ô của chính mình.
  const card = drawer.locator("section[data-privacy-sharing]");
  await card.waitFor({ timeout: 30000 });
  await card.scrollIntoViewIfNeeded();
  await shot(page, `${L}-02-sharing-card`);
  const cardText = await card.innerText();
  console.log("CARD TEXT:", cardText.slice(0, 2500).replace(/\n+/g, " | "));
  const row = (label) => card.locator('[role="group"]').filter({ has: page.locator("h3", { hasText: label }) }).first();
  async function setScope(label, scopeLabel) {
    const r = row(label);
    await r.locator(".ant-radio-button-wrapper", { hasText: scopeLabel }).click();
  }
  await setScope("Nghề nghiệp & nơi làm việc", "Cả họ xem");
  await setScope("Nơi ở (tỉnh/thành)", "Cả họ xem");
  await setScope("Liên hệ: điện thoại · thư điện tử · Zalo", "Riêng tư");
  const status = card.locator('[role="status"]');
  console.log("STATUS before save:", await status.innerText());
  console.log("CONTACT ROW hint:", (await row("Liên hệ").innerText()).replace(/\n+/g, " | "));
  const saveBtn = card.getByRole("button", { name: "Lưu mức chia sẻ" });
  if (await saveBtn.isEnabled()) {
    await saveBtn.click();
    await page.waitForTimeout(2500);
  } else console.log("(không có thay đổi — mức đã được đặt từ trước)");
  console.log("STATUS after save:", await status.innerText());
  await shot(page, `${L}-03-saved`);
  await page.waitForTimeout(1500);
  console.log("STATUS later:", await status.innerText());
  const preview = await card.locator("text=Bảng tóm tắt").locator("..").innerText().catch(() => "");
  console.log("PREVIEW:", preview.replace(/\n+/g, " | ").slice(0, 800));
  // Danh bạ
  await page.goto(BASE + "/vi/danh-ba");
  await page.waitForTimeout(3000);
  const search = page.locator('input[type="search"], input[placeholder]').first();
  await search.fill("Hoàng Diệu Hoa");
  await page.waitForTimeout(3000);
  await shot(page, `${L}-04-danh-ba`);
  console.log("DANH BA:", (await page.locator("main").innerText()).slice(0, 1500).replace(/\n+/g, " | "));
} finally { summary(L, log); await browser.close(); }

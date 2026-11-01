// NT-KHACH-05/06 phần đối chứng: cùng một cái tên người còn sống, khách vs thành viên.
import { open, login, shot, summary, BASE, DESKTOP, PIXEL5 } from "./nt-c-lib.mjs";
const NAME = "Hoàng Diệu Hoa";
const dev = process.argv[2] ?? "desktop";
async function searchAndDump(page, who) {
  await page.goto(BASE + "/vi/search");
  const q = page.locator("#person-search-q");
  await q.waitFor({ timeout: 30000 });
  await q.fill(NAME);
  await page.waitForTimeout(3500);
  await shot(page, `khach-vs-tv-${who}-${dev}-search`);
  const items = await page.locator("main li, main article, main a[href*='/persons/']").allInnerTexts();
  const lines = [...new Set(items.map((t) => t.replace(/\n+/g, " | ").slice(0, 120)))];
  const exact = lines.filter((l) => l.includes(NAME));
  const alive = lines.filter((l) => l.includes("Còn sống"));
  console.log(`[${who}] ${lines.length} dòng; khớp đúng tên: ${exact.length}; ghi "Còn sống": ${alive.length}`);
  lines.slice(0, 6).forEach((l) => console.log(`   ${l}`));
}
{ // KHÁCH
  const { browser, page, log } = await open(`khach-${dev}`, dev === "pixel5" ? PIXEL5 : DESKTOP);
  try {
    await searchAndDump(page, "khach");
    await page.goto(BASE + "/vi/kinship");
    await page.waitForTimeout(3000);
    await shot(page, `khach-vs-tv-khach-${dev}-kinship`);
    const txt = await page.locator("main").innerText();
    console.log("[khach] kinship:", txt.slice(0, 500).replace(/\n+/g, " | "));
    console.log("[khach] có ô chọn người:", await page.locator("#kinship-from, #kinship-to").count());
    const navText = await page.locator("header").innerText();
    console.log("[khach] header có 'Tra danh xưng':", navText.includes("Tra danh xưng"));
  } finally { summary(`khach-${dev}`, log); await browser.close(); }
}
{ // THÀNH VIÊN
  const { browser, page, log } = await open(`tv-${dev}`, dev === "pixel5" ? PIXEL5 : DESKTOP);
  try { await login(page, "thanhvien"); await searchAndDump(page, "thanhvien"); }
  finally { summary(`tv-${dev}`, log); await browser.close(); }
}

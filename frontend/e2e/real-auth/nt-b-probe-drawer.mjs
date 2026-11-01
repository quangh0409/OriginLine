// Thăm dò: người dùng bấm vào ô của mình trên canvas → ngăn hồ sơ mở ra. Nút "Đây là tôi" ở dải
// dưới ô tìm có bấm tới được không?
import { open, login, shot, APP } from "./nt-b-lib.mjs";
const KIND = process.env.KIND ?? "desktop";
const { browser, page, p } = await open(KIND, "probe-drawer-" + KIND);
await login(page, process.env.NTB_ID, "NghiemThu#2026b", "/vi");
await page.goto(APP + "/vi/tree", { waitUntil: "networkidle" });
await page.getByTestId("tree-jump-search").fill("Nguyễn Hữu Khiêm");
await page.getByTestId("tree-jump-result").filter({ hasText: "Nguyễn Hữu Khiêm" }).first().click();
await page.waitForTimeout(2500);
p("sau chọn kết quả tìm, dải: " + (await page.getByTestId("tree-claim-actions").innerText()).replace(/\s+/g, " "));
await shot(page, `probe-drawer-${KIND}-1-sau-tim`);
await page.getByTestId("person-node-name").filter({ hasText: "Nguyễn Hữu Khiêm" }).first().click();
await page.waitForTimeout(2500);
await shot(page, `probe-drawer-${KIND}-2-bam-node`);
const btn = page.getByTestId("tree-claim-this-is-me");
p("nút Đây là tôi count=" + (await btn.count()));
if (await btn.count()) {
  const box = await btn.boundingBox();
  p("box " + JSON.stringify(box));
  await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
  await page.waitForTimeout(2000);
  p("sau khi bấm vào chỗ nút: url=" + page.url() + " · nút còn=" + (await btn.count()));
  await shot(page, `probe-drawer-${KIND}-3-bam-nut`);
}
await browser.close();

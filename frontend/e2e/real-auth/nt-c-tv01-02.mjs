// NT-TV-01 + NT-TV-02 — phả đồ của thành viên, "Về chỗ tôi", tìm theo tên trên canvas.
import { open, login, shot, summary, BASE, DESKTOP, PIXEL5 } from "./nt-c-lib.mjs";
const which = process.argv[2] ?? "desktop";
const L = `tv01-${which}`;
const { browser, page, log } = await open(L, which === "pixel5" ? PIXEL5 : DESKTOP);
try {
  await login(page, "thanhvien");
  await page.goto(BASE + "/vi/tree");
  await page.getByTestId("person-node").first().waitFor({ timeout: 60000 });
  await page.waitForTimeout(1500);
  await shot(page, `${L}-01-open`);
  console.log("URL", page.url(), "count", await page.getByTestId("tree-node-count").innerText().catch(() => "?"));

  // TV-02 bước 1: Về chỗ tôi
  await page.getByTestId("tree-go-to-self").click();
  const self = page.getByTestId("person-node-self");
  await self.first().waitFor({ timeout: 30000 }).catch(() => console.log("!! không thấy person-node-self"));
  await page.waitForTimeout(2000);
  await shot(page, `${L}-02-go-to-self`);
  console.log("URL after self", page.url());
  const selfNode = page.getByTestId("person-node").filter({ has: page.getByTestId("person-node-self") }).first();
  const selfText = await selfNode.innerText().catch(() => "");
  console.log("SELF NODE:", selfText.replace(/\n/g, " | "));
  const box = await selfNode.boundingBox();
  const vp = page.viewportSize();
  console.log("SELF BOX", JSON.stringify(box), "viewport", JSON.stringify(vp),
    box && box.x >= 0 && box.y >= 0 && box.x + box.width <= vp.width && box.y + box.height <= vp.height ? "IN-VIEW" : "OUT-OF-VIEW");

  // TV-01: node còn sống
  const texts = await page.getByTestId("person-node").allInnerTexts();
  const alive = texts.filter((t) => t.includes("Còn sống"));
  console.log("nodes", texts.length, "alive", alive.length);
  console.log(alive.slice(0, 8).map((t) => t.replace(/\n/g, " | ")).join("\n"));
  const aliveOther = page.getByTestId("person-node").filter({ hasText: "Còn sống" }).filter({ hasNot: page.getByTestId("person-node-self") }).first();
  await aliveOther.scrollIntoViewIfNeeded().catch(() => {});
  await aliveOther.click();
  await page.waitForTimeout(2500);
  await shot(page, `${L}-03-alive-profile`);
  const drawer = page.locator(".ant-drawer-content").first();
  const dt = await drawer.innerText().catch(() => "");
  console.log("DRAWER:", dt.slice(0, 1200).replace(/\n+/g, " | "));
  console.log("HAS noticeLabel:", dt.includes("Mức thông tin đang hiển thị"));
  await page.keyboard.press("Escape");
  await page.waitForTimeout(800);

  // TV-02 bước 2: gõ tên người khác
  const q = process.env.Q ?? "Nguyễn Đình Bách";
  await page.getByTestId("tree-jump-search").fill(q);
  await page.getByTestId("tree-jump-result").first().waitFor({ timeout: 20000 });
  const results = await page.getByTestId("tree-jump-result").allInnerTexts();
  console.log("JUMP RESULTS", results.slice(0, 6).map((t) => t.replace(/\n/g, " ")).join(" || "));
  await page.getByTestId("tree-jump-result").first().click();
  await page.waitForTimeout(3000);
  await shot(page, `${L}-04-jump`);
  console.log("URL after jump", page.url());
  const target = page.getByTestId("person-node").filter({ hasText: q.split(" ").pop() }).first();
  const tb = await target.boundingBox().catch(() => null);
  console.log("TARGET BOX", JSON.stringify(tb));
  console.log("BANNER", await page.getByTestId("tree-root-banner").innerText().catch(() => "-"));
} finally { summary(L, log); await browser.close(); }

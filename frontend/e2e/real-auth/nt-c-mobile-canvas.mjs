// Pixel 5: phả đồ phải kéo / zoom / mở-gập được bằng NGÓN TAY (CDP touch), không chỉ chuột.
import { open, login, shot, summary, BASE, PIXEL5 } from "./nt-c-lib.mjs";
const L = "mobile-canvas";
const { browser, page, log } = await open(L, PIXEL5);
const tf = () => page.locator(".react-flow__viewport").first().getAttribute("style");
try {
  await login(page, "thanhvien");
  await page.goto(BASE + "/vi/tree");
  await page.getByTestId("person-node").first().waitFor({ timeout: 60000 });
  await page.waitForTimeout(1500);
  await shot(page, `${L}-01`);
  const cdp = await page.context().newCDPSession(page);
  const pane = await page.locator(".react-flow__pane").first().boundingBox();
  console.log("PANE", JSON.stringify(pane));
  const cx = pane.x + pane.width / 2, cy = pane.y + pane.height / 2;
  const t0 = await tf();
  // Kéo một ngón
  await cdp.send("Input.dispatchTouchEvent", { type: "touchStart", touchPoints: [{ x: cx, y: cy }] });
  for (let i = 1; i <= 10; i++) await cdp.send("Input.dispatchTouchEvent", { type: "touchMove", touchPoints: [{ x: cx - i * 10, y: cy - i * 6 }] });
  await cdp.send("Input.dispatchTouchEvent", { type: "touchEnd", touchPoints: [] });
  await page.waitForTimeout(500);
  const t1 = await tf();
  console.log("DRAG", t0, "=>", t1, t0 !== t1 ? "MOVED" : "NOT-MOVED");
  // Chụm hai ngón: cử chỉ pinch do chính trình duyệt tổng hợp (Input.synthesizePinchGesture).
  await cdp.send("Input.synthesizePinchGesture", { x: cx, y: cy, scaleFactor: 1.8, gestureSourceType: "touch" });
  await page.waitForTimeout(800);
  const t2 = await tf();
  console.log("PINCH", t1, "=>", t2, t1 !== t2 ? "ZOOMED" : "NOT-ZOOMED");
  // Nút + / −
  const zin = page.locator(".react-flow__controls-zoomin").first();
  if (await zin.count()) { await zin.tap(); await page.waitForTimeout(400); console.log("ZOOMIN btn", await tf()); }
  await shot(page, `${L}-02-after-gestures`);
  // Mở / gập — trước hết thu về toàn cây để có nút trong khung.
  await page.getByTestId("tree-fit-whole").tap();
  await page.waitForTimeout(1200);
  const before = await page.getByTestId("person-node").count();
  const toggles = page.getByTestId("tree-node-toggle");
  console.log("toggles", await toggles.count());
  const label = () => page.getByTestId("tree-node-count").innerText();
  const vp = page.viewportSize();
  const all = await toggles.all();
  let target = null;
  for (const t of all) {
    const b = await t.boundingBox();
    if (b && (await t.getAttribute("aria-expanded")) === "false" && b.y > 360 && b.y + b.height < vp.height - 60) { target = t; break; }
  }
  if (!target) { console.log("!! không có toggle gập nào trong khung"); }
  else {
    const l0 = await label();
    await target.tap();
    await page.waitForTimeout(3000);
    const l1 = await label();
    console.log("EXPAND", l0, "=>", l1, "aria", await target.getAttribute("aria-expanded"));
    await shot(page, `${L}-03-expanded`);
    await target.tap().catch((e) => console.log("tap2 fail", String(e).slice(0, 200)));
    await page.waitForTimeout(1500);
    console.log("COLLAPSE", l1, "=>", await label(), "aria", await target.getAttribute("aria-expanded"));
  }
} finally { summary(L, log); await browser.close(); }

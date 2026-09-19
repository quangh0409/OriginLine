/**
 * Đo VÙNG CHẠM THẬT của nút bung/thu nhánh trên phả đồ, ở đúng mức phóng mà cây MỞ RA.
 *
 * <p>Đây là phép đo đứng sau con số trong báo cáo "trước / sau" của việc nới vùng chạm nút bung
 * nhánh. Nó cố ý KHÔNG đọc CSS: nó đọc {@code getBoundingClientRect()} của chính phần tử nhận cú
 * chạm, sau khi React Flow đã áp phép biến đổi máy quay — tức đúng số điểm ảnh CSS mà ngón tay
 * người dùng phải trúng.</p>
 *
 * <p>Ba con số cho mỗi khung máy:</p>
 * <ul>
 *   <li><b>scale</b> — mức phóng phả đồ đang mở ra (sàn {@code MIN_INITIAL_ZOOM}).</li>
 *   <li><b>hit</b> — vùng chạm tính bằng px THẬT trên màn hình. Đây là con số phải ≥ 44.</li>
 *   <li><b>knob</b> — phần NHÌN THẤY (vòng tròn có dấu +/−), cố ý giữ nguyên 24px hệ toạ độ cây.</li>
 * </ul>
 *
 * <pre>
 *   npm run dev:mock            # cổng 3100
 *   node e2e/tools/touch-target.mjs
 * </pre>
 */
import { chromium, devices } from "@playwright/test";

const BASE = process.env.E2E_BASE_URL ?? "http://127.0.0.1:3100";
const TARGET_PX = 44;

const frames = [
  ["Desktop 1440x900", { viewport: { width: 1440, height: 900 } }],
  ["Pixel 5 (393x851)", devices["Pixel 5"]],
];

const browser = await chromium.launch();

for (const [name, opts] of frames) {
  const ctx = await browser.newContext({ ...opts, locale: "vi-VN", timezoneId: "Asia/Ho_Chi_Minh" });
  await ctx.addInitScript(() => window.localStorage.setItem("giapha.dev-role", "member"));
  const page = await ctx.newPage();
  await page.goto(`${BASE}/tree`, { waitUntil: "networkidle", timeout: 180_000 });
  await page.waitForSelector(".react-flow__node", { timeout: 60_000 });
  // fitView chạy 300ms; đọc giữa chừng là đọc một mức phóng người dùng không bao giờ thấy.
  await page.waitForTimeout(3_000);

  const measured = await page.evaluate(() => {
    const style = document.querySelector(".react-flow__viewport")?.getAttribute("style") ?? "";
    const scale = Number(/scale\(([\d.]+)\)/.exec(style)?.[1] ?? "1");
    const canvas = document.querySelector(".react-flow")?.getBoundingClientRect();
    const view = canvas
      ? {
          left: Math.max(canvas.left, 0),
          top: Math.max(canvas.top, 0),
          right: Math.min(canvas.right, window.innerWidth),
          bottom: Math.min(canvas.bottom, window.innerHeight),
        }
      : null;

    const rows = [];
    for (const button of document.querySelectorAll(".react-flow__node button")) {
      const r = button.getBoundingClientRect();
      if (!r.width || !r.height) continue;
      const cx = r.left + r.width / 2;
      const cy = r.top + r.height / 2;
      const inCanvas =
        !view || (cx >= view.left && cx <= view.right && cy >= view.top && cy <= view.bottom);
      // Phần nhìn thấy: vòng tròn bên trong nút, nếu có; nếu không thì chính nút.
      const knobEl = button.querySelector("[data-testid='tree-node-toggle-knob']") ?? button;
      const k = knobEl.getBoundingClientRect();
      const hit = document.elementFromPoint(cx, cy);
      rows.push({
        id: button.closest(".react-flow__node")?.getAttribute("data-id") ?? "?",
        inCanvas,
        w: +r.width.toFixed(2),
        h: +r.height.toFixed(2),
        kw: +k.width.toFixed(2),
        kh: +k.height.toFixed(2),
        reachesItself: Boolean(hit && button.contains(hit)),
      });
    }
    return { scale: +scale.toFixed(4), rows };
  });

  const inCanvas = measured.rows.filter((r) => r.inCanvas);
  const sample = inCanvas.length > 0 ? inCanvas : measured.rows;
  const min = (key) => (sample.length ? Math.min(...sample.map((r) => r[key])) : 0);
  const blocked = sample.filter((r) => !r.reachesItself);

  const hitW = min("w");
  const hitH = min("h");
  console.log(
    [
      `${name}`,
      `  scale mở ra      : ${measured.scale}`,
      `  nút đo được      : ${sample.length} (trong khung canvas)`,
      `  VÙNG CHẠM thật   : ${hitW} x ${hitH} px  ${hitW >= TARGET_PX && hitH >= TARGET_PX ? "✔ ĐẠT" : "✘ DƯỚI"} sàn ${TARGET_PX}px`,
      `  vùng chạm 1:1    : ${(hitW / measured.scale).toFixed(1)} x ${(hitH / measured.scale).toFixed(1)} px (hệ toạ độ cây)`,
      `  phần NHÌN THẤY   : ${min("kw")} x ${min("kh")} px thật`,
      `  chạm giữa nút    : ${blocked.length === 0 ? "trúng nút" : `${blocked.length} nút bị che`}`,
    ].join("\n")
  );

  await ctx.close();
}

await browser.close();

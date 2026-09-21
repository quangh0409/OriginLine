/**
 * ĐO BỀ RỘNG THẬT CỦA MỘT ĐỜI TRÊN PHẢ ĐỒ — trong trình duyệt, không phải trên giấy.
 *
 * Chủ dự án nói "dàn ra quá to". Checklist mục 5 quy câu ấy ra số bằng cách NHÂN hằng số bố cục
 * với số gia đình. Tệp này làm việc còn lại: đọc toạ độ mà React Flow thật sự đặt cho từng tấm
 * thẻ, gom theo đời, rồi in bề rộng của đời rộng nhất.
 *
 * <h2>Đo trong HỆ TOẠ ĐỘ CÂY, không phải pixel màn hình</h2>
 * Mỗi `.react-flow__node` mang `transform: translate(x, y)` tính bằng đơn vị của cây — tức cùng
 * đơn vị với `NODE_WIDTH`, `COUPLE_GAP`, `FAMILY_GAP`. Đo bằng `getBoundingClientRect()` thì con
 * số còn phụ thuộc mức phóng hiện tại, nên hai lần chạy khác khung nhìn sẽ cho hai con số khác
 * nhau mà chẳng có gì đổi.
 *
 * <h2>PHẢI thu toàn cây trước MỖI phép đo</h2>
 * `<ReactFlow onlyRenderVisibleElements>` **xoá khỏi DOM** mọi thẻ nằm ngoài khung nhìn. Đó là
 * đúng cho hiệu năng và là sai cho phép đo: bản đầu tiên của tệp này đếm được đúng 8 thẻ dù đã
 * bung thêm ba đời, và bề rộng "đo được" hoá ra là bề rộng của cái khung nhìn. Nên trước mỗi phép
 * đo phải bấm "Thu toàn cây" (bỏ sàn phóng, xem `fit-view.ts`) để cả cây nằm trong khung — lúc ấy
 * không còn thẻ nào bị xén.
 *
 * <h2>Bung nhiều vòng trước khi đo</h2>
 * Phả đồ nay mở ra ở trạng thái gập (chỉ đường từ gốc tới người đang xem). Đo ngay lúc ấy là đo
 * một cái cây chưa ai bung — vừa không so sánh được với con số 4.352px của checklist, vừa giấu
 * mất việc bề rộng còn lại đến từ đâu. Nên script bung dần từng vòng và in bề rộng sau mỗi vòng:
 * cột `count` cho biết đời ấy đang có bao nhiêu người, tức so được thẳng với phép nhân của
 * checklist.
 *
 * Dùng: `npm run dev:mock` ở một cửa sổ khác, rồi `node e2e/tools/generation-width.mjs`.
 */
import { chromium } from "@playwright/test";

const URL = process.env.E2E_BASE_URL ?? "http://127.0.0.1:3100";
const ROLE = process.env.MEASURE_ROLE ?? "member";

const browser = await chromium.launch();
const ctx = await browser.newContext({
  viewport: { width: 1440, height: 900 },
  locale: "vi-VN",
  timezoneId: "Asia/Ho_Chi_Minh",
});
await ctx.addInitScript(
  ([role]) => window.localStorage.setItem("giapha.dev-role", role),
  [ROLE]
);
const page = await ctx.newPage();
await page.goto(`${URL}/tree`, { waitUntil: "domcontentloaded", timeout: 180_000 });
await page.waitForSelector(".react-flow__node", { timeout: 120_000 });
await page.waitForTimeout(2500);

/** Toạ độ từng thẻ trong HỆ TOẠ ĐỘ CÂY, đọc thẳng từ `transform` React Flow vừa đặt. */
async function readNodes() {
  return page.evaluate(() => {
    const out = [];
    for (const el of document.querySelectorAll(".react-flow__node")) {
      const m = /translate\((-?[\d.]+)px,\s*(-?[\d.]+)px\)/.exec(el.style.transform ?? "");
      if (!m) continue;
      out.push({
        id: el.getAttribute("data-id") ?? "?",
        x: Number(m[1]),
        y: Number(m[2]),
        w: el.getBoundingClientRect().width,
      });
    }
    return out;
  });
}

/** Thu cả cây vào khung nhìn, để `onlyRenderVisibleElements` không xén mất thẻ nào. */
async function fitWholeTree() {
  await page.getByTestId("tree-fit-whole").click({ timeout: 10_000 }).catch(() => {});
  await page.waitForTimeout(1200);
}

/** Bề rộng của từng đời: hàng thẻ gom theo y, rộng = mép phải xa nhất − mép trái gần nhất. */
async function measure(label) {
  await fitWholeTree();
  const nodes = await readNodes();
  const scale = await page.evaluate(() => {
    const t = document.querySelector(".react-flow__viewport")?.style.transform ?? "";
    return Number(/scale\(([\d.]+)\)/.exec(t)?.[1] ?? "1");
  });
  // Bề ngang thẻ trong hệ toạ độ cây = bề ngang trên màn hình chia cho mức phóng.
  const cardWidth = nodes.length ? Math.round(nodes[0].w / scale) : 0;

  const rows = new Map();
  for (const n of nodes) {
    const key = Math.round(n.y);
    if (!rows.has(key)) rows.set(key, []);
    rows.get(key).push(n);
  }

  const generations = [...rows.entries()]
    .map(([y, list]) => ({
      y,
      count: list.length,
      width: Math.round(
        Math.max(...list.map((n) => n.x)) + cardWidth - Math.min(...list.map((n) => n.x))
      ),
    }))
    .sort((a, b) => a.y - b.y);

  const widest = generations.reduce((a, b) => (b.width > a.width ? b : a), generations[0]);

  console.log(
    JSON.stringify(
      {
        label,
        cardWidth,
        nodeCount: nodes.length,
        generations,
        widestGeneration: widest,
        /** Bề rộng toàn cây — con số "phải kéo qua mấy màn hình 1440px". */
        totalWidth: Math.round(
          Math.max(...nodes.map((n) => n.x)) + cardWidth - Math.min(...nodes.map((n) => n.x))
        ),
      },
      null,
      1
    )
  );
}

await measure("mo-ra (gap mac dinh)");

/**
 * Bung một vòng — chỉ bấm những nút ĐANG GẬP.
 *
 * Nút này là nút BẬT/TẮT: bấm hết mọi nút đang thấy sẽ thu gọn luôn những nhánh vừa mở, và phép
 * đo cho ra một cái cây NHỎ HƠN lúc ban đầu. Lọc theo `aria-expanded="false"` là cách duy nhất
 * đúng, và nó cũng là thứ trình đọc màn hình đọc — nên nếu thuộc tính ấy sai thì phép đo này hỏng
 * cùng lúc với phần tiếp cận.
 *
 * `force: true` vì ở mức "thu toàn cây" tấm thẻ có thể chỉ còn vài pixel: Playwright sẽ từ chối
 * một cú bấm mà nó cho là không tới được, trong khi ở đây ta CỐ Ý thao tác bằng mã chứ không giả
 * làm người dùng.
 */
async function expandOneRing() {
  await fitWholeTree();
  const collapsed = await page
    .locator('[data-testid="tree-node-toggle"][aria-expanded="false"]')
    .all();
  for (const toggle of collapsed) {
    await toggle.click({ timeout: 5000, force: true }).catch(() => {});
  }
  await page.waitForTimeout(2500);
  return collapsed.length;
}

const RINGS = Number(process.env.MEASURE_RINGS ?? 4);
for (let ring = 1; ring <= RINGS; ring += 1) {
  const opened = await expandOneRing();
  await measure(`sau ${ring} vong bung (${opened} nut vua mo)`);
  if (opened === 0) break;
}

await browser.close();

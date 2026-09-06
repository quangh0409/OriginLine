import { devices, type Page } from "@playwright/test";
import { expect, signInAs, test, waitForTreeReady } from "./fixtures";

/**
 * Phả đồ phải ĐỌC ĐƯỢC — đo trên trình duyệt thật.
 *
 * <p>Người dùng nói: "các đường nối liên kết đang bị che bởi các ô tên, nhìn không được tường
 * minh". {@code tests/unit/tree/geometry-invariants.test.ts} ghim điều đó ở tầng toạ độ; tệp này
 * ghim nó ở tầng cuối cùng người dùng thực sự nhìn thấy — SVG đã dựng xong, đã áp phép biến đổi
 * của máy quay, trên đúng đồ thị giả lập mà màn hình đang chạy.</p>
 *
 * <p>Hai điều được đo, vì chúng hỏng theo hai kiểu khác nhau:</p>
 *
 * <ol>
 *   <li><b>Hình</b> — không điểm nào của một đường nối được rơi vào trong vùng thẻ nhân khẩu.
 *       Lấy mẫu dọc theo từng {@code <path>} thật chứ không lấy khung bao: một đường gấp khúc chữ
 *       L có khung bao trùm cả tấm thẻ mà vẫn không hề chạm vào nó, nên khung bao sẽ báo sai.</li>
 *   <li><b>Tương tác</b> — chạm vào giữa một tấm thẻ phải TRÚNG thẻ, không trúng {@code <path>}
 *       của đường nối. Đây là lỗi cũ mà {@code zIndex: -1} từng phải chữa (xem
 *       {@code to-flow-elements.ts}); bố cục mới phải làm nó biến mất chứ không tái diễn. Kiểm ở
 *       MỨC THU NHỎ, vì đó là lúc lỗi cũ lộ ra: thẻ co lại còn đường nối thì không.</li>
 * </ol>
 *
 * <p>Chạy trên MSW (`npm run dev:mock`) như toàn bộ bộ e2e hiện có.</p>
 *
 * <p><b>Phạm vi:</b> chỉ chế độ <b>Phân cấp</b> — đó là chế độ dựng theo đơn vị gia đình. Toả tròn
 * và Ma trận đời cố ý vẫn dùng đường nối kiểu cũ (không có khái niệm gia đình để mà nối vào), nên
 * đem bất biến "mọi đoạn thẳng đứng hoặc nằm ngang" ra chấm hai chế độ ấy là đo nhầm đối tượng.</p>
 */

/** Nửa chiều rộng nét vẽ + khử răng cưa. Chạm mép thẻ là hợp lệ; ăn sâu hơn thế thì không. */
const EDGE_TOLERANCE_PX = 3;

interface Overlap {
  readonly nodeId: string;
  readonly edge: string;
  readonly x: number;
  readonly y: number;
  /** Điểm lấy mẫu nằm sâu bao nhiêu px so với mép thẻ gần nhất. */
  readonly depth: number;
}

/**
 * Mọi điểm trên mọi đường nối đang hiển thị mà lại nằm TRONG một tấm thẻ nhân khẩu.
 *
 * <p>Chỉ xét lớp cạnh của React Flow ({@code .react-flow__edges}) — biểu tượng trong nút bấm của
 * {@code <Controls>} và bản đồ thu nhỏ cũng là {@code <path>}, gộp chúng vào thì đang đo nhầm thứ
 * khác.</p>
 */
async function edgesOverlappingCards(page: Page, tolerance: number): Promise<Overlap[]> {
  return page.evaluate((tol) => {
    const canvas = document.querySelector(".react-flow")?.getBoundingClientRect();
    if (!canvas) return [];
    const view = {
      left: Math.max(canvas.left, 0),
      top: Math.max(canvas.top, 0),
      right: Math.min(canvas.right, window.innerWidth),
      bottom: Math.min(canvas.bottom, window.innerHeight),
    };
    const onScreen = (r: DOMRect): boolean =>
      r.width > 0 &&
      r.height > 0 &&
      r.right > view.left &&
      r.left < view.right &&
      r.bottom > view.top &&
      r.top < view.bottom;

    const cards = [...document.querySelectorAll<HTMLElement>(".react-flow__node")]
      .map((el) => ({ id: el.getAttribute("data-id") ?? "?", rect: el.getBoundingClientRect() }))
      .filter((c) => onScreen(c.rect));

    const out: {
      nodeId: string;
      edge: string;
      x: number;
      y: number;
      depth: number;
    }[] = [];

    const paths = [
      ...document.querySelectorAll<SVGPathElement>(".react-flow__edges path"),
    ].filter((p) => p.closest('[data-testid="tuyet-tu-mark"]') === null);

    for (const path of paths) {
      const ctm = path.getScreenCTM();
      if (!ctm) continue;
      let total = 0;
      try {
        total = path.getTotalLength();
      } catch {
        continue;
      }
      if (!Number.isFinite(total) || total === 0) continue;

      // Lấy mẫu mỗi ~4px, tối đa 400 điểm cho một đường: đủ dày để không lọt một tấm thẻ rộng
      // 208px, mà vẫn chạy nhanh trên cả cây.
      const steps = Math.min(400, Math.max(2, Math.ceil(total / 4)));
      const label =
        path.getAttribute("data-testid") ??
        path.closest("[data-testid]")?.getAttribute("data-testid") ??
        path.closest(".react-flow__edge")?.getAttribute("data-id") ??
        "path";

      for (let i = 0; i <= steps; i += 1) {
        const local = path.getPointAtLength((total * i) / steps);
        const point = new DOMPoint(local.x, local.y).matrixTransform(ctm);
        for (const card of cards) {
          const r = card.rect;
          const inside =
            point.x > r.left + tol &&
            point.x < r.right - tol &&
            point.y > r.top + tol &&
            point.y < r.bottom - tol;
          if (!inside) continue;
          const depth = Math.min(
            point.x - r.left,
            r.right - point.x,
            point.y - r.top,
            r.bottom - point.y
          );
          out.push({
            nodeId: card.id,
            edge: label,
            x: Math.round(point.x),
            y: Math.round(point.y),
            depth: Math.round(depth),
          });
        }
      }
    }

    // Gộp theo (thẻ, đường) để báo cáo đọc được thay vì hàng nghìn điểm mẫu.
    const worst = new Map<string, (typeof out)[number]>();
    for (const hit of out) {
      const key = `${hit.nodeId}|${hit.edge}`;
      const current = worst.get(key);
      if (!current || hit.depth > current.depth) worst.set(key, hit);
    }
    return [...worst.values()].sort((a, b) => b.depth - a.depth);
  }, tolerance);
}

interface BlockedCard {
  readonly nodeId: string;
  readonly hit: string;
  readonly tag: string;
  readonly byEdge: boolean;
}

/**
 * Thẻ nào nhận được cú chạm vào chính giữa mình, thẻ nào không.
 *
 * <p>Bỏ qua những thẻ bị lớp phủ của canvas che (Controls / MiniMap / chú giải): đó là bố cục lớp
 * phủ, không phải chuyện đường nối. Còn nếu thứ chắn là một {@code <path>} của lớp cạnh thì đúng
 * là lỗi cũ tái diễn.</p>
 */
async function cardsNotReceivingTheirOwnTap(page: Page): Promise<{
  checked: number;
  blocked: BlockedCard[];
}> {
  return page.evaluate(() => {
    const canvas = document.querySelector(".react-flow")?.getBoundingClientRect();
    if (!canvas) return { checked: 0, blocked: [] as BlockedCard[] };
    const view = {
      left: Math.max(canvas.left, 0),
      top: Math.max(canvas.top, 0),
      right: Math.min(canvas.right, window.innerWidth),
      bottom: Math.min(canvas.bottom, window.innerHeight),
    };
    const OVERLAY = ".react-flow__panel, .react-flow__minimap, .react-flow__controls, .ant-drawer";

    let checked = 0;
    const blocked: BlockedCard[] = [];
    for (const el of document.querySelectorAll<HTMLElement>(".react-flow__node")) {
      const r = el.getBoundingClientRect();
      if (r.width === 0 || r.height === 0) continue;
      const cx = r.left + r.width / 2;
      const cy = r.top + r.height / 2;
      if (cx < view.left || cx > view.right || cy < view.top || cy > view.bottom) continue;

      const hit = document.elementFromPoint(cx, cy);
      if (!hit) continue;
      if (hit.closest(OVERLAY)) continue; // bị lớp phủ che — chuyện khác
      checked += 1;
      if (hit.closest(".react-flow__node") === el) continue; // trúng đúng thẻ

      const tag = hit.tagName.toLowerCase();
      // "Đường nối nuốt cú chạm" có hai dạng: <path> nhìn thấy được, và dải băng vô hình rộng
      // 20px mà React Flow dựng kèm mỗi cạnh (interactionWidth). Cả hai đều nằm trong lớp cạnh
      // hoặc chính là một <path>, nên bắt theo cả hai dấu hiệu.
      const inEdgeLayer = Boolean(hit.closest(".react-flow__edges")) || tag === "path";
      blocked.push({
        nodeId: el.getAttribute("data-id") ?? "?",
        hit:
          (hit as HTMLElement).getAttribute?.("data-testid") ??
          (typeof (hit as HTMLElement).className === "string"
            ? (hit as HTMLElement).className
            : ((hit as unknown as SVGElement).className as unknown as SVGAnimatedString)?.baseVal) ??
          hit.tagName,
        tag,
        byEdge: inEdgeLayer,
      });
    }
    return { checked, blocked };
  });
}

/**
 * Đếm những gì đang thực sự có trên canvas.
 *
 * <p>Không có phép đếm này thì mọi khẳng định "không có chỗ nào đè nhau" đều có thể xanh vì lý do
 * tệ nhất: màn hình chẳng vẽ đường nối nào cả.</p>
 */
async function canvasCensus(page: Page): Promise<{
  paths: number;
  cards: number;
  junctions: number;
}> {
  return page.evaluate(() => ({
    paths: document.querySelectorAll(".react-flow__edges path").length,
    cards: document.querySelectorAll(".react-flow__node").length,
    junctions: document.querySelectorAll('[data-testid="family-junction"]').length,
  }));
}

/** React Flow chuyển động mượt khi phóng/thu — đợi phép biến đổi đứng yên rồi mới đo. */
async function waitForViewportSettled(page: Page): Promise<void> {
  const read = () => page.locator(".react-flow__viewport").getAttribute("style");
  let previous = await read();
  await expect
    .poll(
      async () => {
        const current = await read();
        const settled = current === previous;
        previous = current;
        return settled;
      },
      { timeout: 15_000, intervals: [150, 150, 150, 300] }
    )
    .toBe(true);
}

async function currentScale(page: Page): Promise<number> {
  const style = (await page.locator(".react-flow__viewport").getAttribute("style")) ?? "";
  return Number(/scale\(([\d.]+)\)/.exec(style)?.[1] ?? "1");
}

/**
 * Mức phóng mà lỗi cũ lộ ra: thẻ co lại nhỏ hơn nửa kích thước, còn đường nối thì không co theo.
 *
 * <p>Nút "Thu toàn cây" KHÔNG dùng được cho việc này: trên màn hình 1440×900, nhánh đang tải vừa
 * khung ở mức 0.84, nên bấm nó lại phóng TO lên. Muốn đo đúng trạng thái thu nhỏ thì phải lăn
 * chuột thật.</p>
 */
const SMALL_ZOOM = 0.45;

/** Lăn chuột thu nhỏ cho tới khi mức phóng thật sự xuống dưới {@link SMALL_ZOOM}. */
async function zoomOutFar(page: Page): Promise<number> {
  const box = (await page.locator(".react-flow__pane").boundingBox())!;
  const cx = box.x + box.width / 2;
  const cy = box.y + box.height / 2;
  for (let i = 0; i < 12; i += 1) {
    if ((await currentScale(page)) < SMALL_ZOOM) break;
    await page.mouse.move(cx, cy);
    await page.mouse.wheel(0, 600);
    await page.waitForTimeout(150);
  }
  await waitForViewportSettled(page);
  const scale = await currentScale(page);
  expect(scale, "không thu nhỏ được canvas để kiểm ở mức thu nhỏ").toBeLessThan(SMALL_ZOOM);
  return scale;
}

function describeOverlaps(overlaps: Overlap[]): string {
  return overlaps
    .slice(0, 12)
    .map((o) => `· ${o.edge} đè lên thẻ ${o.nodeId} tại (${o.x},${o.y}), sâu ${o.depth}px`)
    .join("\n");
}

async function openTree(page: Page): Promise<void> {
  await page.goto("/tree");
  await waitForTreeReady(page);
  await waitForViewportSettled(page);
}

/** Ảnh chụp làm bằng chứng, luôn nằm trong `.playwright-mcp/` chứ không phải gốc kho mã. */
async function snapshot(page: Page, name: string): Promise<void> {
  await page.screenshot({ path: `.playwright-mcp/tree-legibility-${name}.png`, fullPage: false });
}

test.beforeEach(async ({ page }) => {
  // Thành viên nhìn thấy cả người còn sống ở Tier 1 — đây mới là phả đồ dày đường nối thật.
  await signInAs(page, "member");
});

test.describe("phả đồ trên máy để bàn", () => {
  test("không đường nối nào chạy đè lên vùng thẻ nhân khẩu", async ({ page, consoleErrors }) => {
    await openTree(page);

    const census = await canvasCensus(page);
    expect(census.cards, "canvas không vẽ thẻ nhân khẩu nào").toBeGreaterThan(1);
    expect(census.paths, "canvas không vẽ đường nối nào — phép đo sẽ xanh một cách vô nghĩa").toBeGreaterThan(1);
    expect(census.junctions, "chế độ Phân cấp không vẽ điểm nối gia đình nào").toBeGreaterThan(0);

    const overlaps = await edgesOverlappingCards(page, EDGE_TOLERANCE_PX);
    if (overlaps.length > 0) await snapshot(page, "desktop-overlap");
    expect(
      overlaps,
      `có ${overlaps.length} chỗ đường nối chui vào trong thẻ:\n${describeOverlaps(overlaps)}`
    ).toEqual([]);

    const realErrors = consoleErrors.filter((e) => !e.includes("[antd: compatible]"));
    expect(realErrors, realErrors.join("\n")).toEqual([]);
  });

  test("vẫn không đường nối nào đè lên thẻ ở mức thu nhỏ", async ({ page }) => {
    await openTree(page);
    const scale = await zoomOutFar(page);
    expect(scale).toBeLessThan(SMALL_ZOOM);

    const census = await canvasCensus(page);
    expect(census.paths, "thu nhỏ xong không còn đường nối nào để đo").toBeGreaterThan(1);

    const overlaps = await edgesOverlappingCards(page, EDGE_TOLERANCE_PX);
    if (overlaps.length > 0) await snapshot(page, "desktop-overlap-zoomed-out");
    expect(
      overlaps,
      `ở mức thu nhỏ có ${overlaps.length} chỗ đường nối chui vào trong thẻ:\n${describeOverlaps(overlaps)}`
    ).toEqual([]);
  });

  test("bấm vào giữa một tấm thẻ thì trúng thẻ, không trúng đường nối (mức thu nhỏ)", async ({
    page,
  }) => {
    await openTree(page);
    await zoomOutFar(page);

    const { checked, blocked } = await cardsNotReceivingTheirOwnTap(page);
    if (blocked.length > 0) await snapshot(page, "desktop-blocked-tap");
    expect(checked, "không có thẻ nào nằm trong canvas để kiểm tra").toBeGreaterThan(0);
    expect(
      blocked.filter((b) => b.byEdge),
      "đường nối được vẽ ĐÈ lên thẻ và nuốt mất cú bấm — đúng lỗi mà zIndex: -1 từng phải chữa:\n" +
        blocked.map((b) => `· ${b.nodeId} bị <${b.tag}> ${b.hit} chắn`).join("\n")
    ).toEqual([]);
    expect(
      blocked,
      "có thẻ không nhận được cú bấm vào chính giữa mình:\n" +
        blocked.map((b) => `· ${b.nodeId} bị <${b.tag}> ${b.hit} chắn`).join("\n")
    ).toEqual([]);
  });

  /**
   * Đo thẳng cái CƠ CHẾ đã nuốt cú chạm, chứ không chỉ đo hậu quả.
   *
   * <p>@xyflow/react đặt {@code .react-flow__edge { pointer-events: visibleStroke }} và mặc định
   * {@code interactionWidth = 20}, nên mỗi cạnh kèm thêm một {@code <path>} vô hình rộng 20px
   * ({@code stroke-opacity: 0} vẫn được {@code visibleStroke} coi là "có vẽ"). Dải băng ấy chính
   * là thứ người dùng chạm phải thay vì tấm thẻ. Ca này khẳng định trên DOM thật rằng nó không
   * còn được dựng ra, và không {@code <path>} nào của lớp cạnh còn nhận sự kiện trỏ.</p>
   */
  test("không đường nối nào nhận sự kiện trỏ — kể cả dải băng vô hình 20px của React Flow", async ({
    page,
  }) => {
    await openTree(page);

    const probe = await page.evaluate(() => {
      const paths = [...document.querySelectorAll<SVGPathElement>(".react-flow__edges path")];
      const live = paths
        .filter((p) => window.getComputedStyle(p).pointerEvents !== "none")
        .map(
          (p) =>
            `${p.getAttribute("data-testid") ?? p.getAttribute("class") ?? "path"} ` +
            `(pointer-events: ${window.getComputedStyle(p).pointerEvents})`
        );
      return {
        total: paths.length,
        live: [...new Set(live)].slice(0, 10),
        interactionBands: document.querySelectorAll(".react-flow__edge-interaction").length,
      };
    });

    expect(probe.total, "không có đường nối nào trên canvas để kiểm tra").toBeGreaterThan(0);
    expect(
      probe.interactionBands,
      "React Flow vẫn dựng dải băng chạm vô hình quanh cạnh (interactionWidth chưa bằng 0)"
    ).toBe(0);
    expect(probe.live, `còn đường nối nhận sự kiện trỏ:\n${probe.live.join("\n")}`).toEqual([]);
  });

  /**
   * Phép đo ở trên chỉ đáng tin nếu nó BIẾT KÊU. Ca này cắm tạm một đường kẻ đi xuyên giữa một
   * tấm thẻ vào đúng lớp cạnh, khẳng định phép đo bắt được, rồi gỡ ra và khẳng định nó im lại.
   * Không có ca này thì một hôm nào đó bộ chọn CSS đổi tên, mọi khẳng định "không có chỗ nào đè"
   * sẽ xanh vĩnh viễn vì chẳng đo gì cả.
   */
  test("phép đo đè-lên-thẻ không mù: cắm một đường xuyên qua thẻ thì nó phải kêu", async ({
    page,
  }) => {
    await openTree(page);

    const injected = await page.evaluate(() => {
      // `.react-flow__edges` là một <div> ở React Flow 12, không phải <svg> — lấy đúng <svg> đang
      // chứa các đường nối để đường thử nghiệm nằm trong cùng hệ toạ độ với chúng.
      const svg = document.querySelector<SVGPathElement>(".react-flow__edges path")?.ownerSVGElement;
      const card = document.querySelector<HTMLElement>(".react-flow__node");
      if (!svg || !card) return null;
      const ctm = svg.getScreenCTM();
      if (!ctm) return null;
      const inverse = ctm.inverse();
      const r = card.getBoundingClientRect();
      const toLocal = (x: number, y: number) => new DOMPoint(x, y).matrixTransform(inverse);
      const a = toLocal(r.left - 40, r.top + r.height / 2);
      const b = toLocal(r.right + 40, r.top + r.height / 2);
      const path = document.createElementNS("http://www.w3.org/2000/svg", "path");
      path.setAttribute("d", `M ${a.x} ${a.y} L ${b.x} ${b.y}`);
      path.setAttribute("data-testid", "duong-ke-thu-nghiem");
      path.setAttribute("stroke", "red");
      path.setAttribute("fill", "none");
      svg.appendChild(path);
      return card.getAttribute("data-id");
    });
    expect(injected, "không cắm được đường thử nghiệm vào lớp cạnh").not.toBeNull();

    const caught = await edgesOverlappingCards(page, EDGE_TOLERANCE_PX);
    expect(
      caught.some((o) => o.nodeId === injected && o.edge === "duong-ke-thu-nghiem"),
      "phép đo KHÔNG bắt được một đường kẻ đi xuyên giữa tấm thẻ — nó đang đo nhầm chỗ"
    ).toBe(true);

    await page.evaluate(() =>
      document.querySelector('[data-testid="duong-ke-thu-nghiem"]')?.remove()
    );
    expect(await edgesOverlappingCards(page, EDGE_TOLERANCE_PX)).toEqual([]);
  });

  test("bấm vào giữa thẻ mở đúng hồ sơ người ấy, kể cả ở mức thu nhỏ", async ({ page }) => {
    await openTree(page);
    await zoomOutFar(page);

    const card = page.locator(".react-flow__node").first();
    const name = (await card.getByTestId("person-node-name").first().innerText()).trim();
    const box = (await card.boundingBox())!;
    await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);

    const drawer = page.getByRole("dialog");
    await expect(drawer, `bấm giữa thẻ "${name}" mà không mở được hồ sơ`).toBeVisible();
    await expect(drawer.getByRole("heading", { name })).toBeVisible();
  });
});

test.describe("phả đồ trên điện thoại", () => {
  // Dự án `mobile-chromium` trong playwright.config.ts chỉ nhận `mobile.spec.ts`, mà tệp cấu hình
  // ấy không thuộc phạm vi sửa của việc này — nên khung nhìn điện thoại được giả lập ngay tại đây
  // (Pixel 5, có cảm ứng). Người dùng phả đồ này phần lớn là kiều bào đọc trên điện thoại, không
  // kiểm ở đây thì coi như không kiểm cho đa số người dùng.
  // `defaultBrowserType` không được phép đặt trong describe (Playwright bắt buộc nó ở cấp tệp hoặc
  // cấu hình vì nó đổi cả worker), nên chỉ lấy phần mô tả THIẾT BỊ: khung nhìn, tỉ lệ điểm ảnh,
  // user-agent, cảm ứng. Trình duyệt vẫn là chromium của dự án desktop — đúng thứ ta cần.
  test.use({
    viewport: devices["Pixel 5"].viewport,
    userAgent: devices["Pixel 5"].userAgent,
    deviceScaleFactor: devices["Pixel 5"].deviceScaleFactor,
    isMobile: devices["Pixel 5"].isMobile,
    hasTouch: devices["Pixel 5"].hasTouch,
  });

  test("không đường nối nào chạy đè lên vùng thẻ trên màn hình điện thoại", async ({ page }) => {
    await openTree(page);

    const census = await canvasCensus(page);
    expect(census.cards, "canvas trên điện thoại không vẽ thẻ nào").toBeGreaterThan(1);
    expect(census.paths, "canvas trên điện thoại không vẽ đường nối nào").toBeGreaterThan(1);

    const overlaps = await edgesOverlappingCards(page, EDGE_TOLERANCE_PX);
    if (overlaps.length > 0) await snapshot(page, "mobile-overlap");
    expect(
      overlaps,
      `có ${overlaps.length} chỗ đường nối chui vào trong thẻ:\n${describeOverlaps(overlaps)}`
    ).toEqual([]);
  });

  test("chạm vào giữa một tấm thẻ thì trúng thẻ, không trúng đường nối (mức thu nhỏ)", async ({
    page,
  }) => {
    await openTree(page);
    await zoomOutFar(page);

    const overlaps = await edgesOverlappingCards(page, EDGE_TOLERANCE_PX);
    const { checked, blocked } = await cardsNotReceivingTheirOwnTap(page);
    if (blocked.length > 0 || overlaps.length > 0) await snapshot(page, "mobile-blocked-tap");

    expect(checked, "không có thẻ nào nằm trong canvas để kiểm tra").toBeGreaterThan(0);
    expect(
      blocked,
      "trên điện thoại có thẻ không nhận được cú chạm vào chính giữa mình:\n" +
        blocked.map((b) => `· ${b.nodeId} bị <${b.tag}> ${b.hit} chắn`).join("\n")
    ).toEqual([]);
    expect(
      overlaps,
      `ở mức thu nhỏ trên điện thoại có ${overlaps.length} chỗ đường nối chui vào thẻ:\n${describeOverlaps(overlaps)}`
    ).toEqual([]);
  });
});

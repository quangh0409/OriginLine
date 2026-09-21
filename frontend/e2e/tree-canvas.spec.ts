import {
  expect,
  panCanvas,
  segmented,
  signInAs,
  test,
  visibleNodeNames,
  waitForTreeReady,
} from "./fixtures";
import type { Page } from "@playwright/test";

/**
 * F2 — canvas phả đồ. These cover the Phase-1 exit criteria that only a real
 * browser can answer: does the tree render, do all three view modes work, do
 * zoom and pan work, can a branch be collapsed and re-expanded, and does
 * tapping a node open the profile.
 *
 * Note on counting: React Flow runs with `onlyRenderVisibleElements`, so the
 * number of `.react-flow__node` elements in the DOM is the number currently
 * INSIDE THE VIEWPORT, not the number in the graph. Tests that care about the
 * graph therefore zoom out first or read the toolbar's loaded-count chip.
 */

/** The expand/collapse toggle inside one specific person card. */
const nodeToggle = (page: Page, nodeId: string | null) =>
  page.locator(`.react-flow__node[data-id="${nodeId}"] button`);

/**
 * Clicks a node's toggle, panning the canvas first if an overlay panel is
 * covering it.
 *
 * Expanding a branch re-flows the whole layout, so the card that was just
 * clicked routinely lands somewhere else — sometimes underneath the minimap
 * (bottom-right, 200x150, and interactive, so it swallows the click). A user
 * simply drags the canvas and carries on; without doing the same, this test
 * is really an assertion about where dagre happens to put a node relative to
 * a React Flow panel, which is not what it is for.
 */
async function clickNodeToggle(page: Page, nodeId: string | null): Promise<void> {
  await bringNodeIntoView(page, nodeId);
  await nodeToggle(page, nodeId).click();
}

/**
 * Kéo canvas cho tới khi một thẻ cụ thể **thật sự bấm được**.
 *
 * Hai lý do phải làm việc này, và lý do thứ hai mới là lý do nó được tách ra thành hàm riêng:
 *
 *  1. Lớp phủ che mất thẻ — bản đồ thu nhỏ ở góc dưới-phải nhận sự kiện chuột nên nó nuốt cú bấm;
 *  2. `onlyRenderVisibleElements` **xoá thẻ khỏi DOM** khi nó trôi ra ngoài khung nhìn. Bung một
 *     nhánh làm cả bố cục dãn ra dưới một máy quay ĐỨNG YÊN (đó là chủ ý — xem `<TreeCanvasInner>`),
 *     nên chính tấm thẻ vừa bấm thường xuyên trôi ra ngoài ngay sau đó. Một lời khẳng định về nó
 *     sẽ đỏ với thông báo "không tìm thấy phần tử", nói về một thứ hoàn toàn không phải nguyên
 *     nhân.
 */
async function bringNodeIntoView(page: Page, nodeId: string | null): Promise<void> {
  for (let attempt = 0; attempt < 6; attempt += 1) {
    // Thẻ KHÔNG còn trong DOM ⇒ nó đã trôi hẳn ra ngoài khung và bị `onlyRenderVisibleElements`
    // xén đi. Lúc ấy không thể tính "phải kéo bao xa" vì không còn gì để đo — phải thu cả cây về
    // khung trước, rồi mới kéo tiếp cho vừa tầm bấm. Đây là đúng thao tác người dùng làm khi họ
    // "lạc" trên phả đồ.
    if ((await page.locator(`.react-flow__node[data-id="${nodeId}"]`).count()) === 0) {
      await page.locator(".react-flow__controls-fitview").click();
      await page.waitForTimeout(600);
    }
    // How far this toggle would have to travel to sit in the middle of the
    // canvas — and whether it needs to move at all.
    const offset = await page.evaluate((id) => {
      const button = document.querySelector<HTMLElement>(
        `.react-flow__node[data-id="${id}"] button`
      );
      const canvas = document.querySelector(".react-flow")?.getBoundingClientRect();
      if (!button || !canvas) return null;
      const r = button.getBoundingClientRect();
      const cx = r.left + r.width / 2;
      const cy = r.top + r.height / 2;
      const hit = document.elementFromPoint(cx, cy);
      if (hit && button.contains(hit)) return null; // already clickable
      return {
        dx: canvas.left + canvas.width / 2 - cx,
        dy: canvas.top + canvas.height / 2 - cy,
      };
    }, nodeId);
    if (!offset) break;

    // Clamped so the drag itself stays inside the window; repeated until the
    // node reaches the clear.
    const clamp = (n: number) => Math.max(-260, Math.min(260, Math.round(n)));
    await panCanvas(page, clamp(offset.dx), clamp(offset.dy));
    await page.waitForTimeout(300);
  }
}

const viewportTransform = (page: Page) =>
  page.locator(".react-flow__viewport").getAttribute("style");

async function zoomOut(page: Page, notches = 900): Promise<void> {
  const box = (await page.locator(".react-flow__pane").boundingBox())!;
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
  await page.mouse.wheel(0, notches);
}

/**
 * Con số thanh công cụ IN RA: số nhân khẩu đang hiện trên canvas.
 *
 * Đọc từ chính CHỮ hiển thị chứ không từ thuộc tính — nhãn và con số phải cùng nói một sự thật,
 * và đây là chỗ khẳng định điều đó.
 */
async function visibleCount(page: Page): Promise<number> {
  const chip = await page.getByText(/nhân khẩu đang hiển thị/).innerText();
  return Number(chip.replace(/\D+/g, "")) || 0;
}

/**
 * Số nhân khẩu đã TẢI VỀ — không in ra chữ (người trong họ không quan tâm phả đồ đã tải bao
 * nhiêu), nhưng vẫn phải quan sát được: đây là bề mặt duy nhất chứng minh giao kèo "không bao
 * giờ tải cả cây".
 */
async function loadedCount(page: Page): Promise<number> {
  const raw = await page.getByTestId("tree-node-count").getAttribute("data-loaded-count");
  return Number(raw ?? "0") || 0;
}



test.beforeEach(async ({ page }) => {
  // A member sees living relatives at Tier 1, which is the realistic canvas.
  await signInAs(page, "member");
});

/**
 * Phả đồ mở ra với người thật trên đó, **từ cái gốc mà máy chủ chọn cho vai này**.
 *
 * <h2>Vì sao ca này không còn ghim tên "Nguyễn Văn Thủy Tổ"</h2>
 *
 * `rootId` là tuỳ chọn, và khi vắng mặt thì máy chủ chọn gốc theo **vai + phạm vi chi**: Hội đồng
 * và Quản trị mở từ Thuỷ tổ, còn một Thành viên mở từ **ông tổ chi nhà mình**. Với bộ dữ liệu giả
 * lập, Thành viên thuộc `root.chi_nhat` nên gốc của họ là cụ đời 2 — Thuỷ tổ **không** có mặt trên
 * canvas ấy, và đó là hành vi đúng chứ không phải lỗi.
 *
 * Đây chính là mục "Đăng nhập xong cây lại NHỎ ĐI" trong checklist §5: ý đồ hợp lý nhưng im lặng.
 * Cách chữa đã làm là **nói ra** — dải ngữ cảnh gốc (`tree-root-banner`) ghi rõ đang mở chi nào và
 * từ cụ nào, và ca kiểm riêng cho nó nằm ở cuối tệp này. Ghim một cái tên cứng ở đây thì ca kiểm
 * chỉ đang khẳng định "Thành viên giả lập thuộc chi nào", không khẳng định được điều nó định
 * khẳng định.
 */
test("renders the phả đồ from the server-chosen root with real people on it", async ({
  page,
  consoleErrors,
}) => {
  await page.goto("/tree");
  await waitForTreeReady(page);

  const names = await visibleNodeNames(page);
  expect(names.length).toBeGreaterThan(1);
  // Người thật, không phải chỗ giữ chỗ: mỗi tấm thẻ mang một cái tên có nghĩa.
  expect(names.every((n) => n.trim().length > 2)).toBe(true);
  // Và cái gốc ấy được NÓI RA, bằng đúng tên một người đang có trên canvas.
  const banner = await page.getByTestId("tree-root-banner").innerText();
  expect(
    names.some((n) => banner.includes(n)),
    `dải ngữ cảnh nói "${banner.replace(/\n/g, " ")}" mà không nhắc tên ai đang có trên canvas`
  ).toBe(true);
  // Đã tải nhiều hơn số thẻ đang vẽ: nạp trước một vòng, cộng phép cắt thẻ ngoài khung của
  // React Flow.
  expect(await loadedCount(page)).toBeGreaterThan(names.length);

  expect(consoleErrors, consoleErrors.join("\n")).toEqual([]);
});

/** Vai toàn dòng họ thì gốc mặc định LÀ Thuỷ tổ — vế còn lại của phép chọn gốc theo vai. */
test("vai toàn dòng họ mở phả đồ từ Thuỷ tổ", async ({ page }) => {
  await signInAs(page, "admin");
  await page.goto("/tree");
  await waitForTreeReady(page);

  expect((await visibleNodeNames(page)).join(" ")).toContain("Nguyễn Văn Thủy Tổ");
});

test("tells living and deceased apart without relying on colour alone", async ({ page }) => {
  await page.goto("/tree");
  await waitForTreeReady(page);

  // Each card carries a screen-reader-only status word next to the dot.
  const statuses = await page.locator(".react-flow__node .sr-only").allInnerTexts();
  expect(statuses.length).toBeGreaterThan(0);
  expect(statuses.some((s) => s.includes("Đã khuất"))).toBe(true);

  await expect(page.getByText("Chú giải")).toBeVisible();
  await expect(page.getByText("Người còn sống")).toBeVisible();
  await expect(page.getByText("Người đã khuất")).toBeVisible();
});

test("switches between phân cấp, tỏa tròn and ma trận, keeping the loaded graph", async ({
  page,
  consoleErrors,
}) => {
  await page.goto("/tree");
  await waitForTreeReady(page);
  const loadedBefore = await loadedCount(page);
  const visibleBefore = await visibleCount(page);

  for (const mode of ["Tỏa tròn", "Ma trận", "Phân cấp"]) {
    await segmented(page, mode).click();
    await waitForTreeReady(page);
    // A view switch changes the layout algorithm only — it must never discard
    // fetched branches or reset what the user expanded.
    expect(await loadedCount(page), `after switching to ${mode}`).toBe(loadedBefore);
    // Đổi cách vẽ thì không ai xuất hiện thêm và không ai biến mất.
    expect(await visibleCount(page), `after switching to ${mode}`).toBe(visibleBefore);
  }

  expect(consoleErrors, consoleErrors.join("\n")).toEqual([]);
});

test("each view mode actually lays the tree out differently", async ({ page }) => {
  await page.goto("/tree");
  await waitForTreeReady(page);

  const positionsFor = async (mode: string) => {
    await segmented(page, mode).click();
    await waitForTreeReady(page);
    return page
      .locator(".react-flow__node")
      .evaluateAll((nodes) =>
        nodes
          .map((n) => (n as HTMLElement).style.transform)
          .sort()
          .join("|")
      );
  };

  const hierarchical = await positionsFor("Phân cấp");
  const radial = await positionsFor("Tỏa tròn");
  const matrix = await positionsFor("Ma trận");

  expect(radial, "tỏa tròn produced the same layout as phân cấp").not.toBe(hierarchical);
  expect(matrix, "ma trận produced the same layout as tỏa tròn").not.toBe(radial);
});

test("zooms with the wheel and pans by dragging the canvas", async ({ page }) => {
  await page.goto("/tree");
  await waitForTreeReady(page);

  const initial = await viewportTransform(page);
  await zoomOut(page, 400);
  await expect.poll(() => viewportTransform(page), { timeout: 10_000 }).not.toBe(initial);

  const afterZoom = await viewportTransform(page);
  await panCanvas(page, -220, -120);
  await expect.poll(() => viewportTransform(page), { timeout: 10_000 }).not.toBe(afterZoom);
});

/**
 * BUG WATCH — the zoom/fit controls are unreachable.
 *
 * <TreeLegend> is `!absolute !bottom-3 !left-3 !z-10`; React Flow's
 * <Controls> defaults to the same bottom-left corner at z-index 5. The legend
 * card therefore sits ON TOP of "zoom in" / "zoom out" / "fit view" and eats
 * their clicks. Wheel zoom still works, so this is invisible to anyone
 * testing with a trackpad — but a touch user, and anyone who lost the tree
 * off-screen and wants "fit view", has no way back.
 */
test("the on-canvas zoom and fit-view controls can actually be clicked", async ({ page }) => {
  await page.goto("/tree");
  await waitForTreeReady(page);

  const legend = (await page.locator(".ant-card").first().boundingBox())!;
  const controls = (await page.locator(".react-flow__controls").boundingBox())!;
  const overlaps =
    legend.x < controls.x + controls.width &&
    legend.x + legend.width > controls.x &&
    legend.y < controls.y + controls.height &&
    legend.y + legend.height > controls.y;
  expect(overlaps, "the legend card overlaps the React Flow controls").toBe(false);

  const before = await viewportTransform(page);
  await page.locator(".react-flow__controls-zoomin").click({ timeout: 5000 });
  await expect.poll(() => viewportTransform(page), { timeout: 10_000 }).not.toBe(before);
});

/**
 * BUG WATCH — the camera never auto-fits.
 *
 * TreeCanvasInner fits the view once per rootId, guarded by a ref. Under
 * React StrictMode (`reactStrictMode: true` in next.config.js) the effect runs
 * twice: the first pass sets the ref and schedules a rAF, the cleanup cancels
 * that rAF, and the second pass returns early because the ref already matches.
 * Net result in development: `fitView` never runs and the tree opens at
 * scale 1 with the thủy tổ wherever dagre happened to put him.
 */
test("frames the loaded branch on first render instead of opening at raw scale 1", async ({
  page,
}) => {
  await page.goto("/tree");
  await waitForTreeReady(page);

  await expect
    .poll(() => viewportTransform(page), { timeout: 15_000 })
    .not.toMatch(/translate\(0px,\s*0px\)\s*scale\(1\)/);
});

test("collapses and re-expands the branch that was expanded", async ({ page }) => {
  await page.goto("/tree");
  await waitForTreeReady(page);

  // Pin the toggle to one specific person, so "collapse" acts on exactly the
  // node that was expanded rather than on whichever toggle happens to be
  // first in the DOM after the re-layout.
  const card = page
    .locator(".react-flow__node")
    .filter({ has: page.getByRole("button", { name: "Mở rộng nhánh này" }) })
    .first();
  await expect(card).toBeVisible({ timeout: 30_000 });
  const nodeId = await card.getAttribute("data-id");
  const toggle = nodeToggle(page, nodeId);

  await expect(toggle).toHaveAttribute("aria-expanded", "false");
  const before = (await visibleNodeNames(page)).length;

  await clickNodeToggle(page, nodeId);
  // Zoom out so viewport culling cannot hide the newly revealed generation.
  await zoomOut(page);
  await expect(toggle).toHaveAttribute("aria-expanded", "true");
  await expect
    .poll(async () => (await visibleNodeNames(page)).length, { timeout: 30_000 })
    .toBeGreaterThan(before);
  const afterExpand = (await visibleNodeNames(page)).length;

  await clickNodeToggle(page, nodeId);
  await expect(toggle).toHaveAttribute("aria-expanded", "false");
  await expect
    .poll(async () => (await visibleNodeNames(page)).length, { timeout: 30_000 })
    .toBeLessThan(afterExpand);

  // Re-expanding is instant: collapsing removed the nodes from the render,
  // it did not discard the fetched data.
  await clickNodeToggle(page, nodeId);
  await expect
    .poll(async () => (await visibleNodeNames(page)).length, { timeout: 30_000 })
    .toBeGreaterThanOrEqual(afterExpand);
});

/**
 * FIXED — the root no longer offers a collapse control that does nothing.
 *
 * useTreeCanvas.collapse() returns early for the root (collapsing it would
 * empty the canvas), but PersonNode used to render the toggle on the root
 * anyway, with aria-expanded="true" and the collapse label. Pressing it was a
 * no-op, which reads as a broken control rather than a deliberate one.
 * PersonNode now hides the toggle when `id === rootId` (see
 * tree-canvas-context.tsx), so this test takes its early-return branch.
 */
test("the thủy tổ card offers no collapse control that does nothing", async ({ page }) => {
  await page.goto("/tree");
  await waitForTreeReady(page);

  const rootToggle = nodeToggle(page, "p-001");
  if ((await rootToggle.count()) === 0) return; // no control offered: correct

  const before = (await visibleNodeNames(page)).length;
  await rootToggle.click();
  await expect
    .poll(async () => (await visibleNodeNames(page)).length, { timeout: 5_000 })
    .not.toBe(before);
});

test("collapsing a branch does not discard what was already fetched", async ({ page }) => {
  await page.goto("/tree");
  await waitForTreeReady(page);

  const card = page
    .locator(".react-flow__node")
    .filter({ has: page.getByRole("button", { name: "Mở rộng nhánh này" }) })
    .first();
  const nodeId = await card.getAttribute("data-id");
  const toggle = nodeToggle(page, nodeId);

  await clickNodeToggle(page, nodeId);
  // Bung xong thì bố cục dãn ra dưới một máy quay đứng yên, nên chính tấm thẻ vừa bấm rất hay
  // trôi khỏi khung — và React Flow xoá nó khỏi DOM. Kéo nó về trước khi hỏi han nó.
  await bringNodeIntoView(page, nodeId);
  await expect(toggle).toHaveAttribute("aria-expanded", "true");
  const loadedAfterExpand = await loadedCount(page);
  const visibleAfterExpand = await visibleCount(page);

  await clickNodeToggle(page, nodeId);
  await bringNodeIntoView(page, nodeId);
  await expect(toggle).toHaveAttribute("aria-expanded", "false");
  expect(await loadedCount(page)).toBe(loadedAfterExpand);

  // ...nhưng số ĐANG HIỂN THỊ thì phải vơi đi. Lỗi cũ: thanh công cụ in ra số đã TẢI dưới nhãn
  // "{count} nhân khẩu đang hiển thị", nên thu một nhánh lại thì màn hình vơi đi mà con số đứng im
  // — người dùng đọc ra là "bấm không ăn".
  await expect
    .poll(() => visibleCount(page), { timeout: 15_000 })
    .toBeLessThan(visibleAfterExpand);
});

test("opens a person profile from a node without unmounting the canvas", async ({ page }) => {
  await page.goto("/tree");
  await waitForTreeReady(page);

  const firstNode = page.locator(".react-flow__node").first();
  const name = (await firstNode.locator(".font-serif").first().innerText()).trim();
  await firstNode.click();

  const drawer = page.getByRole("dialog");
  await expect(drawer).toBeVisible();
  await expect(drawer.getByText("Hồ sơ nhân khẩu")).toBeVisible();
  await expect(drawer.getByRole("heading", { name })).toBeVisible();

  // The canvas is still mounted behind the drawer — the whole reason this is
  // a drawer and not a route change.
  await expect(page.locator(".react-flow__node").first()).toBeAttached();

  await page.keyboard.press("Escape");
  await expect(drawer).toBeHidden();
});

test("the expand toggle never doubles as 'open profile'", async ({ page }) => {
  await page.goto("/tree");
  await waitForTreeReady(page);

  await page.getByRole("button", { name: "Mở rộng nhánh này" }).first().click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
});

test("shows an honest not-found for a root id nobody can reach", async ({ page }) => {
  await page.goto("/tree?rootId=khong-ton-tai");
  await expect(page.getByText("Không tìm thấy nhân khẩu gốc của cây")).toBeVisible({
    timeout: 30_000,
  });
});

/**
 * `?view=` — chế độ xem sống trên URL.
 *
 * Vì sao ca này đáng có: đường lan truyền chính của sản phẩm là người trong họ gửi
 * nhau một liên kết qua Zalo. Chừng nào chế độ xem còn là `useState` thuần thì hai
 * chế độ Tỏa tròn / Ma trận đời chỉ là "thứ mình tôi thấy" — không gửi được, không
 * đánh dấu được, mở lại là mất. design/03 ghi FR-1.4 chỉ đạt "một phần" đúng vì thế.
 *
 * Ca này cũng canh một cái bẫy ĐÃ XẢY RA một lần rồi ở <LanguageSwitcher>: ghi một
 * tham số vào URL bằng cách dựng `URLSearchParams` MỚI sẽ làm rơi mọi tham số khác.
 * Ở đây thứ bị rơi sẽ là `?rootId=` — tức người nhận liên kết mở ra một cây khác.
 */
test("?view= mở đúng chế độ xem, và đổi chế độ không làm rơi ?rootId=", async ({ page }) => {
  await page.goto("/tree?rootId=p-001&view=toa");
  await waitForTreeReady(page);
  await expect(
    page.locator(".ant-segmented-item-selected").filter({ hasText: "Tỏa tròn" })
  ).toBeVisible();

  await segmented(page, "Ma trận").click();
  await waitForTreeReady(page);
  await expect.poll(() => new URL(page.url()).searchParams.get("view")).toBe("matran");
  expect(
    new URL(page.url()).searchParams.get("rootId"),
    "đổi chế độ xem làm rơi mất gốc cây — đúng lỗi đã sửa một lần ở bộ chuyển ngôn ngữ"
  ).toBe("p-001");

  // Phân cấp là mặc định nên nó KHÔNG ghi ra URL: liên kết ngắn nhất cho trường hợp
  // thường gặp nhất, và `/tree` trần vẫn phải mở đúng phả đồ quen thuộc.
  await segmented(page, "Phân cấp").click();
  await waitForTreeReady(page);
  await expect.poll(() => new URL(page.url()).searchParams.get("view")).toBeNull();
  expect(new URL(page.url()).searchParams.get("rootId")).toBe("p-001");
});

/** Một giá trị lạ trên URL không được biến phả đồ thành màn hình lỗi — URL là thứ người ta gõ tay. */
test("?view= với giá trị lạ rơi về Phân cấp thay vì báo lỗi", async ({ page }) => {
  await page.goto("/tree?view=khong-co-che-do-nay");
  await waitForTreeReady(page);
  await expect(
    page.locator(".ant-segmented-item-selected").filter({ hasText: "Phân cấp" })
  ).toBeVisible();
});

/* ══════════════════════════════════════════════════════════════════════════════
   PHẢ ĐỒ GỌN LẠI VÀ TÌM ĐƯỢC NGƯỜI — checklist §5

   Bốn việc chặn, cộng hai việc nhỏ cùng vùng. Cả sáu chỉ trả lời được trong một
   trình duyệt thật, vì chúng là chuyện của BỐ CỤC ĐÃ DỰNG XONG: bề rộng một đời,
   máy quay đã bay tới đâu, và tấm thẻ nào đang mang dấu hiệu gì.
   ════════════════════════════════════════════════════════════════════════════ */

/** Ô tìm người trên canvas. Nhãn đầy đủ nằm ở `aria-label`, nên tìm theo nhãn. */
const jumpInput = (page: Page) => page.getByLabel("Tìm một người trên phả đồ");

/**
 * Đời ĐÔNG NHẤT đang hiển thị: bao nhiêu người, và trải rộng bao nhiêu pixel.
 *
 * <h2>Đo trong HỆ TOẠ ĐỘ CÂY</h2>
 * Đọc `transform: translate(x, y)` mà React Flow đặt cho từng thẻ — cùng đơn vị với `NODE_WIDTH` /
 * `COUPLE_GAP` / `FAMILY_GAP`, nên con số so thẳng được với phép nhân trong checklist §5. Đo bằng
 * `getBoundingClientRect()` thì kết quả còn phụ thuộc mức phóng, và hai lần chạy khác khung nhìn
 * sẽ ra hai con số khác nhau mà chẳng có gì đổi.
 *
 * <h2>PHẢI thu toàn cây trước</h2>
 * `onlyRenderVisibleElements` xoá khỏi DOM mọi thẻ ngoài khung nhìn, nên đo lúc đang phóng to là
 * đo bề rộng của cái khung nhìn chứ không phải của một đời.
 *
 * <h2>Đời ĐÔNG NHẤT, không phải đời RỘNG NHẤT</h2>
 * Một đời chỉ có ba người nhưng nằm ở hai đầu cây thì "rộng" mà không "đông" — bề rộng ấy nói về
 * hình dạng cây, không nói về mật độ mà người dùng phải cuộn qua. Cái cần đo là *bao nhiêu pixel
 * cho mỗi người*, và nó chỉ có nghĩa trên một hàng thật sự đông.
 */
async function busiestGeneration(
  page: Page
): Promise<{ count: number; width: number; pitch: number; cardWidth: number }> {
  await page.getByTestId("tree-fit-whole").click();
  await page.waitForTimeout(1200);
  return page.evaluate(() => {
    const nodes: { x: number; y: number; w: number }[] = [];
    const t = document.querySelector<HTMLElement>(".react-flow__viewport")?.style.transform ?? "";
    const scale = Number(/scale\(([\d.]+)\)/.exec(t)?.[1] ?? "1");
    for (const el of document.querySelectorAll<HTMLElement>(".react-flow__node")) {
      const m = /translate\((-?[\d.]+)px,\s*(-?[\d.]+)px\)/.exec(el.style.transform ?? "");
      if (!m) continue;
      nodes.push({
        x: Number(m[1]),
        y: Number(m[2]),
        w: el.getBoundingClientRect().width / scale,
      });
    }
    const empty = { count: 0, width: 0, pitch: 0, cardWidth: 0 };
    if (nodes.length === 0) return empty;
    const cardWidth = Math.round(nodes[0]!.w);

    const rows = new Map<number, number[]>();
    for (const n of nodes) {
      const key = Math.round(n.y);
      rows.set(key, [...(rows.get(key) ?? []), n.x]);
    }

    let best = empty;
    for (const xs of rows.values()) {
      if (xs.length <= best.count) continue;
      const sorted = [...xs].sort((a, b) => a - b);
      let pitch = Number.POSITIVE_INFINITY;
      for (let i = 1; i < sorted.length; i += 1) {
        pitch = Math.min(pitch, sorted[i]! - sorted[i - 1]!);
      }
      best = {
        count: sorted.length,
        width: Math.round(sorted[sorted.length - 1]! + cardWidth - sorted[0]!),
        pitch: Math.round(pitch),
        cardWidth,
      };
    }
    return best;
  });
}

/**
 * **Thẻ đã hẹp lại, và số đo phải nói ra điều đó.**
 *
 * 160px thay cho 208px. Con số ghim ở đây là con số NGƯỜI DÙNG gặp — bề ngang thật của tấm thẻ
 * sau khi chia lại cho mức phóng, chứ không phải hằng số trong mã. Hai thứ ấy đã từng lệch nhau
 * (component chép tay cỡ thẻ), nên chỗ này đo lại từ đầu bên kia sợi dây.
 */
test("tấm thẻ nhân khẩu rộng 160px, không còn 208px", async ({ page }) => {
  await page.goto("/tree");
  await waitForTreeReady(page);

  const measured = await page.evaluate(() => {
    const t = document.querySelector<HTMLElement>(".react-flow__viewport")?.style.transform ?? "";
    const scale = Number(/scale\(([\d.]+)\)/.exec(t)?.[1] ?? "1");
    const card = document.querySelector(".react-flow__node")?.getBoundingClientRect();
    return card ? Math.round(card.width / scale) : 0;
  });

  expect(measured, "thẻ nhân khẩu đã rộng trở lại — xem layout-constants.ts").toBe(160);
});

/**
 * **Một đời đông phải hẹp hơn hẳn bản cũ.**
 *
 * Số đo trên chính trình duyệt này, cùng vai và cùng trình tự bung, trước/sau đợt thu thẻ:
 *
 * | trạng thái | đời rộng nhất | trước | sau |
 * |---|---|---|---|
 * | mở ra mặc định |  6 người | 1.536px | 1.176px |
 * | bung một vòng  | 15 người | 4.000px | 3.040px |
 * | bung hai vòng  | 23 người | 14.896px | 10.860px |
 *
 * Ca này ghim vế "bung một vòng": 15 người cùng một đời phải nằm dưới 3.500px. Trần đặt cao hơn
 * số đo 3.040px một khoảng, vì bộ dữ liệu giả lập có thể đổi — thứ cần bắt là một bước lùi về
 * 4.000px, không phải một dao động vài chục pixel.
 */
test("một đời đông hẹp hơn hẳn — đo bề rộng thật trên bố cục đã dựng", async ({ page }) => {
  // Ca này làm việc THẬT: bung cả một vòng nhánh, mỗi cú bấm kéo theo một lượt gọi và một lần
  // dựng lại bố cục vài trăm node. Ngân sách mặc định 90s không đủ, và hết giờ ở đây báo về
  // "trang đã đóng" — một thông báo không nói gì về nguyên nhân.
  test.setTimeout(300_000);
  await page.goto("/tree");
  await waitForTreeReady(page);

  // Bung một vòng: chỉ bấm những nút ĐANG GẬP. Nút này bật/tắt, nên bấm hết mọi nút đang thấy sẽ
  // thu lại chính những nhánh vừa mở, và phép đo cho ra một cái cây NHỎ HƠN lúc đầu.
  //
  // Có TRẦN số nút, và trần ấy không phải để cho nhanh: mỗi cú bấm kéo theo một lượt gọi và một
  // lần dựng lại bố cục vài trăm node, nên "bấm hết" là một phép thử có thời gian chạy phụ thuộc
  // bộ dữ liệu giả lập.
  const collapsed = await page
    .locator('[data-testid="tree-node-toggle"][aria-expanded="false"]')
    .all();
  for (const toggle of collapsed.slice(0, 6)) {
    await toggle.click({ force: true, timeout: 5_000 }).catch(() => {});
  }
  await page.waitForTimeout(1500);

  const busiest = await busiestGeneration(page);
  expect(busiest.count, "không bung được đời nào đủ đông để đo").toBeGreaterThanOrEqual(8);

  /**
   * **Bước ngang giữa hai tấm thẻ kề nhau trong cùng một đời.**
   *
   * Đây là đại lượng duy nhất ở đây **không phụ thuộc bộ dữ liệu**. Bề rộng tuyệt đối của một đời
   * thì có: nó cộng cả những khoảng trống mà cây con bên dưới chừa ra, nên cùng một bộ hằng số vẫn
   * cho ra 3.040px ở gốc này và 4.904px ở gốc khác. Bước ngang thì rơi thẳng ra từ hằng số:
   *
   * ```
   *   hai vợ chồng   NODE_WIDTH + COUPLE_GAP          = 160 + 36 = 196px   (trước: 208 + 48 = 256px)
   *   hai anh em     NODE_WIDTH + HIERARCHICAL_NODE_SEP = 160 + 32 = 192px   (trước: 208 + 32 = 240px)
   * ```
   *
   * Trần 210px nằm giữa 196 và 240 — một bước lùi về thẻ 208px thì đỏ ngay, còn dao động của bộ
   * dữ liệu thì không chạm tới.
   *
   * Bề rộng tuyệt đối vẫn được đo và **in ra trong thông báo lỗi**: đó là con số người dùng cảm
   * thấy, nó chỉ không phải là con số đáng ghim.
   */
  const shape =
    `đời đông nhất: ${busiest.count} người · trải ${busiest.width}px · thẻ ${busiest.cardWidth}px · ` +
    `bước ngang nhỏ nhất ${busiest.pitch}px`;

  expect(busiest.cardWidth, shape).toBe(160);
  expect(
    busiest.pitch,
    `${shape}. Bản thẻ 208px có bước ngang 256px (vợ chồng) / 240px (anh em); vượt 210 nghĩa là ` +
      "hằng số bố cục đã bị nới lại."
  ).toBeLessThan(210);
});

/**
 * **Nhảy tới một người theo tên.** Không có nó thì bước "tự nhận mình" của luồng đăng ký không
 * dùng được — người mới phải tìm chính mình trong 1.500 người bằng mắt.
 */
test("gõ tên vào ô tìm trên canvas là nhảy tới đúng người, và mở đường từ gốc xuống họ", async ({
  page,
}) => {
  await page.goto("/tree");
  await waitForTreeReady(page);
  const before = await visibleCount(page);

  await jumpInput(page).fill("Nguyễn Văn");
  const results = page.getByTestId("tree-jump-result");
  await expect(results.first()).toBeVisible();

  // Chọn người ở ĐỜI SÂU NHẤT trong danh sách: người ở đời 2 đã hiện sẵn cạnh gốc, nên nhảy tới
  // họ không chứng minh được rằng đường từ gốc xuống đã được mở.
  const rows = await results.all();
  let target = rows[0]!;
  let deepest = -1;
  for (const row of rows) {
    const gen = Number(/Đời (\d+)/.exec((await row.innerText()) ?? "")?.[1] ?? "-1");
    if (gen > deepest) {
      deepest = gen;
      target = row;
    }
  }
  const personId = await target.getAttribute("data-person-id");
  await target.click();

  // Đúng một tấm thẻ mang dấu hiệu "đang là tâm điểm", và đó là người vừa chọn.
  await expect(page.locator('[data-focused="true"]')).toHaveCount(1);
  await expect(
    page.locator(`.react-flow__node[data-id="${personId}"] [data-focused="true"]`)
  ).toBeVisible();

  // Đường từ gốc xuống họ đã mở ⇒ số người đang hiện TĂNG. Không có phép mở đường thì họ sẽ
  // không bao giờ lộ ra, và con số đứng im.
  await expect.poll(() => visibleCount(page)).toBeGreaterThan(before);

  // `?focus=` nằm trên URL: một liên kết "đây, chỗ của ông trên phả đồ" gửi qua Zalo phải mở lại
  // đúng chỗ ấy.
  //
  // `expect.poll` chứ không đọc một lần: `router.replace` của App Router là bất đồng bộ, và trên
  // `next dev` nó còn đi một vòng về máy chủ để dựng lại thành phần trang. Đọc một lần là đang
  // ghim một sự trùng hợp về thời điểm, không phải ghim hành vi.
  await expect
    .poll(() => new URL(page.url()).searchParams.get("focus"), { timeout: 15_000 })
    .toBe(personId);
});

/** Mở thẳng bằng `?focus=` cũng phải ra đúng kết quả — đó là đầu NHẬN của liên kết vừa nói. */
test("mở /tree?focus= dẫn thẳng tới đúng tấm thẻ", async ({ page }) => {
  await page.goto("/tree");
  await waitForTreeReady(page);
  await jumpInput(page).fill("Nguyễn Văn");
  await expect(page.getByTestId("tree-jump-result").first()).toBeVisible();
  const personId = await page
    .getByTestId("tree-jump-result")
    .first()
    .getAttribute("data-person-id");

  await page.goto(`/tree?focus=${personId}`);
  await waitForTreeReady(page);

  await expect(
    page.locator(`.react-flow__node[data-id="${personId}"] [data-focused="true"]`)
  ).toBeVisible();
});

/**
 * **Nói rõ đang mở chi nào.** Trưởng chi mở phả đồ thấy một cụ đời 2 mà không biết vì sao là cụ
 * ấy — máy chủ chọn gốc theo vai + phạm vi `ltree`, và giao diện im lặng về chuyện đó.
 */
test("một dòng nói rõ phả đồ đang mở từ chi nào và từ cụ nào", async ({ page }) => {
  await page.goto("/tree");
  await waitForTreeReady(page);

  const banner = page.getByTestId("tree-root-banner");
  await expect(banner).toBeVisible();
  await expect(banner).toContainText(/Đang mở/);
  // Tên cụ gốc phải là tên THẬT trên canvas, không phải một nhãn chung chung.
  const names = await visibleNodeNames(page);
  const text = await banner.innerText();
  expect(
    names.some((n) => text.includes(n)),
    `dải ngữ cảnh nói "${text.replace(/\n/g, " ")}" mà không nhắc tên cụ nào đang có trên canvas`
  ).toBe(true);
  // Và lối đổi chi nằm ngay cạnh câu trả lời.
  await expect(banner.getByRole("button", { name: "Đổi chi khác" })).toBeVisible();
});

/**
 * **Kiểu xem danh sách theo đời.** Kéo một bức tranh vài nghìn pixel qua ô cửa 390px bằng hai
 * ngón là thao tác khó với người lớn tuổi; danh sách giải đúng bài toán ấy.
 */
test("kiểu xem Theo đời thay hẳn canvas và đi xuống được từng đời", async ({ page }) => {
  await page.goto("/tree");
  await waitForTreeReady(page);

  await segmented(page, "Theo đời").click();
  const list = page.getByTestId("tree-generation-list");
  await expect(list).toBeVisible();
  // Canvas biến mất hẳn: đây là một lối đi khác, không phải một lớp phủ lên bức tranh cũ.
  await expect(page.locator(".react-flow__node")).toHaveCount(0);

  const firstRows = await page.getByTestId("tree-generation-list-row").count();
  expect(firstRows).toBeGreaterThan(0);

  await page.getByTestId("tree-generation-list-descend").first().click();
  await expect
    .poll(() => page.getByTestId("tree-generation-list-row").count())
    .toBeGreaterThan(0);

  // Đường đã đi quay lên được — trên điện thoại đó là lối lùi duy nhất.
  const trail = list.getByRole("navigation");
  await expect(trail.getByRole("button").first()).toBeEnabled();

  // Sống trên URL như hai chế độ kia, nên gửi được qua Zalo.
  await expect.poll(() => new URL(page.url()).searchParams.get("view")).toBe("doi");
});

/** `?view=doi` mở thẳng ra danh sách — đầu NHẬN của liên kết vừa nói. */
test("?view=doi mở thẳng kiểu xem danh sách theo đời", async ({ page }) => {
  await page.goto("/tree?view=doi");
  await expect(page.getByTestId("tree-generation-list")).toBeVisible();
  await expect(
    page.locator(".ant-segmented-item-selected").filter({ hasText: "Theo đời" })
  ).toBeVisible();
});

/**
 * **Nút "Về chỗ tôi".** Máy chủ đã trả `personId` ở `/me` từ lâu và giao diện chưa dùng — đó là
 * lý do "cuộn ba bước là lạc".
 *
 * Bộ giả lập CỐ Ý để hồ sơ của hai tài khoản đăng nhập (`p-102` / `p-103`) nằm NGOÀI đồ thị phả
 * đồ (xem `src/mocks/data.ts`), nên ở đây ca này chỉ khẳng định được hai điều — và chúng đúng là
 * hai điều dễ hỏng nhất: nút CÓ mặt cho người đã gắn hồ sơ, và một cú nhảy không tới được đích
 * **không** làm hỏng phả đồ.
 */
test('nút "Về chỗ tôi" có mặt, và một cú nhảy hụt không kéo sập phả đồ', async ({ page }) => {
  await page.goto("/tree");
  await waitForTreeReady(page);

  await expect(page.getByTestId("tree-go-to-self")).toBeVisible();

  await page.getByTestId("tree-go-to-self").click();
  await page.waitForTimeout(1500);

  // Phả đồ vẫn là phả đồ: không dải đỏ, không màn "không tìm thấy gốc", thẻ vẫn còn.
  await expect(page.locator(".ant-alert-error")).toHaveCount(0);
  await expect(page.locator(".react-flow__node").first()).toBeVisible();
  expect(await visibleCount(page)).toBeGreaterThan(0);
});

/** Khách không có hồ sơ nào để về, nên không được thấy một cái nút bấm vào không xảy ra gì. */
test('khách KHÔNG thấy nút "Về chỗ tôi"', async ({ page }) => {
  await signInAs(page, "guest");
  await page.goto("/tree");
  await waitForTreeReady(page);
  await expect(page.getByTestId("tree-go-to-self")).toHaveCount(0);
});

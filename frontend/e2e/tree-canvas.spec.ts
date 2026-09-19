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
  const toggle = nodeToggle(page, nodeId);

  for (let attempt = 0; attempt < 5; attempt += 1) {
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

  await toggle.click();
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

test("renders the phả đồ from the thủy tổ with real people on it", async ({
  page,
  consoleErrors,
}) => {
  await page.goto("/tree");
  await waitForTreeReady(page);

  const names = await visibleNodeNames(page);
  expect(names.length).toBeGreaterThan(1);
  expect(names.join(" ")).toContain("Nguyễn Văn Thủy Tổ");
  // Đã tải nhiều hơn số thẻ đang vẽ: nạp trước một vòng, cộng phép cắt thẻ ngoài khung của
  // React Flow.
  expect(await loadedCount(page)).toBeGreaterThan(names.length);

  expect(consoleErrors, consoleErrors.join("\n")).toEqual([]);
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

  for (const mode of ["Tỏa tròn", "Ma trận đời", "Phân cấp"]) {
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
  const matrix = await positionsFor("Ma trận đời");

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
  await expect(toggle).toHaveAttribute("aria-expanded", "true");
  const loadedAfterExpand = await loadedCount(page);
  const visibleAfterExpand = await visibleCount(page);

  await clickNodeToggle(page, nodeId);
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

  await segmented(page, "Ma trận đời").click();
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

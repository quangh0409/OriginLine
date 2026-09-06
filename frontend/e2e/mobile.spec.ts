import {
  expect,
  expectNoLeakyPlaceholders,
  segmented,
  signInAs,
  test,
  waitForTreeReady,
} from "./fixtures";
import type { Page } from "@playwright/test";

/**
 * Mobile is the primary form factor here: the diaspora members this portal
 * exists for read it on a phone (BA v2 — "kiều bào mobile-first"), and the
 * people most likely to double-check a danh xưng are the oldest members of
 * the clan, on a small screen with large text.
 *
 * Runs under the `mobile-chromium` project (Pixel 5, touch enabled).
 */

/**
 * How far the page can be scrolled sideways. Anything above ~2px is a bug.
 *
 * Retried rather than read once: several screens (kinship, search) sync their
 * state into the URL with `router.replace` shortly after mount, and a plain
 * `page.evaluate` landing on that navigation dies with "Execution context was
 * destroyed" — a flake that looks exactly like a layout failure in the report.
 */
async function horizontalOverflow(page: Page): Promise<number> {
  const read = () =>
    page.evaluate(
      () => document.documentElement.scrollWidth - document.documentElement.clientWidth
    );
  for (let attempt = 0; attempt < 5; attempt += 1) {
    try {
      return await read();
    } catch (error) {
      if (attempt === 4) throw error;
      await page.waitForTimeout(300);
    }
  }
  return read();
}

/**
 * How many person cards are genuinely readable: at least 60% of the card's
 * area inside BOTH the canvas element and the browser window. A card whose
 * top sliver pokes into the canvas from below is not "on screen" to a reader,
 * so a plain rectangle-overlap check would pass on a blank-looking tree.
 */
async function cardsMostlyVisible(page: Page): Promise<number> {
  return page.evaluate(() => {
    const canvas = document.querySelector(".react-flow")?.getBoundingClientRect();
    if (!canvas) return 0;
    const view = {
      left: Math.max(canvas.left, 0),
      top: Math.max(canvas.top, 0),
      right: Math.min(canvas.right, window.innerWidth),
      bottom: Math.min(canvas.bottom, window.innerHeight),
    };
    return [...document.querySelectorAll(".react-flow__node")].filter((n) => {
      const r = n.getBoundingClientRect();
      if (r.width === 0 || r.height === 0) return false;
      const w = Math.max(0, Math.min(r.right, view.right) - Math.max(r.left, view.left));
      const h = Math.max(0, Math.min(r.bottom, view.bottom) - Math.max(r.top, view.top));
      return (w * h) / (r.width * r.height) >= 0.6;
    }).length;
  });
}

/**
 * The data-id of the first card that is readable AND actually receives a tap
 * at its own centre (i.e. nothing is drawn on top of it).
 */
async function firstTappableCardId(page: Page): Promise<string | null> {
  return page.evaluate(() => {
    const canvas = document.querySelector(".react-flow")?.getBoundingClientRect();
    if (!canvas) return null;
    const view = {
      left: Math.max(canvas.left, 0),
      top: Math.max(canvas.top, 0),
      right: Math.min(canvas.right, window.innerWidth),
      bottom: Math.min(canvas.bottom, window.innerHeight),
    };
    for (const n of document.querySelectorAll(".react-flow__node")) {
      const r = n.getBoundingClientRect();
      if (r.width === 0 || r.height === 0) continue;
      const w = Math.max(0, Math.min(r.right, view.right) - Math.max(r.left, view.left));
      const h = Math.max(0, Math.min(r.bottom, view.bottom) - Math.max(r.top, view.top));
      if ((w * h) / (r.width * r.height) < 0.6) continue;
      const hit = document.elementFromPoint(r.left + r.width / 2, r.top + r.height / 2);
      if (hit && hit.closest(".react-flow__node") === n) return n.getAttribute("data-id");
    }
    return null;
  });
}

/**
 * Zooms out until at least one card is actually inside the canvas. Needed
 * because the canvas does not frame itself on load (see the fitView bug in
 * tree-canvas.spec.ts) — without this, everything below is untestable on a
 * phone rather than merely ugly.
 */
async function bringCardsIntoView(page: Page): Promise<void> {
  const box = (await page.locator(".react-flow__pane").boundingBox())!;
  for (let i = 0; i < 8; i += 1) {
    if ((await cardsMostlyVisible(page)) > 0) break;
    await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
    await page.mouse.wheel(0, 400);
  }
  await waitForViewportSettled(page);
}

/**
 * React Flow animates the zoom transform and globals.css adds a CSS
 * transition on every node, so a card is "unstable" for a while after a wheel
 * gesture. Playwright refuses to tap an unstable element, so wait for the
 * transform to stop moving rather than for a fixed delay.
 */
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

test.beforeEach(async ({ page }) => {
  await signInAs(page, "member");
});

/**
 * BUG WATCH — the app shell overflows a phone screen by ~193px.
 *
 * <Header> puts its nav links in an Ant Design <Space> tagged
 * `hidden md:flex`. Ant Design injects `.ant-space { display: inline-flex }`
 * into <head> at RUNTIME, after Tailwind's stylesheet, and both selectors
 * have the same specificity — so antd wins and Tailwind's `hidden` never
 * applies. The four nav links stay laid out on a 393px screen, the header
 * wraps into six lines, and every page scrolls sideways.
 *
 * Artifact: .playwright-mcp/mobile-tree-first-load.png
 */
test("no page scrolls sideways on a phone", async ({ page }) => {
  for (const path of ["/", "/tree", "/persons/p-001", "/kinship?from=p-010&to=p-001"]) {
    await page.goto(path);
    await page.waitForLoadState("networkidle");
    expect(await horizontalOverflow(page), `${path} scrolls horizontally`).toBeLessThanOrEqual(2);
  }
});

/**
 * BUG WATCH — the phả đồ opens blank on a phone.
 *
 * The toolbar reports "47 nhân khẩu đang hiển thị" while the canvas shows an
 * empty dot grid: the cards are laid out at y≈817 in a canvas that is only
 * ~450px tall, because the one-shot `fitView` in TreeCanvasInner never runs
 * (see tree-canvas.spec.ts for the StrictMode cause). On a desktop the raw
 * dagre coordinates happen to land inside the viewport, which is why this is
 * invisible unless someone opens a phone.
 */
test("shows at least one person card inside the canvas on first load", async ({ page }) => {
  await page.goto("/tree");
  await waitForTreeReady(page);
  await expect(page.getByText(/nhân khẩu đang hiển thị/)).toBeVisible();

  expect(
    await cardsMostlyVisible(page),
    "the canvas is blank on first load — the cards are laid out off-screen"
  ).toBeGreaterThan(0);
});

test("the phả đồ toolbar is usable on a phone viewport", async ({ page, consoleErrors }) => {
  await page.goto("/tree");
  await waitForTreeReady(page);

  await expect(segmented(page, "Phân cấp")).toBeVisible();
  await expect(segmented(page, "Tỏa tròn")).toBeVisible();
  expect(consoleErrors, consoleErrors.join("\n")).toEqual([]);
});

/**
 * "Thu toàn cây" — đường thoát khỏi sàn phóng, dành cho người muốn nhìn toàn cảnh.
 *
 * Phả đồ CỐ Ý mở ra ở sàn 0.75 để còn đọc và bấm được trên điện thoại (xem ca ngay dưới). Nhưng
 * với nhiều dòng họ, khoảnh khắc thấy trọn cả họ mình — chiếu lên màn hình ngày giỗ Tổ, họp họ —
 * mới là giá trị của sản phẩm, nên phải có một nút làm đúng việc đó. Ca này kiểm hai điều mà chỉ
 * trình duyệt thật trả lời được:
 *
 *   1. bấm xong mức phóng THẬT SỰ xuống dưới sàn 0.75 (tức `fitView` không bị áp sàn);
 *   2. bản thân cái nút vẫn đạt ngưỡng chạm 44px trên điện thoại, và không bị lớp phủ nào
 *      (Controls / MiniMap / chú giải) nuốt mất thao tác chạm.
 */
test('nút "Thu toàn cây" thu được cả cây và vẫn chạm được trên điện thoại', async ({
  page,
  consoleErrors,
}) => {
  const scaleNow = async () => {
    const style = (await page.locator(".react-flow__viewport").getAttribute("style")) ?? "";
    return Number(/scale\(([\d.]+)\)/.exec(style)?.[1] ?? "1");
  };

  await page.goto("/tree");
  await waitForTreeReady(page);
  await waitForViewportSettled(page);
  expect(await scaleNow(), "phả đồ đã không mở ra ở sàn dễ đọc").toBeGreaterThanOrEqual(0.75);

  const button = page.getByTestId("tree-fit-whole");
  await expect(button).toBeVisible();

  const box = (await button.boundingBox())!;
  expect(Math.round(box.width)).toBeGreaterThanOrEqual(44);
  expect(Math.round(box.height)).toBeGreaterThanOrEqual(44);

  // Chạm đúng tâm nút: nếu có gì vẽ đè lên thì thao tác này rơi vào phần tử khác.
  await button.tap();
  await waitForViewportSettled(page);

  expect(
    await scaleNow(),
    "bấm Thu toàn cây mà mức phóng vẫn bị chặn ở sàn 0.75"
  ).toBeLessThan(0.75);
  // Bỏ qua đúng một cảnh báo: antd bắn "[antd: compatible]" ngay lần bấm <Button> đầu tiên
  // (hiệu ứng gợn sóng của nó đi qua đường render cũ). Cảnh báo ấy có sẵn trong ứng dụng và
  // không liên quan gì tới nút này; tests/setup/vitest.setup.ts cũng lọc y hệt. Mọi lỗi khác
  // vẫn phải rỗng — bấm nút mà sinh lỗi thật thì ca này vẫn đỏ.
  const realErrors = consoleErrors.filter((e) => !e.includes("[antd: compatible]"));
  expect(realErrors, realErrors.join("\n")).toEqual([]);
});

test("tapping a node opens the profile as a bottom sheet, not a side panel", async ({ page }) => {
  await page.goto("/tree");
  await waitForTreeReady(page);
  await bringCardsIntoView(page);

  const cardId = await firstTappableCardId(page);
  expect(
    cardId,
    "no person card is both on screen and able to receive its own tap — blocked by " +
      "the missing fitView (cards laid out off-screen) and by the descent edges being " +
      "drawn on top of the cards (toFlowEdges zIndex: 1)"
  ).not.toBeNull();
  await page.locator(`.react-flow__node[data-id="${cardId}"]`).tap();
  const drawer = page.getByRole("dialog");
  await expect(drawer).toBeVisible();

  // Bottom sheet: the panel occupies the lower part of the screen so the tree
  // keeps the top and the content is in thumb reach.
  const viewport = page.viewportSize()!;
  const box = (await drawer.boundingBox())!;
  expect(box.y).toBeGreaterThan(viewport.height * 0.1);
  expect(box.width).toBeGreaterThan(viewport.width * 0.9);
});

/**
 * Touch target on a ZOOMABLE canvas.
 *
 * The control itself must clear WCAG 2.2 SC 2.5.8 (24x24 CSS px) at 1:1. It
 * cannot also clear it at every camera zoom — a fit-to-content canvas exists
 * precisely to render its contents smaller, which is the criterion's own
 * "essential presentation" exception (the same one that covers map pins).
 * Pinning a raw `boundingBox().width >= 24` therefore measures the CAMERA,
 * not the control: it passed only while `fitView` was broken and the canvas
 * happened to open at scale 1, and it started failing the moment the camera
 * was fixed. What is actually required, and asserted here, is:
 *
 *   1. the control is 24x24 at 1:1 (measured size / current scale);
 *   2. the phả đồ does not OPEN below the legibility floor the canvas sets
 *      (MIN_INITIAL_ZOOM = 0.75 in tree-canvas-inner.tsx), so the control is
 *      never smaller than 18px on arrival;
 *   3. a tap at the control's own centre actually reaches it — nothing is
 *      drawn on top of it.
 */
test("the expand/collapse toggle is a real touch target, and the canvas opens above the legibility floor", async ({
  page,
}) => {
  await page.goto("/tree");
  await waitForTreeReady(page);

  const toggle = page.getByRole("button", { name: "Mở rộng nhánh này" }).first();
  await expect(toggle).toBeVisible({ timeout: 30_000 });
  // `fitView` animates over 300ms; reading the transform mid-flight reports a
  // zoom the user never actually sees (0.7406 on the way to 0.75).
  await waitForViewportSettled(page);

  const style = (await page.locator(".react-flow__viewport").getAttribute("style")) ?? "";
  const scale = Number(/scale\(([\d.]+)\)/.exec(style)?.[1] ?? "1");
  expect(scale, "the phả đồ opened below its legibility floor").toBeGreaterThanOrEqual(0.75);

  const box = (await toggle.boundingBox())!;
  expect(Math.round(box.width / scale)).toBeGreaterThanOrEqual(24);
  expect(Math.round(box.height / scale)).toBeGreaterThanOrEqual(24);

  // And it is genuinely hittable where the user aims: its own centre.
  //
  // Only toggles whose centre is INSIDE the canvas count. React Flow keeps a
  // culled node in the DOM with its layout rect intact, so a toggle belonging
  // to a card scrolled off the canvas still reports a bounding box — and
  // `elementFromPoint` at that box returns whatever is painted there instead
  // (the bottom tab bar, or nothing at all). Hit-testing those would be
  // measuring the wrong element, not a defect.
  const hits = await page.evaluate(() => {
    const canvas = document.querySelector(".react-flow")?.getBoundingClientRect();
    if (!canvas) return { checked: 0, blocked: [] as string[] };
    const view = {
      left: Math.max(canvas.left, 0),
      top: Math.max(canvas.top, 0),
      right: Math.min(canvas.right, window.innerWidth),
      bottom: Math.min(canvas.bottom, window.innerHeight),
    };
    let checked = 0;
    const blocked: string[] = [];
    for (const button of document.querySelectorAll<HTMLElement>(".react-flow__node button")) {
      const r = button.getBoundingClientRect();
      const cx = r.left + r.width / 2;
      const cy = r.top + r.height / 2;
      if (cx < view.left || cx > view.right || cy < view.top || cy > view.bottom) continue;
      checked += 1;
      const hit = document.elementFromPoint(cx, cy);
      if (!(hit && button.contains(hit))) {
        blocked.push(
          `${button.closest(".react-flow__node")?.getAttribute("data-id")} bị che bởi ` +
            `${hit ? (hit as HTMLElement).className || hit.tagName : "không có gì"}`
        );
      }
    }
    return { checked, blocked };
  });
  expect(hits.checked, "không có nút mở rộng nào nằm trong canvas để kiểm tra").toBeGreaterThan(0);
  expect(hits.blocked, "có thứ được vẽ đè lên nút mở rộng nhánh").toEqual([]);
});

test("a person profile reads top to bottom on a phone", async ({ page }) => {
  await page.goto("/persons/p-001");
  await expect(page.getByRole("heading", { name: /Nguyễn Văn Thủy Tổ/ })).toBeVisible({
    timeout: 30_000,
  });

  // Sections stack rather than sitting side by side.
  const sections = page.locator("article section");
  const count = await sections.count();
  expect(count).toBeGreaterThan(1);
  const first = (await sections.nth(0).boundingBox())!;
  const second = (await sections.nth(1).boundingBox())!;
  expect(second.y).toBeGreaterThan(first.y);

  await expectNoLeakyPlaceholders(page);
});

test("the danh xưng lookup works on a phone", async ({ page }) => {
  await page.goto("/kinship?from=p-010&to=p-001");
  await expect(page.getByText("Cha", { exact: true }).first()).toBeVisible({ timeout: 30_000 });
  await expect(page.getByText("Tổ chung", { exact: true })).toBeVisible();
  await expect(page.getByText("và xưng là")).toBeVisible();
});

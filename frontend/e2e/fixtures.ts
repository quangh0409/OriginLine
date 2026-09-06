import { expect, test as base, type Page } from "@playwright/test";

export type MockRole = "guest" | "member" | "branch-head" | "admin";

const DEV_ROLE_STORAGE_KEY = "giapha.dev-role";

/**
 * Shared E2E helpers.
 *
 * `consoleErrors` is collected on every test: a React error, a failed MSW
 * interception or a next-intl MISSING_MESSAGE all surface there long before
 * they become a visible defect, and a silent console is part of what "the
 * screen works" means.
 */
export const test = base.extend<{ consoleErrors: string[] }>({
  consoleErrors: async ({ page }, use) => {
    const errors: string[] = [];
    page.on("console", (msg) => {
      if (msg.type() === "error") errors.push(msg.text());
    });
    page.on("pageerror", (error) => errors.push(`pageerror: ${error.message}`));
    // eslint-disable-next-line react-hooks/rules-of-hooks -- Playwright's fixture `use`, not React's.
    await use(errors);
  },
});

export { expect };

/**
 * Pins the dev `x-mock-role` header for the whole browser context. Default is
 * deliberately left alone (guest) so the legally required default — an
 * anonymous visitor sees no living person — is what tests hit unless they
 * explicitly opt out of it.
 */
export async function signInAs(page: Page, role: MockRole): Promise<void> {
  await page.addInitScript(
    ([key, value]) => window.localStorage.setItem(key as string, value as string),
    [DEV_ROLE_STORAGE_KEY, role] as const
  );
}

/**
 * Ant Design's <Segmented> hides its real radio input and paints a label, so
 * `getByRole("radio")` resolves to an invisible element Playwright refuses to
 * click. Target the label the user actually taps.
 */
export function segmented(page: Page, label: string) {
  const exact = new RegExp("^\\s*" + label.replace(/[.*+?^${}()|[\]\\]/g, "\\$&") + "\\s*$");
  return page.locator(".ant-segmented-item-label").filter({ hasText: exact });
}

/**
 * Drags the canvas background to pan. Picks a point that is genuinely empty —
 * dragging on a person card moves the CARD (nodes are draggable by design),
 * which would silently make a pan assertion pass for the wrong reason.
 */
export async function panCanvas(page: Page, dx: number, dy: number): Promise<void> {
  const box = (await page.locator(".react-flow__pane").boundingBox())!;
  const candidates = [
    { x: box.x + box.width - 80, y: box.y + 60 },
    { x: box.x + 80, y: box.y + 40 },
    { x: box.x + box.width - 60, y: box.y + box.height - 200 },
    { x: box.x + box.width / 2, y: box.y + 30 },
  ];
  for (const point of candidates) {
    const onPane = await page.evaluate(
      ([x, y]) => {
        const el = document.elementFromPoint(x as number, y as number);
        return Boolean(el && (el as HTMLElement).classList.contains("react-flow__pane"));
      },
      [point.x, point.y] as const
    );
    if (!onPane) continue;
    await page.mouse.move(point.x, point.y);
    await page.mouse.down();
    await page.mouse.move(point.x + dx, point.y + dy, { steps: 12 });
    await page.mouse.up();
    return;
  }
  throw new Error("no empty point on the canvas to drag from");
}

/** Waits until the phả đồ canvas has painted at least one person card. */
export async function waitForTreeReady(page: Page): Promise<void> {
  await expect(page.locator(".react-flow__node").first()).toBeVisible({ timeout: 60_000 });
}

/** Every person card currently in the DOM, by rendered display name. */
export async function visibleNodeNames(page: Page): Promise<string[]> {
  return page.locator(".react-flow__node .font-serif").allInnerTexts();
}

/**
 * Nothing on screen may look like "there is data here you are not allowed to
 * see" — no dash placeholder, no bullets, no lock, no tier badge, and no
 * untranslated i18n key.
 */
export async function expectNoLeakyPlaceholders(page: Page): Promise<void> {
  const text = (await page.locator("body").innerText()) ?? "";
  expect(text, "next-intl key leaked to the screen").not.toContain("MISSING_MESSAGE");
  expect(text).not.toMatch(/•\s*•\s*•/);
  expect(text).not.toMatch(/\bbị ẩn\b/i);
  expect(text).not.toMatch(/\bkhông có quyền\b/i);
  expect(text).not.toMatch(/\bRESTRICTED\b/);
  await expect(page.locator('[data-icon="lock"]')).toHaveCount(0);
  await expect(page.locator('[data-icon="eye-invisible"]')).toHaveCount(0);
}

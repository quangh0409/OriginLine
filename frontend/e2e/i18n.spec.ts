import { expect, segmented, signInAs, test, waitForTreeReady } from "./fixtures";

/**
 * Bilingual VI–EN is a product requirement, and the failure mode is loud but
 * easy to ship: next-intl renders the literal string `MISSING_MESSAGE` (plus a
 * console error) for a key that exists in one catalogue and not the other. So
 * every screen is walked in both locales and both are asserted clean.
 *
 * Note what is deliberately NOT translated: a danh xưng is business data and
 * stays Vietnamese in the English UI (contracts/README §3).
 */

const SCREENS = [
  { name: "trang chủ", vi: "/", en: "/en" },
  { name: "phả đồ", vi: "/tree", en: "/en/tree" },
  { name: "tra danh xưng", vi: "/kinship?from=p-010&to=p-001", en: "/en/kinship?from=p-010&to=p-001" },
  { name: "hồ sơ nhân khẩu", vi: "/persons/p-001", en: "/en/persons/p-001" },
] as const;

/**
 * The app renders nothing until MswProvider's worker has started (dev only),
 * so `networkidle` alone can still catch an empty <body>. Wait for real text.
 */
async function bodyTextWhenPainted(page: import("@playwright/test").Page): Promise<string> {
  await expect
    .poll(async () => (await page.locator("body").innerText()).trim().length, { timeout: 60_000 })
    .toBeGreaterThan(20);
  return page.locator("body").innerText();
}

test.beforeEach(async ({ page }) => {
  await signInAs(page, "member");
});

for (const screen of SCREENS) {
  test(`${screen.name} renders in Vietnamese with no missing message`, async ({
    page,
    consoleErrors,
  }) => {
    await page.goto(screen.vi);
    const body = await bodyTextWhenPainted(page);
    expect(body, `MISSING_MESSAGE on ${screen.vi}`).not.toContain("MISSING_MESSAGE");
    expect(
      consoleErrors.filter((e) => e.includes("MISSING_MESSAGE") || e.includes("IntlError")),
      consoleErrors.join("\n")
    ).toEqual([]);
  });

  test(`${screen.name} renders in English with no missing message`, async ({
    page,
    consoleErrors,
  }) => {
    await page.goto(screen.en);
    const body = await bodyTextWhenPainted(page);
    expect(body, `MISSING_MESSAGE on ${screen.en}`).not.toContain("MISSING_MESSAGE");
    expect(
      consoleErrors.filter((e) => e.includes("MISSING_MESSAGE") || e.includes("IntlError")),
      consoleErrors.join("\n")
    ).toEqual([]);
  });
}

test("the language switcher moves between vi and en and keeps the page", async ({
  page,
  consoleErrors,
}) => {
  await page.goto("/tree");
  await waitForTreeReady(page);
  await expect(segmented(page, "Phân cấp")).toBeVisible();

  await segmented(page, "EN").click();
  await expect(page).toHaveURL(/\/en\/tree/);
  await expect(segmented(page, "Hierarchical")).toBeVisible({ timeout: 30_000 });

  await segmented(page, "VI").click();
  await expect(page).toHaveURL(/\/tree/);
  await expect(segmented(page, "Phân cấp")).toBeVisible({ timeout: 30_000 });

  expect(
    consoleErrors.filter((e) => e.includes("MISSING_MESSAGE")),
    consoleErrors.join("\n")
  ).toEqual([]);
});

test("a danh xưng stays Vietnamese in the English UI, because it is data not copy", async ({
  page,
}) => {
  await page.goto("/en/kinship?from=p-010&to=p-001");
  // Chrome UI in English...
  await expect(page.getByText("Relationship path")).toBeVisible({ timeout: 30_000 });
  // ...but the title itself is the clan's own word.
  await expect(page.getByText("Cha", { exact: true }).first()).toBeVisible();
});

test("the html lang attribute follows the locale, for screen readers and hyphenation", async ({
  page,
}) => {
  await page.goto("/");
  await expect(page.locator("html")).toHaveAttribute("lang", "vi");
  await page.goto("/en");
  await expect(page.locator("html")).toHaveAttribute("lang", "en");
});

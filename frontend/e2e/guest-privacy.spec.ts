import {
  expect,
  expectNoLeakyPlaceholders,
  signInAs,
  test,
  visibleNodeNames,
  waitForTreeReady,
} from "./fixtures";

/**
 * Phase-1 exit criterion, verbatim:
 *
 *   "Khách đăng xuất không truy cập được bất kỳ dữ liệu người còn sống nào —
 *    có test khẳng định."
 *
 * This file is that test. It is a legal requirement (Nghị định 13/2023), not a
 * UI preference, so it checks the rendered screen AND the network payloads:
 * a living person must not arrive in the browser at all, let alone be drawn.
 */

/** Ids the mock fixtures guarantee are living people. */
const LIVING_IDS = ["p-100", "p-101"];
const LIVING_NAMES = ["Nguyễn Văn An", "Nguyễn Thị Bé"];

test.describe("an anonymous guest", () => {
  test("is a guest by default — no opt-in required to be protected", async ({ page }) => {
    // No localStorage seeding here on purpose: the DEFAULT must already be
    // the safe state.
    await page.goto("/tree");
    await waitForTreeReady(page);

    const names = await visibleNodeNames(page);
    for (const living of LIVING_NAMES) {
      expect(names.join(" | "), `living person "${living}" rendered for a guest`).not.toContain(
        living
      );
    }
  });

  test("never receives a living person in any /tree payload", async ({ page }) => {
    const livingLeaks: string[] = [];

    page.on("response", async (response) => {
      if (!response.url().includes("/api/v1/")) return;
      let body: unknown;
      try {
        body = await response.json();
      } catch {
        return;
      }
      const walk = (value: unknown, path: string) => {
        if (Array.isArray(value)) {
          value.forEach((v, i) => walk(v, `${path}[${i}]`));
          return;
        }
        if (value && typeof value === "object") {
          const record = value as Record<string, unknown>;
          if (record.isAlive === true) {
            livingLeaks.push(`${response.url()} -> ${path} (id=${String(record.id)})`);
          }
          for (const [key, child] of Object.entries(record)) walk(child, `${path}.${key}`);
        }
      };
      walk(body, "$");
    });

    await page.goto("/tree");
    await waitForTreeReady(page);

    // Walk the guest deeper into the graph, where the living generations
    // actually live, so more /tree payloads are inspected. Zooming out between
    // clicks keeps toggles inside the viewport (React Flow culls whatever the
    // re-layout pushed off-screen, so a toggle can vanish mid-loop).
    const pane = page.locator(".react-flow__pane");
    for (let i = 0; i < 4; i += 1) {
      const toggle = page.getByRole("button", { name: "Mở rộng nhánh này" }).first();
      if ((await toggle.count()) === 0) break;
      await toggle.click({ timeout: 10_000 }).catch(() => undefined);
      await page.waitForLoadState("networkidle");
      const box = await pane.boundingBox();
      if (box) {
        await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
        await page.mouse.wheel(0, 300);
      }
    }
    await page.waitForLoadState("networkidle");

    expect(
      livingLeaks,
      `a living person reached an anonymous guest:\n${livingLeaks.join("\n")}`
    ).toEqual([]);
  });

  for (const id of LIVING_IDS) {
    test(`gets a plain not-found (never a 403) for living person ${id}`, async ({ page }) => {
      const statuses: number[] = [];
      page.on("response", (response) => {
        if (response.url().includes(`/api/v1/persons/${id}`)) statuses.push(response.status());
      });

      await page.goto(`/persons/${id}`);
      await expect(page.getByText("Không tìm thấy nhân khẩu này.")).toBeVisible({
        timeout: 30_000,
      });

      // 403 would confirm the person exists, which is the leak the tiering
      // exists to prevent.
      expect(statuses).not.toContain(403);
      expect(statuses).toContain(404);

      const body = await page.locator("body").innerText();
      expect(body).not.toMatch(/quyền/i);
      expect(body).not.toMatch(/đăng nhập để xem/i);
      await expectNoLeakyPlaceholders(page);
    });
  }

  test("still sees deceased ancestors in full — they are public", async ({ page }) => {
    await page.goto("/persons/p-001");
    await expect(page.getByRole("heading", { name: /Nguyễn Văn Thủy Tổ/ })).toBeVisible({
      timeout: 30_000,
    });
    await expect(page.getByText("Đã khuất")).toBeVisible();
    await expect(page.getByText("Tên húy")).toBeVisible();
    await expectNoLeakyPlaceholders(page);
  });

  test("cannot reach a living person through the danh xưng lookup either", async ({ page }) => {
    await page.goto("/kinship?from=p-001&to=p-100");
    await expect(page.getByText("Không tìm thấy một trong hai người.")).toBeVisible({
      timeout: 30_000,
    });
    const body = await page.locator("body").innerText();
    for (const living of LIVING_NAMES) expect(body).not.toContain(living);
  });

  test("the truncation banner is never used as a 'someone is hidden here' signal", async ({
    page,
  }) => {
    // A guest's tree can legitimately have holes with truncated=false. The
    // banner must only ever speak about volume, never about privacy.
    await page.goto("/tree");
    await waitForTreeReady(page);
    const banner = page.getByRole("alert");
    if (await banner.isVisible().catch(() => false)) {
      const text = await banner.innerText();
      expect(text).not.toMatch(/ẩn|riêng tư|quyền/i);
    }
  });
});

test.describe("a signed-in member, by contrast", () => {
  test("does see living relatives, which proves the guest test is not a false negative", async ({
    page,
  }) => {
    await signInAs(page, "member");
    await page.goto("/persons/p-100");
    await expect(page.getByRole("heading", { name: /Nguyễn Văn An/ })).toBeVisible({
      timeout: 30_000,
    });
    await expect(page.getByText("Còn sống")).toBeVisible();
    // ...but only at Tier 1: no contact block, no occupation.
    await expect(page.getByRole("heading", { name: "Liên hệ" })).toHaveCount(0);
    await expectNoLeakyPlaceholders(page);
  });

  test("an admin, and only an admin, sees Tier-3 contact details", async ({ page }) => {
    await signInAs(page, "admin");
    await page.goto("/persons/p-100");
    await expect(page.getByRole("heading", { name: "Liên hệ" })).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText("+84 912 345 678")).toBeVisible();
  });
});

import { defineConfig, devices } from "@playwright/test";

/**
 * E2E against the app running on MSW (`npm run dev:mock`), which is the only
 * way to exercise the Phase-1 exit criteria before the backend is wired: the
 * mock graph is deterministic (fixed seed), so a tree-render timing number
 * from one run is comparable with the next.
 *
 * Artifacts go to `.playwright-mcp/` inside the frontend package — never the
 * repo root.
 */
const PORT = Number(process.env.E2E_PORT ?? 3100);
const BASE_URL = process.env.E2E_BASE_URL ?? `http://127.0.0.1:${PORT}`;

export default defineConfig({
  testDir: "./e2e",
  outputDir: ".playwright-mcp/test-results",
  fullyParallel: false,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: [
    ["list"],
    ["html", { outputFolder: ".playwright-mcp/report", open: "never" }],
    ["json", { outputFile: ".playwright-mcp/results.json" }],
  ],
  timeout: 90_000,
  expect: { timeout: 15_000 },
  use: {
    baseURL: BASE_URL,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "off",
    locale: "vi-VN",
    timezoneId: "Asia/Ho_Chi_Minh",
  },
  projects: [
    {
      name: "desktop-chromium",
      use: { ...devices["Desktop Chrome"], viewport: { width: 1440, height: 900 } },
      // `e2e/real-auth/` cần Keycloak + backend + PostgreSQL sống, và phải
      // chạy ở cổng 3000 với MSW TẮT — xem playwright.real.config.ts.
      testIgnore: [/mobile\.spec\.ts/, /real-auth\//],
    },
    {
      // Diaspora members are mobile-first (BA v2) — the phone viewport is a
      // first-class target, not an afterthought.
      name: "mobile-chromium",
      use: { ...devices["Pixel 5"] },
      // `tree-legibility` chạy trên CẢ HAI dự án: bất biến "đường nối không bị thẻ che" và phép
      // thử chạm vào giữa thẻ ở mức thu nhỏ đều là chuyện của màn hình hẹp trước tiên — đó chính
      // là nơi lỗi cũ xuất hiện. Bỏ nó khỏi đây thì lời khẳng định "đã kiểm trên điện thoại" chỉ
      // đúng nhờ một khung nhìn giả lập trong spec, không phải nhờ thiết bị Pixel 5 thật của
      // Playwright.
      // `layout-containment` chạy trên CẢ HAI dự án, cùng lý do như `tree-legibility`: bất biến
      // "thẻ con không rộng hơn thẻ cha" là chuyện của màn hẹp TRƯỚC TIÊN — 393px là nơi lề 12px
      // và cột `flex-1` không co được lộ ra, còn 1440px thì chúng ẩn đi sau khoảng trống thừa.
      testMatch: [/mobile\.spec\.ts/, /tree-legibility\.spec\.ts/, /layout-containment\.spec\.ts/],
    },
  ],
  webServer: {
    command: "npm run dev:mock",
    url: BASE_URL,
    reuseExistingServer: !process.env.CI,
    // Next dev cold-compiles the [locale] route on the first request; on a
    // cold cache that alone can take several minutes on Windows.
    timeout: 600_000,
    stdout: "pipe",
    stderr: "pipe",
    env: {
      PORT: String(PORT),
      NEXT_PUBLIC_API_MOCKING: "enabled",
    },
  },
});

import { defineConfig, devices } from "@playwright/test";

/**
 * E2E **đối chứng với hệ thống thật** — Keycloak + backend + PostgreSQL đang
 * chạy. Tách khỏi `playwright.config.ts` (chạy trên MSW) vì hai bộ có ràng
 * buộc trái ngược nhau và không thể ở chung một tệp cấu hình:
 *
 *  - Cổng phải là **3000**. Client Keycloak `giapha-frontend` chỉ chấp nhận
 *    `redirectUris = http://localhost:3000/*`; đổi cổng là hỏng redirect ngay.
 *    Bộ MSW cố tình chạy ở 3100 để hai bên không tranh cổng.
 *  - `NEXT_PUBLIC_API_MOCKING` phải là `disabled`, nếu không service worker
 *    của MSW sẽ chặn mất chính những lời gọi mà bài kiểm này sinh ra để xem.
 *  - `baseURL` là `localhost` chứ **không** phải `127.0.0.1`: với Keycloak hai
 *    chuỗi đó là hai origin khác nhau, và `webOrigins` của client chỉ liệt kê
 *    `http://localhost:3000`.
 *
 * KHÔNG chạy được trong CI nếu chưa dựng hạ tầng. Xem README §Tests.
 */
const PORT = 3000;
const BASE_URL = `http://localhost:${PORT}`;

const API_BASE_URL = process.env.E2E_API_BASE_URL ?? "http://localhost:8090";
const KEYCLOAK_URL = process.env.E2E_KEYCLOAK_URL ?? "http://localhost:8081";
export default defineConfig({
  testDir: "./e2e/real-auth",
  outputDir: ".playwright-mcp/test-results-real",
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [
    ["list"],
    ["html", { outputFolder: ".playwright-mcp/report-real", open: "never" }],
    ["json", { outputFile: ".playwright-mcp/results-real.json" }],
  ],
  timeout: 180_000,
  expect: { timeout: 30_000 },
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
      name: "real-backend",
      use: { ...devices["Desktop Chrome"], viewport: { width: 1440, height: 900 } },
    },
  ],
  webServer: {
    // `next dev` trực tiếp, không qua `npm run dev:mock`: script ấy ép
    // NEXT_PUBLIC_API_MOCKING=enabled, đúng thứ phải tắt ở đây.
    command: `npx next dev -p ${PORT}`,
    url: BASE_URL,
    reuseExistingServer: !process.env.CI,
    // Next biên dịch nguội tuyến [locale] ngay ở lần gọi đầu; trên Windows
    // riêng việc đó đã có thể mất vài phút.
    timeout: 600_000,
    stdout: "pipe",
    stderr: "pipe",
    env: {
      PORT: String(PORT),
      NEXT_PUBLIC_API_MOCKING: "disabled",
      NEXT_PUBLIC_API_BASE_URL: API_BASE_URL,
      NEXT_PUBLIC_KEYCLOAK_URL: KEYCLOAK_URL,
      NEXT_PUBLIC_KEYCLOAK_REALM: process.env.E2E_KEYCLOAK_REALM ?? "giapha",
      NEXT_PUBLIC_KEYCLOAK_CLIENT_ID: process.env.E2E_KEYCLOAK_CLIENT_ID ?? "giapha-frontend",
      // KHÔNG đặt `NEXT_PUBLIC_DEFAULT_ROOT_ID` ở đây, và đó là chủ ý.
      //
      // Máy chủ nay tự chọn gốc phả đồ theo VAI người gọi: khách và Hội đồng mở ra thuỷ tổ,
      // trưởng chi mở ra cụ tổ của chính chi mình, thành viên mở ra gốc ngành mình. Ghim một
      // gốc ở đây thì mọi vai đều mở cùng một cây, và bộ E2E chạy với backend thật sẽ **không
      // bao giờ** đi qua nhánh vừa được dựng — tức nó xanh mà không kiểm gì.
    },
  },
});

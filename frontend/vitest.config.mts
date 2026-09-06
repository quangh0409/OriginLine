import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import tsconfigPaths from "vite-tsconfig-paths";

/**
 * Vitest for unit + component tests. Playwright (e2e/) is configured
 * separately and is deliberately excluded here — the two runners must never
 * pick up each other's files.
 *
 * `NEXT_PUBLIC_API_MOCKING=enabled` is set so `src/lib/api/dev-role.ts`
 * activates and `src/lib/api/http.ts` forwards the `x-mock-role` header. That
 * is what lets a component test pin a caller role (guest / member /
 * branch-head / admin) and assert privacy-tier rendering against the same MSW
 * handlers the app runs on in dev.
 */
export default defineConfig({
  plugins: [react(), tsconfigPaths()],
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./tests/setup/vitest.setup.ts"],
    include: ["tests/**/*.test.ts", "tests/**/*.test.tsx"],
    exclude: ["node_modules/**", ".next/**", "e2e/**"],
    env: {
      NEXT_PUBLIC_API_MOCKING: "enabled",
      NEXT_PUBLIC_API_BASE_URL: "http://localhost:8080",
    },
    // Ant Design + React Flow render a lot; the default 5s is tight on a
    // cold first component test.
    testTimeout: 20_000,
    hookTimeout: 20_000,
    restoreMocks: true,
    clearMocks: true,
  },
});

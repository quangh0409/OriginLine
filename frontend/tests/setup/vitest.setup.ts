import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterAll, afterEach, beforeAll, vi } from "vitest";
import { server } from "@/mocks/server";

/**
 * One MSW node server for the whole component suite — the SAME handlers the
 * app runs against under `npm run dev:mock`. Tests therefore exercise the
 * real request/response shapes (including the 404-not-403 guest
 * non-disclosure rule) instead of hand-stubbed fetch mocks that could drift
 * from the contract.
 *
 * `onUnhandledRequest: "error"` is deliberate: a component quietly calling an
 * endpoint nobody mocked would otherwise pass by rendering an error state.
 */
beforeAll(() => {
  server.listen({ onUnhandledRequest: "error" });
});

afterEach(() => {
  cleanup();
  server.resetHandlers();
  // The dev-role header is read from localStorage per request; leaking a role
  // between tests would make the privacy assertions order-dependent.
  window.localStorage.clear();
});

afterAll(() => {
  server.close();
});

// --- jsdom gaps that Ant Design / React Flow rely on ------------------------

if (!window.matchMedia) {
  Object.defineProperty(window, "matchMedia", {
    writable: true,
    value: (query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }),
  });
}

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}
if (!("ResizeObserver" in globalThis)) {
  (globalThis as unknown as Record<string, unknown>).ResizeObserver = ResizeObserverStub;
}

if (!("IntersectionObserver" in globalThis)) {
  class IntersectionObserverStub {
    observe() {}
    unobserve() {}
    disconnect() {}
    takeRecords() {
      return [];
    }
  }
  (globalThis as unknown as Record<string, unknown>).IntersectionObserver = IntersectionObserverStub;
}

if (!window.scrollTo) {
  Object.defineProperty(window, "scrollTo", { writable: true, value: vi.fn() });
}

// jsdom does not implement Blob URLs at all — components that preview a
// locally chosen file before upload (`posts/media`) call `createObjectURL`
// synchronously on file selection, and an unstubbed call throws
// "URL.createObjectURL is not a function" straight out of a render.
if (!window.URL.createObjectURL) {
  Object.defineProperty(window.URL, "createObjectURL", {
    writable: true,
    value: vi.fn(() => `blob:mock-${Math.random().toString(36).slice(2)}`),
  });
}
if (!window.URL.revokeObjectURL) {
  Object.defineProperty(window.URL, "revokeObjectURL", { writable: true, value: vi.fn() });
}

// antd's `rc-motion` / `rc-virtual-list` use these.
if (!(globalThis as unknown as Record<string, unknown>).requestIdleCallback) {
  (globalThis as unknown as Record<string, unknown>).requestIdleCallback = (cb: () => void) =>
    setTimeout(cb, 0);
  (globalThis as unknown as Record<string, unknown>).cancelIdleCallback = (id: number) =>
    clearTimeout(id);
}

// React 18 + antd emit a "unstable css-in-js" / findDOMNode style warning that
// buries real output. Keep errors, drop that one known line.
const originalWarn = console.warn;
console.warn = (...args: unknown[]) => {
  const first = typeof args[0] === "string" ? args[0] : "";
  if (first.includes("[antd: compatible]")) return;
  originalWarn(...(args as Parameters<typeof console.warn>));
};

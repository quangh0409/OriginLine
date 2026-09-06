"use client";

import { useEffect, useState, type ReactNode } from "react";

const MOCKING_ENABLED =
  process.env.NEXT_PUBLIC_API_MOCKING === "enabled" &&
  process.env.NODE_ENV !== "production";

/**
 * Starts the MSW browser worker before rendering children, so the very
 * first React Query fetch on mount is guaranteed to be intercepted (avoids a
 * race where a component fetches before the service worker has registered).
 * Enable with `npm run dev:mock` (sets NEXT_PUBLIC_API_MOCKING=enabled).
 *
 * When mocking is disabled (default `npm run dev`, and always in
 * production), this renders children immediately and does nothing — it never
 * ships to prod.
 */
export function MswProvider({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(!MOCKING_ENABLED);

  useEffect(() => {
    if (!MOCKING_ENABLED) return;
    let cancelled = false;
    import("./browser").then(({ worker }) => {
      worker
        .start({
          onUnhandledRequest: "bypass",
          serviceWorker: { url: "/mockServiceWorker.js" },
        })
        .then(() => {
          if (!cancelled) setReady(true);
        });
    });
    return () => {
      cancelled = true;
    };
  }, []);

  if (!ready) return null;
  return <>{children}</>;
}

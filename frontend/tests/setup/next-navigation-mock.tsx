import type { AnchorHTMLAttributes, ReactNode } from "react";
import { vi } from "vitest";

/**
 * Minimal stand-ins for the App Router hooks that F3/F4/F5 components import.
 *
 * The components under test only ever use navigation to LEAVE the screen, so
 * a spy plus a plain anchor is enough — and it keeps these tests free of a
 * Next.js server runtime, which is what makes them fast enough to run on
 * every save.
 */
export const routerMock = {
  push: vi.fn(),
  replace: vi.fn(),
  back: vi.fn(),
  forward: vi.fn(),
  refresh: vi.fn(),
  prefetch: vi.fn(),
};

export function resetRouterMock(): void {
  Object.values(routerMock).forEach((fn) => fn.mockReset());
}

export function TestLink({
  href,
  children,
  ...rest
}: { href: string; children?: ReactNode } & AnchorHTMLAttributes<HTMLAnchorElement>) {
  return (
    <a href={href} {...rest}>
      {children}
    </a>
  );
}

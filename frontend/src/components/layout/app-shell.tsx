"use client";

import type { ReactNode } from "react";
import { Layout } from "antd";
import { Header } from "./header";
import { MobileNav } from "./mobile-nav";
import { PushPermissionPrompt } from "@/components/notifications/push-permission-prompt";

/**
 * IMPORTANT — must stay a Client Component ("use client" above), not a
 * Server Component. Ant Design attaches compound sub-components (here:
 * `Layout.Content`, `Layout.Footer`) as runtime static properties on the
 * default export, inside a module marked "use client" by antd itself. When
 * a *Server* Component imports such a module, Next.js substitutes the
 * import with a client-reference placeholder that supports being rendered
 * as a JSX tag (`<Layout>` works) but does NOT proxy arbitrary property
 * access (`Layout.Content`/`Layout.Footer` silently become `undefined`,
 * surfacing as "Element type is invalid ... got: undefined" at render time).
 * The same rule applies to any other antd compound API — `Typography.Text`,
 * `List.Item`, `Form.Item`, `Menu.Item`, etc. — never destructure/access
 * those from a plain Server Component; keep the access inside a "use
 * client" file.
 *
 * Top-level page chrome. Deliberately thin — most of the interesting logic
 * lives in <Header>. Sprint 2+ pages (tree canvas, kinship lookup, ...) wrap
 * their content in this same shell.
 *
 * Two F7 pieces are mounted app-wide from here rather than per page:
 *  - <MobileNav>, because on a phone the header's links are hidden and the
 *    notification centre must not be buried in a popover.
 *  - <PushPermissionPrompt>, which decides for itself whether it is due; it
 *    renders null until the user has actually read a profile or a giỗ, so
 *    mounting it everywhere does not mean asking everywhere.
 */
export function AppShell({ children }: { children: ReactNode }) {
  return (
    <Layout className="min-h-screen !bg-bg-page">
      <Header />
      <Layout.Content>{children}</Layout.Content>
      <Layout.Footer className="text-center text-xs text-text-muted !bg-transparent">
        © {new Date().getFullYear()} Cổng Thông Tin Gia Phả Dòng Họ
      </Layout.Footer>
      {/* Clears the fixed bottom tab bar so the footer is never trapped under it. */}
      <div className="h-14 md:hidden" aria-hidden />
      <MobileNav />
      <PushPermissionPrompt />
    </Layout>
  );
}

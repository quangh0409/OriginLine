"use client";

import { Badge } from "antd";
import {
  ApartmentOutlined,
  BellOutlined,
  CalendarOutlined,
  HomeOutlined,
  SearchOutlined,
} from "@ant-design/icons";
import { useTranslations } from "next-intl";
import { Link, usePathname } from "@/i18n/navigation";
import { useUnreadCount } from "@/hooks/use-notifications";
import { colorTokens } from "@/styles/tokens";

const ITEMS = [
  { key: "home", href: "/", icon: <HomeOutlined /> },
  { key: "tree", href: "/tree", icon: <ApartmentOutlined /> },
  { key: "search", href: "/search", icon: <SearchOutlined /> },
  { key: "events", href: "/events", icon: <CalendarOutlined /> },
  { key: "notifications", href: "/notifications", icon: <BellOutlined /> },
] as const;

/**
 * Bottom tab bar for phones (hidden from `md:` up, where the header nav
 * takes over).
 *
 * Diaspora members are mobile-first, and the header's inline links are
 * `hidden md:flex` — on a phone they simply do not exist. Without this bar
 * the notification centre, the MVP's primary giỗ-reminder channel, would be
 * reachable only through the header bell popover, which is exactly the
 * "giấu trong menu phụ" the plan rules out for F7.
 */
export function MobileNav() {
  const t = useTranslations("nav");
  const pathname = usePathname();
  const unreadCount = useUnreadCount();

  return (
    <nav
      aria-label={t("primary")}
      className="fixed inset-x-0 bottom-0 z-30 grid grid-cols-5 border-t bg-bg-card md:hidden"
      style={{
        borderColor: colorTokens.border,
        // Keep the bar clear of the iPhone home indicator.
        paddingBottom: "env(safe-area-inset-bottom)",
      }}
    >
      {ITEMS.map((item) => {
        const active =
          item.href === "/" ? pathname === "/" : pathname.startsWith(item.href);
        return (
          <Link
            key={item.key}
            href={item.href}
            aria-current={active ? "page" : undefined}
            className="flex flex-col items-center gap-0.5 py-2 no-underline"
            style={{ color: active ? colorTokens.primary : colorTokens.textMuted }}
          >
            <span className="text-[17px]" aria-hidden>
              {item.key === "notifications" ? (
                <Badge count={unreadCount} size="small" offset={[2, -2]}>
                  <span style={{ color: "inherit" }}>{item.icon}</span>
                </Badge>
              ) : (
                item.icon
              )}
            </span>
            <span className="text-[10.5px] leading-tight">{t(item.key)}</span>
          </Link>
        );
      })}
    </nav>
  );
}

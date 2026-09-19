import { setRequestLocale, getTranslations } from "next-intl/server";
import type { AppLocale } from "@/i18n/routing";
import { AppShell } from "@/components/layout/app-shell";
import { KhungTrang } from "@/components/common/khung-trang";
import { NotificationCenter } from "@/components/notifications/notification-center";
import { Link } from "@/i18n/navigation";

/**
 * F7 — trung tâm thông báo.
 *
 * A first-class route, not a popover-only feature: this is the channel the
 * MVP promises giỗ reminders will arrive on, so it has to be linkable,
 * bookmarkable and reachable from the main nav on a phone.
 */
export default async function NotificationsPage({
  params,
}: {
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  setRequestLocale(locale as AppLocale);
  const t = await getTranslations("notifications");

  return (
    <AppShell>
      <KhungTrang>
        <h1 className="mb-1 font-serif text-2xl font-bold text-text-main">{t("title")}</h1>
        <p className="mb-4 mt-0 text-than text-text-muted">
          {t("pageSubtitle")}{" "}
          <Link href="/settings" className="text-primary no-underline hover:underline">
            {t("settingsLink")}
          </Link>
        </p>
        <NotificationCenter />
      </KhungTrang>
    </AppShell>
  );
}

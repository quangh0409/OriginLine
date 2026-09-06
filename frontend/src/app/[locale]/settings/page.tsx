import { setRequestLocale, getTranslations } from "next-intl/server";
import type { AppLocale } from "@/i18n/routing";
import { AppShell } from "@/components/layout/app-shell";
import { NotificationSettingsScreen } from "@/components/settings/notification-settings-screen";

/**
 * Cài đặt — currently notification channels only (F7). Later sprints add
 * language, privacy sharing level (F8) and account settings here.
 */
export default async function SettingsPage({
  params,
}: {
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  setRequestLocale(locale as AppLocale);
  const t = await getTranslations("settings");

  return (
    <AppShell>
      <div className="mx-auto w-full max-w-2xl px-3 py-4 sm:px-4 sm:py-6">
        <h1 className="mb-1 font-serif text-2xl font-bold text-text-main">{t("title")}</h1>
        <p className="mb-4 mt-0 text-[14px] text-text-muted">{t("subtitle")}</p>
        <NotificationSettingsScreen />
      </div>
    </AppShell>
  );
}

import { setRequestLocale, getTranslations } from "next-intl/server";
import type { AppLocale } from "@/i18n/routing";
import { AppShell } from "@/components/layout/app-shell";
import { KhungTrang } from "@/components/common/khung-trang";
import { EventCreateScreen } from "@/components/events/event-create-screen";

/** F7 Đợt 2 — tạo việc họ mới. Gác vai hiển thị sống trong `EventCreateScreen`. */
export default async function NewEventPage({
  params,
}: {
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  setRequestLocale(locale as AppLocale);
  const t = await getTranslations("eventForm");

  return (
    <AppShell>
      <KhungTrang>
        <h1 className="mb-4 font-serif text-2xl font-bold text-text-main">{t("createTitle")}</h1>
        <EventCreateScreen />
      </KhungTrang>
    </AppShell>
  );
}

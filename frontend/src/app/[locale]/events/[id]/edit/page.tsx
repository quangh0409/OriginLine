import { setRequestLocale, getTranslations } from "next-intl/server";
import type { AppLocale } from "@/i18n/routing";
import { AppShell } from "@/components/layout/app-shell";
import { KhungTrang } from "@/components/common/khung-trang";
import { EventEditScreen } from "@/components/events/event-edit-screen";

/** F7 Đợt 2 — sửa việc họ. Tải dữ liệu, `ETag` và gác vai sống trong client screen. */
export default async function EditEventPage({
  params,
}: {
  params: Promise<{ locale: string; id: string }>;
}) {
  const { locale, id } = await params;
  setRequestLocale(locale as AppLocale);
  const t = await getTranslations("eventForm");

  return (
    <AppShell>
      <KhungTrang>
        <h1 className="mb-4 font-serif text-2xl font-bold text-text-main">{t("editTitle")}</h1>
        <EventEditScreen eventId={id} />
      </KhungTrang>
    </AppShell>
  );
}

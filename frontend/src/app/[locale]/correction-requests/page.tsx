import { setRequestLocale, getTranslations } from "next-intl/server";
import type { AppLocale } from "@/i18n/routing";
import { AppShell } from "@/components/layout/app-shell";
import { KhungTrang } from "@/components/common/khung-trang";
import { CorrectionScreen } from "@/components/correction/correction-screen";

/**
 * Man hinh dinh chinh: hang doi cho duyet (Truong chi / Hoi dong) va cac de
 * nghi cua chinh nguoi dang dang nhap.
 *
 * Phan quyen nam tron trong <CorrectionScreen>, vi no phu thuoc vao pham vi
 * chi/nganh (`GET /api/v1/me`) chu khong chi vai tro — mot du kien chi biet
 * duoc o phia client sau khi goi API.
 */
export default async function CorrectionRequestsPage({
  params,
}: {
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  setRequestLocale(locale as AppLocale);
  const t = await getTranslations("correction");

  return (
    <AppShell>
      <KhungTrang>
        <h1 className="mb-1 font-serif text-2xl font-bold text-text-main">{t("title")}</h1>
        <p className="mb-4 mt-0 text-than text-text-muted">{t("subtitle")}</p>
        <CorrectionScreen />
      </KhungTrang>
    </AppShell>
  );
}

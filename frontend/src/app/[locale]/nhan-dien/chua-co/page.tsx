import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import type { AppLocale } from "@/i18n/routing";
import { AppShell } from "@/components/layout/app-shell";
import { KhungTrang } from "@/components/common/khung-trang";
import { ClaimNewPersonScreen } from "@/components/claim/claim-new-person-screen";

/**
 * Lối **"Tôi chưa có trong phả"** — `/nhan-dien/chua-co`.
 *
 * Đường tĩnh, không đọc chuỗi truy vấn, nên <b>không</b> cần ranh giới
 * Suspense — khác `/nhan-dien` ngay bên cạnh. Đó cũng chính là lý do ô đã chọn
 * đi qua `?nguoi=` chứ không qua một đoạn đường dẫn: `/nhan-dien/<mã>` sẽ nằm
 * cùng cấp với `chua-co` và `cho-duyet`, và va chạm ấy hỏng lặng lẽ (xem
 * `components/claim/routes.ts`).
 */
export async function generateMetadata({
  params,
}: {
  params: Promise<{ locale: string }>;
}): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "claim" });
  return {
    title: t("newPerson.title"),
    description: t("newPerson.lead"),
    robots: { index: false, follow: false },
  };
}

export default async function ChuaCoTrongPhaPage({
  params,
}: {
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  setRequestLocale(locale as AppLocale);

  return (
    <AppShell>
      <KhungTrang beRong="hep">
        <ClaimNewPersonScreen />
      </KhungTrang>
    </AppShell>
  );
}

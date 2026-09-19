import { setRequestLocale } from "next-intl/server";
import type { AppLocale } from "@/i18n/routing";
import { AppShell } from "@/components/layout/app-shell";
import { KhungTrang } from "@/components/common/khung-trang";
import { DuplicateReviewScreen } from "@/components/import/duplicate-review-screen";

/** Màn 4 — đối chiếu người nghi trùng. Máy xếp thứ tự, người quyết. */
export default async function DataImportDuplicatesPage({
  params,
}: {
  params: Promise<{ locale: string; loId: string }>;
}) {
  const { locale, loId } = await params;
  setRequestLocale(locale as AppLocale);

  return (
    <AppShell>
      <KhungTrang>
        <DuplicateReviewScreen batchId={loId} />
      </KhungTrang>
    </AppShell>
  );
}

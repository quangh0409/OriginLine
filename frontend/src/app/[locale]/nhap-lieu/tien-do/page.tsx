import { setRequestLocale } from "next-intl/server";
import type { AppLocale } from "@/i18n/routing";
import { AppShell } from "@/components/layout/app-shell";
import { KhungTrang } from "@/components/common/khung-trang";
import { BranchProgressScreen } from "@/components/import/branch-progress-screen";

/** Màn 5 — tiến độ theo chi. Không phải bảng xếp hạng. */
export default async function DataImportProgressPage({
  params,
}: {
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  setRequestLocale(locale as AppLocale);

  return (
    <AppShell>
      <KhungTrang>
        <BranchProgressScreen />
      </KhungTrang>
    </AppShell>
  );
}
